package com.mama.scheduler.domain

import android.content.Context
import android.util.Log
import com.mama.scheduler.BuildConfig
import com.mama.scheduler.data.local.ChatMessage
import com.mama.scheduler.data.local.ScheduledEvent
import com.mama.scheduler.data.remote.Content
import com.mama.scheduler.data.remote.GeminiClient
import com.mama.scheduler.data.remote.GeminiRequest
import com.mama.scheduler.data.remote.GenerationConfig
import com.mama.scheduler.data.remote.Part
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Gemini-powered conversational agent for the AI chat tab. */
@Singleton
class SchedulerAgent @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SchedulerAgent"
    }

    data class ActionResult(
        val response: String,
        val pendingAction: PendingAction?
    )

    data class PendingAction(
        val actionType: ActionType,
        val description: String,
        val eventChanges: List<EventChange>,
        val chatMessageId: Int
    )

    data class EventChange(
        val eventId: Int?,
        val eventTitle: String,
        val originalTime: String,
        val newTime: String,
        val changeType: ChangeType,
        val isMassive: Boolean
    )

    enum class ActionType {
        MODIFY_TIME, MODIFY_DATE, CANCEL_EVENT, ADD_EVENT,
        DELETE_EVENT, RESCHEDULE_EVENT, WEATHER_ALERT
    }

    enum class ChangeType { TIME_CHANGE, DATE_CHANGE, CANCELLATION, DELETION }

    suspend fun processMessage(
        userMessage: String,
        chatHistory: List<ChatMessage>,
        events: List<ScheduledEvent>,
        kids: List<String>,
        apiKey: String?
    ): ActionResult = withContext(Dispatchers.IO) {
        val effectiveApiKey = apiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY

        if (effectiveApiKey.isEmpty() || effectiveApiKey == "YOUR_GEMINI_API_KEY_HERE" || effectiveApiKey == "MY_GEMINI_API_KEY") {
            return@withContext ActionResult(
                response = "I'd love to help, but I need a Gemini API key to power my brain! Please add your API key in Settings.",
                pendingAction = null
            )
        }

        // Weather short-circuit
        checkWeatherContext(userMessage, events)?.let { return@withContext it }

        val historyContext = chatHistory.takeLast(10).joinToString("\n") { msg ->
            "${if (msg.isFromUser) "User" else "Assistant"}: ${msg.content}"
        }

        val userPrompt = """
            |CONVERSATION HISTORY:
            |$historyContext
            |
            |USER: $userMessage
        """.trimMargin()

        try {
            val request = GeminiRequest(
                contents = listOf(Content(parts = listOf(Part(text = userPrompt)))),
                generationConfig = GenerationConfig(responseMimeType = "application/json"),
                systemInstruction = Content(
                    parts = listOf(Part(text = buildSystemPrompt(buildEventsContext(events), kids.joinToString(", "))))
                )
            )

            val response = GeminiClient.service.generateContent(effectiveApiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (responseText.isNullOrEmpty()) {
                ActionResult("I'm having trouble thinking right now. Can you try again?", null)
            } else {
                parseAIResponse(responseText, events)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message: ${e.message}", e)
            ActionResult("I'm having trouble processing that. Error: ${e.message}", null)
        }
    }

    private fun buildSystemPrompt(eventsContext: String, kidsContext: String): String = """
        You are a friendly family scheduling assistant powered by Google Gemini AI. You help parents manage their kids' activities.

        IMPORTANT: You are powered by Google Gemini, NOT OpenAI. If asked about your technology, say you use Google Gemini AI.

        FAMILY CONTEXT:
        Kids: [$kidsContext]
        Today's date: ${SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.US).format(Date())}
        Current time: ${SimpleDateFormat("h:mm a", Locale.US).format(Date())}

        UPCOMING EVENTS:
        $eventsContext

        YOUR CAPABILITIES:
        1. Create new events when asked
        2. Modify existing events (time, date, details)
        3. Cancel or delete events
        4. Check weather and alert about storms
        5. Answer questions about the schedule

        RESPONSE FORMAT:
        When the user asks to change events, respond with JSON:
        {
          "response": "Your friendly response to the user",
          "action": {
            "type": "MODIFY_TIME|MODIFY_DATE|CANCEL_EVENT|ADD_EVENT|DELETE_EVENT|RESCHEDULE_EVENT",
            "changes": [
              {
                "eventId": null or event ID number,
                "eventTitle": "Original Event Title",
                "originalTime": "Original time/date",
                "newTime": "New time/date (if changing)",
                "changeType": "TIME_CHANGE|DATE_CHANGE|CANCELLATION|DELETION",
                "isMassive": true or false
              }
            ]
          }
        }

        When NOT making changes, respond with:
        {
          "response": "Your helpful response",
          "action": null
        }

        RULES:
        - Be conversational and friendly
        - For single event time changes, isMassive = false (auto-approve)
        - For multiple events (2+), date changes, or cancellations, isMassive = true (needs approval)
        - Always confirm what you're about to change
        - If weather might affect outdoor events, mention it
        - Keep responses concise but informative
        - NEVER make up event IDs - if you don't know the ID, use null and I'll look it up
    """.trimIndent()

    private fun buildEventsContext(events: List<ScheduledEvent>): String {
        if (events.isEmpty()) return "No scheduled events."
        val sdf = SimpleDateFormat("EEE, MMM dd 'at' h:mm a", Locale.US)
        return events.take(20).joinToString("\n") { event ->
            "[id=${event.id}] [${event.kidName ?: "Family"}] ${event.title} - ${sdf.format(Date(event.startTime))}${if (event.isRecurring) " (Recurring)" else ""}"
        }
    }

    private suspend fun checkWeatherContext(
        userMessage: String,
        events: List<ScheduledEvent>
    ): ActionResult? {
        val lower = userMessage.lowercase()
        if (!lower.contains("weather") && !lower.contains("storm") && !lower.contains("rain") &&
            !lower.contains("outdoor")
        ) return null

        val location = WeatherService.getLastKnownLocation(context)
            ?: return ActionResult(
                "I'd love to check the weather for you! Please enable location permission to get weather alerts for your area.",
                null
            )

        val outdoorKeywords = listOf("swim", "soccer", "tennis", "outdoor", "park", "camp")
        val relevantEvents = events.filter { event ->
            val hoursFromNow = (event.startTime - System.currentTimeMillis()) / 3_600_000
            hoursFromNow in 0..72 && outdoorKeywords.any { event.title.lowercase().contains(it) }
        }
        if (relevantEvents.isEmpty()) return null

        val event = relevantEvents.first()
        val weatherInfo = WeatherService.getWeatherForDate(
            event.startTime, location.latitude, location.longitude
        ) ?: return null

        val sdf = SimpleDateFormat("EEEE 'at' h:mm a", Locale.US)
        return if (weatherInfo.isStormy) {
            ActionResult(
                "${weatherInfo.stormAlert} I see ${event.title} is scheduled for ${sdf.format(Date(event.startTime))}. " +
                    "The forecast shows ${weatherInfo.description} (${weatherInfo.temperature.toInt()}°F) with " +
                    "${weatherInfo.windSpeed.toInt()} mph winds. Would you like me to suggest cancelling it?",
                null
            )
        } else {
            ActionResult(
                "The forecast for ${event.title} looks good! ${weatherInfo.description}, " +
                    "${weatherInfo.temperature.toInt()}°F. No weather concerns!",
                null
            )
        }
    }

    private fun parseAIResponse(
        responseText: String,
        events: List<ScheduledEvent>
    ): ActionResult {
        val cleanJson = GeminiClient.cleanJson(responseText)
        return try {
            val json = JSONObject(cleanJson)
            val reply = json.optString("response", "I understand!")
            val actionObj = json.optJSONObject("action")
                ?: return ActionResult(reply, null)

            val actionType = actionObj.optString("type", "")
            val changesArray = actionObj.optJSONArray("changes")
                ?: return ActionResult(reply, null)

            val changes = mutableListOf<EventChange>()
            for (i in 0 until changesArray.length()) {
                val change = changesArray.getJSONObject(i)
                val eventId = change.optInt("eventId", -1).takeIf { it != -1 }
                val title = change.optString("eventTitle", "Unknown Event")

                val matchedEvent = if (eventId == null) {
                    events.find { it.title.equals(title, ignoreCase = true) }
                } else {
                    events.find { it.id == eventId }
                }

                changes.add(
                    EventChange(
                        eventId = matchedEvent?.id ?: eventId,
                        eventTitle = matchedEvent?.title ?: title,
                        originalTime = change.optString("originalTime", "").ifEmpty {
                            matchedEvent?.let {
                                SimpleDateFormat("EEE, MMM dd 'at' h:mm a", Locale.US).format(Date(it.startTime))
                            } ?: ""
                        },
                        newTime = change.optString("newTime", ""),
                        changeType = runCatching {
                            ChangeType.valueOf(change.optString("changeType", "TIME_CHANGE"))
                        }.getOrDefault(ChangeType.TIME_CHANGE),
                        isMassive = change.optBoolean("isMassive", false)
                    )
                )
            }

            if (changes.isEmpty()) return ActionResult(reply, null)

            ActionResult(
                response = reply,
                pendingAction = PendingAction(
                    actionType = runCatching { ActionType.valueOf(actionType) }
                        .getOrDefault(ActionType.MODIFY_TIME),
                    description = buildActionDescription(changes),
                    eventChanges = changes,
                    chatMessageId = 0
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing AI response: ${e.message}", e)
            ActionResult(cleanJson, null)
        }
    }

    private fun buildActionDescription(changes: List<EventChange>): String = when {
        changes.size == 1 -> {
            val c = changes.first()
            when (c.changeType) {
                ChangeType.TIME_CHANGE -> "Change time of ${c.eventTitle} from ${c.originalTime} to ${c.newTime}"
                ChangeType.DATE_CHANGE -> "Move ${c.eventTitle} from ${c.originalTime} to ${c.newTime}"
                ChangeType.CANCELLATION -> "Cancel ${c.eventTitle}"
                ChangeType.DELETION -> "Delete ${c.eventTitle}"
            }
        }
        else -> "Modify ${changes.size} events: ${changes.joinToString(", ") { it.eventTitle }}"
    }
}

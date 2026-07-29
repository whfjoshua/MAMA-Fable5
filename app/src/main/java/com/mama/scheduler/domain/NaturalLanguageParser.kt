package com.mama.scheduler.domain

import android.util.Log
import com.mama.scheduler.BuildConfig
import com.mama.scheduler.data.local.KidProfileDao
import com.mama.scheduler.data.remote.Content
import com.mama.scheduler.data.remote.GeminiClient
import com.mama.scheduler.data.remote.GeminiRequest
import com.mama.scheduler.data.remote.GenerationConfig
import com.mama.scheduler.data.remote.ParsedScheduleListResult
import com.mama.scheduler.data.remote.ParsedScheduleResult
import com.mama.scheduler.data.remote.Part
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns free-form family text ("Lexi swim tomorrow 3-4pm with 15m travel")
 * into structured [ParsedScheduleResult]s. Uses Gemini when an API key is
 * available, with a rule-based local fallback.
 */
@Singleton
class NaturalLanguageParser @Inject constructor(
    private val kidProfileDao: KidProfileDao
) {
    companion object {
        private const val TAG = "NaturalLanguageParser"
    }

    suspend fun parse(input: String, apiKeyOverride: String? = null): List<ParsedScheduleResult> =
        withContext(Dispatchers.IO) {
            val apiKey = apiKeyOverride?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "YOUR_GEMINI_API_KEY_HERE" || apiKey == "MY_GEMINI_API_KEY") {
                Log.w(TAG, "No Gemini API key - using local rules")
                return@withContext listOf(parseWithLocalRules(input))
            }

            val kids = runCatching { kidProfileDao.getAllProfiles() }.getOrDefault(emptyList())
            val kidsListStr = kids.joinToString(", ") { it.name }

            val systemPrompt = """
                You are an expert family scheduler parsing assistant. Extract structured scheduling events from family-focused natural language input.
                The input may describe one activity or several at once. Generate one event entry per distinct activity.

                REFERENCE TIME CONTEXT:
                Current local time is: ${SimpleDateFormat("EEEE, MMMM dd, yyyy, HH:mm:ss z", Locale.US).format(Date())} (epoch millis: ${System.currentTimeMillis()}).
                Use this context exactly to resolve relative dates ('today', 'tomorrow', 'next wednesday', '3pm').
                - Today is ${SimpleDateFormat("MMMM dd, yyyy (EEEE)", Locale.US).format(Date())}.
                - Tomorrow is ${SimpleDateFormat("MMMM dd, yyyy (EEEE)", Locale.US).format(Date(System.currentTimeMillis() + 86_400_000))}.
                - Day after tomorrow is ${SimpleDateFormat("MMMM dd, yyyy (EEEE)", Locale.US).format(Date(System.currentTimeMillis() + 172_800_000))}.

                KIDS IN THE FAMILY (match names against these):
                [$kidsListStr]
                If the input names one of these kids, match it exactly and return it in "kidName". If a kid name is mentioned but not in this list, return the mentioned name anyway. If no kid is mentioned, return null.

                JSON OUTPUT SCHEMA — return a JSON object with this exact structure:
                {
                  "events": [
                    {
                      "title": "Short activity title (e.g. 'Swimming')",
                      "kidName": "Child name or null",
                      "dateString": "yyyy-MM-dd",
                      "startTimeMillis": 1780084800000,
                      "endTimeMillis": 1780088400000,
                      "travelBufferMinutes": 15,
                      "isRecurring": false,
                      "recurrenceRule": null,
                      "recurringEndDate": null
                    }
                  ]
                }

                RECURRING EVENTS: If the input says "every [day/week]", "for N weeks", "recurring", etc., set isRecurring=true and generate an RRULE:
                - Weekly on a specific day: "RRULE:FREQ=WEEKLY;BYDAY=WE;COUNT=4"
                - Daily: "RRULE:FREQ=DAILY;COUNT=7"
                - Default COUNT to 4 if unspecified. recurringEndDate = "yyyy-MM-dd" of last occurrence.

                "travelBufferMinutes" = travel time ('15m travel', 'with 10 min buffer'). Default 0.
                Return ONLY raw clean JSON text with no markdown fences or extra text.
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(Content(parts = listOf(Part(text = input)))),
                generationConfig = GenerationConfig(responseMimeType = "application/json"),
                systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
            )

            try {
                val response = GeminiClient.service.generateContent(apiKey, request)
                val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!jsonText.isNullOrEmpty()) {
                    val cleanJson = GeminiClient.cleanJson(jsonText)

                    runCatching {
                        GeminiClient.moshi.adapter(ParsedScheduleListResult::class.java).fromJson(cleanJson)
                    }.getOrNull()?.events?.let { return@withContext it }

                    runCatching {
                        GeminiClient.moshi.adapter(ParsedScheduleResult::class.java).fromJson(cleanJson)
                    }.getOrNull()?.let { return@withContext listOf(it) }

                    listOf(parseWithLocalRules(input))
                } else {
                    listOf(parseWithLocalRules(input))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini parsing failed: ${e.message}", e)
                listOf(parseWithLocalRules(input))
            }
        }

    // ---------------- Local rule-based fallback ----------------

    private suspend fun parseWithLocalRules(input: String): ParsedScheduleResult {
        val lower = input.lowercase()

        val kids = runCatching { kidProfileDao.getAllProfiles() }.getOrDefault(emptyList())
        var foundKidName: String? = kids.firstOrNull { lower.contains(it.name.lowercase()) }?.name
        if (foundKidName == null) {
            val commonNames = listOf("lexi", "leo", "sam", "anna", "mia", "alex", "jack")
            foundKidName = commonNames.firstOrNull { lower.contains(it) }
                ?.replaceFirstChar { it.uppercase() }
        }

        var title = "Activity"
        val titlesMap = mapOf(
            "swimming" to "Swimming", "swim" to "Swimming",
            "soccer" to "Soccer", "football" to "Football",
            "piano" to "Piano Lesson", "music" to "Music Practice",
            "dentist" to "Dentist Appointment", "doctor" to "Doctor Appointment",
            "school" to "School Event", "tennis" to "Tennis Lesson",
            "karate" to "Karate Lesson", "dance" to "Dance Class", "ballet" to "Ballet Class"
        )
        for ((key, value) in titlesMap) {
            if (lower.contains(key)) {
                title = value
                break
            }
        }
        if (title == "Activity" && foundKidName != null) {
            val stopWords = setOf("pm", "am", "next", "mon", "tue", "wed", "thu", "fri", "sat", "sun", "today", "tomorrow")
            val cleanWords = input.split(" ").filter { word ->
                val w = word.lowercase()
                w != foundKidName.lowercase() && stopWords.none { w.contains(it) }
            }
            title = cleanWords.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Activity"
        }

        // Date offset
        var offsetDays = 0
        val dayOffsets = listOf(
            "tomorrow" to 1, "tmrw" to 1, "tmr" to 1,
            "next mon" to 3, "next tue" to 4, "next wed" to 5, "next thu" to 6, "next fri" to 7,
            "mon" to 3, "tue" to 4, "wed" to 5, "thu" to 6, "fri" to 7, "sat" to 1, "sun" to 2
        )
        for ((key, offset) in dayOffsets) {
            if (lower.contains(key)) {
                offsetDays = offset
                break
            }
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val targetDateTime = System.currentTimeMillis() + offsetDays * 86_400_000L
        val dateString = sdf.format(Date(targetDateTime))

        // Time parsing
        var startHour = 15
        var startMinute = 0
        var durationHours = 1

        val rangeRegex = "(\\d+):?(\\d+)?\\s*(am|pm)?\\s*-\\s*(\\d+):?(\\d+)?\\s*(am|pm)".toRegex()
        val singleRegex = "(\\d+)\\s*(am|pm)".toRegex()

        val rangeMatch = rangeRegex.find(lower)
        if (rangeMatch != null) {
            val sHour = rangeMatch.groupValues[1].toInt()
            val sMin = rangeMatch.groupValues[2].ifEmpty { "0" }.toInt()
            val sAmpm = rangeMatch.groupValues[3].ifEmpty { rangeMatch.groupValues[6] }
            val eHour = rangeMatch.groupValues[4].toInt()
            val eMin = rangeMatch.groupValues[5].ifEmpty { "0" }.toInt()
            val eAmpm = rangeMatch.groupValues[6]

            startHour = to24Hour(sHour, sAmpm)
            startMinute = sMin
            val endHour24 = to24Hour(eHour, eAmpm)
            val diffMins = (endHour24 * 60 + eMin) - (startHour * 60 + startMinute)
            durationHours = if (diffMins > 0) diffMins / 60 else 1
        } else {
            when {
                lower.contains("lunch") || lower.contains("noon") -> startHour = 12
                lower.contains("breakfast") -> startHour = 8
                lower.contains("dinner") || lower.contains("supper") -> startHour = 18
                else -> {
                    val singleMatch = singleRegex.find(lower)
                    if (singleMatch != null) {
                        startHour = to24Hour(singleMatch.groupValues[1].toInt(), singleMatch.groupValues[2])
                    } else {
                        val plainMatch = "(?<![\\dapm])(\\d{1,2})(?![\\dapm])".toRegex().find(lower)
                        if (plainMatch != null) {
                            val hour = plainMatch.groupValues[1].toInt()
                            startHour = if (hour in 1..11) hour + 12 else if (hour == 12) 0 else hour
                        }
                    }
                }
            }
        }

        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = targetDateTime
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTimeMillis = calendar.timeInMillis
        val endTimeMillis = startTimeMillis + durationHours * 3_600_000L

        // Travel buffer
        var buffer = 0
        if (lower.contains("buffer") || lower.contains("travel")) {
            buffer = "(\\d+)\\s*(m|min|minute|mins)".toRegex().find(lower)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 15
        }

        // Recurring
        var isRecurring = false
        var recurrenceRule: String? = null
        var recurringEndDate: String? = null
        if (lower.contains("every") || lower.contains("recurring") || lower.contains("weekly")) {
            isRecurring = true
            val count = "for\\s+(\\d+)\\s*(weeks?|times?|sessions?)".toRegex()
                .find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 4
            val weekday = listOf(
                "mon" to "MO", "tue" to "TU", "wed" to "WE",
                "thu" to "TH", "fri" to "FR", "sat" to "SA", "sun" to "SU"
            ).firstOrNull { lower.contains(it.first) }?.second
            recurrenceRule = if (weekday != null) {
                "RRULE:FREQ=WEEKLY;BYDAY=$weekday;COUNT=$count"
            } else {
                "RRULE:FREQ=WEEKLY;COUNT=$count"
            }
            recurringEndDate = runCatching {
                sdf.format(Date(targetDateTime + count * 7L * 86_400_000L))
            }.getOrNull()
        }

        return ParsedScheduleResult(
            title = title,
            kidName = foundKidName,
            dateString = dateString,
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            travelBufferMinutes = buffer,
            isRecurring = isRecurring,
            recurrenceRule = recurrenceRule,
            recurringEndDate = recurringEndDate
        )
    }

    private fun to24Hour(hour: Int, ampm: String): Int = when {
        ampm == "pm" && hour < 12 -> hour + 12
        ampm == "am" && hour == 12 -> 0
        else -> hour
    }
}

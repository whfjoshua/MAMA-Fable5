package com.mama.scheduler.ui.screens.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mama.scheduler.data.local.ChatMessage
import com.mama.scheduler.data.repository.ChatRepository
import com.mama.scheduler.data.repository.EventRepository
import com.mama.scheduler.data.prefs.SettingsRepository
import com.mama.scheduler.domain.SchedulerAgent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val eventRepository: EventRepository,
    private val agent: SchedulerAgent,
    private val settings: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    val chatMessages: StateFlow<List<ChatMessage>> = chatRepository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isAiTyping = MutableStateFlow(false)
    val isAiTyping = _isAiTyping.asStateFlow()

    private val _pendingChatAction = MutableStateFlow<SchedulerAgent.PendingAction?>(null)
    val pendingChatAction = _pendingChatAction.asStateFlow()

    fun sendChatMessage(message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            chatRepository.insertMessage(ChatMessage(content = message, isFromUser = true))
            _isAiTyping.value = true
            try {
                val events = eventRepository.getAllEvents()
                val kids = eventRepository.getAllProfiles().map { it.name }
                val chatHistory = chatRepository.getAllMessages()

                val result = agent.processMessage(
                    userMessage = message,
                    chatHistory = chatHistory,
                    events = events,
                    kids = kids,
                    apiKey = settings.geminiApiKeyNow()
                )

                val msgId = chatRepository.insertMessage(
                    ChatMessage(
                        content = result.response,
                        isFromUser = false,
                        isActionable = result.pendingAction != null
                    )
                )

                result.pendingAction?.let { action ->
                    val actionWithMsgId = action.copy(chatMessageId = msgId.toInt())
                    val allSmall = action.eventChanges.isNotEmpty() &&
                        action.eventChanges.all { !it.isMassive }

                    if (allSmall) {
                        // Auto-approve small single changes
                        val firstChange = action.eventChanges.first()
                        firstChange.eventId?.let { eventId ->
                            eventRepository.getEventById(eventId)?.let { event ->
                                eventRepository.updateEvent(applyTimeChange(event, firstChange.newTime))
                                chatRepository.insertMessage(
                                    ChatMessage(
                                        content = "✅ Done! I updated ${event.title} to ${firstChange.newTime}",
                                        isFromUser = false
                                    )
                                )
                            }
                        }
                        _pendingChatAction.value = null
                    } else {
                        _pendingChatAction.value = actionWithMsgId
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing chat: ${e.message}", e)
                chatRepository.insertMessage(
                    ChatMessage(content = "Sorry, I encountered an error: ${e.message}", isFromUser = false)
                )
            } finally {
                _isAiTyping.value = false
            }
        }
    }

    fun approvePendingAction() {
        viewModelScope.launch {
            val action = _pendingChatAction.value ?: return@launch
            when (action.actionType) {
                SchedulerAgent.ActionType.CANCEL_EVENT,
                SchedulerAgent.ActionType.DELETE_EVENT -> {
                    action.eventChanges.forEach { change ->
                        change.eventId?.let { id ->
                            eventRepository.getEventById(id)?.let { eventRepository.deleteEvent(it) }
                        }
                    }
                    chatRepository.insertMessage(
                        ChatMessage(
                            content = "✅ Cancelled/deleted ${action.eventChanges.size} event(s) as requested.",
                            isFromUser = false
                        )
                    )
                }
                SchedulerAgent.ActionType.MODIFY_TIME,
                SchedulerAgent.ActionType.MODIFY_DATE,
                SchedulerAgent.ActionType.RESCHEDULE_EVENT -> {
                    action.eventChanges.forEach { change ->
                        change.eventId?.let { id ->
                            eventRepository.getEventById(id)?.let { event ->
                                val updated = when (change.changeType) {
                                    SchedulerAgent.ChangeType.TIME_CHANGE -> applyTimeChange(event, change.newTime)
                                    SchedulerAgent.ChangeType.DATE_CHANGE -> applyDateChange(event, change.newTime)
                                    else -> event
                                }
                                eventRepository.updateEvent(updated)
                            }
                        }
                    }
                    chatRepository.insertMessage(
                        ChatMessage(
                            content = "✅ Updated ${action.eventChanges.size} event(s) as requested.",
                            isFromUser = false
                        )
                    )
                }
                else -> {}
            }
            _pendingChatAction.value = null
        }
    }

    fun rejectPendingAction() {
        viewModelScope.launch {
            chatRepository.insertMessage(
                ChatMessage(content = "No problem! I cancelled that change.", isFromUser = false)
            )
            _pendingChatAction.value = null
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch { chatRepository.clearAllMessages() }
    }

    private fun applyTimeChange(
        event: com.mama.scheduler.data.local.ScheduledEvent,
        newTimeStr: String
    ): com.mama.scheduler.data.local.ScheduledEvent = try {
        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        val parsed = sdf.parse(newTimeStr)
        if (parsed != null) {
            val newCal = Calendar.getInstance().apply { time = parsed }
            val cal = Calendar.getInstance().apply {
                timeInMillis = event.startTime
                set(Calendar.HOUR_OF_DAY, newCal.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, newCal.get(Calendar.MINUTE))
            }
            val duration = event.endTime - event.startTime
            event.copy(startTime = cal.timeInMillis, endTime = cal.timeInMillis + duration)
        } else event
    } catch (e: Exception) {
        Log.e(TAG, "Error applying time change: ${e.message}", e)
        event
    }

    private fun applyDateChange(
        event: com.mama.scheduler.data.local.ScheduledEvent,
        newDateStr: String
    ): com.mama.scheduler.data.local.ScheduledEvent = try {
        val dateSdf = SimpleDateFormat("EEE, MMM dd 'at' h:mm a", Locale.US)
        val parsed = dateSdf.parse(newDateStr)
        if (parsed != null) {
            val newCalendar = Calendar.getInstance().apply { time = parsed }
            val originalCalendar = Calendar.getInstance().apply { timeInMillis = event.startTime }
            // Preserve original clock time; move the date
            newCalendar.set(Calendar.HOUR_OF_DAY, originalCalendar.get(Calendar.HOUR_OF_DAY))
            newCalendar.set(Calendar.MINUTE, originalCalendar.get(Calendar.MINUTE))
            // Parsed dates without a year default to 1970 — pin to current year
            if (newCalendar.get(Calendar.YEAR) < 2020) {
                newCalendar.set(Calendar.YEAR, originalCalendar.get(Calendar.YEAR))
            }
            val duration = event.endTime - event.startTime
            event.copy(
                startTime = newCalendar.timeInMillis,
                endTime = newCalendar.timeInMillis + duration,
                dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(newCalendar.timeInMillis)
            )
        } else event
    } catch (e: Exception) {
        Log.e(TAG, "Error applying date change: ${e.message}", e)
        event
    }
}

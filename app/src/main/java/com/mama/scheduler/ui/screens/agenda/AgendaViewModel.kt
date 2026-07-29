package com.mama.scheduler.ui.screens.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mama.scheduler.data.local.KidProfile
import com.mama.scheduler.data.local.PendingApprovalEvent
import com.mama.scheduler.data.local.ScheduledEvent
import com.mama.scheduler.data.prefs.SettingsRepository
import com.mama.scheduler.data.repository.EventRepository
import com.mama.scheduler.domain.ConflictDetector
import com.mama.scheduler.domain.ConflictInfo
import com.mama.scheduler.domain.NaturalLanguageParser
import com.mama.scheduler.domain.PhotoFinder
import com.mama.scheduler.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val repository: EventRepository,
    private val parser: NaturalLanguageParser,
    private val conflictDetector: ConflictDetector,
    private val reminderScheduler: ReminderScheduler,
    private val photoFinder: PhotoFinder,
    private val settings: SettingsRepository
) : ViewModel() {

    val allEvents: StateFlow<List<ScheduledEvent>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProfiles: StateFlow<List<KidProfile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isParsing = MutableStateFlow(false)
    val isParsing = _isParsing.asStateFlow()

    private val _isFindingPhotos = MutableStateFlow(false)
    val isFindingPhotos = _isFindingPhotos.asStateFlow()

    /** Parse natural-language input into the approval queue. */
    fun parseSchedulerText(text: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _isParsing.value = true
            try {
                val apiKeyOverride = settings.geminiApiKeyNow().takeIf { it.isNotBlank() }
                val results = parser.parse(text, apiKeyOverride)
                if (results.isNotEmpty()) {
                    val profiles = repository.getAllProfiles()
                    for (result in results) {
                        val matchedProfile =
                            profiles.firstOrNull { it.name.equals(result.kidName, ignoreCase = true) }
                        repository.insertPendingEvent(
                            PendingApprovalEvent(
                                rawInput = text,
                                title = result.title,
                                kidName = matchedProfile?.name ?: result.kidName,
                                kidId = matchedProfile?.id,
                                startTime = result.startTimeMillis,
                                endTime = result.endTimeMillis,
                                dateString = result.dateString,
                                travelBufferMinutes = result.travelBufferMinutes,
                                isRecurring = result.isRecurring,
                                recurrenceRule = result.recurrenceRule,
                                recurringEndDate = result.recurringEndDate
                            )
                        )
                    }
                    val message = if (results.size > 1) {
                        "Parsed ${results.size} activities into your Approvals queue!"
                    } else {
                        val recText = if (results[0].isRecurring) " (recurring)" else ""
                        "Parsed '${results[0].title}'$recText into your Approvals queue!"
                    }
                    onResult(true, message)
                } else {
                    onResult(false, "Couldn't understand that. Try including kid, activity, date and time.")
                }
            } catch (e: Exception) {
                onResult(false, "Parsing failed: ${e.localizedMessage}")
            } finally {
                _isParsing.value = false
            }
        }
    }

    fun addEvent(event: ScheduledEvent) {
        viewModelScope.launch {
            val insertedId = repository.insertEvent(event)
            reminderScheduler.scheduleEventReminder(event.copy(id = insertedId.toInt()))
        }
    }

    fun updateEvent(event: ScheduledEvent) {
        viewModelScope.launch {
            repository.updateEvent(event)
            reminderScheduler.scheduleEventReminder(event)
        }
    }

    fun deleteEvent(event: ScheduledEvent) {
        viewModelScope.launch {
            reminderScheduler.cancelEventReminder(event)
            repository.deleteEvent(event)
        }
    }

    fun checkConflicts(
        startTimeMillis: Long,
        endTimeMillis: Long,
        travelBufferMins: Int,
        kidId: Int?,
        dateString: String,
        excludeEventId: Int? = null
    ): List<ConflictInfo> = conflictDetector.check(
        startTimeMillis, endTimeMillis, travelBufferMins, kidId, dateString,
        allEvents.value, allProfiles.value, excludeEventId
    )

    // ----- Photo highlights -----
    fun findAndSetEventPhotos(event: ScheduledEvent) {
        viewModelScope.launch {
            _isFindingPhotos.value = true
            try {
                val photoUris = photoFinder.findEventPhotos(
                    eventTitle = event.title,
                    kidName = event.kidName,
                    startTimeMillis = event.startTime,
                    endTimeMillis = event.endTime,
                    apiKeyOverride = settings.geminiApiKey.first().takeIf { it.isNotBlank() }
                )
                if (photoUris.isNotEmpty()) {
                    repository.updateEvent(event.copy(photoUris = photoUris.joinToString(",")))
                }
            } finally {
                _isFindingPhotos.value = false
            }
        }
    }

    fun clearEventPhotos(event: ScheduledEvent) {
        viewModelScope.launch {
            repository.updateEvent(event.copy(photoUris = null))
        }
    }
}

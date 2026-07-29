package com.mama.scheduler.ui.screens.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mama.scheduler.data.local.KidProfile
import com.mama.scheduler.data.local.PendingApprovalEvent
import com.mama.scheduler.data.local.ScheduledEvent
import com.mama.scheduler.data.repository.EventRepository
import com.mama.scheduler.domain.ConflictDetector
import com.mama.scheduler.domain.ConflictInfo
import com.mama.scheduler.domain.RecurrenceExpander
import com.mama.scheduler.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApprovalsViewModel @Inject constructor(
    private val repository: EventRepository,
    private val conflictDetector: ConflictDetector,
    private val recurrenceExpander: RecurrenceExpander,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    val pendingEvents: StateFlow<List<PendingApprovalEvent>> = repository.pendingEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEvents: StateFlow<List<ScheduledEvent>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProfiles: StateFlow<List<KidProfile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun conflictsFor(pending: PendingApprovalEvent): List<ConflictInfo> = conflictDetector.check(
        startTimeMillis = pending.startTime,
        endTimeMillis = pending.endTime,
        travelBufferMins = pending.travelBufferMinutes,
        kidId = pending.kidId,
        dateString = pending.dateString,
        events = allEvents.value,
        profiles = allProfiles.value
    )

    fun approve(pending: PendingApprovalEvent) {
        viewModelScope.launch {
            if (pending.isRecurring && pending.recurrenceRule != null) {
                for (instance in recurrenceExpander.expand(pending)) {
                    val insertedId = repository.insertEvent(instance)
                    reminderScheduler.scheduleEventReminder(instance.copy(id = insertedId.toInt()))
                }
            } else {
                val confirmed = ScheduledEvent(
                    title = pending.title,
                    kidId = pending.kidId,
                    kidName = pending.kidName,
                    startTime = pending.startTime,
                    endTime = pending.endTime,
                    dateString = pending.dateString,
                    travelBufferMinutes = pending.travelBufferMinutes,
                    notes = "Parsed from: '${pending.rawInput}'",
                    isRecurring = pending.isRecurring,
                    recurrenceRule = pending.recurrenceRule,
                    recurringEndDate = pending.recurringEndDate
                )
                val insertedId = repository.insertEvent(confirmed)
                reminderScheduler.scheduleEventReminder(confirmed.copy(id = insertedId.toInt()))
            }
            repository.deletePendingEvent(pending)
        }
    }

    fun reject(pending: PendingApprovalEvent) {
        viewModelScope.launch { repository.deletePendingEvent(pending) }
    }

    fun update(pending: PendingApprovalEvent) {
        viewModelScope.launch { repository.updatePendingEvent(pending) }
    }
}

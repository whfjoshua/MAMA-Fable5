package com.mama.scheduler.data.repository

import com.mama.scheduler.data.local.KidProfile
import com.mama.scheduler.data.local.KidProfileDao
import com.mama.scheduler.data.local.PendingApprovalEvent
import com.mama.scheduler.data.local.PendingApprovalEventDao
import com.mama.scheduler.data.local.ScheduledEvent
import com.mama.scheduler.data.local.ScheduledEventDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val kidProfileDao: KidProfileDao,
    private val scheduledEventDao: ScheduledEventDao,
    private val pendingApprovalEventDao: PendingApprovalEventDao
) {
    val allProfiles: Flow<List<KidProfile>> = kidProfileDao.getAllProfilesFlow()
    val allEvents: Flow<List<ScheduledEvent>> = scheduledEventDao.getAllEventsFlow()
    val pendingEvents: Flow<List<PendingApprovalEvent>> = pendingApprovalEventDao.getPendingEventsFlow()

    // ----- Kid profiles -----
    suspend fun insertProfile(profile: KidProfile) = withContext(Dispatchers.IO) {
        kidProfileDao.insertProfile(profile)
    }

    suspend fun deleteProfile(profile: KidProfile) = withContext(Dispatchers.IO) {
        // Unassign the kid from their events first
        scheduledEventDao.getAllEvents()
            .filter { it.kidId == profile.id }
            .forEach { scheduledEventDao.updateEvent(it.copy(kidId = null, kidName = null)) }
        kidProfileDao.deleteProfile(profile)
    }

    suspend fun getAllProfiles(): List<KidProfile> = withContext(Dispatchers.IO) {
        kidProfileDao.getAllProfiles()
    }

    // ----- Scheduled events -----
    suspend fun insertEvent(event: ScheduledEvent): Long = withContext(Dispatchers.IO) {
        scheduledEventDao.insertEvent(event)
    }

    suspend fun updateEvent(event: ScheduledEvent) = withContext(Dispatchers.IO) {
        scheduledEventDao.updateEvent(event)
    }

    suspend fun deleteEvent(event: ScheduledEvent) = withContext(Dispatchers.IO) {
        scheduledEventDao.deleteEvent(event)
    }

    suspend fun getAllEvents(): List<ScheduledEvent> = withContext(Dispatchers.IO) {
        scheduledEventDao.getAllEvents()
    }

    suspend fun getEventById(id: Int): ScheduledEvent? = withContext(Dispatchers.IO) {
        scheduledEventDao.getEventById(id)
    }

    suspend fun getEventsOnDate(dateString: String): List<ScheduledEvent> = withContext(Dispatchers.IO) {
        scheduledEventDao.getEventsByDate(dateString)
    }

    // ----- Pending approvals -----
    suspend fun insertPendingEvent(event: PendingApprovalEvent) = withContext(Dispatchers.IO) {
        pendingApprovalEventDao.insertPendingEvent(event)
    }

    suspend fun updatePendingEvent(event: PendingApprovalEvent) = withContext(Dispatchers.IO) {
        pendingApprovalEventDao.updatePendingEvent(event)
    }

    suspend fun deletePendingEvent(event: PendingApprovalEvent) = withContext(Dispatchers.IO) {
        pendingApprovalEventDao.deletePendingEvent(event)
    }
}

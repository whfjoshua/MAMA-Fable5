package com.mama.scheduler.sync

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar as GoogleCalendarService
import com.google.api.services.calendar.model.Event as GoogleCalendarEvent
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.EventReminder
import com.mama.scheduler.auth.GoogleAuthManager
import com.mama.scheduler.core.DateUtils
import com.mama.scheduler.data.local.KidProfile
import com.mama.scheduler.data.local.ScheduledEvent
import com.mama.scheduler.data.repository.EventRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Two-way sync with the user's Google Calendar via the Calendar API. */
@Singleton
class GoogleCalendarSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: GoogleAuthManager,
    private val repository: EventRepository
) {
    companion object {
        private const val TAG = "GoogleCalendarSync"
    }

    suspend fun sync(calendarId: String): String = withContext(Dispatchers.IO) {
        val account = authManager.account.value
            ?: return@withContext "Please sign in with Google first in Settings"

        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(GoogleAuthManager.CALENDAR_SCOPE)
            )
            credential.selectedAccount = account.account

            val calendarService = GoogleCalendarService.Builder(
                NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
            )
                .setApplicationName("MAMA Scheduler")
                .build()

            val targetCalendar = calendarId.ifEmpty { "primary" }
            val localEvents = repository.getAllEvents()

            // ---- Export local -> Google ----
            var exported = 0
            for (event in localEvents) {
                if (event.googleEventId != null) continue

                val fullTitle = "${event.kidName?.let { "$it: " } ?: ""}${event.title}"
                val googleEvent = GoogleCalendarEvent().apply {
                    summary = fullTitle
                    description = "Added via MAMA Scheduler"
                    start = EventDateTime().apply {
                        dateTime = DateTime(event.startTime)
                        timeZone = TimeZone.getDefault().id
                    }
                    end = EventDateTime().apply {
                        dateTime = DateTime(event.endTime)
                        timeZone = TimeZone.getDefault().id
                    }
                    if (event.isRecurring && event.recurrenceRule != null) {
                        recurrence = listOf(event.recurrenceRule)
                    }
                    reminders = GoogleCalendarEvent.Reminders().apply {
                        useDefault = false
                        overrides = listOf(
                            EventReminder().apply {
                                method = "popup"
                                minutes = event.travelBufferMinutes.coerceAtLeast(15)
                            }
                        )
                    }
                }

                val inserted = calendarService.events().insert(targetCalendar, googleEvent).execute()
                if (inserted?.id != null) {
                    repository.updateEvent(event.copy(googleEventId = inserted.id))
                    exported++
                }
            }

            // ---- Import Google -> local (next 30 days) ----
            val now = Date()
            val thirtyDaysLater = Date(now.time + 30L * 86_400_000)
            val googleEvents = calendarService.events().list(targetCalendar)
                .setTimeMin(DateTime(now))
                .setTimeMax(DateTime(thirtyDaysLater))
                .setSingleEvents(true)
                .execute()

            var imported = 0
            val localKeys = localEvents.map { "${it.title}-${it.startTime}" }.toSet()
            val profiles = repository.getAllProfiles()

            googleEvents.items?.forEach { gEvent ->
                val startTime = gEvent.start.dateTime?.value ?: gEvent.start.date?.value ?: return@forEach
                val endTime = gEvent.end.dateTime?.value ?: gEvent.end.date?.value ?: return@forEach
                val key = "${gEvent.summary}-$startTime"

                if (key !in localKeys && gEvent.status != "cancelled") {
                    var matchedKid: KidProfile? = null
                    var parsedTitle = gEvent.summary ?: "Event"
                    for (p in profiles) {
                        if (gEvent.summary?.startsWith("${p.name}:", ignoreCase = true) == true) {
                            matchedKid = p
                            parsedTitle = gEvent.summary!!.substring(p.name.length + 1).trim()
                            break
                        }
                    }

                    repository.insertEvent(
                        ScheduledEvent(
                            title = parsedTitle,
                            kidId = matchedKid?.id,
                            kidName = matchedKid?.name,
                            startTime = startTime,
                            endTime = endTime,
                            dateString = DateUtils.dateStringFromMillis(startTime),
                            travelBufferMinutes = 0,
                            notes = gEvent.description ?: "Imported from Google Calendar",
                            googleEventId = gEvent.id,
                            isRecurring = !gEvent.recurrence.isNullOrEmpty(),
                            recurrenceRule = gEvent.recurrence?.firstOrNull()
                        )
                    )
                    imported++
                }
            }

            "Google Sync done! Exported $exported, imported $imported from Google Calendar."
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            "Google Calendar sync failed: ${e.message}"
        }
    }
}

package com.mama.scheduler.sync

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import com.mama.scheduler.core.DateUtils
import com.mama.scheduler.data.local.KidProfile
import com.mama.scheduler.data.local.ScheduledEvent
import com.mama.scheduler.data.repository.EventRepository
import com.mama.scheduler.notifications.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Two-way sync with the device (system) calendar via CalendarContract. */
@Singleton
class SystemCalendarSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: EventRepository,
    private val reminderScheduler: ReminderScheduler
) {
    companion object {
        private const val TAG = "SystemCalendarSync"
    }

    suspend fun sync(): String = withContext(Dispatchers.IO) {
        var importedCount = 0
        var exportedCount = 0
        try {
            val calendarId = getDefaultCalendarId()
            val localEvents = repository.getAllEvents()
            val uri = CalendarContract.Events.CONTENT_URI

            // ---- Export local -> system ----
            val existingSystemKeys = mutableSetOf<String>()
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART),
                null, null, null
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                if (titleIdx >= 0 && startIdx >= 0) {
                    while (cursor.moveToNext()) {
                        existingSystemKeys.add("${cursor.getString(titleIdx) ?: ""}-${cursor.getLong(startIdx)}")
                    }
                }
            }

            for (event in localEvents) {
                val fullTitle = "${event.kidName?.let { "$it: " } ?: ""}${event.title}"
                if ("$fullTitle-${event.startTime}" !in existingSystemKeys) {
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.DTSTART, event.startTime)
                        put(CalendarContract.Events.DTEND, event.endTime)
                        put(CalendarContract.Events.TITLE, fullTitle)
                        put(CalendarContract.Events.DESCRIPTION, event.notes ?: "Event synced from MAMA")
                        put(CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                        if (event.isRecurring && event.recurrenceRule != null) {
                            put(CalendarContract.Events.RRULE, event.recurrenceRule)
                        }
                    }
                    context.contentResolver.insert(uri, values)
                    exportedCount++
                }
            }

            // ---- Import system -> local (last 10 days to next 30 days) ----
            val todayMillis = DateUtils.todayMillis()
            val selection =
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(
                (todayMillis - 10L * 86_400_000).toString(),
                (todayMillis + 30L * 86_400_000).toString()
            )
            val localEventKeys = localEvents.map {
                "${it.kidName?.let { k -> "$k: " } ?: ""}${it.title}-${it.startTime}"
            }.toSet()

            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.RRULE
                ),
                selection, selectionArgs, null
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val descIdx = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                val rruleIdx = cursor.getColumnIndex(CalendarContract.Events.RRULE)

                if (titleIdx >= 0 && startIdx >= 0 && endIdx >= 0) {
                    val profiles = repository.getAllProfiles()
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(titleIdx) ?: "Imported Task"
                        val start = cursor.getLong(startIdx)
                        if ("$title-$start" in localEventKeys) continue

                        val desc = cursor.getString(descIdx) ?: ""
                        val end = cursor.getLong(endIdx)
                        val rrule = if (rruleIdx >= 0) cursor.getString(rruleIdx) else null

                        var matchedKid: KidProfile? = null
                        var parsedTitle = title
                        for (p in profiles) {
                            if (title.startsWith("${p.name}:", ignoreCase = true)) {
                                matchedKid = p
                                parsedTitle = title.substring(p.name.length + 1).trim()
                                break
                            } else if (title.contains(p.name, ignoreCase = true)) {
                                matchedKid = p
                                break
                            }
                        }

                        val newEvent = ScheduledEvent(
                            title = parsedTitle,
                            kidId = matchedKid?.id,
                            kidName = matchedKid?.name,
                            startTime = start,
                            endTime = end,
                            dateString = DateUtils.dateStringFromMillis(start),
                            notes = desc.ifBlank { "Imported from Phone Calendar" },
                            isRecurring = rrule != null,
                            recurrenceRule = rrule
                        )
                        val addedId = repository.insertEvent(newEvent)
                        reminderScheduler.scheduleEventReminder(newEvent.copy(id = addedId.toInt()))
                        importedCount++
                    }
                }
            }
            "Sync completed! Imported $importedCount and exported $exportedCount activities."
        } catch (s: SecurityException) {
            "Calendar permission is required! Please grant calendar permission in system settings."
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            "Sync error: ${e.localizedMessage}"
        }
    }

    private fun getDefaultCalendarId(): Long {
        val selection =
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                selection, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve default system calendar", e)
        }
        return 1L
    }
}

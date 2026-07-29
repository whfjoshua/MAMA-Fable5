package com.mama.scheduler.domain

import com.mama.scheduler.core.DateUtils
import com.mama.scheduler.data.local.PendingApprovalEvent
import com.mama.scheduler.data.local.ScheduledEvent
import java.time.DayOfWeek
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/** Expands a recurring pending event (RRULE) into concrete ScheduledEvent instances. */
@Singleton
class RecurrenceExpander @Inject constructor() {

    fun expand(pending: PendingApprovalEvent): List<ScheduledEvent> {
        val rrule = pending.recurrenceRule ?: return emptyList()
        val events = mutableListOf<ScheduledEvent>()
        val duration = pending.endTime - pending.startTime

        val endDate = try {
            pending.recurringEndDate?.let { DateUtils.stringToLocalDate(it) }
                ?: DateUtils.stringToLocalDate(pending.dateString).plusMonths(2)
        } catch (e: Exception) {
            DateUtils.stringToLocalDate(pending.dateString).plusMonths(2)
        }

        var currentDate = DateUtils.stringToLocalDate(pending.dateString)
        val today = DateUtils.stringToLocalDate(DateUtils.todayString())
        val maxCount = 52
        var count = 0

        val stepDays = when {
            rrule.contains("DAILY") -> 1
            rrule.contains("WEEKLY") -> 7
            rrule.contains("MONTHLY") -> 30
            else -> 7
        }

        val byDay: List<DayOfWeek>? = "BYDAY=([A-Z,]+)".toRegex().find(rrule)?.let { match ->
            match.groupValues[1].split(",").mapNotNull { day ->
                when (day.trim()) {
                    "SU" -> DayOfWeek.SUNDAY
                    "MO" -> DayOfWeek.MONDAY
                    "TU" -> DayOfWeek.TUESDAY
                    "WE" -> DayOfWeek.WEDNESDAY
                    "TH" -> DayOfWeek.THURSDAY
                    "FR" -> DayOfWeek.FRIDAY
                    "SA" -> DayOfWeek.SATURDAY
                    else -> null
                }
            }
        }

        val startCal = Calendar.getInstance().apply { timeInMillis = pending.startTime }
        val startHour = startCal.get(Calendar.HOUR_OF_DAY)
        val startMinute = startCal.get(Calendar.MINUTE)

        while (!currentDate.isAfter(endDate) && count < maxCount) {
            val matchesDay = byDay.isNullOrEmpty() || byDay.contains(currentDate.dayOfWeek)
            if (matchesDay && !currentDate.isBefore(today)) {
                val startMillis = DateUtils.millisFromDateString(
                    DateUtils.dateToString(currentDate), startHour, startMinute
                )
                events.add(
                    ScheduledEvent(
                        title = pending.title,
                        kidId = pending.kidId,
                        kidName = pending.kidName,
                        startTime = startMillis,
                        endTime = startMillis + duration,
                        dateString = DateUtils.dateToString(currentDate),
                        travelBufferMinutes = pending.travelBufferMinutes,
                        notes = "Parsed from: '${pending.rawInput}'",
                        isRecurring = true,
                        recurrenceRule = pending.recurrenceRule,
                        recurringEndDate = pending.recurringEndDate
                    )
                )
                count++
            }
            currentDate = if (byDay.isNullOrEmpty()) {
                currentDate.plusDays(stepDays.toLong())
            } else {
                currentDate.plusDays(1)
            }
        }
        return events
    }
}

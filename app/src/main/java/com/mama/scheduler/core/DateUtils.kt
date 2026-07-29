package com.mama.scheduler.core

import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val displayDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM dd", Locale.US)
    private val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEE", Locale.US)

    fun todayString(): String = LocalDate.now().format(dateFormatter)

    fun todayMillis(): Long = LocalDate.now()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    fun nowMillis(): Long = System.currentTimeMillis()

    fun dateToString(date: LocalDate): String = date.format(dateFormatter)

    fun stringToLocalDate(dateStr: String): LocalDate = LocalDate.parse(dateStr, dateFormatter)

    fun formatForDisplay(dateStr: String): String = try {
        stringToLocalDate(dateStr).format(displayDateFormatter)
    } catch (e: Exception) {
        dateStr
    }

    fun formatDayOfWeek(dateStr: String): String = try {
        stringToLocalDate(dateStr).format(dayOfWeekFormatter)
    } catch (e: Exception) {
        dateStr
    }

    fun dayOfMonth(dateStr: String): Int = try {
        stringToLocalDate(dateStr).dayOfMonth
    } catch (e: Exception) {
        0
    }

    fun toDisplayTime(millis: Long): String = try {
        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        sdf.timeZone = TimeZone.getDefault()
        sdf.format(Date(millis))
    } catch (e: Exception) {
        ""
    }

    fun toDisplayDateString(millis: Long): String = try {
        val sdf = SimpleDateFormat("EEEE, MMM dd", Locale.US)
        sdf.timeZone = TimeZone.getDefault()
        sdf.format(Date(millis))
    } catch (e: Exception) {
        ""
    }

    fun dateStringFromMillis(millis: Long): String = try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getDefault()
        sdf.format(Date(millis))
    } catch (e: Exception) {
        todayString()
    }

    fun millisFromDateString(dateStr: String, hour: Int = 0, minute: Int = 0): Long = try {
        stringToLocalDate(dateStr)
            .atTime(hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (e: Exception) {
        nowMillis()
    }

    fun addDays(dateStr: String, days: Int): String = try {
        stringToLocalDate(dateStr).plusDays(days.toLong()).format(dateFormatter)
    } catch (e: Exception) {
        dateStr
    }

    fun daysInMonth(year: Int, month: Int): Int = LocalDate.of(year, month, 1).lengthOfMonth()

    /** Returns 0=Sunday..6=Saturday for the 1st of the given month. */
    fun firstDayOfWeekOfMonth(year: Int, month: Int): Int =
        LocalDate.of(year, month, 1).dayOfWeek.value % 7

    fun currentYear(): Int = LocalDate.now().year

    fun currentMonth(): Int = LocalDate.now().monthValue

    /** Week containing [containingDateStr], starting on Sunday. */
    fun getWeekDates(containingDateStr: String): List<String> {
        val date = stringToLocalDate(containingDateStr)
        val sunday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        return (0..6).map { sunday.plusDays(it.toLong()).format(dateFormatter) }
    }

    fun isToday(dateStr: String): Boolean = dateStr == todayString()

    fun formatTimeRange(startMillis: Long, endMillis: Long): String =
        "${toDisplayTime(startMillis)} – ${toDisplayTime(endMillis)}"
}

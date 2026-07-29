package com.mama.scheduler.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mama.scheduler.data.local.ScheduledEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules per-event alarms and the daily morning-summary worker. */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ReminderScheduler"
        private const val MORNING_WORK_NAME = "morning_summary_notification"
    }

    fun scheduleEventReminder(event: ScheduledEvent) {
        if (event.id == 0) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            putExtra("event_id", event.id)
            putExtra("event_title", event.title)
            putExtra("event_kid_name", event.kidName)
            putExtra("event_notes", event.notes)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, event.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = event.startTime - event.travelBufferMinutes * 60_000L
        if (triggerTime > System.currentTimeMillis()) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.d(TAG, "Set alarm for event ${event.id} at $triggerTime")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule alarm", e)
            }
        }
    }

    fun cancelEventReminder(event: ScheduledEvent) {
        if (event.id == 0) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, EventReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, event.id, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    fun scheduleMorningNotification(hour: Int, minute: Int) {
        val workRequest = PeriodicWorkRequestBuilder<MorningNotificationWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(calculateInitialDelay(hour, minute), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MORNING_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.d(TAG, "Morning notification scheduled for $hour:$minute daily")
    }

    fun cancelMorningNotification() {
        WorkManager.getInstance(context).cancelUniqueWork(MORNING_WORK_NAME)
    }

    private fun calculateInitialDelay(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) target.add(Calendar.DAY_OF_MONTH, 1)
        return target.timeInMillis - now.timeInMillis
    }
}

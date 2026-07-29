package com.mama.scheduler.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mama.scheduler.core.DateUtils
import com.mama.scheduler.data.local.MamaDatabase

/** Posts the daily morning schedule summary notification. */
class MorningNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        return try {
            val db = MamaDatabase.getInstance(context)
            val todayStr = DateUtils.todayString()
            val events = db.scheduledEventDao().getEventsByDate(todayStr)

            val title = "☀️ Morning Schedule Summary"
            val subtitle = if (events.isEmpty()) {
                "No events scheduled for today, ${DateUtils.formatForDisplay(todayStr)}. Have a peaceful day!"
            } else {
                "Agenda today: " + events.joinToString(", ") {
                    "${it.kidName ?: "Family"}: ${it.title} (${DateUtils.toDisplayTime(it.startTime)})"
                }
            }

            EventReminderReceiver.ensureChannel(context)
            val notification = NotificationCompat.Builder(context, EventReminderReceiver.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(77, notification)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

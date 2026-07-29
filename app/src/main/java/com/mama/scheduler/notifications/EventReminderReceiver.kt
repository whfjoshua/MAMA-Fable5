package com.mama.scheduler.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class EventReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "family_alerts"

        fun ensureChannel(context: Context) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Family Alerts & Summaries",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies family members of schedule reminders"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val eventId = intent.getIntExtra("event_id", 0)
        val title = intent.getStringExtra("event_title") ?: "Upcoming Event"
        val kidName = intent.getStringExtra("event_kid_name")
        val note = intent.getStringExtra("event_notes") ?: ""

        val displaySubtitle = if (!kidName.isNullOrBlank()) {
            "Time for $kidName's activity: $title"
        } else {
            "Upcoming family activity: $title"
        }
        val descriptionText = if (note.isNotBlank()) "$displaySubtitle\nNotes: $note" else displaySubtitle

        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🔔 Calendar Reminder!")
            .setContentText(displaySubtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(descriptionText))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(eventId, notification)
    }
}

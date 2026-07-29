package com.mama.scheduler

import android.app.Application
import com.mama.scheduler.data.local.KidProfile
import com.mama.scheduler.data.prefs.SettingsRepository
import com.mama.scheduler.data.repository.EventRepository
import com.mama.scheduler.notifications.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MamaApplication : Application() {

    @Inject lateinit var eventRepository: EventRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            // Seed starter kid profiles on first launch
            if (eventRepository.getAllProfiles().isEmpty()) {
                eventRepository.insertProfile(KidProfile(name = "Lexi", colorHex = "#E64A19", dailyLimit = 2))
                eventRepository.insertProfile(KidProfile(name = "Leo", colorHex = "#1976D2", dailyLimit = 2))
                eventRepository.insertProfile(KidProfile(name = "Sam", colorHex = "#388E3C", dailyLimit = 3))
            }

            // Schedule the daily morning summary if enabled
            if (settingsRepository.morningNotifEnabled.first()) {
                reminderScheduler.scheduleMorningNotification(
                    settingsRepository.morningNotifHour.first(),
                    settingsRepository.morningNotifMinute.first()
                )
            }
        }
    }
}

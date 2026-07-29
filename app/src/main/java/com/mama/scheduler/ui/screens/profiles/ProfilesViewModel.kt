package com.mama.scheduler.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.mama.scheduler.auth.GoogleAuthManager
import com.mama.scheduler.data.local.KidProfile
import com.mama.scheduler.data.prefs.SettingsRepository
import com.mama.scheduler.data.repository.EventRepository
import com.mama.scheduler.notifications.ReminderScheduler
import com.mama.scheduler.sync.GoogleCalendarSync
import com.mama.scheduler.sync.SystemCalendarSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: EventRepository,
    private val settings: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val authManager: GoogleAuthManager,
    private val googleCalendarSync: GoogleCalendarSync,
    private val systemCalendarSync: SystemCalendarSync
) : ViewModel() {

    val allProfiles: StateFlow<List<KidProfile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val geminiApiKey: StateFlow<String> = settings.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val morningNotifEnabled: StateFlow<Boolean> = settings.morningNotifEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val morningNotifHour: StateFlow<Int> = settings.morningNotifHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val morningNotifMinute: StateFlow<Int> = settings.morningNotifMinute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val googleAccount: StateFlow<GoogleSignInAccount?> = authManager.account

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    // ----- Kid profiles -----
    fun addKidProfile(name: String, colorHex: String, dailyLimit: Int) {
        viewModelScope.launch {
            repository.insertProfile(KidProfile(name = name, colorHex = colorHex, dailyLimit = dailyLimit))
        }
    }

    fun deleteKidProfile(profile: KidProfile) {
        viewModelScope.launch { repository.deleteProfile(profile) }
    }

    // ----- Settings -----
    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch { settings.setGeminiApiKey(key) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun setMorningNotification(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            settings.setMorningNotification(enabled, hour, minute)
            if (enabled) {
                reminderScheduler.scheduleMorningNotification(hour, minute)
            } else {
                reminderScheduler.cancelMorningNotification()
            }
        }
    }

    // ----- Google -----
    fun signOutFromGoogle(onResult: (String) -> Unit) {
        authManager.signOut { onResult("Signed out from Google") }
    }

    fun syncWithGoogleCalendar(onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = googleCalendarSync.sync(settings.googleCalendarId.first())
            _isSyncing.value = false
            onResult(result)
        }
    }

    fun syncWithSystemCalendar(onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = systemCalendarSync.sync()
            _isSyncing.value = false
            onResult(result)
        }
    }
}

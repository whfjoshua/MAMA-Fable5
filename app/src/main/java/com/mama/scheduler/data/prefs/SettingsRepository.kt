package com.mama.scheduler.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mama_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val MORNING_NOTIF_ENABLED = booleanPreferencesKey("morning_notif_enabled")
        val MORNING_NOTIF_HOUR = intPreferencesKey("morning_notif_hour")
        val MORNING_NOTIF_MINUTE = intPreferencesKey("morning_notif_minute")
        val GOOGLE_CALENDAR_ID = stringPreferencesKey("google_calendar_id")
    }

    val geminiApiKey: Flow<String> = context.dataStore.data.map { it[Keys.GEMINI_API_KEY] ?: "" }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }
    val morningNotifEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.MORNING_NOTIF_ENABLED] ?: true }
    val morningNotifHour: Flow<Int> = context.dataStore.data.map { it[Keys.MORNING_NOTIF_HOUR] ?: 7 }
    val morningNotifMinute: Flow<Int> =
        context.dataStore.data.map { it[Keys.MORNING_NOTIF_MINUTE] ?: 0 }
    val googleCalendarId: Flow<String> =
        context.dataStore.data.map { it[Keys.GOOGLE_CALENDAR_ID] ?: "primary" }

    suspend fun geminiApiKeyNow(): String = geminiApiKey.first()

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { it[Keys.GEMINI_API_KEY] = key.trim() }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setMorningNotification(enabled: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.MORNING_NOTIF_ENABLED] = enabled
            it[Keys.MORNING_NOTIF_HOUR] = hour
            it[Keys.MORNING_NOTIF_MINUTE] = minute
        }
    }

    suspend fun setGoogleCalendarId(id: String) {
        context.dataStore.edit { it[Keys.GOOGLE_CALENDAR_ID] = id }
    }
}

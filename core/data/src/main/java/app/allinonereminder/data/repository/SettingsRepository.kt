package app.allinonereminder.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

data class UserSettings(
    val defaultSnoozeMinutes: Int = 10,
    val ringDurationSeconds: Int = 120, // default 2 min
    val soundCritical: String = "alarm",
    val soundNormal: String = "reminder",
    val soundGentle: String = "gentle",
    val vibrationEnabled: Boolean = true,
    val speakRemindersEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val deviceId: String
)

class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_DEFAULT_SNOOZE = intPreferencesKey("default_snooze_minutes")
        val KEY_RING_DURATION = intPreferencesKey("ring_duration_seconds")
        val KEY_SOUND_CRITICAL = stringPreferencesKey("sound_critical")
        val KEY_SOUND_NORMAL = stringPreferencesKey("sound_normal")
        val KEY_SOUND_GENTLE = stringPreferencesKey("sound_gentle")
        val KEY_VIBRATION = booleanPreferencesKey("vibration_enabled")
        val KEY_SPEAK_REMINDERS = booleanPreferencesKey("speak_reminders_enabled")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_LAST_RESCHEDULE_ERROR = stringPreferencesKey("last_reschedule_error")
    }

    val lastRescheduleErrorFlow: Flow<String?> = dataStore.data.map { it[KEY_LAST_RESCHEDULE_ERROR] }

    suspend fun recordRescheduleError(errorClass: String, timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_RESCHEDULE_ERROR] = "$timestamp:$errorClass"
        }
    }

    suspend fun clearRescheduleError() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_LAST_RESCHEDULE_ERROR)
        }
    }

    val settingsFlow: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            defaultSnoozeMinutes = prefs[KEY_DEFAULT_SNOOZE] ?: 10,
            ringDurationSeconds = prefs[KEY_RING_DURATION] ?: 120,
            soundCritical = prefs[KEY_SOUND_CRITICAL] ?: "alarm",
            soundNormal = prefs[KEY_SOUND_NORMAL] ?: "reminder",
            soundGentle = prefs[KEY_SOUND_GENTLE] ?: "gentle",
            vibrationEnabled = prefs[KEY_VIBRATION] ?: true,
            speakRemindersEnabled = prefs[KEY_SPEAK_REMINDERS] ?: false,
            onboardingCompleted = prefs[KEY_ONBOARDING_DONE] ?: false,
            deviceId = prefs[KEY_DEVICE_ID] ?: ""
        )
    }

    suspend fun getOrCreateDeviceId(): String {
        val prefs = dataStore.data.first()
        val existing = prefs[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val newId = UUID.randomUUID().toString()
        dataStore.edit { it[KEY_DEVICE_ID] = newId }
        return newId
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_DONE] = completed }
    }

    suspend fun updateSettings(
        defaultSnoozeMinutes: Int? = null,
        ringDurationSeconds: Int? = null,
        vibrationEnabled: Boolean? = null,
        speakRemindersEnabled: Boolean? = null
    ) {
        dataStore.edit { prefs ->
            defaultSnoozeMinutes?.let { prefs[KEY_DEFAULT_SNOOZE] = it }
            ringDurationSeconds?.let { prefs[KEY_RING_DURATION] = it }
            vibrationEnabled?.let { prefs[KEY_VIBRATION] = it }
            speakRemindersEnabled?.let { prefs[KEY_SPEAK_REMINDERS] = it }
        }
    }

    suspend fun setSpeakRemindersEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SPEAK_REMINDERS] = enabled
        }
    }

    suspend fun getLastRefillAlertDate(itemId: String): String? {
        val prefs = dataStore.data.first()
        return prefs[stringPreferencesKey("refill_last_alert_$itemId")]
    }

    suspend fun setLastRefillAlertDate(itemId: String, date: String) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("refill_last_alert_$itemId")] = date
        }
    }
}


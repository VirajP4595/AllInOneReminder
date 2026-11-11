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

/** App appearance: follow the phone, or always light / always dark. Stored as these strings. */
object ThemeMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    fun normalize(value: String?): String = when (value) {
        LIGHT, DARK -> value
        else -> SYSTEM
    }
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

data class UserSettings(
    val defaultSnoozeMinutes: Int = 10,
    val ringDurationSeconds: Int = 120, // default 2 min
    val soundCritical: String = "alarm",
    val soundNormal: String = "reminder",
    val soundGentle: String = "gentle",
    val vibrationEnabled: Boolean = true,
    val speakRemindersEnabled: Boolean = false,
    val healthConnectVerifyEnabled: Boolean = false,
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
        val KEY_HEALTH_CONNECT_VERIFY = booleanPreferencesKey("health_connect_verify_enabled")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_LAST_RESCHEDULE_ERROR = stringPreferencesKey("last_reschedule_error")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_WEEKEND_DAYS = stringPreferencesKey("weekend_days")
        val KEY_CAREGIVER_NAME = stringPreferencesKey("caregiver_name")
        val KEY_NEXT_ALARM_AT = androidx.datastore.preferences.core.longPreferencesKey("next_alarm_at")

        /** Stored sound value meaning "use the phone's default sound". Older installs stored names like "alarm". */
        const val SOUND_DEFAULT = "default"
    }

    /** One of [ThemeMode]; defaults to following the phone. */
    val themeModeFlow: Flow<String> = dataStore.data.map { ThemeMode.normalize(it[KEY_THEME_MODE]) }

    /** When the coordinator last registered the next alarm for (epoch ms), or null when none is due. */
    val nextAlarmAtFlow: Flow<Long?> = dataStore.data.map { it[KEY_NEXT_ALARM_AT] }

    suspend fun setNextAlarmAt(epochMillis: Long?) {
        dataStore.edit { prefs ->
            if (epochMillis == null) prefs.remove(KEY_NEXT_ALARM_AT) else prefs[KEY_NEXT_ALARM_AT] = epochMillis
        }
    }

    /** Days counted as the weekend, as ISO day numbers (1 = Monday ... 7 = Sunday). Default Sat + Sun. */
    val weekendDaysFlow: Flow<Set<Int>> = dataStore.data.map { prefs ->
        prefs[KEY_WEEKEND_DAYS]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..7 }?.toSet()
            ?.takeIf { it.isNotEmpty() } ?: setOf(6, 7)
    }

    suspend fun setWeekendDays(days: Set<Int>) {
        dataStore.edit { it[KEY_WEEKEND_DAYS] = days.filter { d -> d in 1..7 }.sorted().joinToString(",") }
    }

    /** Name of whoever usually marks doses on this shared phone; saved with each Taken/Skip (#80). */
    val caregiverNameFlow: Flow<String?> = dataStore.data.map { it[KEY_CAREGIVER_NAME]?.takeIf { n -> n.isNotBlank() } }

    suspend fun setCaregiverName(name: String) {
        dataStore.edit { prefs ->
            val trimmed = name.trim().take(40)
            if (trimmed.isEmpty()) prefs.remove(KEY_CAREGIVER_NAME) else prefs[KEY_CAREGIVER_NAME] = trimmed
        }
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[KEY_THEME_MODE] = ThemeMode.normalize(mode) }
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
            healthConnectVerifyEnabled = prefs[KEY_HEALTH_CONNECT_VERIFY] ?: false,
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

    /**
     * Alarm sound for one criticality level ("CRITICAL", "NORMAL", "GENTLE"): a ringtone URI from
     * the system picker, or [SOUND_DEFAULT] for the phone's default alarm / notification sound.
     */
    suspend fun setSound(criticality: String, uri: String) {
        val key = when (criticality) {
            "CRITICAL" -> KEY_SOUND_CRITICAL
            "GENTLE" -> KEY_SOUND_GENTLE
            else -> KEY_SOUND_NORMAL
        }
        dataStore.edit { it[key] = uri }
    }

    suspend fun setSpeakRemindersEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SPEAK_REMINDERS] = enabled
        }
    }

    suspend fun setHealthConnectVerifyEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HEALTH_CONNECT_VERIFY] = enabled
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


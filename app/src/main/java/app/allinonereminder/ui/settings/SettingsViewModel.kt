package app.allinonereminder.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.backup.BackupManager
import app.allinonereminder.backup.BackupPreview
import app.allinonereminder.backup.InvalidBackupPassphraseException
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.health.ActivitySource
import app.allinonereminder.health.ExerciseVerificationScheduler
import app.allinonereminder.health.HealthAvailability
import app.allinonereminder.health.HealthConnectActivitySource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: String = "system",
    val weekendDays: Set<Int> = setOf(6, 7),
    val caregiverName: String = "",
    val defaultSnoozeMinutes: Int = 10,
    val ringDurationSeconds: Int = 120,
    val vibrationEnabled: Boolean = true,
    val soundCritical: String = "default",
    val soundNormal: String = "default",
    val soundGentle: String = "default",
    val speakRemindersEnabled: Boolean = false,
    val healthConnectVerifyEnabled: Boolean = false,
    val healthAvailability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    val healthPermissionGranted: Boolean = false,
    val onboardingCompleted: Boolean = true,
    val isProcessing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val restorePreview: BackupPreview? = null,
    val autoBackup: app.allinonereminder.backup.AutoBackup.Status? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
    private val activitySource: ActivitySource,
    private val alarmCoordinator: app.allinonereminder.alarm.AlarmCoordinator
) : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)
    private val _restorePreview = MutableStateFlow<BackupPreview?>(null)
    private val _healthStatus = MutableStateFlow(HealthAvailability.UNSUPPORTED to false)
    private val _autoBackup = MutableStateFlow<app.allinonereminder.backup.AutoBackup.Status?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        _isProcessing,
        _message,
        _error,
        _restorePreview
    ) { settings, isProcessing, msg, err, preview ->
        SettingsUiState(
            defaultSnoozeMinutes = settings.defaultSnoozeMinutes,
            ringDurationSeconds = settings.ringDurationSeconds,
            vibrationEnabled = settings.vibrationEnabled,
            soundCritical = settings.soundCritical,
            soundNormal = settings.soundNormal,
            soundGentle = settings.soundGentle,
            speakRemindersEnabled = settings.speakRemindersEnabled,
            healthConnectVerifyEnabled = settings.healthConnectVerifyEnabled,
            onboardingCompleted = settings.onboardingCompleted,
            isProcessing = isProcessing,
            message = msg,
            error = err,
            restorePreview = preview
        )
    }.combine(_healthStatus) { state, health ->
        state.copy(healthAvailability = health.first, healthPermissionGranted = health.second)
    }.combine(settingsRepository.themeModeFlow) { state, mode ->
        state.copy(themeMode = mode)
    }.combine(settingsRepository.weekendDaysFlow) { state, days ->
        state.copy(weekendDays = days)
    }.combine(settingsRepository.caregiverNameFlow) { state, name ->
        state.copy(caregiverName = name ?: "")
    }.combine(_autoBackup) { state, auto ->
        state.copy(autoBackup = auto)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun refreshAutoBackup() {
        _autoBackup.value = runCatching { app.allinonereminder.backup.AutoBackup.status(context) }.getOrNull()
    }

    /** Turns on weekly automatic backup into [folder]; the first backup runs right away. */
    fun enableAutoBackup(folder: Uri, passphrase: String) {
        val chars = passphrase.toCharArray()
        try {
            app.allinonereminder.backup.AutoBackup.enable(context, folder, chars)
            _message.value = "Automatic backup is on. The first backup is being written now."
        } catch (e: Exception) {
            _error.value = "Could not turn on automatic backup. Pick the folder again."
        } finally {
            chars.fill('\u0000')
        }
        refreshAutoBackup()
    }

    fun disableAutoBackup() {
        runCatching { app.allinonereminder.backup.AutoBackup.disable(context) }
        _message.value = "Automatic backup is off. Existing backup files were kept."
        refreshAutoBackup()
    }

    fun backUpNow() {
        runCatching { app.allinonereminder.backup.AutoBackup.runNow(context) }
        _message.value = "Backing up now. The time below updates when it's done."
    }

    /** Toggles one ISO weekday (1 = Monday) in the weekend; at least one day always stays. */
    fun toggleWeekendDay(day: Int) {
        viewModelScope.launch {
            val current = settingsRepository.weekendDaysFlow.first()
            val next = if (day in current) current - day else current + day
            if (next.isNotEmpty()) {
                settingsRepository.setWeekendDays(next)
                alarmCoordinator.reschedule("weekend_days_changed")
            }
        }
    }

    fun setCaregiverName(name: String) {
        viewModelScope.launch { settingsRepository.setCaregiverName(name) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    /** Re-reads Health Connect availability and permission (call when the screen is shown/resumed). */
    fun refreshHealthStatus() {
        refreshAutoBackup()
        viewModelScope.launch {
            val availability = activitySource.availability()
            val granted = availability == HealthAvailability.AVAILABLE && activitySource.hasExercisePermission()
            _healthStatus.value = availability to granted
            // A revoked permission must not leave the toggle on with nothing behind it.
            if (!granted && settingsRepository.settingsFlow.first().healthConnectVerifyEnabled) {
                settingsRepository.setHealthConnectVerifyEnabled(false)
                ExerciseVerificationScheduler.sync(context, false)
            }
        }
    }

    fun setHealthVerification(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHealthConnectVerifyEnabled(enabled)
            ExerciseVerificationScheduler.sync(context, enabled)
        }
    }

    fun onHealthPermissionResult(granted: Set<String>) {
        if (HealthConnectActivitySource.EXERCISE_PERMISSION in granted) {
            _healthStatus.value = _healthStatus.value.first to true
            setHealthVerification(true)
        } else {
            _healthStatus.value = _healthStatus.value.first to false
            _error.value = "Health Connect permission was not granted, so exercise is not being verified."
        }
    }

    fun updateSnoozeMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.updateSettings(defaultSnoozeMinutes = minutes)
        }
    }

    fun updateRingDurationSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.updateSettings(ringDurationSeconds = seconds)
        }
    }

    fun updateVibration(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateSettings(vibrationEnabled = enabled) }
    }

    /** [criticality] is "CRITICAL", "NORMAL" or "GENTLE"; [uri] a ringtone URI or "default". */
    fun setSound(criticality: String, uri: String) {
        viewModelScope.launch { settingsRepository.setSound(criticality, uri) }
    }

    fun updateSpeakReminders(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSpeakRemindersEnabled(enabled)
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(false)
        }
    }

    fun clearMessages() {
        _message.value = null
        _error.value = null
    }

    fun dismissRestorePreview() {
        _restorePreview.value = null
    }

    fun createBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            _error.value = null
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    backupManager.createBackup(os, passphrase.toCharArray())
                }
                _message.value = "Encrypted backup created successfully (.airbackup)"
            } catch (e: Exception) {
                _error.value = "Failed to create backup: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun inspectRestore(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            _error.value = null
            try {
                val preview = context.contentResolver.openInputStream(uri)?.use { `is` ->
                    backupManager.inspectBackup(`is`, passphrase.toCharArray())
                }
                if (preview != null) {
                    _restorePreview.value = preview
                } else {
                    _error.value = "Unable to read backup file"
                }
            } catch (_: InvalidBackupPassphraseException) {
                _error.value = "Incorrect passphrase or corrupted backup file"
            } catch (e: Exception) {
                _error.value = "Failed to inspect backup: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun confirmRestore(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            _error.value = null
            try {
                val preview = context.contentResolver.openInputStream(uri)?.use { `is` ->
                    backupManager.restoreBackup(`is`, passphrase.toCharArray())
                }
                _restorePreview.value = null
                if (preview != null) {
                    _message.value = "Restored ${preview.profilesCount} profiles, ${preview.itemsCount} items, and ${preview.schedulesCount} schedules"
                } else {
                    _message.value = "Backup restored successfully"
                }
            } catch (_: InvalidBackupPassphraseException) {
                _error.value = "Incorrect passphrase or corrupted backup file"
            } catch (e: Exception) {
                _error.value = "Failed to restore backup: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }
}

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
    val defaultSnoozeMinutes: Int = 10,
    val ringDurationSeconds: Int = 120,
    val vibrationEnabled: Boolean = true,
    val speakRemindersEnabled: Boolean = false,
    val healthConnectVerifyEnabled: Boolean = false,
    val healthAvailability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    val healthPermissionGranted: Boolean = false,
    val onboardingCompleted: Boolean = true,
    val isProcessing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val restorePreview: BackupPreview? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
    private val activitySource: ActivitySource
) : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)
    private val _restorePreview = MutableStateFlow<BackupPreview?>(null)
    private val _healthStatus = MutableStateFlow(HealthAvailability.UNSUPPORTED to false)

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    /** Re-reads Health Connect availability and permission (call when the screen is shown/resumed). */
    fun refreshHealthStatus() {
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

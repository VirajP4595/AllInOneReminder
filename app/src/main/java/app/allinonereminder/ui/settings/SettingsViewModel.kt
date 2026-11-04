package app.allinonereminder.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.backup.BackupManager
import app.allinonereminder.backup.BackupPreview
import app.allinonereminder.backup.InvalidBackupPassphraseException
import app.allinonereminder.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val defaultSnoozeMinutes: Int = 10,
    val ringDurationSeconds: Int = 120,
    val vibrationEnabled: Boolean = true,
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
    private val backupManager: BackupManager
) : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)
    private val _restorePreview = MutableStateFlow<BackupPreview?>(null)

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
            onboardingCompleted = settings.onboardingCompleted,
            isProcessing = isProcessing,
            message = msg,
            error = err,
            restorePreview = preview
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

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

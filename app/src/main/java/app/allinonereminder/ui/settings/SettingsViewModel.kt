package app.allinonereminder.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.data.repository.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val defaultSnoozeMinutes: Int = 10,
    val ringDurationSeconds: Int = 120,
    val vibrationEnabled: Boolean = true,
    val onboardingCompleted: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settingsFlow
        .map { s ->
            SettingsUiState(
                defaultSnoozeMinutes = s.defaultSnoozeMinutes,
                ringDurationSeconds = s.ringDurationSeconds,
                vibrationEnabled = s.vibrationEnabled,
                onboardingCompleted = s.onboardingCompleted
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

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
}

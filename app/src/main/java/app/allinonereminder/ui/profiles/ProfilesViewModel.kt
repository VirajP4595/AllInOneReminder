package app.allinonereminder.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.MealAnchorRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.model.DayType
import app.allinonereminder.model.MealAnchor
import app.allinonereminder.model.MealType
import app.allinonereminder.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ProfileWithDetails(
    val profile: Profile,
    val itemCount: Int,
    val mealAnchors: List<MealAnchor>
)

data class ProfilesUiState(
    val profiles: List<ProfileWithDetails> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val mealAnchorRepository: MealAnchorRepository,
    private val itemRepository: ItemRepository,
    private val settingsRepository: SettingsRepository,
    private val alarmCoordinator: AlarmCoordinator
) : ViewModel() {

    val uiState: StateFlow<ProfilesUiState> = combine(
        profileRepository.getActiveProfiles(),
        itemRepository.getActiveItems()
    ) { profiles, items ->
        val itemsByProfile = items.groupBy { it.profileId }

        val details = profiles.map { prof ->
            val anchors = mealAnchorRepository.getAnchorsSync(prof.id)
            ProfileWithDetails(
                profile = prof,
                itemCount = itemsByProfile[prof.id]?.size ?: 0,
                mealAnchors = anchors
            )
        }

        ProfilesUiState(
            profiles = details,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfilesUiState())

    fun saveProfile(profile: Profile, anchors: List<MealAnchor>) {
        viewModelScope.launch {
            val deviceId = settingsRepository.getOrCreateDeviceId()
            val existing = profileRepository.getProfileSync(profile.id)
            if (existing != null) {
                profileRepository.updateProfile(profile, deviceId)
            } else {
                profileRepository.createProfile(profile.name, profile.colorArgb, deviceId = deviceId)
            }

            anchors.forEach { anchor ->
                mealAnchorRepository.updateAnchor(anchor, deviceId)
            }

            alarmCoordinator.reschedule("profile_saved")
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            val deviceId = settingsRepository.getOrCreateDeviceId()
            profileRepository.deleteProfile(profileId, deviceId)
            alarmCoordinator.reschedule("profile_deleted")
        }
    }
}

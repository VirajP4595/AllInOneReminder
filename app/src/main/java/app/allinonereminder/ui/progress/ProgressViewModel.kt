package app.allinonereminder.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.ProgressRepository
import app.allinonereminder.gamify.*
import app.allinonereminder.model.Item
import app.allinonereminder.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class ProgressUiState(
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    val totalPoints: Int = 0,
    val todayRings: DailyRings? = null,
    val streaks: List<Pair<ItemStreak, Item>> = emptyList(),
    val badges: List<BadgeAward> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val profileRepository: ProfileRepository,
    private val itemRepository: ItemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val profiles = profileRepository.getActiveProfiles().firstOrNull() ?: emptyList()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)

            val currentProfileId = _uiState.value.selectedProfileId
            val result = progressRepository.getGamificationProgress(
                profileId = currentProfileId,
                zoneId = zone
            )

            val allItems = itemRepository.getActiveItems().firstOrNull() ?: emptyList()
            val itemsById = allItems.associateBy { it.id }

            val itemStreaks = result.streaks.mapNotNull { streak ->
                val item = itemsById[streak.itemId] ?: return@mapNotNull null
                streak to item
            }.sortedByDescending { it.first.current }

            val todayRings = result.rings.find { it.date == today }
                ?: DailyRings(
                    date = today,
                    meds = RingProgress(0, 0, 100),
                    hydration = RingProgress(0, 0, 100),
                    movement = RingProgress(0, 0, 100)
                )

            _uiState.update {
                it.copy(
                    profiles = profiles,
                    totalPoints = result.totalPoints,
                    todayRings = todayRings,
                    streaks = itemStreaks,
                    badges = result.badges,
                    isLoading = false
                )
            }
        }
    }

    fun selectProfile(profileId: String?) {
        _uiState.update { it.copy(selectedProfileId = profileId) }
        loadData()
    }
}


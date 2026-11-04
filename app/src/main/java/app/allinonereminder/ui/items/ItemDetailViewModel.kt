package app.allinonereminder.ui.items

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.data.repository.ChangeLogRepository
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.OccurrenceRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.ScheduleRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemDetailUiState(
    val item: Item? = null,
    val profile: Profile? = null,
    val schedules: List<Schedule> = emptyList(),
    val scheduleSummaryText: String = "",
    val photos: List<ItemPhoto> = emptyList(),
    val recentOccurrences: List<Occurrence> = emptyList(),
    val changeLogs: List<ChangeLog> = emptyList(),
    val isLoading: Boolean = true,
    val itemDeleted: Boolean = false
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val scheduleRepository: ScheduleRepository,
    private val profileRepository: ProfileRepository,
    private val occurrenceRepository: OccurrenceRepository,
    private val changeLogRepository: ChangeLogRepository,
    private val settingsRepository: SettingsRepository,
    private val alarmCoordinator: AlarmCoordinator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    init {
        loadItemDetails()
    }

    fun loadItemDetails() {
        viewModelScope.launch {
            val item = itemRepository.getItemSync(itemId) ?: return@launch
            val profile = profileRepository.getProfileSync(item.profileId)
            val schedules = scheduleRepository.getActiveSchedules(itemId).firstOrNull() ?: emptyList()
            val photos = itemRepository.getPhotosSync(itemId)
            val summary = ItemsViewModel.formatSchedulesSummary(schedules)

            // Last 30 occurrences
            val last30 = occurrenceRepository.getHistoryByItem(itemId, 30).firstOrNull() ?: emptyList()

            // Change log entries
            val logs = changeLogRepository.getChangeLogs("item", itemId).firstOrNull() ?: emptyList()

            _uiState.update {
                it.copy(
                    item = item,
                    profile = profile,
                    schedules = schedules,
                    scheduleSummaryText = summary,
                    photos = photos,
                    recentOccurrences = last30,
                    changeLogs = logs,
                    isLoading = false
                )
            }
        }
    }

    fun deleteItem() {
        viewModelScope.launch {
            val item = _uiState.value.item ?: return@launch
            val deviceId = settingsRepository.getOrCreateDeviceId()
            itemRepository.deleteItem(item.id, deviceId)
            alarmCoordinator.reschedule("item_deleted")
            _uiState.update { it.copy(itemDeleted = true) }
        }
    }
}

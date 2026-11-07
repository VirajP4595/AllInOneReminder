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
import app.allinonereminder.data.repository.StockEventRepository
import app.allinonereminder.model.*
import app.allinonereminder.schedule.SchedulingEngine
import app.allinonereminder.schedule.StockCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ItemDetailUiState(
    val item: Item? = null,
    val profile: Profile? = null,
    val schedules: List<Schedule> = emptyList(),
    val scheduleSummaryText: String = "",
    val photos: List<ItemPhoto> = emptyList(),
    val recentOccurrences: List<Occurrence> = emptyList(),
    val changeLogs: List<ChangeLog> = emptyList(),
    val currentStock: Double? = null,
    val stockStatusText: String? = null,
    val hasStockTracking: Boolean = false,
    val isLowStock: Boolean = false,
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
    private val stockEventRepository: StockEventRepository? = null,
    private val settingsRepository: SettingsRepository,
    private val alarmCoordinator: AlarmCoordinator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    init {
        loadItemDetails()
        // Reload whenever the item or its schedules change (e.g. after Edit > Save).
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                itemRepository.getItem(itemId),
                scheduleRepository.getActiveSchedules(itemId)
            ) { item, schedules -> item to schedules }
                .drop(1)
                .collect { loadItemDetails() }
        }
    }

    fun loadItemDetails() {
        viewModelScope.launch {
            val item = itemRepository.getItemSync(itemId) ?: return@launch
            val profile = profileRepository.getProfileSync(item.profileId)
            val schedules = scheduleRepository.getActiveSchedules(itemId).firstOrNull() ?: emptyList()
            val photos = itemRepository.getPhotosSync(itemId)
            val summary = ItemsViewModel.formatSchedulesSummary(schedules, item)

            // Last 30 occurrences
            val last30 = occurrenceRepository.getHistoryByItem(itemId, 30).firstOrNull() ?: emptyList()

            // Change log entries
            val logs = changeLogRepository.getChangeLogs("item", itemId).firstOrNull() ?: emptyList()

            // Stock
            val hasStock = stockEventRepository?.hasStockEvents(itemId) ?: false
            val stock = if (hasStock) stockEventRepository?.getCurrentStockSync(itemId) else null
            val nowInstant = Instant.now()
            val plannedCounts = StockCalculator.plan14DaysCounts(listOf(item), schedules, emptyList(), nowInstant)
            val plannedSize = plannedCounts[item.id] ?: 0
            val stockStatus = if (stock != null) {
                StockCalculator.formatStockStatus(stock, item.unitsPerDose, plannedSize)
            } else null
            val daysOfSupply = if (stock != null) {
                StockCalculator.calculateDaysOfSupply(stock, item.unitsPerDose, plannedSize)
            } else null
            val isLow = if (stock != null) {
                StockCalculator.isLowStock(stock, item.refillThreshold, daysOfSupply)
            } else false

            _uiState.update {
                it.copy(
                    item = item,
                    profile = profile,
                    schedules = schedules,
                    scheduleSummaryText = summary,
                    photos = photos,
                    recentOccurrences = last30,
                    changeLogs = logs,
                    currentStock = stock,
                    stockStatusText = stockStatus,
                    hasStockTracking = hasStock,
                    isLowStock = isLow,
                    isLoading = false
                )
            }
        }
    }

    fun addRefill(amount: Double) {
        viewModelScope.launch {
            stockEventRepository?.addRefill(itemId, amount)
            loadItemDetails()
        }
    }

    fun adjustStock(newTotal: Double) {
        viewModelScope.launch {
            stockEventRepository?.adjustStock(itemId, newTotal)
            loadItemDetails()
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

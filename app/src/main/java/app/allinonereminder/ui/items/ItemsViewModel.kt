package app.allinonereminder.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.ScheduleRepository
import app.allinonereminder.data.repository.StockEventRepository
import app.allinonereminder.model.*
import app.allinonereminder.schedule.SchedulingEngine
import app.allinonereminder.schedule.StockCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.Instant
import javax.inject.Inject

data class ItemWithSchedulesUiModel(
    val item: Item,
    val profile: Profile?,
    val schedules: List<Schedule>,
    val scheduleSummaryText: String,
    val isLowStock: Boolean = false,
    val stockStatusText: String? = null
)

data class ItemsUiState(
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    val selectedTypeFilter: ItemType? = null, // null means "All Types"
    val items: List<ItemWithSchedulesUiModel> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ItemsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val itemRepository: ItemRepository,
    private val scheduleRepository: ScheduleRepository,
    private val stockEventRepository: StockEventRepository? = null
) : ViewModel() {

    private val _selectedProfileId = MutableStateFlow<String?>(null)
    private val _selectedTypeFilter = MutableStateFlow<ItemType?>(null)

    val uiState: StateFlow<ItemsUiState> = combine(
        profileRepository.getActiveProfiles(),
        _selectedProfileId,
        _selectedTypeFilter,
        itemRepository.getActiveItems(),
        scheduleRepository.getAllActiveSchedules(),
        stockEventRepository?.getAllCurrentStocksFlow() ?: flowOf(emptyMap())
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val profiles = args[0] as List<Profile>
        val selectedProfileId = args[1] as String?
        val selectedTypeFilter = args[2] as ItemType?
        @Suppress("UNCHECKED_CAST")
        val allItems = args[3] as List<Item>
        @Suppress("UNCHECKED_CAST")
        val allSchedules = args[4] as List<Schedule>
        @Suppress("UNCHECKED_CAST")
        val stockMap = args[5] as Map<String, Double>

        val profileMap = profiles.associateBy { it.id }
        val schedulesByItem = allSchedules.groupBy { it.itemId }

        val nowInstant = Instant.now()
        val plannedCountsByItem = StockCalculator.plan14DaysCounts(allItems, allSchedules, emptyList(), nowInstant)

        val filteredItems = allItems
            .filter { item ->
                (selectedProfileId == null || item.profileId == selectedProfileId) &&
                (selectedTypeFilter == null || item.type == selectedTypeFilter)
            }
            .map { item ->
                val scheds = schedulesByItem[item.id] ?: emptyList()
                val summary = formatSchedulesSummary(scheds, item)
                val currentStock = stockMap[item.id]
                val plannedCount = plannedCountsByItem[item.id] ?: 0
                val daysOfSupply = if (currentStock != null) {
                    StockCalculator.calculateDaysOfSupply(currentStock, item.unitsPerDose, plannedCount)
                } else null
                val isLow = if (currentStock != null) {
                    StockCalculator.isLowStock(currentStock, item.refillThreshold, daysOfSupply)
                } else false
                val stockStatus = if (currentStock != null) {
                    StockCalculator.formatStockStatus(currentStock, item.unitsPerDose, plannedCount)
                } else null

                ItemWithSchedulesUiModel(
                    item = item,
                    profile = profileMap[item.profileId],
                    schedules = scheds,
                    scheduleSummaryText = summary,
                    isLowStock = isLow,
                    stockStatusText = stockStatus
                )
            }

        ItemsUiState(
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            selectedTypeFilter = selectedTypeFilter,
            items = filteredItems,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ItemsUiState())

    fun selectProfile(profileId: String?) {
        _selectedProfileId.value = profileId
    }

    fun selectTypeFilter(type: ItemType?) {
        _selectedTypeFilter.value = type
    }

    companion object {
        private val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        private fun formatWeekdayMask(mask: Int?): String {
            if (mask == null || mask == 0) return "no days"
            val days = weekdayNames.filterIndexed { index, _ -> (mask and (1 shl index)) != 0 }
            return if (days.size == 7) "every day" else days.joinToString(", ")
        }

        private fun formatMinuteOfDay(minuteOfDay: Int): String {
            val hour = (minuteOfDay / 60) % 24
            val min = minuteOfDay % 60
            val ampm = if (hour >= 12) "PM" else "AM"
            val hour12 = if (hour % 12 == 0) 12 else hour % 12
            return String.format("%d:%02d %s", hour12, min, ampm)
        }

        private fun nthWeekLabel(nthWeek: Int): String = when (nthWeek.coerceIn(1, 5)) {
            1 -> "1st"
            2 -> "2nd"
            3 -> "3rd"
            5 -> "last"
            else -> "4th"
        }

        @Suppress("UNUSED_PARAMETER")
        fun formatSchedulesSummary(schedules: List<Schedule>, item: Item? = null): String {
            if (schedules.isEmpty()) return "No schedule configured"
            return schedules.joinToString("; ") { sched ->
                val recurrence = when (sched.recurrence) {
                    RecurrenceType.DAILY -> "Every day"
                    RecurrenceType.WEEKDAYS -> "Weekdays"
                    RecurrenceType.EVERY_N_DAYS -> "Every ${sched.intervalN ?: 1} days"
                    RecurrenceType.EVERY_N_WEEKS -> {
                        val n = sched.intervalN ?: 1
                        val days = formatWeekdayMask(sched.weekdayMask)
                        if (n == 1) "Specific days ($days)" else "Every $n weeks ($days)"
                    }
                    RecurrenceType.MONTHLY -> {
                        val day = sched.dayOfMonth
                        val nth = sched.nthWeek
                        when {
                            day != null -> "Monthly on day $day"
                            nth != null -> {
                                val weekdayIndex = (0..6).firstOrNull {
                                    ((sched.weekdayMask ?: 0) and (1 shl it)) != 0
                                }
                                val weekday = weekdayNames.getOrNull(weekdayIndex ?: -1) ?: "day"
                                "Monthly on the ${nthWeekLabel(nth)} $weekday"
                            }
                            else -> "Monthly"
                        }
                    }
                    RecurrenceType.INTERVAL -> {
                        val hours = sched.intervalHours ?: 1
                        val start = sched.windowStartMinute ?: sched.minuteOfDay ?: 0
                        val end = sched.windowEndMinute ?: start
                        "Every $hours h from ${formatMinuteOfDay(start)} to ${formatMinuteOfDay(end)}"
                    }
                    RecurrenceType.PRN -> "As needed"
                    RecurrenceType.ONCE -> "Once"
                }

                val timing = when {
                    sched.recurrence == RecurrenceType.PRN -> ""
                    sched.recurrence == RecurrenceType.INTERVAL -> ""
                    sched.anchorType == ScheduleAnchorType.FIXED ->
                        "at ${formatMinuteOfDay(sched.minuteOfDay ?: 0)}, "
                    else -> {
                        val meal = sched.meal?.name?.lowercase() ?: "meal"
                        val rel = when (sched.relation) {
                            MealRelation.BEFORE -> "${sched.offsetMinutes ?: 0}m before"
                            MealRelation.WITH -> "with"
                            MealRelation.AFTER -> "${sched.offsetMinutes ?: 0}m after"
                            null -> "at"
                        }
                        "$rel $meal, "
                    }
                }

                val dose = sched.doseAmountOverride
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "$it — " }
                    ?: ""
                val validity = when {
                    sched.validFrom != null && sched.validUntil != null ->
                        " (${sched.validFrom} to ${sched.validUntil})"
                    sched.validFrom != null -> " (from ${sched.validFrom})"
                    sched.validUntil != null -> " (until ${sched.validUntil})"
                    else -> ""
                }
                "$dose$timing$recurrence$validity"
            }
        }
    }
}

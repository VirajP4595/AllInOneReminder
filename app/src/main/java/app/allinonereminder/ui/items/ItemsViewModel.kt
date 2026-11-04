package app.allinonereminder.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.ScheduleRepository
import app.allinonereminder.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ItemWithSchedulesUiModel(
    val item: Item,
    val profile: Profile?,
    val schedules: List<Schedule>,
    val scheduleSummaryText: String
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
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _selectedProfileId = MutableStateFlow<String?>(null)
    private val _selectedTypeFilter = MutableStateFlow<ItemType?>(null)

    val uiState: StateFlow<ItemsUiState> = combine(
        profileRepository.getActiveProfiles(),
        _selectedProfileId,
        _selectedTypeFilter,
        itemRepository.getActiveItems()
    ) { profiles, selectedProfileId, selectedTypeFilter, allItems ->
        val profileMap = profiles.associateBy { it.id }

        val filteredItems = allItems
            .filter { item ->
                (selectedProfileId == null || item.profileId == selectedProfileId) &&
                (selectedTypeFilter == null || item.type == selectedTypeFilter)
            }
            .map { item ->
                val scheds = scheduleRepository.getActiveSchedules(item.id).firstOrNull() ?: emptyList()
                val summary = formatSchedulesSummary(scheds)
                ItemWithSchedulesUiModel(
                    item = item,
                    profile = profileMap[item.profileId],
                    schedules = scheds,
                    scheduleSummaryText = summary
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
        fun formatSchedulesSummary(schedules: List<Schedule>): String {
            if (schedules.isEmpty()) return "No schedule configured"
            return schedules.joinToString("; ") { sched ->
                val recurrence = when (sched.recurrence) {
                    RecurrenceType.DAILY -> "Every day"
                    RecurrenceType.WEEKDAYS -> "Weekdays"
                    RecurrenceType.EVERY_N_DAYS -> "Every ${sched.intervalN ?: 1} days"
                    RecurrenceType.EVERY_N_WEEKS -> "Every ${sched.intervalN ?: 1} weeks"
                    RecurrenceType.ONCE -> "Once"
                }

                val timing = if (sched.anchorType == ScheduleAnchorType.FIXED) {
                    val mod = sched.minuteOfDay ?: 0
                    val hour = mod / 60
                    val min = mod % 60
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val hour12 = if (hour % 12 == 0) 12 else hour % 12
                    String.format("at %d:%02d %s", hour12, min, ampm)
                } else {
                    val meal = sched.meal?.name?.lowercase() ?: "meal"
                    val rel = when (sched.relation) {
                        MealRelation.BEFORE -> "${sched.offsetMinutes ?: 0}m before"
                        MealRelation.WITH -> "with"
                        MealRelation.AFTER -> "${sched.offsetMinutes ?: 0}m after"
                        null -> "at"
                    }
                    "$rel $meal"
                }
                "$timing, $recurrence"
            }
        }
    }
}

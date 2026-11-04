package app.allinonereminder.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.data.repository.*
import app.allinonereminder.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class OccurrenceUiModel(
    val occurrence: Occurrence,
    val item: Item,
    val profile: Profile?,
    val schedule: Schedule?,
    val photoId: String? = null
)

data class TodayUiState(
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null, // null means "All"
    val occurrences: List<OccurrenceUiModel> = emptyList(),
    val nextAlarmEpochMillis: Long? = null,
    val doubleDoseWarningOccurrence: OccurrenceUiModel? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val itemRepository: ItemRepository,
    private val occurrenceRepository: OccurrenceRepository,
    private val scheduleRepository: ScheduleRepository,
    private val mealSessionRepository: MealSessionRepository,
    private val alarmCoordinator: AlarmCoordinator
) : ViewModel() {

    private val _selectedProfileId = MutableStateFlow<String?>(null)
    private val _doubleDoseWarning = MutableStateFlow<OccurrenceUiModel?>(null)
    val doubleDoseWarning: StateFlow<OccurrenceUiModel?> = _doubleDoseWarning.asStateFlow()

    // Calculate today's time window in local timezone
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val uiState: StateFlow<TodayUiState> = combine(
        profileRepository.getActiveProfiles(),
        _selectedProfileId,
        occurrenceRepository.getOccurrencesBetween(todayStart, todayEnd),
        itemRepository.getActiveItems(),
        _doubleDoseWarning
    ) { profiles, selectedProfileId, todayOccurrences, allItems, warningOcc ->
        val itemMap = allItems.associateBy { it.id }
        val profileMap = profiles.associateBy { it.id }

        val filteredOccs = todayOccurrences
            .filter { occ ->
                selectedProfileId == null || occ.profileId == selectedProfileId
            }
            .sortedBy { it.dueAt }
            .mapNotNull { occ ->
                val item = itemMap[occ.itemId] ?: return@mapNotNull null
                val prof = profileMap[occ.profileId]
                OccurrenceUiModel(
                    occurrence = occ,
                    item = item,
                    profile = prof,
                    schedule = null,
                    photoId = null
                )
            }

        val now = System.currentTimeMillis()
        val nextDue = todayOccurrences
            .filter { it.status in listOf(OccurrenceStatus.PENDING, OccurrenceStatus.SNOOZED, OccurrenceStatus.DEFERRED_MEAL) && it.dueAt >= now }
            .minByOrNull { it.dueAt }
            ?.dueAt

        TodayUiState(
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            occurrences = filteredOccs,
            nextAlarmEpochMillis = nextDue,
            doubleDoseWarningOccurrence = warningOcc,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState())

    fun selectProfile(profileId: String?) {
        _selectedProfileId.value = profileId
    }

    fun onTakeClicked(uiModel: OccurrenceUiModel, force: Boolean = false) {
        viewModelScope.launch {
            val occ = uiModel.occurrence
            val item = uiModel.item
            val now = System.currentTimeMillis()
            val minGap = item.minGapMinutes

            if (!force && minGap != null && minGap > 0) {
                val lastTaken = occurrenceRepository.getLastTakenBeforeSync(item.id, now)
                val lastActedAt = lastTaken?.actedAt
                if (lastActedAt != null) {
                    val minutesSince = (now - lastActedAt) / 60_000L
                    if (minutesSince < minGap) {
                        _doubleDoseWarning.value = uiModel
                        return@launch
                    }
                }
            }

            _doubleDoseWarning.value = null
            occurrenceRepository.updateOccurrence(
                occ.copy(
                    status = OccurrenceStatus.TAKEN,
                    actedAt = now,
                    updatedAt = now
                )
            )
            alarmCoordinator.reschedule("taken_from_today")
        }
    }

    fun dismissDoubleDoseWarning() {
        _doubleDoseWarning.value = null
    }

    fun onSnoozeClicked(uiModel: OccurrenceUiModel, minutes: Int = 10) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val newDue = now + minutes * 60_000L
            occurrenceRepository.updateOccurrence(
                uiModel.occurrence.copy(
                    status = OccurrenceStatus.SNOOZED,
                    dueAt = newDue,
                    updatedAt = now
                )
            )
            alarmCoordinator.reschedule("snoozed_from_today")
        }
    }

    fun onSkipClicked(uiModel: OccurrenceUiModel) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            occurrenceRepository.updateOccurrence(
                uiModel.occurrence.copy(
                    status = OccurrenceStatus.SKIPPED,
                    actedAt = now,
                    updatedAt = now
                )
            )
            alarmCoordinator.reschedule("skipped_from_today")
        }
    }

    fun onStartMealNow(meal: MealType) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
            val profileId = _selectedProfileId.value ?: uiState.value.profiles.firstOrNull()?.id ?: return@launch

            mealSessionRepository.startMealSession(
                profileId = profileId,
                meal = meal,
                date = todayStr,
                startedAt = now
            )
            alarmCoordinator.reschedule("meal_started_now")
        }
    }
}

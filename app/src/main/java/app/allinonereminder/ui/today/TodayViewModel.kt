package app.allinonereminder.ui.today

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.alarm.AlarmRingService
import app.allinonereminder.data.local.ChangeLogWriter
import app.allinonereminder.data.repository.*
import app.allinonereminder.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
    val exactAlarmsDenied: Boolean = false,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val itemRepository: ItemRepository,
    private val occurrenceRepository: OccurrenceRepository,
    private val scheduleRepository: ScheduleRepository,
    private val mealSessionRepository: MealSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val changeLogWriter: ChangeLogWriter,
    private val alarmCoordinator: AlarmCoordinator
) : ViewModel() {

    private val _selectedProfileId = MutableStateFlow<String?>(null)
    private val _doubleDoseWarning = MutableStateFlow<OccurrenceUiModel?>(null)
    val doubleDoseWarning: StateFlow<OccurrenceUiModel?> = _doubleDoseWarning.asStateFlow()

    // Ticking flow deriving today's start and end window, re-evaluating across midnight and timezone changes
    val todayWindowFlow: Flow<Pair<Long, Long>> = flow {
        while (true) {
            val currentZone = ZoneId.systemDefault()
            val currentToday = LocalDate.now(currentZone)
            val start = currentToday.atStartOfDay(currentZone).toInstant().toEpochMilli()
            val end = currentToday.plusDays(1).atStartOfDay(currentZone).toInstant().toEpochMilli()
            emit(Pair(start, end))
            delay(30_000L) // tick every 30 seconds
        }
    }.distinctUntilChanged()

    private fun checkExactAlarmsDenied(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val canSchedule = alarmManager?.canScheduleExactAlarms() == true
            return !canSchedule
        }
        return false
    }

    private val occurrencesFlow = todayWindowFlow.flatMapLatest { (start, end) ->
        occurrenceRepository.getOccurrencesBetween(start, end)
    }

    val uiState: StateFlow<TodayUiState> = combine(
        profileRepository.getActiveProfiles(),
        _selectedProfileId,
        occurrencesFlow,
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
            exactAlarmsDenied = checkExactAlarmsDenied(),
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
            // Stop ringing service if item was ringing
            AlarmRingService.stopForOccurrence(context, occ.id)

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
            // Stop ringing service if item was ringing
            AlarmRingService.stopForOccurrence(context, uiModel.occurrence.id)

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

    fun onSkipClicked(uiModel: OccurrenceUiModel, reason: SkipReason = SkipReason.OTHER) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val deviceId = settingsRepository.getOrCreateDeviceId()
            // Stop ringing service if item was ringing
            AlarmRingService.stopForOccurrence(context, uiModel.occurrence.id)

            occurrenceRepository.updateOccurrence(
                uiModel.occurrence.copy(
                    status = OccurrenceStatus.SKIPPED,
                    actedAt = now,
                    updatedAt = now
                )
            )
            changeLogWriter.recordChange(
                entityType = "occurrence",
                entityId = uiModel.occurrence.id,
                field = "status_skipped",
                oldValue = uiModel.occurrence.status.name,
                newValue = "SKIPPED: ${reason.title}",
                changedAt = now,
                deviceId = deviceId
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

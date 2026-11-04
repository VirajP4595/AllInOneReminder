package app.allinonereminder.ui.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.alarm.AlarmNotificationHelper
import app.allinonereminder.data.repository.*
import app.allinonereminder.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val activeMealSession: MealSession? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeMealSessionFlow = _selectedProfileId.flatMapLatest { profileId ->
        mealSessionRepository.getActiveSession(profileId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TodayUiState> = combine(
        profileRepository.getActiveProfiles(),
        _selectedProfileId,
        occurrenceRepository.getOccurrencesBetween(todayStart, todayEnd),
        itemRepository.getActiveItems(),
        _doubleDoseWarning,
        activeMealSessionFlow
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val profiles = args[0] as List<Profile>
        val selectedProfileId = args[1] as String?
        @Suppress("UNCHECKED_CAST")
        val todayOccurrences = args[2] as List<Occurrence>
        @Suppress("UNCHECKED_CAST")
        val allItems = args[3] as List<Item>
        val warningOcc = args[4] as OccurrenceUiModel?
        val activeSession = args[5] as MealSession?

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
            .filter {
                (it.status == OccurrenceStatus.PENDING || it.status == OccurrenceStatus.SNOOZED) &&
                        it.dueAt > now
            }
            .minByOrNull { it.dueAt }
            ?.dueAt

        TodayUiState(
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            occurrences = filteredOccs,
            nextAlarmEpochMillis = nextDue,
            doubleDoseWarningOccurrence = warningOcc,
            activeMealSession = activeSession,
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

            val session = mealSessionRepository.startMealSession(
                profileId = profileId,
                meal = meal,
                date = todayStr,
                startedAt = now
            )
            AlarmNotificationHelper.postMealNotification(context, profileId, meal, session.id)
            alarmCoordinator.reschedule("meal_started_now")
        }
    }

    fun onEndMealNow(session: MealSession) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            mealSessionRepository.endMealSession(session.id, now)
            AlarmNotificationHelper.cancelMealNotification(context)

            // Recalculate any occurrences scheduled AFTER this meal or currently in DEFERRED_MEAL
            val unresolved = occurrenceRepository.getUnresolvedSync().filter { it.profileId == session.profileId }
            val schedules = scheduleRepository.getAllActiveSchedulesSync().associateBy { it.id }

            for (occ in unresolved) {
                val sched = schedules[occ.scheduleId]
                if (sched != null && sched.anchorType == ScheduleAnchorType.MEAL && sched.meal == session.meal) {
                    if (sched.relation == MealRelation.AFTER || occ.status == OccurrenceStatus.DEFERRED_MEAL) {
                        val offsetMillis = (sched.offsetMinutes ?: 0) * 60_000L
                        val newDue = now + offsetMillis
                        occurrenceRepository.updateOccurrence(
                            occ.copy(
                                status = OccurrenceStatus.PENDING,
                                dueAt = newDue,
                                updatedAt = now
                            )
                        )
                    }
                }
            }

            alarmCoordinator.reschedule("meal_ended_now")
        }
    }
}

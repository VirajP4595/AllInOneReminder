package app.allinonereminder.ui.today

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.alarm.AlarmNotificationHelper
import app.allinonereminder.alarm.AlarmRingService
import app.allinonereminder.alarm.RefillAlertNotifier
import app.allinonereminder.data.local.ChangeLogWriter
import app.allinonereminder.data.repository.*
import app.allinonereminder.model.*
import app.allinonereminder.schedule.SchedulingEngine
import app.allinonereminder.schedule.StockCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class OccurrenceUiModel(
    val occurrence: Occurrence,
    val item: Item,
    val profile: Profile?,
    val schedule: Schedule?,
    val photoId: String? = null,
    val stockStatus: String? = null
)

/** An as-needed (PRN) item shown in its own Today section, with no planned dose times. */
data class AsNeededUiModel(
    val item: Item,
    val profile: Profile?,
    val schedule: Schedule,
    val takenToday: Int,
    val maxPerDay: Int?,
    val canTake: Boolean,
    val stockStatus: String? = null
)


private data class TodayDataBundle(
    val profiles: List<Profile>,
    val selectedProfileId: String?,
    val todayOccurrences: List<Occurrence>,
    val allItems: List<Item>,
    val warningOcc: OccurrenceUiModel?
)

/** Points, level, streak and today's rings shown in the Today header. */
data class TodayProgress(
    val totalPoints: Int,
    val level: Int,
    val levelProgress: Float,
    val pointsToNextLevel: Int,
    val bestStreakDays: Int,
    val medsPercent: Int?,
    val hydrationPercent: Int?,
    val movementPercent: Int?
)

/** Shown in a snackbar with an Undo button after Take/Skip on Today. */
data class UndoInfo(val message: String, val occurrenceId: String, val previous: Occurrence)

/** Points needed per level. */
const val POINTS_PER_LEVEL = 100

data class TodayUiState(
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null, // null means "All"
    val occurrences: List<OccurrenceUiModel> = emptyList(),
    val nextAlarmEpochMillis: Long? = null,
    val doubleDoseWarningOccurrence: OccurrenceUiModel? = null,
    val exactAlarmsDenied: Boolean = false,
    val rescheduleError: String? = null,
    val activeMealSession: MealSession? = null,
    val asNeededItems: List<AsNeededUiModel> = emptyList(),
    val asNeededWarning: AsNeededUiModel? = null,
    val asNeededLimitItem: AsNeededUiModel? = null,
    val progress: TodayProgress? = null,
    val rewardMessage: String? = null,
    val undo: UndoInfo? = null,
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
    private val alarmCoordinator: AlarmCoordinator,
    private val stockEventRepository: StockEventRepository? = null,
    private val refillAlertNotifier: RefillAlertNotifier? = null,
    private val progressRepository: app.allinonereminder.data.repository.ProgressRepository? = null
) : ViewModel() {

    private val _progress = MutableStateFlow<TodayProgress?>(null)
    private val _reward = MutableStateFlow<String?>(null)
    private val _undo = MutableStateFlow<UndoInfo?>(null)

    /** Set when the user logs a dose here, so the next points increase is celebrated once. */
    @Volatile private var celebrateNextGain = false

    private suspend fun refreshProgress(profileId: String?) {
        val repo = progressRepository ?: return
        val result = try {
            repo.getGamificationProgress(profileId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        val today = LocalDate.now(ZoneId.systemDefault())
        val rings = result.rings.firstOrNull { it.date == today }
        val total = result.totalPoints
        val previous = _progress.value?.totalPoints
        _progress.value = TodayProgress(
            totalPoints = total,
            level = total / POINTS_PER_LEVEL + 1,
            levelProgress = (total % POINTS_PER_LEVEL) / POINTS_PER_LEVEL.toFloat(),
            pointsToNextLevel = POINTS_PER_LEVEL - (total % POINTS_PER_LEVEL),
            bestStreakDays = result.streaks.maxOfOrNull { it.current } ?: 0,
            medsPercent = rings?.meds?.takeIf { it.total > 0 }?.percent,
            hydrationPercent = rings?.hydration?.takeIf { it.total > 0 }?.percent,
            movementPercent = rings?.movement?.takeIf { it.total > 0 }?.percent
        )
        if (celebrateNextGain && previous != null && total > previous) {
            celebrateNextGain = false
            _reward.value = "+${total - previous} points! Keep it up"
        }
    }

    fun consumeReward() {
        _reward.value = null
    }

    private val _selectedProfileId = MutableStateFlow<String?>(null)
    private val _doubleDoseWarning = MutableStateFlow<OccurrenceUiModel?>(null)
    val doubleDoseWarning: StateFlow<OccurrenceUiModel?> = _doubleDoseWarning.asStateFlow()
    private val _asNeededWarning = MutableStateFlow<AsNeededUiModel?>(null)
    val asNeededWarning: StateFlow<AsNeededUiModel?> = _asNeededWarning.asStateFlow()
    private val _asNeededLimitItem = MutableStateFlow<AsNeededUiModel?>(null)

    // Ticking flow deriving today's start and end window, re-evaluating across midnight and timezone changes
    val todayWindowFlow: Flow<Pair<Long, Long>> = flow {
        val currentZone = ZoneId.systemDefault()
        val currentToday = LocalDate.now(currentZone)
        val start = currentToday.atStartOfDay(currentZone).toInstant().toEpochMilli()
        val end = currentToday.plusDays(1).atStartOfDay(currentZone).toInstant().toEpochMilli()
        emit(Pair(start, end))
    }

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

    private val activeMealSessionFlow = _selectedProfileId.flatMapLatest { profileId ->
        mealSessionRepository.getActiveSession(profileId)
    }

    val uiState: StateFlow<TodayUiState> = combine(
        profileRepository.getActiveProfiles(),
        _selectedProfileId,
        occurrencesFlow,
        itemRepository.getActiveItems(),
        _doubleDoseWarning,
        activeMealSessionFlow,
        settingsRepository.lastRescheduleErrorFlow,
        scheduleRepository.getAllActiveSchedules(),
        _asNeededWarning,
        _asNeededLimitItem,
        stockEventRepository?.getAllCurrentStocksFlow() ?: flowOf(emptyMap<String, Double>()),
        _progress,
        _reward,
        _undo
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
        val rescheduleError = args[6] as String?
        @Suppress("UNCHECKED_CAST")
        val allSchedules = args[7] as List<Schedule>
        val asNeededWarning = args[8] as AsNeededUiModel?
        val asNeededLimitItem = args[9] as AsNeededUiModel?
        @Suppress("UNCHECKED_CAST")
        val stockMap = args[10] as Map<String, Double>
        val progress = args[11] as TodayProgress?
        val reward = args[12] as String?
        val undo = args[13] as UndoInfo?
        val itemMap = allItems.associateBy { it.id }
        val profileMap = profiles.associateBy { it.id }
        val scheduleMap = allSchedules.associateBy { it.id }

        val nowInstant = Instant.now()
        val plannedCountsByItem = StockCalculator.plan14DaysCounts(allItems, allSchedules, emptyList(), nowInstant)

        val filteredOccs = todayOccurrences
            .filter { occ ->
                selectedProfileId == null || occ.profileId == selectedProfileId
            }
            .sortedBy { it.dueAt }
            .mapNotNull { occ ->
                val item = itemMap[occ.itemId] ?: return@mapNotNull null
                val prof = profileMap[occ.profileId]
                val currentStock = stockMap[item.id]
                val stockStatus = if (currentStock != null) {
                    StockCalculator.formatStockStatus(currentStock, item.unitsPerDose, plannedCountsByItem[item.id] ?: 0)
                } else null
                OccurrenceUiModel(
                    occurrence = occ,
                    item = item,
                    profile = prof,
                    schedule = scheduleMap[occ.scheduleId],
                    photoId = null,
                    stockStatus = stockStatus
                )
            }

        // As-needed items have no planned occurrences. Show one card per PRN schedule, with the
        // number of doses already taken today so the daily cap can be enforced in the UI.
        val asNeededItems = allSchedules
            .filter { it.recurrence == RecurrenceType.PRN }
            .mapNotNull { sched ->
                val item = itemMap[sched.itemId]?.takeIf { it.active } ?: return@mapNotNull null
                if (selectedProfileId != null && item.profileId != selectedProfileId) {
                    return@mapNotNull null
                }
                val takenToday = todayOccurrences.count {
                    it.itemId == item.id && it.status == OccurrenceStatus.TAKEN
                }
                val maxPerDay = item.maxPerDay
                val currentStock = stockMap[item.id]
                val stockStatus = if (currentStock != null) {
                    StockCalculator.formatStockStatus(currentStock, item.unitsPerDose, plannedCountsByItem[item.id] ?: 0)
                } else null
                AsNeededUiModel(
                    item = item,
                    profile = profileMap[item.profileId],
                    schedule = sched,
                    takenToday = takenToday,
                    maxPerDay = maxPerDay,
                    canTake = maxPerDay == null || takenToday < maxPerDay,
                    stockStatus = stockStatus
                )
            }

        val now = System.currentTimeMillis()
        val nextDue = todayOccurrences
            .filter { it.status in listOf(OccurrenceStatus.PENDING, OccurrenceStatus.SNOOZED, OccurrenceStatus.DEFERRED_MEAL) && it.remindAt >= now }
            .minByOrNull { it.remindAt }
            ?.remindAt

        TodayUiState(
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            occurrences = filteredOccs,
            nextAlarmEpochMillis = nextDue,
            doubleDoseWarningOccurrence = warningOcc,
            exactAlarmsDenied = checkExactAlarmsDenied(),
            rescheduleError = rescheduleError,
            activeMealSession = activeSession,
            asNeededItems = asNeededItems,
            asNeededWarning = asNeededWarning,
            asNeededLimitItem = asNeededLimitItem,
            progress = progress,
            rewardMessage = reward,
            undo = undo,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState())

    init {
        viewModelScope.launch {
            combine(occurrencesFlow, _selectedProfileId) { occs, profileId -> occs to profileId }
                .collectLatest { (_, profileId) -> refreshProgress(profileId) }
        }
    }

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

            celebrateNextGain = true
            if (occurrenceRepository.markOccurrenceTaken(occ.id, now)) {
                occurrenceRepository.setMarkedBy(occ.id, settingsRepository.caregiverNameFlow.first())
                _undo.value = UndoInfo("${item.name} marked taken", occ.id, occ)
            }
            refillAlertNotifier?.checkAndNotify(occ.itemId)
            alarmCoordinator.reschedule("taken_from_today")
        }
    }

    /** Reverts the last Take/Skip done on Today (the snackbar's Undo button). */
    fun undoLast() {
        val undo = _undo.value ?: return
        _undo.value = null
        viewModelScope.launch {
            occurrenceRepository.reopen(undo.occurrenceId, undo.previous)
            recordUndo(undo.occurrenceId, undo.previous.status.name)
            alarmCoordinator.reschedule("undo_from_today")
        }
    }

    fun consumeUndo() {
        _undo.value = null
    }

    /** Undo for a dose in "Done today": back to pending, alarm comes back a minute later at the earliest. */
    fun onReopenClicked(uiModel: OccurrenceUiModel) {
        viewModelScope.launch {
            occurrenceRepository.reopen(uiModel.occurrence.id)
            recordUndo(uiModel.occurrence.id, uiModel.occurrence.status.name)
            alarmCoordinator.reschedule("reopened_from_today")
        }
    }

    /** "Taken after all" for a dose marked missed. */
    fun onTakenAfterAll(uiModel: OccurrenceUiModel) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            occurrenceRepository.reopen(uiModel.occurrence.id, now = now, minDelayMillis = 0L)
            occurrenceRepository.markOccurrenceTaken(uiModel.occurrence.id, now)
            refillAlertNotifier?.checkAndNotify(uiModel.occurrence.itemId)
            alarmCoordinator.reschedule("taken_after_missed")
        }
    }

    /** Sets the time a dose was really taken, on the day it was taken. Never in the future. */
    fun onChangeTakenTime(uiModel: OccurrenceUiModel, minuteOfDay: Int) {
        viewModelScope.launch {
            val occ = uiModel.occurrence
            val zone = ZoneId.systemDefault()
            val day = Instant.ofEpochMilli(occ.actedAt ?: occ.dueAt).atZone(zone).toLocalDate()
            val picked = day.atStartOfDay(zone).plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()
            val actedAt = minOf(picked, System.currentTimeMillis())
            if (occurrenceRepository.changeTakenTime(occ.id, actedAt)) {
                changeLogWriter.recordChange(
                    entityType = "occurrence",
                    entityId = occ.id,
                    field = "actedAt",
                    oldValue = occ.actedAt?.toString(),
                    newValue = actedAt.toString(),
                    changedAt = System.currentTimeMillis(),
                    deviceId = settingsRepository.getOrCreateDeviceId()
                )
            }
        }
    }

    private suspend fun recordUndo(occurrenceId: String, oldStatus: String) {
        changeLogWriter.recordChange(
            entityType = "occurrence",
            entityId = occurrenceId,
            field = "status_undone",
            oldValue = oldStatus,
            newValue = "PENDING",
            changedAt = System.currentTimeMillis(),
            deviceId = settingsRepository.getOrCreateDeviceId()
        )
    }

    fun dismissDoubleDoseWarning() {
        _doubleDoseWarning.value = null
    }

    /**
     * Records a dose for an as-needed (PRN) item. PRN items are never planned by the engine, so the
     * user creates the TAKEN occurrence here. The daily cap and the double-dose gap both block the
     * insert unless the user explicitly confirms the gap warning.
     */
    fun onTakeAsNeeded(asNeeded: AsNeededUiModel, force: Boolean = false) {
        viewModelScope.launch {
            val item = asNeeded.item
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val takenToday = occurrenceRepository.countTakenBetweenSync(item.id, dayStart, dayEnd)

            val maxPerDay = item.maxPerDay
            if (maxPerDay != null && takenToday >= maxPerDay) {
                _asNeededLimitItem.value = asNeeded.copy(takenToday = takenToday, canTake = false)
                return@launch
            }

            if (!force) {
                val lastTaken = occurrenceRepository.getLastTakenBeforeSync(item.id, now)
                val warning = SchedulingEngine.doseWarning(
                    item = item,
                    lastTakenAt = lastTaken?.actedAt?.let { Instant.ofEpochMilli(it) },
                    now = Instant.ofEpochMilli(now)
                )
                if (warning != null) {
                    _asNeededWarning.value = asNeeded
                    return@launch
                }
            }

            _asNeededWarning.value = null
            _asNeededLimitItem.value = null
            val deviceId = settingsRepository.getOrCreateDeviceId()
            val newOcc = Occurrence(
                id = UUID.randomUUID().toString(),
                itemId = item.id,
                scheduleId = asNeeded.schedule.id,
                profileId = item.profileId,
                plannedAt = now,
                dueAt = now,
                remindAt = now,
                status = OccurrenceStatus.TAKEN,
                actedAt = now,
                deviceId = deviceId,
                updatedAt = now
            )
            occurrenceRepository.recordAsNeededTaken(newOcc, now)
            refillAlertNotifier?.checkAndNotify(item.id)
            alarmCoordinator.reschedule("taken_as_needed")
        }
    }

    fun dismissAsNeededWarning() {
        _asNeededWarning.value = null
    }

    fun dismissAsNeededLimit() {
        _asNeededLimitItem.value = null
    }

    fun retryReschedule() {
        viewModelScope.launch {
            alarmCoordinator.reschedule("User retry from banner")
        }
    }

    /** Snoozes for [minutes], or for the user's default snooze from Settings when null. */
    fun onSnoozeClicked(uiModel: OccurrenceUiModel, minutes: Int? = null) {
        viewModelScope.launch {
            val snoozeMinutes = minutes ?: settingsRepository.settingsFlow.first().defaultSnoozeMinutes
            val now = System.currentTimeMillis()
            val newDue = now + snoozeMinutes * 60_000L
            // Stop ringing service if item was ringing
            AlarmRingService.stopForOccurrence(context, uiModel.occurrence.id)

            occurrenceRepository.updateOccurrence(
                uiModel.occurrence.copy(
                    status = OccurrenceStatus.SNOOZED,
                    remindAt = newDue,
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
                    markedBy = settingsRepository.caregiverNameFlow.first(),
                    updatedAt = now
                )
            )
            _undo.value = UndoInfo("${uiModel.item.name} skipped", uiModel.occurrence.id, uiModel.occurrence)
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

    fun onStartMealNow(meal: MealType, targetProfileId: String? = null) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
            val profiles = uiState.value.profiles

            // Require explicit profile choice when "All" is selected and multiple profiles exist
            val profileId = targetProfileId
                ?: _selectedProfileId.value
                ?: if (profiles.size == 1) profiles.first().id else null

            if (profileId == null) {
                // Cannot start meal when multiple profiles exist and none explicitly targeted
                return@launch
            }

            val session = mealSessionRepository.startMealSession(
                profileId = profileId,
                meal = meal,
                date = todayStr,
                startedAt = now
            )
            AlarmNotificationHelper.postMealNotification(context, profileId, meal, session.id)

            // Re-time AFTER-meal items for this profile on this day (§15)
            val unresolved = occurrenceRepository.getUnresolvedSync().filter { it.profileId == profileId }
            val schedules = scheduleRepository.getAllActiveSchedulesSync().associateBy { it.id }
            for (occ in unresolved) {
                val sched = schedules[occ.scheduleId]
                if (sched != null && sched.anchorType == ScheduleAnchorType.MEAL && sched.meal == meal && sched.relation == MealRelation.AFTER) {
                    val offsetMillis = (sched.offsetMinutes ?: 0) * 60_000L
                    val newDue = now + offsetMillis
                    occurrenceRepository.updateOccurrence(
                        occ.copy(
                            status = OccurrenceStatus.PENDING,
                            plannedAt = newDue,
                            dueAt = newDue,
                            remindAt = newDue,
                            updatedAt = now
                        )
                    )
                }
            }

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
                                remindAt = newDue,
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

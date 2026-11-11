package app.allinonereminder.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.MealSessionRepository
import app.allinonereminder.data.repository.OccurrenceRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.ScheduleRepository
import app.allinonereminder.model.Item
import app.allinonereminder.model.ItemType
import app.allinonereminder.model.MealRelation
import app.allinonereminder.model.Occurrence
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.model.Profile
import app.allinonereminder.model.Schedule
import app.allinonereminder.model.ScheduleAnchorType
import app.allinonereminder.model.SkipReason
import app.allinonereminder.model.effectiveDoseAmount
import app.allinonereminder.schedule.SchedulingEngine
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class AlarmItemUiModel(
    val occurrence: Occurrence,
    val item: Item,
    val schedule: Schedule?,
    val profile: Profile?
)

data class AlarmUiState(
    val items: List<AlarmItemUiModel> = emptyList(),
    val warningOccurrenceId: String? = null,
    val warningTimeStr: String? = null,
    val skipOccurrenceId: String? = null,
    val snoozeOccurrenceId: String? = null,
    val finished: Boolean = false
)

/** Marker for "snooze every reminder on screen" in [AlarmUiState.snoozeOccurrenceId]. */
const val SNOOZE_ALL = "__all__"

@HiltViewModel
class AlarmViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val occurrenceRepository: OccurrenceRepository,
    private val itemRepository: ItemRepository,
    private val profileRepository: ProfileRepository,
    private val scheduleRepository: ScheduleRepository,
    private val mealSessionRepository: MealSessionRepository,
    private val alarmCoordinator: AlarmCoordinator,
    private val refillAlertNotifier: RefillAlertNotifier? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()

    fun loadOccurrences(occurrenceIds: Array<String>) {
        viewModelScope.launch {
            val list = mutableListOf<AlarmItemUiModel>()
            for (id in occurrenceIds) {
                val occ = occurrenceRepository.getByIdSync(id) ?: continue
                val item = itemRepository.getItemSync(occ.itemId) ?: continue
                val profile = profileRepository.getProfileSync(occ.profileId)
                val schedule = scheduleRepository.getScheduleSync(occ.scheduleId)
                list.add(AlarmItemUiModel(occ, item, schedule, profile))
            }
            _uiState.value = _uiState.value.copy(items = list)
        }
    }

    fun handleNotificationTake(occurrenceIds: Array<String>) {
        viewModelScope.launch {
            val now = Instant.now()
            val list = mutableListOf<AlarmItemUiModel>()
            var warningOcc: Occurrence? = null
            var warningTimeStr: String? = null

            for (id in occurrenceIds) {
                val occ = occurrenceRepository.getByIdSync(id) ?: continue
                val item = itemRepository.getItemSync(occ.itemId) ?: continue
                val profile = profileRepository.getProfileSync(occ.profileId)
                val schedule = scheduleRepository.getScheduleSync(occ.scheduleId)
                list.add(AlarmItemUiModel(occ, item, schedule, profile))

                if (warningOcc == null) {
                    val lastTaken = occurrenceRepository.getLastTakenBeforeSync(item.id, now.toEpochMilli())
                    val lastTakenInstant = lastTaken?.actedAt?.let { Instant.ofEpochMilli(it) }
                    val warning = SchedulingEngine.doseWarning(item, lastTakenInstant, now)
                    if (warning != null && lastTakenInstant != null) {
                        val timeFormatter = app.allinonereminder.ui.components.AppTime.formatter().withZone(ZoneId.systemDefault())
                        warningOcc = occ
                        warningTimeStr = timeFormatter.format(lastTakenInstant)
                    }
                }
            }

            if (warningOcc != null) {
                // Warning triggered! Populate UI state with items and show warning dialog on AlarmActivity
                _uiState.value = _uiState.value.copy(
                    items = list,
                    warningOccurrenceId = warningOcc.id,
                    warningTimeStr = warningTimeStr
                )
            } else {
                // No warning: mark all taken via targeted updates, stop ringing, reschedule, and finish
                for (occUi in list) {
                    occurrenceRepository.markTakenTargeted(occUi.occurrence.id, now.toEpochMilli())
                    AlarmRingService.stopForOccurrence(context, occUi.occurrence.id)
                    refillAlertNotifier?.checkAndNotify(occUi.item.id)
                }
                alarmCoordinator.reschedule("Notification: Taken Direct")
                _uiState.value = _uiState.value.copy(finished = true)
            }
        }
    }

    fun onTakenClicked(occurrenceId: String, force: Boolean = false) {
        viewModelScope.launch {
            val occUi = _uiState.value.items.firstOrNull { it.occurrence.id == occurrenceId } ?: return@launch
            val now = Instant.now()

            if (!force) {
                // Double-dose check
                val lastTaken = occurrenceRepository.getLastTakenBeforeSync(occUi.item.id, now.toEpochMilli())
                val lastTakenInstant = lastTaken?.actedAt?.let { Instant.ofEpochMilli(it) }
                val warning = SchedulingEngine.doseWarning(occUi.item, lastTakenInstant, now)
                if (warning != null && lastTakenInstant != null) {
                    val timeFormatter = app.allinonereminder.ui.components.AppTime.formatter().withZone(ZoneId.systemDefault())
                    _uiState.value = _uiState.value.copy(
                        warningOccurrenceId = occurrenceId,
                        warningTimeStr = timeFormatter.format(lastTakenInstant)
                    )
                    return@launch
                }
            }

            // Mark taken via targeted update
            occurrenceRepository.markTakenTargeted(occurrenceId, now.toEpochMilli())
            refillAlertNotifier?.checkAndNotify(occUi.item.id)
            dismissWarningDialog()
            checkCompletion(occurrenceId)
        }
    }

    fun onAllTakenClicked() {
        viewModelScope.launch {
            val now = Instant.now()
            // Double-dose guard check for all items in group
            for (uiModel in _uiState.value.items) {
                val lastTaken = occurrenceRepository.getLastTakenBeforeSync(uiModel.item.id, now.toEpochMilli())
                val lastTakenInstant = lastTaken?.actedAt?.let { Instant.ofEpochMilli(it) }
                val warning = SchedulingEngine.doseWarning(uiModel.item, lastTakenInstant, now)
                if (warning != null && lastTakenInstant != null) {
                    val timeFormatter = app.allinonereminder.ui.components.AppTime.formatter().withZone(ZoneId.systemDefault())
                    _uiState.value = _uiState.value.copy(
                        warningOccurrenceId = uiModel.occurrence.id,
                        warningTimeStr = timeFormatter.format(lastTakenInstant)
                    )
                    return@launch
                }
            }

            for (uiModel in _uiState.value.items) {
                occurrenceRepository.markTakenTargeted(uiModel.occurrence.id, now.toEpochMilli())
                AlarmRingService.stopForOccurrence(context, uiModel.occurrence.id)
                refillAlertNotifier?.checkAndNotify(uiModel.item.id)
            }
            alarmCoordinator.reschedule("Alarm: All Taken")
            _uiState.value = _uiState.value.copy(finished = true)
        }
    }

    /** One snooze for everything still on screen, so the group comes back together, once. */
    fun onSnoozeAllClicked(minutes: Int) {
        viewModelScope.launch {
            val newRemindAt = Instant.now().toEpochMilli() + minutes * 60_000L
            for (uiModel in _uiState.value.items) {
                occurrenceRepository.snoozeTargeted(uiModel.occurrence.id, newRemindAt)
                AlarmRingService.stopForOccurrence(context, uiModel.occurrence.id)
            }
            dismissSnoozeDialog()
            alarmCoordinator.reschedule("Alarm: Snooze all")
            _uiState.value = _uiState.value.copy(items = emptyList(), finished = true)
        }
    }

    fun onSnoozeClicked(occurrenceId: String, minutes: Int) {
        viewModelScope.launch {
            val now = Instant.now()
            val newDue = now.toEpochMilli() + minutes * 60_000L
            occurrenceRepository.snoozeTargeted(occurrenceId, newDue)
            dismissSnoozeDialog()
            checkCompletion(occurrenceId)
        }
    }

    fun onSkipClicked(occurrenceId: String, reason: SkipReason, note: String? = null) {
        viewModelScope.launch {
            val now = Instant.now()
            occurrenceRepository.markSkippedTargeted(occurrenceId, now.toEpochMilli(), reason.name, note)
            dismissSkipDialog()
            checkCompletion(occurrenceId)
        }
    }

    fun onNotEatenYetClicked(occurrenceId: String, afterEating: Boolean, deferMinutes: Int = 30) {
        viewModelScope.launch {
            val occUi = _uiState.value.items.firstOrNull { it.occurrence.id == occurrenceId } ?: return@launch
            val now = Instant.now()
            val sched = occUi.schedule
            val meal = sched?.meal

            if (afterEating && meal != null) {
                // Open a meal session and post ongoing notification
                val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
                val targetProfileId = occUi.profile?.id ?: occUi.occurrence.profileId
                val session = mealSessionRepository.startMealSession(
                    profileId = targetProfileId,
                    meal = meal,
                    date = todayStr,
                    startedAt = now.toEpochMilli()
                )
                AlarmNotificationHelper.postMealNotification(context, targetProfileId, meal, session.id)

                // Set status to DEFERRED_MEAL with 90 min + offset fallback
                val offset = sched.offsetMinutes ?: 0
                val fallbackDue = now.toEpochMilli() + (90 + offset) * 60_000L
                occurrenceRepository.deferMealTargeted(occurrenceId, fallbackDue)
            } else {
                // Snooze for specified minutes
                val newDue = now.toEpochMilli() + deferMinutes * 60_000L
                occurrenceRepository.snoozeTargeted(occurrenceId, newDue)
            }

            checkCompletion(occurrenceId)
        }
    }

    fun onStartingMealNowClicked(occurrenceId: String) {
        viewModelScope.launch {
            val occUi = _uiState.value.items.firstOrNull { it.occurrence.id == occurrenceId } ?: return@launch
            val now = Instant.now()
            val sched = occUi.schedule
            val meal = sched?.meal

            if (meal != null) {
                val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
                val targetProfileId = occUi.profile?.id ?: occUi.occurrence.profileId
                val session = mealSessionRepository.startMealSession(
                    profileId = targetProfileId,
                    meal = meal,
                    date = todayStr,
                    startedAt = now.toEpochMilli()
                )
                AlarmNotificationHelper.postMealNotification(context, targetProfileId, meal, session.id)

                // Re-time AFTER-meal items for this profile on this day (§15)
                val unresolved = occurrenceRepository.getUnresolvedSync().filter { it.profileId == targetProfileId }
                val schedules = scheduleRepository.getAllActiveSchedulesSync().associateBy { it.id }
                for (occ in unresolved) {
                    val s = schedules[occ.scheduleId]
                    if (s != null && s.anchorType == ScheduleAnchorType.MEAL && s.meal == meal && s.relation == MealRelation.AFTER) {
                        val offsetMillis = (s.offsetMinutes ?: 0) * 60_000L
                        val newDue = now.toEpochMilli() + offsetMillis
                        occurrenceRepository.updateOccurrence(
                            occ.copy(
                                status = OccurrenceStatus.PENDING,
                                plannedAt = newDue,
                                dueAt = newDue,
                                remindAt = newDue,
                                updatedAt = now.toEpochMilli()
                            )
                        )
                    }
                }
            }

            // Stop ringing service for this occurrence; DO NOT mark taken per §15
            AlarmRingService.stopForOccurrence(context, occurrenceId)
            alarmCoordinator.reschedule("Alarm: Starting meal now")
        }
    }

    private suspend fun checkCompletion(actedOccurrenceId: String) {
        AlarmRingService.stopForOccurrence(context, actedOccurrenceId)
        val remaining = _uiState.value.items.filter { it.occurrence.id != actedOccurrenceId }

        // The rest stay on screen and stay RINGING; the ring service owns their re-ring and, if the
        // screen is left alone, puts them all back on ONE shared re-ring time. Moving each item's
        // reminder time here is what made a group of reminders ring again after every single tap.
        _uiState.value = _uiState.value.copy(items = remaining)
        if (remaining.isNotEmpty()) {
            // The user is handling the group, so stop the noise while the rest wait on screen.
            AlarmRingService.silence(context)
        }
        alarmCoordinator.reschedule("Alarm: Acted on $actedOccurrenceId")
        if (remaining.isEmpty()) {
            _uiState.value = _uiState.value.copy(finished = true)
        }
    }

    fun showSkipDialog(occurrenceId: String) {
        _uiState.value = _uiState.value.copy(skipOccurrenceId = occurrenceId)
    }

    fun dismissSkipDialog() {
        _uiState.value = _uiState.value.copy(skipOccurrenceId = null)
    }

    fun showSnoozeAllDialog() {
        _uiState.value = _uiState.value.copy(snoozeOccurrenceId = SNOOZE_ALL)
    }

    fun showSnoozeDialog(occurrenceId: String) {
        _uiState.value = _uiState.value.copy(snoozeOccurrenceId = occurrenceId)
    }

    fun dismissSnoozeDialog() {
        _uiState.value = _uiState.value.copy(snoozeOccurrenceId = null)
    }

    fun dismissWarningDialog() {
        _uiState.value = _uiState.value.copy(warningOccurrenceId = null, warningTimeStr = null)
    }
}

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    companion object {
        const val ACTION_TAKE_NOTIFICATION = "app.allinonereminder.action.TAKE_NOTIFICATION"
    }

    private val viewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.allinonereminder.ui.components.AppTime.refresh(this)

        // Show over the lock screen and wake the display, like a clock alarm. Do NOT call
        // KeyguardManager.requestDismissKeyguard here: on a phone with a PIN/pattern it puts the
        // unlock prompt on top of the alarm, so the user had to unlock before seeing it.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val occurrenceIds = intent.getStringArrayExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS) ?: emptyArray()
        if (intent.action == ACTION_TAKE_NOTIFICATION) {
            viewModel.handleNotificationTake(occurrenceIds)
        } else {
            viewModel.loadOccurrences(occurrenceIds)
        }

        val initialThemeMode = kotlinx.coroutines.runBlocking { settingsRepository.themeModeFlow.first() }
        setContent {
            val themeMode by settingsRepository.themeModeFlow.collectAsState(initial = initialThemeMode)
            app.allinonereminder.ui.theme.AppTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.uiState.collectAsState()

                    if (state.finished) {
                        // Stop ringing service and finish
                        val serviceIntent = Intent(this@AlarmActivity, AlarmRingService::class.java)
                        stopService(serviceIntent)
                        finish()
                    } else {
                        AlarmScreenContent(
                            state = state,
                            onTaken = { id -> viewModel.onTakenClicked(id) },
                            onAllTaken = { viewModel.onAllTakenClicked() },
                            onSnooze = { id -> viewModel.showSnoozeDialog(id) },
                            onSnoozeAll = { viewModel.showSnoozeAllDialog() },
                            onSkip = { id -> viewModel.showSkipDialog(id) },
                            onNotEatenYet = { id, afterEating, minutes ->
                                viewModel.onNotEatenYetClicked(id, afterEating, minutes)
                            },
                            onStartingMealNow = { id -> viewModel.onStartingMealNowClicked(id) }
                        )

                        // Double-dose confirmation dialog
                        if (state.warningOccurrenceId != null) {
                            AlertDialog(
                                onDismissRequest = { viewModel.dismissWarningDialog() },
                                title = { Text("Double-Dose Warning") },
                                text = {
                                    Text("Last taken at ${state.warningTimeStr ?: "recently"}. Are you sure you want to log this dose again?")
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            state.warningOccurrenceId?.let {
                                                viewModel.onTakenClicked(it, force = true)
                                            }
                                        }
                                    ) {
                                        Text("Log Again")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { viewModel.dismissWarningDialog() }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        // Snooze Dialog
                        if (state.snoozeOccurrenceId != null) {
                            SnoozeOptionsDialog(
                                onDismiss = { viewModel.dismissSnoozeDialog() },
                                onSelect = { min ->
                                    state.snoozeOccurrenceId?.let { id ->
                                        if (id == SNOOZE_ALL) viewModel.onSnoozeAllClicked(min)
                                        else viewModel.onSnoozeClicked(id, min)
                                    }
                                }
                            )
                        }

                        // Skip Dialog
                        if (state.skipOccurrenceId != null) {
                            SkipReasonDialog(
                                onDismiss = { viewModel.dismissSkipDialog() },
                                onConfirm = { reason ->
                                    state.skipOccurrenceId?.let { id ->
                                        viewModel.onSkipClicked(id, reason)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlarmScreenContent(
    state: AlarmUiState,
    onTaken: (String) -> Unit,
    onAllTaken: () -> Unit,
    onSnooze: (String) -> Unit,
    onSnoozeAll: () -> Unit,
    onSkip: (String) -> Unit,
    onNotEatenYet: (String, Boolean, Int) -> Unit,
    onStartingMealNow: (String) -> Unit
) {
    val firstProfile = state.items.firstOrNull()?.profile
    val profileColor = firstProfile?.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header: the time, big, and who it is for, on the profile colour
            val headerText = app.allinonereminder.ui.components.readableTextColor(profileColor)
            val dueAt = state.items.minOfOrNull { it.occurrence.dueAt }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(profileColor)
                    .padding(top = 28.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
            ) {
                Text(
                    text = if (dueAt != null) {
                        java.time.Instant.ofEpochMilli(dueAt).atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.app.allinonereminder.ui.components.AppTime.formatter())
                    } else "",
                    color = headerText,
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    text = when {
                        firstProfile == null -> "Time for your reminder"
                        state.items.size > 1 -> "Time for ${firstProfile.name}'s reminders"
                        else -> "Time for ${firstProfile.name}'s reminder"
                    },
                    color = headerText.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.items.size > 1) {
                Text(
                    text = "${state.items.size} reminders due together",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val allActionLabel = if (state.items.all { it.item.type == ItemType.APPOINTMENT }) "All done" else "All taken"
                    Button(
                        onClick = onAllTaken,
                        modifier = Modifier
                            .weight(1.3f)
                            .defaultMinSize(minHeight = 52.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(allActionLabel, maxLines = 1, softWrap = false)
                    }
                    FilledTonalButton(
                        onClick = onSnoozeAll,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 52.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Snooze all", maxLines = 1, softWrap = false)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(state.items, key = { it.occurrence.id }) { uiModel ->
                    AlarmItemCard(
                        compact = state.items.size > 1,
                        uiModel = uiModel,
                        onTaken = { onTaken(uiModel.occurrence.id) },
                        onSnooze = { onSnooze(uiModel.occurrence.id) },
                        onSkip = { onSkip(uiModel.occurrence.id) },
                        onNotEatenYet = { afterEating, min ->
                            onNotEatenYet(uiModel.occurrence.id, afterEating, min)
                        },
                        onStartingMealNow = { onStartingMealNow(uiModel.occurrence.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun AlarmItemCard(
    compact: Boolean = false,
    uiModel: AlarmItemUiModel,
    onTaken: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onNotEatenYet: (Boolean, Int) -> Unit,
    onStartingMealNow: () -> Unit
) {
    var showMealDeferralMenu by remember { mutableStateOf(false) }

    val isAppointment = uiModel.item.type == ItemType.APPOINTMENT

    // Meal-linked reminders keep the full card so "Starting my meal now" / "I haven't eaten yet" stay available.
    if (compact && uiModel.schedule?.meal == null) {
        CompactAlarmRow(
            uiModel = uiModel,
            isAppointment = isAppointment,
            onTaken = onTaken,
            onSnooze = onSnooze,
            onSkip = onSkip
        )
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = uiModel.item.name,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            val doseDesc = if (isAppointment) {
                effectiveDoseAmount(uiModel.schedule, uiModel.item)
            } else {
                app.allinonereminder.model.doseLabel(uiModel.schedule, uiModel.item)
            }
            Text(
                text = doseDesc,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Meal context if available
            val sched = uiModel.schedule
            val meal = sched?.meal
            val relation = sched?.relation
            if (meal != null && relation != null) {
                val relationText = when (relation) {
                    MealRelation.BEFORE -> "${sched.offsetMinutes ?: 0} min before ${meal.name.lowercase()}"
                    MealRelation.WITH -> "With ${meal.name.lowercase()}"
                    MealRelation.AFTER -> "${sched.offsetMinutes ?: 0} min after ${meal.name.lowercase()}"
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = relationText,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            val instructions = uiModel.item.instructions
            if (!instructions.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = instructions,
                    fontSize = 15.sp,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main actions: one big primary button, then Snooze and Skip side by side.
            Button(
                onClick = onTaken,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isAppointment) "Got it" else "Taken", fontSize = 18.sp, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Snooze", maxLines = 1, softWrap = false)
                }

                if (!isAppointment) {
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Skip", maxLines = 1, softWrap = false)
                    }
                }
            }

            // Meal-specific quick actions
            if (sched?.meal != null) {
                Spacer(modifier = Modifier.height(8.dp))
                if (sched.relation == MealRelation.AFTER) {
                    TextButton(
                        onClick = { showMealDeferralMenu = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("I haven't eaten yet...")
                    }
                } else if (sched.relation == MealRelation.BEFORE || sched.relation == MealRelation.WITH) {
                    TextButton(
                        onClick = onStartingMealNow,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Starting my meal now")
                    }
                }
            }
        }
    }

    if (showMealDeferralMenu) {
        AlertDialog(
            onDismissRequest = { showMealDeferralMenu = false },
            title = { Text("Remind me when?") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showMealDeferralMenu = false
                            onNotEatenYet(true, 0)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("After I finish eating (Done notification)", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            showMealDeferralMenu = false
                            onNotEatenYet(false, 30)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("In 30 minutes", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            showMealDeferralMenu = false
                            onNotEatenYet(false, 45)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("In 45 minutes", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            showMealDeferralMenu = false
                            onNotEatenYet(false, 60)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("In 60 minutes", modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMealDeferralMenu = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SnoozeOptionsDialog(
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    app.allinonereminder.ui.components.SnoozeChoiceDialog(onDismiss = onDismiss, onSelect = onSelect)
}

@Composable
fun SkipReasonDialog(
    onDismiss: () -> Unit,
    onConfirm: (SkipReason) -> Unit
) {
    var selectedReason by remember { mutableStateOf(SkipReason.NOT_FEELING_WELL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reason for skipping") },
        text = {
            Column {
                SkipReason.values().forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedReason == reason),
                            onClick = { selectedReason = reason }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(reason.title, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedReason) }) {
                Text("Confirm Skip")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/** One reminder in a group: name and dose with a Taken button, Snooze and Skip underneath. */
@Composable
private fun CompactAlarmRow(
    uiModel: AlarmItemUiModel,
    isAppointment: Boolean,
    onTaken: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                app.allinonereminder.ui.components.TypeBadge(uiModel.item.type, size = 44.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(uiModel.item.name, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    val dose = if (isAppointment) {
                        effectiveDoseAmount(uiModel.schedule, uiModel.item)
                    } else {
                        app.allinonereminder.model.doseLabel(uiModel.schedule, uiModel.item)
                    }
                    if (dose.isNotBlank()) {
                        Text(dose, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onTaken,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isAppointment) "Got it" else "Taken", maxLines = 1, softWrap = false)
                }
            }
            Row(modifier = Modifier.padding(start = 48.dp)) {
                TextButton(onClick = onSnooze) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Snooze")
                }
                if (!isAppointment) {
                    TextButton(onClick = onSkip) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Skip")
                    }
                }
            }
        }
    }
}

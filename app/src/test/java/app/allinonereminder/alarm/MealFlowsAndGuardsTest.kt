package app.allinonereminder.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.allinonereminder.data.local.AppDatabase
import app.allinonereminder.data.local.ChangeLogWriter
import app.allinonereminder.data.repository.*
import app.allinonereminder.model.*
import app.allinonereminder.ui.today.TodayViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class MealFlowsAndGuardsTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var profileRepo: ProfileRepository
    private lateinit var itemRepo: ItemRepository
    private lateinit var scheduleRepo: ScheduleRepository
    private lateinit var mealAnchorRepo: MealAnchorRepository
    private lateinit var mealSessionRepo: MealSessionRepository
    private lateinit var occurrenceRepo: OccurrenceRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var coordinator: AlarmCoordinator

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val changeLogWriter = ChangeLogWriter(db.changeLogDao())
        profileRepo = ProfileRepository(
            profileDao = db.profileDao(),
            mealAnchorDao = db.mealAnchorDao(),
            itemDao = db.itemDao(),
            scheduleDao = db.scheduleDao(),
            database = db,
            changeLogWriter = changeLogWriter
        )
        itemRepo = ItemRepository(db.itemDao(), db.itemPhotoDao(), changeLogWriter)
        scheduleRepo = ScheduleRepository(db.scheduleDao(), changeLogWriter)
        mealAnchorRepo = MealAnchorRepository(db.mealAnchorDao(), changeLogWriter)
        mealSessionRepo = MealSessionRepository(db.mealSessionDao())
        occurrenceRepo = OccurrenceRepository(db.occurrenceDao())
        settingsRepo = SettingsRepository(context.dataStore)

        coordinator = AlarmCoordinator(
            context = context,
            itemRepository = itemRepo,
            scheduleRepository = scheduleRepo,
            mealAnchorRepository = mealAnchorRepo,
            mealSessionRepository = mealSessionRepo,
            occurrenceRepository = occurrenceRepo,
            settingsRepository = settingsRepo
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun mealDeferredSnooze_transitionsToDeferredMealAndReschedulesWhenMealEnds() = runBlocking {
        val profile = profileRepo.createProfile("Alice", 0xFF00FF, deviceId = "dev-1")
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()

        // Configure Lunch anchor at 13:00 (780 mins)
        mealAnchorRepo.updateAnchor(
            MealAnchor(
                id = UUID.randomUUID().toString(),
                profileId = profile.id,
                dayType = DayType.WEEKDAY,
                meal = MealType.LUNCH,
                minuteOfDay = 13 * 60,
                updatedAt = now
            ),
            "dev-1"
        )

        // Medicine scheduled 30m AFTER Lunch
        val itemId = UUID.randomUUID().toString()
        itemRepo.createItem(
            Item(
                id = itemId,
                profileId = profile.id,
                type = ItemType.MEDICINE,
                name = "Digestive Enzyme",
                dosageAmount = "1",
                dosageUnit = "tablet",
                criticality = Criticality.NORMAL,
                graceMinutes = 60,
                reRingMinutes = 5,
                timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
                homeZoneId = ZoneId.systemDefault().id,
                startDate = todayStr,
                createdAt = now,
                updatedAt = now
            ),
            "dev-1"
        )

        scheduleRepo.createSchedule(
            Schedule(
                id = "sched-lunch-after",
                itemId = itemId,
                anchorType = ScheduleAnchorType.MEAL,
                meal = MealType.LUNCH,
                relation = MealRelation.AFTER,
                offsetMinutes = 30,
                recurrence = RecurrenceType.DAILY,
                anchorDate = todayStr,
                createdAt = now,
                updatedAt = now
            ),
            "dev-1"
        )

        coordinator.reschedule("Initial setup")

        // 1. Check occurrence was planned
        val occurrences = occurrenceRepo.getUnresolvedSync().filter { it.itemId == itemId }
        assertThat(occurrences).isNotEmpty()
        val occ = occurrences.first()

        // 2. User taps "I haven't eaten yet" -> "After I finish eating"
        // Starts meal session and sets status to DEFERRED_MEAL with 90 min + offset fallback
        val session = mealSessionRepo.startMealSession(profile.id, MealType.LUNCH, todayStr, now)
        val fallbackDue = now + (90 + 30) * 60_000L
        // Same repository call the alarm screen uses: only remindAt moves, dueAt stays intended.
        occurrenceRepo.deferMealTargeted(occ.id, fallbackDue)

        val deferredOcc = occurrenceRepo.getByIdSync(occ.id)
        assertThat(deferredOcc?.status).isEqualTo(OccurrenceStatus.DEFERRED_MEAL)
        assertThat(deferredOcc?.remindAt).isEqualTo(fallbackDue)
        assertThat(deferredOcc?.dueAt).isEqualTo(occ.dueAt)

        // 3. User finishes lunch 45 minutes later
        val finishedAt = now + 45 * 60_000L
        mealSessionRepo.endMealSession(session.id, finishedAt)

        // Trigger NotificationActionReceiver logic for MEAL_DONE
        val receiver = NotificationActionReceiver().apply {
            this.occurrenceRepository = occurrenceRepo
            this.itemRepository = itemRepo
            this.scheduleRepository = scheduleRepo
            this.mealSessionRepository = mealSessionRepo
            this.settingsRepository = settingsRepo
            this.alarmCoordinator = coordinator
        }

        val mealDoneIntent = Intent(NotificationActionReceiver.ACTION_NOTIFICATION_MEAL_DONE).apply {
            putExtra(NotificationActionReceiver.EXTRA_SESSION_ID, session.id)
            putExtra(NotificationActionReceiver.EXTRA_PROFILE_ID, profile.id)
            putExtra(NotificationActionReceiver.EXTRA_MEAL_TYPE, MealType.LUNCH.name)
            putExtra(NotificationActionReceiver.EXTRA_FINISHED_AT, finishedAt)
        }
        receiver.handleAction(context, mealDoneIntent)

        // Verify occurrence is updated to PENDING with dueAt = finishedAt + 30m offset
        val updatedOcc = occurrenceRepo.getByIdSync(occ.id)
        assertThat(updatedOcc?.status).isEqualTo(OccurrenceStatus.PENDING)
        assertThat(updatedOcc?.dueAt).isAtLeast(finishedAt + 30 * 60_000L)
    }

    @Test
    fun grouping_simultaneousOccurrencesGroupedInSingleAlarm() = runBlocking {
        val profile = profileRepo.createProfile("Bob", 0x00FF00, deviceId = "dev-1")
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val dueTime = now + 60 * 60_000L

        // Two items due at exactly the same minute
        val item1 = Item(
            id = "item-1",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Aspirin",
            dosageAmount = "100",
            dosageUnit = "mg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        val item2 = Item(
            id = "item-2",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Vitamin C",
            dosageAmount = "500",
            dosageUnit = "mg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item1, "dev-1")
        itemRepo.createItem(item2, "dev-1")

        val schedTime = java.time.Instant.ofEpochMilli(dueTime).atZone(ZoneId.systemDefault()).toLocalTime()
        scheduleRepo.createSchedule(
            Schedule(
                id = "sched-1",
                itemId = item1.id,
                anchorType = ScheduleAnchorType.FIXED,
                minuteOfDay = schedTime.hour * 60 + schedTime.minute,
                recurrence = RecurrenceType.DAILY,
                anchorDate = todayStr,
                createdAt = now,
                updatedAt = now
            ),
            "dev-1"
        )
        scheduleRepo.createSchedule(
            Schedule(
                id = "sched-2",
                itemId = item2.id,
                anchorType = ScheduleAnchorType.FIXED,
                minuteOfDay = schedTime.hour * 60 + schedTime.minute,
                recurrence = RecurrenceType.DAILY,
                anchorDate = todayStr,
                createdAt = now,
                updatedAt = now
            ),
            "dev-1"
        )

        val occ1 = Occurrence(
            id = "occ-group-1",
            itemId = item1.id,
            scheduleId = "sched-1",
            profileId = profile.id,
            plannedAt = dueTime,
            dueAt = dueTime,
            status = OccurrenceStatus.PENDING,
            deviceId = "dev-1",
            updatedAt = now
        )
        val occ2 = Occurrence(
            id = "occ-group-2",
            itemId = item2.id,
            scheduleId = "sched-2",
            profileId = profile.id,
            plannedAt = dueTime,
            dueAt = dueTime,
            status = OccurrenceStatus.PENDING,
            deviceId = "dev-1",
            updatedAt = now
        )
        occurrenceRepo.upsertOccurrences(listOf(occ1, occ2))

        val result = coordinator.reschedule("Group test")
        assertThat(result.registered).isTrue()
        assertThat(result.occurrenceCount).isEqualTo(2)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        assertThat(scheduledAlarms).hasSize(1)
    }

    @Test
    fun missedMarking_marksOverdueOccurrenceAsMissed() = runBlocking {
        val profile = profileRepo.createProfile("Charlie", 0x0000FF, deviceId = "dev-1")
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()

        val item = Item(
            id = "item-missed",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Antibiotic",
            dosageAmount = "250",
            dosageUnit = "mg",
            criticality = Criticality.NORMAL,
            graceMinutes = 30, // 30 min grace
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item, "dev-1")

        // Occurrence was planned 90 minutes ago (well past 30m grace)
        val pastDue = now - 90 * 60_000L
        val overdueOcc = Occurrence(
            id = "occ-overdue",
            itemId = item.id,
            scheduleId = "sched-overdue",
            profileId = profile.id,
            plannedAt = pastDue,
            dueAt = pastDue,
            status = OccurrenceStatus.PENDING,
            deviceId = "dev-1",
            updatedAt = pastDue
        )
        occurrenceRepo.upsertOccurrences(listOf(overdueOcc))

        coordinator.reschedule("Missed check")

        val checkedOcc = occurrenceRepo.getByIdSync("occ-overdue")
        assertThat(checkedOcc?.status).isEqualTo(OccurrenceStatus.MISSED)
    }

    @Test
    fun doubleDoseGuard_blocksTakeFromNotificationAction() = runBlocking {
        val profile = profileRepo.createProfile("David", 0xFFAA00, deviceId = "dev-1")
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val itemId = "item-guard"

        val item = Item(
            id = itemId,
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Pain Reliever",
            dosageAmount = "400",
            dosageUnit = "mg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            minGapMinutes = 240, // 4 hours min gap
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item, "dev-1")

        // First dose taken 30 minutes ago
        val takenOcc = Occurrence(
            id = "occ-taken-earlier",
            itemId = itemId,
            scheduleId = "sched-1",
            profileId = profile.id,
            plannedAt = now - 30 * 60_000L,
            dueAt = now - 30 * 60_000L,
            status = OccurrenceStatus.TAKEN,
            actedAt = now - 30 * 60_000L,
            deviceId = "dev-1",
            updatedAt = now - 30 * 60_000L
        )
        occurrenceRepo.upsertOccurrences(listOf(takenOcc))

        // Second dose due now
        val nextOcc = Occurrence(
            id = "occ-due-now",
            itemId = itemId,
            scheduleId = "sched-2",
            profileId = profile.id,
            plannedAt = now,
            dueAt = now,
            status = OccurrenceStatus.PENDING,
            deviceId = "dev-1",
            updatedAt = now
        )
        occurrenceRepo.upsertOccurrences(listOf(nextOcc))

        val receiver = NotificationActionReceiver().apply {
            this.occurrenceRepository = occurrenceRepo
            this.itemRepository = itemRepo
            this.scheduleRepository = scheduleRepo
            this.mealSessionRepository = mealSessionRepo
            this.settingsRepository = settingsRepo
            this.alarmCoordinator = coordinator
        }

        // User taps "Taken" from notification action
        val takenIntent = Intent(NotificationActionReceiver.ACTION_NOTIFICATION_TAKEN).apply {
            putExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS, arrayOf("occ-due-now"))
        }
        receiver.handleAction(context, takenIntent)

        // Wait a moment for coroutine
        for (i in 0 until 50) {
            if (occurrenceRepo.getByIdSync("occ-due-now")?.status != OccurrenceStatus.PENDING) break
            Thread.sleep(10)
        }

        // Occurrence MUST NOT be marked TAKEN because guard intercepted it
        val currentOcc = occurrenceRepo.getByIdSync("occ-due-now")
        assertThat(currentOcc?.status).isEqualTo(OccurrenceStatus.PENDING)

        // Verification: NotificationActionReceiver launched AlarmActivity for confirmation
        val shadowApp: ShadowApplication = Shadows.shadowOf(ApplicationProvider.getApplicationContext() as android.app.Application)
        val startedIntent = shadowApp.nextStartedActivity
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent.component?.className).isEqualTo(AlarmActivity::class.java.name)
    }

    @Test
    fun startingMealNow_doesNotCallTakeForce_retimesAndGuardsDose() = runBlocking {
        val profile = profileRepo.createProfile("Alice", 0xFF00FF, deviceId = "dev-1")
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()

        val itemBefore = Item(
            id = "item-before-lunch",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Pre-Meal Insulin",
            dosageAmount = "5",
            dosageUnit = "units",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            minGapMinutes = 240, // 4 hours min gap
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        val itemAfter = Item(
            id = "item-after-lunch",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Digestive Enzyme",
            dosageAmount = "1",
            dosageUnit = "tablet",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(itemBefore, "dev-1")
        itemRepo.createItem(itemAfter, "dev-1")

        scheduleRepo.createSchedule(
            Schedule(
                id = "sched-before",
                itemId = itemBefore.id,
                anchorType = ScheduleAnchorType.MEAL,
                meal = MealType.LUNCH,
                relation = MealRelation.BEFORE,
                offsetMinutes = 15,
                recurrence = RecurrenceType.DAILY,
                anchorDate = todayStr,
                createdAt = now,
                updatedAt = now
            ),
            "dev-1"
        )
        scheduleRepo.createSchedule(
            Schedule(
                id = "sched-after",
                itemId = itemAfter.id,
                anchorType = ScheduleAnchorType.MEAL,
                meal = MealType.LUNCH,
                relation = MealRelation.AFTER,
                offsetMinutes = 30,
                recurrence = RecurrenceType.DAILY,
                anchorDate = todayStr,
                createdAt = now,
                updatedAt = now
            ),
            "dev-1"
        )

        // Dose taken 1 hour ago (within 240m minGap)
        val takenOcc = Occurrence(
            id = "occ-taken-1h-ago",
            itemId = itemBefore.id,
            scheduleId = "sched-before",
            profileId = profile.id,
            plannedAt = now - 60 * 60_000L,
            dueAt = now - 60 * 60_000L,
            status = OccurrenceStatus.TAKEN,
            actedAt = now - 60 * 60_000L,
            deviceId = "dev-1",
            updatedAt = now - 60 * 60_000L
        )
        occurrenceRepo.upsertOccurrences(listOf(takenOcc))

        // Current ringing occurrence for before-lunch
        val occRinging = Occurrence(
            id = "occ-ringing-before",
            itemId = itemBefore.id,
            scheduleId = "sched-before",
            profileId = profile.id,
            plannedAt = now,
            dueAt = now,
            status = OccurrenceStatus.RINGING,
            deviceId = "dev-1",
            updatedAt = now
        )
        // After-lunch occurrence planned for later
        val occAfter = Occurrence(
            id = "occ-future-after",
            itemId = itemAfter.id,
            scheduleId = "sched-after",
            profileId = profile.id,
            plannedAt = now + 90 * 60_000L,
            dueAt = now + 90 * 60_000L,
            status = OccurrenceStatus.PENDING,
            deviceId = "dev-1",
            updatedAt = now
        )
        occurrenceRepo.upsertOccurrences(listOf(occRinging, occAfter))

        val alarmViewModel = AlarmViewModel(
            context = context,
            occurrenceRepository = occurrenceRepo,
            itemRepository = itemRepo,
            profileRepository = profileRepo,
            scheduleRepository = scheduleRepo,
            mealSessionRepository = mealSessionRepo,
            alarmCoordinator = coordinator
        )
        alarmViewModel.loadOccurrences(arrayOf(occRinging.id))

        // Wait for occurrences to load in UI state
        for (i in 0 until 50) {
            testDispatcher.scheduler.advanceUntilIdle()
            ShadowLooper.idleMainLooper()
            if (alarmViewModel.uiState.value.items.isNotEmpty()) break
            Thread.sleep(10)
        }

        // Tapping "Starting my meal now" MUST NOT mark the dose TAKEN (§15, review A6)
        alarmViewModel.onStartingMealNowClicked(occRinging.id)
        testDispatcher.scheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        // 1. Verify item is NOT marked taken
        val occAfterStart = occurrenceRepo.getByIdSync(occRinging.id)
        assertThat(occAfterStart?.status).isNotEqualTo(OccurrenceStatus.TAKEN)

        // 2. Verify meal session is created
        val activeSession = mealSessionRepo.getActiveSessionSync(profile.id)
        assertThat(activeSession).isNotNull()
        assertThat(activeSession?.meal).isEqualTo(MealType.LUNCH)

        // 3. Verify after-meal item is re-timed from current meal start time (now + 30m offset)
        val reTimedAfter = occurrenceRepo.getByIdSync(occAfter.id)
        assertThat(reTimedAfter?.dueAt).isAtLeast(now + 30 * 60_000L - 1000L)

        // 4. Verify taking the item now triggers the double-dose warning guard because of 1h ago dose
        alarmViewModel.onTakenClicked(occRinging.id, force = false)
        for (i in 0 until 50) {
            testDispatcher.scheduler.advanceUntilIdle()
            ShadowLooper.idleMainLooper()
            if (alarmViewModel.uiState.value.warningOccurrenceId != null) break
            Thread.sleep(10)
        }
        assertThat(alarmViewModel.uiState.value.warningOccurrenceId).isEqualTo(occRinging.id)
        val occStillNotTaken = occurrenceRepo.getByIdSync(occRinging.id)
        assertThat(occStillNotTaken?.status).isNotEqualTo(OccurrenceStatus.TAKEN)
    }

    @Test
    fun notificationTaken_buildsActivityIntentOrEnforcesGuard() = runBlocking {
        val profile = profileRepo.createProfile("Bob", 0x00FF00, deviceId = "dev-1")
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()

        // 1. Verify notification builder creates PendingIntent.getActivity to AlarmActivity
        val notification = AlarmNotificationHelper.buildRingingNotification(
            context = context,
            occurrenceIds = arrayOf("occ-1"),
            title = "Test Alarm",
            content = "Take medicine",
            criticality = Criticality.NORMAL
        )
        assertThat(notification.actions).isNotNull()
        val actions = notification.actions!!
        assertThat(actions.size).isGreaterThan(0)
        val takenAction = actions.first { it.title?.toString() == "Taken" }
        assertThat(takenAction.actionIntent).isNotNull()
        // It uses getActivity per Android 12+ trampoline rules
        val shadowPendingIntent = Shadows.shadowOf(takenAction.actionIntent)
        assertThat(shadowPendingIntent.isActivity).isTrue()

        // 2. Verify unguarded path: AlarmViewModel handles ACTION_TAKE_NOTIFICATION and marks TAKEN
        val unguardedItem = Item(
            id = "item-unguarded",
            profileId = profile.id,
            type = ItemType.WATER_DRINK,
            name = "Water",
            dosageAmount = "250",
            dosageUnit = "ml",
            criticality = Criticality.GENTLE,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(unguardedItem, "dev-1")
        val occUnguarded = Occurrence(
            id = "occ-unguarded-take",
            itemId = unguardedItem.id,
            scheduleId = "sched-w",
            profileId = profile.id,
            plannedAt = now,
            dueAt = now,
            status = OccurrenceStatus.RINGING,
            deviceId = "dev-1",
            updatedAt = now
        )
        occurrenceRepo.upsertOccurrences(listOf(occUnguarded))

        val alarmViewModel = AlarmViewModel(
            context = context,
            occurrenceRepository = occurrenceRepo,
            itemRepository = itemRepo,
            profileRepository = profileRepo,
            scheduleRepository = scheduleRepo,
            mealSessionRepository = mealSessionRepo,
            alarmCoordinator = coordinator
        )
        alarmViewModel.handleNotificationTake(arrayOf(occUnguarded.id))

        for (i in 0 until 50) {
            testDispatcher.scheduler.advanceUntilIdle()
            ShadowLooper.idleMainLooper()
            if (alarmViewModel.uiState.value.finished) break
            Thread.sleep(10)
        }
        val takenOcc = occurrenceRepo.getByIdSync(occUnguarded.id)
        assertThat(takenOcc?.status).isEqualTo(OccurrenceStatus.TAKEN)
        assertThat(alarmViewModel.uiState.value.finished).isTrue()
    }

    @Test
    fun groupAlarm_takeOneItem_keepsRemainingItemsRingingWithoutMovingTheirReminder() = runBlocking {
        val profile = profileRepo.createProfile("Charlie", 0x0000FF, deviceId = "dev-1")
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()

        val item1 = Item(
            id = "item-group-1",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Med 1",
            dosageAmount = "1",
            dosageUnit = "tab",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        val item2 = Item(
            id = "item-group-2",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Med 2",
            dosageAmount = "1",
            dosageUnit = "tab",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 10,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item1, "dev-1")
        itemRepo.createItem(item2, "dev-1")

        val occ1 = Occurrence(
            id = "occ-grp-1",
            itemId = item1.id,
            scheduleId = "sched-1",
            profileId = profile.id,
            plannedAt = now - 5 * 60_000L,
            dueAt = now - 5 * 60_000L,
            status = OccurrenceStatus.RINGING,
            deviceId = "dev-1",
            updatedAt = now
        )
        val occ2 = Occurrence(
            id = "occ-grp-2",
            itemId = item2.id,
            scheduleId = "sched-2",
            profileId = profile.id,
            plannedAt = now - 5 * 60_000L,
            dueAt = now - 5 * 60_000L,
            status = OccurrenceStatus.RINGING,
            deviceId = "dev-1",
            updatedAt = now
        )
        occurrenceRepo.upsertOccurrences(listOf(occ1, occ2))

        val alarmViewModel = AlarmViewModel(
            context = context,
            occurrenceRepository = occurrenceRepo,
            itemRepository = itemRepo,
            profileRepository = profileRepo,
            scheduleRepository = scheduleRepo,
            mealSessionRepository = mealSessionRepo,
            alarmCoordinator = coordinator
        )
        alarmViewModel.loadOccurrences(arrayOf(occ1.id, occ2.id))

        for (i in 0 until 50) {
            testDispatcher.scheduler.advanceUntilIdle()
            ShadowLooper.idleMainLooper()
            if (alarmViewModel.uiState.value.items.size == 2) break
            Thread.sleep(10)
        }

        // Taking item1 must NOT push item2's reminder forward: item2 stays on screen and RINGING, and
        // the ring service re-rings any leftovers once, together, if the screen is left alone.
        alarmViewModel.onTakenClicked(occ1.id, force = true)

        for (i in 0 until 50) {
            testDispatcher.scheduler.advanceUntilIdle()
            ShadowLooper.idleMainLooper()
            if (alarmViewModel.uiState.value.items.size == 1) break
            Thread.sleep(10)
        }

        assertThat(occurrenceRepo.getByIdSync(occ1.id)?.status).isEqualTo(OccurrenceStatus.TAKEN)
        assertThat(alarmViewModel.uiState.value.finished).isFalse()

        // Occurrence 2 is untouched: still RINGING, same reminder time, same due time
        val remainingOcc = occurrenceRepo.getByIdSync(occ2.id)
        assertThat(remainingOcc?.status).isEqualTo(OccurrenceStatus.RINGING)
        assertThat(remainingOcc?.remindAt).isEqualTo(occ2.remindAt)
        assertThat(remainingOcc?.dueAt).isEqualTo(occ2.dueAt)
        assertThat(alarmViewModel.uiState.value.items.map { it.occurrence.id }).containsExactly(occ2.id)
    }

    @Test
    fun wrongPersonMeal_requiresExplicitProfileChoiceWhenAllSelected() = runBlocking {
        val mom = profileRepo.createProfile("Mom", 0xFF00FF, deviceId = "dev-1")
        val dad = profileRepo.createProfile("Dad", 0x00FF00, deviceId = "dev-1")
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()

        val dadItem = Item(
            id = "item-dad-lunch",
            profileId = dad.id,
            type = ItemType.MEDICINE,
            name = "Dad Medicine",
            dosageAmount = "1",
            dosageUnit = "tab",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(dadItem, "dev-1")
        scheduleRepo.createSchedule(
            Schedule(
                id = "sched-dad-lunch",
                itemId = dadItem.id,
                anchorType = ScheduleAnchorType.MEAL,
                meal = MealType.LUNCH,
                relation = MealRelation.AFTER,
                offsetMinutes = 30,
                recurrence = RecurrenceType.DAILY,
                anchorDate = todayStr,
                createdAt = now,
                updatedAt = now
            ),
            "dev-1"
        )

        val originalDue = now + 120 * 60_000L
        val dadOcc = Occurrence(
            id = "occ-dad-1",
            itemId = dadItem.id,
            scheduleId = "sched-dad-lunch",
            profileId = dad.id,
            plannedAt = originalDue,
            dueAt = originalDue,
            status = OccurrenceStatus.PENDING,
            deviceId = "dev-1",
            updatedAt = now
        )
        occurrenceRepo.upsertOccurrences(listOf(dadOcc))

        val changeLogWriter = ChangeLogWriter(db.changeLogDao())
        val todayViewModel = TodayViewModel(
            context = context,
            profileRepository = profileRepo,
            itemRepository = itemRepo,
            occurrenceRepository = occurrenceRepo,
            scheduleRepository = scheduleRepo,
            mealSessionRepository = mealSessionRepo,
            settingsRepository = settingsRepo,
            changeLogWriter = changeLogWriter,
            alarmCoordinator = coordinator
        )

        // Multiple profiles exist and "All" is selected: calling onStartMealNow without explicit profile choice does NOTHING
        todayViewModel.onStartMealNow(MealType.LUNCH, targetProfileId = null)
        testDispatcher.scheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        // No meal session created, Dad's dose NOT re-timed
        assertThat(mealSessionRepo.getActiveSessionSync(mom.id)).isNull()
        assertThat(mealSessionRepo.getActiveSessionSync(dad.id)).isNull()
        assertThat(occurrenceRepo.getByIdSync(dadOcc.id)?.dueAt).isEqualTo(originalDue)

        // When Mom explicitly starts lunch, Mom gets a session, Dad's medicines are NOT re-timed
        todayViewModel.onStartMealNow(MealType.LUNCH, targetProfileId = mom.id)
        testDispatcher.scheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()
        assertThat(mealSessionRepo.getActiveSessionSync(mom.id)).isNotNull()
        assertThat(occurrenceRepo.getByIdSync(dadOcc.id)?.dueAt).isEqualTo(originalDue)

        // When Dad explicitly starts lunch, Dad's medicines ARE re-timed
        todayViewModel.onStartMealNow(MealType.LUNCH, targetProfileId = dad.id)
        for (i in 0 until 50) {
            testDispatcher.scheduler.advanceUntilIdle()
            ShadowLooper.idleMainLooper()
            if (mealSessionRepo.getActiveSessionSync(dad.id) != null &&
                occurrenceRepo.getByIdSync(dadOcc.id)?.dueAt != originalDue) break
            Thread.sleep(10)
        }
        assertThat(mealSessionRepo.getActiveSessionSync(dad.id)).isNotNull()
        val reTimedDadOcc = occurrenceRepo.getByIdSync(dadOcc.id)
        assertThat(reTimedDadOcc?.dueAt).isLessThan(originalDue)
    }

}

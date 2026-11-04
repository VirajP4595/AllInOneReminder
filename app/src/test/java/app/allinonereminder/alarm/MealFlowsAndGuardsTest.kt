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
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
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

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val changeLogWriter = ChangeLogWriter(db.changeLogDao())
        profileRepo = ProfileRepository(db.profileDao(), db.mealAnchorDao(), changeLogWriter)
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
        occurrenceRepo.updateOccurrence(
            occ.copy(
                status = OccurrenceStatus.DEFERRED_MEAL,
                dueAt = fallbackDue,
                updatedAt = now
            )
        )

        val deferredOcc = occurrenceRepo.getByIdSync(occ.id)
        assertThat(deferredOcc?.status).isEqualTo(OccurrenceStatus.DEFERRED_MEAL)

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
}


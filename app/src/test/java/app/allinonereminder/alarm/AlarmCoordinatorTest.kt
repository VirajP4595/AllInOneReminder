package app.allinonereminder.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.allinonereminder.data.local.AppDatabase
import app.allinonereminder.data.local.ChangeLogWriter
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.MealAnchorRepository
import app.allinonereminder.data.repository.MealSessionRepository
import app.allinonereminder.data.repository.OccurrenceRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.ScheduleRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.data.repository.dataStore
import app.allinonereminder.model.Criticality
import app.allinonereminder.model.Item
import app.allinonereminder.model.ItemType
import app.allinonereminder.model.Occurrence
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.model.RecurrenceType
import app.allinonereminder.model.Schedule
import app.allinonereminder.model.ScheduleAnchorType
import app.allinonereminder.model.TimePolicy
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AlarmCoordinatorTest {

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
    fun alarmCoordinator_isIdempotent_andRegistersSingleAlarmClock() = runBlocking {
        val profile = profileRepo.createProfile("Mom", 0xFF0000, deviceId = "dev-1")
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val itemId = UUID.randomUUID().toString()

        val item = Item(
            id = itemId,
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Aspirin",
            dosageAmount = "100",
            dosageUnit = "mg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            minGapMinutes = 240,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        itemRepo.createItem(item, "dev-1")

        val schedule = Schedule(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = 12 * 60, // 12:00
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        scheduleRepo.createSchedule(schedule, "dev-1")

        // First run
        val res1 = coordinator.reschedule("test 1")
        assertThat(res1.registered).isTrue()

        // Second run (idempotent)
        val res2 = coordinator.reschedule("test 2")
        assertThat(res2.registered).isTrue()
        assertThat(res2.dueAtEpochMillis).isEqualTo(res1.dueAtEpochMillis)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAm = Shadows.shadowOf(alarmManager)
        assertThat(shadowAm.scheduledAlarms).isNotEmpty()
        val scheduledAlarm = shadowAm.scheduledAlarms.first()
        assertThat(scheduledAlarm.triggerAtTime).isEqualTo(res1.dueAtEpochMillis)
    }

    @Test
    fun alarmCoordinator_neverOverwritesActedOnOccurrences() = runBlocking {
        val profile = profileRepo.createProfile("Mom", 0xFF0000, deviceId = "dev-1")
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val itemId = UUID.randomUUID().toString()

        val item = Item(
            id = itemId,
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Thyroxine",
            dosageAmount = "50",
            dosageUnit = "mcg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        itemRepo.createItem(item, "dev-1")

        val schedule = Schedule(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = 7 * 60,
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        scheduleRepo.createSchedule(schedule, "dev-1")

        // Initial materialization
        coordinator.reschedule("init")

        val occurrences = occurrenceRepo.getUnresolvedSync()
        assertThat(occurrences).isNotEmpty()
        val targetOcc = occurrences.first()

        // Mark as TAKEN with actedAt
        val actedAt = System.currentTimeMillis()
        occurrenceRepo.updateOccurrence(
            targetOcc.copy(
                status = OccurrenceStatus.TAKEN,
                actedAt = actedAt,
                updatedAt = actedAt
            )
        )

        // Reschedule again
        coordinator.reschedule("reschedule after action")

        val afterReschedule = occurrenceRepo.getByIdSync(targetOcc.id)
        assertThat(afterReschedule?.status).isEqualTo(OccurrenceStatus.TAKEN)
        assertThat(afterReschedule?.actedAt).isEqualTo(actedAt)
    }

    @Test
    fun alarmCoordinator_marksOverdueUnresolvedRowsAsMissed() = runBlocking {
        val now = System.currentTimeMillis()
        val oldPlanned = now - 120 * 60_000L // 2 hours ago

        val profile = profileRepo.createProfile("Mom", 0xFF0000, deviceId = "dev-1")
        val itemId = UUID.randomUUID().toString()
        val item = Item(
            id = itemId,
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Insulin",
            dosageAmount = "10",
            dosageUnit = "units",
            criticality = Criticality.CRITICAL,
            graceMinutes = 60, // 60 min grace
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = "2026-01-01",
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item, "dev-1")

        val occ = Occurrence(
            id = "overdue-occ-1",
            itemId = itemId,
            scheduleId = "sched-old",
            profileId = profile.id,
            plannedAt = oldPlanned,
            dueAt = oldPlanned,
            status = OccurrenceStatus.PENDING,
            deviceId = "dev-1",
            updatedAt = oldPlanned
        )
        occurrenceRepo.upsertOccurrences(listOf(occ))

        // Running coordinator
        coordinator.reschedule("check missed")

        val updated = occurrenceRepo.getByIdSync("overdue-occ-1")
        assertThat(updated?.status).isEqualTo(OccurrenceStatus.MISSED)
    }
}


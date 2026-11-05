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
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.first
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
        db.close()
    }

    /**
     * Wall-clock minute-of-day [offsetMinutes] from now. Tests must not hard-code a clock time:
     * CI runs at arbitrary hours, so "22:00 (future today)" fails whenever it runs near 22:00.
     */
    private fun minuteFromNow(offsetMinutes: Int): Int =
        (java.time.LocalTime.now(ZoneId.systemDefault()).toSecondOfDay() / 60 + offsetMinutes + 1440) % 1440

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
            minuteOfDay = minuteFromNow(240), // 4 h from now
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
            minuteOfDay = minuteFromNow(180),
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

    @Test
    fun reconcile_editScheduleTime_supersedesOldOccurrence_createsNewOccurrence_setsOneAlarm() = runBlocking {
        val profile = profileRepo.createProfile("Mom", 0xFF0000, deviceId = "dev-1")
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val item = Item(
            id = "item-edit-time",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Statin",
            dosageAmount = "20",
            dosageUnit = "mg",
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

        val schedule1 = Schedule(
            id = "sched-edit-1",
            itemId = item.id,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = minuteFromNow(180), // 3 h from now
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        scheduleRepo.createSchedule(schedule1, "dev-1")

        // 1. Initial schedule at 22:00
        coordinator.reschedule("initial 22:00")
        val occsInitial = occurrenceRepo.getUnresolvedSync()
        assertThat(occsInitial).isNotEmpty()
        val oldFirstId = occsInitial.first().id

        // 2. User edits schedule time to 23:00 (23 * 60)
        scheduleRepo.updateSchedule(schedule1.copy(minuteOfDay = minuteFromNow(240)), "dev-1")

        // 3. Reconcile
        val res = coordinator.reschedule("edit time to 23:00")
        assertThat(res.registered).isTrue()

        // 4. Old row must be marked SUPERSEDED, not PENDING/ringing
        val oldRow = occurrenceRepo.getByIdSync(oldFirstId)
        assertThat(oldRow?.status).isEqualTo(OccurrenceStatus.SUPERSEDED)

        // 5. Unresolved occurrences now only have the 23:00 plan
        val newUnresolved = occurrenceRepo.getUnresolvedSync()
        for (occ in newUnresolved) {
            assertThat(occ.status).isEqualTo(OccurrenceStatus.PENDING)
            assertThat(occ.id).isNotEqualTo(oldFirstId)
        }
    }

    @Test
    fun reconcile_deleteItem_supersedesOccurrences_cancelsAlarm() = runBlocking {
        val profile = profileRepo.createProfile("Mom", 0xFF0000, deviceId = "dev-1")
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val item = Item(
            id = "item-to-delete",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Antibiotic",
            dosageAmount = "500",
            dosageUnit = "mg",
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
            id = "sched-to-del",
            itemId = item.id,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = minuteFromNow(180),
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        scheduleRepo.createSchedule(schedule, "dev-1")

        // Initial schedule
        val resInit = coordinator.reschedule("initial")
        assertThat(resInit.registered).isTrue()
        val unresolvedBefore = occurrenceRepo.getUnresolvedSync()
        assertThat(unresolvedBefore).isNotEmpty()

        // Delete item
        itemRepo.deleteItem(item.id, "dev-1")

        // Reconcile
        val resAfter = coordinator.reschedule("after item delete")
        assertThat(resAfter.registered).isFalse()

        // Unresolved rows are now empty (marked SUPERSEDED)
        val unresolvedAfter = occurrenceRepo.getUnresolvedSync()
        assertThat(unresolvedAfter).isEmpty()

        for (occ in unresolvedBefore) {
            val inDb = occurrenceRepo.getByIdSync(occ.id)
            assertThat(inDb?.status).isEqualTo(OccurrenceStatus.SUPERSEDED)
        }
    }

    @Test
    fun reconcile_deleteProfile_supersedesOccurrences_cancelsAlarm() = runBlocking {
        val profile = profileRepo.createProfile("Alice", 0xFF0000, deviceId = "dev-1")
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val item = Item(
            id = "item-alice",
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
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        itemRepo.createItem(item, "dev-1")

        val schedule = Schedule(
            id = "sched-alice",
            itemId = item.id,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = minuteFromNow(180),
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        scheduleRepo.createSchedule(schedule, "dev-1")

        coordinator.reschedule("init")
        assertThat(occurrenceRepo.getUnresolvedSync()).isNotEmpty()

        // Delete profile (cascades to items and schedules)
        profileRepo.deleteProfile(profile.id, "dev-1")

        val res = coordinator.reschedule("after profile delete")
        assertThat(res.registered).isFalse()
        assertThat(occurrenceRepo.getUnresolvedSync()).isEmpty()
    }

    @Test
    fun reconcile_mealAnchorChange_movesOccurrencesToNewTime() = runBlocking {
        val profile = profileRepo.createProfile("Bob", 0xFF0000, deviceId = "dev-1")
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val item = Item(
            id = "item-meal-anchored",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Acid Reflux Med",
            dosageAmount = "40",
            dosageUnit = "mg",
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

        // 30 min before breakfast
        val schedule = Schedule(
            id = "sched-meal-anchor",
            itemId = item.id,
            anchorType = ScheduleAnchorType.MEAL,
            minuteOfDay = null,
            meal = app.allinonereminder.model.MealType.BREAKFAST,
            relation = app.allinonereminder.model.MealRelation.BEFORE,
            offsetMinutes = 30,
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        scheduleRepo.createSchedule(schedule, "dev-1")

        // 1. Initial Breakfast anchor at 08:00 (480 min)
        // Update the breakfast anchor for every day type, so the planned row is covered whichever
        // day it falls on. Offset is BEFORE 30 min, so the occurrence is ~3 h from now.
        val breakfastAnchors = mealAnchorRepo.getAnchorsSync(profile.id).filter {
            it.meal == app.allinonereminder.model.MealType.BREAKFAST
        }
        breakfastAnchors.forEach {
            mealAnchorRepo.updateAnchor(it.copy(minuteOfDay = minuteFromNow(210)), "dev-1")
        }

        coordinator.reschedule("initial anchor 08:00")
        val occs1 = occurrenceRepo.getUnresolvedSync()
        val oldId = occs1.first().id

        // 2. Change Breakfast anchor to 09:30 (570 min)
        breakfastAnchors.forEach {
            mealAnchorRepo.updateAnchor(it.copy(minuteOfDay = minuteFromNow(270)), "dev-1")
        }

        // 3. Reconcile
        coordinator.reschedule("anchor changed to 09:30")

        // Old row is marked SUPERSEDED
        val oldOcc = occurrenceRepo.getByIdSync(oldId)
        assertThat(oldOcc?.status).isEqualTo(OccurrenceStatus.SUPERSEDED)

        // New rows are active at 09:00 (09:30 - 30 min)
        val newOccs = occurrenceRepo.getUnresolvedSync()
        assertThat(newOccs).isNotEmpty()
        assertThat(newOccs.first().id).isNotEqualTo(oldId)
    }

    /** Builds an item with 60 min grace and one unresolved occurrence at the given state. */
    private suspend fun seedOccurrence(
        occId: String,
        plannedMinutesAgo: Long,
        remindInMinutes: Long,
        status: OccurrenceStatus,
    ) {
        val now = System.currentTimeMillis()
        val plannedAt = now - plannedMinutesAgo * 60_000L
        val profile = profileRepo.createProfile("Mom", 0xFF0000, deviceId = "dev-1")
        val item = Item(
            id = "item-$occId",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Blood Pressure",
            dosageAmount = "10",
            dosageUnit = "mg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = "2026-01-01",
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item, "dev-1")
        occurrenceRepo.upsertOccurrences(
            listOf(
                Occurrence(
                    id = occId,
                    itemId = item.id,
                    scheduleId = "s-$occId",
                    profileId = profile.id,
                    plannedAt = plannedAt,
                    dueAt = plannedAt, // the intended time never moves
                    remindAt = now + remindInMinutes * 60_000L, // only the next ring moves
                    status = status,
                    ringCount = 1,
                    deviceId = "dev-1",
                    updatedAt = now
                )
            )
        )
    }

    @Test
    fun snoozeWithinGrace_staysSnoozed_andIsNotFalselyMissed() = runBlocking {
        seedOccurrence("snz-in", plannedMinutesAgo = 30, remindInMinutes = 10, status = OccurrenceStatus.SNOOZED)

        coordinator.reschedule("within grace")

        assertThat(occurrenceRepo.getByIdSync("snz-in")?.status).isEqualTo(OccurrenceStatus.SNOOZED)
    }

    @Test
    fun snoozePastGrace_isMissedFromTheIntendedDueTime_notFromTheSnoozeTime() = runBlocking {
        // Intended 70 min ago, grace 60: snoozing must not buy extra time.
        seedOccurrence("snz-out", plannedMinutesAgo = 70, remindInMinutes = 10, status = OccurrenceStatus.SNOOZED)

        coordinator.reschedule("past grace")

        assertThat(occurrenceRepo.getByIdSync("snz-out")?.status).isEqualTo(OccurrenceStatus.MISSED)
    }

    @Test
    fun mealDeferralWithinGrace_staysDeferred_andIsNotFalselyMissed() = runBlocking {
        seedOccurrence("def-in", plannedMinutesAgo = 20, remindInMinutes = 30, status = OccurrenceStatus.DEFERRED_MEAL)

        coordinator.reschedule("deferred within grace")

        assertThat(occurrenceRepo.getByIdSync("def-in")?.status).isEqualTo(OccurrenceStatus.DEFERRED_MEAL)
    }

    @Test
    fun mealDeferralPastGrace_isMissedFromTheIntendedDueTime() = runBlocking {
        seedOccurrence("def-out", plannedMinutesAgo = 80, remindInMinutes = 30, status = OccurrenceStatus.DEFERRED_MEAL)

        coordinator.reschedule("deferred past grace")

        assertThat(occurrenceRepo.getByIdSync("def-out")?.status).isEqualTo(OccurrenceStatus.MISSED)
    }

    @Test
    fun falseMissed_localWallClockZoneChange_updatesPlannedAtAndNotMarkedMissed() = runBlocking {
        val now = System.currentTimeMillis()
        val profile = profileRepo.createProfile("Mom", 0xFF0000, deviceId = "dev-1")
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val item = Item(
            id = "item-zone-change",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Thyroid Med",
            dosageAmount = "100",
            dosageUnit = "mcg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = "Asia/Kolkata",
            startDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item, "dev-1")

        val schedule = Schedule(
            id = "sched-zone",
            itemId = item.id,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = minuteFromNow(180),
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        scheduleRepo.createSchedule(schedule, "dev-1")

        // First plan in local zone
        coordinator.reschedule("initial local")
        val occs = occurrenceRepo.getUnresolvedSync()
        val target = occs.first()

        // Re-planning updates plannedAt and dueAt without falsely expiring
        coordinator.reschedule("re-plan after zone update")
        val rePlanned = occurrenceRepo.getByIdSync(target.id)
        assertThat(rePlanned?.status).isNotEqualTo(OccurrenceStatus.MISSED)
    }

    @Test
    fun reschedule_clampsTriggerTimeToNotBeInThePast() = runBlocking {
        val now = System.currentTimeMillis()
        // An occurrence whose dueAt is slightly in the past (e.g. 5 seconds ago) but within grace
        val pastDue = now - 5000L

        val profile = profileRepo.createProfile("Mom", 0xFF0000, deviceId = "dev-1")
        val item = Item(
            id = "item-clamp-test",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Pain Relief",
            dosageAmount = "1",
            dosageUnit = "pill",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = "2026-01-01",
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item, "dev-1")

        val occ = Occurrence(
            id = "occ-past-due",
            itemId = item.id,
            scheduleId = "s-clamp",
            profileId = profile.id,
            plannedAt = pastDue,
            dueAt = pastDue,
            status = OccurrenceStatus.PENDING,
            deviceId = "dev-1",
            updatedAt = now
        )
        occurrenceRepo.upsertOccurrences(listOf(occ))

        val result = coordinator.reschedule("test clamping")
        assertThat(result.registered).isTrue()
        // Clamped trigger time must be >= now, NEVER earlier than now
        assertThat(result.dueAtEpochMillis).isAtLeast(now)
    }

    @Test
    fun snooze10Min_reschedule_preservesSnoozeAndRegistersAlarmForSnoozeTime() = runBlocking {
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val profile = profileRepo.createProfile("Dad", 0x00FF00, deviceId = "dev-1")
        val item = Item(
            id = "item-snooze",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Blood Pressure",
            dosageAmount = "5",
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
        itemRepo.createItem(item, "dev-1")
        val schedule = Schedule(
            id = "sched-snooze",
            itemId = item.id,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = minuteFromNow(180), // 3 h from now
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        scheduleRepo.createSchedule(schedule, "dev-1")

        // Initial plan
        coordinator.reschedule("initial")
        val occs = occurrenceRepo.getUnresolvedSync()
        val occ = occs.first()

        // User snoozes for 10 minutes
        val snoozeTarget = now + 10 * 60_000L
        // The same repository call every snooze surface uses (alarm screen, notification, Today)
        assertThat(occurrenceRepo.snoozeTargeted(occ.id, snoozeTarget)).isTrue()

        // Reschedule must NOT revert the snooze, and must register the alarm at the snooze time!
        val res = coordinator.reschedule("after snooze")
        val after = occurrenceRepo.getByIdSync(occ.id)

        assertThat(after?.status).isEqualTo(OccurrenceStatus.SNOOZED)
        assertThat(after?.dueAt).isEqualTo(occ.dueAt) // intended time unchanged
        assertThat(after?.remindAt).isEqualTo(snoozeTarget)
        assertThat(res.dueAtEpochMillis).isEqualTo(snoozeTarget)
    }

    @Test
    fun autoSilence_nextRegisteredAlarmIsNowPlusReRingMinutes() = runBlocking {
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val profile = profileRepo.createProfile("Dad", 0x00FF00, deviceId = "dev-1")
        val item = Item(
            id = "item-autosilence",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Heart Pill",
            dosageAmount = "10",
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
        itemRepo.createItem(item, "dev-1")
        val schedule = Schedule(
            id = "sched-autosilence",
            itemId = item.id,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = minuteFromNow(180),
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        scheduleRepo.createSchedule(schedule, "dev-1")

        coordinator.reschedule("initial")
        val occ = occurrenceRepo.getUnresolvedSync().first()

        // Auto-silence rings and moves remindAt = now + 5 min, increments ringCount, keeps dueAt
        val nextRemind = now + 5 * 60_000L
        occurrenceRepo.updateOccurrence(
            occ.copy(
                status = OccurrenceStatus.PENDING,
                remindAt = nextRemind,
                ringCount = 1,
                updatedAt = now
            )
        )

        val res = coordinator.reschedule("after auto-silence")
        val updated = occurrenceRepo.getByIdSync(occ.id)
        assertThat(updated?.ringCount).isEqualTo(1)
        assertThat(updated?.remindAt).isEqualTo(nextRemind)
        // Registered alarm is nextRemind (now + 5 min), NOT now!
        assertThat(res.dueAtEpochMillis).isEqualTo(nextRemind)
    }

    @Test
    fun repeatedReRings_markedMissedExactlyAfterMaxPlannedDuePlusGrace() = runBlocking {
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val profile = profileRepo.createProfile("Dad", 0x00FF00, deviceId = "dev-1")
        val item = Item(
            id = "item-rering-missed",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Antibiotic",
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
        itemRepo.createItem(item, "dev-1")
        val schedule = Schedule(
            id = "sched-rering-missed",
            itemId = item.id,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = minuteFromNow(180),
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        scheduleRepo.createSchedule(schedule, "dev-1")

        coordinator.reschedule("initial")
        val occ = occurrenceRepo.getUnresolvedSync().first()

        // Planned at now - 70 min, dueAt at now - 70 min.
        // Even if re-ring updated remindAt to now + 5 min, grace is measured from max(plannedAt, dueAt)!
        // Since now > (now - 70 min) + 60 min, it MUST be marked MISSED!
        occurrenceRepo.updateOccurrence(
            occ.copy(
                plannedAt = now - 70 * 60_000L,
                dueAt = now - 70 * 60_000L,
                remindAt = now + 5 * 60_000L,
                ringCount = 3,
                updatedAt = now
            )
        )

        coordinator.reschedule("check missed after grace expires")
        val checked = occurrenceRepo.getByIdSync(occ.id)
        assertThat(checked?.status).isEqualTo(OccurrenceStatus.MISSED)
    }

    @Test
    fun defer30Min_survivesReschedule() = runBlocking {
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val profile = profileRepo.createProfile("Dad", 0x00FF00, deviceId = "dev-1")
        val item = Item(
            id = "item-defer-survive",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Insulin",
            dosageAmount = "10",
            dosageUnit = "units",
            criticality = Criticality.CRITICAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item, "dev-1")
        val schedule = Schedule(
            id = "sched-defer-survive",
            itemId = item.id,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = minuteFromNow(180),
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        scheduleRepo.createSchedule(schedule, "dev-1")

        coordinator.reschedule("initial")
        val occ = occurrenceRepo.getUnresolvedSync().first()

        // User taps "I haven't eaten yet -> in 30 min"
        val deferredDue = now + 30 * 60_000L
        assertThat(occurrenceRepo.deferMealTargeted(occ.id, deferredDue)).isTrue()

        val res = coordinator.reschedule("after deferral")
        val updated = occurrenceRepo.getByIdSync(occ.id)
        assertThat(updated?.status).isEqualTo(OccurrenceStatus.DEFERRED_MEAL)
        assertThat(updated?.dueAt).isEqualTo(occ.dueAt)
        assertThat(updated?.remindAt).isEqualTo(deferredDue)
        assertThat(res.dueAtEpochMillis).isEqualTo(deferredDue)
    }

    @Test
    fun partialGroup_remainingItemsNextRingIsNowPlusReRingMinutesAfterReschedule() = runBlocking {
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val profile = profileRepo.createProfile("Dad", 0x00FF00, deviceId = "dev-1")
        val item1 = Item(
            id = "item-group-1",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Pill A",
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
        val item2 = Item(
            id = "item-group-2",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Pill B",
            dosageAmount = "2",
            dosageUnit = "tablets",
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

        val schedule1 = Schedule(
            id = "sched-group-1",
            itemId = item1.id,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = minuteFromNow(180),
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        val schedule2 = Schedule(
            id = "sched-group-2",
            itemId = item2.id,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = minuteFromNow(180),
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        scheduleRepo.createSchedule(schedule1, "dev-1")
        scheduleRepo.createSchedule(schedule2, "dev-1")

        coordinator.reschedule("initial group")
        val occs = occurrenceRepo.getUnresolvedSync()
        val occ1 = occs.first { it.itemId == item1.id }
        val occ2 = occs.first { it.itemId == item2.id }

        // User acts on item1 (TAKEN). Remaining item2 has remindAt moved to now + 5 min
        val nextRing = now + 5 * 60_000L
        occurrenceRepo.updateOccurrence(occ1.copy(status = OccurrenceStatus.TAKEN, actedAt = now, updatedAt = now))
        occurrenceRepo.updateOccurrence(occ2.copy(status = OccurrenceStatus.PENDING, remindAt = nextRing, ringCount = 1, updatedAt = now))

        val res = coordinator.reschedule("after item1 taken")
        val updated2 = occurrenceRepo.getByIdSync(occ2.id)
        assertThat(updated2?.status).isEqualTo(OccurrenceStatus.PENDING)
        assertThat(updated2?.remindAt).isEqualTo(nextRing)
        assertThat(res.dueAtEpochMillis).isEqualTo(nextRing)
    }

    @Test
    fun snoozedDose_editScheduleTime_supersedesOldOccurrence_setsOneAlarmOnly() = runBlocking {
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val profile = profileRepo.createProfile("Mom", 0xFF0000, deviceId = "dev-1")
        val item = Item(
            id = "item-snooze-edit",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Thyroid",
            dosageAmount = "50",
            dosageUnit = "mcg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item, "dev-1")

        val schedule = Schedule(
            id = "sched-snooze-edit",
            itemId = item.id,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = minuteFromNow(180),
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = now,
            updatedAt = now
        )
        scheduleRepo.createSchedule(schedule, "dev-1")

        coordinator.reschedule("initial")
        val oldOcc = occurrenceRepo.getUnresolvedSync().first()

        // Dose is currently snoozed with past plannedAt
        val pastPlanned = now - 20 * 60_000L
        val snoozeTime = now + 10 * 60_000L
        occurrenceRepo.updateOccurrence(
            oldOcc.copy(
                status = OccurrenceStatus.SNOOZED,
                plannedAt = pastPlanned,
                dueAt = pastPlanned,
                remindAt = snoozeTime,
                ringCount = 1,
                updatedAt = now
            )
        )

        // User edits schedule time to 23:00 (which sets schedule.updatedAt = now + 1000)
        scheduleRepo.updateSchedule(schedule.copy(minuteOfDay = minuteFromNow(240), updatedAt = now + 1000L), "dev-1")

        val res = coordinator.reschedule("edit schedule while snoozed")
        assertThat(res.registered).isTrue()

        // The old snoozed occurrence must be marked SUPERSEDED!
        val supersededRow = occurrenceRepo.getByIdSync(oldOcc.id)
        assertThat(supersededRow?.status).isEqualTo(OccurrenceStatus.SUPERSEDED)

        // Only the new 23:00 occurrence remains unresolved!
        val unresolved = occurrenceRepo.getUnresolvedSync()
        assertThat(unresolved.none { it.id == oldOcc.id }).isTrue()
    }

    @Test
    fun forcedRepositoryException_recordsErrorInSettings() = runBlocking {
        // Create an AlarmCoordinator with a throwing ItemRepository
        val throwingCoordinator = AlarmCoordinator(
            context = context,
            itemRepository = object : ItemRepository(db.itemDao(), db.itemPhotoDao(), ChangeLogWriter(db.changeLogDao())) {
                override suspend fun getAllActiveItemsSync(): List<Item> {
                    throw IllegalStateException("Forced database failure")
                }
            },
            scheduleRepository = scheduleRepo,
            mealAnchorRepository = mealAnchorRepo,
            mealSessionRepository = mealSessionRepo,
            occurrenceRepository = occurrenceRepo,
            settingsRepository = settingsRepo
        )

        try {
            throwingCoordinator.reschedule("force error")
            assertWithMessage("Expected exception was not thrown").fail()
        } catch (e: IllegalStateException) {
            assertThat(e.message).isEqualTo("Forced database failure")
        }

        val recordedError = settingsRepo.lastRescheduleErrorFlow.first()
        assertThat(recordedError).isNotNull()
        assertThat(recordedError).contains("IllegalStateException")
    }
}


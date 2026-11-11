package app.allinonereminder.ui

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.data.local.AppDatabase
import app.allinonereminder.data.local.ChangeLogWriter
import app.allinonereminder.data.repository.*
import app.allinonereminder.model.*
import app.allinonereminder.ui.items.ItemsViewModel
import app.allinonereminder.ui.today.OccurrenceUiModel
import app.allinonereminder.ui.today.AsNeededUiModel
import app.allinonereminder.ui.today.TodayViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TodayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var profileRepo: ProfileRepository
    private lateinit var itemRepo: ItemRepository
    private lateinit var scheduleRepo: ScheduleRepository
    private lateinit var mealAnchorRepo: MealAnchorRepository
    private lateinit var mealSessionRepo: MealSessionRepository
    private lateinit var occurrenceRepo: OccurrenceRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var changeLogWriter: ChangeLogWriter
    private lateinit var coordinator: AlarmCoordinator

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        changeLogWriter = ChangeLogWriter(db.changeLogDao())
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
        Dispatchers.resetMain()
    }

    @Test
    fun itemsViewModel_formatSchedulesSummary_producesCorrectPlainEnglish() {
        val schedule1 = Schedule(
            id = "s-1",
            itemId = "i-1",
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = 8 * 60, // 08:00 AM
            recurrence = RecurrenceType.DAILY,
            anchorDate = "2026-01-01",
            createdAt = 0L,
            updatedAt = 0L
        )
        val schedule2 = Schedule(
            id = "s-2",
            itemId = "i-1",
            anchorType = ScheduleAnchorType.MEAL,
            meal = MealType.DINNER,
            relation = MealRelation.BEFORE,
            offsetMinutes = 30,
            recurrence = RecurrenceType.WEEKDAYS,
            anchorDate = "2026-01-01",
            createdAt = 0L,
            updatedAt = 0L
        )

        val summary = ItemsViewModel.formatSchedulesSummary(listOf(schedule1, schedule2))
        assertThat(summary).contains("8:00 AM daily")
        assertThat(summary).contains("30 min before dinner on weekdays")
    }

    @Test
    fun todayViewModel_doubleDoseGuard_warnsWhenDoseTakenWithinMinGap() = runBlocking {
        val profile = profileRepo.createProfile("Mom", 0xFF0000, deviceId = "dev-1")
        val now = System.currentTimeMillis()
        val itemId = UUID.randomUUID().toString()

        val item = Item(
            id = itemId,
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Paracetamol",
            dosageAmount = "500",
            dosageUnit = "mg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            minGapMinutes = 240, // 4 hours
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = LocalDate.now(ZoneId.systemDefault()).toString(),
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item, "dev-1")

        // First occurrence taken 30 minutes ago
        val takenOcc = Occurrence(
            id = "occ-1",
            itemId = itemId,
            scheduleId = "sched-1",
            profileId = profile.id,
            plannedAt = now - 35 * 60_000L,
            dueAt = now - 35 * 60_000L,
            status = OccurrenceStatus.TAKEN,
            actedAt = now - 30 * 60_000L,
            deviceId = "dev-1",
            updatedAt = now - 30 * 60_000L
        )
        occurrenceRepo.upsertOccurrences(listOf(takenOcc))

        // Second occurrence due now
        val nextOcc = Occurrence(
            id = "occ-2",
            itemId = itemId,
            scheduleId = "sched-1",
            profileId = profile.id,
            plannedAt = now,
            dueAt = now,
            status = OccurrenceStatus.PENDING,
            deviceId = "dev-1",
            updatedAt = now
        )
        occurrenceRepo.upsertOccurrences(listOf(nextOcc))

        val viewModel = TodayViewModel(
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

        val collectJob = launch { viewModel.uiState.collect { } }
        testDispatcher.scheduler.runCurrent()

        val uiModel = OccurrenceUiModel(
            occurrence = nextOcc,
            item = item,
            profile = profile,
            schedule = null
        )

        // Attempting to take should trigger warning without updating status
        viewModel.onTakeClicked(uiModel, force = false)
        var warning: OccurrenceUiModel? = null
        for (i in 0 until 50) {
            testDispatcher.scheduler.runCurrent()
            warning = viewModel.doubleDoseWarning.value
            if (warning != null) break
            Thread.sleep(20)
        }

        assertThat(warning).isNotNull()
        assertThat(warning?.item?.id).isEqualTo(itemId)

        val checkStatus = occurrenceRepo.getByIdSync("occ-2")
        assertThat(checkStatus?.status).isEqualTo(OccurrenceStatus.PENDING)

        // Taking with force = true confirms and marks TAKEN
        viewModel.onTakeClicked(uiModel, force = true)
        for (i in 0 until 50) {
            testDispatcher.scheduler.runCurrent()
            if (viewModel.doubleDoseWarning.value == null && occurrenceRepo.getByIdSync("occ-2")?.status == OccurrenceStatus.TAKEN) break
            Thread.sleep(20)
        }
        assertThat(viewModel.doubleDoseWarning.value).isNull()

        val updatedStatus = occurrenceRepo.getByIdSync("occ-2")
        assertThat(updatedStatus?.status).isEqualTo(OccurrenceStatus.TAKEN)

        collectJob.cancel()
    }

    @Test
    fun deviceId_writtenToChangeLog_matchesSettingsRepositoryDeviceId() = runBlocking {
        val expectedDeviceId = settingsRepo.getOrCreateDeviceId()
        assertThat(expectedDeviceId).isNotEmpty()

        val profile = profileRepo.createProfile("Alice", 0x00FF00, deviceId = expectedDeviceId)
        val logs = db.changeLogDao().getAllSync()
        val profileLog = logs.firstOrNull { it.entityType == "profile" && it.entityId == profile.id }

        assertThat(profileLog).isNotNull()
        assertThat(profileLog?.deviceId).isEqualTo(expectedDeviceId)
    }

    @Test
    fun todayViewModel_actionOnRingingItem_marksTakenAndStopsService() = runBlocking {
        val expectedDeviceId = settingsRepo.getOrCreateDeviceId()
        val profile = profileRepo.createProfile("Bob", 0x0000FF, deviceId = expectedDeviceId)
        val now = System.currentTimeMillis()
        val item = Item(
            id = "item-ringing-1",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Aspirin",
            dosageAmount = "81",
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
        itemRepo.createItem(item, expectedDeviceId)

        val ringingOcc = Occurrence(
            id = "occ-ringing-1",
            itemId = item.id,
            scheduleId = "s-ring",
            profileId = profile.id,
            plannedAt = now - 5000L,
            dueAt = now - 5000L,
            status = OccurrenceStatus.RINGING,
            deviceId = expectedDeviceId,
            updatedAt = now
        )
        occurrenceRepo.upsertOccurrences(listOf(ringingOcc))

        val viewModel = TodayViewModel(
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

        val uiModel = OccurrenceUiModel(
            occurrence = ringingOcc,
            item = item,
            profile = profile,
            schedule = null
        )

        viewModel.onTakeClicked(uiModel)
        for (i in 0 until 50) {
            testDispatcher.scheduler.runCurrent()
            if (occurrenceRepo.getByIdSync("occ-ringing-1")?.status == OccurrenceStatus.TAKEN) break
            Thread.sleep(20)
        }

        val afterAction = occurrenceRepo.getByIdSync("occ-ringing-1")
        assertThat(afterAction?.status).isEqualTo(OccurrenceStatus.TAKEN)
    }

    @Test
    fun todayViewModel_skipAction_recordsReasonInChangeLog() = runBlocking {
        val expectedDeviceId = settingsRepo.getOrCreateDeviceId()
        val profile = profileRepo.createProfile("Carol", 0xFF00FF, deviceId = expectedDeviceId)
        val now = System.currentTimeMillis()
        val item = Item(
            id = "item-skip-1",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Vitamin D",
            dosageAmount = "1000",
            dosageUnit = "IU",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = "2026-01-01",
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item, expectedDeviceId)

        val pendingOcc = Occurrence(
            id = "occ-skip-1",
            itemId = item.id,
            scheduleId = "s-skip",
            profileId = profile.id,
            plannedAt = now,
            dueAt = now,
            status = OccurrenceStatus.PENDING,
            deviceId = expectedDeviceId,
            updatedAt = now
        )
        occurrenceRepo.upsertOccurrences(listOf(pendingOcc))

        val viewModel = TodayViewModel(
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

        val uiModel = OccurrenceUiModel(
            occurrence = pendingOcc,
            item = item,
            profile = profile,
            schedule = null
        )

        viewModel.onSkipClicked(uiModel, SkipReason.NOT_FEELING_WELL)
        for (i in 0 until 50) {
            testDispatcher.scheduler.runCurrent()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (db.changeLogDao().getAllSync().any { it.entityId == "occ-skip-1" }) break
            Thread.sleep(20)
        }

        val afterAction = occurrenceRepo.getByIdSync("occ-skip-1")
        assertThat(afterAction?.status).isEqualTo(OccurrenceStatus.SKIPPED)

        val logs = db.changeLogDao().getAllSync()
        val skipLog = logs.firstOrNull { it.entityType == "occurrence" && it.entityId == "occ-skip-1" }
        assertThat(skipLog).isNotNull()
        assertThat(skipLog?.newValue).contains("Not feeling well")
        assertThat(skipLog?.deviceId).isEqualTo(expectedDeviceId)
    }

    @Test
    fun todayViewModel_retryReschedule_triggersAlarmCoordinator() = runBlocking {
        val vm = TodayViewModel(
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

        settingsRepo.recordRescheduleError("SimulatedException", System.currentTimeMillis())
        assertThat(settingsRepo.lastRescheduleErrorFlow.first()).contains("SimulatedException")

        vm.retryReschedule()

        for (i in 0 until 50) {
            testDispatcher.scheduler.runCurrent()
            if (settingsRepo.lastRescheduleErrorFlow.first() == null) break
            Thread.sleep(20)
        }

        assertThat(settingsRepo.lastRescheduleErrorFlow.first()).isNull()
    }

    @Test
    fun itemsViewModel_formatSchedulesSummary_describesNewRecurrenceTypesAndDoseOverride() {
        val monthly = Schedule(
            id = "s-monthly",
            itemId = "i-1",
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = 8 * 60,
            recurrence = RecurrenceType.MONTHLY,
            dayOfMonth = 15,
            anchorDate = "2026-01-01",
            createdAt = 0L,
            updatedAt = 0L
        )
        val lastFriday = Schedule(
            id = "s-last-friday",
            itemId = "i-1",
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = 20 * 60,
            recurrence = RecurrenceType.MONTHLY,
            weekdayMask = 1 shl 4, // Friday
            nthWeek = 5,
            anchorDate = "2026-01-01",
            createdAt = 0L,
            updatedAt = 0L
        )
        val interval = Schedule(
            id = "s-interval",
            itemId = "i-1",
            anchorType = ScheduleAnchorType.FIXED,
            recurrence = RecurrenceType.INTERVAL,
            intervalHours = 4,
            windowStartMinute = 8 * 60,
            windowEndMinute = 20 * 60,
            anchorDate = "2026-01-01",
            createdAt = 0L,
            updatedAt = 0L
        )
        val asNeeded = Schedule(
            id = "s-prn",
            itemId = "i-1",
            anchorType = ScheduleAnchorType.FIXED,
            recurrence = RecurrenceType.PRN,
            doseAmountOverride = "250",
            anchorDate = "2026-01-01",
            createdAt = 0L,
            updatedAt = 0L
        )

        val summary = ItemsViewModel.formatSchedulesSummary(listOf(monthly, lastFriday, interval, asNeeded))
        assertThat(summary).contains("8:00 AM monthly on day 15")
        assertThat(summary).ignoringCase().contains("monthly on the last Fri")
        assertThat(summary).contains("Every 4 h from 8:00 AM to 8:00 PM")
        assertThat(summary).contains("250 — As needed")
    }

    @Test
    fun todayViewModel_takeAsNeeded_insertsTakenOccurrenceAtNow(): Unit = runBlocking {
        val deviceId = settingsRepo.getOrCreateDeviceId()
        val profile = profileRepo.createProfile("Mom", 0xFF0000, deviceId = deviceId)
        val now = System.currentTimeMillis()
        val item = Item(
            id = "item-prn-1",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Pain Relief",
            dosageAmount = "500",
            dosageUnit = "mg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            minGapMinutes = 240,
            maxPerDay = 3,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = LocalDate.now(ZoneId.systemDefault()).toString(),
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item, deviceId)

        val schedule = Schedule(
            id = "sched-prn-1",
            itemId = item.id,
            anchorType = ScheduleAnchorType.FIXED,
            recurrence = RecurrenceType.PRN,
            anchorDate = LocalDate.now(ZoneId.systemDefault()).toString(),
            createdAt = now,
            updatedAt = now
        )
        scheduleRepo.createSchedule(schedule, deviceId)

        val viewModel = TodayViewModel(
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

        viewModel.onTakeAsNeeded(
            AsNeededUiModel(
                item = item,
                profile = profile,
                schedule = schedule,
                takenToday = 0,
                maxPerDay = 3,
                canTake = true
            )
        )

        var created: List<Occurrence> = emptyList()
        for (i in 0 until 50) {
            testDispatcher.scheduler.runCurrent()
            created = occurrenceRepo.getOccurrencesBetweenSync(0, Long.MAX_VALUE)
                .filter { it.itemId == item.id }
            if (created.isNotEmpty()) break
            Thread.sleep(20)
        }

        assertThat(created).hasSize(1)
        val occurrence = created.single()
        assertThat(occurrence.status).isEqualTo(OccurrenceStatus.TAKEN)
        assertThat(occurrence.scheduleId).isEqualTo(schedule.id)
        assertThat(occurrence.profileId).isEqualTo(profile.id)
        assertThat(occurrence.plannedAt).isEqualTo(occurrence.dueAt)
        assertThat(occurrence.dueAt).isEqualTo(occurrence.remindAt)
        assertThat(occurrence.remindAt).isEqualTo(occurrence.actedAt)
        assertThat(occurrence.deviceId).isEqualTo(deviceId)
    }

    @Test
    fun todayViewModel_takeAsNeeded_blocksWhenDailyLimitReached() = runBlocking {
        val deviceId = settingsRepo.getOrCreateDeviceId()
        val profile = profileRepo.createProfile("Mom", 0xFF0000, deviceId = deviceId)
        val now = System.currentTimeMillis()
        val item = Item(
            id = "item-prn-limit",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Pain Relief",
            dosageAmount = "500",
            dosageUnit = "mg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            minGapMinutes = 0,
            maxPerDay = 1,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = LocalDate.now(ZoneId.systemDefault()).toString(),
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item, deviceId)

        val schedule = Schedule(
            id = "sched-prn-limit",
            itemId = item.id,
            anchorType = ScheduleAnchorType.FIXED,
            recurrence = RecurrenceType.PRN,
            anchorDate = LocalDate.now(ZoneId.systemDefault()).toString(),
            createdAt = now,
            updatedAt = now
        )
        scheduleRepo.createSchedule(schedule, deviceId)

        occurrenceRepo.upsertOccurrences(
            listOf(
                Occurrence(
                    id = "existing-prn-dose",
                    itemId = item.id,
                    scheduleId = schedule.id,
                    profileId = profile.id,
                    plannedAt = now - 60_000L,
                    dueAt = now - 60_000L,
                    status = OccurrenceStatus.TAKEN,
                    actedAt = now - 60_000L,
                    deviceId = deviceId,
                    updatedAt = now - 60_000L
                )
            )
        )

        val viewModel = TodayViewModel(
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

        viewModel.onTakeAsNeeded(
            AsNeededUiModel(
                item = item,
                profile = profile,
                schedule = schedule,
                takenToday = 1,
                maxPerDay = 1,
                canTake = false
            )
        )

        for (i in 0 until 20) {
            testDispatcher.scheduler.runCurrent()
            Thread.sleep(10)
        }

        val all = occurrenceRepo.getOccurrencesBetweenSync(0, Long.MAX_VALUE)
            .filter { it.itemId == item.id }
        assertThat(all).hasSize(1)
    }

    @Test
    fun todayViewModel_takeAsNeeded_warnsWithinMinGap_andForceRecordsDose(): Unit = runBlocking {
        val deviceId = settingsRepo.getOrCreateDeviceId()
        val profile = profileRepo.createProfile("Mom", 0xFF0000, deviceId = deviceId)
        val now = System.currentTimeMillis()
        val item = Item(
            id = "item-prn-gap",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Pain Relief",
            dosageAmount = "500",
            dosageUnit = "mg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            minGapMinutes = 240,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = ZoneId.systemDefault().id,
            startDate = LocalDate.now(ZoneId.systemDefault()).toString(),
            createdAt = now,
            updatedAt = now
        )
        itemRepo.createItem(item, deviceId)

        val schedule = Schedule(
            id = "sched-prn-gap",
            itemId = item.id,
            anchorType = ScheduleAnchorType.FIXED,
            recurrence = RecurrenceType.PRN,
            anchorDate = LocalDate.now(ZoneId.systemDefault()).toString(),
            createdAt = now,
            updatedAt = now
        )
        scheduleRepo.createSchedule(schedule, deviceId)

        occurrenceRepo.upsertOccurrences(
            listOf(
                Occurrence(
                    id = "recent-prn-dose",
                    itemId = item.id,
                    scheduleId = schedule.id,
                    profileId = profile.id,
                    plannedAt = now - 10 * 60_000L,
                    dueAt = now - 10 * 60_000L,
                    status = OccurrenceStatus.TAKEN,
                    actedAt = now - 10 * 60_000L,
                    deviceId = deviceId,
                    updatedAt = now - 10 * 60_000L
                )
            )
        )

        val viewModel = TodayViewModel(
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

        val asNeeded = AsNeededUiModel(
            item = item,
            profile = profile,
            schedule = schedule,
            takenToday = 1,
            maxPerDay = null,
            canTake = true
        )

        viewModel.onTakeAsNeeded(asNeeded, force = false)
        for (i in 0 until 50) {
            testDispatcher.scheduler.runCurrent()
            if (viewModel.asNeededWarning.value != null) break
            Thread.sleep(20)
        }

        assertThat(viewModel.asNeededWarning.value).isNotNull()
        assertThat(
            occurrenceRepo.getOccurrencesBetweenSync(0, Long.MAX_VALUE).count { it.itemId == item.id }
        ).isEqualTo(1)

        viewModel.onTakeAsNeeded(asNeeded, force = true)
        var all: List<Occurrence> = emptyList()
        for (i in 0 until 50) {
            testDispatcher.scheduler.runCurrent()
            all = occurrenceRepo.getOccurrencesBetweenSync(0, Long.MAX_VALUE)
                .filter { it.itemId == item.id }
            if (all.size == 2) break
            Thread.sleep(20)
        }

        assertThat(viewModel.asNeededWarning.value).isNull()
        assertThat(all).hasSize(2)
        assertThat(all.map { it.status }).containsExactly(OccurrenceStatus.TAKEN, OccurrenceStatus.TAKEN)
    }
}

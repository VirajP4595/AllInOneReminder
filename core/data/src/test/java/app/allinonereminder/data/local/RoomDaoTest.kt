package app.allinonereminder.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.allinonereminder.data.local.dao.ChangeLogDao
import app.allinonereminder.data.local.dao.ItemDao
import app.allinonereminder.data.local.dao.ItemPhotoDao
import app.allinonereminder.data.local.dao.MealAnchorDao
import app.allinonereminder.data.local.dao.MealSessionDao
import app.allinonereminder.data.local.dao.OccurrenceDao
import app.allinonereminder.data.local.dao.ProfileDao
import app.allinonereminder.data.local.dao.ScheduleDao
import app.allinonereminder.data.local.entity.ChangeLogEntity
import app.allinonereminder.data.local.entity.ItemEntity
import app.allinonereminder.data.local.entity.ItemPhotoEntity
import app.allinonereminder.data.local.entity.MealAnchorEntity
import app.allinonereminder.data.local.entity.MealSessionEntity
import app.allinonereminder.data.local.entity.OccurrenceEntity
import app.allinonereminder.data.local.entity.ProfileEntity
import app.allinonereminder.data.local.entity.ScheduleEntity
import app.allinonereminder.model.Criticality
import app.allinonereminder.model.DayType
import app.allinonereminder.model.ItemType
import app.allinonereminder.model.MealRelation
import app.allinonereminder.model.MealType
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.model.RecurrenceType
import app.allinonereminder.model.ScheduleAnchorType
import app.allinonereminder.model.TimePolicy
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class RoomDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var profileDao: ProfileDao
    private lateinit var mealAnchorDao: MealAnchorDao
    private lateinit var itemDao: ItemDao
    private lateinit var itemPhotoDao: ItemPhotoDao
    private lateinit var scheduleDao: ScheduleDao
    private lateinit var occurrenceDao: OccurrenceDao
    private lateinit var mealSessionDao: MealSessionDao
    private lateinit var changeLogDao: ChangeLogDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profileDao = db.profileDao()
        mealAnchorDao = db.mealAnchorDao()
        itemDao = db.itemDao()
        itemPhotoDao = db.itemPhotoDao()
        scheduleDao = db.scheduleDao()
        occurrenceDao = db.occurrenceDao()
        mealSessionDao = db.mealSessionDao()
        changeLogDao = db.changeLogDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun profileDao_insertAndSoftDelete() = runBlocking {
        val profileId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val profile = ProfileEntity(
            id = profileId,
            name = "John Doe",
            colorArgb = 0xFF00FF,
            createdAt = now,
            updatedAt = now
        )
        profileDao.insert(profile)

        val activeList = profileDao.getAllActive().first()
        assertThat(activeList).hasSize(1)
        assertThat(activeList[0].name).isEqualTo("John Doe")

        val retrieved = profileDao.getByIdSync(profileId)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.id).isEqualTo(profileId)

        // Soft delete
        profileDao.softDelete(profileId, now + 1000)
        val activeAfterDelete = profileDao.getAllActive().first()
        assertThat(activeAfterDelete).isEmpty()

        val allProfiles = profileDao.getAllSync()
        assertThat(allProfiles).hasSize(1)
        assertThat(allProfiles[0].deletedAt).isNotNull()
    }

    @Test
    fun mealAnchorDao_defaultAnchors() = runBlocking {
        val profileId = UUID.randomUUID().toString()
        val profile = ProfileEntity(
            id = profileId,
            name = "Jane",
            colorArgb = 0x00FF00,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        profileDao.insert(profile)

        val defaults = DefaultMealAnchors.createDefaultAnchors(profileId, 1000L)
        mealAnchorDao.insertAll(defaults)

        val anchors = mealAnchorDao.getByProfileIdSync(profileId)
        assertThat(anchors).hasSize(12) // 6 weekday + 6 weekend

        val breakfastWeekday = anchors.first { it.dayType == DayType.WEEKDAY && it.meal == MealType.BREAKFAST }
        assertThat(breakfastWeekday.minuteOfDay).isEqualTo(8 * 60 + 30) // 08:30
    }

    @Test
    fun itemDao_and_scheduleDao_crud() = runBlocking {
        val profileId = UUID.randomUUID().toString()
        profileDao.insert(
            ProfileEntity(
                id = profileId,
                name = "Alice",
                colorArgb = 0x123456,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        val itemId = UUID.randomUUID().toString()
        val item = ItemEntity(
            id = itemId,
            profileId = profileId,
            type = ItemType.MEDICINE,
            name = "Metformin",
            dosageAmount = "500",
            dosageUnit = "mg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            minGapMinutes = 240,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = "Asia/Kolkata",
            startDate = "2026-11-04",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        itemDao.insert(item)

        val scheduleId = UUID.randomUUID().toString()
        val schedule = ScheduleEntity(
            id = scheduleId,
            itemId = itemId,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = 9 * 60,
            recurrence = RecurrenceType.DAILY,
            anchorDate = "2026-11-04",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        scheduleDao.insert(schedule)

        val activeItems = itemDao.getActiveByProfile(profileId).first()
        assertThat(activeItems).hasSize(1)
        assertThat(activeItems[0].name).isEqualTo("Metformin")

        val activeSchedules = scheduleDao.getActiveByItemId(itemId).first()
        assertThat(activeSchedules).hasSize(1)
        assertThat(activeSchedules[0].minuteOfDay).isEqualTo(540)
    }

    @Test
    fun occurrenceDao_upsertAndDoubleDoseCheck() = runBlocking {
        val occurrenceId = "test-occ-id-1"
        val itemId = "item-1"
        val scheduleId = "sched-1"
        val profileId = "prof-1"
        val planned = 1700000000000L
        val due = planned

        val occ = OccurrenceEntity(
            id = occurrenceId,
            itemId = itemId,
            scheduleId = scheduleId,
            profileId = profileId,
            plannedAt = planned,
            dueAt = due,
            status = OccurrenceStatus.PENDING,
            ringCount = 0,
            deviceId = "dev-1",
            updatedAt = planned
        )
        occurrenceDao.upsertAll(listOf(occ))

        val retrieved = occurrenceDao.getByIdSync(occurrenceId)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.status).isEqualTo(OccurrenceStatus.PENDING)

        // Mark TAKEN
        val actedTime = planned + 120000L
        val takenOcc = retrieved!!.copy(
            status = OccurrenceStatus.TAKEN,
            actedAt = actedTime,
            updatedAt = actedTime
        )
        occurrenceDao.update(takenOcc)

        val lastTaken = occurrenceDao.getLastTakenBeforeSync(itemId, actedTime + 1000)
        assertThat(lastTaken).isNotNull()
        assertThat(lastTaken?.actedAt).isEqualTo(actedTime)
    }

    @Test
    fun mealSessionDao_openAndClose() = runBlocking {
        val profileId = UUID.randomUUID().toString()
        profileDao.insert(
            ProfileEntity(
                id = profileId,
                name = "Bob",
                colorArgb = 0x654321,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        val sessionId = UUID.randomUUID().toString()
        val session = MealSessionEntity(
            id = sessionId,
            profileId = profileId,
            meal = MealType.LUNCH,
            date = "2026-11-04",
            startedAt = 10000L
        )
        mealSessionDao.insert(session)

        val openSession = mealSessionDao.getOpenSessionSync(profileId, MealType.LUNCH)
        assertThat(openSession).isNotNull()
        assertThat(openSession?.endedAt).isNull()

        // End meal
        mealSessionDao.update(openSession!!.copy(endedAt = 15000L))
        val closedSession = mealSessionDao.getOpenSessionSync(profileId, MealType.LUNCH)
        assertThat(closedSession).isNull()
    }

    @Test
    fun changeLogDao_and_writer() = runBlocking {
        val writer = ChangeLogWriter(changeLogDao)
        writer.recordChange(
            entityType = "item",
            entityId = "item-99",
            field = "dosageAmount",
            oldValue = "1",
            newValue = "2",
            changedAt = 2000L,
            deviceId = "device-abc"
        )

        val logs = changeLogDao.getByEntity("item", "item-99").first()
        assertThat(logs).hasSize(1)
        assertThat(logs[0].oldValue).isEqualTo("1")
        assertThat(logs[0].newValue).isEqualTo("2")
        assertThat(logs[0].field).isEqualTo("dosageAmount")
    }
}


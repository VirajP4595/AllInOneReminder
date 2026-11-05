package app.allinonereminder.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.allinonereminder.data.local.AppDatabase
import app.allinonereminder.data.local.ChangeLogWriter
import app.allinonereminder.data.local.entity.ItemEntity
import app.allinonereminder.data.local.entity.ScheduleEntity
import app.allinonereminder.model.Criticality
import app.allinonereminder.model.ItemType
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

@RunWith(RobolectricTestRunner::class)
class ProfileRepositoryCascadeDeleteTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ProfileRepository
    private lateinit var itemRepo: ItemRepository
    private lateinit var scheduleRepo: ScheduleRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val changeLogWriter = ChangeLogWriter(db.changeLogDao())
        repository = ProfileRepository(
            profileDao = db.profileDao(),
            mealAnchorDao = db.mealAnchorDao(),
            itemDao = db.itemDao(),
            scheduleDao = db.scheduleDao(),
            database = db,
            changeLogWriter = changeLogWriter
        )
        itemRepo = ItemRepository(db.itemDao(), db.itemPhotoDao(), changeLogWriter)
        scheduleRepo = ScheduleRepository(db.scheduleDao(), changeLogWriter)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun deleteProfile_cascadesSoftDeleteToItemsAndSchedulesWithChangeLog() = runBlocking {
        val now = 1700000000000L
        val deviceId = "test-device"

        // 1. Create a profile
        val profile = repository.createProfile(
            name = "Grandpa",
            colorArgb = 0xFF123456.toInt(),
            deviceId = deviceId
        )

        // 2. Create an item for that profile
        val item = ItemEntity(
            id = "item-grandpa-1",
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Heart Pill",
            dosageAmount = "50",
            dosageUnit = "mg",
            instructions = "Take in morning",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            minGapMinutes = 240,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = "Asia/Kolkata",
            startDate = "2026-01-01",
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        db.itemDao().insert(item)

        // 3. Create a schedule for that item
        val schedule = ScheduleEntity(
            id = "sched-grandpa-1",
            itemId = item.id,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = 480,
            meal = null,
            relation = null,
            offsetMinutes = null,
            recurrence = RecurrenceType.DAILY,
            intervalN = null,
            weekdayMask = null,
            anchorDate = "2026-01-01",
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        db.scheduleDao().insert(schedule)

        // Verify active items and schedules exist before delete
        assertThat(db.itemDao().getActiveByProfile(profile.id).first()).hasSize(1)
        assertThat(db.scheduleDao().getActiveByItemId(item.id).first()).hasSize(1)

        // 4. Delete the profile
        repository.deleteProfile(profile.id, deviceId)

        // 5. Verify profile is soft-deleted
        val updatedProfile = db.profileDao().getByIdSync(profile.id)
        assertThat(updatedProfile).isNotNull()
        assertThat(updatedProfile!!.deletedAt).isNotNull()

        // 6. Verify item is soft-deleted (no active items)
        val activeItems = db.itemDao().getActiveByProfile(profile.id).first()
        assertThat(activeItems).isEmpty()
        val itemInDb = db.itemDao().getByIdSync(item.id)
        assertThat(itemInDb!!.deletedAt).isNotNull()

        // 7. Verify schedule is soft-deleted (no active schedules)
        val activeSchedules = db.scheduleDao().getActiveByItemId(item.id).first()
        assertThat(activeSchedules).isEmpty()
        val scheduleInDb = db.scheduleDao().getByIdSync(schedule.id)
        assertThat(scheduleInDb!!.deletedAt).isNotNull()

        // 8. Verify change-log records exist for all three
        val logs = db.changeLogDao().getAllSync()
        val deletedProfileLog = logs.find { it.entityType == "profile" && it.entityId == profile.id && it.field == "deletedAt" }
        val deletedItemLog = logs.find { it.entityType == "item" && it.entityId == item.id && it.field == "deletedAt" }
        val deletedScheduleLog = logs.find { it.entityType == "schedule" && it.entityId == schedule.id && it.field == "deletedAt" }

        assertThat(deletedProfileLog).isNotNull()
        assertThat(deletedItemLog).isNotNull()
        assertThat(deletedScheduleLog).isNotNull()
    }
}


package app.allinonereminder.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.allinonereminder.data.local.AppDatabase
import app.allinonereminder.data.local.entity.ItemEntity
import app.allinonereminder.data.local.entity.OccurrenceEntity
import app.allinonereminder.data.local.entity.ProfileEntity
import app.allinonereminder.data.local.entity.ScheduleEntity
import app.allinonereminder.model.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StockTrackingTest {

    private lateinit var db: AppDatabase
    private lateinit var occurrenceRepo: OccurrenceRepository
    private lateinit var stockRepo: StockEventRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        occurrenceRepo = OccurrenceRepository(
            occurrenceDao = db.occurrenceDao(),
            stockEventDao = db.stockEventDao(),
            itemDao = db.itemDao(),
            database = db
        )
        stockRepo = StockEventRepository(db.stockEventDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun markOccurrenceTaken_decrementsStock_andIsIdempotent() = runBlocking {
        val now = 1700000000000L

        // Insert profile, item (with unitsPerDose = 2.0), and occurrence
        db.profileDao().insert(ProfileEntity(id = "prof-1", name = "Mom", colorArgb = 0xFF00FF, createdAt = now, updatedAt = now))
        db.itemDao().insert(
            ItemEntity(
                id = "item-1", profileId = "prof-1", type = ItemType.MEDICINE, name = "Metformin",
                dosageAmount = "1000", dosageUnit = "mg", criticality = Criticality.NORMAL,
                graceMinutes = 60, reRingMinutes = 5, timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
                homeZoneId = "UTC", startDate = "2026-01-01", unitsPerDose = 2.0, refillThreshold = 5.0,
                active = true, createdAt = now, updatedAt = now
            )
        )
        db.scheduleDao().insert(
            ScheduleEntity(
                id = "sched-1", itemId = "item-1", anchorType = ScheduleAnchorType.FIXED,
                recurrence = RecurrenceType.DAILY, anchorDate = "2026-01-01", createdAt = now, updatedAt = now
            )
        )
        db.occurrenceDao().upsertAll(
            listOf(
                OccurrenceEntity(
                    id = "occ-1", itemId = "item-1", scheduleId = "sched-1", profileId = "prof-1",
                    plannedAt = now, dueAt = now, remindAt = now, status = OccurrenceStatus.PENDING,
                    ringCount = 0, actedAt = null, skipReason = null, note = null, deviceId = "dev-1", updatedAt = now
                )
            )
        )

        // Initial refill: +30 units
        stockRepo.addRefill("item-1", 30.0, createdAt = now)
        assertThat(stockRepo.getCurrentStockSync("item-1")).isEqualTo(30.0)

        // First take: decrements by 2.0 units -> stock becomes 28.0
        val success1 = occurrenceRepo.markOccurrenceTaken("occ-1", actedAt = now + 1000)
        assertThat(success1).isTrue()
        assertThat(stockRepo.getCurrentStockSync("item-1")).isEqualTo(28.0)

        // Second take (repeated tap / retry): must be idempotent!
        val success2 = occurrenceRepo.markOccurrenceTaken("occ-1", actedAt = now + 2000)
        assertThat(success2).isFalse()
        assertThat(stockRepo.getCurrentStockSync("item-1")).isEqualTo(28.0)

        // Verify only 2 events in ledger: 1 REFILL and 1 TAKEN
        val events = db.stockEventDao().getAllStockEvents()
        assertThat(events).hasSize(2)
        assertThat(events[0].reason).isEqualTo(StockEventReason.REFILL)
        assertThat(events[0].delta).isEqualTo(30.0)
        assertThat(events[1].reason).isEqualTo(StockEventReason.TAKEN)
        assertThat(events[1].delta).isEqualTo(-2.0)
    }

    @Test
    fun adjustStock_setsTotalToTarget_usingDifferenceDelta() = runBlocking {
        val now = 1700000000000L
        db.profileDao().insert(ProfileEntity(id = "prof-1", name = "Mom", colorArgb = 0xFF00FF, createdAt = now, updatedAt = now))
        db.itemDao().insert(
            ItemEntity(
                id = "item-1", profileId = "prof-1", type = ItemType.MEDICINE, name = "Aspirin",
                dosageAmount = "100", dosageUnit = "mg", criticality = Criticality.NORMAL,
                graceMinutes = 60, reRingMinutes = 5, timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
                homeZoneId = "UTC", startDate = "2026-01-01", unitsPerDose = 1.0, refillThreshold = 5.0,
                active = true, createdAt = now, updatedAt = now
            )
        )

        // Refill +20
        stockRepo.addRefill("item-1", 20.0, now)
        assertThat(stockRepo.getCurrentStockSync("item-1")).isEqualTo(20.0)

        // Adjust to 50: delta should be +30.0
        stockRepo.adjustStock("item-1", 50.0, now + 10)
        assertThat(stockRepo.getCurrentStockSync("item-1")).isEqualTo(50.0)

        // Adjust to 15: delta should be -35.0
        stockRepo.adjustStock("item-1", 15.0, now + 20)
        assertThat(stockRepo.getCurrentStockSync("item-1")).isEqualTo(15.0)

        val events = db.stockEventDao().getAllStockEvents()
        assertThat(events).hasSize(3)
        assertThat(events[1].reason).isEqualTo(StockEventReason.ADJUST)
        assertThat(events[1].delta).isEqualTo(30.0)
        assertThat(events[2].reason).isEqualTo(StockEventReason.ADJUST)
        assertThat(events[2].delta).isEqualTo(-35.0)
    }
}


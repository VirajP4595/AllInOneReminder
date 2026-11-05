package app.allinonereminder.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.allinonereminder.data.local.dao.OccurrenceDao
import app.allinonereminder.data.local.entity.OccurrenceEntity
import app.allinonereminder.data.local.entity.ProfileEntity
import app.allinonereminder.model.OccurrenceStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TargetedOccurrenceDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var occurrenceDao: OccurrenceDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        occurrenceDao = db.occurrenceDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun markTakenTargeted_onlyUpdatesPendingRows_andReturnsZeroOnSecondAttempt() = runBlocking {
        val now = 1700000000000L
        val occ = OccurrenceEntity(
            id = "occ-test-1",
            itemId = "item-1",
            scheduleId = "sched-1",
            profileId = "prof-1",
            plannedAt = now,
            dueAt = now,
            status = OccurrenceStatus.PENDING,
            ringCount = 0,
            deviceId = "dev-1",
            updatedAt = now
        )
        occurrenceDao.upsertAll(listOf(occ))

        // First action: succeeds, rowCount == 1
        val actedAt = now + 5000L
        val firstUpdateRows = occurrenceDao.markTakenTargeted("occ-test-1", actedAt, actedAt, note = "Taken")
        assertThat(firstUpdateRows).isEqualTo(1)

        val updated = occurrenceDao.getByIdSync("occ-test-1")
        assertThat(updated?.status).isEqualTo(OccurrenceStatus.TAKEN)
        assertThat(updated?.actedAt).isEqualTo(actedAt)

        // Second action (e.g. concurrent double-tap or second surface): fails, rowCount == 0
        val secondUpdateRows = occurrenceDao.markTakenTargeted("occ-test-1", actedAt + 1000L, actedAt + 1000L, note = "Double-tap")
        assertThat(secondUpdateRows).isEqualTo(0)

        // Verifies previous TAKEN snapshot was NOT overwritten with newer time or note
        val afterSecond = occurrenceDao.getByIdSync("occ-test-1")
        assertThat(afterSecond?.actedAt).isEqualTo(actedAt)
        assertThat(afterSecond?.note).isEqualTo("Taken")
    }

    @Test
    fun markSupersededTargeted_marksUnresolvedRowsAsSuperseded() = runBlocking {
        val now = 1700000000000L
        val occ1 = OccurrenceEntity(
            id = "occ-obsolete-1",
            itemId = "item-1",
            scheduleId = "sched-1",
            profileId = "prof-1",
            plannedAt = now + 10000L,
            dueAt = now + 10000L,
            status = OccurrenceStatus.PENDING,
            ringCount = 0,
            deviceId = "dev-1",
            updatedAt = now
        )
        val occ2 = OccurrenceEntity(
            id = "occ-taken-2",
            itemId = "item-1",
            scheduleId = "sched-1",
            profileId = "prof-1",
            plannedAt = now - 5000L,
            dueAt = now - 5000L,
            status = OccurrenceStatus.TAKEN,
            ringCount = 1,
            actedAt = now - 1000L,
            deviceId = "dev-1",
            updatedAt = now
        )
        occurrenceDao.upsertAll(listOf(occ1, occ2))

        val count = occurrenceDao.markSupersededTargeted(listOf("occ-obsolete-1", "occ-taken-2"), now)
        // Only occ1 (PENDING) was marked SUPERSEDED; occ2 (TAKEN) was not touched
        assertThat(count).isEqualTo(1)

        assertThat(occurrenceDao.getByIdSync("occ-obsolete-1")?.status).isEqualTo(OccurrenceStatus.SUPERSEDED)
        assertThat(occurrenceDao.getByIdSync("occ-taken-2")?.status).isEqualTo(OccurrenceStatus.TAKEN)
    }
}


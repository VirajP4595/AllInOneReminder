package app.allinonereminder.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DatabaseMigrationTest {

    private val testDbName = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun openV1Schema_createsAllTablesAndValidates() {
        val db = helper.createDatabase(testDbName, 1)

        // Verify tables exist
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
        val tableNames = mutableListOf<String>()
        while (cursor.moveToNext()) {
            tableNames.add(cursor.getString(0))
        }
        cursor.close()

        assertThat(tableNames).contains("profile")
        assertThat(tableNames).contains("item")
        assertThat(tableNames).contains("item_photo")
        assertThat(tableNames).contains("schedule")
        assertThat(tableNames).contains("occurrence")
        assertThat(tableNames).contains("meal_anchor")
        assertThat(tableNames).contains("meal_session")
        assertThat(tableNames).contains("change_log")

        db.close()
    }

    @Test
    fun migrate1To2_preservesData_andPopulatesRemindAtFromDueAt() {
        var db = helper.createDatabase(testDbName, 1).apply {
            execSQL("INSERT INTO occurrence (id, itemId, scheduleId, profileId, plannedAt, dueAt, status, ringCount, actedAt, skipReason, note, deviceId, updatedAt) VALUES ('occ-1', 'i-1', 's-1', 'p-1', 1700000000, 1700000500, 'PENDING', 0, NULL, NULL, NULL, 'dev-1', 1000)")
            close()
        }

        // Run migration to v2 and validate schema
        db = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        val cursor = db.query("SELECT id, plannedAt, dueAt, remindAt, status FROM occurrence WHERE id = 'occ-1'")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(0)).isEqualTo("occ-1")
        assertThat(cursor.getLong(1)).isEqualTo(1700000000L)
        assertThat(cursor.getLong(2)).isEqualTo(1700000500L)
        // remindAt must be populated from dueAt!
        assertThat(cursor.getLong(3)).isEqualTo(1700000500L)
        assertThat(cursor.getString(4)).isEqualTo("PENDING")
        cursor.close()
        db.close()
    }

    @Test
    fun migrate2To3_addsNewScheduleAndItemColumns_preservesExistingRows() {
        var db = helper.createDatabase(testDbName, 2).apply {
            execSQL(
                "INSERT INTO item (id, profileId, type, name, dosageAmount, dosageUnit, criticality, graceMinutes, reRingMinutes, timePolicy, homeZoneId, startDate, active, createdAt, updatedAt) " +
                    "VALUES ('i-1', 'p-1', 'MEDICINE', 'Aspirin', '500', 'mg', 'NORMAL', 60, 5, 'LOCAL_WALL_CLOCK', 'Asia/Kolkata', '2026-01-01', 1, 1000, 1000)"
            )
            execSQL(
                "INSERT INTO schedule (id, itemId, anchorType, recurrence, anchorDate, createdAt, updatedAt) " +
                    "VALUES ('s-1', 'i-1', 'FIXED', 'DAILY', '2026-01-01', 1000, 1000)"
            )
            close()
        }

        db = helper.runMigrationsAndValidate(testDbName, 3, true, MIGRATION_2_3)

        val itemCursor = db.query("SELECT id, dosageAmount, maxPerDay FROM item WHERE id = 'i-1'")
        assertThat(itemCursor.moveToFirst()).isTrue()
        assertThat(itemCursor.getString(0)).isEqualTo("i-1")
        assertThat(itemCursor.getString(1)).isEqualTo("500")
        assertThat(itemCursor.isNull(2)).isTrue()
        itemCursor.close()

        val scheduleCursor = db.query(
            "SELECT id, recurrence, dayOfMonth, nthWeek, intervalHours, windowStartMinute, windowEndMinute, validFrom, validUntil, doseAmountOverride " +
                "FROM schedule WHERE id = 's-1'"
        )
        assertThat(scheduleCursor.moveToFirst()).isTrue()
        assertThat(scheduleCursor.getString(0)).isEqualTo("s-1")
        assertThat(scheduleCursor.getString(1)).isEqualTo("DAILY")
        for (index in 2..9) {
            assertThat(scheduleCursor.isNull(index)).isTrue()
        }
        scheduleCursor.close()

        // The new columns are writable with the values the editor and backup restore use.
        db.execSQL(
            "UPDATE schedule SET dayOfMonth = 15, nthWeek = 2, intervalHours = 4, windowStartMinute = 480, " +
                "windowEndMinute = 1200, validFrom = '2026-01-01', validUntil = '2026-02-01', doseAmountOverride = '250' WHERE id = 's-1'"
        )
        db.execSQL("UPDATE item SET maxPerDay = 3 WHERE id = 'i-1'")

        val updatedSchedule = db.query(
            "SELECT dayOfMonth, nthWeek, intervalHours, windowStartMinute, windowEndMinute, validFrom, validUntil, doseAmountOverride " +
                "FROM schedule WHERE id = 's-1'"
        )
        assertThat(updatedSchedule.moveToFirst()).isTrue()
        assertThat(updatedSchedule.getInt(0)).isEqualTo(15)
        assertThat(updatedSchedule.getInt(1)).isEqualTo(2)
        assertThat(updatedSchedule.getInt(2)).isEqualTo(4)
        assertThat(updatedSchedule.getInt(3)).isEqualTo(480)
        assertThat(updatedSchedule.getInt(4)).isEqualTo(1200)
        assertThat(updatedSchedule.getString(5)).isEqualTo("2026-01-01")
        assertThat(updatedSchedule.getString(6)).isEqualTo("2026-02-01")
        assertThat(updatedSchedule.getString(7)).isEqualTo("250")
        updatedSchedule.close()

        val updatedItem = db.query("SELECT maxPerDay FROM item WHERE id = 'i-1'")
        assertThat(updatedItem.moveToFirst()).isTrue()
        assertThat(updatedItem.getInt(0)).isEqualTo(3)
        updatedItem.close()

        db.close()
    }
}

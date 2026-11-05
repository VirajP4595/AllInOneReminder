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
}

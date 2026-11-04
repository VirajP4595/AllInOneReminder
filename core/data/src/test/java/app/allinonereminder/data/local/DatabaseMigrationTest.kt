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
}


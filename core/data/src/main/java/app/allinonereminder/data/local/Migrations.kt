package app.allinonereminder.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE occurrence ADD COLUMN remindAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE occurrence SET remindAt = dueAt")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_occurrence_remindAt ON occurrence (remindAt)")
    }
}


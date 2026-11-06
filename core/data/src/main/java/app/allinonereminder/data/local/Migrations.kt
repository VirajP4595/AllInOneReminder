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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule ADD COLUMN dayOfMonth INTEGER")
        db.execSQL("ALTER TABLE schedule ADD COLUMN nthWeek INTEGER")
        db.execSQL("ALTER TABLE schedule ADD COLUMN intervalHours INTEGER")
        db.execSQL("ALTER TABLE schedule ADD COLUMN windowStartMinute INTEGER")
        db.execSQL("ALTER TABLE schedule ADD COLUMN windowEndMinute INTEGER")
        db.execSQL("ALTER TABLE schedule ADD COLUMN validFrom TEXT")
        db.execSQL("ALTER TABLE schedule ADD COLUMN validUntil TEXT")
        db.execSQL("ALTER TABLE schedule ADD COLUMN doseAmountOverride TEXT")
        db.execSQL("ALTER TABLE item ADD COLUMN maxPerDay INTEGER")
    }
}


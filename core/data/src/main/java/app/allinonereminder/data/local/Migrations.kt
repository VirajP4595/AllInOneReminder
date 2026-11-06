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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE item ADD COLUMN unitsPerDose REAL")
        db.execSQL("ALTER TABLE item ADD COLUMN refillThreshold REAL")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stock_event` (" +
                "`id` TEXT NOT NULL, " +
                "`itemId` TEXT NOT NULL, " +
                "`delta` REAL NOT NULL, " +
                "`reason` TEXT NOT NULL, " +
                "`occurrenceId` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`itemId`) REFERENCES `item`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_event_itemId` ON `stock_event` (`itemId`)")
    }
}


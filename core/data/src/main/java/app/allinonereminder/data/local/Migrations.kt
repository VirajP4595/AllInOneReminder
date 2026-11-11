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

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE item ADD COLUMN appointmentId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_appointmentId` ON `item` (`appointmentId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `appointment` (" +
                "`id` TEXT NOT NULL, " +
                "`profileId` TEXT NOT NULL, " +
                "`doctor` TEXT NOT NULL, " +
                "`place` TEXT, " +
                "`startAt` INTEGER NOT NULL, " +
                "`purpose` TEXT, " +
                "`notes` TEXT, " +
                "`prescriptionNotes` TEXT, " +
                "`status` TEXT NOT NULL, " +
                "`remindMinutesBefore` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`profileId`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointment_profileId` ON `appointment` (`profileId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointment_status` ON `appointment` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointment_startAt` ON `appointment` (`startAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointment_deletedAt` ON `appointment` (`deletedAt`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `appointment_prep` (" +
                "`id` TEXT NOT NULL, " +
                "`appointmentId` TEXT NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`minutesBefore` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`appointmentId`) REFERENCES `appointment`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointment_prep_appointmentId` ON `appointment_prep` (`appointmentId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `appointment_attachment` (" +
                "`id` TEXT NOT NULL, " +
                "`appointmentId` TEXT NOT NULL, " +
                "`filePath` TEXT NOT NULL, " +
                "`mimeType` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`appointmentId`) REFERENCES `appointment`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointment_attachment_appointmentId` ON `appointment_attachment` (`appointmentId`)")
    }
}

/** v6: expiry date and category on items, on/off cycles and weekend shift on schedules, who marked a dose. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE item ADD COLUMN expiryDate TEXT")
        db.execSQL("ALTER TABLE item ADD COLUMN category TEXT")
        db.execSQL("ALTER TABLE schedule ADD COLUMN cycleOnDays INTEGER")
        db.execSQL("ALTER TABLE schedule ADD COLUMN cycleOffDays INTEGER")
        db.execSQL("ALTER TABLE schedule ADD COLUMN weekendShiftMinutes INTEGER")
        db.execSQL("ALTER TABLE occurrence ADD COLUMN markedBy TEXT")
    }
}

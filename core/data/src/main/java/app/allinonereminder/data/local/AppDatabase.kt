package app.allinonereminder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.allinonereminder.data.local.converter.RoomConverters
import app.allinonereminder.data.local.dao.ChangeLogDao
import app.allinonereminder.data.local.dao.ItemDao
import app.allinonereminder.data.local.dao.ItemPhotoDao
import app.allinonereminder.data.local.dao.MealAnchorDao
import app.allinonereminder.data.local.dao.MealSessionDao
import app.allinonereminder.data.local.dao.OccurrenceDao
import app.allinonereminder.data.local.dao.ProfileDao
import app.allinonereminder.data.local.dao.ScheduleDao
import app.allinonereminder.data.local.entity.ChangeLogEntity
import app.allinonereminder.data.local.entity.ItemEntity
import app.allinonereminder.data.local.entity.ItemPhotoEntity
import app.allinonereminder.data.local.entity.MealAnchorEntity
import app.allinonereminder.data.local.entity.MealSessionEntity
import app.allinonereminder.data.local.entity.OccurrenceEntity
import app.allinonereminder.data.local.entity.ProfileEntity
import app.allinonereminder.data.local.entity.ScheduleEntity

@Database(
    entities = [
        ProfileEntity::class,
        MealAnchorEntity::class,
        ItemEntity::class,
        ItemPhotoEntity::class,
        ScheduleEntity::class,
        OccurrenceEntity::class,
        MealSessionEntity::class,
        ChangeLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun mealAnchorDao(): MealAnchorDao
    abstract fun itemDao(): ItemDao
    abstract fun itemPhotoDao(): ItemPhotoDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun mealSessionDao(): MealSessionDao
    abstract fun changeLogDao(): ChangeLogDao
}


package app.allinonereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import app.allinonereminder.data.local.entity.AppointmentAttachmentEntity
import app.allinonereminder.data.local.entity.AppointmentEntity
import app.allinonereminder.data.local.entity.AppointmentPrepEntity
import app.allinonereminder.data.local.entity.ChangeLogEntity
import app.allinonereminder.data.local.entity.ItemEntity
import app.allinonereminder.data.local.entity.ItemPhotoEntity
import app.allinonereminder.data.local.entity.MealAnchorEntity
import app.allinonereminder.data.local.entity.MealSessionEntity
import app.allinonereminder.data.local.entity.OccurrenceEntity
import app.allinonereminder.data.local.entity.ProfileEntity
import app.allinonereminder.data.local.entity.ScheduleEntity
import app.allinonereminder.data.local.entity.StockEventEntity
import app.allinonereminder.model.MealType
import app.allinonereminder.model.OccurrenceStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE deletedAt IS NULL ORDER BY createdAt ASC")
    fun getAllActive(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile WHERE id = :id AND deletedAt IS NULL")
    fun getById(id: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun getByIdSync(id: String): ProfileEntity?

    @Query("SELECT * FROM profile")
    suspend fun getAllSync(): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<ProfileEntity>)

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun deleteAll()

    @Query("UPDATE profile SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)
}

@Dao
interface MealAnchorDao {
    @Query("SELECT * FROM meal_anchor WHERE profileId = :profileId")
    fun getByProfileId(profileId: String): Flow<List<MealAnchorEntity>>

    @Query("SELECT * FROM meal_anchor WHERE profileId = :profileId")
    suspend fun getByProfileIdSync(profileId: String): List<MealAnchorEntity>

    @Query("SELECT * FROM meal_anchor")
    suspend fun getAllSync(): List<MealAnchorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anchor: MealAnchorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(anchors: List<MealAnchorEntity>)

    @Update
    suspend fun update(anchor: MealAnchorEntity)

    @Query("DELETE FROM meal_anchor")
    suspend fun deleteAll()
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM item WHERE profileId = :profileId AND deletedAt IS NULL ORDER BY name ASC")
    fun getActiveByProfile(profileId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM item WHERE deletedAt IS NULL ORDER BY name ASC")
    fun getAllActive(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM item WHERE deletedAt IS NULL")
    suspend fun getAllActiveSync(): List<ItemEntity>

    @Query("SELECT * FROM item WHERE id = :id AND deletedAt IS NULL")
    fun getById(id: String): Flow<ItemEntity?>

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun getByIdSync(id: String): ItemEntity?

    @Query("SELECT * FROM item")
    suspend fun getAllSync(): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ItemEntity>)

    @Update
    suspend fun update(item: ItemEntity)

    @Query("DELETE FROM item")
    suspend fun deleteAll()

    @Query("UPDATE item SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("SELECT * FROM item WHERE profileId = :profileId AND deletedAt IS NULL")
    suspend fun getActiveByProfileSync(profileId: String): List<ItemEntity>

    @Query("UPDATE item SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE profileId = :profileId AND deletedAt IS NULL")
    suspend fun softDeleteByProfile(profileId: String, deletedAt: Long)

    @Query("SELECT * FROM item WHERE appointmentId = :appointmentId AND deletedAt IS NULL LIMIT 1")
    suspend fun getByAppointmentIdSync(appointmentId: String): ItemEntity?

    @Query("UPDATE item SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE appointmentId = :appointmentId AND deletedAt IS NULL")
    suspend fun softDeleteByAppointmentId(appointmentId: String, deletedAt: Long)
}

@Dao
interface ItemPhotoDao {
    @Query("SELECT * FROM item_photo WHERE itemId = :itemId AND deletedAt IS NULL ORDER BY sortOrder ASC")
    fun getByItemId(itemId: String): Flow<List<ItemPhotoEntity>>

    @Query("SELECT * FROM item_photo WHERE itemId = :itemId AND deletedAt IS NULL ORDER BY sortOrder ASC")
    suspend fun getByItemIdSync(itemId: String): List<ItemPhotoEntity>

    @Query("SELECT * FROM item_photo")
    suspend fun getAllSync(): List<ItemPhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: ItemPhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<ItemPhotoEntity>)

    @Query("DELETE FROM item_photo")
    suspend fun deleteAll()

    @Query("UPDATE item_photo SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule WHERE itemId = :itemId AND deletedAt IS NULL")
    fun getActiveByItemId(itemId: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedule WHERE itemId = :itemId AND deletedAt IS NULL")
    suspend fun getActiveByItemIdSync(itemId: String): List<ScheduleEntity>

    @Query("SELECT * FROM schedule WHERE deletedAt IS NULL")
    fun getAllActive(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedule WHERE deletedAt IS NULL")
    suspend fun getAllActiveSync(): List<ScheduleEntity>

    @Query("SELECT * FROM schedule WHERE id = :id AND deletedAt IS NULL")
    fun getById(id: String): Flow<ScheduleEntity?>

    @Query("SELECT * FROM schedule WHERE id = :id")
    suspend fun getByIdSync(id: String): ScheduleEntity?

    @Query("SELECT * FROM schedule")
    suspend fun getAllSync(): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<ScheduleEntity>)

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Query("DELETE FROM schedule")
    suspend fun deleteAll()

    @Query("UPDATE schedule SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("UPDATE schedule SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE itemId = :itemId AND deletedAt IS NULL")
    suspend fun softDeleteByItemId(itemId: String, deletedAt: Long)

    @Query("SELECT * FROM schedule WHERE itemId IN (SELECT id FROM item WHERE profileId = :profileId) AND deletedAt IS NULL")
    suspend fun getActiveByProfileSync(profileId: String): List<ScheduleEntity>

    @Query("UPDATE schedule SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE itemId IN (SELECT id FROM item WHERE profileId = :profileId) AND deletedAt IS NULL")
    suspend fun softDeleteByProfile(profileId: String, deletedAt: Long)
}

@Dao
interface OccurrenceDao {
    @Query("SELECT * FROM occurrence WHERE dueAt >= :from AND dueAt < :to ORDER BY dueAt ASC")
    fun getOccurrencesBetween(from: Long, to: Long): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM occurrence WHERE dueAt >= :from AND dueAt < :to ORDER BY dueAt ASC")
    suspend fun getOccurrencesBetweenSync(from: Long, to: Long): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrence WHERE id = :id")
    suspend fun getByIdSync(id: String): OccurrenceEntity?

    @Query("SELECT * FROM occurrence WHERE id IN (:ids)")
    suspend fun getByIdsSync(ids: List<String>): List<OccurrenceEntity>

    @Query("UPDATE occurrence SET status = 'TAKEN', actedAt = :actedAt, updatedAt = :updatedAt, note = :note WHERE id = :id AND status IN ('PENDING', 'RINGING', 'SNOOZED', 'DEFERRED_MEAL')")
    suspend fun markTakenTargeted(id: String, actedAt: Long, updatedAt: Long, note: String? = null): Int

    @Query("UPDATE occurrence SET status = 'SKIPPED', actedAt = :actedAt, updatedAt = :updatedAt, skipReason = :skipReason, note = :note WHERE id = :id AND status IN ('PENDING', 'RINGING', 'SNOOZED', 'DEFERRED_MEAL')")
    suspend fun markSkippedTargeted(id: String, actedAt: Long, updatedAt: Long, skipReason: String?, note: String? = null): Int

    @Query("UPDATE occurrence SET status = 'SNOOZED', remindAt = :newRemindAt, updatedAt = :updatedAt WHERE id = :id AND status IN ('PENDING', 'RINGING', 'SNOOZED', 'DEFERRED_MEAL')")
    suspend fun snoozeTargeted(id: String, newRemindAt: Long, updatedAt: Long): Int

    @Query("UPDATE occurrence SET status = 'DEFERRED_MEAL', remindAt = :newRemindAt, updatedAt = :updatedAt WHERE id = :id AND status IN ('PENDING', 'RINGING', 'SNOOZED', 'DEFERRED_MEAL')")
    suspend fun deferMealTargeted(id: String, newRemindAt: Long, updatedAt: Long): Int

    @Query("UPDATE occurrence SET status = 'MISSED', updatedAt = :updatedAt WHERE id = :id AND status IN ('PENDING', 'RINGING', 'SNOOZED', 'DEFERRED_MEAL')")
    suspend fun markMissedTargeted(id: String, updatedAt: Long): Int

    @Query("UPDATE occurrence SET status = 'SUPERSEDED', updatedAt = :updatedAt WHERE id IN (:ids) AND status IN ('PENDING', 'RINGING', 'SNOOZED', 'DEFERRED_MEAL')")
    suspend fun markSupersededTargeted(ids: List<String>, updatedAt: Long): Int

    @Query("SELECT * FROM occurrence WHERE status IN ('PENDING', 'RINGING', 'SNOOZED', 'DEFERRED_MEAL') ORDER BY dueAt ASC")
    suspend fun getUnresolvedSync(): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrence WHERE itemId = :itemId AND status = 'TAKEN' AND actedAt IS NOT NULL AND actedAt <= :beforeTime ORDER BY actedAt DESC LIMIT 1")
    suspend fun getLastTakenBeforeSync(itemId: String, beforeTime: Long): OccurrenceEntity?

    @Query("SELECT COUNT(*) FROM occurrence WHERE itemId = :itemId AND status = 'TAKEN' AND actedAt >= :from AND actedAt < :to")
    suspend fun countTakenBetweenSync(itemId: String, from: Long, to: Long): Int

    @Query("SELECT * FROM occurrence WHERE itemId = :itemId ORDER BY plannedAt DESC LIMIT :limit")
    fun getHistoryByItem(itemId: String, limit: Int = 30): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM occurrence")
    suspend fun getAllSync(): List<OccurrenceEntity>

    @Upsert
    suspend fun upsertAll(occurrences: List<OccurrenceEntity>)

    @Update
    suspend fun update(occurrence: OccurrenceEntity)

    @Query("DELETE FROM occurrence")
    suspend fun deleteAll()
}

@Dao
interface MealSessionDao {
    @Query("SELECT * FROM meal_session WHERE profileId = :profileId AND meal = :meal AND endedAt IS NULL LIMIT 1")
    fun getOpenSession(profileId: String, meal: MealType): Flow<MealSessionEntity?>

    @Query("SELECT * FROM meal_session WHERE profileId = :profileId AND meal = :meal AND endedAt IS NULL LIMIT 1")
    suspend fun getOpenSessionSync(profileId: String, meal: MealType): MealSessionEntity?

    @Query("SELECT * FROM meal_session WHERE (:profileId IS NULL OR profileId = :profileId) AND endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun getActiveSession(profileId: String?): Flow<MealSessionEntity?>

    @Query("SELECT * FROM meal_session WHERE (:profileId IS NULL OR profileId = :profileId) AND endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveSessionSync(profileId: String?): MealSessionEntity?

    @Query("SELECT * FROM meal_session WHERE endedAt IS NULL")
    suspend fun getAllOpenSessionsSync(): List<MealSessionEntity>

    @Query("SELECT * FROM meal_session WHERE profileId = :profileId AND date = :date")
    suspend fun getSessionsForDateSync(profileId: String, date: String): List<MealSessionEntity>

    @Query("SELECT * FROM meal_session")
    suspend fun getAllSync(): List<MealSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: MealSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<MealSessionEntity>)

    @Update
    suspend fun update(session: MealSessionEntity)

    @Query("DELETE FROM meal_session")
    suspend fun deleteAll()
}

@Dao
interface ChangeLogDao {
    @Insert
    suspend fun insert(changeLog: ChangeLogEntity)

    @Insert
    suspend fun insertAll(changeLogs: List<ChangeLogEntity>)

    @Query("SELECT * FROM change_log WHERE entityType = :entityType AND entityId = :entityId ORDER BY changedAt ASC")
    fun getByEntity(entityType: String, entityId: String): Flow<List<ChangeLogEntity>>

    @Query("SELECT * FROM change_log ORDER BY changedAt ASC")
    suspend fun getAllSync(): List<ChangeLogEntity>

    @Query("DELETE FROM change_log")
    suspend fun deleteAll()
}

data class ItemStock(
    val itemId: String,
    val stock: Double
)

@Dao
interface StockEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: StockEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSync(event: StockEventEntity): Long

    @Query("SELECT COALESCE(SUM(delta), 0.0) FROM stock_event WHERE itemId = :itemId")
    fun getCurrentStockFlow(itemId: String): Flow<Double>

    @Query("SELECT COALESCE(SUM(delta), 0.0) FROM stock_event WHERE itemId = :itemId")
    suspend fun getCurrentStock(itemId: String): Double

    @Query("SELECT itemId, COALESCE(SUM(delta), 0.0) as stock FROM stock_event GROUP BY itemId")
    fun getAllCurrentStocksFlow(): Flow<List<ItemStock>>

    @Query("SELECT COUNT(*) FROM stock_event WHERE itemId = :itemId")
    suspend fun countEventsForItem(itemId: String): Int

    @Query("SELECT * FROM stock_event WHERE itemId = :itemId ORDER BY createdAt DESC")
    fun getEventsForItemFlow(itemId: String): Flow<List<StockEventEntity>>

    @Query("SELECT * FROM stock_event WHERE itemId = :itemId ORDER BY createdAt DESC")
    suspend fun getEventsForItem(itemId: String): List<StockEventEntity>

    @Query("SELECT * FROM stock_event ORDER BY createdAt ASC")
    suspend fun getAllStockEvents(): List<StockEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<StockEventEntity>)

    @Query("DELETE FROM stock_event")
    suspend fun deleteAll()
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointment WHERE deletedAt IS NULL ORDER BY startAt ASC")
    fun getAllActive(): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointment WHERE deletedAt IS NULL ORDER BY startAt ASC")
    suspend fun getAllActiveSync(): List<AppointmentEntity>

    @Query("SELECT * FROM appointment WHERE id = :id AND deletedAt IS NULL")
    fun getById(id: String): Flow<AppointmentEntity?>

    @Query("SELECT * FROM appointment WHERE id = :id")
    suspend fun getByIdSync(id: String): AppointmentEntity?

    @Query("SELECT * FROM appointment WHERE profileId = :profileId AND deletedAt IS NULL ORDER BY startAt ASC")
    fun getActiveByProfile(profileId: String): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointment")
    suspend fun getAllSync(): List<AppointmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appointment: AppointmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(appointments: List<AppointmentEntity>)

    @Update
    suspend fun update(appointment: AppointmentEntity)

    @Query("UPDATE appointment SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("UPDATE appointment SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("DELETE FROM appointment WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM appointment")
    suspend fun deleteAll()
}

@Dao
interface AppointmentPrepDao {
    @Query("SELECT * FROM appointment_prep WHERE appointmentId = :appointmentId ORDER BY minutesBefore DESC")
    fun getByAppointmentIdFlow(appointmentId: String): Flow<List<AppointmentPrepEntity>>

    @Query("SELECT * FROM appointment_prep WHERE appointmentId = :appointmentId ORDER BY minutesBefore DESC")
    suspend fun getByAppointmentIdSync(appointmentId: String): List<AppointmentPrepEntity>

    @Query("SELECT * FROM appointment_prep")
    suspend fun getAllSync(): List<AppointmentPrepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prep: AppointmentPrepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(preps: List<AppointmentPrepEntity>)

    @Query("DELETE FROM appointment_prep WHERE appointmentId = :appointmentId")
    suspend fun deleteByAppointmentId(appointmentId: String)

    @Query("DELETE FROM appointment_prep")
    suspend fun deleteAll()
}

@Dao
interface AppointmentAttachmentDao {
    @Query("SELECT * FROM appointment_attachment WHERE appointmentId = :appointmentId ORDER BY createdAt ASC")
    fun getByAppointmentIdFlow(appointmentId: String): Flow<List<AppointmentAttachmentEntity>>

    @Query("SELECT * FROM appointment_attachment WHERE appointmentId = :appointmentId ORDER BY createdAt ASC")
    suspend fun getByAppointmentIdSync(appointmentId: String): List<AppointmentAttachmentEntity>

    @Query("SELECT * FROM appointment_attachment")
    suspend fun getAllSync(): List<AppointmentAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AppointmentAttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<AppointmentAttachmentEntity>)

    @Query("DELETE FROM appointment_attachment WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM appointment_attachment WHERE appointmentId = :appointmentId")
    suspend fun deleteByAppointmentId(appointmentId: String)

    @Query("DELETE FROM appointment_attachment")
    suspend fun deleteAll()
}



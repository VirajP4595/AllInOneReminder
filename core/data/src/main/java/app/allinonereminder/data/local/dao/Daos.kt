package app.allinonereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import app.allinonereminder.data.local.entity.ChangeLogEntity
import app.allinonereminder.data.local.entity.ItemEntity
import app.allinonereminder.data.local.entity.ItemPhotoEntity
import app.allinonereminder.data.local.entity.MealAnchorEntity
import app.allinonereminder.data.local.entity.MealSessionEntity
import app.allinonereminder.data.local.entity.OccurrenceEntity
import app.allinonereminder.data.local.entity.ProfileEntity
import app.allinonereminder.data.local.entity.ScheduleEntity
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

    @Query("UPDATE occurrence SET status = 'SNOOZED', dueAt = :newDueAt, updatedAt = :updatedAt WHERE id = :id AND status IN ('PENDING', 'RINGING', 'SNOOZED', 'DEFERRED_MEAL')")
    suspend fun snoozeTargeted(id: String, newDueAt: Long, updatedAt: Long): Int

    @Query("UPDATE occurrence SET status = 'DEFERRED_MEAL', dueAt = :newDueAt, updatedAt = :updatedAt WHERE id = :id AND status IN ('PENDING', 'RINGING', 'SNOOZED', 'DEFERRED_MEAL')")
    suspend fun deferMealTargeted(id: String, newDueAt: Long, updatedAt: Long): Int

    @Query("UPDATE occurrence SET status = 'MISSED', updatedAt = :updatedAt WHERE id = :id AND status IN ('PENDING', 'RINGING', 'SNOOZED', 'DEFERRED_MEAL')")
    suspend fun markMissedTargeted(id: String, updatedAt: Long): Int

    @Query("UPDATE occurrence SET status = 'SUPERSEDED', updatedAt = :updatedAt WHERE id IN (:ids) AND status IN ('PENDING', 'RINGING', 'SNOOZED', 'DEFERRED_MEAL')")
    suspend fun markSupersededTargeted(ids: List<String>, updatedAt: Long): Int

    @Query("SELECT * FROM occurrence WHERE status IN ('PENDING', 'RINGING', 'SNOOZED', 'DEFERRED_MEAL') ORDER BY dueAt ASC")
    suspend fun getUnresolvedSync(): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrence WHERE itemId = :itemId AND status = 'TAKEN' AND actedAt IS NOT NULL AND actedAt <= :beforeTime ORDER BY actedAt DESC LIMIT 1")
    suspend fun getLastTakenBeforeSync(itemId: String, beforeTime: Long): OccurrenceEntity?

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


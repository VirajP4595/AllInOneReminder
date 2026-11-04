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

    @Update
    suspend fun update(profile: ProfileEntity)

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

    @Update
    suspend fun update(item: ItemEntity)

    @Query("UPDATE item SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)
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

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Query("UPDATE schedule SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)
}

@Dao
interface OccurrenceDao {
    @Query("SELECT * FROM occurrence WHERE dueAt >= :from AND dueAt < :to ORDER BY dueAt ASC")
    fun getOccurrencesBetween(from: Long, to: Long): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM occurrence WHERE dueAt >= :from AND dueAt < :to ORDER BY dueAt ASC")
    suspend fun getOccurrencesBetweenSync(from: Long, to: Long): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrence WHERE id = :id")
    suspend fun getByIdSync(id: String): OccurrenceEntity?

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
}

@Dao
interface MealSessionDao {
    @Query("SELECT * FROM meal_session WHERE profileId = :profileId AND meal = :meal AND endedAt IS NULL LIMIT 1")
    fun getOpenSession(profileId: String, meal: MealType): Flow<MealSessionEntity?>

    @Query("SELECT * FROM meal_session WHERE profileId = :profileId AND meal = :meal AND endedAt IS NULL LIMIT 1")
    suspend fun getOpenSessionSync(profileId: String, meal: MealType): MealSessionEntity?

    @Query("SELECT * FROM meal_session WHERE profileId = :profileId AND date = :date")
    suspend fun getSessionsForDateSync(profileId: String, date: String): List<MealSessionEntity>

    @Query("SELECT * FROM meal_session")
    suspend fun getAllSync(): List<MealSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: MealSessionEntity)

    @Update
    suspend fun update(session: MealSessionEntity)
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
}


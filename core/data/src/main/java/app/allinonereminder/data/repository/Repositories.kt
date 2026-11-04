package app.allinonereminder.data.repository

import app.allinonereminder.data.local.ChangeLogWriter
import app.allinonereminder.data.local.DefaultMealAnchors
import app.allinonereminder.data.local.dao.ChangeLogDao
import app.allinonereminder.data.local.dao.ItemDao
import app.allinonereminder.data.local.dao.ItemPhotoDao
import app.allinonereminder.data.local.dao.MealAnchorDao
import app.allinonereminder.data.local.dao.MealSessionDao
import app.allinonereminder.data.local.dao.OccurrenceDao
import app.allinonereminder.data.local.dao.ProfileDao
import app.allinonereminder.data.local.dao.ScheduleDao
import app.allinonereminder.data.mapper.toDomain
import app.allinonereminder.data.mapper.toEntity
import app.allinonereminder.model.ChangeLog
import app.allinonereminder.model.Item
import app.allinonereminder.model.ItemPhoto
import app.allinonereminder.model.MealAnchor
import app.allinonereminder.model.MealSession
import app.allinonereminder.model.MealType
import app.allinonereminder.model.Occurrence
import app.allinonereminder.model.Profile
import app.allinonereminder.model.Schedule
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import app.allinonereminder.data.local.AppDatabase
import kotlinx.coroutines.flow.map
import java.util.UUID

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val mealAnchorDao: MealAnchorDao,
    private val itemDao: ItemDao,
    private val scheduleDao: ScheduleDao,
    private val database: AppDatabase,
    private val changeLogWriter: ChangeLogWriter
) {
    fun getActiveProfiles(): Flow<List<Profile>> =
        profileDao.getAllActive().map { list -> list.map { it.toDomain() } }

    fun getProfile(id: String): Flow<Profile?> =
        profileDao.getById(id).map { it?.toDomain() }

    suspend fun getProfileSync(id: String): Profile? =
        profileDao.getByIdSync(id)?.toDomain()

    suspend fun createProfile(
        name: String,
        colorArgb: Int,
        photoPath: String? = null,
        birthYear: Int? = null,
        allergies: String? = null,
        notes: String? = null,
        deviceId: String
    ): Profile {
        val now = System.currentTimeMillis()
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name,
            colorArgb = colorArgb,
            photoPath = photoPath,
            birthYear = birthYear,
            allergies = allergies,
            notes = notes,
            createdAt = now,
            updatedAt = now
        )
        profileDao.insert(profile.toEntity())

        // Insert default meal anchors
        val anchors = DefaultMealAnchors.createDefaultAnchors(profile.id, now)
        mealAnchorDao.insertAll(anchors)

        changeLogWriter.recordChange(
            entityType = "profile",
            entityId = profile.id,
            field = "created",
            oldValue = null,
            newValue = profile.name,
            changedAt = now,
            deviceId = deviceId
        )

        return profile
    }

    suspend fun updateProfile(profile: Profile, deviceId: String) {
        val old = profileDao.getByIdSync(profile.id)?.toDomain()
        val now = System.currentTimeMillis()
        val updated = profile.copy(updatedAt = now)
        profileDao.update(updated.toEntity())

        if (old != null) {
            val oldFields = mapOf(
                "name" to old.name,
                "colorArgb" to old.colorArgb,
                "birthYear" to old.birthYear,
                "allergies" to old.allergies,
                "notes" to old.notes
            )
            val newFields = mapOf(
                "name" to updated.name,
                "colorArgb" to updated.colorArgb,
                "birthYear" to updated.birthYear,
                "allergies" to updated.allergies,
                "notes" to updated.notes
            )
            changeLogWriter.recordDiffs("profile", profile.id, deviceId, oldFields, newFields, now)
        }
    }

    suspend fun deleteProfile(id: String, deviceId: String) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val activeItems = itemDao.getActiveByProfileSync(id)
            val activeSchedules = scheduleDao.getActiveByProfileSync(id)

            profileDao.softDelete(id, now)
            itemDao.softDeleteByProfile(id, now)
            scheduleDao.softDeleteByProfile(id, now)

            changeLogWriter.recordChange("profile", id, "deletedAt", null, now.toString(), now, deviceId)
            for (item in activeItems) {
                changeLogWriter.recordChange("item", item.id, "deletedAt", null, now.toString(), now, deviceId)
            }
            for (sched in activeSchedules) {
                changeLogWriter.recordChange("schedule", sched.id, "deletedAt", null, now.toString(), now, deviceId)
            }
        }
    }
}

class MealAnchorRepository(
    private val mealAnchorDao: MealAnchorDao,
    private val changeLogWriter: ChangeLogWriter
) {
    fun getAnchors(profileId: String): Flow<List<MealAnchor>> =
        mealAnchorDao.getByProfileId(profileId).map { list -> list.map { it.toDomain() } }

    suspend fun getAnchorsSync(profileId: String): List<MealAnchor> =
        mealAnchorDao.getByProfileIdSync(profileId).map { it.toDomain() }

    suspend fun updateAnchor(anchor: MealAnchor, deviceId: String) {
        val now = System.currentTimeMillis()
        val updated = anchor.copy(updatedAt = now)
        mealAnchorDao.update(updated.toEntity())
        changeLogWriter.recordChange(
            "meal_anchor",
            anchor.id,
            "${anchor.dayType}_${anchor.meal}",
            null,
            anchor.minuteOfDay.toString(),
            now,
            deviceId
        )
    }
}

class ItemRepository(
    private val itemDao: ItemDao,
    private val itemPhotoDao: ItemPhotoDao,
    private val changeLogWriter: ChangeLogWriter
) {
    fun getActiveItems(profileId: String? = null): Flow<List<Item>> {
        return if (profileId != null) {
            itemDao.getActiveByProfile(profileId).map { list -> list.map { it.toDomain() } }
        } else {
            itemDao.getAllActive().map { list -> list.map { it.toDomain() } }
        }
    }

    suspend fun getAllActiveItemsSync(): List<Item> =
        itemDao.getAllActiveSync().map { it.toDomain() }

    fun getItem(id: String): Flow<Item?> =
        itemDao.getById(id).map { it?.toDomain() }

    suspend fun getItemSync(id: String): Item? =
        itemDao.getByIdSync(id)?.toDomain()

    suspend fun createItem(item: Item, deviceId: String) {
        val now = System.currentTimeMillis()
        val toInsert = item.copy(createdAt = now, updatedAt = now)
        itemDao.insert(toInsert.toEntity())
        changeLogWriter.recordChange("item", item.id, "created", null, item.name, now, deviceId)
    }

    suspend fun updateItem(item: Item, deviceId: String) {
        val old = itemDao.getByIdSync(item.id)?.toDomain()
        val now = System.currentTimeMillis()
        val updated = item.copy(updatedAt = now)
        itemDao.update(updated.toEntity())

        if (old != null) {
            val oldFields = mapOf(
                "name" to old.name,
                "dosageAmount" to old.dosageAmount,
                "dosageUnit" to old.dosageUnit,
                "instructions" to old.instructions,
                "criticality" to old.criticality.name,
                "graceMinutes" to old.graceMinutes,
                "reRingMinutes" to old.reRingMinutes,
                "minGapMinutes" to old.minGapMinutes,
                "timePolicy" to old.timePolicy.name,
                "startDate" to old.startDate,
                "endDate" to old.endDate,
                "active" to old.active
            )
            val newFields = mapOf(
                "name" to updated.name,
                "dosageAmount" to updated.dosageAmount,
                "dosageUnit" to updated.dosageUnit,
                "instructions" to updated.instructions,
                "criticality" to updated.criticality.name,
                "graceMinutes" to updated.graceMinutes,
                "reRingMinutes" to updated.reRingMinutes,
                "minGapMinutes" to updated.minGapMinutes,
                "timePolicy" to updated.timePolicy.name,
                "startDate" to updated.startDate,
                "endDate" to updated.endDate,
                "active" to updated.active
            )
            changeLogWriter.recordDiffs("item", item.id, deviceId, oldFields, newFields, now)
        }
    }

    suspend fun deleteItem(id: String, deviceId: String) {
        val now = System.currentTimeMillis()
        itemDao.softDelete(id, now)
        changeLogWriter.recordChange("item", id, "deletedAt", null, now.toString(), now, deviceId)
    }

    fun getPhotos(itemId: String): Flow<List<ItemPhoto>> =
        itemPhotoDao.getByItemId(itemId).map { list -> list.map { it.toDomain() } }

    suspend fun getPhotosSync(itemId: String): List<ItemPhoto> =
        itemPhotoDao.getByItemIdSync(itemId).map { it.toDomain() }

    suspend fun addPhoto(photo: ItemPhoto) {
        itemPhotoDao.insert(photo.toEntity())
    }

    suspend fun deletePhoto(id: String) {
        val now = System.currentTimeMillis()
        itemPhotoDao.softDelete(id, now)
    }
}

class ScheduleRepository(
    private val scheduleDao: ScheduleDao,
    private val changeLogWriter: ChangeLogWriter
) {
    fun getActiveSchedules(itemId: String): Flow<List<Schedule>> =
        scheduleDao.getActiveByItemId(itemId).map { list -> list.map { it.toDomain() } }

    suspend fun getAllActiveSchedulesSync(): List<Schedule> =
        scheduleDao.getAllActiveSync().map { it.toDomain() }

    fun getSchedule(id: String): Flow<Schedule?> =
        scheduleDao.getById(id).map { it?.toDomain() }

    suspend fun getScheduleSync(id: String): Schedule? =
        scheduleDao.getByIdSync(id)?.toDomain()

    suspend fun createSchedule(schedule: Schedule, deviceId: String) {
        val now = System.currentTimeMillis()
        val toInsert = schedule.copy(createdAt = now, updatedAt = now)
        scheduleDao.insert(toInsert.toEntity())
        changeLogWriter.recordChange("schedule", schedule.id, "created", null, schedule.recurrence.name, now, deviceId)
    }

    suspend fun updateSchedule(schedule: Schedule, deviceId: String) {
        val old = scheduleDao.getByIdSync(schedule.id)?.toDomain()
        val now = System.currentTimeMillis()
        val updated = schedule.copy(updatedAt = now)
        scheduleDao.update(updated.toEntity())

        if (old != null) {
            val oldFields = mapOf(
                "minuteOfDay" to old.minuteOfDay,
                "meal" to old.meal?.name,
                "relation" to old.relation?.name,
                "offsetMinutes" to old.offsetMinutes,
                "recurrence" to old.recurrence.name,
                "weekdayMask" to old.weekdayMask,
                "intervalN" to old.intervalN,
                "onceDate" to old.onceDate
            )
            val newFields = mapOf(
                "minuteOfDay" to updated.minuteOfDay,
                "meal" to updated.meal?.name,
                "relation" to updated.relation?.name,
                "offsetMinutes" to updated.offsetMinutes,
                "recurrence" to updated.recurrence.name,
                "weekdayMask" to updated.weekdayMask,
                "intervalN" to updated.intervalN,
                "onceDate" to updated.onceDate
            )
            changeLogWriter.recordDiffs("schedule", schedule.id, deviceId, oldFields, newFields, now)
        }
    }

    suspend fun deleteSchedule(id: String, deviceId: String) {
        val now = System.currentTimeMillis()
        scheduleDao.softDelete(id, now)
        changeLogWriter.recordChange("schedule", id, "deletedAt", null, now.toString(), now, deviceId)
    }
}

class OccurrenceRepository(
    private val occurrenceDao: OccurrenceDao
) {
    fun getOccurrencesBetween(from: Long, to: Long): Flow<List<Occurrence>> =
        occurrenceDao.getOccurrencesBetween(from, to).map { list -> list.map { it.toDomain() } }

    suspend fun getOccurrencesBetweenSync(from: Long, to: Long): List<Occurrence> =
        occurrenceDao.getOccurrencesBetweenSync(from, to).map { it.toDomain() }

    suspend fun getByIdSync(id: String): Occurrence? =
        occurrenceDao.getByIdSync(id)?.toDomain()

    suspend fun getUnresolvedSync(): List<Occurrence> =
        occurrenceDao.getUnresolvedSync().map { it.toDomain() }

    suspend fun getLastTakenBeforeSync(itemId: String, beforeTime: Long): Occurrence? =
        occurrenceDao.getLastTakenBeforeSync(itemId, beforeTime)?.toDomain()

    fun getHistoryByItem(itemId: String, limit: Int = 30): Flow<List<Occurrence>> =
        occurrenceDao.getHistoryByItem(itemId, limit).map { list -> list.map { it.toDomain() } }

    suspend fun upsertOccurrences(occurrences: List<Occurrence>) {
        occurrenceDao.upsertAll(occurrences.map { it.toEntity() })
    }

    suspend fun updateOccurrence(occurrence: Occurrence) {
        occurrenceDao.update(occurrence.toEntity())
    }
}

class MealSessionRepository(
    private val mealSessionDao: MealSessionDao
) {
    fun getOpenSession(profileId: String, meal: MealType): Flow<MealSession?> =
        mealSessionDao.getOpenSession(profileId, meal).map { it?.toDomain() }

    suspend fun getOpenSessionSync(profileId: String, meal: MealType): MealSession? =
        mealSessionDao.getOpenSessionSync(profileId, meal)?.toDomain()

    suspend fun getSessionsForDateSync(profileId: String, date: String): List<MealSession> =
        mealSessionDao.getSessionsForDateSync(profileId, date).map { it.toDomain() }

    suspend fun startMealSession(
        profileId: String,
        meal: MealType,
        date: String,
        startedAt: Long = System.currentTimeMillis()
    ): MealSession {
        val session = MealSession(
            id = UUID.randomUUID().toString(),
            profileId = profileId,
            meal = meal,
            date = date,
            startedAt = startedAt,
            endedAt = null
        )
        mealSessionDao.insert(session.toEntity())
        return session
    }

    suspend fun endMealSession(id: String, endedAt: Long = System.currentTimeMillis()) {
        val existing = mealSessionDao.getAllSync().firstOrNull { it.id == id }
        if (existing != null) {
            mealSessionDao.update(existing.copy(endedAt = endedAt))
        }
    }
}

class ChangeLogRepository(
    private val changeLogDao: ChangeLogDao
) {
    fun getChangeLogs(entityType: String, entityId: String): Flow<List<ChangeLog>> =
        changeLogDao.getByEntity(entityType, entityId).map { list -> list.map { it.toDomain() } }
}


package app.allinonereminder.data.repository

import app.allinonereminder.data.local.ChangeLogWriter
import app.allinonereminder.data.local.DefaultMealAnchors
import app.allinonereminder.data.local.dao.AppointmentAttachmentDao
import app.allinonereminder.data.local.dao.AppointmentDao
import app.allinonereminder.data.local.dao.AppointmentPrepDao
import app.allinonereminder.data.local.dao.ChangeLogDao
import app.allinonereminder.data.local.dao.ItemDao
import app.allinonereminder.data.local.dao.ItemPhotoDao
import app.allinonereminder.data.local.dao.MealAnchorDao
import app.allinonereminder.data.local.dao.MealSessionDao
import app.allinonereminder.data.local.dao.OccurrenceDao
import app.allinonereminder.data.local.dao.ProfileDao
import app.allinonereminder.data.local.dao.ScheduleDao
import app.allinonereminder.data.local.dao.StockEventDao
import app.allinonereminder.data.local.entity.AppointmentAttachmentEntity
import app.allinonereminder.data.local.entity.AppointmentEntity
import app.allinonereminder.data.local.entity.AppointmentPrepEntity
import app.allinonereminder.data.local.entity.ScheduleEntity
import app.allinonereminder.data.local.entity.StockEventEntity
import app.allinonereminder.data.mapper.toDomain
import app.allinonereminder.data.mapper.toEntity
import app.allinonereminder.model.Appointment
import app.allinonereminder.model.AppointmentAttachment
import app.allinonereminder.model.AppointmentPrep
import app.allinonereminder.model.AppointmentStatus
import app.allinonereminder.model.ChangeLog
import app.allinonereminder.model.Criticality
import app.allinonereminder.model.Item
import app.allinonereminder.model.ItemPhoto
import app.allinonereminder.model.ItemType
import app.allinonereminder.model.MealAnchor
import app.allinonereminder.model.MealSession
import app.allinonereminder.model.MealType
import app.allinonereminder.model.Occurrence
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.model.Profile
import app.allinonereminder.model.RecurrenceType
import app.allinonereminder.model.Schedule
import app.allinonereminder.model.ScheduleAnchorType
import app.allinonereminder.model.StockEvent
import app.allinonereminder.model.StockEventReason
import app.allinonereminder.model.TimePolicy
import app.allinonereminder.model.UuidV5
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
        mealAnchorDao.insert(updated.toEntity())
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

open class ItemRepository(
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

    open suspend fun getAllActiveItemsSync(): List<Item> =
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

    suspend fun getActiveSchedulesSync(itemId: String): List<Schedule> =
        scheduleDao.getActiveByItemIdSync(itemId).map { it.toDomain() }

    fun getAllActiveSchedules(): Flow<List<Schedule>> =
        scheduleDao.getAllActive().map { list -> list.map { it.toDomain() } }

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
    private val occurrenceDao: OccurrenceDao,
    private val stockEventDao: StockEventDao? = null,
    private val itemDao: ItemDao? = null,
    private val database: AppDatabase? = null
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

    suspend fun countTakenBetweenSync(itemId: String, from: Long, to: Long): Int =
        occurrenceDao.countTakenBetweenSync(itemId, from, to)

    fun getHistoryByItem(itemId: String, limit: Int = 30): Flow<List<Occurrence>> =
        occurrenceDao.getHistoryByItem(itemId, limit).map { list -> list.map { it.toDomain() } }

    suspend fun upsertOccurrences(occurrences: List<Occurrence>) {
        occurrenceDao.upsertAll(occurrences.map { it.toEntity() })
    }

    suspend fun updateOccurrence(occurrence: Occurrence) {
        occurrenceDao.update(occurrence.toEntity())
    }

    suspend fun getByIdsSync(ids: List<String>): List<Occurrence> =
        occurrenceDao.getByIdsSync(ids).map { it.toDomain() }

    suspend fun markOccurrenceTaken(id: String, actedAt: Long = System.currentTimeMillis(), note: String? = null): Boolean {
        val block: suspend () -> Boolean = {
            val occ = occurrenceDao.getByIdSync(id)
            if (occ != null) {
                val updatedCount = occurrenceDao.markTakenTargeted(id, actedAt, actedAt, note)
                if (updatedCount > 0 && stockEventDao != null && stockEventDao.countEventsForItem(occ.itemId) > 0) {
                    val item = itemDao?.getByIdSync(occ.itemId)
                    val units = item?.unitsPerDose ?: 1.0
                    val stockEventId = UuidV5.generate(UuidV5.OCCURRENCE_NAMESPACE, "stock_$id")
                    stockEventDao.insert(
                        StockEventEntity(
                            id = stockEventId,
                            itemId = occ.itemId,
                            delta = -units,
                            reason = StockEventReason.TAKEN,
                            occurrenceId = id,
                            createdAt = actedAt
                        )
                    )
                }
                updatedCount > 0
            } else {
                false
            }
        }
        return if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }

    suspend fun markTakenTargeted(id: String, actedAt: Long, note: String? = null): Boolean {
        return markOccurrenceTaken(id, actedAt, note)
    }

    suspend fun recordAsNeededTaken(occurrence: Occurrence, actedAt: Long = System.currentTimeMillis()): Occurrence {
        val takenOcc = occurrence.copy(
            status = OccurrenceStatus.TAKEN,
            plannedAt = actedAt,
            dueAt = actedAt,
            remindAt = actedAt,
            actedAt = actedAt,
            updatedAt = actedAt
        )
        val block: suspend () -> Occurrence = {
            occurrenceDao.upsertAll(listOf(takenOcc.toEntity()))
            // Only items whose stock is already being tracked (a refill/adjust exists) get a decrement.
            if (stockEventDao != null && stockEventDao.countEventsForItem(occurrence.itemId) > 0) {
                val item = itemDao?.getByIdSync(occurrence.itemId)
                val units = item?.unitsPerDose ?: 1.0
                val stockEventId = UuidV5.generate(UuidV5.OCCURRENCE_NAMESPACE, "stock_${occurrence.id}")
                stockEventDao.insert(
                    StockEventEntity(
                        id = stockEventId,
                        itemId = occurrence.itemId,
                        delta = -units,
                        reason = StockEventReason.TAKEN,
                        occurrenceId = occurrence.id,
                        createdAt = actedAt
                    )
                )
            }
            takenOcc
        }
        return if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }

    suspend fun markSkippedTargeted(id: String, actedAt: Long, skipReason: String?, note: String? = null): Boolean {
        val count = occurrenceDao.markSkippedTargeted(id, actedAt, actedAt, skipReason, note)
        return count > 0
    }

    /** Moves only `remindAt`; `dueAt` stays the intended time so MISSED is judged from it (R2-1). */
    suspend fun snoozeTargeted(id: String, newRemindAt: Long): Boolean {
        val now = System.currentTimeMillis()
        val count = occurrenceDao.snoozeTargeted(id, newRemindAt, now)
        return count > 0
    }

    suspend fun deferMealTargeted(id: String, newRemindAt: Long): Boolean {
        val now = System.currentTimeMillis()
        val count = occurrenceDao.deferMealTargeted(id, newRemindAt, now)
        return count > 0
    }

    suspend fun markMissedTargeted(id: String): Boolean {
        val now = System.currentTimeMillis()
        val count = occurrenceDao.markMissedTargeted(id, now)
        return count > 0
    }

    suspend fun markSupersededTargeted(ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        val now = System.currentTimeMillis()
        return occurrenceDao.markSupersededTargeted(ids, now)
    }
}

class MealSessionRepository(
    private val mealSessionDao: MealSessionDao
) {
    fun getOpenSession(profileId: String, meal: MealType): Flow<MealSession?> =
        mealSessionDao.getOpenSession(profileId, meal).map { it?.toDomain() }

    suspend fun getOpenSessionSync(profileId: String, meal: MealType): MealSession? =
        mealSessionDao.getOpenSessionSync(profileId, meal)?.toDomain()

    fun getActiveSession(profileId: String? = null): Flow<MealSession?> =
        mealSessionDao.getActiveSession(profileId).map { it?.toDomain() }

    suspend fun getActiveSessionSync(profileId: String? = null): MealSession? =
        mealSessionDao.getActiveSessionSync(profileId)?.toDomain()

    suspend fun getAllOpenSessionsSync(): List<MealSession> =
        mealSessionDao.getAllOpenSessionsSync().map { it.toDomain() }

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

class StockEventRepository(
    private val stockEventDao: StockEventDao
) {
    fun getCurrentStock(itemId: String): Flow<Double> =
        stockEventDao.getCurrentStockFlow(itemId)

    fun getAllCurrentStocksFlow(): Flow<Map<String, Double>> =
        stockEventDao.getAllCurrentStocksFlow().map { list -> list.associate { it.itemId to it.stock } }

    suspend fun getCurrentStockSync(itemId: String): Double =
        stockEventDao.getCurrentStock(itemId)

    fun getEventsForItem(itemId: String): Flow<List<StockEvent>> =
        stockEventDao.getEventsForItemFlow(itemId).map { list -> list.map { it.toDomain() } }

    suspend fun getEventsForItemSync(itemId: String): List<StockEvent> =
        stockEventDao.getEventsForItem(itemId).map { it.toDomain() }

    suspend fun hasStockEvents(itemId: String): Boolean =
        stockEventDao.countEventsForItem(itemId) > 0

    suspend fun addRefill(itemId: String, amount: Double, createdAt: Long = System.currentTimeMillis()): StockEvent {
        val event = StockEvent(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            delta = amount,
            reason = StockEventReason.REFILL,
            occurrenceId = null,
            createdAt = createdAt
        )
        stockEventDao.insert(event.toEntity())
        return event
    }

    suspend fun adjustStock(itemId: String, targetAmount: Double, createdAt: Long = System.currentTimeMillis()): StockEvent {
        val current = getCurrentStockSync(itemId)
        val diff = targetAmount - current
        val event = StockEvent(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            delta = diff,
            reason = StockEventReason.ADJUST,
            occurrenceId = null,
            createdAt = createdAt
        )
        stockEventDao.insert(event.toEntity())
        return event
    }
}

class AppointmentRepository(
    private val appointmentDao: AppointmentDao,
    private val appointmentPrepDao: AppointmentPrepDao,
    private val appointmentAttachmentDao: AppointmentAttachmentDao,
    private val itemDao: ItemDao,
    private val scheduleDao: ScheduleDao,
    private val database: AppDatabase,
    private val changeLogWriter: ChangeLogWriter
) {
    fun getAllAppointments(): Flow<List<Appointment>> =
        appointmentDao.getAllActive().map { list -> list.map { it.toDomain() } }

    fun getAppointmentsByProfile(profileId: String): Flow<List<Appointment>> =
        appointmentDao.getActiveByProfile(profileId).map { list -> list.map { it.toDomain() } }

    fun getAppointment(id: String): Flow<Appointment?> =
        appointmentDao.getById(id).map { it?.toDomain() }

    suspend fun getAppointmentSync(id: String): Appointment? =
        appointmentDao.getByIdSync(id)?.toDomain()

    fun getPrepsForAppointment(appointmentId: String): Flow<List<AppointmentPrep>> =
        appointmentPrepDao.getByAppointmentIdFlow(appointmentId).map { list -> list.map { it.toDomain() } }

    suspend fun getPrepsForAppointmentSync(appointmentId: String): List<AppointmentPrep> =
        appointmentPrepDao.getByAppointmentIdSync(appointmentId).map { it.toDomain() }

    fun getAttachmentsForAppointment(appointmentId: String): Flow<List<AppointmentAttachment>> =
        appointmentAttachmentDao.getByAppointmentIdFlow(appointmentId).map { list -> list.map { it.toDomain() } }

    suspend fun getAttachmentsForAppointmentSync(appointmentId: String): List<AppointmentAttachment> =
        appointmentAttachmentDao.getByAppointmentIdSync(appointmentId).map { it.toDomain() }

    suspend fun saveAppointment(
        appointment: Appointment,
        preps: List<AppointmentPrep>,
        deviceId: String,
        zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()
    ) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val existing = appointmentDao.getByIdSync(appointment.id)
            val apptEntity = appointment.copy(updatedAt = now).toEntity()
            if (existing == null) {
                appointmentDao.insert(apptEntity)
                changeLogWriter.recordChange("appointment", appointment.id, "created", null, appointment.doctor, now, deviceId)
            } else {
                appointmentDao.update(apptEntity)
                changeLogWriter.recordChange("appointment", appointment.id, "updated", null, appointment.doctor, now, deviceId)
            }

            // Sync preps
            appointmentPrepDao.deleteByAppointmentId(appointment.id)
            if (preps.isNotEmpty()) {
                appointmentPrepDao.insertAll(preps.map { it.toEntity() })
            }

            // Materialize hidden item & schedules
            syncHiddenItemAndSchedules(appointment, preps, now, zoneId)
        }
    }

    suspend fun updateStatus(id: String, status: AppointmentStatus, deviceId: String) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            appointmentDao.updateStatus(id, status.name, now)
            val appt = appointmentDao.getByIdSync(id)?.toDomain()
            if (appt != null) {
                val preps = appointmentPrepDao.getByAppointmentIdSync(id).map { it.toDomain() }
                syncHiddenItemAndSchedules(appt, preps, now, java.time.ZoneId.systemDefault())
            }
            changeLogWriter.recordChange("appointment", id, "status", null, status.name, now, deviceId)
        }
    }

    suspend fun deleteAppointment(id: String, deviceId: String) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            appointmentDao.softDelete(id, now)
            val hiddenItem = itemDao.getByAppointmentIdSync(id)
            itemDao.softDeleteByAppointmentId(id, now)
            if (hiddenItem != null) {
                scheduleDao.softDeleteByItemId(hiddenItem.id, now)
            }
            changeLogWriter.recordChange("appointment", id, "deletedAt", null, now.toString(), now, deviceId)
        }
    }

    suspend fun addAttachment(attachment: AppointmentAttachment) {
        appointmentAttachmentDao.insert(attachment.toEntity())
    }

    suspend fun deleteAttachment(id: String) {
        appointmentAttachmentDao.deleteById(id)
    }

    private suspend fun syncHiddenItemAndSchedules(
        appointment: Appointment,
        preps: List<AppointmentPrep>,
        now: Long,
        zoneId: java.time.ZoneId
    ) {
        var hiddenItem = itemDao.getByAppointmentIdSync(appointment.id)
        val isUpcoming = appointment.status == AppointmentStatus.UPCOMING && appointment.deletedAt == null

        val placeDesc = appointment.place?.takeIf { it.isNotBlank() }
        val purposeDesc = appointment.purpose?.takeIf { it.isNotBlank() }
        val instructionText = listOfNotNull(purposeDesc, placeDesc?.let { "at $it" }).joinToString(", ")

        if (hiddenItem == null) {
            val newItem = Item(
                id = UUID.randomUUID().toString(),
                profileId = appointment.profileId,
                type = ItemType.APPOINTMENT,
                name = appointment.doctor,
                dosageAmount = "",
                dosageUnit = "",
                instructions = instructionText.ifBlank { null },
                criticality = Criticality.NORMAL,
                timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
                homeZoneId = zoneId.id,
                startDate = java.time.Instant.ofEpochMilli(appointment.startAt).atZone(zoneId).toLocalDate().toString(),
                appointmentId = appointment.id,
                active = isUpcoming,
                createdAt = now,
                updatedAt = now
            )
            itemDao.insert(newItem.toEntity())
            hiddenItem = newItem.toEntity()
        } else {
            val updated = hiddenItem.copy(
                name = appointment.doctor,
                instructions = instructionText.ifBlank { null },
                profileId = appointment.profileId,
                active = isUpcoming,
                updatedAt = now
            )
            itemDao.update(updated)
            hiddenItem = updated
        }

        // Soft-delete previous schedules for this hidden item
        scheduleDao.softDeleteByItemId(hiddenItem.id, now)

        if (!isUpcoming) return

        val newSchedules = mutableListOf<ScheduleEntity>()

        // 1. Visit reminders from remindMinutesBefore (e.g. "120,30")
        val reminderMinutes = appointment.remindMinutesBefore
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it >= 0 }

        for (minutes in reminderMinutes) {
            val reminderInstant = appointment.startAt - minutes * 60_000L
            val zdt = java.time.Instant.ofEpochMilli(reminderInstant).atZone(zoneId)
            val onceDate = zdt.toLocalDate().toString()
            val minuteOfDay = zdt.hour * 60 + zdt.minute

            val timeLabel = when {
                minutes == 0 -> "now"
                minutes < 60 -> "$minutes minutes"
                minutes % 60 == 0 -> "${minutes / 60} hour${if (minutes / 60 > 1) "s" else ""}"
                else -> "${minutes / 60}h ${minutes % 60}m"
            }
            val doseDesc = "in $timeLabel" + if (!placeDesc.isNullOrBlank()) ", at $placeDesc" else ""

            newSchedules.add(
                ScheduleEntity(
                    id = UUID.randomUUID().toString(),
                    itemId = hiddenItem.id,
                    anchorType = ScheduleAnchorType.FIXED,
                    minuteOfDay = minuteOfDay,
                    recurrence = RecurrenceType.ONCE,
                    onceDate = onceDate,
                    anchorDate = onceDate,
                    doseAmountOverride = doseDesc,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        // 2. Prep reminders
        for (prep in preps) {
            val prepInstant = appointment.startAt - prep.minutesBefore * 60_000L
            val zdt = java.time.Instant.ofEpochMilli(prepInstant).atZone(zoneId)
            val onceDate = zdt.toLocalDate().toString()
            val minuteOfDay = zdt.hour * 60 + zdt.minute

            newSchedules.add(
                ScheduleEntity(
                    id = UUID.randomUUID().toString(),
                    itemId = hiddenItem.id,
                    anchorType = ScheduleAnchorType.FIXED,
                    minuteOfDay = minuteOfDay,
                    recurrence = RecurrenceType.ONCE,
                    onceDate = onceDate,
                    anchorDate = onceDate,
                    doseAmountOverride = "Prep: ${prep.text}",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        if (newSchedules.isNotEmpty()) {
            scheduleDao.insertAll(newSchedules)
        }
    }
}




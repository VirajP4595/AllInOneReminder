package app.allinonereminder.data.repository

import app.allinonereminder.data.local.dao.ItemDao
import app.allinonereminder.data.local.dao.OccurrenceDao
import app.allinonereminder.data.mapper.toDomain
import app.allinonereminder.gamify.*
import app.allinonereminder.model.Item
import app.allinonereminder.model.Occurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class DayAdherenceBucket {
    NONE_DUE,
    PERFECT,
    GOOD,
    PARTIAL,
    NONE_TAKEN
}

fun calculateDayBucket(totalDue: Int, onTime: Int): DayAdherenceBucket {
    return when {
        totalDue == 0 -> DayAdherenceBucket.NONE_DUE
        onTime == totalDue -> DayAdherenceBucket.PERFECT
        onTime.toDouble() / totalDue >= 0.75 -> DayAdherenceBucket.GOOD
        onTime > 0 -> DayAdherenceBucket.PARTIAL
        else -> DayAdherenceBucket.NONE_TAKEN
    }
}

data class DayAdherence(
    val date: LocalDate,
    val totalDue: Int,
    val onTime: Int,
    val taken: Int,
    val skipped: Int,
    val missed: Int,
    val bucket: DayAdherenceBucket
)

class ProgressRepository(
    private val occurrenceDao: OccurrenceDao,
    private val itemDao: ItemDao
) {
    private val onTimeWindow: Duration = Duration.ofMinutes(30)

    suspend fun getGamificationProgress(
        profileId: String? = null,
        zoneId: ZoneId = ZoneId.systemDefault(),
        daysBack: Long = 90
    ): GamificationResult {
        val now = System.currentTimeMillis()
        val fromTime = now - (daysBack * 24 * 3600 * 1000L)
        val toTime = now + (24 * 3600 * 1000L)

        val occurrenceEntities = occurrenceDao.getOccurrencesBetweenSync(fromTime, toTime)
        val allItems = itemDao.getAllSync().associateBy { it.id }

        val records = occurrenceEntities.mapNotNull { entity ->
            val occ = entity.toDomain()
            if (profileId != null && occ.profileId != profileId) return@mapNotNull null
            val item = allItems[occ.itemId]?.toDomain() ?: return@mapNotNull null
            mapToOccurrenceRecord(occ, item)
        }

        return Gamification.compute(GamificationInput(records, zoneId))
    }

    suspend fun getMonthAdherence(
        yearMonth: YearMonth,
        profileId: String? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Map<LocalDate, DayAdherence> {
        val startOfMonth = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfMonth = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val occurrenceEntities = occurrenceDao.getOccurrencesBetweenSync(startOfMonth, endOfMonth)
        val allItems = itemDao.getAllSync().associateBy { it.id }

        val occurrencesByDate = mutableMapOf<LocalDate, MutableList<Pair<Occurrence, Item>>>()

        for (day in 1..yearMonth.lengthOfMonth()) {
            occurrencesByDate[yearMonth.atDay(day)] = mutableListOf()
        }

        for (entity in occurrenceEntities) {
            val occ = entity.toDomain()
            if (profileId != null && occ.profileId != profileId) continue
            val item = allItems[occ.itemId]?.toDomain() ?: continue
            // Adherence counts only resolved doses. Future/unresolved rows are not "due yet" and
            // SUPERSEDED rows are leftovers from edited schedules; visit reminders are not doses.
            if (!isResolved(occ.status) || item.type == app.allinonereminder.model.ItemType.APPOINTMENT) continue
            val localDate = Instant.ofEpochMilli(occ.dueAt).atZone(zoneId).toLocalDate()
            occurrencesByDate.getOrPut(localDate) { mutableListOf() }.add(occ to item)
        }

        return occurrencesByDate.mapValues { (date, list) ->
            var onTimeCount = 0
            var takenCount = 0
            var skippedCount = 0
            var missedCount = 0

            for ((occ, _) in list) {
                when (occ.status) {
                    app.allinonereminder.model.OccurrenceStatus.TAKEN -> {
                        takenCount++
                        val actedAt = occ.actedAt
                        if (actedAt != null) {
                            val diff = Duration.between(Instant.ofEpochMilli(occ.dueAt), Instant.ofEpochMilli(actedAt)).abs()
                            if (diff <= onTimeWindow) {
                                onTimeCount++
                            }
                        }
                    }
                    app.allinonereminder.model.OccurrenceStatus.SKIPPED -> skippedCount++
                    app.allinonereminder.model.OccurrenceStatus.MISSED -> missedCount++
                    else -> {}
                }
            }

            val totalDue = list.size
            DayAdherence(
                date = date,
                totalDue = totalDue,
                onTime = onTimeCount,
                taken = takenCount,
                skipped = skippedCount,
                missed = missedCount,
                bucket = calculateDayBucket(totalDue, onTimeCount)
            )
        }
    }

    suspend fun getOccurrencesForDate(
        date: LocalDate,
        profileId: String? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<Pair<Occurrence, Item>> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val occurrences = occurrenceDao.getOccurrencesBetweenSync(start, end).map { it.toDomain() }
        val allItems = itemDao.getAllSync().associateBy { it.id }

        return occurrences.mapNotNull { occ ->
            if (occ.status == app.allinonereminder.model.OccurrenceStatus.SUPERSEDED) return@mapNotNull null
            if (profileId != null && occ.profileId != profileId) return@mapNotNull null
            val item = allItems[occ.itemId]?.toDomain() ?: return@mapNotNull null
            occ to item
        }.sortedBy { it.first.dueAt }
    }

    private fun isResolved(status: app.allinonereminder.model.OccurrenceStatus): Boolean =
        status == app.allinonereminder.model.OccurrenceStatus.TAKEN ||
            status == app.allinonereminder.model.OccurrenceStatus.SKIPPED ||
            status == app.allinonereminder.model.OccurrenceStatus.MISSED

    fun mapToOccurrenceRecord(occ: Occurrence, item: Item): OccurrenceRecord {
        val gamifyType = when (item.type) {
            app.allinonereminder.model.ItemType.MEDICINE -> ItemType.MEDICINE
            app.allinonereminder.model.ItemType.INJECTION -> ItemType.INJECTION
            app.allinonereminder.model.ItemType.WATER_DRINK -> ItemType.WATER_DRINK
            app.allinonereminder.model.ItemType.EXERCISE -> ItemType.EXERCISE
            app.allinonereminder.model.ItemType.HABIT -> ItemType.HABIT
            app.allinonereminder.model.ItemType.FOOD -> ItemType.FOOD
            app.allinonereminder.model.ItemType.SUPPLEMENT -> ItemType.SUPPLEMENT
            app.allinonereminder.model.ItemType.APPOINTMENT -> ItemType.APPOINTMENT
            app.allinonereminder.model.ItemType.CUSTOM -> ItemType.CUSTOM
            else -> ItemType.CUSTOM
        }

        val gamifyCriticality = when (item.criticality) {
            app.allinonereminder.model.Criticality.CRITICAL -> Criticality.CRITICAL
            app.allinonereminder.model.Criticality.NORMAL -> Criticality.NORMAL
            app.allinonereminder.model.Criticality.GENTLE -> Criticality.GENTLE
        }

        val gamifyStatus = when (occ.status) {
            app.allinonereminder.model.OccurrenceStatus.TAKEN -> OccurrenceStatus.TAKEN
            app.allinonereminder.model.OccurrenceStatus.SKIPPED -> OccurrenceStatus.SKIPPED
            app.allinonereminder.model.OccurrenceStatus.MISSED -> OccurrenceStatus.MISSED
            else -> OccurrenceStatus.PENDING
        }

        val gamifySkipReason = when (occ.skipReason) {
            app.allinonereminder.model.SkipReason.DOCTOR_STOPPED_IT.name, "DOCTOR_STOPPED_IT" -> SkipReason.DOCTOR_STOPPED_IT
            null -> null
            else -> SkipReason.OTHER
        }

        return OccurrenceRecord(
            occurrenceId = occ.id,
            itemId = occ.itemId,
            itemType = gamifyType,
            criticality = gamifyCriticality,
            dueAt = Instant.ofEpochMilli(occ.dueAt),
            graceMinutes = item.graceMinutes,
            status = gamifyStatus,
            actedAt = occ.actedAt?.let { Instant.ofEpochMilli(it) },
            skipReason = gamifySkipReason
        )
    }
}


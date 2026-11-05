package app.allinonereminder.gamify

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object Fx {

    fun occurrence(
        id: String,
        itemId: String = "item-1",
        itemType: ItemType = ItemType.MEDICINE,
        dueAt: String = "2026-01-05T09:00:00Z",
        graceMinutes: Int = 60,
        status: OccurrenceStatus,
        actedAt: String? = null,
        skipReason: SkipReason? = null,
    ): OccurrenceRecord = OccurrenceRecord(
        occurrenceId = id,
        itemId = itemId,
        itemType = itemType,
        dueAt = Instant.parse(dueAt),
        graceMinutes = graceMinutes,
        status = status,
        actedAt = actedAt?.let(Instant::parse),
        skipReason = skipReason,
    )

    fun taken(
        id: String,
        itemId: String = "item-1",
        itemType: ItemType = ItemType.MEDICINE,
        dueAt: String = "2026-01-05T09:00:00Z",
        actedAt: String = dueAt,
        graceMinutes: Int = 60,
    ): OccurrenceRecord = occurrence(
        id = id,
        itemId = itemId,
        itemType = itemType,
        dueAt = dueAt,
        graceMinutes = graceMinutes,
        status = OccurrenceStatus.TAKEN,
        actedAt = actedAt,
    )

    fun dailyTaken(
        itemId: String,
        itemType: ItemType,
        start: LocalDate,
        days: Int,
        hour: Int = 9,
    ): List<OccurrenceRecord> = (0 until days).map { offset ->
        val date = start.plusDays(offset.toLong())
        val due = date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toString()
        taken(
            id = "$itemId-$date",
            itemId = itemId,
            itemType = itemType,
            dueAt = due,
        )
    }

    fun daily(
        itemId: String,
        itemType: ItemType,
        start: LocalDate,
        days: Int,
        status: OccurrenceStatus = OccurrenceStatus.TAKEN,
        hour: Int = 9,
    ): List<OccurrenceRecord> = (0 until days).map { offset ->
        val date = start.plusDays(offset.toLong())
        val due = date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toString()
        occurrence(
            id = "$itemId-$date",
            itemId = itemId,
            itemType = itemType,
            dueAt = due,
            status = status,
            actedAt = if (status == OccurrenceStatus.TAKEN) due else null,
        )
    }
}

package app.allinonereminder.report

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Builds a doctor-facing report. The builder is deterministic: the same input
 * produces the same report, regardless of input list order.
 */
object DoctorReportBuilder {

    fun build(input: DoctorReportInput): DoctorReport {
        val medicines = input.items
            .filter { it.active && it.type.isMedication }
            .sortedWith(compareBy({ it.name }, { it.id }))

        val medicineIds = medicines.map { it.id }.toSet()
        val inRange = input.occurrences
            .filter { it.itemId in medicineIds }
            .filter {
                input.range.contains(
                    it.dueAt.atZone(input.zoneId).toLocalDate(),
                )
            }

        val occurrencesByItem = inRange.groupBy { it.itemId }
        val medicineReports = medicines.map { item ->
            medicineReport(
                item = item,
                occurrences = occurrencesByItem[item.id].orEmpty(),
                zoneId = input.zoneId,
            )
        }

        return DoctorReport(
            profile = input.profile,
            generatedAt = input.now,
            range = input.range,
            zoneId = input.zoneId,
            medicines = medicineReports,
            daily = dailyAdherence(inRange, input.range, input.zoneId),
            vitals = input.vitals
                ?.summarize(input.profile.id, input.range, input.zoneId)
                ?.sortedWith(compareBy({ it.kind }, { it.label }))
                .orEmpty(),
            pastAppointments = appointmentSlice(
                input.appointments,
                input.range,
                input.zoneId,
                upcoming = false,
                now = input.now,
            ),
            upcomingAppointments = appointmentSlice(
                input.appointments,
                input.range,
                input.zoneId,
                upcoming = true,
                now = input.now,
            ),
        )
    }

    private fun medicineReport(
        item: ReportItem,
        occurrences: List<ReportOccurrence>,
        zoneId: ZoneId,
    ): MedicineReport {
        val taken = occurrences.count { it.status == ReportOccurrenceStatus.TAKEN }
        val missed = occurrences.count { it.status == ReportOccurrenceStatus.MISSED }
        val skipped = occurrences.count { it.status == ReportOccurrenceStatus.SKIPPED }

        return MedicineReport(
            itemId = item.id,
            name = item.name,
            dosage = item.dosage,
            scheduleText = scheduleText(item.schedules),
            taken = taken,
            missed = missed,
            skipped = skipped,
            adherencePercent = percent(taken, taken + missed + skipped),
            missedDoses = occurrences
                .filter { it.status == ReportOccurrenceStatus.MISSED }
                .sortedWith(
                    compareBy(
                        { it.dueAt },
                        { it.occurrenceId },
                    ),
                )
                .map {
                    MissedDose(
                        occurrenceId = it.occurrenceId,
                        itemId = it.itemId,
                        dueAt = it.dueAt,
                        localDate = it.dueAt.atZone(zoneId).toLocalDate(),
                    )
                },
        )
    }

    private fun dailyAdherence(
        occurrences: List<ReportOccurrence>,
        range: ReportRange,
        zoneId: ZoneId,
    ): List<DailyAdherence> {
        val byDate = occurrences.groupBy {
            it.dueAt.atZone(zoneId).toLocalDate()
        }
        val days = mutableListOf<DailyAdherence>()
        var date = range.from
        while (!date.isAfter(range.to)) {
            val day = byDate[date].orEmpty()
            val taken = day.count { it.status == ReportOccurrenceStatus.TAKEN }
            val missed = day.count { it.status == ReportOccurrenceStatus.MISSED }
            val skipped = day.count { it.status == ReportOccurrenceStatus.SKIPPED }
            days += DailyAdherence(
                date = date,
                taken = taken,
                missed = missed,
                skipped = skipped,
                percent = percent(taken, taken + missed + skipped),
            )
            date = date.plusDays(1)
        }
        return days
    }

    private fun appointmentSlice(
        appointments: List<ReportAppointment>,
        range: ReportRange,
        zoneId: ZoneId,
        upcoming: Boolean,
        now: Instant,
    ): List<ReportAppointment> {
        val inRange = appointments.filter {
            range.contains(it.at.atZone(zoneId).toLocalDate())
        }
        val slice = inRange.filter {
            if (upcoming) !it.at.isBefore(now) else it.at.isBefore(now)
        }
        return if (upcoming) {
            slice.sortedWith(compareBy({ it.at }, { it.id }))
        } else {
            slice.sortedWith(
                compareByDescending<ReportAppointment> { it.at }
                    .thenBy { it.id },
            )
        }
    }

    private fun percent(part: Int, total: Int): Int? =
        if (total == 0) {
            null
        } else {
            ((part * 100.0) / total).roundToInt().coerceIn(0, 100)
        }

    private fun scheduleText(schedules: List<ReportSchedule>): String =
        if (schedules.isEmpty()) {
            "no schedule"
        } else {
            schedules
                .sortedWith(SCHEDULE_ORDER)
                .joinToString("; ") { describe(it) }
        }

    private fun describe(schedule: ReportSchedule): String =
        when (schedule) {
            is ReportSchedule.Fixed -> String.format(
                Locale.ROOT,
                "%02d:%02d",
                schedule.minuteOfDay / 60,
                schedule.minuteOfDay % 60,
            )
            is ReportSchedule.MealRelative -> buildString {
                if (schedule.offsetMinutes > 0) {
                    append(schedule.offsetMinutes)
                    append(" min ")
                }
                append(schedule.relation.label)
                append(' ')
                append(schedule.meal.label)
            }
        }

    private val Meal.label: String
        get() = when (this) {
            Meal.BREAKFAST -> "breakfast"
            Meal.LUNCH -> "lunch"
            Meal.DINNER -> "dinner"
            Meal.SNACK -> "snack"
        }

    private val MealRelation.label: String
        get() = when (this) {
            MealRelation.BEFORE -> "before"
            MealRelation.WITH -> "with"
            MealRelation.AFTER -> "after"
        }

    private val SCHEDULE_ORDER: Comparator<ReportSchedule> =
        Comparator { left, right ->
            when {
                left is ReportSchedule.Fixed && right is ReportSchedule.Fixed ->
                    left.minuteOfDay.compareTo(right.minuteOfDay)
                left is ReportSchedule.Fixed -> -1
                right is ReportSchedule.Fixed -> 1
                else -> {
                    val leftMeal = left as ReportSchedule.MealRelative
                    val rightMeal = right as ReportSchedule.MealRelative
                    compareValuesBy(
                        leftMeal,
                        rightMeal,
                        { it.meal.ordinal },
                        { it.relation.ordinal },
                        { it.offsetMinutes },
                    )
                }
            }
        }
}

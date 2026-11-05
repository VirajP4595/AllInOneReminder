package app.allinonereminder.report

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ReportProfile(
    val id: String,
    val name: String,
    val birthYear: Int? = null,
    val allergies: String? = null,
    val notes: String? = null,
)

enum class ReportItemType {
    MEDICINE,
    INJECTION,
    WATER_DRINK,
    EXERCISE,
    HABIT,
    FOOD,
    SUPPLEMENT,
    APPOINTMENT,
    CUSTOM;

    val isMedication: Boolean
        get() = this == MEDICINE || this == INJECTION
}

enum class Meal { BREAKFAST, LUNCH, DINNER, SNACK }

enum class MealRelation { BEFORE, WITH, AFTER }

sealed class ReportSchedule {
    data class Fixed(val minuteOfDay: Int) : ReportSchedule() {
        init {
            require(minuteOfDay in 0..1439) {
                "minuteOfDay must be between 0 and 1439"
            }
        }
    }

    data class MealRelative(
        val meal: Meal,
        val relation: MealRelation,
        val offsetMinutes: Int = 0,
    ) : ReportSchedule() {
        init {
            require(offsetMinutes >= 0) { "offsetMinutes must not be negative" }
        }
    }
}

data class ReportItem(
    val id: String,
    val type: ReportItemType,
    val name: String,
    val dosage: String? = null,
    val active: Boolean = true,
    val schedules: List<ReportSchedule> = emptyList(),
)

enum class ReportOccurrenceStatus {
    PENDING,
    TAKEN,
    SKIPPED,
    MISSED,
    DUPLICATE_ACTION,
}

data class ReportOccurrence(
    val occurrenceId: String,
    val itemId: String,
    val dueAt: Instant,
    val status: ReportOccurrenceStatus,
    val actedAt: Instant? = null,
    val note: String? = null,
)

data class ReportAppointment(
    val id: String,
    val title: String,
    val at: Instant,
    val doctor: String? = null,
    val place: String? = null,
    val purpose: String? = null,
    val notes: String? = null,
)

data class ReportRange(
    val from: LocalDate,
    val to: LocalDate,
) {
    init {
        require(!to.isBefore(from)) { "range end must not be before start" }
    }

    fun contains(date: LocalDate): Boolean =
        !date.isBefore(from) && !date.isAfter(to)
}

/** A vitals summary supplied by Track B or by a fake; this module never imports vitals code. */
data class VitalsSummary(
    val kind: String,
    val label: String,
    val unit: String,
    val count: Int,
    val min: Double,
    val average: Double,
    val max: Double,
)

fun interface VitalsSummaryProvider {
    fun summarize(
        profileId: String,
        range: ReportRange,
        zoneId: ZoneId,
    ): List<VitalsSummary>
}

data class MissedDose(
    val occurrenceId: String,
    val itemId: String,
    val dueAt: Instant,
    val localDate: LocalDate,
)

data class DailyAdherence(
    val date: LocalDate,
    val taken: Int,
    val missed: Int,
    val skipped: Int,
    val percent: Int?,
)

data class MedicineReport(
    val itemId: String,
    val name: String,
    val dosage: String?,
    val scheduleText: String,
    val taken: Int,
    val missed: Int,
    val skipped: Int,
    val adherencePercent: Int?,
    val missedDoses: List<MissedDose>,
)

data class DoctorReport(
    val profile: ReportProfile,
    val generatedAt: Instant,
    val range: ReportRange,
    val zoneId: ZoneId,
    val medicines: List<MedicineReport>,
    val daily: List<DailyAdherence>,
    val vitals: List<VitalsSummary>,
    val pastAppointments: List<ReportAppointment>,
    val upcomingAppointments: List<ReportAppointment>,
)

data class DoctorReportInput(
    val profile: ReportProfile,
    val items: List<ReportItem>,
    val occurrences: List<ReportOccurrence>,
    val appointments: List<ReportAppointment>,
    val range: ReportRange,
    val zoneId: ZoneId,
    val now: Instant,
    val vitals: VitalsSummaryProvider? = null,
)

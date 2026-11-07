package app.allinonereminder.model

data class Profile(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val photoPath: String? = null,
    val birthYear: Int? = null,
    val allergies: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

data class MealAnchor(
    val id: String,
    val profileId: String,
    val dayType: DayType,
    val meal: MealType,
    val minuteOfDay: Int,
    val updatedAt: Long
)

data class Item(
    val id: String,
    val profileId: String,
    val type: ItemType,
    val name: String,
    val dosageAmount: String,
    val dosageUnit: String,
    val form: String? = null,
    val instructions: String? = null,
    val criticality: Criticality = Criticality.NORMAL,
    val graceMinutes: Int = 60,
    val reRingMinutes: Int = 5,
    val minGapMinutes: Int? = if (type == ItemType.MEDICINE || type == ItemType.INJECTION) 240 else null,
    val timePolicy: TimePolicy = TimePolicy.LOCAL_WALL_CLOCK,
    val homeZoneId: String,
    val startDate: String, // YYYY-MM-DD
    val endDate: String? = null, // YYYY-MM-DD
    val exerciseTargetMinutes: Int? = null,
    val maxPerDay: Int? = null, // for PRN (as-needed) items
    val unitsPerDose: Double? = 1.0,
    val refillThreshold: Double? = null,
    val appointmentId: String? = null,
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

/** Items created by the "Send test alarm" button. They ring like a real reminder but stay out of lists and stats. */
const val TEST_ALARM_ID_PREFIX = "test-alarm-"

val Item.isTestAlarm: Boolean get() = id.startsWith(TEST_ALARM_ID_PREFIX)

data class ItemPhoto(
    val id: String,
    val itemId: String,
    val filePath: String,
    val sortOrder: Int,
    val createdAt: Long,
    val deletedAt: Long? = null
)

data class Schedule(
    val id: String,
    val itemId: String,
    val anchorType: ScheduleAnchorType,
    val minuteOfDay: Int? = null,
    val meal: MealType? = null,
    val relation: MealRelation? = null,
    val offsetMinutes: Int? = null,
    val recurrence: RecurrenceType,
    val weekdayMask: Int? = null, // bit 0 = Monday ... bit 6 = Sunday
    val intervalN: Int? = null,
    val anchorDate: String, // YYYY-MM-DD
    val onceDate: String? = null, // YYYY-MM-DD
    val dayOfMonth: Int? = null, // MONTHLY: 1-31, clamped to the month length
    val nthWeek: Int? = null, // MONTHLY: 1-4, or 5 = last
    val intervalHours: Int? = null, // INTERVAL: hours between doses
    val windowStartMinute: Int? = null, // INTERVAL: first dose of the day
    val windowEndMinute: Int? = null, // INTERVAL: last dose of the day (inclusive)
    val validFrom: String? = null, // YYYY-MM-DD, inclusive
    val validUntil: String? = null, // YYYY-MM-DD, inclusive
    val doseAmountOverride: String? = null, // taper step dose
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

/**
 * The dose to show for a schedule. A taper uses several schedules on one item, each with its own
 * [Schedule.doseAmountOverride]; every other schedule falls back to the item's dose.
 */
fun effectiveDoseAmount(schedule: Schedule?, item: Item): String =
    schedule?.doseAmountOverride?.takeIf { it.isNotBlank() } ?: item.dosageAmount

data class Occurrence(
    val id: String,
    val itemId: String,
    val scheduleId: String,
    val profileId: String,
    val plannedAt: Long,
    val dueAt: Long,
    val remindAt: Long = dueAt,
    val status: OccurrenceStatus = OccurrenceStatus.PENDING,
    val ringCount: Int = 0,
    val actedAt: Long? = null,
    val skipReason: String? = null,
    val note: String? = null,
    val deviceId: String,
    val updatedAt: Long
)

data class MealSession(
    val id: String,
    val profileId: String,
    val meal: MealType,
    val date: String, // YYYY-MM-DD
    val startedAt: Long,
    val endedAt: Long? = null
)

data class ChangeLog(
    val id: String,
    val entityType: String,
    val entityId: String,
    val field: String,
    val oldValue: String? = null,
    val newValue: String? = null,
    val changedAt: Long,
    val deviceId: String
)


data class StockEvent(
    val id: String,
    val itemId: String,
    val delta: Double,
    val reason: StockEventReason,
    val occurrenceId: String? = null,
    val createdAt: Long
)

data class Appointment(
    val id: String,
    val profileId: String,
    val doctor: String,
    val place: String? = null,
    val startAt: Long,
    val purpose: String? = null,
    val notes: String? = null,
    val prescriptionNotes: String? = null,
    val status: AppointmentStatus = AppointmentStatus.UPCOMING,
    val remindMinutesBefore: String = "120,30",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

data class AppointmentPrep(
    val id: String,
    val appointmentId: String,
    val text: String,
    val minutesBefore: Int,
    val createdAt: Long = System.currentTimeMillis()
)

data class AppointmentAttachment(
    val id: String,
    val appointmentId: String,
    val filePath: String,
    val mimeType: String,
    val createdAt: Long
)

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
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

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
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

data class Occurrence(
    val id: String,
    val itemId: String,
    val scheduleId: String,
    val profileId: String,
    val plannedAt: Long,
    val dueAt: Long,
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


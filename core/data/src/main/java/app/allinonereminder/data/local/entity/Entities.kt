package app.allinonereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.allinonereminder.model.Criticality
import app.allinonereminder.model.DayType
import app.allinonereminder.model.ItemType
import app.allinonereminder.model.MealRelation
import app.allinonereminder.model.MealType
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.model.RecurrenceType
import app.allinonereminder.model.ScheduleAnchorType
import app.allinonereminder.model.TimePolicy

@Entity(
    tableName = "profile",
    indices = [
        Index("deletedAt")
    ]
)
data class ProfileEntity(
    @PrimaryKey val id: String,
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

@Entity(
    tableName = "meal_anchor",
    indices = [
        Index(value = ["profileId", "dayType", "meal"], unique = true),
        Index("profileId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MealAnchorEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val dayType: DayType,
    val meal: MealType,
    val minuteOfDay: Int,
    val updatedAt: Long
)

@Entity(
    tableName = "item",
    indices = [
        Index("profileId"),
        Index("type"),
        Index("active"),
        Index("deletedAt")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val type: ItemType,
    val name: String,
    val dosageAmount: String,
    val dosageUnit: String,
    val form: String? = null,
    val instructions: String? = null,
    val criticality: Criticality,
    val graceMinutes: Int,
    val reRingMinutes: Int,
    val minGapMinutes: Int? = null,
    val timePolicy: TimePolicy,
    val homeZoneId: String,
    val startDate: String,
    val endDate: String? = null,
    val exerciseTargetMinutes: Int? = null,
    val maxPerDay: Int? = null,
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "item_photo",
    indices = [
        Index("itemId"),
        Index("deletedAt")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ItemPhotoEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val filePath: String,
    val sortOrder: Int,
    val createdAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "schedule",
    indices = [
        Index("itemId"),
        Index("deletedAt")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val anchorType: ScheduleAnchorType,
    val minuteOfDay: Int? = null,
    val meal: MealType? = null,
    val relation: MealRelation? = null,
    val offsetMinutes: Int? = null,
    val recurrence: RecurrenceType,
    val weekdayMask: Int? = null,
    val intervalN: Int? = null,
    val anchorDate: String,
    val onceDate: String? = null,
    val dayOfMonth: Int? = null,
    val nthWeek: Int? = null,
    val intervalHours: Int? = null,
    val windowStartMinute: Int? = null,
    val windowEndMinute: Int? = null,
    val validFrom: String? = null,
    val validUntil: String? = null,
    val doseAmountOverride: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "occurrence",
    indices = [
        Index("itemId"),
        Index("scheduleId"),
        Index("profileId"),
        Index("dueAt"),
        Index("remindAt"),
        Index("status")
    ]
)
data class OccurrenceEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val scheduleId: String,
    val profileId: String,
    val plannedAt: Long,
    val dueAt: Long,
    @ColumnInfo(defaultValue = "0") val remindAt: Long = 0L,
    val status: OccurrenceStatus,
    val ringCount: Int,
    val actedAt: Long? = null,
    val skipReason: String? = null,
    val note: String? = null,
    val deviceId: String,
    val updatedAt: Long
)

@Entity(
    tableName = "meal_session",
    indices = [
        Index("profileId"),
        Index(value = ["profileId", "date", "meal"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MealSessionEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val meal: MealType,
    val date: String,
    val startedAt: Long,
    val endedAt: Long? = null
)

@Entity(
    tableName = "change_log",
    indices = [
        Index(value = ["entityType", "entityId"]),
        Index("changedAt")
    ]
)
data class ChangeLogEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val field: String,
    val oldValue: String? = null,
    val newValue: String? = null,
    val changedAt: Long,
    val deviceId: String
)


package app.allinonereminder.data.local.converter

import androidx.room.TypeConverter
import app.allinonereminder.model.AppointmentStatus
import app.allinonereminder.model.Criticality
import app.allinonereminder.model.DayType
import app.allinonereminder.model.ItemType
import app.allinonereminder.model.MealRelation
import app.allinonereminder.model.MealType
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.model.RecurrenceType
import app.allinonereminder.model.ScheduleAnchorType
import app.allinonereminder.model.StockEventReason
import app.allinonereminder.model.TimePolicy

class RoomConverters {
    @TypeConverter
    fun fromDayType(value: DayType?): String? = value?.name

    @TypeConverter
    fun toDayType(value: String?): DayType? = value?.let { DayType.valueOf(it) }

    @TypeConverter
    fun fromMealType(value: MealType?): String? = value?.name

    @TypeConverter
    fun toMealType(value: String?): MealType? = value?.let { MealType.valueOf(it) }

    @TypeConverter
    fun fromItemType(value: ItemType?): String? = value?.name

    @TypeConverter
    fun toItemType(value: String?): ItemType? = value?.let { ItemType.valueOf(it) }

    @TypeConverter
    fun fromCriticality(value: Criticality?): String? = value?.name

    @TypeConverter
    fun toCriticality(value: String?): Criticality? = value?.let { Criticality.valueOf(it) }

    @TypeConverter
    fun fromTimePolicy(value: TimePolicy?): String? = value?.name

    @TypeConverter
    fun toTimePolicy(value: String?): TimePolicy? = value?.let { TimePolicy.valueOf(it) }

    @TypeConverter
    fun fromScheduleAnchorType(value: ScheduleAnchorType?): String? = value?.name

    @TypeConverter
    fun toScheduleAnchorType(value: String?): ScheduleAnchorType? = value?.let { ScheduleAnchorType.valueOf(it) }

    @TypeConverter
    fun fromMealRelation(value: MealRelation?): String? = value?.name

    @TypeConverter
    fun toMealRelation(value: String?): MealRelation? = value?.let { MealRelation.valueOf(it) }

    @TypeConverter
    fun fromRecurrenceType(value: RecurrenceType?): String? = value?.name

    @TypeConverter
    fun toRecurrenceType(value: String?): RecurrenceType? = value?.let { RecurrenceType.valueOf(it) }

    @TypeConverter
    fun fromOccurrenceStatus(value: OccurrenceStatus?): String? = value?.name

    @TypeConverter
    fun toOccurrenceStatus(value: String?): OccurrenceStatus? = value?.let { OccurrenceStatus.valueOf(it) }

    @TypeConverter
    fun fromStockEventReason(value: StockEventReason?): String? = value?.name

    @TypeConverter
    fun toStockEventReason(value: String?): StockEventReason? = value?.let { StockEventReason.valueOf(it) }

    @TypeConverter
    fun fromAppointmentStatus(value: AppointmentStatus?): String? = value?.name

    @TypeConverter
    fun toAppointmentStatus(value: String?): AppointmentStatus? = value?.let { AppointmentStatus.valueOf(it) }
}


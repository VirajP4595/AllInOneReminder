package app.allinonereminder.ui.items

import app.allinonereminder.model.ItemType
import app.allinonereminder.model.MealRelation
import app.allinonereminder.model.MealType
import app.allinonereminder.model.RecurrenceType
import app.allinonereminder.model.ScheduleAnchorType

/** Common set-ups offered when adding a new item, so most people don't fill the whole form (#77). */
enum class QuickTemplate(
    val label: String,
    val type: ItemType,
    val dosageAmount: String? = null,
    val dosageUnit: String? = null,
    val defaultName: String? = null
) {
    ONCE_MORNING("Once a day, morning", ItemType.MEDICINE),
    TWICE_AFTER_FOOD("Twice a day after food", ItemType.MEDICINE),
    THREE_TIMES("Three times a day", ItemType.MEDICINE),
    AT_BEDTIME("At bed time", ItemType.MEDICINE),
    WEEKLY_SUNDAY("Weekly, Sunday", ItemType.SUPPLEMENT),
    WATER_2H("Water every 2 hours", ItemType.WATER_DRINK, "1", "glass", "Water");

    fun schedules(): List<ScheduleDraft> = when (this) {
        ONCE_MORNING -> listOf(ScheduleDraft(anchorType = ScheduleAnchorType.FIXED, minuteOfDay = 8 * 60))
        TWICE_AFTER_FOOD -> listOf(
            ScheduleDraft(anchorType = ScheduleAnchorType.MEAL, meal = MealType.BREAKFAST, relation = MealRelation.AFTER, offsetMinutes = 15),
            ScheduleDraft(anchorType = ScheduleAnchorType.MEAL, meal = MealType.DINNER, relation = MealRelation.AFTER, offsetMinutes = 15)
        )
        THREE_TIMES -> listOf(8 * 60, 14 * 60, 20 * 60).map {
            ScheduleDraft(anchorType = ScheduleAnchorType.FIXED, minuteOfDay = it)
        }
        AT_BEDTIME -> listOf(
            ScheduleDraft(anchorType = ScheduleAnchorType.MEAL, meal = MealType.BED, relation = MealRelation.WITH, offsetMinutes = 0)
        )
        WEEKLY_SUNDAY -> listOf(
            ScheduleDraft(
                anchorType = ScheduleAnchorType.FIXED,
                minuteOfDay = 9 * 60,
                recurrence = RecurrenceType.EVERY_N_WEEKS,
                intervalN = 1,
                weekdayMask = 1 shl 6 // Sunday
            )
        )
        WATER_2H -> listOf(
            ScheduleDraft(
                anchorType = ScheduleAnchorType.FIXED,
                minuteOfDay = 8 * 60,
                recurrence = RecurrenceType.INTERVAL,
                intervalHours = 2,
                windowStartMinute = 8 * 60,
                windowEndMinute = 20 * 60
            )
        )
    }
}

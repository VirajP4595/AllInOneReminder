package app.allinonereminder.model

enum class DayType {
    WEEKDAY,
    WEEKEND
}

enum class MealType {
    WAKE,
    BREAKFAST,
    LUNCH,
    SNACK,
    DINNER,
    BED
}

enum class ItemType {
    MEDICINE,
    INJECTION,
    WATER_DRINK,
    FOOD,
    SUPPLEMENT,
    EXERCISE,
    APPOINTMENT,
    LAB_TEST,
    HABIT,
    CUSTOM
}

enum class Criticality {
    CRITICAL,
    NORMAL,
    GENTLE
}

enum class TimePolicy {
    LOCAL_WALL_CLOCK,
    HOME_ZONE
}

enum class ScheduleAnchorType {
    FIXED,
    MEAL
}

enum class MealRelation {
    BEFORE,
    WITH,
    AFTER
}

enum class RecurrenceType {
    DAILY,
    WEEKDAYS,
    EVERY_N_DAYS,
    EVERY_N_WEEKS,
    MONTHLY,
    INTERVAL,
    PRN,
    ONCE
}

enum class OccurrenceStatus {
    PENDING,
    RINGING,
    SNOOZED,
    DEFERRED_MEAL,
    TAKEN,
    SKIPPED,
    MISSED,
    SUPERSEDED
}

enum class SkipReason(val title: String) {
    NOT_FEELING_WELL("Not feeling well"),
    DOCTOR_STOPPED_IT("Doctor stopped it"),
    RAN_OUT("Ran out"),
    OTHER("Other")
}


enum class StockEventReason {
    TAKEN,
    REFILL,
    ADJUST
}

enum class AppointmentStatus {
    UPCOMING,
    DONE,
    CANCELLED
}

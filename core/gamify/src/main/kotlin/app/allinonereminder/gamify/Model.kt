package app.allinonereminder.gamify

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The item types from the Phase 1 model that can produce gamification events. */
enum class ItemType {
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

    val isHabitItem: Boolean
        get() = this == WATER_DRINK ||
            this == EXERCISE ||
            this == HABIT ||
            this == FOOD ||
            this == SUPPLEMENT

    val ringCategory: RingCategory
        get() = when (this) {
            MEDICINE, INJECTION -> RingCategory.MEDS
            WATER_DRINK -> RingCategory.HYDRATION
            EXERCISE -> RingCategory.MOVEMENT
            else -> RingCategory.NONE
        }
}

enum class RingCategory { MEDS, HYDRATION, MOVEMENT, NONE }

enum class Criticality { CRITICAL, NORMAL, GENTLE }

enum class OccurrenceStatus {
    PENDING,
    TAKEN,
    SKIPPED,
    MISSED,

    /** A second action for an occurrence that already had a TAKEN/SKIPPED action. */
    DUPLICATE_ACTION,
}

enum class SkipReason {
    DOCTOR_STOPPED_IT,
    OTHER,
}

/**
 * One occurrence and its final action. The module is a pure function of these
 * records; it never reads a clock, database or Android API.
 */
data class OccurrenceRecord(
    val occurrenceId: String,
    val itemId: String,
    val itemType: ItemType,
    val criticality: Criticality = Criticality.NORMAL,
    val dueAt: Instant,
    val graceMinutes: Int = 60,
    val status: OccurrenceStatus,
    val actedAt: Instant? = null,
    val skipReason: SkipReason? = null,
)

data class DailySteps(
    val date: LocalDate,
    val steps: Int,
)

data class GamificationInput(
    val occurrences: List<OccurrenceRecord>,
    val zoneId: ZoneId,
    val steps: List<DailySteps> = emptyList(),
)

enum class PointsReason {
    HABIT_ON_TIME,
    HABIT_LATE_IN_GRACE,
    MEDICATION_ON_TIME,
    OUTSIDE_WINDOW,
    DUPLICATE_ACTION,
    NOT_TAKEN,
    DOCTOR_STOPPED,
    NOT_GAMIFIED,
    MISSING_ACTED_AT,
}

data class PointsEntry(
    val occurrenceId: String,
    val itemId: String,
    val points: Int,
    val reason: PointsReason,
)

data class ItemStreak(
    val itemId: String,
    val current: Int,
    val longest: Int,
    val freezesUsed: Int,
)

data class RingProgress(
    val onTime: Int,
    val total: Int,
    val percent: Int,
)

data class DailyRings(
    val date: LocalDate,
    val meds: RingProgress,
    val hydration: RingProgress,
    val movement: RingProgress,
)

enum class Badge {
    STREAK_7,
    STREAK_30,
    STREAK_100,
    HYDRATION_HERO,
    PERFECT_WEEK,
    STEPS_10K,
}

data class BadgeAward(
    val badge: Badge,
    val itemId: String? = null,
)

data class GamificationResult(
    val ledger: List<PointsEntry>,
    val totalPoints: Int,
    val streaks: List<ItemStreak>,
    val rings: List<DailyRings>,
    val badges: List<BadgeAward>,
)

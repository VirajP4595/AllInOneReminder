package app.allinonereminder.gamify

import java.time.LocalDate

internal object BadgeCalculator {

    private const val PERFECT_WEEK_DAYS = 7
    private const val STEPS_GOAL = 10_000

    fun badges(
        streaks: List<ItemStreak>,
        rings: List<DailyRings>,
        steps: List<DailySteps>,
    ): List<BadgeAward> {
        val awards = mutableListOf<BadgeAward>()

        for (streak in streaks) {
            if (streak.longest >= 7) {
                awards += BadgeAward(Badge.STREAK_7, streak.itemId)
            }
            if (streak.longest >= 30) {
                awards += BadgeAward(Badge.STREAK_30, streak.itemId)
            }
            if (streak.longest >= 100) {
                awards += BadgeAward(Badge.STREAK_100, streak.itemId)
            }
        }

        if (hasConsecutive(rings, PERFECT_WEEK_DAYS) { day ->
                day.hydration.total > 0 && day.hydration.percent == 100
            }
        ) {
            awards += BadgeAward(Badge.HYDRATION_HERO)
        }

        if (hasConsecutive(rings, PERFECT_WEEK_DAYS) { day ->
                val total = day.meds.total + day.hydration.total + day.movement.total
                total > 0 &&
                    day.meds.percent == 100 &&
                    day.hydration.percent == 100 &&
                    day.movement.percent == 100
            }
        ) {
            awards += BadgeAward(Badge.PERFECT_WEEK)
        }

        if (steps.any { it.steps >= STEPS_GOAL }) {
            awards += BadgeAward(Badge.STEPS_10K)
        }

        return awards.sortedWith(
            compareBy({ it.badge.ordinal }, { it.itemId ?: "" }),
        )
    }

    private fun hasConsecutive(
        rings: List<DailyRings>,
        requiredDays: Int,
        predicate: (DailyRings) -> Boolean,
    ): Boolean {
        var run = 0
        var previous: LocalDate? = null

        for (day in rings) {
            val consecutive = previous != null && day.date == previous.plusDays(1)
            run = if (predicate(day)) {
                if (run == 0 || consecutive) run + 1 else 1
            } else {
                0
            }
            if (run >= requiredDays) return true
            previous = day.date
        }
        return false
    }
}

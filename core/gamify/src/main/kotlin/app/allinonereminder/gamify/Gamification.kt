package app.allinonereminder.gamify

/**
 * Pure, deterministic entry point. The same input always produces the same
 * output, regardless of record order.
 */
object Gamification {

    fun compute(input: GamificationInput): GamificationResult {
        val occurrences = input.occurrences.sortedWith(
            compareBy(
                { it.occurrenceId },
                { it.itemId },
                { it.dueAt },
                { it.status.ordinal },
                { it.actedAt?.toEpochMilli() ?: Long.MAX_VALUE },
            ),
        )

        val ledger = PointsCalculator.ledger(occurrences)
        val streaks = StreakCalculator.streaks(occurrences, input.zoneId)
        val rings = RingsCalculator.rings(occurrences, input.zoneId)
        val badges = BadgeCalculator.badges(streaks, rings, input.steps)

        return GamificationResult(
            ledger = ledger,
            totalPoints = ledger.sumOf { it.points },
            streaks = streaks,
            rings = rings,
            badges = badges,
        )
    }
}

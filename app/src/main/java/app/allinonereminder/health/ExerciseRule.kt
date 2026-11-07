package app.allinonereminder.health

import java.time.Duration
import java.time.Instant

/**
 * An exercise occurrence is satisfied when the exercise recorded in
 * [dueAt - 3 h, dueAt + graceMinutes] adds up to at least the target. Sessions are clipped to the
 * window and overlapping sessions (the same walk written by two apps) are counted once.
 */
object ExerciseRule {
    val LOOK_BACK: Duration = Duration.ofHours(3)

    fun windowStart(dueAt: Instant): Instant = dueAt.minus(LOOK_BACK)

    fun windowEnd(dueAt: Instant, graceMinutes: Int): Instant = dueAt.plusSeconds(graceMinutes * 60L)

    fun minutesInWindow(spans: List<ExerciseSpan>, from: Instant, to: Instant): Long {
        val clipped = spans
            .map { maxOf(it.start, from) to minOf(it.end, to) }
            .filter { it.first.isBefore(it.second) }
            .sortedBy { it.first }
        var totalMillis = 0L
        var curStart: Instant? = null
        var curEnd: Instant? = null
        for ((s, e) in clipped) {
            if (curEnd == null || s.isAfter(curEnd)) {
                if (curStart != null && curEnd != null) totalMillis += Duration.between(curStart, curEnd).toMillis()
                curStart = s
                curEnd = e
            } else if (e.isAfter(curEnd)) {
                curEnd = e
            }
        }
        if (curStart != null && curEnd != null) totalMillis += Duration.between(curStart, curEnd).toMillis()
        return totalMillis / 60_000L
    }

    fun isSatisfied(spans: List<ExerciseSpan>, dueAt: Instant, graceMinutes: Int, targetMinutes: Int): Boolean {
        if (targetMinutes <= 0) return false
        return minutesInWindow(spans, windowStart(dueAt), windowEnd(dueAt, graceMinutes)) >= targetMinutes
    }
}

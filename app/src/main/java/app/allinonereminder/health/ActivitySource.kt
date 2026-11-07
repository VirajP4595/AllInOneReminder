package app.allinonereminder.health

import java.time.Instant

/** One recorded exercise session (any type), as read from the phone's activity store. */
data class ExerciseSpan(val start: Instant, val end: Instant)

enum class HealthAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED, UNSUPPORTED }

/**
 * Read-only view of the phone's activity data. Kept behind an interface so the verification rule
 * does not depend on Health Connect itself. Every method must be safe to call at any time:
 * it returns empty/false/zero instead of throwing (permission revoked, provider missing, ...).
 */
interface ActivitySource {
    fun availability(): HealthAvailability
    suspend fun hasExercisePermission(): Boolean
    suspend fun exerciseSpans(from: Instant, to: Instant): List<ExerciseSpan>

    /** Steps since local midnight. Exposed for a future steps badge; nothing is gated on it. */
    suspend fun todaySteps(): Long
}

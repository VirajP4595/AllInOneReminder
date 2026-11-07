package app.allinonereminder.health

import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.OccurrenceRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.model.ItemType
import app.allinonereminder.model.Occurrence
import app.allinonereminder.model.OccurrenceStatus
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Completes exercise occurrences the phone already knows were done. Completion goes through the
 * one shared mark-taken path ([OccurrenceRepository.markOccurrenceTaken]) so points and stock stay
 * consistent. Callers must run `AlarmCoordinator.reschedule()` afterwards.
 */
@Singleton
class ExerciseVerifier @Inject constructor(
    private val activitySource: ActivitySource,
    private val itemRepository: ItemRepository,
    private val occurrenceRepository: OccurrenceRepository,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val NOTE = "Verified by Health Connect"
        private val OPEN = setOf(
            OccurrenceStatus.PENDING,
            OccurrenceStatus.SNOOZED,
            OccurrenceStatus.DEFERRED_MEAL
        )
    }

    private suspend fun isActive(): Boolean =
        settingsRepository.settingsFlow.first().healthConnectVerifyEnabled &&
            activitySource.availability() == HealthAvailability.AVAILABLE &&
            activitySource.hasExercisePermission()

    /** Returns the ids of the candidates that were satisfied and are now marked TAKEN. */
    suspend fun completeSatisfied(candidates: List<Occurrence>, now: Instant = Instant.now()): Set<String> {
        val open = candidates.filter { it.status in OPEN }
        if (open.isEmpty() || !isActive()) return emptySet()

        val done = mutableSetOf<String>()
        for (occ in open) {
            val item = itemRepository.getItemSync(occ.itemId) ?: continue
            val target = item.exerciseTargetMinutes ?: continue
            if (item.type != ItemType.EXERCISE) continue

            val dueAt = Instant.ofEpochMilli(occ.dueAt)
            val spans = activitySource.exerciseSpans(
                ExerciseRule.windowStart(dueAt),
                ExerciseRule.windowEnd(dueAt, item.graceMinutes)
            )
            if (ExerciseRule.isSatisfied(spans, dueAt, item.graceMinutes, target) &&
                occurrenceRepository.markOccurrenceTaken(occ.id, now.toEpochMilli(), NOTE)
            ) {
                done.add(occ.id)
            }
        }
        return done
    }

    /** Used by the periodic worker: every open exercise occurrence. */
    suspend fun completePending(now: Instant = Instant.now()): Set<String> =
        completeSatisfied(occurrenceRepository.getUnresolvedSync(), now)
}

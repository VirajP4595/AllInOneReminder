package app.allinonereminder.sync

/**
 * Deterministic view over append-only occurrence events.
 *
 * The earliest TAKEN/SKIPPED per occurrence (by `actedAt`, then `eventId`) wins. Every later action
 * for that occurrence is rendered as [ActionKind.DUPLICATE_ACTION] and keeps a pointer to the
 * winning event, so history is never lost.
 */
class ActionLog(actions: List<OccurrenceAction>) {
    private val actions: List<OccurrenceAction> = actions.sortedWith(
        compareBy({ it.occurrenceRef }, { it.actedAt }, { it.eventId }),
    )

    private val winners: Map<String, OccurrenceAction> = this.actions
        .filter { it.kind == ActionKind.TAKEN || it.kind == ActionKind.SKIPPED }
        .groupBy { it.occurrenceRef }
        .mapValues { (_, group) -> group.minWith(compareBy({ it.actedAt }, { it.eventId })) }

    fun winner(occurrenceRef: String): OccurrenceAction? = winners[occurrenceRef]

    /** All events, with losing actions converted to DUPLICATE_ACTION. */
    fun effectiveActions(): List<OccurrenceAction> = actions.map { action ->
        if (action.kind == ActionKind.DUPLICATE_ACTION) return@map action
        val winner = winners[action.occurrenceRef]
        if (winner == null || winner.eventId == action.eventId) {
            action
        } else {
            action.copy(
                kind = ActionKind.DUPLICATE_ACTION,
                duplicateOfEventId = winner.eventId,
            )
        }
    }

    /** Latest winning TAKEN time for an item, across every device, for the double-dose guard. */
    fun lastTakenAt(itemId: String): Long? = actions
        .filter { it.itemId == itemId && it.kind == ActionKind.TAKEN }
        .filter { winners[it.occurrenceRef]?.eventId == it.eventId }
        .maxOfOrNull { it.actedAt }

    fun actions(): List<OccurrenceAction> = actions.toList()
}

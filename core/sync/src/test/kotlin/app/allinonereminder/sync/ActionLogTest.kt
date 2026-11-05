package app.allinonereminder.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionLogTest {
    private fun action(
        id: String,
        kind: ActionKind,
        actedAt: Long,
        occurrence: String = "occurrence-1",
        item: String = "item-1",
    ) = OccurrenceAction(id, occurrence, item, kind, actedAt, "device-$id")

    @Test
    fun `earliest action wins and later actions become duplicates`() {
        val log = ActionLog(listOf(action("b", ActionKind.TAKEN, 900), action("a", ActionKind.TAKEN, 1_000)))

        assertEquals("b", log.winner("occurrence-1")!!.eventId)
        val effective = log.effectiveActions()
        assertEquals(ActionKind.TAKEN, effective.single { it.eventId == "b" }.kind)
        assertEquals(ActionKind.DUPLICATE_ACTION, effective.single { it.eventId == "a" }.kind)
        assertEquals("b", effective.single { it.eventId == "a" }.duplicateOfEventId)
        assertEquals(900, log.lastTakenAt("item-1"))
    }

    @Test
    fun `an earlier skip beats a later take`() {
        val log = ActionLog(
            listOf(
                action("take", ActionKind.TAKEN, 1_000),
                action("skip", ActionKind.SKIPPED, 800),
            ),
        )

        assertEquals("skip", log.winner("occurrence-1")!!.eventId)
        assertNull(log.lastTakenAt("item-1"))
    }

    @Test
    fun `last taken at ignores duplicate takes and other items`() {
        val log = ActionLog(
            listOf(
                action("early", ActionKind.TAKEN, 500, occurrence = "occ-1", item = "item-1"),
                action("late", ActionKind.TAKEN, 900, occurrence = "occ-1", item = "item-1"),
                action("other", ActionKind.TAKEN, 2_000, occurrence = "occ-2", item = "item-2"),
                action("dupe-kind", ActionKind.DUPLICATE_ACTION, 3_000, occurrence = "occ-2", item = "item-2"),
            ),
        )

        assertEquals(500, log.lastTakenAt("item-1"))
        assertEquals(2_000, log.lastTakenAt("item-2"))
        assertNull(log.lastTakenAt("unknown-item"))
    }

    @Test
    fun `tie on actedAt is broken by event id`() {
        val log = ActionLog(
            listOf(
                action("z", ActionKind.TAKEN, 100),
                action("a", ActionKind.TAKEN, 100),
            ),
        )

        assertEquals("a", log.winner("occurrence-1")!!.eventId)
    }

    @Test
    fun `unknown occurrence and already duplicate events are left alone`() {
        val duplicate = OccurrenceAction(
            "d",
            "occ-1",
            "item-1",
            ActionKind.DUPLICATE_ACTION,
            50,
            "device-d",
            "winner",
        )
        val log = ActionLog(listOf(action("winner", ActionKind.TAKEN, 100), duplicate))

        assertNull(log.winner("missing"))
        val effective = log.effectiveActions()
        assertEquals(duplicate, effective.single { it.eventId == "d" })
        assertEquals("winner", effective.single { it.eventId == "winner" }.eventId)
    }

    @Test
    fun `empty log is safe`() {
        val log = ActionLog(emptyList())

        assertNull(log.winner("anything"))
        assertNull(log.lastTakenAt("anything"))
        assertTrue(log.effectiveActions().isEmpty())
        assertTrue(log.actions().isEmpty())
    }

    @Test
    fun `actions are returned in deterministic order`() {
        val log = ActionLog(
            listOf(
                action("c", ActionKind.TAKEN, 300, occurrence = "occ-2"),
                action("a", ActionKind.TAKEN, 100, occurrence = "occ-1"),
                action("b", ActionKind.TAKEN, 200, occurrence = "occ-1"),
            ),
        )

        assertEquals(listOf("a", "b", "c"), log.actions().map { it.eventId })
    }
}

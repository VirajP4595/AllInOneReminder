package app.allinonereminder.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConflictTest {
    private val key = testRow().key

    private fun syncedDevices(): Triple<FakeServer, InMemoryLocalStore, InMemoryLocalStore> {
        val server = FakeServer()
        val phoneA = InMemoryLocalStore()
        val phoneB = InMemoryLocalStore()
        phoneA.put(testRow(version = 1, updatedAt = 100, payload = "base", dirty = true))
        engine(phoneA, server, "device-a").syncUntilIdle()
        engine(phoneB, server, "device-b").syncUntilIdle()
        return Triple(server, phoneA, phoneB)
    }

    @Test
    fun `concurrent edit wins by updatedAt regardless of push order`() {
        val (server, phoneA, phoneB) = syncedDevices()
        val a = engine(phoneA, server, "device-a")
        val b = engine(phoneB, server, "device-b")

        phoneA.put(testRow(version = 2, updatedAt = 200, device = "device-a", payload = "a"))
        phoneB.put(testRow(version = 2, updatedAt = 300, device = "device-b", payload = "b"))
        b.syncUntilIdle()
        a.syncUntilIdle()

        assertEquals("b", payloadOf(phoneA.get(key)))
        assertEquals("b", payloadOf(phoneB.get(key)))

        val (server2, phoneA2, phoneB2) = syncedDevices()
        val a2 = engine(phoneA2, server2, "device-a")
        val b2 = engine(phoneB2, server2, "device-b")
        phoneA2.put(testRow(version = 2, updatedAt = 300, device = "device-a", payload = "a"))
        phoneB2.put(testRow(version = 2, updatedAt = 200, device = "device-b", payload = "b"))
        b2.syncUntilIdle()
        a2.syncUntilIdle()
        b2.syncUntilIdle()

        assertEquals("a", payloadOf(phoneA2.get(key)))
        assertEquals("a", payloadOf(phoneB2.get(key)))
        assertEquals(3, server2.row(key)!!.version)
    }

    @Test
    fun `delete versus edit race is resolved by last writer wins`() {
        val (server, phoneA, phoneB) = syncedDevices()

        phoneA.put(testRow(version = 2, updatedAt = 200, deleted = true, payload = ""))
        phoneB.put(testRow(version = 2, updatedAt = 300, payload = "still here"))
        engine(phoneB, server, "device-b").syncUntilIdle()
        engine(phoneA, server, "device-a").syncUntilIdle()

        val winner = phoneA.get(key)!!
        assertFalse(winner.deleted)
        assertEquals("still here", payloadOf(winner))

        val (server2, phoneA2, phoneB2) = syncedDevices()
        phoneA2.put(testRow(version = 2, updatedAt = 400, deleted = true, payload = ""))
        phoneB2.put(testRow(version = 2, updatedAt = 300, payload = "older edit"))
        engine(phoneB2, server2, "device-b").syncUntilIdle()
        engine(phoneA2, server2, "device-a").syncUntilIdle()
        engine(phoneB2, server2, "device-b").syncUntilIdle()

        assertTrue(phoneA2.get(key)!!.deleted)
        assertTrue(phoneB2.get(key)!!.deleted)
    }

    @Test
    fun `ten minute clock skew does not lose writes`() {
        val (server, phoneA, phoneB) = syncedDevices()
        val skew = 10L * 60 * 1000
        val base = 10_000_000L

        phoneA.put(testRow(version = 2, updatedAt = base + skew, device = "device-a", payload = "a"))
        phoneB.put(testRow(version = 2, updatedAt = base, device = "device-b", payload = "b"))
        engine(phoneA, server, "device-a").syncUntilIdle()
        engine(phoneB, server, "device-b").syncUntilIdle()
        engine(phoneA, server, "device-a").syncUntilIdle()

        assertEquals("a", payloadOf(phoneA.get(key)))
        assertEquals("a", payloadOf(phoneB.get(key)))
        assertFalse(server.allRows().isEmpty())
    }

    @Test
    fun `three devices converge including an earlier take event`() {
        val server = FakeServer()
        val phoneA = InMemoryLocalStore()
        val phoneB = InMemoryLocalStore()
        val phoneC = InMemoryLocalStore()
        val a = engine(phoneA, server, "device-a")
        val b = engine(phoneB, server, "device-b")
        val c = engine(phoneC, server, "device-c")

        phoneA.put(testRow(version = 1, updatedAt = 100, payload = "base"))
        a.syncUntilIdle()
        b.syncUntilIdle()
        c.syncUntilIdle()

        val eventA = OccurrenceAction("event-a", "occurrence-1", "item-1", ActionKind.TAKEN, 1_000, "device-a")
        val eventB = OccurrenceAction("event-b", "occurrence-1", "item-1", ActionKind.TAKEN, 900, "device-b")
        phoneA.put(actionRow(eventA, version = 1))
        phoneB.put(actionRow(eventB, version = 1))
        phoneC.put(testRow(version = 2, updatedAt = 150, device = "device-c", payload = "c-edit"))

        repeat(3) {
            c.syncUntilIdle()
            b.syncUntilIdle()
            a.syncUntilIdle()
        }

        assertEquals("c-edit", payloadOf(phoneA.get(key)))
        assertEquals("c-edit", payloadOf(phoneB.get(key)))
        assertEquals("c-edit", payloadOf(phoneC.get(key)))

        val log = a.actionLog()
        assertEquals("event-b", log.winner("occurrence-1")!!.eventId)
        assertEquals(900, log.lastTakenAt("item-1"))
        assertEquals(ActionKind.DUPLICATE_ACTION, log.effectiveActions().single { it.eventId == "event-a" }.kind)
        assertEquals(2, phoneA.allRows().count { it.key.entityType == EntityTypes.OCCURRENCE_ACTION })
    }

    @Test
    fun `same version same time falls back to device id order`() {
        val (server, phoneA, phoneB) = syncedDevices()
        phoneA.put(testRow(version = 2, updatedAt = 500, device = "device-b", payload = "b"))
        phoneB.put(testRow(version = 2, updatedAt = 500, device = "device-c", payload = "c"))
        engine(phoneA, server, "device-a").syncUntilIdle()
        engine(phoneB, server, "device-b").syncUntilIdle()
        engine(phoneA, server, "device-a").syncUntilIdle()

        assertEquals("c", payloadOf(phoneA.get(key)))
    }
}

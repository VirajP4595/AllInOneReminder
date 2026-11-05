package app.allinonereminder.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class SyncEngineTest {
    @Test
    fun `pushes dirty rows and pulls them on another device`() {
        val server = FakeServer()
        val phoneA = InMemoryLocalStore()
        val phoneB = InMemoryLocalStore()
        val a = engine(phoneA, server, "device-a")
        val b = engine(phoneB, server, "device-b")

        phoneA.put(testRow(payload = "Metformin 500"))
        a.syncUntilIdle()
        b.syncUntilIdle()

        assertEquals("Metformin 500", payloadOf(phoneB.get(testRow().key)))
        assertEquals(1, server.allRows().size)
    }

    @Test
    fun `push batches dirty rows`() {
        val server = FakeServer()
        val phone = InMemoryLocalStore()
        repeat(5) { index ->
            phone.put(testRow(rowId = "row-$index", updatedAt = index.toLong()))
        }
        val engine = engine(phone, server, "device-a", batchSize = 2)

        val stats = engine.pushOnce()

        assertEquals(5, stats.pushed)
        assertEquals(0, stats.rejected)
        assertEquals(3, stats.rounds)
        assertEquals(5, server.allRows().size)
    }

    @Test
    fun `pull pages through more rows than the batch size`() {
        val server = FakeServer()
        val phoneA = InMemoryLocalStore()
        repeat(5) { index ->
            phoneA.put(testRow(rowId = "row-$index", updatedAt = index.toLong()))
        }
        engine(phoneA, server, "device-a").pushOnce()

        val phoneB = InMemoryLocalStore()
        val stats = engine(phoneB, server, "device-b", batchSize = 2).pullOnce()

        assertEquals(5, stats.applied)
        assertEquals(3, stats.pages)
        assertEquals(5, stats.cursor)
        assertEquals(5, phoneB.allRows().size)
        assertEquals(5, phoneB.lastPulledSeq())
    }

    @Test
    fun `stale version rejection pulls merges and retries with a higher version`() {
        val server = FakeServer()
        val phoneA = InMemoryLocalStore()
        val phoneB = InMemoryLocalStore()
        val a = engine(phoneA, server, "device-a")
        val b = engine(phoneB, server, "device-b")

        phoneA.put(testRow(version = 1, updatedAt = 100, payload = "base"))
        a.syncUntilIdle()
        b.syncUntilIdle()

        phoneB.put(testRow(version = 2, updatedAt = 300, device = "device-b", payload = "b-edit"))
        b.syncUntilIdle()
        phoneA.put(testRow(version = 2, updatedAt = 400, device = "device-a", payload = "a-edit"))

        val stats = a.pushOnce()

        assertEquals(1, stats.rejected)
        assertEquals("a-edit", payloadOf(phoneA.get(testRow().key)))
        assertEquals(3, server.row(testRow().key)!!.version)
        assertEquals("a-edit", payloadOf(phoneA.get(testRow().key)))
    }

    @Test
    fun `interrupted push is retried without loss or duplication`() {
        val server = FakeServer()
        val phone = InMemoryLocalStore()
        repeat(5) { index ->
            phone.put(testRow(rowId = "row-$index", updatedAt = index.toLong()))
        }
        val engine = engine(phone, server, "device-a", batchSize = 10)
        server.failPushAfter = 2

        assertThrows(IOException::class.java) { engine.pushOnce() }
        assertEquals(2, server.allRows().size)

        engine.syncUntilIdle()

        assertEquals(5, server.allRows().size)
        assertTrue(phone.dirtyRows(10).isEmpty())
        assertEquals(5, phone.allRows().size)
    }

    @Test
    fun `interrupted pull advances the cursor only after the local commit`() {
        val server = FakeServer()
        val phoneA = InMemoryLocalStore()
        repeat(3) { index ->
            phoneA.put(testRow(rowId = "row-$index", updatedAt = index.toLong()))
        }
        engine(phoneA, server, "device-a").pushOnce()

        val phoneB = InMemoryLocalStore()
        val b = engine(phoneB, server, "device-b", batchSize = 10)
        phoneB.failNextPullCommit = true

        assertThrows(IOException::class.java) { b.pullOnce() }
        assertEquals(3, phoneB.allRows().size)
        assertEquals(0, phoneB.lastPulledSeq())
        assertEquals(0, phoneB.commitCalls)

        val stats = b.pullOnce()

        assertEquals(3, stats.applied)
        assertEquals(3, phoneB.allRows().size)
        assertEquals(3, phoneB.lastPulledSeq())
        assertEquals(1, phoneB.commitCalls)
    }

    @Test
    fun `interrupted remote pull can be retried`() {
        val server = FakeServer()
        val phoneA = InMemoryLocalStore()
        phoneA.put(testRow(rowId = "row-1"))
        phoneA.put(testRow(rowId = "row-2", updatedAt = 2))
        engine(phoneA, server, "device-a").pushOnce()

        val phoneB = InMemoryLocalStore()
        val b = engine(phoneB, server, "device-b")
        server.failPullOnCall = 1

        assertThrows(IOException::class.java) { b.pullOnce() }
        assertEquals(0, phoneB.allRows().size)

        b.pullOnce()

        assertEquals(2, phoneB.allRows().size)
    }

    @Test
    fun `a device offline for seven days catches up through pages`() {
        val server = FakeServer()
        val phoneA = InMemoryLocalStore()
        repeat(12) { index ->
            phoneA.put(testRow(rowId = "row-$index", version = 1, updatedAt = index.toLong()))
        }
        engine(phoneA, server, "device-a").syncUntilIdle()

        val phoneOffline = InMemoryLocalStore()
        val offline = engine(phoneOffline, server, "device-offline", batchSize = 5)
        phoneOffline.put(testRow(rowId = "own-row", updatedAt = 50))

        val stats = offline.syncUntilIdle()

        assertEquals(13, stats.pulled) // 12 remote rows plus the device's own write echoed back
        assertEquals(13, phoneOffline.allRows().size)
        assertTrue(phoneOffline.dirtyRows(10).isEmpty())
    }

    @Test
    fun `change log rows are append only and never overwritten`() {
        val server = FakeServer()
        val phoneA = InMemoryLocalStore()
        phoneA.put(testRow(entityType = EntityTypes.CHANGE_LOG, rowId = "log-1", payload = "remote"))
        engine(phoneA, server, "device-a").pushOnce()

        val phoneB = InMemoryLocalStore()
        phoneB.put(
            testRow(
                entityType = EntityTypes.CHANGE_LOG,
                rowId = "log-1",
                payload = "local",
                dirty = true,
            ),
        )
        engine(phoneB, server, "device-b").pullOnce()

        assertEquals(
            "local",
            payloadOf(phoneB.get(testRow(entityType = EntityTypes.CHANGE_LOG, rowId = "log-1").key)),
        )
        assertEquals(1, phoneB.allRows().size)
    }

    @Test
    fun `tombstones purge only after the retention period and every device cursor`() {
        val now = 100L * 24 * 60 * 60 * 1000
        val server = FakeServer()
        val phone = InMemoryLocalStore()
        val old = now - 31L * 24 * 60 * 60 * 1000
        phone.put(
            testRow(
                rowId = "tombstone",
                deleted = true,
                updatedAt = old,
                serverSeq = 5,
                dirty = false,
            ),
        )
        phone.put(
            testRow(
                rowId = "young-tombstone",
                deleted = true,
                updatedAt = now - 10L * 24 * 60 * 60 * 1000,
                serverSeq = 6,
                dirty = false,
            ),
        )
        val engine = engine(phone, server, "device-a")

        phone.markPullCommitted(5)
        server.recordCursor("device-b", 4)
        assertEquals(0, engine.purgeTombstones(now))

        server.recordCursor("device-b", 5)
        assertEquals(1, engine.purgeTombstones(now)) // old tombstone purged, young one survives
        assertNull(phone.get(testRow(rowId = "tombstone").key))
        assertFalse(phone.allRows().any { it.key.rowId == "tombstone" })

        server.recordCursor("device-b", 6)
        assertEquals(0, engine.purgeTombstones(now)) // young tombstone is not 30 days old yet
    }

    @Test
    fun `tombstones without a server sequence are never purged`() {
        val now = 100L * 24 * 60 * 60 * 1000
        val server = FakeServer()
        val phone = InMemoryLocalStore()
        phone.put(
            testRow(
                rowId = "never-synced",
                deleted = true,
                updatedAt = now - 31L * 24 * 60 * 60 * 1000,
                serverSeq = 0,
                dirty = false,
            ),
        )
        phone.markPullCommitted(10)
        server.recordCursor("device-b", 10)

        assertEquals(0, engine(phone, server, "device-a").purgeTombstones(now))
    }

    @Test
    fun `five thousand row initial sync finishes under two seconds`() {
        val server = FakeServer()
        val phoneA = InMemoryLocalStore()
        repeat(5_000) { index ->
            phoneA.put(testRow(rowId = "row-$index", updatedAt = index.toLong()))
        }
        val a = engine(phoneA, server, "device-a", batchSize = 500)
        val phoneB = InMemoryLocalStore()
        val b = engine(phoneB, server, "device-b", batchSize = 500)

        val started = System.nanoTime()
        a.pushOnce()
        b.pullOnce()
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertEquals(5_000, phoneB.allRows().size)
        assertTrue(elapsedMillis < 2_000, "initial sync took ${elapsedMillis}ms")
    }

    @Test
    fun `rejects invalid engine and model arguments`() {
        val local = InMemoryLocalStore()
        val server = FakeServer()
        assertThrows(IllegalArgumentException::class.java) {
            SyncEngine(local, server, FakeRowCipher(), TestActionCodec(), " ", 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyncEngine(local, server, FakeRowCipher(), TestActionCodec(), "device-a", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyncEngine(local, server, FakeRowCipher(), TestActionCodec(), "device-a").syncUntilIdle(0)
        }
        assertThrows(IllegalArgumentException::class.java) { RowKey("", "item", "row") }
        assertThrows(IllegalArgumentException::class.java) {
            OccurrenceAction("", "occ", "item", ActionKind.TAKEN, 1, "device")
        }
        assertThrows(IllegalArgumentException::class.java) {
            OccurrenceAction("event", "occ", "item", ActionKind.TAKEN, -1, "device")
        }
        assertThrows(IllegalArgumentException::class.java) {
            OccurrenceAction("event", "occ", "item", ActionKind.TAKEN, 1, " ")
        }
    }

    @Test
    fun `a row committed late with a lower server sequence is still pulled`() {
        val server = FakeServer()
        val lateRemote = RemoteRow(
            key = RowKey("household-1", "item", "late"),
            version = 1,
            updatedAt = 1,
            updatedByDevice = "device-a",
            deleted = false,
            keyVersion = 1,
            nonce = ByteArray(12) { 7 },
            ciphertext = FakeRowCipher().encrypt(
                "value-late".toByteArray(),
                RowAad("household-1", "item", "late", 1, 1),
            ).ciphertext,
            serverSeq = 0,
        )
        val earlyRemote = lateRemote.copy(
            key = RowKey("household-1", "item", "early"),
            ciphertext = FakeRowCipher().encrypt(
                "value-early".toByteArray(),
                RowAad("household-1", "item", "early", 1, 1),
            ).ciphertext,
        )
        // seq 6 becomes visible first; seq 5 commits afterwards.
        server.commitWithSeq(earlyRemote, 6)

        val reader = InMemoryLocalStore()
        val engine = engine(reader, server, "device-b")
        engine.pullOnce()
        assertEquals(6, reader.lastPulledSeq())
        assertEquals(listOf("early"), reader.allRows().map { it.key.rowId })

        server.commitWithSeq(lateRemote, 5)
        val stats = engine.pullOnce()

        assertEquals(1, stats.applied)
        assertEquals(6, reader.lastPulledSeq())
        assertEquals(setOf("early", "late"), reader.allRows().map { it.key.rowId }.toSet())
    }

    @Test
    fun `replaying the overlap window changes nothing and reports zero applied`() {
        val server = FakeServer()
        val phoneA = InMemoryLocalStore()
        repeat(3) { phoneA.put(testRow(rowId = "row-$it", updatedAt = it.toLong())) }
        engine(phoneA, server, "device-a").pushOnce()
        val phoneB = InMemoryLocalStore()
        val b = engine(phoneB, server, "device-b")

        assertEquals(3, b.pullOnce().applied)
        val second = b.pullOnce()

        assertEquals(0, second.applied)
        assertEquals(3, second.cursor)
        assertEquals(3, phoneB.allRows().size)
    }

    @Test
    fun `a device silent for sixty days no longer holds tombstones back`() {
        val now = 200L * 24 * 60 * 60 * 1000
        val day = 24L * 60 * 60 * 1000
        val server = FakeServer()
        val phone = InMemoryLocalStore()
        phone.put(
            testRow(rowId = "gone", deleted = true, updatedAt = now - 40 * day, serverSeq = 5, dirty = false),
        )
        phone.markPullCommitted(5)
        val engine = engine(phone, server, "device-a")

        server.recordCursor("lost-phone", 0, lastSeenAt = now - 61 * day)
        assertEquals(1, engine.purgeTombstones(now))
    }

    @Test
    fun `a device seen within sixty days still holds tombstones back`() {
        val now = 200L * 24 * 60 * 60 * 1000
        val day = 24L * 60 * 60 * 1000
        val server = FakeServer()
        val phone = InMemoryLocalStore()
        phone.put(
            testRow(rowId = "kept", deleted = true, updatedAt = now - 40 * day, serverSeq = 5, dirty = false),
        )
        phone.markPullCommitted(5)
        val engine = engine(phone, server, "device-a")

        server.recordCursor("slow-phone", 0, lastSeenAt = now - 59 * day)
        assertEquals(0, engine.purgeTombstones(now))
    }
}

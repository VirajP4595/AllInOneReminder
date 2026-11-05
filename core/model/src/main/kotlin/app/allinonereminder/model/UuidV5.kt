package app.allinonereminder.model

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

object UuidV5 {
    // Namespace for All-in-One Reminder occurrence generation
    val OCCURRENCE_NAMESPACE: UUID = UUID.fromString("b3e2c1a0-1234-4567-89ab-cdef01234567")

    fun nameUuidFromBytes(namespace: UUID, name: ByteArray): UUID {
        val md = MessageDigest.getInstance("SHA-1")
        val nsBuffer = ByteBuffer.wrap(ByteArray(16))
        nsBuffer.putLong(namespace.mostSignificantBits)
        nsBuffer.putLong(namespace.leastSignificantBits)

        md.update(nsBuffer.array())
        md.update(name)
        val hash = md.digest()

        // Set version to 5 (0101 in bits 12-15 of time_hi_and_version)
        hash[6] = ((hash[6].toInt() and 0x0f) or 0x50).toByte()
        // Set variant to IETF / RFC 4122 (10 in bits 6-7 of clock_seq_hi_and_reserved)
        hash[8] = ((hash[8].toInt() and 0x3f) or 0x80).toByte()

        val buffer = ByteBuffer.wrap(hash)
        val msb = buffer.long
        val lsb = buffer.long
        return UUID(msb, lsb)
    }

    fun generate(namespace: UUID, name: String): String {
        return nameUuidFromBytes(namespace, name.toByteArray(Charsets.UTF_8)).toString()
    }
}

package app.allinonereminder.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.GeneralSecurityException

class PairingTest {
    private val householdKey = HouseholdKey.fromBytes(ByteArray(32) { (it + 7).toByte() }, version = 5)

    @Test
    fun `two simulated devices pair and agree on the key and verification code`() {
        val inviter = Pairing.createInvite("household-1", "invite-1", "123456")
        val invitee = Pairing.newInviteeKeyPair()

        val sealed = Pairing.sealHouseholdKey(inviter.invite, invitee.publicKey, householdKey)
        val opened = Pairing.openHouseholdKey(inviter.invite, invitee, sealed, keyVersion = 5)

        assertArrayEquals(householdKey.copyKeyBytes(), opened.copyKeyBytes())
        assertEquals(5, opened.version)
        assertEquals(
            Pairing.verificationCode(inviter.invite, invitee.publicKey, sealed),
            Pairing.verificationCode(inviter.invite, invitee.publicKey, sealed),
        )
        assertEquals(8, Pairing.verificationCode(inviter.invite, invitee.publicKey, sealed).length)
    }

    @Test
    fun `verification code differs when the server substitutes the inviter key`() {
        val inviter = Pairing.createInvite("household-1", "invite-1", "123456")
        val invitee = Pairing.newInviteeKeyPair()
        val sealed = Pairing.sealHouseholdKey(inviter.invite, invitee.publicKey, householdKey)
        val honest = Pairing.verificationCode(inviter.invite, invitee.publicKey, sealed)

        val substituted = inviter.invite.copy(
            ephemeralPublicKey = Pairing.newInviteeKeyPair().publicKey,
        )

        assertNotEquals(honest, Pairing.verificationCode(substituted, invitee.publicKey, sealed))
    }

    @Test
    fun `verification code differs when the server substitutes the invitee key`() {
        val inviter = Pairing.createInvite("household-1", "invite-1", "123456")
        val invitee = Pairing.newInviteeKeyPair()
        val sealed = Pairing.sealHouseholdKey(inviter.invite, invitee.publicKey, householdKey)
        val honest = Pairing.verificationCode(inviter.invite, invitee.publicKey, sealed)

        assertNotEquals(
            honest,
            Pairing.verificationCode(inviter.invite, Pairing.newInviteeKeyPair().publicKey, sealed),
        )
    }

    @Test
    fun `verification code changes when the sealed key changes`() {
        val inviter = Pairing.createInvite("household-1", "invite-1", "123456")
        val invitee = Pairing.newInviteeKeyPair()
        val sealed = Pairing.sealHouseholdKey(inviter.invite, invitee.publicKey, householdKey)
        val otherSealed = Pairing.sealHouseholdKey(inviter.invite, invitee.publicKey, householdKey)

        assertNotEquals(
            Pairing.verificationCode(inviter.invite, invitee.publicKey, sealed),
            Pairing.verificationCode(inviter.invite, invitee.publicKey, otherSealed),
        )
    }

    @Test
    fun `QR payload round trips`() {
        val invite = Pairing.createInvite("household-1", "invite-1", "654321").invite
        val parsed = PairingInvite.fromQrPayload(invite.toQrPayload())

        assertEquals(invite.householdId, parsed.householdId)
        assertEquals(invite.inviteId, parsed.inviteId)
        assertEquals(invite.code, parsed.code)
        assertArrayEquals(invite.ephemeralPublicKey, parsed.ephemeralPublicKey)
    }

    @Test
    fun `rejects malformed QR payloads`() {
        assertThrows(IllegalArgumentException::class.java) {
            PairingInvite.fromQrPayload("airpair:v1:only:three:parts")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingInvite.fromQrPayload("something:v1:a:b:AAAA:123456")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingInvite.fromQrPayload("airpair:v2:a:b:AAAA:123456")
        }
    }

    @Test
    fun `validates invite fields`() {
        val publicKey = ByteArray(32)
        assertThrows(IllegalArgumentException::class.java) {
            PairingInvite(" ", "invite", "123456", publicKey)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingInvite("household", " ", "123456", publicKey)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingInvite("household", "invite", "12345", publicKey)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingInvite("household", "invite", "1234567", publicKey)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingInvite("household", "invite", "abcdef", publicKey)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingInvite("household", "invite", "123456", ByteArray(31))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingInvite("house:hold", "invite", "123456", publicKey)
        }
    }

    @Test
    fun `rejects an invitee key of the wrong length`() {
        val invite = Pairing.createInvite("household", "invite", "123456").invite

        assertThrows(IllegalArgumentException::class.java) {
            Pairing.sealHouseholdKey(invite, ByteArray(31), householdKey)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Pairing.verificationCode(invite, ByteArray(31), ByteArray(10))
        }
    }

    @Test
    fun `rejects a tampered sealed key`() {
        val inviter = Pairing.createInvite("household", "invite", "123456")
        val invitee = Pairing.newInviteeKeyPair()
        val sealed = Pairing.sealHouseholdKey(inviter.invite, invitee.publicKey, householdKey)
        sealed[sealed.size / 2] = (sealed[sealed.size / 2].toInt() xor 1).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            Pairing.openHouseholdKey(inviter.invite, invitee, sealed, keyVersion = 5)
        }
    }

    @Test
    fun `rejects opening with the wrong invitee key`() {
        val inviter = Pairing.createInvite("household", "invite", "123456")
        val invitee = Pairing.newInviteeKeyPair()
        val sealed = Pairing.sealHouseholdKey(inviter.invite, invitee.publicKey, householdKey)

        assertThrows(GeneralSecurityException::class.java) {
            Pairing.openHouseholdKey(
                inviter.invite,
                Pairing.newInviteeKeyPair(),
                sealed,
                keyVersion = 5,
            )
        }
    }

    @Test
    fun `rejects opening under a different invite`() {
        val inviter = Pairing.createInvite("household", "invite", "123456")
        val invitee = Pairing.newInviteeKeyPair()
        val sealed = Pairing.sealHouseholdKey(inviter.invite, invitee.publicKey, householdKey)
        val otherInvite = inviter.invite.copy(inviteId = "invite-2")

        assertThrows(GeneralSecurityException::class.java) {
            Pairing.openHouseholdKey(otherInvite, invitee, sealed, keyVersion = 5)
        }
    }
}

package app.allinonereminder.crypto

import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HpkePrivateKey
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.hybrid.internal.HpkeDecrypt
import com.google.crypto.tink.hybrid.internal.HpkeEncrypt
import com.google.crypto.tink.hybrid.internal.HpkeKeyCreator
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.util.Bytes
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

/**
 * QR payload shown by the existing device when it invites a new one.
 *
 * Format: `airpair:v1:<householdId>:<inviteId>:<base64url(inviter X25519 public key)>:<6-digit code>`.
 */
data class PairingInvite(
    val householdId: String,
    val inviteId: String,
    val code: String,
    val ephemeralPublicKey: ByteArray,
) {
    init {
        require(householdId.isNotBlank() && inviteId.isNotBlank()) { "invite ids must not be blank" }
        require(CODE_REGEX.matches(code)) { "pairing code must be exactly six digits" }
        require(ephemeralPublicKey.size == X25519_PUBLIC_KEY_BYTES) {
            "ephemeral public key must be $X25519_PUBLIC_KEY_BYTES bytes"
        }
        require(':' !in householdId && ':' !in inviteId) { "invite ids must not contain ':'" }
    }

    fun toQrPayload(): String = listOf(
        QR_PREFIX,
        QR_VERSION,
        householdId,
        inviteId,
        Base64Url.encode(ephemeralPublicKey),
        code,
    ).joinToString(":")

    companion object {
        const val X25519_PUBLIC_KEY_BYTES: Int = 32
        const val QR_PREFIX: String = "airpair"
        const val QR_VERSION: String = "v1"
        private val CODE_REGEX = Regex("^[0-9]{6}$")

        fun fromQrPayload(payload: String): PairingInvite {
            val parts = payload.split(':')
            require(parts.size == 6 && parts[0] == QR_PREFIX && parts[1] == QR_VERSION) {
                "not a supported pairing QR payload"
            }
            return PairingInvite(
                householdId = parts[2],
                inviteId = parts[3],
                ephemeralPublicKey = Base64Url.decode(parts[4]),
                code = parts[5],
            )
        }
    }
}

/** The new device's ephemeral X25519 key pair. */
class InviteeKeyPair internal constructor(
    val publicKey: ByteArray,
    internal val privateKey: HpkePrivateKey,
) {
    init {
        require(publicKey.size == PairingInvite.X25519_PUBLIC_KEY_BYTES) {
            "public key must be ${PairingInvite.X25519_PUBLIC_KEY_BYTES} bytes"
        }
    }
}

/** The existing device's invite, including the private key that never leaves the device. */
class InviteSession internal constructor(
    val invite: PairingInvite,
    internal val privateKey: HpkePrivateKey,
)

/**
 * X25519 HPKE pairing (DHKEM X25519 + HKDF-SHA256 + AES-256-GCM) and the out-of-band verification
 * code derived from the handshake transcript.
 */
object Pairing {
    private const val HKDF_INFO = "allinonereminder-pairing-verification-v1"
    private val SALT = "allinonereminder-pairing-v1".toByteArray(StandardCharsets.UTF_8)

    val HPKE_PARAMETERS: HpkeParameters = HpkeParameters.builder()
        .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
        .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
        .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
        .setVariant(HpkeParameters.Variant.NO_PREFIX)
        .build()

    /** Existing device: create the invite that is shown as a QR code. */
    fun createInvite(householdId: String, inviteId: String, code: String): InviteSession {
        val privateKey = HpkeKeyCreator.createKey(HPKE_PARAMETERS, null)
        val publicKey = privateKey.publicKey.publicKeyBytes.toByteArray()
        return InviteSession(PairingInvite(householdId, inviteId, code, publicKey), privateKey)
    }

    /** New device: generate its ephemeral key pair before posting the public key to the invite. */
    fun newInviteeKeyPair(): InviteeKeyPair {
        val privateKey = HpkeKeyCreator.createKey(HPKE_PARAMETERS, null)
        return InviteeKeyPair(privateKey.publicKey.publicKeyBytes.toByteArray(), privateKey)
    }

    /** Existing device: seal the household key to the new device's ephemeral public key. */
    fun sealHouseholdKey(
        invite: PairingInvite,
        inviteePublicKey: ByteArray,
        householdKey: HouseholdKey,
    ): ByteArray {
        val aad = handshakeAad(invite, inviteePublicKey)
        val publicKey = HpkePublicKey.create(
            HPKE_PARAMETERS,
            Bytes.copyFrom(inviteePublicKey.copyOf()),
            null,
        )
        return HpkeEncrypt.create(publicKey).encrypt(
            householdKey.copyKeyBytes(),
            aad,
        )
    }

    /** New device: open the sealed household key with its ephemeral private key. */
    fun openHouseholdKey(
        invite: PairingInvite,
        inviteeKeyPair: InviteeKeyPair,
        sealedHouseholdKey: ByteArray,
        keyVersion: Int,
    ): HouseholdKey {
        val decrypted = HpkeDecrypt.create(inviteeKeyPair.privateKey).decrypt(
            sealedHouseholdKey,
            handshakeAad(invite, inviteeKeyPair.publicKey),
        )
        return HouseholdKey.fromBytes(decrypted, keyVersion)
    }

    /**
     * Six-digit code shown by both devices. Including both ephemeral public keys and the sealed key
     * in the transcript means a server that substitutes either key produces different codes.
     */
    fun verificationCode(
        invite: PairingInvite,
        inviteePublicKey: ByteArray,
        sealedHouseholdKey: ByteArray,
    ): String {
        val transcript = handshakeAad(invite, inviteePublicKey) + sealedHouseholdKey
        val derived = Hkdf.computeHkdf(
            "HmacSha256",
            transcript,
            SALT,
            HKDF_INFO.toByteArray(StandardCharsets.UTF_8),
            4,
        )
        val value = ((derived[0].toLong() and 0xff) shl 24) or
            ((derived[1].toLong() and 0xff) shl 16) or
            ((derived[2].toLong() and 0xff) shl 8) or
            (derived[3].toLong() and 0xff)
        return String.format(Locale.ROOT, "%06d", value % 1_000_000L)
    }

    private fun handshakeAad(invite: PairingInvite, inviteePublicKey: ByteArray): ByteArray {
        require(inviteePublicKey.size == PairingInvite.X25519_PUBLIC_KEY_BYTES) {
            "invitee public key must be ${PairingInvite.X25519_PUBLIC_KEY_BYTES} bytes"
        }
        return listOf(
            invite.householdId,
            invite.inviteId,
            Base64Url.encode(invite.ephemeralPublicKey),
            Base64Url.encode(inviteePublicKey),
        ).joinToString("|").toByteArray(StandardCharsets.UTF_8)
    }
}

private object Base64Url {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

    fun decode(text: String): ByteArray = decoder.decode(text)
}

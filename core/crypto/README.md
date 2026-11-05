# core-crypto

Pure Kotlin/JVM encryption for synced household data. No Android, no networking, no logging of key
material. This folder is a standalone Gradle build; it will be folded into the root build after
Phase 1 merges.

## What it does

- **Household key (HK):** 256-bit AES-GCM key with a `key_version`.
- **Rows:** AES-256-GCM (Tink `AesGcmJce`) with associated data
  `household_id|entity_type|row_id|version|key_version`.
- **Photos:** Tink AES-GCM-HKDF streaming AEAD, so large files are processed in segments.
- **Pairing:** X25519 HPKE (DHKEM X25519 + HKDF-SHA256 + AES-256-GCM) seals the HK, plus a 6-digit
  verification code derived from the handshake transcript.
- **Recovery phrase:** 24 BIP-39 English words with checksum, also encodable as a QR payload.
- **Rotation:** a pure lazy sequence that decrypts every row with the current HK and re-encrypts it
  for `key_version + 1`.
- **`KeyWrapper`:** interface only. The Android Keystore implementation lands in P2-B1 (Track B).

## Build and test

```bash
./gradlew test jacocoTestReport jacocoTestCoverageVerification
```

CI: `.github/workflows/core-crypto.yml`. The build fails if line coverage is below 90%.

## Library versions

- Kotlin 2.4.21
- Google Tink 1.24.0 (`com.google.crypto.tink:tink`)
- JUnit Jupiter 6.1.3
- JaCoCo 0.8.16
- Gradle 8.14.3 (wrapper)

## Decisions

- **Tink only for cryptography.** AES-GCM, streaming AEAD, HPKE and HKDF all come from Tink. No
  primitive is implemented by hand. The only JDK primitive used is `MessageDigest("SHA-256")` for
  the BIP-39 checksum, because BIP-39 is a mnemonic encoding format, not an encryption primitive.
- **HPKE parameters are explicit.** Tink's `PredefinedHybridParameters` only ships AES-128-GCM HPKE,
  so this module builds `DHKEM_X25519_HKDF_SHA256 + HKDF_SHA256 + AES_256_GCM` and uses Tink's
  HPKE implementation (`HpkeKeyCreator`/`HpkeEncrypt`/`HpkeDecrypt`).
- **Row nonce is split out.** Tink returns `nonce || ciphertext || tag`. The 12-byte nonce is stored
  in `sync_rows.nonce`; the rest is `sync_rows.ciphertext`. Decryption reassembles Tink's exact wire
  format.
- **Verification code transcript.** The code hashes the household id, invite id, both ephemeral
  public keys and the sealed key. A server that substitutes either public key makes the two devices
  display different codes.
- **No `toString` leaks.** `HouseholdKey`, `RecoveryPhrase` and mnemonic errors never include key
  bytes or words.

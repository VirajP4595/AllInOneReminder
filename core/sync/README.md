# core-sync

Pure Kotlin/JVM sync engine (outbox + pull + conflict rules). No Android, no HTTP client, no
dependency on another module; Track B supplies `LocalStore`, `RemoteStore` and `RowCipher`
implementations.

## Behaviour

- **Outbox:** `SyncEngine.pushOnce()` sends dirty rows in batches, encrypting each row with its
  `Aad`. Accepted rows are marked clean with their `server_seq`.
- **Stale versions:** the server rejects `version <= stored version`; the engine then pulls, merges
  and retries. If the local value still wins LWW, its version is bumped above the server's so the
  retry succeeds.
- **Pull:** fetches pages after `lastPulledSeq`; every row in a page is decrypted before any is
  applied, and the cursor is advanced only after the local commit. A replayed page is idempotent.
- **Conflicts:** last-writer-wins per row by `(version, updated_at, device_id)`.
- **Events:** `occurrence_action` and `change_log` rows are append-only and merged by event id.
  `ActionLog` makes the earliest TAKEN/SKIPPED per occurrence the winner; later actions are
  `DUPLICATE_ACTION` with a pointer to the winner. `SyncEngine.lastTakenAt(itemId)` feeds the
  cross-device double-dose guard.
- **Tombstones:** purge only when the tombstone is 30 days old **and** every active device's
  `last_pull_seq` is at or past its `server_seq`.
- **Batching:** push rounds and pull pages are bounded; `syncUntilIdle()` repeats until no dirty
  rows or new remote rows remain.

## Build and test

```bash
./gradlew test jacocoTestReport jacocoTestCoverageVerification
```

CI: `.github/workflows/core-sync.yml`; the build fails below 90% line coverage.

## Library versions

- Kotlin 2.4.21
- JUnit Jupiter 6.1.3
- JaCoCo 0.8.16
- Gradle 8.14.3 (wrapper)

## Decisions

- **Types are local.** `RowKey`, `RowAad`, `EncryptedRow`, `RowCipher`, `LocalRow` and `RemoteRow`
  mirror the Phase 1/`/core/crypto` shapes but live here so the module is a standalone build.
  Track B maps them one-to-one when the module joins the root build.
- **`OccurrenceAction` is decoded through `ActionCodec`.** The engine never guesses a serialization
  format; Track B supplies the app's codec.
- **Duplicate actions are derived, not destructive.** The original event row is never rewritten
  during sync; `ActionLog.effectiveActions()` exposes the duplicate view. This keeps the append-only
  log immutable and makes replay idempotent.
- **Clock skew is data, not local time.** The engine never uses the wall clock for conflict
  resolution; only the supplied `updated_at`/`acted_at` values participate. The clock is injected
  solely for tombstone age.
- **No networking, no health data in logs.** Nothing in this module logs payloads or identifiers.

# Phases 2–4 — Build spec and work breakdown

Status: **planned, not started.** Phase 1 (PRs #8–#14) is still in review. This document is the binding spec for the GitHub issues labelled `phase-2`, `phase-3` and `phase-4`. Context and product decisions live in `docs/PLAN.md` §0–§11; this file refines §2, §4, §6 and §8 into buildable work.

Where to find this file: branch `claude/great-goldberg-dn0f1n` until the owner merges it to `main`. Agents: `git fetch origin claude/great-goldberg-dn0f1n` and read it with `git show origin/claude/great-goldberg-dn0f1n:docs/ROADMAP_P2_P4.md`. Read it; don't merge that branch.

## 0. How the work is split

| Track | Can start | Why |
|---|---|---|
| **Track A — independent modules** | **Now**, in parallel with the Phase 1 fixes | Pure Kotlin/JVM modules and a standalone backend. They don't touch `/app`, `/core/data` or `/core/schedule`, which Phase 1 is still changing. |
| **Track B — Android integration** | **Only after Phase 1 is merged to `main`** and v0.1.x passes the owner's device tests | Depends on Phase 1's DB schema (moving to v2 with `remindAt`), `AlarmCoordinator` and the UI |

Rules for every Track A issue:
- Work on branches named `p2/<issue#>-<slug>` (or `p3/`, `p4/`), with **`main` as the base**.
- Do not touch `/app`, `/core/data`, `/core/schedule` or `/core/model`. If you need a shared type, define it inside your own module and note the future mapping in your PR.
- **Backend issues** (`/backend/supabase`) need no Gradle changes.
- **Kotlin module issues:** `main` doesn't contain the Gradle build yet (it's in the unmerged Phase 1 stack). Make the module a **standalone Gradle build** inside its folder (its own `settings.gradle.kts`, Gradle wrapper and version catalog; Kotlin/JVM only, no Android plugin), plus its own CI workflow `.github/workflows/<module>.yml` that runs `./gradlew test` and a coverage check in that folder. The reviewer will fold it into the root build after Phase 1 merges.
- Don't edit `docs/DECISIONS.md`, `docs/TESTING.md` or `README.md` (Phase 1 changes them). Put decisions in the module's own `README.md` under "Decisions".

Global rules (all phases):
- $0 services only.
- Never push to `main`; never merge your own PR; never weaken or skip tests.
- No analytics, crash-reporting or ads SDKs. Never log health data, keys or passphrases.
- Every PR follows `.github/pull_request_template.md`. Every claim in a PR has `path:line` + the name of a test that exists (check with grep before posting).

**Phase 2 changes one Phase 1 rule:** the `INTERNET` permission is allowed **only** from issue P2-B2 onward, and only for Supabase + FCM hosts. Track A Kotlin modules must contain no networking code; the backend is its own deployable.

---

## 1. Phase 2 — Family sync, pairing, escalation

### 1.1 Threat model and data exposure
- Supabase stores **ciphertext** for all health data (profiles, items, schedules, photos, logs, vitals). Supabase and Google cannot read medicine names, doses, photos or notes.
- The server **can** see:
  - household membership (Google account emails)
  - device FCM tokens
  - row counts and sizes
  - **escalation slots**: opaque random refs, due time, grace minutes, done time.
  So the server learns *when* doses are due and taken, but not *what*. This is accepted.
- Losing every paired device **and** the recovery phrase = permanent data loss. This is by design.

### 1.2 Backend (`/backend/supabase`, Supabase CLI project)
Tables (RLS on all; access only for active members of the row's `household_id`):

| Table | Columns | Notes |
|---|---|---|
| `households` | id uuid, created_at, created_by | |
| `members` | household_id, user_id (auth.users), display_name_cipher, joined_at, removed_at | Any member can edit (PLAN §0) |
| `invites` | id, household_id, code_hash, inviter_pubkey, invitee_pubkey, sealed_key, expires_at, used_at, created_by | 6-digit code or QR; code stored as a hash; single use; 15 min expiry; carries the pairing handshake (§1.3) |
| `devices` | id, user_id, household_id, fcm_token, platform, app_version, last_heartbeat_at, created_at | |
| `device_profile_prefs` | device_id, profile_ref, mode (`ALARM`,`NOTIFICATION`,`MISSED_ONLY`,`OFF`) | `profile_ref` = opaque random id, never the name |
| `sync_rows` | household_id, entity_type, row_id, version bigint, updated_at, updated_by_device, deleted bool, key_version int, nonce bytea, ciphertext bytea, server_seq bigserial | PK (household_id, entity_type, row_id). Pull cursor = `server_seq`. Reject writes where `version <=` the stored version |
| `escalation_slots` | id, household_id, occurrence_ref, profile_ref, owner_device_id, due_at, grace_minutes, critical bool, done_at, escalated_at | Written by the patient's device for its upcoming occurrences (next 48 h). Opaque refs only |
| Storage bucket `photos` | path `household_id/<random>.bin` | Encrypted bytes only; bucket policy = household members |

Server functions:
- `escalate` (Edge Function, every 5 min via `pg_cron` + `pg_net`):
  1. Select slots where `done_at is null and escalated_at is null and now() > due_at + grace_minutes`.
  2. For each slot, find devices in the household whose pref for that `profile_ref` is not `OFF`, excluding `owner_device_id`, and send an FCM HTTP v1 **data** message `{type:"missed", occurrence_ref, profile_ref}`.
  3. Set `escalated_at`. This must be idempotent: a re-run never double-sends.
- `heartbeat-watch` (same cron): if a device that owns open slots has `last_heartbeat_at` older than 24 h, push `{type:"device_silent", device_id}` to the other members, at most once per 24 h per device.
- Secrets: the FCM service-account JSON lives only in `supabase secrets`. **Never** in the repo or the app.
- Tests:
  - SQL tests for every RLS policy (pgTAP via `supabase test db`): members can read/write their household's rows; non-members read 0 rows and writes are rejected; removed members lose access; invites are single-use and expire.
  - Deno tests for both Edge Functions with a mocked FCM endpoint: idempotency, exclusion of the owner device, `OFF` prefs respected, once-per-24h silence alerts.

### 1.3 Crypto (`/core/crypto`, pure Kotlin/JVM)
- Library: **Google Tink** (Apache-2.0, offline). No hand-rolled primitives.
- **Household key (HK):** 256-bit. Rows use AES-256-GCM (Tink AEAD) with associated data = `household_id|entity_type|row_id|version|key_version`. A row copied to another id or version must fail to decrypt.
- **Photos:** Tink streaming AEAD (AES-GCM-HKDF streaming), so large files never sit fully in memory.
- **Pairing:**
  1. The existing device shows a QR code with `household_id`, `invite_id`, an ephemeral X25519 public key and the 6-digit code.
  2. The new device posts its ephemeral public key to the invite.
  3. The existing device seals HK to that key with Tink HPKE (DHKEM X25519 + HKDF-SHA256 + AES-256-GCM).
  4. Both devices show a 6-digit **verification code** derived from the handshake transcript, and the user confirms they match. This stops a server-in-the-middle attack.
- **Recovery phrase:** HK exported as 24 words (BIP-39 English wordlist, checksum) and as a QR code. Restore = enter the phrase on a new device.
- **Rotation:** removing a member → HK v+1 → re-encrypt all rows (the household is small) → `key_version` bumps. Expose this as a pure function over an iterator of rows.
- Local storage of HK is wrapped by the Android Keystore; that's Track B. This module exposes a `KeyWrapper` interface only.
- Tests:
  - round trips
  - wrong associated data / swapped row id / changed version → failure
  - tampered ciphertext → failure
  - HPKE pairing between two simulated devices
  - verification code equal on both sides, and different when the server substitutes a key
  - mnemonic round trip, and a single-word typo → checksum failure
  - rotation re-encrypts every row, and old ciphertext doesn't decrypt under the new key version
  - Tink test vectors where available

### 1.4 Sync engine (`/core/sync`, pure Kotlin/JVM)
- Interfaces:
  - `LocalStore`: changed rows since the last push; apply remote rows
  - `RemoteStore`: push a batch; pull since `server_seq`
  - `RowCipher`: matches `/core/crypto`'s API; use a fake in tests
  No Android, no HTTP client; Track B supplies the implementations.
- **Outbox:** every local write bumps `version` and marks the row dirty. Push sends the dirty rows; the server rejects stale versions, and the client then pulls, merges and retries.
- **Pull:** fetch after the last `server_seq`, decrypt, apply, advance the cursor only after the local commit.
- **Conflicts:** last-writer-wins per row by the `(version, updated_at, device_id)` total order.
  - **Occurrence actions and change logs are append-only events** (event id = UUID), never overwritten, merged by id.
  - Two devices acting on the same occurrence: the earliest `TAKEN` / `SKIPPED` by `acted_at` wins; the later one becomes `DUPLICATE_ACTION`, kept in history.
  - A `TAKEN` event from another device within `minGap` must surface to the local double-dose guard (expose `lastTakenAt(itemId)` across devices).
- **Tombstones:** `deleted = true` rows sync. Purge locally only after 30 days **and** after every active device's `last_pull_seq` is past the tombstone.
- Tests (in-memory fake server, 2 and 3 simulated devices):
  - concurrent edits of the same row
  - a device offline for 7 days, then sync
  - delete versus edit race
  - duplicate Taken
  - ±10 min clock skew
  - push or pull interrupted mid-batch, then retry (no loss, no duplication)
  - 5,000-row initial sync under 2 s on the JVM

### 1.5 Android integration (Track B — after Phase 1 merges)
- **P2-B1 Account, household, pairing:**
  - Google sign-in via Credential Manager → Supabase Auth (ID-token sign-in).
  - Create a household on first sign-in.
  - Invite = QR + 6-digit code; scan with **ZXing** (on-device; not Google Play ML Kit).
  - HK wrapped in the Keystore.
  - Recovery-phrase screen with forced confirmation (re-enter 3 random words).
  - Members screen: list, remove a member (→ rotation), leave the household.
- **P2-B2 Sync + notification modes:**
  - Add `INTERNET` + `ACCESS_NETWORK_STATE`.
  - Implement `LocalStore` over Room (a DB migration adding `version`, `dirty`, `server_seq`, with a migration test) and `RemoteStore` over Supabase (supabase-kt or Ktor; pick one and record it).
  - WorkManager periodic sync (15 min) plus an immediate sync on local change while online.
  - Photos: encrypt-then-upload; download-then-decrypt.
  - Per-device × profile notification mode:
    - `ALARM`: rings the full alarm locally, like the patient's phone
    - `NOTIFICATION`: a normal notification
    - `MISSED_ONLY`: escalation pushes only
    - `OFF`
  - Write `escalation_slots` for the device's own profiles' occurrences; set `done_at` on Taken or Skipped.
  - Daily heartbeat.
  - "Missed-dose alerts are OFF" banner after 24 h without reaching the backend.
  - **Change alerts:** dose/time/stop changes by another member → a local notification ("Viraj changed Mom's Metformin 500 → 1000 mg").
- **P2-B3 FCM receive:**
  - `firebase-messaging` only (no Analytics / Crashlytics); `google-services.json` committed (client config).
  - `missed` → resolve `occurrence_ref` locally, decrypt, show "Mom missed 09:00 Metformin" (high priority; tap → history).
  - `device_silent` → "Mom's phone hasn't checked in for 24 h — open the app on her phone."
- **P2-B4 SMS fallback (opt-in):**
  - `SEND_SMS` (sideloaded build only).
  - If a CRITICAL dose becomes MISSED and the device has had no network for 30 min, text the locally stored caregiver numbers: "Reminder app: <profile> missed a critical dose at 09:00." No medicine name.
  - Maximum 3 per day. Settings warn that carrier charges may apply.
- **P2-B5 Weekly encrypted export:** reuse `.airbackup`. The user picks a folder once via SAF `ACTION_OPEN_DOCUMENT_TREE` (Google Drive appears as a provider, so no Drive API or keys are needed); weekly WorkManager job; keep the last 4.

### 1.6 Phase 2 end-to-end acceptance (owner, on real phones)
1. Two phones pair via QR; verification codes match; data appears on both within 1 min online.
2. Edit a dose on phone A → phone B updates and shows a change alert.
3. Patient phone off, CRITICAL dose due → the caregiver phone gets the "missed" push between grace + 0 and grace + 10 min.
4. Supabase dashboard shows only ciphertext in `sync_rows` and storage.
5. Remove a member → key rotates → that member's device can't decrypt new rows.
6. Recovery phrase on a factory-reset phone restores everything.

---

## 2. Phase 3 — Engagement and more schedule types

### P3-A1 `/core/gamify` (pure Kotlin, Track A)
Input: occurrence history + item type + criticality + timezone. Output: points ledger, per-item streaks, badges, daily rings. A pure, deterministic function of history (no mutable counters except an optional derived cache).
- Habit items (WATER_DRINK, EXERCISE, HABIT, FOOD, SUPPLEMENT): **10 pts** if TAKEN within ±30 min of `dueAt`, **5 pts** if later but within grace, **0** after grace.
- Medicines/injections: **5 pts** only within ±30 min of `dueAt`; otherwise **0**. **Never** award points for a TAKEN after grace or a `DUPLICATE_ACTION`.
- **Streak** per item = consecutive local days on which every occurrence is TAKEN. A SKIPPED with reason `DOCTOR_STOPPED_IT` neither breaks nor extends the streak. **1 automatic freeze per ISO week per item** covers one missed day.
- **Daily rings:** Meds / Hydration / Movement = % of that day's occurrences of those types completed on time.
- **Badges:**
  - 7/30/100-day streak (per item)
  - "Hydration hero" (7 consecutive days with the hydration ring at 100%)
  - "Perfect week" (all rings at 100% for 7 days)
  - "10k steps" takes a steps input supplied by P3-B4; accept it as an optional parameter.
- Tests:
  - every rule above
  - DST days (23 h / 25 h) in Europe/London and America/New_York
  - a freeze used and then exhausted
  - doctor-stopped skip
  - late-taken med = 0
  - duplicate action = 0
  - 1 year × 20 items computed in < 200 ms on the JVM
  - ≥ 90 % line coverage enforced in this module's CI

### Track B (after Phase 1 merges)
- **P3-B1 Schedule types:**
  - `MONTHLY` (day of month with month-end clamp, or "Nth weekday")
  - `INTERVAL` (every N hours within a waking window, anchored to the first dose)
  - `PRN` (no alarm; "Take now" guarded by `minGap` + `maxPerDay`)
  - `TAPER` (dose amount by date ranges)
  - DB migration + migration test; engine tests including DST for INTERVAL.
- **P3-B2 Stock and refill:**
  - `stockCount`, `unitsPerDose`, `refillThreshold`.
  - Decrement on TAKEN as an append-only event, so two devices never double-decrement.
  - "Refill soon" reminder at the threshold or ≤ 5 days of supply; a "Refilled +N" action.
- **P3-B3 History, progress, widget, TTS:**
  - adherence calendar (colour by % on time)
  - Progress screen (points, streaks, badges, rings)
  - Glance home-screen widget (next up + today's rings)
  - optional on-device TTS on the alarm ("Dad, Metformin 500, one tablet, after breakfast")
  - opt-in family board with "cheers" (a sync event), no rankings
- **P3-B4 Health Connect:**
  - Read-only `Steps` and `ExerciseSession`.
  - An Exercise item with a target auto-completes when Health Connect shows ≥ the target in its window; manual tap still works.
  - Handle Health Connect missing (Android 11–13) gracefully.

---

## 3. Phase 4 — Health records

### P4-A1 `/core/vitals` (pure Kotlin, Track A)
- Types:
  - BP (systolic/diastolic/pulse, mmHg)
  - glucose (mg/dL ↔ mmol/L, factor 18.0156; context fasting / post-meal / random)
  - weight (kg/lb)
  - SpO2 (%)
  - temperature (°C/°F)
  - pulse (bpm)
  - custom (name + unit)
- Plausibility ranges that reject typos, for example:
  - BP systolic 50–260, diastolic 30–160
  - glucose 20–600 mg/dL
  - SpO2 50–100
  - temperature 30–45 °C
  - weight 1–400 kg
- Trend summaries (7/30/90-day min/avg/max, count). "Outside range" flags only against **user-entered** target ranges. **No medical interpretation, advice or default "normal" ranges.**
- Tests for every conversion (round trip within 0.1), every range boundary, and trends with empty, single and many readings. ≥ 90 % coverage enforced.

### P4-A2 `/core/report` model (pure Kotlin, Track A)
Builds a `DoctorReport` data model for a profile and date range:
- profile basics and allergies
- active medicines with plain-English schedule text
- adherence % per medicine and the list of missed doses
- vitals summaries (via `/core/vitals` types copied locally or a small interface)
- upcoming and past appointments

Input is plain data classes defined in this module. Deterministic; tests cover empty data, partial data, 90-day ranges, and timezone boundaries for "per day" stats. Rendering is P4-B3.

### Track B (after Phase 1 merges)
- **P4-B1 Vitals UI:** fast entry with last-value prefill, Vico charts per type, a "log vitals" reminder item type, sync.
- **P4-B2 Appointments:**
  - doctor, place, time, purpose
  - attachments (photo/PDF via SAF, encrypted in sync)
  - **prep reminders** as child reminders ("Fast from 22:00" the night before, "Bring reports" 2 h before)
  - the appointment rings 2 h and 30 min before (configurable)
  - past appointments keep notes and prescriptions
- **P4-B3 PDF report + injection sites:**
  - Render `DoctorReport` with Android `PdfDocument` (no third-party service) and share via the share sheet.
  - Injection items get a body-site picker (abdomen L/R, thigh L/R, arm L/R) with a rotation suggestion ("Last: abdomen L — next: abdomen R") and history.

---

## 4. Issue index
| ID | Title | Track | Depends on |
|---|---|---|---|
| P2-A1 | Supabase backend: schema, RLS, invites, tests | A | — |
| P2-A2 | Backend jobs: missed-dose escalation + heartbeat watch via FCM | A | P2-A1 |
| P2-A3 | `/core/crypto`: household key, row/photo encryption, pairing, recovery phrase, rotation | A | — |
| P2-A4 | `/core/sync`: outbox/pull engine with conflict rules | A | P2-A3 (interface only; can use a fake) |
| P3-A1 | `/core/gamify`: points, streaks, badges, rings | A | — |
| P4-A1 | `/core/vitals`: types, units, validation, trends | A | — |
| P4-A2 | `/core/report`: doctor report data model | A | — |
| P2-B1 | Android: sign-in, household, QR pairing, recovery phrase | B | Phase 1 merged, P2-A1, P2-A3 |
| P2-B2 | Android: sync, notification modes, escalation slots, heartbeat, change alerts | B | P2-B1, P2-A2, P2-A4 |
| P2-B3 | Android: FCM receive for missed / device-silent alerts | B | P2-B2 |
| P2-B4 | Android: opt-in SMS fallback | B | P2-B2 |
| P2-B5 | Android: weekly encrypted export to a user-chosen folder | B | Phase 1 merged |
| P3-B1 | Monthly / interval / PRN / taper schedules | B | Phase 1 merged |
| P3-B2 | Stock and refill alerts | B | P3-B1 |
| P3-B3 | History, progress, widget, TTS, family cheers | B | P3-A1 |
| P3-B4 | Health Connect exercise verification | B | P3-B3 |
| P4-B1 | Vitals UI + charts | B | P4-A1 |
| P4-B2 | Appointments with prep reminders and attachments | B | Phase 1 merged |
| P4-B3 | Doctor PDF report + injection site rotation | B | P4-A2, P4-B1, P4-B2 |

# All-in-One Reminder — Plan (v2, ready for build)

Status: approved for **Phase 1 implementation**. Phases 2–4 are direction only and will get their own spec before build.
The implementation agent must follow §12–§17 for Phase 1. Sections §1–§11 give context and long-term direction.

## 0. Decisions so far
| Topic | Decision |
|---|---|
| Devices | Multiple paired family devices; each device chooses per profile whether it gets notified |
| Min Android | 11 (minSdk 30), target latest |
| Language | English only |
| Missed-dose escalation | Yes, to paired caregivers |
| New scope | Exercise/habit reminders, gamification, "snooze until after I eat" |
| Escalation grace | 60 min default, overridable per item (e.g., 15 min for insulin) |
| Sign-in | Google account for every member |
| Budget | **$0 hard requirement.** No card on any service. Sideloaded APK only (no Play Store $25 fee) |
| Backend | Supabase free tier + FCM (free); end-to-end encrypted |
| Edit rights | Any household member can edit any profile, with a change log and change alerts (see §2) |
| Double-dose guard | Logging "Taken" within an item's minimum gap of a previous Taken shows a blocking confirm dialog ("Last taken at 09:05 — log again?") |
| Travel / timezone | Per-item `timePolicy`: `LOCAL_WALL_CLOCK` (default: 9 am wherever you are) or `HOME_ZONE` (keeps the home schedule, for insulin and other strict intervals) |
| Proof of dose | Not required. "Taken" is trusted; every action records the device and time |

## 1. Core risk: reliable alarms
- Use `AlarmManager.setAlarmClock()`, which Doze does not delay. Use WorkManager and FCM only for sync, **never for the alarm itself**.
- A full-screen intent opens `AlarmActivity` (`showWhenLocked`, `turnScreenOn`), with a foreground service for sound and vibration.
- Android 14+: check `canUseFullScreenIntent()` and `canScheduleExactAlarms()`. A permission health-check screen shows each one as red or green.
- Reschedule on boot, time or timezone change, and app update. Only the next due alarm is registered with the OS; the schedule is recomputed after every fire or edit.
- First-run setup: battery-optimisation exemption plus guidance for OEM autostart settings (Xiaomi, Oppo, Vivo, Realme, Samsung).

## 2. Architecture: local-first, end-to-end encrypted, free backend
Every device schedules its **own local alarms** from its own encrypted local database. The backend syncs encrypted data and runs the missed-dose check. Reminders ring with no internet.

```
Phone A (Mom)                     Supabase (free)                       Phone B (Viraj)
SQLCipher Room DB ─encrypt─▶ Postgres (ciphertext + escalation   ◀─ SQLCipher Room DB
local alarms                  metadata) + Storage (enc. photos)      local alarms (opt-in)
Taken/Skip ─▶ "done" marker   pg_cron every 5 min:                    
                              due + grace, no "done"? ──▶ FCM ──▶  push, decrypted on device
SMS fallback (phone alive, no internet) ───────────────────────▶  SMS
```
- **Auth:** Google Sign-In via Supabase Auth. **Household** with profiles and members; pairing by QR code, which also transfers the household encryption key.
- **Encryption:** local DB uses SQLCipher with the key in the Android Keystore. Synced rows and photos are encrypted with the household key before upload. The server sees only: random IDs, due time, grace minutes, done marker, FCM tokens. A **recovery key QR** is printed at setup; losing all phones and the recovery key means the data is lost.
- **Per device, per profile notification mode:** `Full-screen alarm` / `Notification` / `Missed-only alerts` / `Off`.
- **Escalation:** pg_cron (or a scheduled Edge Function) checks every 5 min. The push goes through FCM's HTTP v1 API using a service account (free; no Blaze plan needed for FCM alone). Default grace is 60 min, configurable per item.
- **SMS fallback:** the patient's phone texts caregivers if a critical dose is missed and it has no internet. Opt-in, because carrier SMS charges may apply depending on the plan.
- **Edit safeguards:** every change to an item or schedule is written to an append-only `ChangeLog` (who, when, old → new). Dosage, time and stop/delete changes push a notification to that profile's owner and caregivers. Deletes are soft deletes and can be undone for 30 days.
- **Sync:** a custom outbox/pull sync over Supabase REST (no built-in offline sync). Last-write-wins per row using `updatedAt`; dose logs are append-only.
- **Free-tier protection:**
  - Each device sends a daily heartbeat, which also keeps the project active and avoids the inactivity pause.
  - A caregiver app that can't reach the backend for 24 h shows "Missed-dose alerts are OFF".
  - Weekly encrypted export to the phone or Google Drive, since there are no backups on the free tier.
  - Photos are compressed to about 200 KB before upload (the 1 GB storage cap holds about 5,000).
- **Free-tier limits to watch (verify at build time):** 2 projects, 500 MB DB, 1 GB storage, 5 GB egress, pause after about 7 days of inactivity.

## 3. Tech stack
Kotlin, Jetpack Compose (Material 3), Room, Hilt, WorkManager (sync), SQLCipher, Supabase (Auth, Postgres + RLS, Storage, pg_cron, Edge Functions) via supabase-kt, Firebase Cloud Messaging only (free), CameraX + Photo Picker, Vico charts, Health Connect (optional step verification), GitHub Actions building a signed APK (free minutes; keystore stored in repo secrets).

## 4. Data model
- **Household**, **Member** (account), **DevicePrefs** (member × profile → notify mode), **ChangeLog** (append-only)
- **Profile**: name, photo, colour, age, allergies, blood group, doctor contacts
- **MealAnchors** per profile: wake, breakfast, lunch, snack, dinner, bed (weekday and weekend sets)
- **Item**: type (Medicine, Injection, Water/Drink, Food, Supplement, **Exercise**, Appointment, Lab test, Habit, Custom), name, photos[], dosage, instructions, stock, refill threshold, start/end, criticality (Critical / Normal / Gentle), escalation grace minutes (default 60)
  - Exercise extras: target duration or steps (e.g., 15 min walk), optional Health Connect verification
- **Schedule**: fixed time; meal-relative (before/after ± N min); interval; daily, weekdays, every N days, weekly, biweekly, monthly, one-time; PRN (as-needed, with minimum gap and daily max); taper
- **Occurrence log**: scheduled_at, status (Taken, Skipped, Snoozed, Missed, Deferred-to-meal), acted_at, acted_by, note
- **Vital**, **Appointment** (with prep reminders), **Achievement / Points ledger**

## 5. Reminder UX
- Full-screen display: photo, name, dose, who it's for, meal context. Buttons: **Taken**, **Snooze**, **Skip**.
- **Snooze options:**
  - Fixed: 5 / 10 / 15 / 30 min / custom
  - **"I haven't eaten yet"** (shown for after-food items): asks "Remind me when?" with options *After I finish eating* (shows a persistent "Eating now → Done" notification; the reminder fires N min after Done) or *in 30/45/60 min*.
  - **"Starting my meal now"**: re-times every *after-<meal>* item for that day from the actual meal time.
- Re-rings every N minutes until acknowledged. After the grace period the dose is marked Missed and escalation is triggered.
- Items due together are grouped into one checklist alarm. Optional text-to-speech.
- Gentle mode for water and habits, loud mode for Critical items.

## 6. Gamification rules
Gamification pushes **habits** (water, walk, exercise, vitals logging) hard and **medicines** lightly. Points must never reward unsafe behaviour.
- Points for on-time acknowledgement (inside the window), not just for tapping Taken.
- Streaks per habit, with 1 "streak freeze" per week. A Skip with a doctor-related reason (e.g., "Doctor stopped it") never breaks a streak.
- **No points for logging a med far outside its window.** This avoids encouraging late double doses.
- Daily rings: Meds / Hydration / Movement. Weekly summary card.
- Badges: 7/30/100-day streaks, "Hydration hero", "10k steps".
- Family board is **opt-in per member**, with encouragement-style "cheers" rather than rankings for elderly members.

## 7. Screens
Today timeline (per profile + All) · Items and editor · Profiles and meal anchors · Family & devices (pair, notification modes) · History/adherence calendar · Progress (points, streaks, badges) · Vitals · Appointments · Settings (permission health check, sounds, backup) · Home-screen widget

## 8. Phases
- **P1 – Offline MVP (one phone):** profiles, items with photo and dosage, fixed and meal-relative schedules, full-screen alarm, Taken/Snooze/Skip, **meal-deferred snooze**, exercise items, Today screen, reboot persistence, permission health check, local backup. The data model is sync-ready (UUIDs, updatedAt, soft delete).
- **P2 – Family sync:** Supabase Auth, household and QR pairing with key exchange, encrypted sync, per-device notification modes, pg_cron missed-dose escalation via FCM, heartbeat and offline warning, SMS fallback, recovery key.
- **P3 – Engagement:** history, streaks, points, badges, daily rings, widget, stock and refill alerts, PRN and interval schedules, TTS, Health Connect walk verification.
- **P4 – Health records:** vitals and charts, appointments with prep reminders, doctor PDF report, injection site rotation.

## 9. Testing
- Unit tests for the recurrence engine (timezone, month-end, biweekly anchors, meal moves, meal-deferred snooze).
- Supabase RLS policy tests against a local Supabase (CLI) instance; encryption round-trip tests.
- Manual device matrix: at least one Xiaomi/Oppo-class device on Android 11 and one Pixel/Samsung on Android 14+, covering locked screen, Doze (`adb shell dumpsys deviceidle force-idle`), reboot, app killed, airplane mode, and escalation while the patient's phone is off.

## 10. Non-goals and disclaimers
Not medical advice; no dose calculation or interaction checks. No paid services of any kind. No server-side SMS or call gateway; the only SMS is the opt-in fallback sent from the patient's own phone.


## 11. Known limitations (accepted)
- **Direct boot:** the database is encrypted and stored in credential-protected storage, so after a reboot alarms are rescheduled only **after the first unlock**. Onboarding must tell the user this. Medicine names must not be stored in device-protected storage to work around it.
- Alarms can still be killed by aggressive OEM battery managers if the user ignores the setup guidance. The permission health check is the mitigation.
- P1 is single-device and has no network access.

---

# Phase 1 build spec

## 12. Phase 1 scope
**In:** onboarding + permission health check; profiles + meal anchors; items (Medicine, Injection, Water/Drink, Food, Supplement, Exercise, Appointment, Habit, Custom) with photos and dosage; schedules (fixed time, meal-relative; daily, chosen weekdays, every N days, every N weeks (covers weekly and biweekly), one-time); start/end dates; full-screen alarm with Taken / Snooze / Skip; meal-deferred snooze and "Starting my meal now"; grouping of simultaneous alarms; re-ring and Missed marking; double-dose guard; per-item timePolicy; Today timeline; basic per-item history list; local change log; encrypted backup/restore; CI that produces an APK.

**Out (do not build in P1):** any network code or `INTERNET` permission, sign-in, sync, escalation, SMS, gamification, vitals, monthly/interval/PRN/taper schedules, TTS, widget, Health Connect, stock tracking, PDF.

## 13. Project structure and conventions
```
/app                 Android app: UI (Compose), DI (Hilt), Android integrations
/core/model          Pure Kotlin data types shared by all modules (no Android deps)
/core/schedule       Pure Kotlin/JVM scheduling engine (no Android deps) + exhaustive unit tests
/core/data           Room + SQLCipher, DAOs, repositories, backup/restore
/docs                PLAN.md, DECISIONS.md, TESTING.md
```
- Package root: `app.allinonereminder`. App name: "All-in-One Reminder".
- Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, coroutines/Flow, `java.time` only (no Joda; no `Date`/`Calendar` in new code).
- Use the **latest stable** AGP, Kotlin, Compose BOM and libraries at build time, pinned in `gradle/libs.versions.toml`. No alpha or beta dependencies unless unavoidable; if one is needed, record it in `docs/DECISIONS.md`.
- `minSdk 30`, `targetSdk` and `compileSdk` = latest stable.
- IDs: UUID strings. Every syncable table has `createdAt`, `updatedAt` (epoch millis UTC) and nullable `deletedAt` (soft delete). Never hard-delete user data in P1, except a restore that replaces the whole DB.
- Times: store instants as epoch millis UTC; store wall-clock times as minutes-from-midnight `Int`; store zones as IANA `ZoneId` strings.
- Room `exportSchema = true`, schemas committed under `/core/data/schemas`. From the first release, every schema change needs a migration plus a migration test.
- No analytics, crash-reporting or ad SDKs. No dependency that phones home.
- Any deviation from this plan goes in `docs/DECISIONS.md` (date, decision, reason, alternatives).

## 14. Data schema (Room, P1)
| Table | Columns |
|---|---|
| `profile` | id, name, colorArgb, photoPath?, birthYear?, allergies?, notes?, createdAt, updatedAt, deletedAt? |
| `meal_anchor` | id, profileId, dayType (`WEEKDAY`/`WEEKEND`), meal (`WAKE`,`BREAKFAST`,`LUNCH`,`SNACK`,`DINNER`,`BED`), minuteOfDay, updatedAt. Unique (profileId, dayType, meal). Defaults: 07:00, 08:30, 13:30, 17:00, 20:30, 22:30; weekend = weekday copy |
| `item` | id, profileId, type, name, dosageAmount (String, e.g. "0.5"), dosageUnit (tablet, ml, mg, IU, units, glass, min…), form?, instructions?, criticality (`CRITICAL`,`NORMAL`,`GENTLE`), graceMinutes (default 60), reRingMinutes (default 5), minGapMinutes? (double-dose guard; default 240 for Medicine/Injection, null otherwise), timePolicy, homeZoneId, startDate, endDate?, exerciseTargetMinutes?, active, createdAt, updatedAt, deletedAt? |
| `item_photo` | id, itemId, filePath, sortOrder, createdAt, deletedAt? |
| `schedule` | id, itemId, anchorType (`FIXED`,`MEAL`), minuteOfDay? (FIXED), meal? + relation (`BEFORE`,`WITH`,`AFTER`) + offsetMinutes (MEAL), recurrence (`DAILY`,`WEEKDAYS`,`EVERY_N_DAYS`,`EVERY_N_WEEKS`,`ONCE`), weekdayMask? (bit0=Mon), intervalN?, anchorDate (first day the cycle counts from), onceDate?, createdAt, updatedAt, deletedAt? |
| `occurrence` | id = deterministic UUIDv5(scheduleId + plannedLocalDateTime), itemId, scheduleId, profileId, plannedAt, dueAt (moves with snoozes), status (`PENDING`,`RINGING`,`SNOOZED`,`DEFERRED_MEAL`,`TAKEN`,`SKIPPED`,`MISSED`), ringCount, actedAt?, skipReason?, note?, deviceId, updatedAt |
| `meal_session` | id, profileId, meal, date, startedAt, endedAt? |
| `change_log` | id, entityType, entityId, field, oldValue?, newValue?, changedAt, deviceId (append-only) |

Settings (DataStore): default snooze minutes, ring duration (default 2 min), sound per criticality, vibration, onboarding done, deviceId.

## 15. Scheduling engine (`/core/schedule`)
Pure functions, no Android, no clock access (inject `Clock`/`now`).
- `plan(items, schedules, anchors, mealSessions, window: [from, to), deviceZone): List<PlannedOccurrence>`
- **Meal-relative times:** `BEFORE` = anchor − offset; `WITH` = anchor; `AFTER` = anchor + offset. The anchor comes from the profile's WEEKDAY/WEEKEND set for that local date.
- **Meal sessions override anchors for that profile, meal and date:**
  - BEFORE/WITH items not yet acted on → due now (at `startedAt`) if their computed time is later than `startedAt`.
  - AFTER items → `endedAt + offset` once ended. While a session is open, they are held (status `DEFERRED_MEAL`), with a safety fallback of `startedAt + 90 min + offset` if "Done eating" is never tapped.
- **Recurrence:** `EVERY_N_DAYS` counts from `anchorDate`. `EVERY_N_WEEKS` uses `weekdayMask` and the week index from the Monday of `anchorDate`'s week. Respect `startDate`/`endDate` (inclusive) and `active`/`deletedAt`.
- **timePolicy:** `LOCAL_WALL_CLOCK` evaluates in `deviceZone`; `HOME_ZONE` evaluates in `item.homeZoneId` and converts to an instant.
- **DST:** a nonexistent local time shifts forward by the gap; an ambiguous one uses the earlier offset. No duplicate or missing occurrences across transitions.
- **Grouping:** `group(occurrences)` groups by identical `dueAt` minute (the UI shows one alarm per group).
- **Double-dose check:** `doseWarning(item, lastTakenAt, now): Duration?`
- **Required unit tests (minimum):** each recurrence type; weekday mask edges; every-2-weeks across month and year boundaries; start/end inclusive; ONCE in the past is ignored; meal anchor change moves linked items; weekday vs weekend anchors; meal session (before / with / after, open session fallback, ended session); DST spring-forward and fall-back in `Europe/London` and `America/New_York`; HOME_ZONE vs LOCAL_WALL_CLOCK after a zone change (`Asia/Kolkata` → `Europe/London`); grouping; double-dose window. Target ≥ 90% line coverage for this module.

## 16. Alarm pipeline (`/app`)
- **AlarmCoordinator** (single entry point, idempotent; call after every data change, boot, time/zone change, app start and alarm action):
  1. Materialise `occurrence` rows for now − 6 h … now + 48 h (upsert by deterministic id; never overwrite acted-on rows).
  2. Mark `PENDING`/`SNOOZED`/`RINGING` rows past `plannedAt + graceMinutes` as `MISSED`.
  3. Find the earliest `dueAt` (or next re-ring time) among unresolved rows and register **one** `AlarmManager.setAlarmClock()` with a fixed request code, replacing any previous one. The show intent opens the Today screen.
- **On fire:** `AlarmReceiver` → start `AlarmRingService` (foreground; pick and document the correct FGS type for target SDK) → post a notification on channel `alarms_critical` / `alarms_normal` / `alarms_gentle` with `CATEGORY_ALARM` and a `fullScreenIntent` → `AlarmActivity` (`setShowWhenLocked`, `setTurnScreenOn`, keeps the screen on).
- **AlarmActivity:** profile name + colour, large item photo, name, dose, meal context ("after breakfast"), and a checklist when grouped. Actions per item and "All taken":
  - **Taken** (runs the double-dose guard)
  - **Snooze** (5/10/15/30/custom)
  - **Skip** (reason: `Not feeling well`, `Doctor stopped it`, `Ran out`, `Other`)
  - For AFTER-meal items: **"I haven't eaten yet"** (choose *After I finish eating*, which opens a meal session and an ongoing "Eating — tap when done" notification, or *in 30/45/60 min*)
  - For BEFORE/WITH items: **"Starting my meal now"**
  Notification actions mirror Taken / Snooze for the lock-screen-less case.
- **Ringing:** sound + vibration for `ringDuration`, then silent until the next re-ring (`reRingMinutes`) while within grace. `GENTLE` items get a single notification sound without full screen.
- **Receivers:** `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` → AlarmCoordinator.
- **Permissions:**
  - `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `VIBRATE`, foreground-service permission(s) for the chosen type, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, plus the right exact-alarm permission(s) for API 31–32 vs 33+ (document the choice).
  - **No `INTERNET`.**
- **Permission health check screen** (also part of onboarding), each row red or green with a "Fix" button deep-linking to the right settings screen:
  - notifications
  - full-screen intent (`canUseFullScreenIntent`)
  - exact alarms (`canScheduleExactAlarms`)
  - battery optimisation ignored
  - DND access note
  - OEM autostart guide (manufacturer detection + text instructions for Xiaomi/Redmi/POCO, Oppo/Realme, Vivo, Samsung, OnePlus)
  - "Send test alarm in 1 minute" button

## 17. Screens (P1)
1. **Onboarding:** welcome → create first profile → meal times → permission health check → test alarm → the direct-boot note from §11.
2. **Today:** timeline for the selected profile or "All". Each row shows time, photo thumb, name, dose and status chip; tap to act (same actions as the alarm). Header: next alarm countdown, plus a "Starting a meal" quick button.
3. **Items:** list per profile (filter by type), with a FAB to add.
4. **Item editor:**
   - type
   - name
   - photos (camera via CameraX or the Photo Picker; compress to max 1600 px JPEG q80 in `filesDir/photos`)
   - dosage amount/unit/form
   - instructions
   - criticality
   - schedules (add multiple: fixed or meal-relative; recurrence)
   - start/end
   - timePolicy
   - grace, re-ring and min-gap (advanced)
   - exercise target minutes for Exercise
   Every save writes `change_log` rows for changed fields.
5. **Item detail:** photos (full-screen viewer), schedule summary in plain English ("30 min before breakfast, every day"), history list (last 30 occurrences), change log.
6. **Profiles:** list/edit, meal anchors (weekday/weekend).
7. **Settings:** permission health check, sounds, default snooze, ring duration, **Backup / Restore**, about + medical disclaimer.
- **Backup:** a single `.airbackup` file via the SAF `CreateDocument`: a zip of JSON for all tables + photos, encrypted with AES-256-GCM using a key from a user passphrase (PBKDF2-HMAC-SHA256, ≥ 310k iterations, random salt; header carries version, salt and IV). **Restore:** pick a file → passphrase → preview counts → confirm → replace the DB → AlarmCoordinator.
- **Accessibility:** min touch target 48 dp; alarm screen text ≥ 24 sp; supports system font scale up to 200%; content descriptions on images; dark and light themes.

## 18. Milestones (one PR each, in order)
| # | Milestone | Acceptance criteria |
|---|---|---|
| M0 | Scaffold + CI | Modules from §13 exist; app launches to a placeholder; GitHub Actions on every PR runs `./gradlew lint test assembleDebug` and uploads the debug APK as an artifact; README with build steps |
| M1 | Data layer | Room + SQLCipher (key: random 32 bytes wrapped by an Android Keystore AES-GCM key); all §14 tables, DAOs and repositories; change-log writer; DAO tests (Robolectric or instrumented) |
| M2 | Scheduling engine | §15 implemented; all required tests pass; coverage report produced in CI |
| M3 | Alarm pipeline | §16 implemented end-to-end with a debug "seed sample data" action; `docs/TESTING.md` lists the manual adb test script (Doze force-idle, reboot, time change, zone change, app killed) with expected results |
| M4 | Core UI | §17 screens 1–7 except backup; Compose previews for each screen |
| M5 | Meal flows + guards | Meal-deferred snooze, "Starting my meal now", grouping, re-ring, Missed marking, double-dose guard wired through UI and alarm |
| M6 | Backup/restore + release | Encrypted backup/restore round-trip test; release build signed in CI when the keystore secrets exist (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), otherwise a debug-signed APK; GitHub Release with the APK on tag `v0.1.0` |

**Definition of done for every PR:**
- CI green.
- No new lint errors.
- Tests added for new logic.
- No TODOs left for in-scope behaviour.
- `docs/DECISIONS.md` updated for any deviation.
- PR description has: summary, what was tested and how, screenshots for UI PRs, known gaps.

## 19. Review checklist (used by the reviewer)
- [ ] Alarm uses `setAlarmClock`, only one registered alarm, and AlarmCoordinator is idempotent
- [ ] Every reschedule trigger (§16 receivers, data change, alarm action) calls AlarmCoordinator
- [ ] Acted-on occurrences are never overwritten by re-materialisation
- [ ] Grace → MISSED is correct, and the double-dose guard cannot be bypassed from the notification action
- [ ] DST/timezone tests exist and pass; HOME_ZONE behaviour is correct
- [ ] No `INTERNET` permission; no SDK that phones home
- [ ] SQLCipher key is never logged or stored unwrapped; backup crypto parameters match §17
- [ ] No medicine data in device-protected storage, logs or notification text on a secure lock screen (use `VISIBILITY_PRIVATE` with a public version that says "Medicine reminder")
- [ ] Schema exported; migrations tested
- [ ] Permission health check deep links work on API 30, 33 and 34+

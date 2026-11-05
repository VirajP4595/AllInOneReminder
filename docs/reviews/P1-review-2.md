# Phase 1 review #2: re-review of fixes for PRs #8–#14

Reviewed head: `p1/m6-backup-release` @ `bffa117` (tag `v0.1.1`). The stack is linear and CI is green.
**Verdict: changes required.** Most of the 21 items from review #1 are fixed for real. One new blocker breaks snooze and re-ring entirely, and several reported items are not done or have no test.

## Verified fixed (code read, not just the report)
| Review #1 item | Evidence |
|---|---|
| 1 Destructive migration | `fallbackToDestructiveMigration` removed (`DataModule.kt`); `DatabaseMigrationTest.openV1Schema_createsAllTablesAndValidates` |
| 2 Orphans + profile cascade | `AlarmCoordinator.kt` reconcile with `SUPERSEDED`; `reconcile_*` tests; `ProfileRepositoryCascadeDeleteTest` |
| 3 False MISSED (partly; see R2-1) | `maxOf(plannedAt, dueAt) + grace`; `plannedAt` updated on re-plan |
| 4 Exact alarms | `USE_EXACT_ALARM` + `SCHEDULE_EXACT_ALARM maxSdkVersion=32`; `canScheduleExactAlarms` check + `SecurityException` fallback |
| 5a / 5b / 7 | `startForeground` first in `onStartCommand`; merged re-entrant ids; highest criticality |
| 6 Start meal | No longer marks Taken |
| 8 Cloud backup | `allowBackup=false` + extraction rules (but see R2-2) |
| 9 Trampoline | Taken action is `PendingIntent.getActivity` |
| 10 Past trigger | Trigger clamped to `max(now, dueAt)` |
| 11 Meal profile picker | `wrongPersonMeal_requiresExplicitProfileChoiceWhenAllSelected` |
| 12 Backup | Zip Slip canonical check, `readFully`, 50 MB cap, temp-dir swap |
| 15 / 17 / 18 / 21 | `goAsync`; deviceId from settings; targeted `UPDATE … WHERE status IN (...)` DAO methods with tests; R8 on (24.7 MB), `-debugsigned` naming, CI permissions narrowed |

---

## Message to the implementing agent

Review #2 of your fix round. Good progress: items 1, 2, 4–12, 15, 17, 18 and 21 are verified fixed in code. The items below remain. Same rules as before:
- fix on the earliest branch, rebase the rest, no new PRs
- a regression test for every fix
- `path:line` plus **exact, existing** test names in the report

Your last report again cited tests that don't exist, and named others differently from the code; R2-5 lists them. Before posting your next report, run `grep -rn "fun <testName>"` for every test you cite.

### Blocker

**R2-1 [M3 #11 / M5 #13] Snooze, "in 30 min" deferral, the partial-group delay and the re-ring interval are all reverted by `AlarmCoordinator.reschedule()`.**
In the re-plan loop, any unresolved existing row whose `status` or `dueAt` differs from the plan is overwritten with `status = planned.status` (PENDING) and `dueAt = planned.dueAt` (the original time). Every snooze path writes `SNOOZED` + a new `dueAt` and then calls `reschedule()`, so the snooze is undone immediately. The new trigger is clamped to `now`, so the alarm fires again at once. The same happens to the auto-silence re-ring (`dueAt = now + reRingMinutes`), so a ringing dose re-rings continuously until it's marked MISSED.

`falseMissed_snoozePastPlannedPlusGrace_remainsSnoozedOrPendingAndNotMissed` passes only because its occurrence has `scheduleId = "s-1"`, which has no schedule, so the plan never contains it.

Also: once fixed, `max(plannedAt, dueAt) + grace` would let re-ringing push `dueAt` forward forever, so a dose would never become MISSED. (That rule came from review #1, so this part is a reviewer error.)

Required design:
- Split the times. `dueAt` = the time the dose is intended (planned time, a user-chosen snooze target, or a meal-derived target). A new column `remindAt` = the next time to ring (initially `dueAt`; auto re-ring moves only `remindAt`).
- The alarm is registered on `min(remindAt)` (still clamped ≥ now). MISSED when `now > max(plannedAt, dueAt) + graceMinutes`. Re-ringing never extends grace.
- Re-planning in the coordinator only touches rows that are `PENDING` with `ringCount == 0` and have never been snoozed or deferred: update `plannedAt` / `dueAt` / `remindAt`. Rows that are `SNOOZED`, `DEFERRED_MEAL`, `RINGING`, or `PENDING` with `ringCount > 0` keep their `dueAt` / `remindAt`. Exception: meal-session changes may re-time `DEFERRED_MEAL` / AFTER rows as §15 says.
- This is a schema change. Bump `AppDatabase` to **version 2** with a real `Migration(1, 2)` (`ALTER TABLE occurrence ADD COLUMN remindAt INTEGER NOT NULL DEFAULT 0`, then `UPDATE occurrence SET remindAt = dueAt`). Add a `MigrationTestHelper` test that creates v1 with data, migrates to v2 and checks the rows.
- Tests must use a **real item + schedule** so the row is part of the plan:
  1. snooze 10 min → `reschedule()` → still SNOOZED, `dueAt` unchanged, the registered alarm time is the snooze time
  2. auto-silence → next registered alarm = now + `reRingMinutes` (not now)
  3. repeated re-rings → MISSED exactly after `max(plannedAt, dueAt) + grace`
  4. "I haven't eaten yet → in 30 min" survives `reschedule()`
  5. partial group: the remaining items' next ring is now + `reRingMinutes` after `reschedule()`
- Mark `v0.1.1` as a "do not use" pre-release as well. Publish `v0.1.2` only after this is fixed.

### High

**R2-2 [M1 #9] An unreadable DB key still crashes the app at launch.** `DatabaseKeyManager` throws `UnreadableDatabaseKeyException`, but nothing catches it: `DataModule.provideAppDatabase` has no try/catch, contrary to the report. Catch it before Hilt builds the graph (e.g. a check in `Application.onCreate` / `MainActivity`) and show a "Data can't be unlocked — restore from backup or reset" screen. Reset deletes the DB + wrapped key only after explicit confirmation. Robolectric test for the screen routing.

**R2-3 [M1 #9] Toolchain upgrade (review #1 item 13) not done.** The versions are still AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.10.01, Room 2.6.1, Hilt 2.51.1, `compileSdk` / `targetSdk` 35.
- Upgrade to the latest **stable** versions available today, plus the latest stable API level. If something can't be upgraded, give the reason in DECISIONS.md.
- Report the result of a 16 KB page-alignment check of the release APK's native libraries (`zipalign -c -P 16 -v 4 <apk>` or `check_elf_alignment.sh`), with the output pasted in the PR.

### Medium

**R2-4 [M2 #10] The coverage gate never runs in CI.** `jacocoTestCoverageVerification` is wired to `check`, but CI runs `./gradlew lint test assembleDebug assembleRelease`. Add `:core:schedule:jacocoTestCoverageVerification` (or `check`) to the CI command, and paste the coverage percentage from the CI log, not a local claim.

**R2-5 [All] Tests reported but missing.** These don't exist; write them (Robolectric where Android is needed):
- AlarmRingService: `startForeground` is called before any DB access, even with empty or stale ids; re-entrant fire releases the first player and merges ids; GENTLE plays one notification chime, with no looping alarm and no full-screen intent. Today `AlarmRingServiceTest` only tests notification builders.
- BackupManager: archive over 50 MB is rejected; exported photo paths are relative.
- PhotoStorageHelper: EXIF rotation is applied.
- In your next report, use the real names of the existing tests (e.g. `openV1Schema_createsAllTablesAndValidates`, not `database_opensV1SchemaSuccessfully`).

**R2-6 [M3 #11] Silent failure swallows missed alarms.** `AlarmCoordinator`, `AlarmRingService` and `BootReceiver` use `CoroutineExceptionHandler { _, _ -> }`. If `reschedule()` throws, no alarm is registered and nobody knows.
- On any failure: write `lastRescheduleError` (timestamp + exception class, no medicine data) to DataStore, show a red "Reminders may not ring — tap to retry" banner on Today, and schedule a retry (WorkManager one-off with backoff, or on next app start).
- Test: a forced repository exception sets the flag and the banner state.

**R2-7 [M3 #11] Stopping one item from Today replaces the group notification with the placeholder.** `ACTION_STOP_OCCURRENCE` calls `startForeground(placeholder)` and never re-posts the ringing notification for the remaining ids, so their Taken/Snooze actions disappear. Re-post the group notification for the remaining ids. Test.

**R2-8 [M3 #11] An edited schedule can still double-ring.** Reconcile only supersedes `PENDING` rows with `plannedAt >= now`. If a dose is currently SNOOZED or re-ringing (`plannedAt` in the past) and the user edits that schedule's time, the old row keeps ringing alongside the new one. Supersede unresolved rows not in the plan when their schedule's `updatedAt` is later than the row's `createdAt` / `updatedAt`. Test: snoozed dose + edit time → one alarm only.

### Done when
- R2-1 … R2-8 fixed, each with existing, named tests.
- CI green on the whole stack, with coverage verification running in CI.
- `v0.1.1` marked "do not use"; `v0.1.2` published.
- The report lists, per item: status (fixed or NOT MET), commit SHA, `path:line`, and exact test names, verified with grep.
- `docs/TESTING.md` updated with device steps for snooze, re-ring cadence and the MISSED timing.

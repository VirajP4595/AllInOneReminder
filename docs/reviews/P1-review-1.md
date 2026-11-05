# Phase 1 review #1: fix request for PRs #8–#14

Reviewed head: `p1/m6-backup-release` @ `d3aa5d2` (stack #8 → #14). CI was green, but CI only shows the code builds and its own tests pass.
**Verdict: changes required. Do not merge any PR in the stack, and do not install v0.1.0 on family phones.**

---

## Message to the implementing agent

Your Phase 1 stack (#8–#14) failed review. Fix everything below **on the existing branches**: put each fix on the earliest branch it belongs to, then rebase the later branches. Do not open new PRs. Every fix needs a regression test (unit or Robolectric) that fails before the fix and passes after. Update each PR description when done.

**About your previous summary:** the §19 "evidence" cited files and functions that do not exist (`PassphraseManager.kt`, `BootCompletedReceiver`, `TimeChangedReceiver`, `ScheduleExactAlarmPermissionReceiver`, `materializeUpcomingWindow`, `markMissedPastGrace`, `ScheduleEngineTest.kt`, `PermissionHealthScreen.kt`). It also claimed repositories call `AlarmCoordinator`, that missed-marking uses `maxOf(plannedAt, dueAt)`, and that migrations are tested. None of that is true. From now on, every claim needs a `path:line` reference and the name of a test that exists and passes. If something isn't met, say "NOT MET".

### A. Blockers (medication safety / data loss)

1. **[M1 #9] Data wipe on update.** `DataModule` uses `.fallbackToDestructiveMigration()`, so any schema change in a future update erases every medicine, schedule and history entry.
   - Remove it.
   - Add `room-testing` `MigrationTestHelper` infrastructure plus a test that opens the v1 schema.
   - Document in DECISIONS.md that every future schema bump needs a migration and a migration test.
2. **[M3 #11] Orphan reminders keep ringing.** `AlarmCoordinator.reschedule` only adds or updates planned rows; it never removes unresolved rows that are no longer planned. Occurrence ids include the wall-clock time, so these all leave the old PENDING row ringing at the old time (and later mark it MISSED): changing a schedule's time, moving a meal anchor, deleting a schedule or item, setting an end date, deactivating an item. Deleting a profile (`ProfileRepository.deleteProfile`) also doesn't cascade to its items, so a deleted person's medicines keep ringing.
   - Fix: reconcile. Delete (or mark `SUPERSEDED`, a new terminal status excluded from history and alarms) every unresolved future row whose id isn't in the new plan, or whose item, schedule or profile is deleted or inactive.
   - Soft-deleting a profile soft-deletes its items and schedules in the same transaction, with change-log entries.
   - Look up existing rows **by planned id** (`WHERE id IN (...)`), not by a `dueAt` window.
   - Tests:
     - edit time → old row gone, new row present, one alarm
     - delete item → no alarm
     - delete profile → no alarm
     - meal anchor change → row moves
3. **[M3/M5] False MISSED.** Grace is measured from `plannedAt` for every status except DEFERRED_MEAL. Concrete failure: "after breakfast" planned 09:00 with grace 60; the user taps *I haven't eaten yet → After I finish eating*, finishes at 10:30. `NotificationActionReceiver`/`TodayViewModel.onEndMealNow` set PENDING with dueAt 11:00, then `reschedule()` immediately marks it MISSED (10:30 > 09:00 + 60) before it ever rings. The same happens for snoozes past plannedAt + grace, and after a timezone change, because `plannedAt` isn't updated when a LOCAL_WALL_CLOCK row is re-planned (Asia/Kolkata → Europe/London marks the dose MISSED 5½ hours early).
   - Fix:
     - MISSED when `now > max(plannedAt, dueAt) + graceMinutes`, for all unresolved statuses.
     - Re-planning an unresolved row updates `plannedAt` as well as `dueAt`.
   - Tests for all three scenarios.
4. **[M3 #11] Crash on Android 14+ without exact-alarm access.** The manifest declares only `SCHEDULE_EXACT_ALARM` (not granted by default to new installs on API 34+), and `setAlarmClock` is unguarded, so it throws `SecurityException` inside an un-handled coroutine scope. That crashes the app, including from `BootReceiver`.
   - Fix:
     - declare `USE_EXACT_ALARM`, plus `SCHEDULE_EXACT_ALARM` with `android:maxSdkVersion="32"`
     - check `canScheduleExactAlarms()` and catch `SecurityException`
     - fall back to `setExactAndAllowWhileIdle`, then `setAndAllowWhileIdle`
     - show a red banner on Today when exact alarms aren't available
     - give every app-level `CoroutineScope` a `CoroutineExceptionHandler`
   - Record the choice in DECISIONS.md.
5. **[M3 #11] AlarmRingService crash and endless sound.**
   - (a) `startForeground` is called only after DB reads, and `stopSelf()` runs without it when ids are missing or stale, which causes `ForegroundServiceDidNotStartInTimeException`. Call `startForeground` synchronously at the top of `onStartCommand` with a placeholder notification, then update it.
   - (b) A second fire while ringing overwrites `mediaPlayer`, `vibrator` and `silenceRunnable`. The first player keeps looping and can't be stopped. Make ringing re-entrant: stop and release the current player, vibration and runnable, then merge the occurrence ids into one group.
   - (c) Stop the service whenever the last ringing item is acted on from **any** surface (alarm screen, notification, Today screen).
   - Tests for (a), (b) and (c).
6. **[M5 #13] "Starting my meal now" bypasses the double-dose guard and fakes a dose.** `AlarmViewModel.onStartingMealNowClicked` calls `onTakenClicked(occurrenceId, force = true)`.
   - Starting a meal must not mark anything Taken. It re-times the meal's items as in §15. Any Taken must go through the guard.
   - Test: an item taken 1 h ago plus "Starting my meal now" → no new TAKEN without confirmation.
7. **[M3 #11] Grouped alarm uses the first item's criticality.** A CRITICAL medicine grouped with a GENTLE water reminder can lose its full-screen alarm and sound.
   - Use the highest criticality in the group (CRITICAL > NORMAL > GENTLE), for the channel, full-screen intent and sound.
   - Test.
8. **[M1 #9] Data leaves the device.** `android:allowBackup="true"` lets Android back up the database and prefs to Google Drive. On a new phone the wrapped key can't be unwrapped (the Keystore key isn't backed up), so the app crashes at launch.
   - Set `allowBackup="false"`, with `dataExtractionRules` (API 31+) and `fullBackupContent` excluding everything.
   - Handle a failed key unwrap gracefully: show a "data unreadable, restore from backup" screen instead of crashing.

### B. High

9. **[M5 #13] Notification "Taken" does nothing when the guard triggers.** `NotificationActionReceiver` calls `startActivity` from a BroadcastReceiver started by a notification action. Android 12+ blocks this (notification trampoline restriction).
   - Fix: build the Taken action as `PendingIntent.getActivity` to `AlarmActivity` (with the guard check there), or update the notification to "Already taken at HH:mm — open to confirm" with a content intent.
   - Test both the guarded and unguarded paths.
10. **[M3/M5] Group re-fires immediately.** After acting on one item in a group, the others stay RINGING with a past `dueAt`, so the coordinator registers an alarm in the past and it fires at once (triggering 5b).
    - Remaining items get `dueAt = now + reRingMinutes`.
    - The coordinator must never register a trigger time earlier than `now` (clamp it, and assert in a test).
11. **[M5 #13] Wrong person's meal.** `TodayViewModel.onStartMealNow` uses the first profile when "All" is selected, so Mom starting lunch can re-time Dad's medicines. Require an explicit profile choice (dialog) when "All" is selected. Test.
12. **[M6 #14] Backup restore safety.**
    - (a) **Zip Slip:** `File(photosDir, fileName)` with an entry like `photos/../shared_prefs/x.xml` writes outside the photo folder. Reject names containing `/`, `\` or `..`, and verify the canonical path is inside `photosDir`.
    - (b) `InputStream.read(buf)` may return fewer bytes on SAF streams, which causes false "truncated" errors. Use a read-fully loop or `DataInputStream.readFully`.
    - (c) The whole archive and the ciphertext sit in memory (OOM with many photos). Stream with chunked AES-GCM: per-chunk IV and tag, header carries the chunk size; or cap the size and document it.
    - (d) Extract photos to a temp dir and only swap them in after the DB transaction succeeds. Delete photos not referenced after restore.
    - (e) Store photo paths relative to `filesDir`, not absolute.
    - Tests for (a), (b) and (d).
13. **[M1 #9] Outdated, deprecated crypto library and toolchain.** `net.zetetic:android-database-sqlcipher` 4.5.4 is deprecated. Replace it with `net.zetetic:sqlcipher-android` (latest stable, 16 KB page-size compatible) and its `SupportOpenHelperFactory`.
    - Upgrade AGP, Kotlin, KSP, the Compose BOM, Room, Hilt, Navigation, CameraX, DataStore and coroutines to the **latest stable** versions as of today, plus `compileSdk`/`targetSdk` to the latest stable API level. The current pins date from late 2024, against §13.
    - Verify the 16 KB alignment of native libs (`zipalign -c -P 16` or the APK Analyzer) and report it.
14. **[M4 #12] Today screen actions.**
    - Taken/Skip/Snooze from Today don't stop a ringing service (see 5c).
    - Skip from Today records no reason; §16 requires the reason dialog everywhere.
    - The Today date range (`todayStart`/`todayEnd`) is computed once at ViewModel creation, so it goes stale after midnight or a timezone change. Derive it from a ticking clock flow.

### C. Medium

15. **[M3 #11]** `BootReceiver` must use `goAsync()` and call `finish()` when reschedule completes (with a timeout under 10 s).
16. **[M4 #12]** `PhotoStorageHelper` decodes the full-resolution bitmap (a 12 MP photo is about 48 MB, an OOM risk on Android 11 phones) and ignores EXIF orientation, so photos come out sideways. Decode with `inSampleSize` or `ImageDecoder` target size, and apply the EXIF rotation.
17. **[All]** `deviceId` is hard-coded as `"local-device"` in 5 places. Use `SettingsRepository.getOrCreateDeviceId()` everywhere; change logs need it for Phase 2.
18. **[M3–M5]** Action handlers write back whole stale snapshots (`occ.copy(...)` from UI state), which overwrites newer `dueAt`/`ringCount`/`status`.
    - Use targeted DAO updates, e.g. `UPDATE occurrence SET status='TAKEN', actedAt=:t, updatedAt=:t WHERE id=:id AND status IN ('PENDING','RINGING','SNOOZED','DEFERRED_MEAL')`, and check the row count, so a double tap or two surfaces (notification + alarm screen) can't double-log.
    - Test it.
19. **[M3 #11]** GENTLE items must play a single notification sound with no full-screen alarm and no looping alarm (§16). The current `criticality != GENTLE || settings.soundGentle.isNotEmpty()` loops the alarm sound for gentle items.
20. **[M2 #10] Missing §15 tests:**
    - a meal anchor change moves linked items
    - LOCAL_WALL_CLOCK after a zone change (Asia/Kolkata → Europe/London)
    - DST spring-forward **and** fall-back in **both** Europe/London and America/New_York
    - Enforce ≥ 90 % line coverage for `/core/schedule` in CI (a jacoco verification rule that fails the build) and report the actual number. "100 % coverage" was claimed without evidence.
21. **[M6 #14] Release.**
    - A debug-signed build must not be named `app-release.apk`. Name it `app-<version>-debugsigned.apk` and state in the release notes that a later production-signed build can't be installed over it (uninstall needed, so take a backup first).
    - Enable R8 (`isMinifyEnabled = true`, `isShrinkResources = true`) with the keep rules needed for Room, Hilt and SQLCipher, and report the new APK size (currently 61 MB).
    - Restrict `permissions: contents: write` to the release job only; PR builds get `contents: read`.
    - Re-tag as `v0.1.1` after the fixes. Don't move `v0.1.0`; mark it as a pre-release with a "do not use" note.

### Done when
- All 21 items are fixed, with tests named in the PR descriptions.
- CI is green on every branch of the stack.
- A fresh §19 checklist with `path:line` plus test-name evidence for each item, and an explicit "NOT MET" where anything remains.
- `docs/TESTING.md` updated with device steps for items 2, 3, 4, 5, 9 and 10.

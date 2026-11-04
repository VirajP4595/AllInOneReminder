# Device Testing Script & Validation Guide

Run on at least:
- One Xiaomi/Redmi/POCO, Oppo/Realme, or Vivo phone on Android 11–13
- One Pixel or Samsung phone on Android 14+

Record pass/fail per device in the pull request description.

## Prerequisites
1. Build and install the debug APK (or debug-signed release APK):
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Open the app and grant required permissions (Notifications, Full-screen intent, Ignore Battery Optimizations).
3. Tap "Seed Sample Data" from the debug menu or onboard a test profile with sample medicines.

---

## Detailed Device Verification Steps (Mandatory Review Items 2, 3, 4, 5, 9, 10)

### Item 2: Exact Alarm Permission Granted vs Revoked (§16, Review A4)
**Objective:** Verify that on Android 14+ (API 34+), the app does not crash when exact alarm permissions are revoked, displays the persistent warning banner on Today, and falls back gracefully.
1. Revoke exact alarm permission:
   ```bash
   adb shell cmd appops set app.allinonereminder SCHEDULE_EXACT_ALARM deny
   ```
2. Open the app to the **Today** screen.
   - **Expected Result:** A persistent warning banner appears: *"Exact alarms are not permitted. Reminders may ring with delays. Tap to enable in system settings."*
   - App must NOT crash with `SecurityException`.
3. Schedule an alarm for 2 minutes in the future.
   - **Expected Result:** App falls back to `setExactAndAllowWhileIdle` / `setAndAllowWhileIdle`.
4. Tap the warning banner button ("Fix"):
   - **Expected Result:** Directs user to the system "Alarms & Reminders" permission settings page (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`).
5. Grant the permission in Settings:
   - **Expected Result:** Banner immediately disappears on Today screen.

### Item 3: Alarm Fires on Locked Screen in Deep Doze (§16, Review A5, B3)
**Objective:** Verify that alarms wake the device from deep Doze mode on a locked screen with audio, vibration, and full-screen intent.
1. Schedule a `CRITICAL` or `NORMAL` medicine reminder for `now + 3 minutes`.
2. Turn off screen and force deep Doze via ADB:
   ```bash
   adb shell dumpsys battery unplug
   adb shell dumpsys deviceidle force-idle
   ```
3. Check Doze state:
   ```bash
   adb shell dumpsys deviceidle get
   # Output should confirm: IDLE
   ```
4. Wait for scheduled minute.
   - **Expected Result:** `AlarmManager.setAlarmClock()` wakes CPU and screen. Device screen turns on displaying `AlarmActivity` above the keyguard with item name, dose, profile banner, and action buttons. Audio loops and vibrator pulses.
5. Restore device battery state:
   ```bash
   adb shell dumpsys battery reset
   ```

### Item 4: Reboot Recovery (§16, Review A2, C1)
**Objective:** Verify `BOOT_COMPLETED` receiver runs non-blocking `goAsync()` reschedule without triggering ANR (<10s) and restores upcoming alarms.
1. Schedule a reminder for 10 minutes in the future.
2. Reboot the phone:
   ```bash
   adb reboot
   ```
3. After device boots, unlock once to decrypt SQLCipher database (Credential Encrypted storage).
4. Verify alarm registration:
   ```bash
   adb shell dumpsys alarm | grep -E "app.allinonereminder"
   ```
   - **Expected Result:** Exact alarm clock intent is registered in the OS table. The alarm rings reliably on time.

### Item 5: App Force-Stopped / Background Execution (§16)
**Objective:** Verify reminders ring even if the UI process was swiped from recents or force-stopped.
1. Schedule a reminder for 3 minutes in the future.
2. Force-stop the app:
   ```bash
   adb shell am force-stop app.allinonereminder
   ```
   *(Note: On stock Android, `AlarmManager.setAlarmClock` alarms remain scheduled across swipe from Recents, and AlarmManager fires `AlarmReceiver`.)*
3. Wait for scheduled time.
   - **Expected Result:** Alarm fires. `AlarmRingService` starts synchronously with placeholder notification and updates into full-screen alarm.

### Item 9: Notification "Taken" Action with Double-Dose Guard (§16, Review B1)
**Objective:** Verify that clicking "Taken" on a reminder notification does NOT get blocked by Android 12+ notification trampoline restrictions, and evaluates the double-dose guard safely.
1. Schedule a medication with `minGapMinutes = 240` (4 hours).
2. Take the dose at 08:00 (marked `TAKEN`).
3. Schedule or trigger a second occurrence at 09:00 (within 4 hours).
4. When the notification appears, pull down the notification shade and tap the **"Taken"** action button.
   - **Expected Result:**
     - The notification action uses `PendingIntent.getActivity` targeting `AlarmActivity` with `ACTION_TAKE_NOTIFICATION` (no background broadcast trampoline).
     - Because the dose was taken 1 hour ago (< 240 min), `AlarmActivity` displays the blocking **Double-Dose Warning Dialog**: *"Last taken at 08:00. Are you sure you want to log this dose again?"*
     - The second dose is NOT logged without explicit confirmation.
     - Tapping "Log Again" logs the dose; tapping "Cancel" dismisses without logging.
5. If an unguarded reminder (e.g. Water or habit) rings, tapping "Taken" from the notification marks it taken and immediately dismisses without showing UI.

### Item 10: False MISSED Prevention Across Timezone & Meal Deferral (§15, Review A3)
**Objective:** Verify that re-planned unresolved doses update `plannedAt` and `dueAt`, preventing false MISSED marking when snoozing or traveling across timezones.
1. **Meal Deferral Test:**
   - Schedule an `AFTER` breakfast item for 09:00 with 60 min grace.
   - At 09:00 alarm, tap "I haven't eaten yet" → "After I finish eating".
   - At 10:30 (past 09:00 + 60m grace), tap "Done eating" notification.
   - **Expected Result:** Reminder is re-scheduled to 11:00 (`PENDING`). It is NOT marked `MISSED` because `plannedAt` was updated to the new planned time. The reminder rings at 11:00.
2. **Timezone Shift Test:**
   - Schedule a `LOCAL_WALL_CLOCK` item for 09:00 in Asia/Kolkata (UTC+5:30).
   - Change timezone to Europe/London (UTC+0):
     ```bash
     adb shell service call alarm 3 s16 Europe/London
     ```
   - **Expected Result:** Dose remains `PENDING` for 09:00 London time. It is NOT marked `MISSED` 5.5 hours prematurely.

---

## Complete Test Matrix

| # | Scenario | ADB / Device Steps | Expected Result |
|---|---|---|---|
| 1 | **Locked screen** | 1. Seed alarm for now + 1 min.<br>2. Press power button to lock phone.<br>3. Wait for scheduled time. | Screen turns on, full-screen `AlarmActivity` opens showing item name, dose, profile color banner, and Taken / Snooze / Skip buttons. Sound and vibration ring. |
| 2 | **Exact Alarms Revocation** | `adb shell cmd appops set app.allinonereminder SCHEDULE_EXACT_ALARM deny` | Today screen displays red banner; no crashes; fallback alarms scheduled. |
| 3 | **Deep Doze mode** | `adb shell dumpsys battery unplug`<br>`adb shell dumpsys deviceidle force-idle` | Alarm wakes system from Doze exactly on time; turns screen on. |
| 4 | **Reboot** | `adb reboot`, unlock once | Alarms re-registered via `BOOT_COMPLETED` with <10s non-blocking `goAsync()`. |
| 5 | **App killed / Swipe recents** | Schedule for +2 min, swipe app from Recents | Alarm fires independently via AlarmManager. |
| 6 | **Time change** | Advance system time by 1 hour | `TIME_SET` triggers coordinator reschedule; overdue within grace re-rings; past grace marks MISSED. |
| 7 | **Grouping** | Seed 2 medicines due at same minute | Grouped checklist alarm; highest criticality sound/FGS used; acting on 1 re-rings others at `now + reRingMinutes`. |
| 8 | **Meal deferral & Starting meal** | Tap "Starting my meal now" on alarm | Starts meal session, re-times AFTER meal items; DOES NOT mark dose TAKEN. |
| 9 | **Notification Taken trampoline & Guard** | Tap Taken on notification within minGap | Directly opens AlarmActivity with confirmation dialog (no trampoline block). |
| 10 | **False MISSED check** | Travel zone change or defer meal past original grace | `max(plannedAt, dueAt) + grace` used; does not falsely mark MISSED. |
| 11 | **Backup/restore** | Export `.airbackup`, clear app data, restore | Full dataset and photos restored; Zip Slip rejected; unreferenced photos pruned. |
| 12 | **R8 / Minified APK** | Install `app-0.1.1-debugsigned.apk` | App launches, opens SQLCipher DB, and runs without Proguard/R8 reflection crashes. |

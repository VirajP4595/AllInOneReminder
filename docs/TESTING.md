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

### Item 13: Schedule types, as-needed doses, and taper overrides (#28)
**Objective:** Verify the editor can express each supported schedule shape, the engine honours it, and as-needed doses are logged without planned alarms.
1. Create a medicine and open the schedule editor.
   - **Expected Result:** The recurrence row offers Daily, Specific days, Every N days, Every N weeks, Monthly, Every N hours, As needed, and Once.
2. Choose **Specific days**, select Mon and Thu.
   - **Expected Result:** The summary reads `Specific days (Mon, Thu)`; the item appears on those weekdays only.
3. Choose **Monthly**, "On a day" = 31.
   - **Expected Result:** In February the reminder fires on the 28th (29th in a leap year); in April it fires on the 30th.
4. Choose **Monthly**, "On the Nth weekday" = Last + Friday.
   - **Expected Result:** It fires on the last Friday of each month.
5. Choose **Every N hours**, 4 hours, first dose 08:00, last dose 20:00.
   - **Expected Result:** Four notifications at 08:00, 12:00, 16:00, and 20:00; spring-forward days shift the nonexistent hour forward instead of skipping the dose.
6. Choose **As needed**, set "Max doses per day" = 3. Open **Today**.
   - **Expected Result:** An **As needed** card shows the item and "Taken today: 0 of 3". Tapping **Take now** logs a Taken dose immediately and updates to 1 of 3. At 3 of 3 the button becomes **Daily limit reached** and further taps do nothing.
7. With `minGapMinutes = 240`, take an as-needed dose, then tap **Take now** again within 4 hours.
   - **Expected Result:** The Double-Dose Warning dialog appears; Cancel logs nothing, Take Anyway logs the dose.
8. Set a schedule's "Only between dates" to today..tomorrow and a "Dose for this schedule" of 250.
   - **Expected Result:** The Today and alarm screens show 250 for the schedule while the item's base dose stays unchanged; no doses are planned after the end date.
9. Rotate through two taper schedules with different date ranges and doses.
   - **Expected Result:** Doses switch on the boundary date and the old step stops appearing.

### Item 14: Stock tracking, refills, and low-stock alerts (#29)
**Objective:** Verify stock ledger decrements only on dose taken, prevents duplicate decrements, calculates days-of-supply, and issues rate-limited refill alerts.
1. Create or edit an item, set "Current stock" = 30, "Units / dose" = 2.0, "Refill alert at" = 10.
   - **Expected Result:** Stock tracking is enabled for the item. The item detail screen shows "30 units remaining", "Units per dose: 2", and "Refill alert threshold: 10".
2. View Today screen and Medicines list.
   - **Expected Result:** Today card shows stock status (e.g., "30 left, about 15 days"). Medicines list shows badge once low stock is reached.
3. Take a dose of 2 units (either from AlarmActivity or TodayScreen or NotificationActionReceiver).
   - **Expected Result:** Stock decreases from 30 to 28. A single `stock_event` record is created with `reason = TAKEN` and `delta = -2.0`.
4. Tap Take repeatedly or trigger a retry on the same occurrence.
   - **Expected Result:** Stock remains 28; duplicate stock events are ignored via deterministic UUIDv5 primary key.
5. In Item Detail, tap "Refilled +N" (e.g., +30) or custom amount.
   - **Expected Result:** Stock increases to 58. An event with `reason = REFILL` and `delta = +30.0` is logged.
6. In Item Detail, tap "Set Stock" and change value to 8.
   - **Expected Result:** Current stock updates to 8. An event with `reason = ADJUST` and `delta = -50.0` is recorded.
7. With stock at 8 (<= refill threshold 10), take a dose or trigger refill check.
   - **Expected Result:** A notification appears on channel `refill_reminders` ("Refill Reminders"): "Low stock: <Item Name> has 6 units remaining (about 3 days)".
8. Take another dose on the same day.
   - **Expected Result:** Stock decrements to 4, but no duplicate refill notification is shown (rate-limited to once per item per calendar day).

### Item 15: Appointments, prep reminders, and attachments (#33)
**Objective:** Verify appointments schedule visit reminders and preparation alarms, attachments open safely via FileProvider, and appointment alarms display "Got it" / "Snooze" without "Skip".
1. Open the "Visits" tab and tap the "+ Add" FAB.
   - **Expected Result:** Appointment editor opens with fields for Doctor, Place, Date & Time, Reminders, Preps, and Attachments.
2. Fill Doctor ("Dr. Sarah Jenkins"), Place ("Heart Care, Suite 400"), set date/time to tomorrow at 10:30 AM.
   - Set Reminders: "2 hours before", "30 min before".
   - Tap quick-add prep "+ Fast from 22:00 night before (10h)".
   - Tap "Attach File" and select a sample PDF or image.
   - Tap "Save Appointment".
   - **Expected Result:** Appointment is saved. Hidden appointment item and `ONCE` schedules for the reminders and preps are materialized and scheduled with AlarmCoordinator.
3. Check the "Visits" tab:
   - **Expected Result:** Dr. Jenkins appears under the "Upcoming" tab with date, time, location, preps, and attachment badge.
4. Tap the appointment card:
   - **Expected Result:** Appointment Detail opens. Shows doctor, place, time, preps checklist, attachments list, and doctor notes.
5. Tap an attached document in Detail:
   - **Expected Result:** Opens in external viewer (PDF reader / Image viewer) securely via `FileProvider` (`content://app.allinonereminder.fileprovider/attachments/...`).
6. Advance system clock or trigger the visit reminder alarm:
   - **Expected Result:** Full-screen `AlarmActivity` displays "Dr. Sarah Jenkins in 2 hours, at Heart Care, Suite 400".
   - Action buttons show **"Got it"** and **"Snooze"** (no "Skip" button).
7. Tap "Got it" on the alarm:
   - **Expected Result:** Occurrence marked done.
8. In Appointment Detail, tap "Mark as Done" and enter prescription notes:
   - **Expected Result:** Appointment moves from "Upcoming" to "Past". Notes and prescription notes are saved and visible in Past visits.
9. Export backup and inspect JSON/ZIP:
   - **Expected Result:** Backup zip contains `attachments/` folder with stored files, and JSON includes `appointments`, `appointmentPreps`, and `appointmentAttachments`.

---

### Item 16: History, progress, widget, and spoken alarms (#30)
**Objective:** Verify gamification rewards (points, streaks, activity rings, badges), adherence calendar history with pure color buckets, Glance home screen widget, and opt-in spoken alarms.
1. Open Progress tab in bottom navigation:
   - **Expected Result:** Shows Total Points card with trophy icon, Today's concentric Activity Rings (Meds, Hydration, Movement), Active Streaks list, and Badges achievement grid.
2. Tap "Adherence Calendar" or navigate to History screen:
   - **Expected Result:** Month selector (< Month YYYY >) and 7-column calendar grid.
   - Days with doses show color buckets: Dark Green (100% on time), Light Green (>=80%), Amber (<80% or any skipped), Gray (no doses / future).
   - Tapping any date reveals dose cards with status, time taken/skipped/missed, and notes.
3. Add Jetpack Glance widget to Android home screen:
   - Long-press home screen -> Widgets -> All-in-One Reminder ("Today's Plan").
   - **Expected Result:** Upcoming dose display ("Next: [Medicine] • [Time]") and activity rings status badges (Meds %, Water %, Move %).
   - Tapping widget opens the app to Today screen.
4. Spoken alarms test:
   - Go to Settings -> Toggle "Speak reminders aloud" ON.
   - Trigger a non-gentle alarm.
   - **Expected Result:** TextToSpeech speaks aloud once: "[Profile name], [Medicine name] [Dose] [Unit], [Timing / Instructions]".
   - Trigger a GENTLE alarm -> verify TTS does NOT speak aloud.
   - Turn toggle OFF -> verify TTS remains silent on all alarms.

---


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
| 13 | **Schedules & Taper** | Configure specific days, interval, monthly, PRN, taper | Doses appear at correct times, PRN logs without plan, taper doses apply. |
| 14 | **Stock ledger & Refills** | Log doses, refill, adjust stock, verify low stock notification | Append-only ledger decrements accurately; refill alert fires once per day. |
| 15 | **Appointments & Preps** | Create visit with preps and attachment; verify alarm text and FileProvider | Displays "Got it"/"Snooze", prep alarms fire, attachment opens in viewer, backup saves files. |
| 16 | **History, progress, widget & spoken alarms** | View Progress & History screens; add Glance widget; toggle spoken alarms | Rings & streaks display; calendar color buckets match adherence; widget refreshes; TTS speaks medicine aloud when enabled (silent when GENTLE or disabled). |
| 17 | **Health Connect verification** | Settings > turn on "Verify exercise with Health Connect" and grant access. Record a 15 min walk in Health Connect (or a fitness app), then let a "15 min walk" reminder come due. Also: phone without Health Connect; revoke the permission in Health Connect while the toggle is on. | With a recorded walk the reminder is ticked off ("Verified by Health Connect") and does not ring; with no data it rings normally. Without Health Connect, Settings explains why and offers install; nothing else changes. After revoking, the app does not crash and the toggle turns off. |

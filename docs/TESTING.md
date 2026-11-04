# Device test script

Run on at least:
- one Xiaomi/Redmi/POCO, Oppo/Realme or Vivo phone on Android 11–13
- one Pixel or Samsung phone on Android 14+

Record pass/fail per phone in the PR or issue.

## Prerequisites
1. Install debug APK:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Open app and grant permissions in the Permission Health Check screen.
3. Tap "Seed Sample Data" (or "Send test alarm in 1 minute").

---

## Test matrix

| # | Scenario | ADB / Device Steps | Expected Result |
|---|---|---|---|
| 1 | **Locked screen** | 1. Seed alarm for now + 1 min.<br>2. Press power button to lock phone.<br>3. Wait for scheduled time. | Screen turns on, full-screen `AlarmActivity` opens showing item name, dose, profile color banner, and Taken / Snooze / Skip buttons. Sound and vibration ring. |
| 2 | **Doze mode** | 1. Set alarm for now + 3 min.<br>2. Lock device and run:<br>`adb shell dumpsys battery unplug`<br>`adb shell dumpsys deviceidle force-idle`<br>3. Verify doze state:<br>`adb shell dumpsys deviceidle get`<br>(should be `IDLE`) | `AlarmManager.setAlarmClock()` wakes the system from Doze exactly on time (±1 min). Alarm rings and turns screen on. Reset battery after test:<br>`adb shell dumpsys battery reset` |
| 3 | **Reboot** | 1. Set alarm for now + 5 min.<br>2. `adb reboot`<br>3. Unlock phone once to decrypt credential storage.<br>4. Check alarm registration:<br>`adb shell dumpsys alarm \| grep app.allinonereminder` | `BOOT_COMPLETED` receiver runs `AlarmCoordinator.reschedule()`. Exact alarm is re-registered with OS. Reminder rings on time. |
| 4 | **App killed / Swipe recents** | 1. Schedule alarm for now + 2 min.<br>2. Swipe app away from Recents screen.<br>3. Wait for scheduled time. | Alarm fires via AlarmManager independent of UI process lifecycle. Full-screen alarm activity launches. |
| 5 | **Time change** | 1. Advance device time forward by 1 hour manually:<br>`adb shell "date $(date +%m%d%H%M%Y.%S)"`<br>or via Settings > Date & Time. | `TIME_SET` broadcast triggers `AlarmCoordinator.reschedule()`. Overdue items within grace re-ring; past-grace items marked MISSED. |
| 6 | **Timezone change** | 1. Have one `LOCAL_WALL_CLOCK` item and one `HOME_ZONE` (Asia/Kolkata) item.<br>2. Change system timezone:<br>`adb shell service call alarm 3 s16 Europe/London` | `TIMEZONE_CHANGED` broadcast fires. `LOCAL_WALL_CLOCK` item remains at local wall clock hour (e.g. 09:00 London). `HOME_ZONE` item shifts local display to keep original home instant (09:00 IST = 04:30 London). |
| 7 | **Grouping** | 1. Seed two medicines due at the same minute (e.g. 08:30).<br>2. Wait for alarm. | Single full-screen alarm displays with checklist of both items and "All taken" option. |
| 8 | **Meal deferral** | 1. Schedule AFTER breakfast item.<br>2. When alarm rings, tap "I haven't eaten yet" → "After I finish eating".<br>3. Tap "Eating done" notification. | Ongoing eating notification appears. After tapping "Done", reminder re-rings after configured offset minutes. |
| 9 | **Double dose** | 1. Mark medication "Taken".<br>2. Immediately tap "Taken" again within minGapMinutes (default 240m). | Blocking confirmation dialog appears warning user: "Last taken at [time] — log again?". |
| 10 | **Missed dose** | 1. Let alarm ring without user interaction.<br>2. Wait until `plannedAt + graceMinutes` (e.g. 60m). | Re-rings stop. Occurrence row status changes to `MISSED`. No further alarms scheduled for this occurrence. |
| 11 | **Backup/restore** | 1. In Settings, export `.airbackup` file.<br>2. Wipe app data: `adb shell pm clear app.allinonereminder`<br>3. Open app, restore `.airbackup` with passphrase. | All profiles, items, meal anchors, and history restored. `AlarmCoordinator` reschedules next due alarm. |

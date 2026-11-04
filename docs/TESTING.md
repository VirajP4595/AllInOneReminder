# Device test script

Run on at least:
- one Xiaomi/Redmi/POCO, Oppo/Realme or Vivo phone on Android 11–13
- one Pixel or Samsung phone on Android 14+

Record pass/fail per phone in the PR or issue.

> The M3 agent fills in the exact adb commands and expected results below. Skeleton:

| # | Scenario | How | Expected |
|---|---|---|---|
| 1 | Locked screen | Lock phone, wait for test alarm | Full-screen alarm turns screen on |
| 2 | Doze | `adb shell dumpsys deviceidle force-idle`, wait for alarm | Rings on time (±1 min) |
| 3 | Reboot | Reboot, unlock once, wait for next alarm | Rings on time |
| 4 | App killed | Swipe from recents / force stop from OEM cleaner | Rings on time (force-stop: document behaviour) |
| 5 | Time change | Change clock manually | Next alarm recomputed |
| 6 | Zone change | Switch timezone Asia/Kolkata → Europe/London | LOCAL_WALL_CLOCK items move; HOME_ZONE items keep the home instant |
| 7 | Grouping | Two items due at the same minute | One alarm with a checklist |
| 8 | Meal deferral | "I haven't eaten yet" → After I finish eating → Done | Rings offset minutes after Done |
| 9 | Double dose | Mark Taken twice within min gap | Confirm dialog shown |
| 10 | Missed | Ignore alarm past grace | Status MISSED; re-rings stop |
| 11 | Backup/restore | Export, wipe app data, restore | All data + photos back; alarms rescheduled |

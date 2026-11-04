# All-in-One Reminder — Plan (v0 draft)

Status: planning only, no code yet. Open questions are at the bottom; answer them before Phase 1 starts.

## 1. Core risk: reliable alarms
The hard part is not the UI. It is making the alarm ring on time, full-screen, on a locked phone, after a reboot, on OEM Android builds (Xiaomi/Redmi, Oppo, Vivo, Realme, OnePlus, Samsung) that kill background apps.
- Use `AlarmManager.setAlarmClock()`. It is exempt from Doze and shows an alarm icon. Do not use WorkManager for timed alerts.
- Use a full-screen intent notification that opens `AlarmActivity` (`showWhenLocked`, `turnScreenOn`), with a foreground service playing sound and vibration.
- Android 14+: check `NotificationManager.canUseFullScreenIntent()` and `AlarmManager.canScheduleExactAlarms()`, and walk the user to the settings screens if either is off.
- Reschedule on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` and app update.
- First-run setup: battery-optimisation exemption plus OEM "autostart" guidance.
- Only one future alarm is registered with the OS at a time (the next due one). The scheduler recomputes after every fire or edit. This avoids alarm-limit and drift bugs.

## 2. Tech stack (recommended)
| Concern | Choice | Why |
|---|---|---|
| Language/UI | Kotlin + Jetpack Compose (Material 3) | Alarm/lock-screen behaviour is native territory; cross-platform adds a bridge layer at the riskiest point |
| Storage | Room (SQLite) | Offline-first, no account needed |
| DI | Hilt | Standard |
| Photos | Android Photo Picker + CameraX, stored in app-private storage | No broad storage permission |
| Charts | Vico | Compose-native |
| Build/Release | Gradle + GitHub Actions producing a signed APK | Sideload; no Play Store policy constraints |
| Min SDK | 26 (Android 8) | Covers older phones parents tend to use |

## 3. Data model
- **Profile**: name, photo, colour, age, allergies, blood group, doctor contacts. (Self, Mom, Dad, Kid…)
- **MealAnchors** (per profile): wake, breakfast, lunch, snack, dinner, bed. Weekday and weekend sets.
- **Item**: type (Medicine, Injection, Water/Drink, Food, Supplement, Appointment, Test/Lab, Habit, Custom), name, photos[], dosage (amount + unit + form), instructions, notes, colour/icon, stock count, refill threshold, start/end date (course length).
- **Schedule** (one item can have several):
  - Fixed clock time(s)
  - Meal-relative: `BEFORE/AFTER <meal> ± N min` (e.g., 30 min before breakfast). Moving breakfast moves every linked reminder.
  - Interval: every N hours within a waking window
  - Recurrence: daily, specific weekdays, every N days, weekly, biweekly, monthly (date or "2nd Tuesday"), one-time
  - PRN / ad hoc: no alarm; a "Take now" button with a minimum gap and max-per-day guard
  - Taper: dose changes by date range
- **Occurrence/Log**: scheduled_at, status (Taken, Skipped, Snoozed, Missed), acted_at, actual dose, note.
- **Vital**: type (BP, sugar fasting/PP, weight, SpO2, temp, pulse, water ml, custom), value(s), time, profile.
- **Appointment**: doctor, place, time, purpose, prep reminders (e.g., "fast from 10 pm" the night before), attachments (prescriptions, reports).

## 4. Full-screen reminder UX
- Large photo of the medicine, name, dose, "after breakfast", and the profile it is for.
- Buttons: **Taken**, **Snooze** (5/10/15/custom), **Skip** (with reason).
- Re-ring every N min until acknowledged (configurable); after X tries, mark as Missed.
- Grouping: items due at the same moment show as one alarm with a checklist.
- Optional text-to-speech: "Dad, take Metformin 500, one tablet, after breakfast."
- Quiet hours; a gentle mode for water/habits versus a loud mode for critical meds and injections.

## 5. Screens
1. Today timeline (per profile + All), with tick-off
2. Items list → Item editor (photo, dosage, schedules)
3. Profile + meal anchors
4. History/adherence calendar
5. Vitals + charts
6. Appointments
7. Settings (permissions health check, sounds, backup)
8. Home-screen widget: "Next up" and today's progress

## 6. Phases
- **Phase 1 – MVP**: profiles, items with photo and dosage, fixed and meal-relative schedules, daily/weekday/every-N-days/one-time, full-screen alarm with Taken/Snooze/Skip, Today screen, reboot persistence, permission health check, local backup/restore (JSON + photos zip).
- **Phase 2**: interval and PRN, course end dates, stock and refill alerts, history and adherence %, grouping, TTS, widget.
- **Phase 3**: vitals and charts, appointments with prep reminders, doctor report PDF (meds list + adherence + vitals), injection site rotation.
- **Phase 4 (needs backend; decide later)**: multi-device family sync, caregiver escalation ("Dad missed his 9 am dose"), Google Drive auto-backup.

## 7. Testing
- Unit tests for the recurrence engine (DST/timezone change, month-end, biweekly anchors, meal moves).
- Instrumented tests for the alarm path, plus a manual matrix on at least one Xiaomi/Oppo-class device and one Pixel/Samsung, covering locked screen, Doze (`adb shell dumpsys deviceidle force-idle`), reboot and app-killed states.

## 8. Non-goals and disclaimers
- Not medical advice; no dose calculation or drug-interaction checks in v1.
- No cloud or account in Phases 1–3. Data stays on the phone.

## 9. Open questions
1. Do family members use **their own phones**, or does everything ring on one phone? (Decides whether Phase 4 sync is needed at all.)
2. Android version and brand of every phone it must run on?
3. App language(s): English only, or Hindi/regional as well?
4. Should missed critical doses alert someone else (SMS/call)?
5. Is backup to a file enough, or is Google Drive backup required?

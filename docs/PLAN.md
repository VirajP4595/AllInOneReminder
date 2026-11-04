# All-in-One Reminder — Plan (v1 draft)

Status: planning only, no code yet.

## 0. Decisions so far
| Topic | Decision |
|---|---|
| Devices | Multiple paired family devices; each device chooses per profile whether it gets notified |
| Min Android | 11 (minSdk 30), target latest |
| Language | English only |
| Missed-dose escalation | Yes, to paired caregivers |
| New scope | Exercise/habit reminders, gamification, "snooze until after I eat" |

## 1. Core risk: reliable alarms
- Use `AlarmManager.setAlarmClock()`, which Doze does not delay. Use WorkManager and FCM only for sync, **never for the alarm itself**.
- A full-screen intent opens `AlarmActivity` (`showWhenLocked`, `turnScreenOn`), with a foreground service for sound and vibration.
- Android 14+: check `canUseFullScreenIntent()` and `canScheduleExactAlarms()`. A permission health-check screen shows each one as red or green.
- Reschedule on boot, time or timezone change, and app update. Only the next due alarm is registered with the OS; the schedule is recomputed after every fire or edit.
- First-run setup: battery-optimisation exemption plus guidance for OEM autostart settings (Xiaomi, Oppo, Vivo, Realme, Samsung).

## 2. Architecture: local-first plus sync
Every device schedules its **own local alarms** from its local database. The cloud syncs data only. Reminders therefore still ring with no internet.

```
Phone A (Mom)                Firebase                     Phone B (Viraj)
Room DB ──sync──▶ Firestore ◀──sync── Room DB
local alarms                 Cloud Function (every 5 min):   local alarms (opt-in per profile)
Taken/Skip log ─▶            "dose due + grace, no log?"  ─▶ FCM push "Mom missed 9:00 Metformin"
```
- **Auth:** Google Sign-In via Firebase Auth.
- **Household:** one household with many profiles and many member accounts. Pairing works by QR code or a 6-digit invite code.
- **Per device, per profile notification mode:** `Full-screen alarm` / `Notification` / `Missed-only alerts` / `Off`.
- **Escalation is checked on the server**, not on the patient's phone. A dead, off or offline phone can't report its own miss.
- **Conflicts:** last-write-wins per field. Every row has a UUID, `updatedAt` and a soft-delete flag. Dose logs are append-only, so they never conflict.
- **Cost:** Firestore and FCM fit the free tier for a family. Scheduled Cloud Functions need the Blaze (pay-as-you-go) plan with a card on file. Expected cost is about $0 at family scale.
- **Privacy:** health data sits in your own Firebase project. Security rules allow access only to household members. Photos go to Cloud Storage with the same rules.

## 3. Tech stack
Kotlin, Jetpack Compose (Material 3), Room, Hilt, WorkManager (sync), Firebase (Auth, Firestore, Storage, FCM, Functions), CameraX + Photo Picker, Vico charts, Health Connect (optional step verification), GitHub Actions building a signed APK.

## 4. Data model
- **Household**, **Member** (account), **DevicePrefs** (member × profile → notify mode)
- **Profile**: name, photo, colour, age, allergies, blood group, doctor contacts
- **MealAnchors** per profile: wake, breakfast, lunch, snack, dinner, bed (weekday and weekend sets)
- **Item**: type (Medicine, Injection, Water/Drink, Food, Supplement, **Exercise**, Appointment, Lab test, Habit, Custom), name, photos[], dosage, instructions, stock, refill threshold, start/end, criticality (Critical / Normal / Gentle)
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
- **P2 – Family sync:** Firebase Auth, household and pairing, Firestore sync, per-device notification modes, server-side missed-dose escalation via FCM.
- **P3 – Engagement:** history, streaks, points, badges, daily rings, widget, stock and refill alerts, PRN and interval schedules, TTS, Health Connect walk verification.
- **P4 – Health records:** vitals and charts, appointments with prep reminders, doctor PDF report, injection site rotation.

## 9. Testing
- Unit tests for the recurrence engine (timezone, month-end, biweekly anchors, meal moves, meal-deferred snooze).
- Firestore security-rule tests with the emulator.
- Manual device matrix: at least one Xiaomi/Oppo-class device on Android 11 and one Pixel/Samsung on Android 14+, covering locked screen, Doze (`adb shell dumpsys deviceidle force-idle`), reboot, app killed, airplane mode, and escalation while the patient's phone is off.

## 10. Non-goals and disclaimers
Not medical advice; no dose calculation or interaction checks. No SMS/call escalation in v1 (it needs a paid gateway); push only.

## 11. Remaining open questions
1. Escalation grace period: how long after a critical dose is due before a caregiver is alerted? (Proposed: 30 min, configurable per item.)
2. Is a Google account acceptable for every family member, including elderly parents?
3. Who may edit a profile's medicines: everyone in the household, or only "admins"?

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
| Escalation grace | 60 min default, overridable per item (e.g., 15 min for insulin) |
| Sign-in | Google account for every member |
| Budget | **$0 hard requirement.** No card on any service. Sideloaded APK only (no Play Store $25 fee) |
| Backend | Supabase free tier + FCM (free); end-to-end encrypted |
| Edit rights | Any household member can edit any profile, with a change log and change alerts (see §2) |

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

## 11. Remaining open questions
None blocking P1.

# Security and code review, November 2026

Scope: the Android app (`app`, `core/*`) on `main` after the redesign (PR #59) and the lock-screen alarm fix (PR #60). The backend (`backend/supabase`) and the sync/crypto modules are not used by the app yet and were reviewed earlier (#35 to #41).

Method: manual review of the manifest, every exported component, intents and PendingIntents, notifications, storage, encryption, backup/restore, logging and the alarm path.

## Fixed in this review

| # | Severity | Finding | Fix |
|---|---|---|---|
| 1 | High (functional) | Alarm needed an unlock: `AlarmActivity` called `KeyguardManager.requestDismissKeyguard()`, which puts the PIN/pattern prompt on top of the alarm. | Removed; the activity only shows over the keyguard and turns the screen on (PR #60). |
| 2 | Medium | Restore trusted photo and attachment paths from the backup file. A crafted path such as `photos/../databases/allinonereminder.db` made the app treat its own database as a photo; deleting that photo could delete the database. | Restore keeps only a validated file name (`safeArchiveFileName`) and rebuilds the path under the app's own folder; bad names reject the backup. |
| 3 | Medium (functional) | Restored attachments could not be opened: the app stores attachments by file name, but restore wrote back a full path. | Same fix as 2: attachments are restored as a file name. |
| 4 | Medium | Refill notification showed the medicine name on a secure lock screen (its channel had no lock-screen visibility). | Notification is `VISIBILITY_PRIVATE` with a neutral public version; the channel is private for new installs. |
| 5 | Low | Restore had no limit on how far an archive may expand (zip bomb: fill storage or run out of memory). Needs the file's own passphrase. | Extraction is capped at 300 MB in total and 30 MB for `backup.json`. |
| 6 | Low | `BootReceiver` is exported (required) but acted on any action, so another app could make it recompute alarms repeatedly. | Only the five system actions it is registered for are handled. |
| 7 | Low | Backup passphrase minimum was 6 characters; the backup file can be attacked offline. | Minimum 8, with a hint to use a short phrase. PBKDF2 at 310,000 iterations stays. |

## Checked and fine

- **Exported components:** only `MainActivity` (launcher, reads no intent data), the Health Connect rationale screen (static text), its `VIEW_PERMISSION_USAGE` alias (guarded by `START_VIEW_PERMISSION_USAGE`), `BootReceiver` (now action-checked) and the widget receiver (standard). Alarm, notification-action receivers, the ring service and the FileProvider are not exported.
- **PendingIntents:** all 8 use `FLAG_IMMUTABLE`.
- **Database:** SQLCipher with a random 32-byte key wrapped by an AES-256-GCM key in the Android Keystore (`DatabaseKeyManager`).
- **Backup file:** AES-256-GCM, random salt and IV per file, PBKDF2-HMAC-SHA256 (310k), Zip Slip checks on entry names, 50 MB input cap. `android:allowBackup="false"`, so the encrypted database is never copied by cloud backup.
- **Alarm notifications:** private on a secure lock screen with a neutral public version ("Medicine reminder").
- **Logging:** no `Log`/`println` calls, so no medicine names, doses or keys in logs.
- **SQL:** Room queries only, all parameterised; no raw SQL outside migrations.
- **Files:** photos and attachments are saved under UUID names; the FileProvider exposes only `photos/` and `attachments/`, read-only, per grant.
- **Network:** no `INTERNET` permission. Health Connect is read-only and on-device.

## Accepted trade-offs (owner decision)

- The full-screen alarm shows the medicine name over the lock screen, so anyone holding the phone can see it while it rings. This is how alarm apps work; a "hide names on the lock screen" setting could be added.
- The home-screen widget shows the next reminder's name.
- "Speak reminders aloud" says medicine names out loud (off by default, with a warning).
- Notification actions (Taken, Snooze) work from the lock screen.

## Open items

- `DatabaseKeyManager` falls back to a software key in SharedPreferences when the Android Keystore provider is missing. That only happens in unit tests, but production code could fail closed instead.
- The backup header (magic, version) is not covered by the GCM tag. Tampering only breaks parsing or decryption, so the risk is low.
- Several background paths swallow every exception (`CoroutineExceptionHandler { _, _ -> }`), which hides failures. That's a reliability issue, not a security one.
- Release signing key: keep `aiar-release.jks` and its passwords backed up in two places; GitHub cannot give the key back.

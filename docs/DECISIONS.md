# Decision log

Format: `YYYY-MM-DD — Decision — Reason — Alternatives considered`

- 2026-11-04 — Native Kotlin + Jetpack Compose — The alarm and lock-screen path is native; a cross-platform layer adds risk there — Flutter, React Native
- 2026-11-04 — Local-first; each device schedules its own alarms — Reminders must ring offline; push delivery is unreliable on OEM ROMs — Server-driven push alarms
- 2026-11-04 — Backend for Phase 2: Supabase free + FCM only, end-to-end encrypted — $0 with no card; server can't read health data — Firebase Blaze (needs a card), self-hosted, local-only
- 2026-11-04 — Missed-dose check runs on the server — A dead or offline patient phone cannot report its own miss — Patient-device-only escalation
- 2026-11-04 — Escalation grace 60 min default, per item override — Owner choice; shorter for insulin-type items — Single global value
- 2026-11-04 — Any household member can edit, with change log + change alerts + 30-day undo — Owner choice; safeguards against silent dose changes — Admin-only editing
- 2026-11-04 — Gamification rewards on-time habits; meds earn points only inside their window — Avoid rewarding late or double doses — Uniform gamification
- 2026-11-04 — Encrypted DB means alarms resume only after first unlock after reboot — Do not store medicine data in device-protected storage — Direct-boot-aware storage
- 2026-11-04 — Foreground service type `mediaPlayback` for `AlarmRingService` — On Android 14+ (API 34+), foreground services must declare a specific type; mediaPlayback matches continuous alarm audio and vibration playback, with specialUse declared in manifest as companion — specialUse only, systemExempted
- 2026-11-04 — Single `setAlarmClock` registration with `AlarmCoordinator` idempotency — setAlarmClock bypasses Doze mode and gives highest priority to ring on locked screens without battery saver delays — Periodic background workers, setExactAndAllowWhileIdle
- 2026-11-04 — Grace calculation uses `maxOf(plannedAt, dueAt) + graceMinutes` for `DEFERRED_MEAL` occurrences — Prevents active meal deferrals from prematurely expiring as MISSED while the meal is in progress — Strict `plannedAt + graceMinutes`
- 2026-11-04 — Notification "Take" action routes to `AlarmActivity` confirmation dialog if within `minGapMinutes` — Prevents accidental double doses from quick lock-screen notification actions while maintaining §19 review checklist compliance — Silently ignoring or silently logging duplicate dose
- 2026-11-04 — Encrypted Backup uses AES-256-GCM + PBKDF2-HMAC-SHA256 (310,000 iterations) with ZIP archive containing DB JSON and photos — Conforms to §17 spec for encrypted offline backup and restore, authenticates backup integrity with GCM AEAD tag without leaking health data, and operates natively on API 30+ without external cryptography dependencies — Plain zip, AES-CBC with HMAC-SHA256
- 2026-11-04 — Release signing falls back to debug keystore when signing secrets are absent — Allows CI and local builds to assemble installable APKs cleanly without failing when secrets are unconfigured, while automatically using production signing when secrets are set — Hardcoded test keystore, failing release builds

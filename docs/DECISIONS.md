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
- 2026-11-04 — Destructive migrations disabled; all future schema upgrades require explicit Migration and MigrationTestHelper tests — Prevents accidental silent wipe of patient medications, doses, and history on app updates — Fallback to destructive migration
- 2026-11-04 — android:allowBackup="false" with full backup exclusions — Prevents Android Cloud Backup from copying encrypted DB without KeyStore master key, which causes unrecoverable crash on new device — Cloud backup enabled
- 2026-11-04 — net.zetetic:sqlcipher-android:4.6.1 with 16 KB page-size support and SupportOpenHelperFactory — Replaces deprecated android-database-sqlcipher 4.5.4 to support modern Android 15+ 16 KB page size architecture — Legacy sqlcipher
- 2026-11-04 — Profile soft-deletion cascades to items and schedules in transaction — Prevents orphaned medicine alarms from continuing to ring after profile deletion — Profile-only soft delete

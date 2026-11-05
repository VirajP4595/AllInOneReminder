# Backend (Supabase CLI project)

Phase 2 backend for family sync: schema, row-level security (RLS), invites and
escalation slots. Spec: `docs/ROADMAP_P2_P4.md` §1.1–§1.2.

Everything under `backend/supabase` is additive to the Android app. It does not
change any Gradle build.

## What the server can see

The server stores **ciphertext only** for health data (`sync_rows`, the `photos`
bucket). It can see household membership, device FCM tokens, opaque
`profile_ref` / `occurrence_ref` values, row sizes and escalation timing
(due / grace / done). It never sees medicine names, doses, notes, photos or
vitals. See §1.1 of the roadmap.

## Tables

| Table | Purpose |
|---|---|
| `households` | household id, creator |
| `members` | household membership, encrypted display name, `removed_at` |
| `invites` | hashed 6-digit code, expiry, single use, pairing handshake fields |
| `devices` | per-user device, FCM token, heartbeat |
| `device_profile_prefs` | per-device × opaque profile notification mode |
| `sync_rows` | encrypted row log with `version` and the `server_seq` pull cursor |
| `escalation_slots` | opaque occurrence refs, due time, grace, done/escalated timestamps |

The `photos` Storage bucket is private. Object paths are
`household_id/<random>.bin` and contain encrypted bytes only.

## Local development

Prerequisites: Docker running, Node.js, and the Supabase CLI (no global install
needed — use `npx`).

```bash
cd backend
npx --yes supabase@latest start
npx --yes supabase@latest db reset
npx --yes supabase@latest test db
```

`db reset` applies `supabase/migrations/*.sql` from scratch. `test db` runs the
pgTAP suite in `supabase/tests/rls_test.sql`. The suite currently has **40**
assertions and must end with `All tests successful`.

To stop the stack:

```bash
npx --yes supabase@latest stop
```

## CI

`.github/workflows/backend.yml` starts the local stack in Docker, resets the
database and runs the pgTAP suite on every pull request that touches
`backend/**`. No secrets are required for CI.

## Production setup checklist

Follow `docs/SETUP.md` §3. The backend-specific parts:

1. Create a Supabase project on the free plan and note the Project URL and
   anon/publishable key (safe to ship in the app; RLS protects the data).
2. Enable Google sign-in and the `pg_cron` and `pg_net` extensions.
3. Deploy the schema: `npx --yes supabase@latest db push` (or run the
   migration SQL from the dashboard SQL editor).
4. Deploy the Edge Functions (`escalate`, `heartbeat-watch` — added in P2-A2)
   with `npx --yes supabase@latest functions deploy`.
5. Set the FCM service-account JSON **only** as a Supabase secret:

   ```bash
   npx --yes supabase@latest secrets set FCM_SERVICE_ACCOUNT="$(cat service-account.json)"
   ```

   The secret name is `FCM_SERVICE_ACCOUNT`. Never commit the JSON, never put it
   in the app, and never log it.
6. Add repository secrets for CI deploys only if needed:
   `SUPABASE_ACCESS_TOKEN` and `SUPABASE_DB_PASSWORD`. Never store the
   `service_role` key in the app.

## Decisions

- **RLS helpers are `SECURITY DEFINER`.** `is_active_member`,
  `is_household_creator` and `device_household` read `members` / `households` /
  `devices`; defining them as invoker would recurse into their own policies.
  They are revoked from `public` and granted only to `authenticated`.
- **`members` is editable by any active member** (PLAN §0). The creator may add
  the first row while bootstrapping; after that an active member adds family
  members.
- **Invites are enforced by a trigger**, not only by RLS: expired invites cannot
  be inserted, and a used invite cannot be updated again (`invite_expired` /
  `invite_already_used`, SQLSTATE `23514`). The code is stored as a SHA-256 hex
  hash (`code_hash`) and is never stored in plain text.
- **`sync_rows` writes go through `sync_rows_before_write`.** It rejects
  `version <=` the stored version (`stale_version`) and assigns a fresh
  `server_seq` on every insert and update, so clients cannot forge the pull
  cursor.
- **Invitee-side handshake is deferred.** The invite row already carries
  `inviter_pubkey`, `invitee_pubkey` and `sealed_key`, but the RPC that lets a
  non-member claim an invite and become a member is P2-B1 work. Until then only
  members can create and read invites.
- **Photo policies derive the household from the first path segment**:
  `(storage.foldername(name))[1]::uuid`. Uploads are only accepted under a
  household the caller is an active member of.

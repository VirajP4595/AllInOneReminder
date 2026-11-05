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
pgTAP suites in `supabase/tests/`. There are currently **75** assertions (63 RLS
+ 12 job/cron) and the run must end with `All tests successful`.

The Edge Functions have their own Deno tests (mocked FCM endpoint, no network):

```bash
cd backend
npx --yes deno@latest test supabase/functions
```

To stop the stack:

```bash
npx --yes supabase@latest stop
```

## Edge Functions

Both functions are small HTTP entrypoints around pure, tested cores in
`supabase/functions/_shared`.

| Function | What it does |
|---|---|
| `escalate` | Finds `escalation_slots` past `due_at + grace_minutes` that are neither done nor escalated, sends `{type:"missed", occurrence_ref, profile_ref}` to every household device whose pref for that profile is not `OFF` (excluding the owner), and marks the slot escalated. |
| `heartbeat-watch` | Finds devices that own open slots and have not checked in for more than 24 h, sends `{type:"device_silent", device_id}` to the other devices in the household, and records the alert. |

Idempotency lives in `escalation_sends`: a slot is escalated only when every
eligible device has a terminal (`sent` or `invalid_token`) row. A transient FCM
failure writes nothing, so the next run retries only the devices that still
need it and never double-sends. `device_silence_alerts` enforces one alert per
device per 24 h. Invalid FCM tokens (HTTP 400/404) are cleared from `devices` so
they are not retried forever.

The FCM service-account JSON is read **only** from the `FCM_SERVICE_ACCOUNT`
Supabase secret. Payloads contain opaque refs only — no names, medicines or
doses.

The `pg_cron` jobs call `/functions/v1/<name>` every five minutes. Both functions are deployed with
`verify_jwt = false` (see `supabase/config.toml`) and reject any request that does not carry the
`x-cron-secret` header matching the `CRON_SECRET` Edge Function secret, because the anon key is
public and must not be enough to trigger them. The project URL and the same secret are stored in
Supabase Vault, run once per project in the SQL editor:

```sql
select vault.create_secret('https://<project-ref>.supabase.co', 'supabase_url');
select vault.create_secret('<the same long random string as CRON_SECRET>', 'cron_secret');
```

Until both exist, the cron helper raises `cron_not_configured` and nothing is sent. The
service-role key stays inside the Edge Function environment and is never stored in SQL.

List queries are read page by page (`_shared/paging.ts`) because PostgREST caps each response at
1000 rows; `in.(...)` filters are chunked to keep URLs short.

`escalation_slots.critical` is not read here: it is reserved for the opt-in SMS fallback (P2-B4),
which runs on the patient's phone.

## CI

`.github/workflows/backend.yml` has two jobs on every pull request that touches
`backend/**`:

- `test`: starts the local Supabase stack in Docker, resets the database and
  runs the pgTAP suites.
- `deno`: runs the Edge Function tests with Deno. No network, credentials or
  secrets are required.

## Production setup checklist

Follow `docs/SETUP.md` §3. The backend-specific parts:

1. Create a Supabase project on the free plan and note the Project URL and
   anon/publishable key (safe to ship in the app; RLS protects the data).
2. Enable Google sign-in and the `pg_cron` and `pg_net` extensions.
3. Deploy the schema: `npx --yes supabase@latest db push` (or run the
   migration SQL from the dashboard SQL editor).
4. Deploy the Edge Functions:
   `npx --yes supabase@latest functions deploy escalate` and
   `npx --yes supabase@latest functions deploy heartbeat-watch`.
5. Generate a long random string (for example `openssl rand -hex 32`), store it in Vault as
   shown in **Edge Functions** above, and also set it as the `CRON_SECRET` Edge Function secret:
   `npx --yes supabase@latest secrets set CRON_SECRET=<the string>`.
6. Set the FCM service-account JSON **only** as a Supabase secret:

   ```bash
   npx --yes supabase@latest secrets set FCM_SERVICE_ACCOUNT="$(cat service-account.json)"
   ```

   The secret name is `FCM_SERVICE_ACCOUNT`. Never commit the JSON, never put it
   in the app, and never log it.
7. Add repository secrets for CI deploys only if needed:
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
- **Escalation idempotency uses a delivery ledger, not a single flag.** A slot
  is marked escalated only after every eligible device has a terminal
  `escalation_sends` row. Transient failures stay pending and are retried, while
  successful sends are never repeated.
- **A device with no pref for a profile is not a target.** Missing pref means the
  device has not opted in; `OFF` is an explicit opt-out.
- **Heartbeat alerts are recorded only after at least one terminal send.** If
  there are no other devices or every send hits a 5xx/network error, the alert
  stays pending and is retried on the next run. Once recorded, it is suppressed
  for 24 h.
- **Cron scheduling is data, not code.** The jobs call
  `public.invoke_edge_function`, which reads the project URL and the shared
  `cron_secret` from Supabase Vault at execution time. The service-role key is
  never present in SQL.

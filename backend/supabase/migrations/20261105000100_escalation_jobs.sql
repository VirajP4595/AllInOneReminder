-- P2-A2: server-side missed-dose escalation and heartbeat watch.
--
-- The Edge Functions behind these jobs send opaque refs only. All health data
-- remains client-side ciphertext; the server learns only *when* a dose was due
-- and whether it was taken.

create extension if not exists pg_cron;
create extension if not exists pg_net;

-- Per (slot, device) delivery ledger. This is what makes escalation idempotent:
-- a slot is marked escalated only once every eligible device has a terminal row
-- here. A transient FCM failure writes no row, so the next run retries only the
-- devices that still need it and never double-sends to the ones that succeeded.
create table public.escalation_sends (
    slot_id uuid not null references public.escalation_slots (id) on delete cascade,
    device_id uuid not null references public.devices (id) on delete cascade,
    status text not null check (status in ('sent', 'invalid_token')),
    sent_at timestamptz not null default now(),
    primary key (slot_id, device_id)
);

-- A silent device is alerted at most once per 24 h.
create table public.device_silence_alerts (
    device_id uuid primary key references public.devices (id) on delete cascade,
    last_alert_at timestamptz not null default now()
);

-- Server-only tables: RLS is enabled and deliberately has no policies, so the
-- anon/authenticated roles can never read them. The service role bypasses RLS.
alter table public.escalation_sends enable row level security;
alter table public.device_silence_alerts enable row level security;

-- Called by pg_cron. The URL and the anon key are database settings, not repo
-- secrets: `alter database postgres set app.supabase_url = '...'` and
-- `alter database postgres set app.anon_key = '...'` (see backend/README.md).
-- The anon key is public by design; the service role key never leaves the
-- Edge Function environment.
create or replace function public.invoke_edge_function(p_function text)
returns bigint
language sql
security definer
set search_path = public, net
as $$
    select net.http_post(
        url := current_setting('app.supabase_url') || '/functions/v1/' || p_function,
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'Authorization', 'Bearer ' || current_setting('app.anon_key')
        ),
        body := '{}'::jsonb
    );
$$;

revoke all on function public.invoke_edge_function(text) from public;

select cron.schedule(
    'escalate-every-5-minutes',
    '*/5 * * * *',
    $$select public.invoke_edge_function('escalate');$$
);

select cron.schedule(
    'heartbeat-watch-every-5-minutes',
    '*/5 * * * *',
    $$select public.invoke_edge_function('heartbeat-watch');$$
);

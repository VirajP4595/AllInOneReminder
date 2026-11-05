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

-- Called by pg_cron. The project URL and a random shared secret live in Supabase Vault (not in
-- the repo, not in database settings):
--   select vault.create_secret('https://<ref>.supabase.co', 'supabase_url');
--   select vault.create_secret('<long random string>', 'cron_secret');
-- The same random string is set as the CRON_SECRET Edge Function secret. The functions are
-- deployed with verify_jwt = false and reject any request without a matching x-cron-secret, so
-- the public anon key shipped in the APK cannot trigger them (review E1).
create or replace function public.invoke_edge_function(p_function text)
returns bigint
language plpgsql
security definer
set search_path = public, net, vault
as $$
declare
    v_url text;
    v_secret text;
begin
    select decrypted_secret into v_url
    from vault.decrypted_secrets where name = 'supabase_url';
    select decrypted_secret into v_secret
    from vault.decrypted_secrets where name = 'cron_secret';
    if v_url is null or v_secret is null then
        raise exception 'cron_not_configured';
    end if;
    return net.http_post(
        url := v_url || '/functions/v1/' || p_function,
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'x-cron-secret', v_secret
        ),
        body := '{}'::jsonb
    );
end;
$$;

-- Server-only: pg_cron runs as postgres. Clients must not be able to call it through the API.
revoke all on function public.invoke_edge_function(text) from public, anon, authenticated;

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

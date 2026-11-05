begin;

select plan(12);

select is(
    (select count(*)::int from pg_extension where extname = 'pg_cron'),
    1,
    'pg_cron is installed'
);

select is(
    (select count(*)::int from pg_extension where extname = 'pg_net'),
    1,
    'pg_net is installed'
);

select is(
    (select count(*)::int from cron.job where jobname = 'escalate-every-5-minutes'),
    1,
    'the escalate cron job is registered'
);

select is(
    (select count(*)::int from cron.job where jobname = 'heartbeat-watch-every-5-minutes'),
    1,
    'the heartbeat-watch cron job is registered'
);

select is(
    (select schedule from cron.job where jobname = 'escalate-every-5-minutes'),
    '*/5 * * * *',
    'escalate runs every 5 minutes'
);

select is(
    (select schedule from cron.job where jobname = 'heartbeat-watch-every-5-minutes'),
    '*/5 * * * *',
    'heartbeat-watch runs every 5 minutes'
);

select ok(
    (select command from cron.job where jobname = 'escalate-every-5-minutes')
        like '%invoke_edge_function(''escalate'')%',
    'the escalate job calls the escalate Edge Function'
);

select ok(
    (select command from cron.job where jobname = 'heartbeat-watch-every-5-minutes')
        like '%invoke_edge_function(''heartbeat-watch'')%',
    'the heartbeat job calls the heartbeat-watch Edge Function'
);

select ok(
    (select bool_and(relrowsecurity)
     from pg_class
     where oid in (
         'public.escalation_sends'::regclass,
         'public.device_silence_alerts'::regclass
     )),
    'RLS is enabled on the server-only job tables'
);

select is(
    (select count(*)::int
     from pg_policies
     where schemaname = 'public'
       and tablename in ('escalation_sends', 'device_silence_alerts')),
    0,
    'server-only job tables expose no client policies'
);

select ok(
    not has_function_privilege('anon', 'public.invoke_edge_function(text)', 'execute')
    and not has_function_privilege('authenticated', 'public.invoke_edge_function(text)', 'execute'),
    'clients cannot call invoke_edge_function through the API'
);

select throws_ok(
    $$ select public.invoke_edge_function('escalate') $$,
    'P0001',
    'cron_not_configured',
    'the cron helper refuses to run until Vault holds the URL and secret'
);

select * from finish();
rollback;

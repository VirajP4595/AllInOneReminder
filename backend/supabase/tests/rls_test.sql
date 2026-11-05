begin;

create extension if not exists pgtap with schema extensions;

select plan(40);

-- Fixtures ---------------------------------------------------------------
insert into auth.users (id) values
    ('aaaaaaaa-0000-0000-0000-000000000001'),
    ('bbbbbbbb-0000-0000-0000-000000000002'),
    ('cccccccc-0000-0000-0000-000000000003');

select set_config(
    'request.jwt.claims',
    '{"sub":"aaaaaaaa-0000-0000-0000-000000000001","role":"authenticated"}',
    true
);
select set_config('request.jwt.claim.sub', 'aaaaaaaa-0000-0000-0000-000000000001', true);
set local role authenticated;

insert into public.households (id, created_by)
values ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0000-0000-0000-000000000001');
insert into public.members (household_id, user_id)
values ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0000-0000-0000-000000000001');

select set_config(
    'request.jwt.claims',
    '{"sub":"cccccccc-0000-0000-0000-000000000003","role":"authenticated"}',
    true
);
select set_config('request.jwt.claim.sub', 'cccccccc-0000-0000-0000-000000000003', true);

insert into public.households (id, created_by)
values ('22222222-2222-2222-2222-222222222222', 'cccccccc-0000-0000-0000-000000000003');
insert into public.members (household_id, user_id)
values ('22222222-2222-2222-2222-222222222222', 'cccccccc-0000-0000-0000-000000000003');

select set_config(
    'request.jwt.claims',
    '{"sub":"aaaaaaaa-0000-0000-0000-000000000001","role":"authenticated"}',
    true
);
select set_config('request.jwt.claim.sub', 'aaaaaaaa-0000-0000-0000-000000000001', true);

insert into public.members (household_id, user_id, display_name_cipher)
values (
    '11111111-1111-1111-1111-111111111111',
    'bbbbbbbb-0000-0000-0000-000000000002',
    '\x00'
);

-- Household RLS ----------------------------------------------------------
select is(
    (select count(*)::int from public.households where id = '11111111-1111-1111-1111-111111111111'),
    1,
    'active member reads their household'
);

select is(
    (select count(*)::int from public.households where id = '22222222-2222-2222-2222-222222222222'),
    0,
    'non-member sees zero households'
);

select is(
    (select count(*)::int from public.members where household_id = '11111111-1111-1111-1111-111111111111'),
    2,
    'active member reads all household members'
);

select is(
    (select count(*)::int from public.members where household_id = '22222222-2222-2222-2222-222222222222'),
    0,
    'non-member sees zero members'
);

select throws_ok(
    $$ insert into public.households (id, created_by)
       values ('33333333-3333-3333-3333-333333333333', 'cccccccc-0000-0000-0000-000000000003') $$,
    '42501',
    null,
    'cannot create a household for another user'
);

-- Invites ----------------------------------------------------------------
select lives_ok(
    $$ insert into public.invites (id, household_id, code_hash, expires_at, created_by)
       values (
           '44444444-4444-4444-4444-444444444444',
           '11111111-1111-1111-1111-111111111111',
           encode(digest('123456', 'sha256'), 'hex'),
           now() + interval '15 minutes',
           'aaaaaaaa-0000-0000-0000-000000000001'
       ) $$,
    'member creates an invite with a code hash'
);

select is(
    (select count(*)::int from public.invites where id = '44444444-4444-4444-4444-444444444444'),
    1,
    'member reads their household invite'
);

select throws_ok(
    $$ insert into public.invites (id, household_id, code_hash, expires_at, created_by)
       values (
           '45454545-4545-4545-4545-454545454545',
           '11111111-1111-1111-1111-111111111111',
           encode(digest('999999', 'sha256'), 'hex'),
           now() - interval '1 minute',
           'aaaaaaaa-0000-0000-0000-000000000001'
       ) $$,
    '23514',
    'invite_expired',
    'expired invites cannot be created'
);

select set_config(
    'request.jwt.claims',
    '{"sub":"cccccccc-0000-0000-0000-000000000003","role":"authenticated"}',
    true
);
select set_config('request.jwt.claim.sub', 'cccccccc-0000-0000-0000-000000000003', true);

select is(
    (select count(*)::int from public.invites),
    0,
    'non-member sees zero invites'
);

select throws_ok(
    $$ insert into public.invites (id, household_id, code_hash, expires_at, created_by)
       values (
           '46464646-4646-4646-4646-464646464646',
           '11111111-1111-1111-1111-111111111111',
           encode(digest('000000', 'sha256'), 'hex'),
           now() + interval '15 minutes',
           'cccccccc-0000-0000-0000-000000000003'
       ) $$,
    '42501',
    null,
    'non-member cannot create an invite in another household'
);

select set_config(
    'request.jwt.claims',
    '{"sub":"aaaaaaaa-0000-0000-0000-000000000001","role":"authenticated"}',
    true
);
select set_config('request.jwt.claim.sub', 'aaaaaaaa-0000-0000-0000-000000000001', true);

select lives_ok(
    $$ update public.invites set used_at = now()
       where id = '44444444-4444-4444-4444-444444444444' $$,
    'invite can be used once'
);

select throws_ok(
    $$ update public.invites set used_at = now()
       where id = '44444444-4444-4444-4444-444444444444' $$,
    '23514',
    'invite_already_used',
    'a used invite cannot be used again'
);

-- sync_rows: RLS, version rejection, cursor ------------------------------
select lives_ok(
    $$ insert into public.sync_rows
           (household_id, entity_type, row_id, version, updated_at, key_version, nonce, ciphertext)
       values (
           '11111111-1111-1111-1111-111111111111',
           'item', 'row-1', 1, now(), 1, '\x00', '\x01'
       ) $$,
    'member inserts an encrypted sync row'
);

create temporary table seq_probe as
select server_seq
from public.sync_rows
where household_id = '11111111-1111-1111-1111-111111111111'
  and entity_type = 'item'
  and row_id = 'row-1';

select set_config(
    'request.jwt.claims',
    '{"sub":"bbbbbbbb-0000-0000-0000-000000000002","role":"authenticated"}',
    true
);
select set_config('request.jwt.claim.sub', 'bbbbbbbb-0000-0000-0000-000000000002', true);

select is(
    (select count(*)::int from public.sync_rows
     where household_id = '11111111-1111-1111-1111-111111111111'),
    1,
    'active member reads household sync rows'
);

select set_config(
    'request.jwt.claims',
    '{"sub":"cccccccc-0000-0000-0000-000000000003","role":"authenticated"}',
    true
);
select set_config('request.jwt.claim.sub', 'cccccccc-0000-0000-0000-000000000003', true);

select is(
    (select count(*)::int from public.sync_rows),
    0,
    'non-member sees zero sync rows'
);

select throws_ok(
    $$ insert into public.sync_rows
           (household_id, entity_type, row_id, version, updated_at, key_version, nonce, ciphertext)
       values (
           '11111111-1111-1111-1111-111111111111',
           'item', 'row-2', 1, now(), 1, '\x00', '\x01'
       ) $$,
    '42501',
    null,
    'non-member cannot insert a sync row'
);

select set_config(
    'request.jwt.claims',
    '{"sub":"aaaaaaaa-0000-0000-0000-000000000001","role":"authenticated"}',
    true
);
select set_config('request.jwt.claim.sub', 'aaaaaaaa-0000-0000-0000-000000000001', true);

select lives_ok(
    $$ update public.sync_rows set version = 2
       where household_id = '11111111-1111-1111-1111-111111111111'
         and entity_type = 'item' and row_id = 'row-1' $$,
    'a newer version is accepted'
);

select ok(
    (select server_seq from public.sync_rows
     where household_id = '11111111-1111-1111-1111-111111111111'
       and entity_type = 'item' and row_id = 'row-1') > (select server_seq from seq_probe),
    'server_seq increases on every write'
);

select throws_ok(
    $$ update public.sync_rows set version = 2
       where household_id = '11111111-1111-1111-1111-111111111111'
         and entity_type = 'item' and row_id = 'row-1' $$,
    '23514',
    'stale_version',
    'stale versions are rejected'
);

select set_config(
    'request.jwt.claims',
    '{"sub":"bbbbbbbb-0000-0000-0000-000000000002","role":"authenticated"}',
    true
);
select set_config('request.jwt.claim.sub', 'bbbbbbbb-0000-0000-0000-000000000002', true);

select lives_ok(
    $$ update public.sync_rows set version = 3
       where household_id = '11111111-1111-1111-1111-111111111111'
         and entity_type = 'item' and row_id = 'row-1' $$,
    'any active member can write a newer version'
);

select set_config(
    'request.jwt.claims',
    '{"sub":"aaaaaaaa-0000-0000-0000-000000000001","role":"authenticated"}',
    true
);
select set_config('request.jwt.claim.sub', 'aaaaaaaa-0000-0000-0000-000000000001', true);

select throws_ok(
    $$ update public.sync_rows set version = 3
       where household_id = '11111111-1111-1111-1111-111111111111'
         and entity_type = 'item' and row_id = 'row-1' $$,
    '23514',
    'stale_version',
    'a write at the stored version is still rejected'
);

-- Devices and prefs ------------------------------------------------------
select lives_ok(
    $$ insert into public.devices (id, user_id, household_id, platform)
       values (
           'dddddddd-0000-0000-0000-000000000001',
           'aaaaaaaa-0000-0000-0000-000000000001',
           '11111111-1111-1111-1111-111111111111',
           'android'
       ) $$,
    'member registers a device'
);

select is(
    (select count(*)::int from public.devices),
    1,
    'member reads household devices'
);

select lives_ok(
    $$ insert into public.device_profile_prefs (device_id, profile_ref, mode)
       values ('dddddddd-0000-0000-0000-000000000001', 'profile-ref-1', 'ALARM') $$,
    'member sets a profile notification mode'
);

select is(
    (select count(*)::int from public.device_profile_prefs),
    1,
    'member reads device profile prefs'
);

select lives_ok(
    $$ insert into public.escalation_slots
           (id, household_id, occurrence_ref, profile_ref, owner_device_id, due_at)
       values (
           'eeeeeeee-0000-0000-0000-000000000001',
           '11111111-1111-1111-1111-111111111111',
           'occurrence-1', 'profile-ref-1',
           'dddddddd-0000-0000-0000-000000000001',
           now()
       ) $$,
    'member writes an escalation slot'
);

select is(
    (select count(*)::int from public.escalation_slots),
    1,
    'member reads escalation slots'
);

-- Storage policy ---------------------------------------------------------
select lives_ok(
    $$ insert into storage.objects (bucket_id, name)
       values ('photos', '11111111-1111-1111-1111-111111111111/photo.bin') $$,
    'member writes an encrypted photo object'
);

select set_config(
    'request.jwt.claims',
    '{"sub":"bbbbbbbb-0000-0000-0000-000000000002","role":"authenticated"}',
    true
);
select set_config('request.jwt.claim.sub', 'bbbbbbbb-0000-0000-0000-000000000002', true);

select is(
    (select count(*)::int from storage.objects where bucket_id = 'photos'),
    1,
    'active member reads household photos'
);

select set_config(
    'request.jwt.claims',
    '{"sub":"cccccccc-0000-0000-0000-000000000003","role":"authenticated"}',
    true
);
select set_config('request.jwt.claim.sub', 'cccccccc-0000-0000-0000-000000000003', true);

select is(
    (select count(*)::int from public.devices),
    0,
    'non-member sees zero devices'
);

select is(
    (select count(*)::int from public.device_profile_prefs),
    0,
    'non-member sees zero device prefs'
);

select is(
    (select count(*)::int from public.escalation_slots),
    0,
    'non-member sees zero escalation slots'
);

select is(
    (select count(*)::int from storage.objects where bucket_id = 'photos'),
    0,
    'non-member sees zero photos'
);

select throws_ok(
    $$ insert into public.devices (user_id, household_id)
       values (
           'cccccccc-0000-0000-0000-000000000003',
           '11111111-1111-1111-1111-111111111111'
       ) $$,
    '42501',
    null,
    'non-member cannot register a device in another household'
);

-- Removed members --------------------------------------------------------
select set_config(
    'request.jwt.claims',
    '{"sub":"aaaaaaaa-0000-0000-0000-000000000001","role":"authenticated"}',
    true
);
select set_config('request.jwt.claim.sub', 'aaaaaaaa-0000-0000-0000-000000000001', true);

select lives_ok(
    $$ update public.members set removed_at = now()
       where household_id = '11111111-1111-1111-1111-111111111111'
         and user_id = 'bbbbbbbb-0000-0000-0000-000000000002' $$,
    'member removes another member'
);

select set_config(
    'request.jwt.claims',
    '{"sub":"bbbbbbbb-0000-0000-0000-000000000002","role":"authenticated"}',
    true
);
select set_config('request.jwt.claim.sub', 'bbbbbbbb-0000-0000-0000-000000000002', true);

select is(
    (select count(*)::int from public.sync_rows),
    0,
    'removed member loses access to sync rows'
);

select is(
    (select count(*)::int from public.members),
    0,
    'removed member no longer sees household membership'
);

select throws_ok(
    $$ insert into public.sync_rows
           (household_id, entity_type, row_id, version, updated_at, key_version, nonce, ciphertext)
       values (
           '11111111-1111-1111-1111-111111111111',
           'item', 'row-removed', 1, now(), 1, '\x00', '\x01'
       ) $$,
    '42501',
    null,
    'removed member cannot write sync rows'
);

-- Structural checks ------------------------------------------------------
select ok(
    (select bool_and(relrowsecurity)
     from pg_class
     where oid in (
         'public.households'::regclass,
         'public.members'::regclass,
         'public.invites'::regclass,
         'public.devices'::regclass,
         'public.device_profile_prefs'::regclass,
         'public.sync_rows'::regclass,
         'public.escalation_slots'::regclass
     )),
    'RLS is enabled on every table'
);

select has_column('public', 'sync_rows', 'server_seq', 'sync_rows has a pull cursor');

select * from finish();
rollback;

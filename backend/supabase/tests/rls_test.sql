begin;

create extension if not exists pgtap with schema extensions;

select plan(63);

-- Helpers (temp schema, run with the caller's rights so RLS applies) -------
create function pg_temp.as_user(p_uid uuid) returns text language sql as $$
    select set_config('request.jwt.claims',
        json_build_object('sub', p_uid, 'role', 'authenticated')::text, true)
        || set_config('request.jwt.claim.sub', p_uid::text, true);
$$;
create function pg_temp.rows_affected(p_sql text) returns int language plpgsql as $$
declare n int;
begin
    execute p_sql;
    get diagnostics n = row_count;
    return n;
end;
$$;

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


-- Review fixes S1-S8 -----------------------------------------------------
-- Users: A creator of household 1, B removed above, C other household, D a second active member.
reset role;
insert into auth.users (id) values ('f0f0f0f0-0000-0000-0000-000000000004');
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');

select lives_ok(
    $$ insert into public.members (household_id, user_id)
       values ('11111111-1111-1111-1111-111111111111', 'f0f0f0f0-0000-0000-0000-000000000004') $$,
    'S1 setup: an active member adds another member'
);

-- S1: removal is soft and permanent.
select is(
    pg_temp.rows_affected($$ delete from public.members
        where user_id = 'bbbbbbbb-0000-0000-0000-000000000002' $$),
    0,
    'S1: member rows cannot be deleted'
);

select throws_ok(
    $$ update public.members set removed_at = null
       where household_id = '11111111-1111-1111-1111-111111111111'
         and user_id = 'bbbbbbbb-0000-0000-0000-000000000002' $$,
    '23514',
    'removal_is_permanent',
    'S1: a removed member cannot be un-removed'
);

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select throws_ok(
    $$ insert into public.members (household_id, user_id)
       values ('11111111-1111-1111-1111-111111111111', 'bbbbbbbb-0000-0000-0000-000000000002') $$,
    '42501',
    null,
    'S1: a removed member cannot re-add themselves'
);

-- S1: a creator who removes themselves cannot re-enter or add members.
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
insert into public.households (id, created_by)
values ('33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000001');
insert into public.members (household_id, user_id)
values ('33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000001');
update public.members set removed_at = now()
where household_id = '33333333-3333-3333-3333-333333333333'
  and user_id = 'aaaaaaaa-0000-0000-0000-000000000001';

select throws_ok(
    $$ insert into public.members (household_id, user_id)
       values ('33333333-3333-3333-3333-333333333333', 'f0f0f0f0-0000-0000-0000-000000000004') $$,
    '42501',
    null,
    'S1: a removed creator cannot add members'
);

select is(
    (select count(*)::int from public.households
     where id = '33333333-3333-3333-3333-333333333333'),
    0,
    'S1: a removed creator no longer sees the household'
);

-- S2: only the creator can delete a household.
select pg_temp.as_user('f0f0f0f0-0000-0000-0000-000000000004');
select is(
    pg_temp.rows_affected($$ delete from public.households
        where id = '11111111-1111-1111-1111-111111111111' $$),
    0,
    'S2: a non-creator member cannot delete the household'
);

select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
insert into public.households (id, created_by)
values ('44444444-aaaa-4444-8444-444444444444', 'aaaaaaaa-0000-0000-0000-000000000001');
insert into public.members (household_id, user_id)
values ('44444444-aaaa-4444-8444-444444444444', 'aaaaaaaa-0000-0000-0000-000000000001');
select is(
    pg_temp.rows_affected($$ delete from public.households
        where id = '44444444-aaaa-4444-8444-444444444444' $$),
    1,
    'S2: the creator can delete their household'
);

-- S3: devices belong to their owner.
select pg_temp.as_user('f0f0f0f0-0000-0000-0000-000000000004');
select is(
    pg_temp.rows_affected($$ update public.devices set fcm_token = 'hijacked'
        where id = 'dddddddd-0000-0000-0000-000000000001' $$),
    0,
    'S3: another member cannot change a device''s FCM token'
);
select is(
    pg_temp.rows_affected($$ delete from public.devices
        where id = 'dddddddd-0000-0000-0000-000000000001' $$),
    0,
    'S3: another member cannot delete a device'
);

select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select lives_ok(
    $$ update public.devices set fcm_token = 'mine'
       where id = 'dddddddd-0000-0000-0000-000000000001' $$,
    'S3: the owner can update their own device'
);
select throws_ok(
    $$ update public.devices set user_id = 'f0f0f0f0-0000-0000-0000-000000000004'
       where id = 'dddddddd-0000-0000-0000-000000000001' $$,
    '23514',
    'device_identity_immutable',
    'S3: a device cannot be handed to another user'
);

-- S4: notification modes are written only by the device owner.
select pg_temp.as_user('f0f0f0f0-0000-0000-0000-000000000004');
select is(
    pg_temp.rows_affected($$ update public.device_profile_prefs set mode = 'OFF'
        where device_id = 'dddddddd-0000-0000-0000-000000000001' $$),
    0,
    'S4: another member cannot change a device''s mode'
);
select throws_ok(
    $$ insert into public.device_profile_prefs (device_id, profile_ref, mode)
       values ('dddddddd-0000-0000-0000-000000000001', 'profile-ref-2', 'OFF') $$,
    '42501',
    null,
    'S4: another member cannot add a mode for someone else''s device'
);

-- S5: member identity is immutable.
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select throws_ok(
    $$ update public.members set user_id = 'cccccccc-0000-0000-0000-000000000003'
       where household_id = '11111111-1111-1111-1111-111111111111'
         and user_id = 'f0f0f0f0-0000-0000-0000-000000000004' $$,
    '23514',
    'member_identity_immutable',
    'S5: a member row cannot be re-pointed at another user'
);

-- S6: a write cannot be attributed to someone else's device.
select pg_temp.as_user('f0f0f0f0-0000-0000-0000-000000000004');
select throws_ok(
    $$ insert into public.sync_rows
           (household_id, entity_type, row_id, version, updated_at, key_version, nonce, ciphertext,
            updated_by_device)
       values ('11111111-1111-1111-1111-111111111111', 'item', 'row-spoof', 1, now(), 1, '\x00', '\x01',
               'dddddddd-0000-0000-0000-000000000001') $$,
    '23514',
    'device_not_owned',
    'S6: updated_by_device must be the caller''s own device'
);

-- S7: only the creator of an invite can change it.
select is(
    pg_temp.rows_affected($$ update public.invites set sealed_key = '\x00'
        where id = '44444444-4444-4444-4444-444444444444' $$),
    0,
    'S7: another member cannot modify an invite'
);

-- S8: function and table exposure.
select ok(
    not has_function_privilege('authenticated', 'public.sync_rows_before_write()', 'execute'),
    'S8: clients cannot call trigger functions'
);
select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select is(
    public.device_household('dddddddd-0000-0000-0000-000000000001'),
    null,
    'S8: device_household does not reveal households to non-members'
);
select ok(
    not has_table_privilege('anon', 'public.sync_rows', 'select'),
    'S8: the anon role has no table access'
);

-- Removed member is locked out of every table, not just sync_rows.
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(
    (select count(*)::int from public.escalation_slots),
    0,
    'removed member sees zero escalation slots'
);
select is(
    (select count(*)::int from storage.objects where bucket_id = 'photos'),
    0,
    'removed member sees zero photos'
);
select is(
    (select count(*)::int from public.devices),
    0,
    'removed member sees zero devices'
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

-- Phase 2 backend schema (ROADMAP_P2_P4 §1.2).
-- All health data in sync_rows and Storage is ciphertext. The server sees only opaque refs,
-- household membership, FCM tokens and escalation timing metadata.

create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------- households
create table public.households (
    id uuid primary key default gen_random_uuid(),
    created_at timestamptz not null default now(),
    created_by uuid not null references auth.users (id) on delete cascade
);

-- ---------------------------------------------------------------- members
create table public.members (
    household_id uuid not null references public.households (id) on delete cascade,
    user_id uuid not null references auth.users (id) on delete cascade,
    display_name_cipher bytea,
    joined_at timestamptz not null default now(),
    removed_at timestamptz,
    primary key (household_id, user_id)
);
create index members_user_idx on public.members (user_id);

-- ---------------------------------------------------------------- invites
create table public.invites (
    id uuid primary key default gen_random_uuid(),
    household_id uuid not null references public.households (id) on delete cascade,
    code_hash text not null check (length(code_hash) >= 32),
    inviter_pubkey bytea,
    invitee_pubkey bytea,
    sealed_key bytea,
    expires_at timestamptz not null,
    used_at timestamptz,
    created_by uuid not null references auth.users (id) on delete cascade
);
create index invites_household_idx on public.invites (household_id);

-- ---------------------------------------------------------------- devices
create table public.devices (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    household_id uuid not null references public.households (id) on delete cascade,
    fcm_token text,
    platform text,
    app_version text,
    last_heartbeat_at timestamptz,
    created_at timestamptz not null default now()
);
create index devices_household_idx on public.devices (household_id);
create index devices_user_idx on public.devices (user_id);

-- ---------------------------------------------------------------- device_profile_prefs
create table public.device_profile_prefs (
    device_id uuid not null references public.devices (id) on delete cascade,
    profile_ref text not null,
    mode text not null check (mode in ('ALARM', 'NOTIFICATION', 'MISSED_ONLY', 'OFF')),
    primary key (device_id, profile_ref)
);

-- ---------------------------------------------------------------- sync_rows
-- PK is the row identity. server_seq is the pull cursor and is reassigned on every write by the
-- trigger below, so clients cannot forge it.
create table public.sync_rows (
    household_id uuid not null references public.households (id) on delete cascade,
    entity_type text not null,
    row_id text not null,
    version bigint not null check (version >= 1),
    updated_at timestamptz not null,
    updated_by_device uuid references public.devices (id) on delete set null,
    deleted boolean not null default false,
    key_version integer not null check (key_version >= 1),
    nonce bytea not null,
    ciphertext bytea not null,
    server_seq bigserial not null,
    primary key (household_id, entity_type, row_id)
);
create unique index sync_rows_server_seq_idx on public.sync_rows (server_seq);
create index sync_rows_pull_idx on public.sync_rows (household_id, server_seq);

-- ---------------------------------------------------------------- escalation_slots
create table public.escalation_slots (
    id uuid primary key default gen_random_uuid(),
    household_id uuid not null references public.households (id) on delete cascade,
    occurrence_ref text not null,
    profile_ref text not null,
    owner_device_id uuid not null references public.devices (id) on delete cascade,
    due_at timestamptz not null,
    grace_minutes integer not null default 60 check (grace_minutes >= 0),
    critical boolean not null default false,
    done_at timestamptz,
    escalated_at timestamptz
);
create index escalation_slots_due_idx on public.escalation_slots (due_at)
    where done_at is null and escalated_at is null;
create index escalation_slots_household_idx on public.escalation_slots (household_id);

-- ---------------------------------------------------------------- helpers
-- SECURITY DEFINER avoids recursive RLS when policies on members need to check membership.
create or replace function public.is_active_member(p_household_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.members m
        where m.household_id = p_household_id
          and m.user_id = auth.uid()
          and m.removed_at is null
    );
$$;

create or replace function public.is_household_creator(p_household_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.households h
        where h.id = p_household_id
          and h.created_by = auth.uid()
    );
$$;

create or replace function public.device_household(p_device_id uuid)
returns uuid
language sql
stable
security definer
set search_path = public
as $$
    -- Only reveals the household to its own active members (S8).
    select d.household_id
    from public.devices d
    where d.id = p_device_id
      and public.is_active_member(d.household_id);
$$;

create or replace function public.household_has_members(p_household_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (select 1 from public.members m where m.household_id = p_household_id);
$$;

create or replace function public.device_owned_by_caller(p_device_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.devices d
        where d.id = p_device_id
          and d.user_id = auth.uid()
          and public.is_active_member(d.household_id)
    );
$$;

revoke all on function public.is_active_member(uuid) from public;
revoke all on function public.is_household_creator(uuid) from public;
revoke all on function public.device_household(uuid) from public;
grant execute on function public.is_active_member(uuid) to authenticated;
grant execute on function public.is_household_creator(uuid) to authenticated;
grant execute on function public.device_household(uuid) to authenticated;

-- ---------------------------------------------------------------- triggers
create or replace function public.sync_rows_before_write()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
    if tg_op = 'UPDATE' then
        if new.household_id <> old.household_id
            or new.entity_type <> old.entity_type
            or new.row_id <> old.row_id then
            raise exception 'row_identity_immutable' using errcode = '23514';
        end if;
        if new.version <= old.version then
            raise exception 'stale_version' using errcode = '23514';
        end if;
    end if;
    -- S6: a client may only attribute a write to one of its own devices in this household.
    -- Service-role writes have no auth.uid() and are not checked.
    if new.updated_by_device is not null and auth.uid() is not null then
        if not exists (
            select 1 from public.devices d
            where d.id = new.updated_by_device
              and d.user_id = auth.uid()
              and d.household_id = new.household_id
        ) then
            raise exception 'device_not_owned' using errcode = '23514';
        end if;
    end if;
    -- Every write gets a fresh cursor value, whether insert or update.
    new.server_seq := nextval(pg_get_serial_sequence('public.sync_rows', 'server_seq'));
    return new;
end;
$$;

create trigger sync_rows_before_write
before insert or update on public.sync_rows
for each row execute function public.sync_rows_before_write();

create or replace function public.invites_before_write()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
    if tg_op = 'INSERT' then
        if new.expires_at <= now() then
            raise exception 'invite_expired' using errcode = '23514';
        end if;
    elsif tg_op = 'UPDATE' then
        if old.used_at is not null then
            raise exception 'invite_already_used' using errcode = '23514';
        end if;
        if old.expires_at <= now() then
            raise exception 'invite_expired' using errcode = '23514';
        end if;
    end if;
    return new;
end;
$$;

create trigger invites_before_write
before insert or update on public.invites
for each row execute function public.invites_before_write();

create or replace function public.members_before_update()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
    if new.household_id <> old.household_id or new.user_id <> old.user_id then
        raise exception 'member_identity_immutable' using errcode = '23514';
    end if;
    -- Removal is permanent (S1): a removed member can only rejoin through a new pairing flow.
    if old.removed_at is not null and new.removed_at is distinct from old.removed_at then
        raise exception 'removal_is_permanent' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger members_before_update
before update on public.members
for each row execute function public.members_before_update();

create or replace function public.devices_before_update()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
    if new.user_id <> old.user_id or new.household_id <> old.household_id then
        raise exception 'device_identity_immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger devices_before_update
before update on public.devices
for each row execute function public.devices_before_update();

create or replace function public.households_before_update()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
    if new.created_by <> old.created_by then
        raise exception 'household_creator_immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger households_before_update
before update on public.households
for each row execute function public.households_before_update();

-- ---------------------------------------------------------------- RLS
alter table public.households enable row level security;
alter table public.members enable row level security;
alter table public.invites enable row level security;
alter table public.devices enable row level security;
alter table public.device_profile_prefs enable row level security;
alter table public.sync_rows enable row level security;
alter table public.escalation_slots enable row level security;

-- households: active members, plus the creator while bootstrapping the first member row.
create policy households_select on public.households for select to authenticated
    using (
        public.is_active_member(id)
        or (created_by = auth.uid() and not public.household_has_members(id))
    );
create policy households_insert on public.households for insert to authenticated
    with check (created_by = auth.uid());
create policy households_update on public.households for update to authenticated
    using (public.is_active_member(id)) with check (public.is_active_member(id));
create policy households_delete on public.households for delete to authenticated
    using (created_by = auth.uid() and public.is_active_member(id));

-- members: any active member can read and edit (PLAN §0). The creator may add only the very first
-- row, and rows are never deleted: removal is a soft, permanent removed_at.
create policy members_select on public.members for select to authenticated
    using (public.is_active_member(household_id));
create policy members_insert on public.members for insert to authenticated
    with check (
        public.is_active_member(household_id)
        or (
            public.is_household_creator(household_id)
            and not public.household_has_members(household_id)
        )
    );
-- WITH CHECK is true on purpose: it runs on the new row, which is no longer active after a
-- removal, so requiring activeness would block self-removal. The trigger above freezes identity.
create policy members_update on public.members for update to authenticated
    using (public.is_active_member(household_id))
    with check (true);

-- invites: household members only. The invitee-side handshake RPC is added with P2-B1.
create policy invites_select on public.invites for select to authenticated
    using (public.is_active_member(household_id));
create policy invites_insert on public.invites for insert to authenticated
    with check (public.is_active_member(household_id) and created_by = auth.uid());
create policy invites_update on public.invites for update to authenticated
    using (public.is_active_member(household_id) and created_by = auth.uid())
    with check (public.is_active_member(household_id) and created_by = auth.uid());
create policy invites_delete on public.invites for delete to authenticated
    using (public.is_active_member(household_id));

-- devices: active members of the device's household; the owner writes their own device row.
create policy devices_select on public.devices for select to authenticated
    using (public.is_active_member(household_id));
create policy devices_insert on public.devices for insert to authenticated
    with check (user_id = auth.uid() and public.is_active_member(household_id));
create policy devices_update on public.devices for update to authenticated
    using (user_id = auth.uid() and public.is_active_member(household_id))
    with check (user_id = auth.uid() and public.is_active_member(household_id));
create policy devices_delete on public.devices for delete to authenticated
    using (user_id = auth.uid() and public.is_active_member(household_id));

-- device_profile_prefs: resolved through the device's household.
create policy device_profile_prefs_select on public.device_profile_prefs for select to authenticated
    using (public.is_active_member(public.device_household(device_id)));
-- Writes only for your own device (S4); reads for the whole household.
create policy device_profile_prefs_insert on public.device_profile_prefs for insert to authenticated
    with check (public.device_owned_by_caller(device_id));
create policy device_profile_prefs_update on public.device_profile_prefs for update to authenticated
    using (public.device_owned_by_caller(device_id))
    with check (public.device_owned_by_caller(device_id));
create policy device_profile_prefs_delete on public.device_profile_prefs for delete to authenticated
    using (public.device_owned_by_caller(device_id));

-- sync_rows: every command is scoped to an active member of the row's household.
create policy sync_rows_select on public.sync_rows for select to authenticated
    using (public.is_active_member(household_id));
create policy sync_rows_insert on public.sync_rows for insert to authenticated
    with check (public.is_active_member(household_id));
create policy sync_rows_update on public.sync_rows for update to authenticated
    using (public.is_active_member(household_id))
    with check (public.is_active_member(household_id));
create policy sync_rows_delete on public.sync_rows for delete to authenticated
    using (public.is_active_member(household_id));

-- escalation_slots: household members can read and maintain their own slots.
create policy escalation_slots_select on public.escalation_slots for select to authenticated
    using (public.is_active_member(household_id));
create policy escalation_slots_insert on public.escalation_slots for insert to authenticated
    with check (public.is_active_member(household_id));
create policy escalation_slots_update on public.escalation_slots for update to authenticated
    using (public.is_active_member(household_id))
    with check (public.is_active_member(household_id));
create policy escalation_slots_delete on public.escalation_slots for delete to authenticated
    using (public.is_active_member(household_id));

-- ---------------------------------------------------------------- grants
grant usage on schema public to authenticated;
revoke all on all tables in schema public from anon;
revoke all on all sequences in schema public from anon;
revoke all on all functions in schema public from anon;
grant select, insert, update, delete on all tables in schema public to authenticated;
grant usage, select on all sequences in schema public to authenticated;
-- S8: clients may call only the helpers that RLS policies need; trigger functions and any
-- later server-side function stay unreachable from the API.
revoke execute on all functions in schema public from public, anon, authenticated;
grant execute on function public.is_active_member(uuid) to authenticated;
grant execute on function public.is_household_creator(uuid) to authenticated;
grant execute on function public.household_has_members(uuid) to authenticated;
grant execute on function public.device_owned_by_caller(uuid) to authenticated;
grant execute on function public.device_household(uuid) to authenticated;
alter default privileges in schema public revoke execute on functions from public, anon, authenticated;

-- ---------------------------------------------------------------- storage: encrypted photos
insert into storage.buckets (id, name, public)
values ('photos', 'photos', false)
on conflict (id) do nothing;

create policy photos_select on storage.objects for select to authenticated
    using (
        bucket_id = 'photos'
        and public.is_active_member((storage.foldername(name))[1]::uuid)
    );
create policy photos_insert on storage.objects for insert to authenticated
    with check (
        bucket_id = 'photos'
        and public.is_active_member((storage.foldername(name))[1]::uuid)
    );
create policy photos_update on storage.objects for update to authenticated
    using (
        bucket_id = 'photos'
        and public.is_active_member((storage.foldername(name))[1]::uuid)
    )
    with check (
        bucket_id = 'photos'
        and public.is_active_member((storage.foldername(name))[1]::uuid)
    );
create policy photos_delete on storage.objects for delete to authenticated
    using (
        bucket_id = 'photos'
        and public.is_active_member((storage.foldername(name))[1]::uuid)
    );

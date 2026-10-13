-- Runmoni production foundation v0.1.4. Not a migration of pilot bearer tokens.
-- Supabase Auth email OTP identities map to auth.users.id. Apply to a fresh project.
begin;
create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to authenticated;

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  nickname text not null unique check(char_length(trim(nickname)) between 2 and 24),
  adult_confirmed boolean not null default false,
  avatar jsonb not null default '{"hair":0,"top":0,"bottom":0,"shoes":0,"accessory":0}',
  created_at timestamptz not null default now()
);
create unique index profiles_nickname_casefold_idx on public.profiles(lower(nickname));
create table public.crews (
  id uuid primary key default gen_random_uuid(), owner_id uuid not null references public.profiles(id),
  name text not null check(char_length(trim(name)) between 2 and 60), description text not null default '' check(char_length(description)<=500),
  region text not null default '' check(char_length(region)<=80), pace_label text not null default '자유 페이스' check(char_length(pace_label)<=80),
  created_at timestamptz not null default now()
);
create table public.crew_members (
  crew_id uuid not null references public.crews(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  role text not null default 'member' check(role in ('owner','dj','member')),
  primary key(crew_id,user_id)
);
create index crew_members_user_idx on public.crew_members(user_id);
create table public.parties (
  id uuid primary key default gen_random_uuid(), crew_id uuid not null references public.crews(id) on delete cascade,
  creator_id uuid not null references public.profiles(id), title text not null check(char_length(trim(title)) between 2 and 80),
  kind text not null check(kind in ('local','remote')), starts_at timestamptz not null,
  meeting_point text not null default '' check(char_length(meeting_point)<=200),
  latitude double precision check(latitude between -90 and 90), longitude double precision check(longitude between -180 and 180),
  distance_km numeric not null check(distance_km>0 and distance_km<=100),
  pace_label text not null default '자유 페이스' check(char_length(pace_label) between 1 and 80),
  capacity integer not null default 20 check(capacity between 2 and 20),
  status text not null default 'scheduled' check(status in ('scheduled','cancelled')),
  check ((latitude is null)=(longitude is null)), check(kind='remote' or char_length(trim(meeting_point))>=2)
);
create index parties_crew_start_idx on public.parties(crew_id,starts_at);
create table public.party_members (
  party_id uuid not null references public.parties(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  primary key(party_id,user_id)
);
create index party_members_user_idx on public.party_members(user_id);
create table public.blocks (
  blocker_id uuid not null references public.profiles(id) on delete cascade,
  blocked_id uuid not null references public.profiles(id) on delete cascade,
  primary key(blocker_id,blocked_id), check(blocker_id<>blocked_id)
);
create index blocks_blocked_idx on public.blocks(blocked_id);
create table public.messages (
  id bigint generated always as identity primary key, scope_type text not null check(scope_type in ('crew','party')),
  scope_id uuid not null, sender_id uuid not null references public.profiles(id),
  body text not null check(char_length(trim(body)) between 1 and 2000),
  kind text not null default 'chat' check(kind in ('chat','announcement')), created_at timestamptz not null default now()
);
create index messages_scope_id_idx on public.messages(scope_type,scope_id,id);
create table public.runs (
  id uuid primary key, user_id uuid not null references public.profiles(id) on delete cascade,
  party_id uuid references public.parties(id), started_at timestamptz not null, ended_at timestamptz,
  status text not null default 'active' check(status in ('active','finalized')),
  distance_m numeric not null default 0 check(distance_m>=0), active_duration_s numeric not null default 0 check(active_duration_s>=0),
  elapsed_duration_s numeric not null default 0 check(elapsed_duration_s>=active_duration_s), reward_points integer not null default 0 check(reward_points>=0), deleted_at timestamptz
);
create index runs_user_start_idx on public.runs(user_id,started_at);
create table public.track_points (
  run_id uuid not null references public.runs(id) on delete cascade, seq integer not null check(seq>=0),
  segment integer not null default 0 check(segment>=0), latitude double precision not null check(latitude between -90 and 90),
  longitude double precision not null check(longitude between -180 and 180), accuracy_m numeric not null check(accuracy_m>=0 and accuracy_m<=10000),
  altitude_m numeric check(altitude_m between -1000 and 10000), recorded_at timestamptz not null,
  primary key(run_id,seq)
);
create table public.item_catalogue (
  id text primary key, slot text not null check(slot in ('hair','top','bottom','shoes','accessory')),
  variant integer not null check(variant between 0 and 3), cost integer not null check(cost>=0), unique(slot,variant)
);
insert into public.item_catalogue(id,slot,variant,cost)
select s||':'||v,s,v,(array[0,500,1000,2000])[v+1]
from unnest(array['hair','top','bottom','shoes','accessory']) s cross join generate_series(0,3) v;
create table public.owned_items (
  user_id uuid not null references public.profiles(id) on delete cascade, item_id text not null references public.item_catalogue(id),
  primary key(user_id,item_id)
);
create table public.mileage_ledger (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles(id) on delete cascade,
  run_id uuid unique references public.runs(id), item_id text references public.item_catalogue(id), delta integer not null,
  created_at timestamptz not null default now(), check((run_id is null)<>(item_id is null))
);
create index mileage_ledger_user_time_idx on public.mileage_ledger(user_id,created_at);
create table public.presence (
  user_id uuid primary key references public.profiles(id) on delete cascade, run_id uuid not null references public.runs(id) on delete cascade,
  scope_type text not null check(scope_type in ('crew','party')), scope_id uuid not null,
  latitude double precision not null check(latitude between -90 and 90), longitude double precision not null check(longitude between -180 and 180),
  exact_opt_in boolean not null check(exact_opt_in), updated_at timestamptz not null default now(),
  expires_at timestamptz not null default (now()+interval '45 seconds')
);
create index presence_scope_ttl_idx on public.presence(scope_type,scope_id,expires_at);
create table public.music_leases (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles(id),
  scope_type text not null check(scope_type in ('crew','party')), scope_id uuid not null,
  session_id uuid not null, expires_at timestamptz not null, revoked boolean not null default false
);
create index music_leases_room_idx on public.music_leases(scope_type,scope_id,expires_at);

-- Definer helpers avoid recursive membership RLS. No caller-supplied user argument.
create function private.is_adult() returns boolean language sql stable security definer set search_path='' as $$
  select exists(select 1 from public.profiles where id=(select auth.uid()) and adult_confirmed);
$$;
create function private.is_crew_member(cid uuid) returns boolean language sql stable security definer set search_path='' as $$
  select exists(select 1 from public.crew_members where crew_id=cid and user_id=(select auth.uid()));
$$;
create function private.is_crew_owner(cid uuid) returns boolean language sql stable security definer set search_path='' as $$
  select exists(select 1 from public.crews where id=cid and owner_id=(select auth.uid()));
$$;
create function private.is_party_member(pid uuid) returns boolean language sql stable security definer set search_path='' as $$
  select exists(select 1 from public.party_members where party_id=pid and user_id=(select auth.uid()));
$$;
create function private.is_blocked(other_user uuid) returns boolean language sql stable security definer set search_path='' as $$
  select exists(select 1 from public.blocks where blocker_id=(select auth.uid()) and blocked_id=other_user or blocker_id=other_user and blocked_id=(select auth.uid()));
$$;
create function private.scope_access(st text,sid uuid) returns boolean language sql stable security definer set search_path='' as $$
  select private.is_adult() and case st when 'crew' then private.is_crew_member(sid) when 'party' then private.is_party_member(sid) else false end;
$$;
create function private.can_announce(st text,sid uuid) returns boolean language sql stable security definer set search_path='' as $$
  select case st when 'crew' then private.is_crew_owner(sid) when 'party' then exists(select 1 from public.parties p where p.id=sid and (p.creator_id=(select auth.uid()) or private.is_crew_owner(p.crew_id))) else false end;
$$;
create function private.can_equip(style jsonb) returns boolean language sql stable security definer set search_path='' as $$
  select case when jsonb_typeof(style) is distinct from 'object' then false else style ?& array['hair','top','bottom','shoes','accessory'] and
  not exists(select 1 from jsonb_each(style) kv where kv.key not in ('hair','top','bottom','shoes','accessory') or
    jsonb_typeof(kv.value)<>'number' or kv.value::text not in ('0','1','2','3') or
    (kv.value::text<>'0' and not exists(select 1 from public.owned_items o where o.user_id=(select auth.uid()) and o.item_id=kv.key||':'||kv.value::text))) end;
$$;
create function private.can_read_profile(other_user uuid) returns boolean language sql stable security definer set search_path='' as $$
  select other_user=(select auth.uid()) or (private.is_adult() and not private.is_blocked(other_user) and exists(
    select 1 from public.crew_members own join public.crew_members peer using(crew_id) where own.user_id=(select auth.uid()) and peer.user_id=other_user));
$$;
revoke all on all functions in schema private from public;
grant execute on all functions in schema private to authenticated;

-- Every exposed table is opt-in. Server writes are reserved for service_role.
alter table public.profiles enable row level security;
alter table public.crews enable row level security;
alter table public.crew_members enable row level security;
alter table public.parties enable row level security;
alter table public.party_members enable row level security;
alter table public.blocks enable row level security;
alter table public.messages enable row level security;
alter table public.runs enable row level security;
alter table public.track_points enable row level security;
alter table public.item_catalogue enable row level security;
alter table public.owned_items enable row level security;
alter table public.mileage_ledger enable row level security;
alter table public.presence enable row level security;
alter table public.music_leases enable row level security;
revoke all on public.profiles,public.crews,public.crew_members,public.parties,public.party_members,public.blocks,public.messages,public.runs,public.track_points,public.item_catalogue,public.owned_items,public.mileage_ledger,public.presence,public.music_leases from anon,authenticated;
grant select on public.profiles,public.crews,public.crew_members,public.parties,public.party_members,public.blocks,public.messages,public.runs,public.track_points,public.item_catalogue,public.owned_items,public.mileage_ledger,public.presence to authenticated;
grant insert(id,nickname,adult_confirmed,avatar) on public.profiles to authenticated;
grant update(nickname,avatar) on public.profiles to authenticated;
grant insert,delete on public.blocks to authenticated;
grant insert(scope_type,scope_id,sender_id,body,kind) on public.messages to authenticated;
grant usage,select on sequence public.messages_id_seq to authenticated;
grant all on public.profiles,public.crews,public.crew_members,public.parties,public.party_members,public.blocks,public.messages,public.runs,public.track_points,public.item_catalogue,public.owned_items,public.mileage_ledger,public.presence,public.music_leases to service_role;
grant usage,select on sequence public.messages_id_seq to service_role;

create policy profiles_read on public.profiles for select to authenticated using(private.can_read_profile(id));
create policy profiles_create on public.profiles for insert to authenticated with check(id=(select auth.uid()) and adult_confirmed and private.can_equip(avatar));
create policy profiles_edit on public.profiles for update to authenticated using(id=(select auth.uid())) with check(id=(select auth.uid()) and private.can_equip(avatar));
create policy crews_discover on public.crews for select to authenticated using((select private.is_adult()));
create policy crew_members_read on public.crew_members for select to authenticated using(private.is_crew_member(crew_id));
create policy parties_read on public.parties for select to authenticated using(private.is_crew_member(crew_id));
create policy party_members_read on public.party_members for select to authenticated using(private.is_party_member(party_id));
create policy blocks_read on public.blocks for select to authenticated using(blocker_id=(select auth.uid()));
create policy blocks_create on public.blocks for insert to authenticated with check(blocker_id=(select auth.uid()) and (select private.is_adult()));
create policy blocks_delete on public.blocks for delete to authenticated using(blocker_id=(select auth.uid()));
create policy messages_read on public.messages for select to authenticated using(private.scope_access(scope_type,scope_id) and not private.is_blocked(sender_id));
create policy messages_create on public.messages for insert to authenticated with check(sender_id=(select auth.uid()) and private.scope_access(scope_type,scope_id) and (kind='chat' or private.can_announce(scope_type,scope_id)) and (scope_type<>'party' or exists(select 1 from public.parties where id=scope_id and status='scheduled')));
create policy runs_own on public.runs for select to authenticated using(user_id=(select auth.uid()) and deleted_at is null);
create policy tracks_own on public.track_points for select to authenticated using(exists(select 1 from public.runs where id=run_id and user_id=(select auth.uid()) and deleted_at is null));
create policy catalogue_read on public.item_catalogue for select to authenticated using((select private.is_adult()));
create policy items_own on public.owned_items for select to authenticated using(user_id=(select auth.uid()));
create policy ledger_own on public.mileage_ledger for select to authenticated using(user_id=(select auth.uid()));
create policy presence_read on public.presence for select to authenticated using(exact_opt_in and expires_at>now() and private.scope_access(scope_type,scope_id) and not private.is_blocked(user_id) and exists(select 1 from public.runs r where r.id=run_id and r.status='active' and r.deleted_at is null));
-- runs RLS allows only own rows: scope read needs a definer helper for active state.
create function private.run_is_active(rid uuid) returns boolean language sql stable security definer set search_path='' as $$
  select exists(select 1 from public.runs where id=rid and status='active' and deleted_at is null);
$$;
revoke all on function private.run_is_active(uuid) from public;
grant execute on function private.run_is_active(uuid) to authenticated;
drop policy presence_read on public.presence;
create policy presence_read on public.presence for select to authenticated using(exact_opt_in and expires_at>now() and private.scope_access(scope_type,scope_id) and not private.is_blocked(user_id) and private.run_is_active(run_id));

-- All mutating membership paths serialize on the same row.
create function public.create_crew(p_name text,p_description text default '',p_region text default '',p_pace_label text default '자유 페이스') returns uuid
language plpgsql security definer set search_path='' as $$
declare cid uuid;
begin
  if not private.is_adult() then raise insufficient_privilege; end if;
  insert into public.crews(owner_id,name,description,region,pace_label) values(auth.uid(),p_name,p_description,p_region,p_pace_label) returning id into cid;
  insert into public.crew_members values(cid,auth.uid(),'owner'); return cid;
end;
$$;
create function public.join_crew(cid uuid) returns void language plpgsql security definer set search_path='' as $$
begin
  if not private.is_adult() then raise insufficient_privilege; end if;
  if not exists(select 1 from public.crews where id=cid) then raise exception 'crew_not_found'; end if;
  insert into public.crew_members values(cid,auth.uid(),'member') on conflict do nothing;
end;
$$;
create function public.grant_crew_role(cid uuid,target_user uuid,new_role text) returns void language plpgsql security definer set search_path='' as $$
begin
  if not private.is_adult() or not private.is_crew_owner(cid) or new_role is null or new_role not in ('dj','member') then raise insufficient_privilege; end if;
  update public.crew_members set role=new_role where crew_id=cid and user_id=target_user and role<>'owner';
  if not found then raise exception 'member_not_found_or_owner_locked'; end if;
  if new_role='member' then
    update public.music_leases set revoked=true where user_id=target_user and
      ((scope_type='crew' and scope_id=cid) or (scope_type='party' and scope_id in (select id from public.parties where crew_id=cid)));
  end if;
end;
$$;
create function public.leave_crew(cid uuid) returns void language plpgsql security definer set search_path='' as $$
begin
  if not private.is_adult() or not private.is_crew_member(cid) then raise insufficient_privilege; end if;
  if private.is_crew_owner(cid) then raise exception 'owner_cannot_leave'; end if;
  -- Keep the same locking order as join/leave/cancel_party before removing members.
  perform 1 from public.parties where crew_id=cid order by id for update;
  delete from public.party_members where user_id=auth.uid() and party_id in (select id from public.parties where crew_id=cid);
  delete from public.crew_members where crew_id=cid and user_id=auth.uid();
  delete from public.presence where user_id=auth.uid() and
    ((scope_type='crew' and scope_id=cid) or (scope_type='party' and scope_id in (select id from public.parties where crew_id=cid)));
  update public.music_leases set revoked=true where user_id=auth.uid() and
    ((scope_type='crew' and scope_id=cid) or (scope_type='party' and scope_id in (select id from public.parties where crew_id=cid)));
end;
$$;
create function public.create_party(cid uuid,p_title text,p_kind text,p_starts_at timestamptz,p_meeting_point text,p_distance_km numeric,p_pace_label text,p_capacity integer,p_latitude double precision default null,p_longitude double precision default null) returns uuid
language plpgsql security definer set search_path='' as $$
declare pid uuid;
begin
  if not private.is_adult() or not private.is_crew_owner(cid) then raise insufficient_privilege; end if;
  if p_starts_at<now() or p_starts_at>now()+interval '366 days' then raise exception 'invalid_party_time'; end if;
  insert into public.parties(crew_id,creator_id,title,kind,starts_at,meeting_point,distance_km,pace_label,capacity,latitude,longitude) values(cid,auth.uid(),p_title,p_kind,p_starts_at,p_meeting_point,p_distance_km,p_pace_label,p_capacity,p_latitude,p_longitude) returning id into pid;
  insert into public.party_members values(pid,auth.uid()); return pid;
end;
$$;
create function public.join_party(pid uuid) returns void language plpgsql security definer set search_path='' as $$
declare p public.parties;
begin
  if not private.is_adult() then raise insufficient_privilege; end if;
  select * into p from public.parties where id=pid for update;
  if not found then raise exception 'party_not_found'; end if;
  if not private.is_adult() or not private.is_crew_member(p.crew_id) then raise insufficient_privilege; end if;
  if p.status='cancelled' then raise exception 'party_cancelled'; end if;
  if private.is_party_member(pid) then return; end if;
  if (select count(*) from public.party_members where party_id=pid)>=p.capacity then raise exception 'party_full'; end if;
  insert into public.party_members values(pid,auth.uid());
end;
$$;
create function public.leave_party(pid uuid) returns void language plpgsql security definer set search_path='' as $$
declare p public.parties;
begin
  if not private.is_adult() then raise insufficient_privilege; end if;
  select * into p from public.parties where id=pid for update;
  if not found then raise exception 'party_not_found'; end if;
  if p.creator_id=auth.uid() then raise exception 'creator_cannot_leave'; end if;
  delete from public.party_members where party_id=pid and user_id=auth.uid();
  delete from public.presence where scope_type='party' and scope_id=pid and user_id=auth.uid();
  update public.music_leases set revoked=true where scope_type='party' and scope_id=pid and user_id=auth.uid();
end;
$$;
create function public.cancel_party(pid uuid) returns void language plpgsql security definer set search_path='' as $$
declare p public.parties;
begin
  if not private.is_adult() then raise insufficient_privilege; end if;
  select * into p from public.parties where id=pid for update;
  if not found then raise exception 'party_not_found'; end if;
  if p.creator_id<>auth.uid() and not private.is_crew_owner(p.crew_id) then raise insufficient_privilege; end if;
  update public.parties set status='cancelled' where id=pid;
  delete from public.presence where scope_type='party' and scope_id=pid;
  update public.music_leases set revoked=true where scope_type='party' and scope_id=pid;
end;
$$;
create function public.purchase_item(sku text) returns boolean language plpgsql security definer set search_path='' as $$
declare price integer; balance integer;
begin
  if not private.is_adult() then raise insufficient_privilege; end if;
  perform 1 from public.profiles where id=auth.uid() for update;
  select cost into price from public.item_catalogue where id=sku;
  if not found then raise exception 'item_not_found'; end if;
  if price=0 or exists(select 1 from public.owned_items where user_id=auth.uid() and item_id=sku) then return false; end if;
  select coalesce(sum(delta),0) into balance from public.mileage_ledger where user_id=auth.uid();
  if balance<price then raise exception 'insufficient_mileage'; end if;
  insert into public.owned_items values(auth.uid(),sku);
  insert into public.mileage_ledger(user_id,item_id,delta) values(auth.uid(),sku,-price); return true;
end;
$$;
create function public.share_presence(rid uuid,st text,sid uuid,lat double precision,lon double precision,opt_in boolean) returns void
language plpgsql security definer set search_path='' as $$
begin
  if not private.is_adult() then raise insufficient_privilege; end if;
  if opt_in is not true then delete from public.presence where user_id=auth.uid(); return; end if;
  if not private.scope_access(st,sid) or not exists(select 1 from public.runs where id=rid and user_id=auth.uid() and status='active' and deleted_at is null) then raise insufficient_privilege; end if;
  if st='party' and exists(select 1 from public.parties where id=sid and status='cancelled') then raise exception 'party_cancelled'; end if;
  insert into public.presence(user_id,run_id,scope_type,scope_id,latitude,longitude,exact_opt_in,updated_at,expires_at)
    values(auth.uid(),rid,st,sid,lat,lon,true,now(),now()+interval '45 seconds')
  on conflict(user_id) do update set run_id=excluded.run_id,scope_type=excluded.scope_type,scope_id=excluded.scope_id,latitude=excluded.latitude,longitude=excluded.longitude,exact_opt_in=true,updated_at=now(),expires_at=now()+interval '45 seconds';
end;
$$;

-- Deliberately no authenticated write grant to tracks/runs/ledger/leases.
-- A future verified server adapter validates batch sequence/distance and finalizes
-- while locking both runs and profiles. UNIQUE(run_id) in the ledger is the
-- final exactly-once guard. Apply/test this migration before wiring that adapter.
revoke all on function public.create_crew(text,text,text,text), public.join_crew(uuid), public.leave_crew(uuid), public.grant_crew_role(uuid,uuid,text), public.create_party(uuid,text,text,timestamptz,text,numeric,text,integer,double precision,double precision), public.join_party(uuid), public.leave_party(uuid), public.cancel_party(uuid), public.purchase_item(text), public.share_presence(uuid,text,uuid,double precision,double precision,boolean) from public,anon;
grant execute on function public.create_crew(text,text,text,text), public.join_crew(uuid), public.leave_crew(uuid), public.grant_crew_role(uuid,uuid,text), public.create_party(uuid,text,text,timestamptz,text,numeric,text,integer,double precision,double precision), public.join_party(uuid), public.leave_party(uuid), public.cancel_party(uuid), public.purchase_item(text), public.share_presence(uuid,text,uuid,double precision,double precision,boolean) to authenticated;
commit;

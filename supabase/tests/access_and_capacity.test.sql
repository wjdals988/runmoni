begin;
select plan(23);
select has_table('public','profiles','Profiles exist');
select has_table('public','track_points','Private tracks exist');
select ok((select bool_and(relrowsecurity) from pg_class join pg_namespace ns on ns.oid=relnamespace where ns.nspname='public' and relname in ('profiles','crews','crew_members','parties','party_members','blocks','messages','runs','track_points','item_catalogue','owned_items','mileage_ledger','presence','music_leases')), 'All app tables enable RLS');
select ok(not has_table_privilege('anon','public.presence','select'),'Anonymous exact location read denied');
select ok(not has_table_privilege('authenticated','public.mileage_ledger','insert'),'Clients cannot mint mileage');
select ok(not has_table_privilege('authenticated','public.crew_members','update'),'Clients cannot self-grant DJ');
select ok(not has_table_privilege('authenticated','public.party_members','insert'),'Membership uses atomic RPC');
select ok(not has_table_privilege('authenticated','public.track_points','insert'),'Tracks require verified server adapter');
select ok(not has_column_privilege('authenticated','public.profiles','adult_confirmed','update'),'Adult confirmation cannot be changed by profile edit');

insert into auth.users(id,email) values
 ('00000000-0000-4000-8000-000000000001','one@example.invalid'),
 ('00000000-0000-4000-8000-000000000002','two@example.invalid'),
 ('00000000-0000-4000-8000-000000000003','three@example.invalid');
insert into public.profiles(id,nickname,adult_confirmed) values
 ('00000000-0000-4000-8000-000000000001','리더',true),
 ('00000000-0000-4000-8000-000000000002','멤버',true),
 ('00000000-0000-4000-8000-000000000003','외부',true);
insert into public.crews(id,owner_id,name) values('00000000-0000-4000-8000-000000000010','00000000-0000-4000-8000-000000000001','테스트 크루');
insert into public.crew_members values
 ('00000000-0000-4000-8000-000000000010','00000000-0000-4000-8000-000000000001','owner'),
 ('00000000-0000-4000-8000-000000000010','00000000-0000-4000-8000-000000000002','member');
insert into public.parties(id,crew_id,creator_id,title,kind,starts_at,meeting_point,distance_km,capacity) values('00000000-0000-4000-8000-000000000020','00000000-0000-4000-8000-000000000010','00000000-0000-4000-8000-000000000001','5K 테스트','local',now()+interval '1 hour','공원',5,2);
insert into public.party_members values('00000000-0000-4000-8000-000000000020','00000000-0000-4000-8000-000000000001');
insert into public.runs(id,user_id,started_at) values('00000000-0000-4000-8000-000000000030','00000000-0000-4000-8000-000000000001',now());
insert into public.track_points(run_id,seq,latitude,longitude,accuracy_m,recorded_at) values('00000000-0000-4000-8000-000000000030',0,37,127,5,now());
insert into public.presence(user_id,run_id,scope_type,scope_id,latitude,longitude,exact_opt_in) values('00000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000030','crew','00000000-0000-4000-8000-000000000010',37,127,true);

set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-4000-8000-000000000003',true);
select is((select count(*)::int from public.profiles),1,'Outsider only sees own profile');
select is((select count(*)::int from public.crew_members),0,'Outsider cannot read membership');
select is((select count(*)::int from public.parties),0,'Outsider cannot read crew parties');
select is((select count(*)::int from public.presence),0,'Outsider cannot read crew exact locations');
select is((select count(*)::int from public.track_points),0,'Outsider cannot read another runner raw track');
select ok(not private.can_equip('[]'::jsonb),'Malformed avatar is denied without expanding non-object JSON');
select ok(not private.can_equip('{"hair":1,"top":0,"bottom":0,"shoes":0,"accessory":0}'::jsonb),'Unowned paid variant cannot be equipped');
select throws_ok($$select public.join_party('00000000-0000-4000-8000-000000000020')$$,'42501',null,'Outsider party join denied');
select set_config('request.jwt.claim.sub','00000000-0000-4000-8000-000000000002',true);
select lives_ok($$select public.join_party('00000000-0000-4000-8000-000000000020')$$,'Member takes final place');
select lives_ok($$select public.join_party('00000000-0000-4000-8000-000000000020')$$,'Joining twice is idempotent');
select set_config('request.jwt.claim.sub','00000000-0000-4000-8000-000000000003',true);
select public.join_crew('00000000-0000-4000-8000-000000000010');
select is((select count(*)::int from public.presence),1,'Crew peer sees active opt-in presence despite private run RLS');
insert into public.blocks values('00000000-0000-4000-8000-000000000003','00000000-0000-4000-8000-000000000001');
select is((select count(*)::int from public.presence),0,'Blocked exact presence is hidden');
select throws_ok($$select public.join_party('00000000-0000-4000-8000-000000000020')$$,'P0001','party_full','Full party refuses another member');
select throws_ok($$select public.grant_crew_role('00000000-0000-4000-8000-000000000010','00000000-0000-4000-8000-000000000003','dj')$$,'42501',null,'Member cannot grant DJ');
reset role;
select * from finish();
rollback;

# Runmoni pilot API v0.1.4

Local-only base URL: `http://127.0.0.1:8787`. Android emulator uses `http://10.0.2.2:8787`; USB device uses `adb reverse tcp:8787 tcp:8787`. Prefix `/v1`. JSON uses snake_case, UUID strings, UTC Unix milliseconds, distances in metres and durations in seconds. Authenticated endpoints require `Authorization: Bearer <token>`. Lists return `{ "items": [...] }`. Failures return `{ "error": { "code": "...", "message": "..." } }` and HTTP 401/403/404/409/422/503.

## Authentication and profile

Input boundaries: GPS `seq` and `segment` are integers in 0…2147483647;
message `after_id` is in 0…9223372036854775807. Larger values return 422
`invalid_request` before SQL execution. Calendar statistics return 422
`invalid_stats_time` when the requested Seoul period cannot be represented
(including an end boundary beyond year 9999). `period=all` ignores the anchor.
Music capability/lease issuance requires both bridge secrets to contain at least
32 characters, matching bridge admission. Configured capability is not proof
that a remote audio server is reachable.

- `GET /health`: `{status:"ok", version:"0.1.4", auth_mode:"invite_code_pilot", capabilities:{voice:false,music_broadcast:false}}` when audio credentials are unset.
- `POST /v1/auth/pilot`: `{invite_code, nickname, adult_confirmed:true}` → `{access_token, token_type:"bearer", expires_at_ms, user:{id,nickname,mileage,avatar}}`. Explicit adult-attested invite-only pilot, NOT email OTP or verified identity. Nicknames unique. Existing nickname cannot be used to obtain another person's token. Persist the issued token on the device; lost-token recovery is operator-assisted in this pilot.
- `DELETE /v1/auth/session`: revoke current bearer token.
- `GET /v1/me`: `{id,nickname,mileage,avatar,owned_items}`. `owned_items` is a string array of SKUs (e.g. `["hair:0","hair:1","top:0"]`); all five variant-0 SKUs are always included.
- `PATCH /v1/me`: `{nickname?, avatar?:{hair:0,top:0,bottom:0,shoes:0,accessory:0}}`; each variant is 0–3 and nonzero variants require owned SKU `slot:variant`. Duplicate nickname returns 400 `nickname_taken`.
- `GET /v1/shop/items`: cosmetic catalogue `{items:[{id,name,cost,slot,value}]}`.
- `POST /v1/shop/purchase`: `{item_id}` → `{item_id,mileage,already_owned}`. Repeated purchase does not debit again; one item ownership per user/SKU.
- `POST /v1/blocks`: `{user_id}`; `DELETE /v1/blocks/{user_id}`; block hides presence and messages in both directions. A voice token is denied if a blocked pair would share the room.

## Crews, parties and communications

- `POST /v1/crews`: `{name,description:"",region:"",pace_label:"자유 페이스"}` → crew `{id,name,description,region,pace_label,owner_id,invitation_code,member_count,role}`. Roles `owner`, `dj`, `member`; creator is owner.
- `GET /v1/crews`: joined crews only. `GET /v1/crews/discover`: discoverable crew summaries, no invite code or member locations.
- `POST /v1/crews/join`: `{invitation_code}` → crew. Idempotent membership.
- `POST /v1/crews/{crew_id}/join`: open join for invite-authenticated pilot testers. `DELETE /v1/crews/{crew_id}/join`: leave crew and all its parties; owner cannot leave in this pilot.
- `GET /v1/crews/{crew_id}` and `GET /v1/crews/{crew_id}/members`: members only. Member items contain `{user_id,role,nickname,avatar}` with a five-slot object, and hide blocked peers in either direction.
- `PATCH /v1/crews/{crew_id}/members/{user_id}`: `{role:"dj"|"member"}`; crew owner only, cannot demote owner.
- `POST /v1/parties`: `{crew_id,title,kind:"local"|"remote",starts_at_ms,meeting_point:"",latitude:null,longitude:null,distance_km,pace_label:"자유 페이스",capacity}`. Crew owner only. Local parties require a meeting point; remote parties may omit it. Coordinates are optional but must be supplied as a pair. Capacity 2–20, creator automatically joins and consumes one place.
- `GET /v1/parties`: non-cancelled parties from joined crews ordered by start time. `GET /v1/parties/{party_id}` includes those fields and `{participant_count,is_joined,status,creator_id}`.
- `POST /v1/parties/{party_id}/join`: crew member; atomically checks capacity, idempotent if joined. `DELETE /v1/parties/{party_id}/join`: leave; creator must cancel instead.
- `POST /v1/parties/{party_id}/cancel`: creator or crew owner; releases presence, denies further room entry.
- `GET|POST /v1/crews/{crew_id}/messages` and `/v1/parties/{party_id}/messages`. GET accepts `after_id=0&limit=100`, items `{id,scope_type,scope_id,sender_id,nickname,body,kind,created_at_ms}`. `after_id=0` returns the **latest** visible messages in ascending ID order; `after_id>0` returns the next page after that ID, also ascending. POST `{body,kind:"chat"|"announcement"}`. Crew members read/write crew chat; joined participants read/write party chat. Only owner/party creator posts announcements. Text max 2000 chars. No fake websocket: client polls every 3 seconds while room visible.

## Running and privacy

- `POST /v1/runs`: `{id?:"client-generated-uuid",started_at_ms,party_id?:null}` → run `{id,user_id,party_id,started_at_ms,ended_at_ms,status,distance_m,active_duration_s,elapsed_duration_s,reward_points,deleted_at_ms}`. Optional party requires joined participant. Same user/id plus unchanged start time and party is idempotent, including a finalized run; another owner or changed metadata returns 409 `run_id_conflict`.
- `POST /v1/runs/{run_id}/tracks/batch`: `{points:[{seq,segment:0,latitude,longitude,recorded_at_ms,accuracy_m,altitude_m?:null}]}`. Maximum 1000 points; seq≥0; segment≥0 increments after pause/resume or GPS interruption. UNIQUE(run_id,seq), exact retry succeeds; a conflicting sequence payload fails 409 without partial insertion. Response `{accepted,duplicates,total_points}`. Upload queued points before finalize.
- `POST /v1/runs/{run_id}/finalize`: `{ended_at_ms,active_duration_s}` → frozen run summary. Durations are finite seconds and may be fractional (e.g. `5.401`); elapsed seconds equal `(ended_at_ms-started_at_ms)/1000`, with `0 ≤ active ≤ elapsed ≤ 86400`. Existing finalized runs return their stored summary and retain one reward ledger entry on a valid retry, even when reconstructed completion metadata differs. Non-contiguous sequences fail 409; timestamps must increase. Distance sums consecutive samples only within the same segment, accuracy ≤30m, plausible speed ≤8m/s and gap ≤60s. Reward is 1 point per 10m (**100 points/km**), default configurable abuse backstop `RUNMONI_REWARD_DAILY_LIMIT=10000` points/day in Asia/Seoul, credited once. No manual/imported distance rewards. These heuristics do not prove running or defeat all GPS spoofing.
- `GET /v1/runs` and `GET /v1/runs/{run_id}`: own history; `GET /v1/runs/{run_id}/tracks`: own route only. `GET /v1/stats?period=day|month|year|all&anchor_ms=<UTCms>` gives own finalized totals `{period,distance_m,active_duration_s,run_count,mileage}` using Seoul boundaries.
- `DELETE /v1/runs/{run_id}`: remove GPS points and hide record from history/statistics; retain the run summary as a tombstone and its reward ledger so deleted IDs cannot earn twice. Existing credited mileage remains. Account deletion/recovery is operator-assisted in this pilot.
- `PUT /v1/presence`: `{run_id,scope_type:"crew"|"party"|"pilot",scope_id?:uuid,latitude,longitude,exact_opt_in:true}`. Shared only during an owned active run and authorized scope; queued GPS points need not be uploaded first. `pilot` omits scope_id and exposes opted-in exact locations to all authenticated adult-attested invite testers. It is not internet-public discovery. `false` revokes. Server expiry is 45s after update.
- `GET /v1/presence?scope_type=party&scope_id=...`: authorized scope only, current opt-in entries `{user_id,nickname,avatar,latitude,longitude,updated_at_ms,expires_at_ms}`. Blocks enforced both directions. Crew presence requires crew membership; party presence requires joined participation.
- `GET /v1/presence?scope_type=pilot`: invite-authenticated pilot cohort only, otherwise same TTL/block rules.
- `DELETE /v1/presence`: immediately stop sharing. Finalize/cancel also stops sharing. No unauthenticated exact-location endpoint.

## Audio capabilities (optional external infrastructure)

- `POST /v1/voice/token`: `{scope_type:"crew"|"party",scope_id}` → `{server_url,token,room_name,expires_at_ms}`. Membership/block checks, audio-only LiveKit grant, 5-minute join token. Missing `LIVEKIT_URL/API_KEY/API_SECRET` returns 503 `capability_not_configured`, never fake success.
- `POST /v1/music/lease`: `{scope_type,scope_id,session_id?:uuid,pilot_test_acknowledged:true}` → `{bridge_url,token,room_name,session_id,lease_id,expires_at_ms}`. The flag acknowledges an adult invite-cohort technical test; it does **not** prove music licensing or service compliance. Owner or crew DJ only, same membership/block checks; requires LiveKit credentials plus `AUDIO_BRIDGE_URL`, `AUDIO_BRIDGE_JWT_SECRET` and `AUDIO_BRIDGE_CONTROL_SECRET`. One active DJ lease per room, 15-minute maximum. The bridge accepts HS256 JWT claims `{sub,room,session_id,lease_id,role:"dj",iat,exp}`.
- `DELETE /v1/music/lease/{lease_id}`: holder or crew owner revoke. `GET /v1/music/lease/{lease_id}/status`: bridge-only `Authorization: Bearer <AUDIO_BRIDGE_CONTROL_SECRET>` → `{active,expires_at_ms}`. Configure bridge to check every 5 seconds and disconnect on inactive/unreachable.
- Phone music capture and remote playback require user permission and content/service rights. A lease is not evidence that DRM capture or public music retransmission is permitted. Production Supabase email auth/migrations are prepared separately; this server does not implement that login flow.

## Contract/version history

- 2026-10-13: v0.1.0 introduced local adult-attested pilot auth, atomic party capacity, scoped messages/presence, sequence-idempotent uploads, frozen finalization/one-time rewards and optional audio capabilities. Public geolocation, email OTP, payments and deployment are outside this pilot implementation.
- 2026-10-13: v0.1.1 aligns Android models: five avatar slots, 0/500/1000/2000 cosmetic costs, 100 points/km; explicit configurable daily abuse limit; crew region/pace label/open join/leave; local/remote parties; segment-aware tracks; invite-cohort opt-in map; record deletion tombstones. No external launch or email-auth claim added.
- 2026-10-13: v0.1.2 changes music acknowledgement to `pilot_test_acknowledged` and explicitly separates technical testing from music rights. Crew member avatars now use the same five-slot object as profiles/presence; member lists enforce bidirectional blocks. Party creation stays crew-owner-only for both local and remote pilot events.
- 2026-10-13: v0.1.3 fixes initial chat polling to return the latest visible page, with ascending presentation, so messages after the 100th are observable. Cursor polling continues from `after_id` in ascending order. Added a 101-message regression.
- 2026-10-13: v0.1.4 preserves fractional active/elapsed seconds, preventing valid Android millisecond durations such as 5.401 seconds from failing 422. Expanded chat coverage to 201 messages, verified concurrent daily-cap accounting and immutable run metadata, and retained existing SQLite history without a destructive table migration.

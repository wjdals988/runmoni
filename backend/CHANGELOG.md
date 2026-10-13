# Backend change history

## 0.1.4 — 2026-10-13

- Fixed Android completion rejection: `active_duration_s` accepts finite fractional seconds and elapsed seconds use exact millisecond division. A 5,401ms run now stores 5.401s for both values, rather than rejecting the request or truncating elapsed time to 5s. Zero-to-24-hour and active≤elapsed guards remain enforced.
- New SQLite databases declare duration columns REAL. Existing non-STRICT INTEGER-affinity columns retain history and accept fractional REAL values; no table rebuild is required. The un-applied Supabase foundation declares both duration columns numeric for the future adapter.
- Expanded chat regression from 101 to 201 messages: initial polling returns IDs 102–201 in ascending order, while `after_id=1` returns IDs 2–101. The 100-message limit applies to the newest initial page and the next forward page.
- Added regressions for 0.001/0.999/5.401-second completions, exact 24-hour boundaries, legacy SQLite duration compatibility, frozen finalize retries, owner/start/party UUID conflicts and finalized run creation retries.
- Upgraded daily-cap validation to three synchronized concurrent completions. With a 15-point test cap, rewards were 11/4/0 in acquisition order, the sum stayed 15 and each of the three runs retained exactly one ledger entry. Existing two-request finalization and party-last-place races remain covered.

## 0.1.3 — 2026-10-13

- Fixed a polling pagination defect: initial `after_id=0` now returns the newest visible page in ascending order. Previously, repeated initial polling could stay on the first 100 messages forever. Positive cursors still return the next oldest-to-newest page.
- Added a 101-message regression covering initial latest-page visibility, limit handling and positive-cursor follow-up. Presence update was verified to allow owned active runs before queued GPS upload, supporting the Android 15-second location loop.

## 0.1.2 — 2026-10-13

- `rights_confirmed` was replaced by `pilot_test_acknowledged` in the music lease input. A technical pilot acknowledgement is not a licensing claim. JSON with the old field is rejected rather than silently accepted.
- Profile `owned_items` contract explicitly declares string SKUs; crew member avatars now use the same five-slot JSON object as profile/presence responses and apply bidirectional blocks.
- Supabase foundation adds case-insensitive nickname uniqueness, explicit adult guards, guarded JSON avatar validation, DJ demotion lease revocation and a restricted crew-leave RPC. Production adapter/email auth remain separate work.
- Local and remote parties remain crew-owner-created in this invite pilot. Broad membership party creation is not granted by the server.

## 0.1.1 — 2026-10-13

- Aligned the Android domain contract: `region`, `pace_label`, local/remote event kinds, optional paired coordinates, open authenticated crew join/leave, run deletion, five avatar slots and variant 0–3.
- Catalogue: 20 SKUs, free base variant plus 500/1,000/2,000-point variants. Rewards: 1 point/10m, configurable 10,000 points/day abuse backstop in Asia/Seoul.
- Added GPS `segment` boundaries to avoid connecting pause/resume and location gaps. Excluded accuracy above 30m, speed above 8m/s and sampling gaps above 60s from credited distance.
- Added exact opt-in `pilot` presence scope for authenticated adult-attested invite testers. Retained crew/party scope permission, 45-second server TTL and bidirectional blocks.
- Added additive SQLite schema migration (`user_version=2`). Presence upgrade intentionally clears ephemeral sharing, while account/run/ledger history is retained.
- Deleting a run removes its GPS samples and hides it from history/statistics while retaining its summary and reward guard. Deleted UUIDs cannot be reused to farm points.

## 0.1.0 — 2026-10-13

- Introduced SQLite transactional pilot auth with hashed bearer tokens and nickname impersonation prevention. Reusing another person's nickname cannot issue a token.
- Created crews, owner/DJ permissions, atomic party capacity, scoped chat/announcements, private tracks, sequence deduplication and frozen exactly-once finalization rewards.
- Introduced optional audio-only LiveKit tokens, owner/DJ music leases, room exclusivity and bridge-only revocation checks. Missing infrastructure is a typed 503 capability error.
- Added narrow CORS defaults and tests using an isolated real SQLite database. No remote service, public server deployment, production email OTP or payment processor was configured.

## Validation record — 2026-10-13

- Windows, CPython 3.12.14: FastAPI 0.143.0, Uvicorn 0.54.0, LiveKit API 1.2.1, HTTPX 0.28.1, pytest 9.1.1 were installed and pinned. Package versions were checked against official PyPI/docs. Full transitive lock is included.
- Initial 12 backend tests passed. A JWT grant test caught numeric LiveKit source values; fixed the grant to the SDK-supported string `microphone` before declaring success.
- Supabase CLI and `psql` were not found. Docker executable is present but its daemon is unavailable; no Docker/Postgres execution or remote Supabase schema application was claimed.
- SQL grammar parsing and final pytest totals are recorded in the final implementation report. These do not replace a production RLS/pgTAP database run or physical GPS/audio device tests.

### Offline v0.1.4 completion verification

- Before the duration fix, all four selected duration regression cases failed with HTTP 422, demonstrating the original defect. A first default pytest invocation encountered a Windows temp-folder permission error; using a fresh workspace basetemp and disabling its shared cache resolved the test environment issue.
- Final command: `backend/.venv/Scripts/python.exe -m pytest backend/tests/test_api.py -q -p no:cacheprovider --basetemp backend/.testtmp-final-014 --tb=short` — **23 passed, 1 warning, 9.68s**. The warning is Starlette's deprecated HTTPX TestClient integration; requests and all assertions completed. No dependency changes or external network requests were made in this completion pass.
- `backend/.venv/Scripts/python.exe supabase/validate_sql.py` — **142 top-level statements (99 migration + 43 test statements), 20 function bodies parsed**. These are grammar counts, not passed database tests. The pgTAP file plans **23 checks, 0 executed** in this environment.
- Local `docker info` found no `docker_engine` pipe; `supabase` and `psql` commands were not found. No PostgreSQL/RLS execution, Supabase project mutation, physical GPS test, real LiveKit connection or remote audio playback is claimed.
- Review confirmed mutations use one SQLite `BEGIN IMMEDIATE` transaction: capacity check+join, ownership+payment and finalize+daily-cap ledger insertion are serialized. Exact run ownership/start/party metadata is checked before reusing a UUID; finalized summaries remain frozen. Location presence intentionally permits an owned active run before track upload and expires after 45 seconds.

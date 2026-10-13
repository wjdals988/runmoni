# Production Supabase preparation — not the running pilot server

`migrations/202610130001_runmoni_foundation.sql` prepares PostgreSQL profiles linked to `auth.users.id`, crews/roles, local/remote parties, scoped messages, private tracks, cosmetic ownership/ledger, 45-second opt-in presence and leases. It must be applied and its RLS tests run on a fresh local Supabase project before use. No remote project has been modified and no database policy is claimed as deployment-verified.

The Android pilot currently uses invite-code FastAPI bearer auth from `backend/`. These tokens are **not** Supabase access tokens and must never be used as production identities. Production email OTP must call Supabase Auth `signInWithOtp`, complete verification, then create the matching profile. Adult confirmation here is an attestation, not age verification. Configure SMTP/rate limits and account recovery separately before public signup.

The SQL uses `timestamptz`; the pilot API uses UTC epoch milliseconds. A production API adapter must convert these types. Production presence supports authorized crew/party scopes only; the pilot's all-invited-testers exact map is deliberately not exposed as a public production endpoint.

Foundation v0.1.4 uses numeric active/elapsed seconds so millisecond measurements such as 5.401 seconds are retained. This is a fresh-project foundation edit; no existing or remote Supabase database has been altered.

RLS and grants are explicit across **14 tables**. `anon` has no application table privileges. Helpers live in an unexposed `private` schema, use `auth.uid()` internally and fixed search paths to prevent recursive membership policies. Authenticated clients cannot write roles/membership/capacity/ledger/tracks/leases directly. Membership and purchases use restricted RPCs, which serialize on party/profile rows. User nickname/avatar edits do not allow changing adult or identity fields; malformed/unsupported/unowned avatar variants are denied. Nicknames have a case-insensitive unique index. Crew leaving and DJ demotion revoke relevant leases. GPS/reward finalization remains a verified service-side operation to implement in the production adapter; the ledger UNIQUE(run_id) prevents a second credit.

Run using an installed Supabase CLI + Docker from this directory or a configured Supabase workspace:

```text
supabase init
supabase start
supabase db reset
supabase test db
```

The migration is an initial foundation, not an automatic conversion/import of pilot SQLite accounts. Pilot account mapping, production API adapter, live audio authorization revocation and anonymous coarse discovery need their own reviewed rollout.

Verified locally on 2026-10-13 without downloads: `backend/.venv/Scripts/python.exe supabase/validate_sql.py` parsed **142 top-level SQL statements** (99 migration, 43 test) and **20 function bodies**. The pgTAP test file plans **23 checks**, with **0 executed** here: no Supabase CLI or `psql` was found, and the Docker daemon's local `docker_engine` pipe was absent. Grammar parsing cannot establish object resolution, RLS access behavior or PostgreSQL concurrency correctness. The separate SQLite pilot backend passed **23 pytest cases**; that result does not validate these PostgreSQL policies.

Official references checked 2026-10-13: [RLS/grants](https://supabase.com/docs/guides/database/postgres/row-level-security), [auth profile linkage](https://supabase.com/docs/guides/auth/managing-user-data).

import concurrent.futures
import sqlite3
import threading
from pathlib import Path
from uuid import uuid4

import jwt
import pytest
from fastapi.testclient import TestClient

from backend.main import Settings, create_app, period_bounds, token_hash

NOW = 1791853200000


@pytest.fixture
def pilot(tmp_path):
    stamp = [NOW]
    app = create_app(Settings(db_path=str(tmp_path / "pilot.sqlite3"), pilot_invite="test-invite-code"), lambda: stamp[0])
    client = TestClient(app)

    def account(name):
        response = client.post("/v1/auth/pilot", json={"invite_code": "test-invite-code", "nickname": name, "adult_confirmed": True})
        assert response.status_code == 200, response.text
        data = response.json()
        return data["user"], {"Authorization": f"Bearer {data['access_token']}"}

    return app, client, stamp, account


def crew(client, headers):
    response = client.post("/v1/crews", headers=headers, json={"name": "한강 크루", "description": "같이 달려요", "region": "서울", "pace_label": "6:00–7:00"})
    assert response.status_code == 200, response.text
    return response.json()


def party(client, headers, crew_id, capacity=2):
    response = client.post("/v1/parties", headers=headers, json={"crew_id": crew_id, "title": "토요일 5K", "starts_at_ms": NOW + 3600000, "meeting_point": "공원 입구", "distance_km": 5, "pace_label": "6:30", "capacity": capacity})
    assert response.status_code == 200, response.text
    return response.json()


def run(client, headers, **extra):
    response = client.post("/v1/runs", headers=headers, json={"started_at_ms": NOW - 60000, **extra})
    assert response.status_code == 200, response.text
    return response.json()


def point(seq, lat, time_offset, segment=0):
    return {"seq": seq, "segment": segment, "latitude": lat, "longitude": 127, "recorded_at_ms": NOW - 60000 + time_offset, "accuracy_m": 5}


def test_auth_identity_adult_token_hash_revoke(pilot):
    app, client, _, account = pilot
    assert client.get("/v1/me").status_code == 401
    assert client.post("/v1/auth/pilot", json={"invite_code": "wrong-code", "nickname": "wrong", "adult_confirmed": True}).status_code == 403
    assert client.post("/v1/auth/pilot", json={"invite_code": "test-invite-code", "nickname": "minor", "adult_confirmed": False}).status_code == 403
    user, headers = account("러너 A")
    assert client.post("/v1/auth/pilot", json={"invite_code": "test-invite-code", "nickname": "러너 A", "adult_confirmed": True}).status_code == 400
    with app.state.db() as c:
        stored = c.execute("SELECT token_hash FROM sessions").fetchone()[0]
        assert len(stored) == 64 and headers["Authorization"][7:] not in stored
    assert client.delete("/v1/auth/session", headers=headers).status_code == 200
    assert client.get("/v1/me", headers=headers).status_code == 401


def test_atomic_last_party_place_and_join_idempotency(pilot):
    _, client, _, account = pilot
    owner, owner_h = account("크루장")
    _, first_h = account("첫 참가자")
    _, second_h = account("둘째 참가자")
    group = crew(client, owner_h)
    for h in [first_h, second_h]:
        assert client.post(f"/v1/crews/{group['id']}/join", headers=h).status_code == 200
    event = party(client, owner_h, group["id"])
    barrier = threading.Barrier(2)

    def join(h):
        barrier.wait()
        return client.post(f"/v1/parties/{event['id']}/join", headers=h)

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(join, h) for h in [first_h, second_h]]
        responses = [future.result() for future in futures]
    assert sorted(r.status_code for r in responses) == [200, 409]
    winner = first_h if responses[0].status_code == 200 else second_h
    assert client.post(f"/v1/parties/{event['id']}/join", headers=winner).json()["participant_count"] == 2
    assert client.get(f"/v1/parties/{event['id']}", headers=owner_h).json()["participant_count"] == 2
    assert client.post(f"/v1/parties/{event['id']}/cancel", headers=winner).status_code == 403
    assert client.post(f"/v1/parties/{event['id']}/cancel", headers=owner_h).status_code == 200
    assert client.post(f"/v1/parties/{event['id']}/join", headers=winner).status_code == 409


def test_messages_and_role_permissions_scoped(pilot):
    _, client, _, account = pilot
    owner, owner_h = account("리더")
    member, member_h = account("멤버")
    _, outsider_h = account("비회원")
    group = crew(client, owner_h)
    client.post(f"/v1/crews/{group['id']}/join", headers=member_h)
    event = party(client, owner_h, group["id"])
    crew_url, party_url = f"/v1/crews/{group['id']}/messages", f"/v1/parties/{event['id']}/messages"
    assert client.get(crew_url, headers=outsider_h).status_code == 403
    assert client.get(party_url, headers=member_h).status_code == 403
    assert client.post(crew_url, headers=member_h, json={"body": "위치 알려 주세요", "kind": "announcement"}).status_code == 403
    posted = client.post(crew_url, headers=owner_h, json={"body": "오전 8시에 만나요", "kind": "announcement"})
    assert posted.status_code == 200
    assert client.get(crew_url, headers=member_h).json()["items"][0]["kind"] == "announcement"
    role_url = f"/v1/crews/{group['id']}/members/{member['id']}"
    assert client.patch(role_url, headers=member_h, json={"role": "dj"}).status_code == 403
    assert client.patch(role_url, headers=owner_h, json={"role": "dj"}).status_code == 200
    assert client.get(crew_url, headers=member_h, params={"after_id": posted.json()["id"]}).json()["items"] == []
    assert client.post("/v1/blocks", headers=member_h, json={"user_id": owner["id"]}).status_code == 200
    assert client.get(crew_url, headers=member_h).json()["items"] == []


def test_initial_chat_poll_shows_latest_100_and_cursor_next_100(pilot):
    _, client, _, account = pilot
    _, h = account("페이지 리더")
    group = crew(client, h)
    url = f"/v1/crews/{group['id']}/messages"
    posted_ids = []
    for sequence in range(201):
        response = client.post(url, headers=h, json={"body": f"메시지 {sequence + 1}"})
        assert response.status_code == 200
        posted_ids.append(response.json()['id'])
    initial = client.get(url, headers=h).json()['items']
    assert len(initial) == 100
    assert [row['id'] for row in initial] == posted_ids[-100:]
    latest_three = client.get(url, headers=h, params={"limit": 3}).json()['items']
    assert [row['id'] for row in latest_three] == posted_ids[-3:]
    cursor = client.get(url, headers=h, params={"after_id": posted_ids[-3]}).json()['items']
    assert [row['id'] for row in cursor] == posted_ids[-2:]
    next_page = client.get(url, headers=h, params={"after_id": posted_ids[0]}).json()['items']
    assert [row['id'] for row in next_page] == posted_ids[1:101]


def test_legacy_integer_duration_schema_retains_data_and_fractional_seconds(tmp_path):
    database = tmp_path / "legacy.sqlite3"
    schema = Path(__file__).parents[1].joinpath("schema.sql").read_text(encoding="utf-8")
    schema = schema.replace("duration_s REAL", "duration_s INTEGER")
    user_id, run_id, token = str(uuid4()), str(uuid4()), "legacy-test-session"
    with sqlite3.connect(database) as c:
        c.executescript(schema)
        c.execute("INSERT INTO users(id,nickname,adult_confirmed,created_at_ms) VALUES(?,?,1,?)", (user_id, "기존 러너", NOW))
        c.execute("INSERT INTO sessions VALUES(?,?,?)", (token_hash(token), user_id, NOW + 86400000))
        c.execute("INSERT INTO runs(id,user_id,started_at_ms) VALUES(?,?,?)", (run_id, user_id, NOW - 5401))
    app = create_app(Settings(db_path=str(database), pilot_invite="test-invite-code"), lambda: NOW)
    client = TestClient(app)
    h = {"Authorization": f"Bearer {token}"}
    response = client.post(f"/v1/runs/{run_id}/finalize", headers=h, json={"ended_at_ms": NOW, "active_duration_s": 5.401})
    assert response.status_code == 200, response.text
    assert response.json()['elapsed_duration_s'] == response.json()['active_duration_s'] == 5.401
    assert client.get("/v1/me", headers=h).json()['nickname'] == "기존 러너"
    with app.state.db() as c:
        assert dict(c.execute("PRAGMA table_info(runs)").fetchall()[0])['name'] == 'id'
        # SQLite INTEGER affinity accepts fractional REAL values; preserve the
        # existing table rather than rebuilding private run/ledger history.
        declared = {row['name']: row['type'] for row in c.execute("PRAGMA table_info(runs)")}
        assert declared['active_duration_s'] == declared['elapsed_duration_s'] == 'INTEGER'
        assert c.execute("SELECT typeof(active_duration_s),typeof(elapsed_duration_s) FROM runs WHERE id=?", (run_id,)).fetchone()[:] == ('real', 'real')


@pytest.mark.parametrize("elapsed_ms", [1, 999, 5401])
def test_fractional_duration_round_trip_and_frozen_retry(pilot, elapsed_ms):
    app, client, _, account = pilot
    _, h = account("소수점 러너")
    record = run(client, h, started_at_ms=NOW - elapsed_ms)
    url = f"/v1/runs/{record['id']}"
    seconds = elapsed_ms / 1000.0
    payload = {"ended_at_ms": NOW, "active_duration_s": seconds}
    response = client.post(url + "/finalize", headers=h, json=payload)
    assert response.status_code == 200, response.text
    result = response.json()
    assert result["elapsed_duration_s"] == seconds
    assert result["active_duration_s"] == seconds
    assert result["reward_points"] == 0
    assert client.get(url, headers=h).json() == result
    assert client.get("/v1/stats", headers=h, params={"period": "all"}).json()["active_duration_s"] == seconds
    # Once finalized, an upload retry receives the stored summary even if its
    # reconstructed completion metadata differs. It must never re-credit.
    retry = client.post(url + "/finalize", headers=h, json={"ended_at_ms": NOW + 1000, "active_duration_s": 0})
    assert retry.status_code == 200 and retry.json() == result
    with app.state.db() as c:
        assert c.execute("SELECT COUNT(*) FROM ledger WHERE run_id=?", (record["id"],)).fetchone()[0] == 1


def test_duration_bounds_reject_then_allow_exact_ms(pilot):
    _, client, _, account = pilot
    _, h = account("시간 경계 러너")
    record = run(client, h, started_at_ms=NOW - 5401)
    url = f"/v1/runs/{record['id']}"
    for body in [{"ended_at_ms": NOW, "active_duration_s": 5.402},
                 {"ended_at_ms": record['started_at_ms'] - 1, "active_duration_s": 0},
                 {"ended_at_ms": NOW, "active_duration_s": -0.001}]:
        response = client.post(url + "/finalize", headers=h, json=body)
        assert response.status_code == 422
        assert client.get(url, headers=h).json()["status"] == "active"
    assert client.post(url + "/finalize", headers=h, json={"ended_at_ms": NOW, "active_duration_s": 5.401}).status_code == 200
    day = run(client, h, started_at_ms=NOW - 86400000)
    day_url = f"/v1/runs/{day['id']}/finalize"
    assert client.post(day_url, headers=h, json={"ended_at_ms": NOW + 1, "active_duration_s": 86400}).status_code == 422
    assert client.post(day_url, headers=h, json={"ended_at_ms": NOW, "active_duration_s": 86400}).status_code == 200


def test_run_uuid_retry_checks_owner_start_and_party_metadata(pilot):
    _, client, _, account = pilot
    _, owner_h = account("메타데이터 리더")
    _, member_h = account("메타데이터 멤버")
    group = crew(client, owner_h)
    event = party(client, owner_h, group['id'])
    payload = {"id": str(uuid4()), "started_at_ms": NOW - 60000, "party_id": event['id']}
    assert client.post("/v1/runs", headers=member_h, json=payload).status_code == 403
    created = client.post("/v1/runs", headers=owner_h, json=payload)
    assert created.status_code == 200
    assert client.post("/v1/runs", headers=owner_h, json=payload).json() == created.json()
    for h, conflicting in [(member_h, payload), (owner_h, {**payload, "started_at_ms": NOW - 59000}),
                           (owner_h, {**payload, "party_id": None})]:
        response = client.post("/v1/runs", headers=h, json=conflicting)
        assert response.status_code == 409 and response.json()['error']['code'] == 'run_id_conflict'
    finished = client.post(f"/v1/runs/{payload['id']}/finalize", headers=owner_h, json={"ended_at_ms": NOW, "active_duration_s": 60})
    assert finished.status_code == 200
    assert client.post("/v1/runs", headers=owner_h, json=payload).json() == finished.json()


def test_sequence_retry_conflict_atomic_missing_and_finalize_once_race(pilot):
    app, client, _, account = pilot
    user, h = account("실측 러너")
    _, other_h = account("다른 러너")
    record = run(client, h)
    url = f"/v1/runs/{record['id']}"
    points = [point(0, 37, 0), point(1, 37.0005, 20000), point(2, 37.001, 40000)]
    assert client.post(url + "/tracks/batch", headers=other_h, json={"points": points}).status_code == 404
    assert client.post(url + "/tracks/batch", headers=h, json={"points": points}).json()["accepted"] == 3
    assert client.post(url + "/tracks/batch", headers=h, json={"points": points}).json()["duplicates"] == 3
    bad = [point(3, 37.0015, 50000), point(1, 38, 20000)]
    assert client.post(url + "/tracks/batch", headers=h, json={"points": bad}).status_code == 409
    assert len(client.get(url + "/tracks", headers=h).json()["items"]) == 3
    barrier = threading.Barrier(2)

    def finish():
        barrier.wait()
        return client.post(url + "/finalize", headers=h, json={"ended_at_ms": NOW, "active_duration_s": 60})

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(finish), pool.submit(finish)]
        responses = [future.result() for future in futures]
    assert all(r.status_code == 200 for r in responses)
    assert responses[0].json() == responses[1].json()
    result = responses[0].json()
    assert 110 < result["distance_m"] < 112
    assert result["reward_points"] == 11
    assert client.get("/v1/me", headers=h).json()["mileage"] == 11
    with app.state.db() as c:
        assert c.execute("SELECT COUNT(*) FROM ledger WHERE run_id=?", (record["id"],)).fetchone()[0] == 1
    assert client.post(url + "/tracks/batch", headers=h, json={"points": points}).status_code == 409


def test_pause_segment_gap_and_bad_accuracy_do_not_credit(pilot):
    _, client, _, account = pilot
    _, h = account("세그먼트 러너")
    record = run(client, h)
    url = f"/v1/runs/{record['id']}"
    points = [point(0, 37, 0), point(1, 37.0001, 10000), point(2, 37.0005, 20000, 1), point(3, 37.0006, 30000, 1)]
    client.post(url + "/tracks/batch", headers=h, json={"points": points})
    result = client.post(url + "/finalize", headers=h, json={"ended_at_ms": NOW, "active_duration_s": 30}).json()
    assert 22 < result["distance_m"] < 23
    assert result["reward_points"] == 2
    record2 = run(client, h)
    points2 = [point(0, 37, 0), {**point(1, 37.001, 30000), "accuracy_m": 100}]
    client.post(f"/v1/runs/{record2['id']}/tracks/batch", headers=h, json={"points": points2})
    bad = client.post(f"/v1/runs/{record2['id']}/finalize", headers=h, json={"ended_at_ms": NOW, "active_duration_s": 60}).json()
    assert bad["distance_m"] == 0 and bad["reward_points"] == 0


def test_missing_sequences_rejected_then_recoverable(pilot):
    _, client, _, account = pilot
    _, h = account("재전송 러너")
    record = run(client, h)
    url = f"/v1/runs/{record['id']}"
    client.post(url + "/tracks/batch", headers=h, json={"points": [point(0, 37, 0), point(2, 37.001, 40000)]})
    assert client.post(url + "/finalize", headers=h, json={"ended_at_ms": NOW, "active_duration_s": 60}).status_code == 409
    client.post(url + "/tracks/batch", headers=h, json={"points": [point(1, 37.0005, 20000)]})
    assert client.post(url + "/finalize", headers=h, json={"ended_at_ms": NOW, "active_duration_s": 60}).status_code == 200


def test_presence_optin_scope_ttl_block_finalize_and_pilot_auth(pilot):
    _, client, stamp, account = pilot
    owner, owner_h = account("지도 리더")
    member, member_h = account("지도 멤버")
    _, outsider_h = account("지도 외부")
    group = crew(client, owner_h)
    client.post(f"/v1/crews/{group['id']}/join", headers=member_h)
    record = run(client, owner_h)
    payload = {"run_id": record["id"], "scope_type": "crew", "scope_id": group["id"], "latitude": 37, "longitude": 127, "exact_opt_in": True}
    params = {"scope_type": "crew", "scope_id": group["id"]}
    assert client.put("/v1/presence", headers=owner_h, json=payload).status_code == 200
    assert client.get("/v1/presence", headers=outsider_h, params=params).status_code == 403
    assert len(client.get("/v1/presence", headers=member_h, params=params).json()["items"]) == 1
    stamp[0] += 45000
    assert client.get("/v1/presence", headers=member_h, params=params).json()["items"] == []
    client.put("/v1/presence", headers=owner_h, json=payload)
    client.post("/v1/blocks", headers=member_h, json={"user_id": owner["id"]})
    assert client.get("/v1/presence", headers=member_h, params=params).json()["items"] == []
    client.delete(f"/v1/blocks/{owner['id']}", headers=member_h)
    client.put("/v1/presence", headers=owner_h, json={**payload, "scope_type": "pilot", "scope_id": None})
    assert client.get("/v1/presence", params={"scope_type": "pilot"}).status_code == 401
    assert len(client.get("/v1/presence", headers=outsider_h, params={"scope_type": "pilot"}).json()["items"]) == 1
    assert client.put("/v1/presence", headers=owner_h, json={**payload, "exact_opt_in": False}).json()["shared"] is False
    client.put("/v1/presence", headers=owner_h, json=payload)
    client.post(f"/v1/runs/{record['id']}/finalize", headers=owner_h, json={"ended_at_ms": stamp[0], "active_duration_s": 60})
    assert client.get("/v1/presence", headers=member_h, params=params).json()["items"] == []


def test_cosmetic_ownership_atomic_purchase_and_delete_no_replay(pilot):
    app, client, _, account = pilot
    user, h = account("꾸미는 러너")
    assert client.patch("/v1/me", headers=h, json={"avatar": {"hair": 1}}).status_code == 403
    assert client.post("/v1/shop/purchase", headers=h, json={"item_id": "hair:1"}).status_code == 409
    with app.state.db(True) as c:
        c.execute("INSERT INTO ledger VALUES(?,?,NULL,NULL,1000,?)", (str(uuid4()), user["id"], NOW))
    barrier = threading.Barrier(2)

    def buy():
        barrier.wait()
        return client.post("/v1/shop/purchase", headers=h, json={"item_id": "hair:1"})

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(buy), pool.submit(buy)]
        responses = [future.result() for future in futures]
    assert all(r.status_code == 200 for r in responses)
    assert client.get("/v1/me", headers=h).json()["mileage"] == 500
    assert client.patch("/v1/me", headers=h, json={"avatar": {"hair": 1}}).status_code == 200
    record = run(client, h)
    url = f"/v1/runs/{record['id']}"
    assert client.delete(url, headers=h).status_code == 200
    assert client.get(url, headers=h).status_code == 404
    assert client.post("/v1/runs", headers=h, json={"id": record["id"], "started_at_ms": record["started_at_ms"]}).status_code == 409


def test_voice_missing_credentials_not_fake_connected_and_roles(pilot):
    _, client, _, account = pilot
    _, owner_h = account("음성 리더")
    _, outsider_h = account("음성 외부")
    group = crew(client, owner_h)
    body = {"scope_type": "crew", "scope_id": group["id"]}
    assert client.post("/v1/voice/token", headers=outsider_h, json=body).status_code == 403
    response = client.post("/v1/voice/token", headers=owner_h, json=body)
    assert response.status_code == 503 and response.json()["error"]["code"] == "capability_not_configured"
    assert client.get("/health").json()["capabilities"] == {"voice": False, "music_broadcast": False}


def test_audio_jwt_grants_lease_revoke_and_role_demotion(tmp_path):
    settings = Settings(db_path=str(tmp_path / "audio.sqlite3"), pilot_invite="test-invite-code", livekit_url="wss://test.invalid", livekit_api_key="test-key", livekit_api_secret="test-secret-that-is-at-least-32-characters", bridge_url="wss://bridge.invalid/audio", bridge_secret="bridge-secret-that-is-at-least-32-characters", bridge_control_secret="control-secret-that-is-at-least-32-characters")
    app = create_app(settings)
    client = TestClient(app)
    login = client.post("/v1/auth/pilot", json={"invite_code": "test-invite-code", "nickname": "방송 리더", "adult_confirmed": True}).json()
    h = {"Authorization": f"Bearer {login['access_token']}"}
    group = crew(client, h)
    body = {"scope_type": "crew", "scope_id": group["id"]}
    voice = client.post("/v1/voice/token", headers=h, json=body)
    assert voice.status_code == 200, voice.text
    decoded = jwt.decode(voice.json()["token"], settings.livekit_api_secret, algorithms=["HS256"])
    assert decoded["video"]["roomJoin"] is True
    assert decoded["video"]["canPublishSources"] == ["microphone"]
    assert client.post("/v1/music/lease", headers=h, json={**body, "pilot_test_acknowledged": False}).status_code == 403
    music = client.post("/v1/music/lease", headers=h, json={**body, "pilot_test_acknowledged": True})
    assert music.status_code == 200, music.text
    lease = music.json()
    music_claims = jwt.decode(lease["token"], settings.bridge_secret, algorithms=["HS256"])
    assert music_claims["role"] == "dj" and music_claims["lease_id"] == lease["lease_id"]
    assert music_claims["exp"] - music_claims["iat"] == 900
    assert client.post("/v1/music/lease", headers=h, json={**body, "pilot_test_acknowledged": True}).status_code == 409
    status_url = f"/v1/music/lease/{lease['lease_id']}/status"
    control = {"Authorization": f"Bearer {settings.bridge_control_secret}"}
    assert client.get(status_url).status_code == 401
    assert client.get(status_url, headers=control).json()["active"] is True
    assert client.delete(f"/v1/music/lease/{lease['lease_id']}", headers=h).status_code == 200
    assert client.get(status_url, headers=control).json()["active"] is False
    second = client.post("/v1/auth/pilot", json={"invite_code": "test-invite-code", "nickname": "방송 DJ", "adult_confirmed": True}).json()
    dj_h = {"Authorization": f"Bearer {second['access_token']}"}
    client.post(f"/v1/crews/{group['id']}/join", headers=dj_h)
    role_url = f"/v1/crews/{group['id']}/members/{second['user']['id']}"
    assert client.patch(role_url, headers=h, json={"role": "dj"}).status_code == 200
    dj_lease = client.post("/v1/music/lease", headers=dj_h, json={**body, "pilot_test_acknowledged": True}).json()
    dj_status = f"/v1/music/lease/{dj_lease['lease_id']}/status"
    assert client.get(dj_status, headers=control).json()["active"] is True
    assert client.patch(role_url, headers=h, json={"role": "member"}).status_code == 200
    assert client.get(dj_status, headers=control).json()["active"] is False
    assert client.post("/v1/music/lease", headers=dj_h, json={**body, "pilot_test_acknowledged": True}).status_code == 403
    assert client.post("/v1/blocks", headers=dj_h, json={"user_id": login['user']['id']}).status_code == 200
    assert client.post("/v1/voice/token", headers=h, json=body).status_code == 403
    assert client.post("/v1/voice/token", headers=dj_h, json=body).status_code == 403


def test_configurable_daily_reward_limit_is_atomic_across_runs(pilot):
    app, client, _, account = pilot
    app.state.settings.reward_daily_limit = 15
    user, h = account("일별 보상 러너")
    urls = []
    for _ in range(3):
        record = run(client, h)
        url = f"/v1/runs/{record['id']}"
        assert client.post(url + "/tracks/batch", headers=h, json={"points": [point(0, 37, 0), point(1, 37.001, 40000)]}).status_code == 200
        urls.append(url)
    barrier = threading.Barrier(3)

    def finish(url):
        barrier.wait()
        return client.post(url + "/finalize", headers=h, json={"ended_at_ms": NOW, "active_duration_s": 60})

    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        responses = list(pool.map(finish, urls))
    assert all(response.status_code == 200 for response in responses)
    assert sorted(response.json()["reward_points"] for response in responses) == [0, 4, 11]
    assert client.get("/v1/me", headers=h).json()["mileage"] == 15
    with app.state.db() as c:
        assert c.execute("SELECT COUNT(*),SUM(delta) FROM ledger WHERE user_id=? AND run_id IS NOT NULL", (user['id'],)).fetchone()[:] == (3, 15)


def test_sampling_gap_speed_and_non_monotonic_time_fail_safely(pilot):
    _, client, _, account = pilot
    _, h = account("시간 검증 러너")
    record = run(client, h, started_at_ms=NOW - 120000)
    url = f"/v1/runs/{record['id']}"
    points = [{**point(0, 37, 0), "recorded_at_ms": NOW - 120000},
              {**point(1, 37.001, 0), "recorded_at_ms": NOW - 30000},
              {**point(2, 37.01, 0), "recorded_at_ms": NOW}]
    assert client.post(url + "/tracks/batch", headers=h, json={"points": points}).status_code == 200
    finished = client.post(url + "/finalize", headers=h, json={"ended_at_ms": NOW, "active_duration_s": 120}).json()
    assert finished["distance_m"] == 0 and finished["reward_points"] == 0
    second = run(client, h)
    second_url = f"/v1/runs/{second['id']}"
    assert client.post(second_url + "/tracks/batch", headers=h, json={"points": [point(0, 37, 30000), point(1, 37.0001, 10000)]}).status_code == 200
    invalid = client.post(second_url + "/finalize", headers=h, json={"ended_at_ms": NOW, "active_duration_s": 60})
    assert invalid.status_code == 409 and invalid.json()["error"]["code"] == "non_monotonic_track"
    assert client.get("/v1/me", headers=h).json()["mileage"] == 0


def test_crew_leave_removes_party_membership_and_exact_presence(pilot):
    _, client, _, account = pilot
    _, owner_h = account("탈퇴 리더")
    member, member_h = account("탈퇴 멤버")
    group = crew(client, owner_h)
    assert client.post(f"/v1/crews/{group['id']}/join", headers=member_h).status_code == 200
    event = party(client, owner_h, group["id"])
    assert client.post(f"/v1/parties/{event['id']}/join", headers=member_h).status_code == 200
    record = run(client, member_h, party_id=event['id'])
    assert client.put("/v1/presence", headers=member_h, json={"run_id": record['id'], "scope_type": "party", "scope_id": event['id'], "latitude": 37, "longitude": 127, "exact_opt_in": True}).status_code == 200
    assert client.delete(f"/v1/crews/{group['id']}/join", headers=owner_h).status_code == 409
    assert client.delete(f"/v1/crews/{group['id']}/join", headers=member_h).status_code == 200
    assert client.get(f"/v1/parties/{event['id']}", headers=owner_h).json()['participant_count'] == 1
    assert client.get("/v1/presence", headers=owner_h, params={"scope_type": "party", "scope_id": event['id']}).json()['items'] == []
    assert client.get(f"/v1/crews/{group['id']}/messages", headers=member_h).status_code == 403
    assert client.get(f"/v1/runs/{record['id']}", headers=member_h).status_code == 200


def test_remote_party_fields_and_owner_only_creation(pilot):
    _, client, _, account = pilot
    _, owner_h = account("원격 리더")
    _, member_h = account("원격 멤버")
    group = crew(client, owner_h)
    client.post(f"/v1/crews/{group['id']}/join", headers=member_h)
    body = {"crew_id": group['id'], "title": "각자 5K", "kind": "remote", "starts_at_ms": NOW + 3600000, "distance_km": 5, "pace_label": "자유 페이스", "capacity": 8}
    assert client.post("/v1/parties", headers=member_h, json=body).status_code == 403
    assert client.post("/v1/parties", headers=owner_h, json={**body, "latitude": 37}).status_code == 422
    result = client.post("/v1/parties", headers=owner_h, json=body)
    assert result.status_code == 200
    assert result.json()['kind'] == 'remote' and result.json()['meeting_point'] == ''
    assert result.json()['latitude'] is None and result.json()['longitude'] is None


def test_seoul_period_boundaries():
    start, end = period_bounds("day", NOW)
    assert end - start == 86400000
    assert (start + 9 * 3600000) % 86400000 == 0


def test_cors_rejects_arbitrary_browser_origin(pilot):
    _, client, _, _ = pilot
    response = client.options("/v1/me", headers={"Origin": "https://evil.example", "Access-Control-Request-Method": "GET"})
    assert response.status_code == 400
    assert "access-control-allow-origin" not in response.headers

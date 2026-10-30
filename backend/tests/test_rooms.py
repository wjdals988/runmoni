"""Public room metadata and authenticated membership, isolated from legacy pilot data."""
import concurrent.futures
import sqlite3
import threading
from pathlib import Path
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from backend.main import Settings, create_app, token_hash

NOW = 1791853200000


@pytest.fixture
def rooms(tmp_path):
    stamp = [NOW]
    app = create_app(Settings(db_path=str(tmp_path / 'rooms.sqlite3'), pilot_invite='legacy-test-invite'), lambda: stamp[0])
    return app, TestClient(app), stamp


def guest(client, name):
    r = client.post('/v1/auth/guest', json={'nickname': name, 'adult_confirmed': True})
    assert r.status_code == 200
    return r.json(), {'Authorization': 'Bearer ' + r.json()['access_token']}


def crew(client, headers, **extra):
    r = client.post('/v1/crews', headers=headers, json={'name': '공개 크루', 'visibility': 'open', **extra})
    assert r.status_code == 200
    return r.json()


def party(client, headers, **extra):
    r = client.post('/v1/parties', headers=headers, json={'title': '원격 파티', 'kind': 'remote', 'starts_at_ms': NOW + 60000, 'distance_km': 5, 'capacity': 2, **extra})
    assert r.status_code == 200
    return r.json()


def ids(response):
    assert response.status_code == 200
    return {x['id'] for x in response.json()['items']}


def test_guest_needs_no_invite_and_cannot_claim_an_existing_identity(rooms):
    app, client, stamp = rooms
    assert client.get('/v1/crews/discover').status_code == 200
    assert client.post('/v1/auth/guest', json={'nickname': '테스터', 'adult_confirmed': False}).status_code == 403
    assert client.post('/v1/auth/guest', json={'nickname': 'x', 'adult_confirmed': True}).status_code == 422
    data, h = guest(client, '첫 프로필')
    assert data['user']['auth_mode'] == 'guest'
    assert client.post('/v1/auth/guest', json={'nickname': '첫 프로필', 'adult_confirmed': True}).status_code == 400
    with app.state.db() as c:
        stored = c.execute('SELECT token_hash FROM sessions').fetchone()[0]
        assert stored == token_hash(data['access_token']) and stored != data['access_token']
    assert client.get('/v1/crews/discover', headers={'Authorization': 'Bearer invalid'}).status_code == 401
    stamp[0] = data['expires_at_ms'] + 1
    assert client.get('/v1/me', headers=h).status_code == 401


def test_public_crew_is_browsable_and_joinable_without_an_invitation(rooms):
    _, client, _ = rooms
    _, owner = guest(client, '방장 러너')
    _, visitor = guest(client, '방문 러너')
    assert client.post('/v1/crews', json={'name': '무인증 방', 'visibility': 'open'}).status_code == 401
    room = crew(client, owner)
    assert 'invitation_code' not in room
    assert room['id'] in ids(client.get('/v1/crews/discover'))
    assert client.get(f"/v1/crews/{room['id']}/messages", headers=visitor).status_code == 403
    for _ in range(2):
        joined = client.post(f"/v1/crews/{room['id']}/join", headers=visitor)
        assert joined.status_code == 200 and joined.json()['member_count'] == 2
    assert client.get(f"/v1/crews/{room['id']}/messages", headers=visitor).status_code == 200
    assert client.delete(f"/v1/crews/{room['id']}/join", headers=visitor).status_code == 200
    assert client.get(f"/v1/crews/{room['id']}/messages", headers=visitor).status_code == 403


def test_guest_and_anonymous_browsers_cannot_reach_legacy_rooms_or_locations(rooms):
    _, client, _ = rooms
    r = client.post('/v1/auth/pilot', json={'invite_code': 'legacy-test-invite', 'nickname': '기존 방장', 'adult_confirmed': True}).json()
    pilot = {'Authorization': 'Bearer '+r['access_token']}
    legacy = client.post('/v1/crews', headers=pilot, json={'name': '이전 비공개 방'}).json()
    old_party = party(client, pilot, crew_id=legacy['id'])
    _, h = guest(client, '새 게스트')
    for auth in ({}, h):
        assert legacy['id'] not in ids(client.get('/v1/crews/discover', headers=auth))
        assert old_party['id'] not in ids(client.get('/v1/parties/discover', headers=auth))
    assert client.post(f"/v1/crews/{legacy['id']}/join", headers=h).status_code == 403
    assert client.post('/v1/crews/join', headers=h, json={'invitation_code': legacy['invitation_code']}).status_code == 403
    assert client.get('/v1/presence?scope_type=pilot', headers=h).status_code == 403
    assert client.post('/v1/crews', headers=h, json={'name': '비공개 시도'}).status_code == 403
    assert legacy['id'] in ids(client.get('/v1/crews/discover', headers=pilot))


def test_standalone_party_does_not_require_or_silently_add_crew_membership(rooms):
    _, client, _ = rooms
    _, owner = guest(client, '파티 방장')
    _, visitor = guest(client, '파티 참가자')
    room = party(client, owner)
    assert room['is_joined'] and room['is_organizer']
    assert room['id'] in ids(client.get('/v1/parties/discover'))
    assert not ids(client.get('/v1/crews', headers=owner))
    assert not ids(client.get('/v1/crews/discover'))
    path = f"/v1/parties/{room['id']}"
    assert client.get(path+'/messages', headers=visitor).status_code == 403
    assert client.post(path+'/join', headers=visitor).status_code == 200
    assert client.post(path+'/join', headers=visitor).json()['participant_count'] == 2
    assert not ids(client.get('/v1/crews', headers=visitor))
    assert client.get(f"/v1/crews/{room['crew_id']}/members", headers=visitor).status_code == 403
    assert client.post(f"/v1/crews/{room['crew_id']}/join", headers=visitor).status_code == 404
    assert client.post(path+'/messages', headers=visitor, json={'body': '함께 달려요'}).status_code == 200
    assert client.post(path+'/messages', headers=visitor, json={'body': '공지 사칭', 'kind': 'announcement'}).status_code == 403
    assert client.post(path+'/messages', headers=owner, json={'body': '출발 안내', 'kind': 'announcement'}).status_code == 200
    assert client.delete(path+'/join', headers=owner).status_code == 409
    assert client.delete(path+'/join', headers=visitor).status_code == 200
    assert client.get(path+'/messages', headers=visitor).status_code == 403


def test_last_public_party_place_remains_atomic(rooms):
    _, client, _ = rooms
    _, owner = guest(client, '경합 방장')
    _, a = guest(client, '경합 A')
    _, b = guest(client, '경합 B')
    room = party(client, owner)
    barrier = threading.Barrier(2)
    def join(h):
        barrier.wait()
        return client.post(f"/v1/parties/{room['id']}/join", headers=h).status_code
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        assert sorted(pool.map(join, [a, b])) == [200, 409]
    assert client.get(f"/v1/parties/{room['id']}", headers=owner).json()['participant_count'] == 2


def test_public_crew_member_can_host_and_outsider_can_join_only_the_party(rooms):
    _, client, stamp = rooms
    _, owner = guest(client, '크루 리더')
    _, member = guest(client, '크루 멤버')
    _, outsider = guest(client, '파티 방문자')
    group = crew(client, owner)
    assert client.post(f"/v1/crews/{group['id']}/join", headers=member).status_code == 200
    room = party(client, member, crew_id=group['id'])
    assert client.post(f"/v1/parties/{room['id']}/join", headers=outsider).status_code == 200
    assert not ids(client.get('/v1/crews', headers=outsider))
    assert client.post(f"/v1/parties/{room['id']}/cancel", headers=outsider).status_code == 403
    assert client.post(f"/v1/parties/{room['id']}/cancel", headers=member).status_code == 200
    assert room['id'] not in ids(client.get('/v1/parties/discover'))
    assert client.post(f"/v1/parties/{room['id']}/join", headers=owner).status_code == 409
    ended = party(client, owner)
    stamp[0] += 60001
    assert ended['id'] not in ids(client.get('/v1/parties/discover'))
    assert client.post(f"/v1/parties/{ended['id']}/join", headers=outsider).status_code == 409


def test_migration_preserves_existing_private_data(tmp_path):
    path = tmp_path/'old.sqlite3'
    old_id, crew_id = str(uuid4()), str(uuid4())
    with sqlite3.connect(path) as c:
        c.executescript(Path('backend/schema.sql').read_text())
        c.execute('INSERT INTO users(id,nickname,adult_confirmed,created_at_ms) VALUES(?,?,1,?)', (old_id, '기존 러너', NOW))
        c.execute('INSERT INTO crews(id,name,description,owner_id,invitation_code,created_at_ms) VALUES(?,?,?,?,?,?)', (crew_id, '예전 방', '', old_id, 'old-test-code', NOW))
        c.execute("INSERT INTO crew_members VALUES(?,?,'owner')", (crew_id, old_id))
        c.execute('INSERT INTO ledger VALUES(?,?,NULL,NULL,777,?)', (str(uuid4()), old_id, NOW))
    app = create_app(Settings(db_path=str(path)), lambda: NOW)
    with app.state.db() as c:
        assert c.execute('SELECT nickname,auth_mode FROM users WHERE id=?', (old_id,)).fetchone()[:] == ('기존 러너', 'pilot')
        assert c.execute('SELECT visibility,room_kind FROM crews WHERE id=?', (crew_id,)).fetchone()[:] == ('pilot', 'crew')
        assert c.execute('SELECT SUM(delta) FROM ledger WHERE user_id=?', (old_id,)).fetchone()[0] == 777
        assert c.execute('PRAGMA user_version').fetchone()[0] == 3
    assert crew_id not in ids(TestClient(app).get('/v1/crews/discover'))

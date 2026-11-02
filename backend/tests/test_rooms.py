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
    if "crew_id" not in extra:
        extra["crew_id"] = crew(client, headers)["id"]
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


def test_party_requires_a_parent_and_never_silently_joins_a_crew(rooms):
    _, client, _ = rooms
    _, owner = guest(client, '파티 주최자')
    _, visitor = guest(client, '파티 참가자')
    rejected = client.post('/v1/parties', headers=owner, json={'title': '독립 시도', 'kind': 'remote', 'starts_at_ms': NOW+60000, 'distance_km': 5, 'capacity': 2})
    assert rejected.status_code == 422
    assert not ids(client.get('/v1/crews/discover'))
    assert not ids(client.get('/v1/parties/discover'))
    group = crew(client, owner)
    room = party(client, owner, crew_id=group['id'])
    path = f"/v1/parties/{room['id']}"
    assert client.post(path+'/join', headers=visitor).status_code == 403
    assert not ids(client.get('/v1/crews', headers=visitor))
    assert client.post(f"/v1/crews/{group['id']}/join", headers=visitor).status_code == 200
    assert client.post(path+'/join', headers=visitor).status_code == 200
    assert client.post(path+'/join', headers=visitor).json()['participant_count'] == 2
    assert client.post(path+'/messages', headers=visitor, json={'body': '함께 달려요'}).status_code == 200
    assert client.post(path+'/messages', headers=visitor, json={'body': '공지 사칭', 'kind': 'announcement'}).status_code == 403
    assert client.post(path+'/messages', headers=owner, json={'body': '출발 안내', 'kind': 'announcement'}).status_code == 200
    assert client.delete(f"/v1/crews/{group['id']}/join", headers=visitor).status_code == 200
    assert client.get(path+'/messages', headers=visitor).status_code == 403


def test_last_public_party_place_remains_atomic(rooms):
    _, client, _ = rooms
    _, owner = guest(client, '경합 방장')
    _, a = guest(client, '경합 A')
    _, b = guest(client, '경합 B')
    room = party(client, owner)
    for h in (a,b):
        assert client.post(f"/v1/crews/{room['crew_id']}/join", headers=h).status_code == 200
    barrier = threading.Barrier(2)
    def join(h):
        barrier.wait()
        return client.post(f"/v1/parties/{room['id']}/join", headers=h).status_code
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        assert sorted(pool.map(join, [a, b])) == [200, 409]
    assert client.get(f"/v1/parties/{room['id']}", headers=owner).json()['participant_count'] == 2


def test_public_crew_member_can_host_and_outsider_must_join_the_crew(rooms):
    _, client, stamp = rooms
    _, owner = guest(client, '크루 리더')
    _, member = guest(client, '크루 멤버')
    _, outsider = guest(client, '파티 방문자')
    group = crew(client, owner)
    assert client.post(f"/v1/crews/{group['id']}/join", headers=member).status_code == 200
    room = party(client, member, crew_id=group['id'])
    assert client.post(f"/v1/parties/{room['id']}/join", headers=outsider).status_code == 403
    assert client.post(f"/v1/crews/{group['id']}/join", headers=outsider).status_code == 200
    assert client.post(f"/v1/parties/{room['id']}/join", headers=outsider).status_code == 200
    assert group['id'] in ids(client.get('/v1/crews', headers=outsider))
    assert client.post(f"/v1/parties/{room['id']}/cancel", headers=outsider).status_code == 403
    assert client.post(f"/v1/parties/{room['id']}/cancel", headers=member).status_code == 200
    assert room['id'] not in ids(client.get('/v1/parties/discover'))
    assert client.post(f"/v1/parties/{room['id']}/join", headers=owner).status_code == 409
    ended = party(client, owner)
    assert client.post(f"/v1/crews/{ended['crew_id']}/join", headers=outsider).status_code == 200
    stamp[0] += 60001
    assert ended['id'] not in ids(client.get('/v1/parties/discover'))
    assert client.post(f"/v1/parties/{ended['id']}/join", headers=outsider).status_code == 409


def test_migration_preserves_existing_private_data(tmp_path):
    path = tmp_path/'old.sqlite3'
    old_id, crew_id = str(uuid4()), str(uuid4())
    with sqlite3.connect(path) as c:
        c.executescript(Path('backend/schema.sql').read_text().replace('deleted_at_ms INTEGER, status', 'status'))
        c.execute('INSERT INTO users(id,nickname,adult_confirmed,created_at_ms) VALUES(?,?,1,?)', (old_id, '기존 러너', NOW))
        c.execute('INSERT INTO crews(id,name,description,owner_id,invitation_code,created_at_ms) VALUES(?,?,?,?,?,?)', (crew_id, '예전 방', '', old_id, 'old-test-code', NOW))
        c.execute("INSERT INTO crew_members VALUES(?,?,'owner')", (crew_id, old_id))
        c.execute('INSERT INTO ledger VALUES(?,?,NULL,NULL,777,?)', (str(uuid4()), old_id, NOW))
    app = create_app(Settings(db_path=str(path)), lambda: NOW)
    with app.state.db() as c:
        assert c.execute('SELECT nickname,auth_mode FROM users WHERE id=?', (old_id,)).fetchone()[:] == ('기존 러너', 'pilot')
        assert c.execute('SELECT visibility,room_kind FROM crews WHERE id=?', (crew_id,)).fetchone()[:] == ('pilot', 'crew')
        assert c.execute('SELECT SUM(delta) FROM ledger WHERE user_id=?', (old_id,)).fetchone()[0] == 777
        assert c.execute('PRAGMA user_version').fetchone()[0] == 4
        assert 'deleted_at_ms' in {r[1] for r in c.execute('PRAGMA table_info(parties)')}
    assert crew_id not in ids(TestClient(app).get('/v1/crews/discover'))


def test_party_deletion_is_authorized_and_preserves_parent_messages_and_history(rooms):
    app, client, _ = rooms
    _, owner = guest(client, '삭제 크루장')
    _, host = guest(client, '파티 주최')
    _, visitor = guest(client, '일반 멤버')
    group = crew(client, owner)
    for h in (host, visitor):
        assert client.post(f"/v1/crews/{group['id']}/join", headers=h).status_code == 200
    event = party(client, host, crew_id=group['id'])
    other = party(client, owner, crew_id=group['id'])
    path=f"/v1/parties/{event['id']}"
    assert client.post(path+'/messages', headers=host, json={'body':'보존할 대화'}).status_code == 200
    assert client.delete(path, headers=visitor).status_code == 403
    assert client.delete(path, headers=owner).status_code == 200
    assert event['id'] not in ids(client.get('/v1/parties/discover'))
    assert other['id'] in ids(client.get('/v1/parties/discover'))
    assert group['id'] in ids(client.get('/v1/crews/discover'))
    for h in (owner, host, visitor):
        assert client.get(path, headers=h).status_code == 404
        assert client.post(path+'/join', headers=h).status_code == 404
        assert client.get(path+'/messages', headers=h).status_code == 404
    with app.state.db() as c:
        assert c.execute('SELECT deleted_at_ms FROM parties WHERE id=?',(event['id'],)).fetchone()[0] == NOW
        assert c.execute("SELECT COUNT(*) FROM messages WHERE scope_type='party' AND scope_id=?",(event['id'],)).fetchone()[0] == 1
        assert c.execute('SELECT COUNT(*) FROM party_members WHERE party_id=?',(event['id'],)).fetchone()[0] == 1


def test_former_crew_member_can_remove_own_party_but_not_read_or_remove_others(rooms):
    _, client, _ = rooms
    _, owner = guest(client, '정리 크루장')
    _, host = guest(client, '탈퇴 주최자')
    group = crew(client, owner)
    assert client.post(f"/v1/crews/{group['id']}/join", headers=host).status_code == 200
    mine = party(client, host, crew_id=group['id'])
    other = party(client, owner, crew_id=group['id'])
    assert client.delete(f"/v1/crews/{group['id']}/join", headers=host).status_code == 200
    assert client.get(f"/v1/parties/{mine['id']}/messages", headers=host).status_code == 403
    assert client.delete(f"/v1/parties/{other['id']}", headers=host).status_code == 403
    assert client.delete(f"/v1/parties/{mine['id']}", headers=host).status_code == 200
    assert other['id'] in ids(client.get('/v1/parties/discover'))

"""Public API boundaries that must fail as client errors, without 500 responses."""
import pytest
from fastapi.testclient import TestClient

from backend.main import Settings, create_app


@pytest.fixture
def api(tmp_path):
    settings = Settings(
        db_path=str(tmp_path / "boundaries.sqlite3"),
        pilot_invite="boundary-test-invite",
        livekit_url="wss://livekit.invalid",
        livekit_api_key="test-key",
        livekit_api_secret="livekit-secret-at-least-32-characters",
        bridge_url="wss://bridge.invalid/audio",
        bridge_secret="b" * 32,
        bridge_control_secret="c" * 32,
    )
    client = TestClient(create_app(settings, lambda: 1791853200000))
    login = client.post("/v1/auth/pilot", json={
        "invite_code": settings.pilot_invite, "nickname": "경계 테스트", "adult_confirmed": True,
    })
    headers = {"Authorization": f"Bearer {login.json()['access_token']}"}
    return settings, client, headers


@pytest.mark.parametrize("period", ["day", "month", "year"])
@pytest.mark.parametrize("anchor", [253402300798999, 253373000000000])
def test_unrepresentable_calendar_period_returns_422(api, period, anchor):
    _, client, headers = api
    response = client.get("/v1/stats", headers=headers, params={"period": period, "anchor_ms": anchor})
    expected = 422 if anchor == 253402300798999 or period == "year" else 200
    assert response.status_code == expected, response.text
    if expected == 422:
        assert response.json()["error"]["code"] == "invalid_stats_time"


def test_all_time_stats_do_not_convert_unused_anchor(api):
    _, client, headers = api
    response = client.get("/v1/stats", headers=headers, params={
        "period": "all", "anchor_ms": 253402300798999,
    })
    assert response.status_code == 200
    assert response.json()["run_count"] == 0


@pytest.mark.parametrize("field", ["seq", "segment"])
def test_track_integer_overflow_is_rejected_before_database_write(api, field):
    _, client, headers = api
    run = client.post("/v1/runs", headers=headers, json={"started_at_ms": 1791853190000}).json()
    point = {"seq": 0, "segment": 0, "latitude": 37, "longitude": 127,
             "recorded_at_ms": 1791853190000, "accuracy_m": 5}
    response = client.post(f"/v1/runs/{run['id']}/tracks/batch", headers=headers,
                           json={"points": [point, {**point, field: 2 ** 100}]})
    assert response.status_code == 422
    assert client.get(f"/v1/runs/{run['id']}/tracks", headers=headers).json()["items"] == []


@pytest.mark.parametrize("scope", ["crews", "parties"])
def test_message_cursor_integer_overflow_is_a_validation_error(api, scope):
    _, client, headers = api
    crew = client.post("/v1/crews", headers=headers, json={"name": "경계 크루"}).json()
    scope_id = crew["id"]
    if scope == "parties":
        party = client.post("/v1/parties", headers=headers, json={
            "crew_id": scope_id, "title": "경계 파티", "kind": "remote",
            "starts_at_ms": 1791856800000, "distance_km": 5, "capacity": 2,
        })
        assert party.status_code == 200
        scope_id = party.json()["id"]
    response = client.get(f"/v1/{scope}/{scope_id}/messages", headers=headers,
                          params={"after_id": 2 ** 100})
    assert response.status_code == 422


@pytest.mark.parametrize("secret_field", ["bridge_secret", "bridge_control_secret"])
@pytest.mark.parametrize("length", [0, 31, 32])
def test_music_capability_and_lease_match_bridge_secret_requirements(api, secret_field, length):
    settings, client, headers = api
    setattr(settings, secret_field, "x" * length)
    capabilities = client.get("/health").json()["capabilities"]
    assert capabilities["voice"] is True
    assert capabilities["music_broadcast"] is (length >= 32)
    crew = client.post("/v1/crews", headers=headers, json={"name": "방송 크루"}).json()
    response = client.post("/v1/music/lease", headers=headers, json={
        "scope_type": "crew", "scope_id": crew["id"], "pilot_test_acknowledged": True,
    })
    assert response.status_code == (200 if length >= 32 else 503)
    if length < 32:
        assert response.json()["error"]["code"] == "capability_not_configured"

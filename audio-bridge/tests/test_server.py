import json
import asyncio
import struct
import threading
import time
from uuid import uuid4

import jwt
from fastapi.testclient import TestClient

from server import Settings, create_app

SECRET = "runmoni-test-secret-never-deploy-1234567890"


def credentials():
    return Settings("wss://test.invalid", "key", "secret", SECRET, SECRET)


def lease_payload():
    now = int(time.time())
    return {"sub": str(uuid4()), "room": f"party-{uuid4()}", "session_id": str(uuid4()),
            "lease_id": str(uuid4()), "role": "dj", "iat": now, "exp": now + 900}


def start(payload):
    return {"type": "start", "version": 1, "sampleRate": 48000, "channels": 2, "codec": "opus",
            "frameMs": 20, "room": payload["room"], "sessionId": payload["session_id"]}


class FakePublisher:
    instances = []

    def __init__(self, settings, lease):
        self.packets, self.closed, self.finished = [], False, False
        self.instances.append(self)

    async def connect(self):
        pass

    async def publish(self, packet):
        self.packets.append(packet)

    async def finish(self):
        self.finished = True

    async def close(self):
        self.closed = True


async def active(settings, lease):
    return True


async def inactive(settings, lease):
    return False


def test_unconfigured_is_not_fake_success():
    with TestClient(create_app(Settings())) as client:
        assert client.get("/health").json()["configured"] is False
        with client.websocket_connect("/audio") as websocket:
            assert websocket.receive_json() == {"type": "error", "message": "bridge_not_configured"}


def test_revoked_lease_is_rejected_before_music_publisher():
    payload = lease_payload()
    with TestClient(create_app(credentials(), FakePublisher, inactive)) as client:
        with client.websocket_connect("/audio", headers={"Authorization": f"Bearer {jwt.encode(payload, SECRET, algorithm='HS256')}"}) as websocket:
            assert websocket.receive_json()["message"] == "inactive_dj_lease"


def test_separate_music_participant_path_and_cleanup():
    FakePublisher.instances.clear()
    payload = lease_payload()
    headers = {"Authorization": f"Bearer {jwt.encode(payload, SECRET, algorithm='HS256')}"}
    with TestClient(create_app(credentials(), FakePublisher, active)) as client:
        with client.websocket_connect("/audio", headers=headers) as websocket:
            websocket.send_json(start(payload))
            assert websocket.receive_json()["type"] == "ready"
            assert client.get("/health").json()["active_music_rooms"] == 1
            websocket.send_bytes(struct.pack(">q", 1_000_000) + b"opus-packet")
            # The HTTP request lets the app loop process the queued frame without a timing sleep.
            client.get("/health")
        assert client.get("/health").json()["active_music_rooms"] == 0
    assert FakePublisher.instances[0].closed is True
    assert FakePublisher.instances[0].packets == [b"opus-packet"]


def test_duplicate_room_stream_is_rejected():
    payload = lease_payload()
    headers = {"Authorization": f"Bearer {jwt.encode(payload, SECRET, algorithm='HS256')}"}
    with TestClient(create_app(credentials(), FakePublisher, active)) as client:
        with client.websocket_connect("/audio", headers=headers) as first:
            first.send_json(start(payload))
            assert first.receive_json()["type"] == "ready"
            with client.websocket_connect("/audio", headers=headers) as second:
                second.send_json(start(payload))
                assert second.receive_json()["message"] == "music_room_already_publishing"


def test_file_eof_acknowledges_only_after_inflight_packet_and_source_drain():
    entered, release = threading.Event(), threading.Event()

    class SlowPublisher(FakePublisher):
        async def publish(self, packet):
            entered.set()
            assert await asyncio.to_thread(release.wait, 2)
            await super().publish(packet)

    FakePublisher.instances.clear()
    payload = lease_payload()
    headers = {"Authorization": f"Bearer {jwt.encode(payload, SECRET, algorithm='HS256')}"}
    with TestClient(create_app(credentials(), SlowPublisher, active)) as client:
        with client.websocket_connect("/audio", headers=headers) as websocket:
            websocket.send_json(start(payload))
            assert websocket.receive_json()["type"] == "ready"
            websocket.send_bytes(struct.pack(">q", 1_000_000) + b"tail")
            assert entered.wait(2)
            websocket.send_json({"type": "end", "sessionId": payload["session_id"]})
            client.get("/health")
            publisher = FakePublisher.instances[-1]
            assert publisher.finished is False
            release.set()
            assert websocket.receive_json() == {"type": "finished", "sessionId": payload["session_id"]}
            assert publisher.packets == [b"tail"]
            assert publisher.finished is True
        assert client.get("/health").json()["active_music_rooms"] == 0
    assert publisher.closed is True


def test_eof_wrong_session_is_rejected_and_cleans_up():
    payload = lease_payload()
    headers = {"Authorization": f"Bearer {jwt.encode(payload, SECRET, algorithm='HS256')}"}
    with TestClient(create_app(credentials(), FakePublisher, active)) as client:
        with client.websocket_connect("/audio", headers=headers) as websocket:
            websocket.send_json(start(payload))
            assert websocket.receive_json()["type"] == "ready"
            websocket.send_json({"type": "end", "sessionId": str(uuid4())})
            assert websocket.receive_json() == {"type": "error", "message": "invalid_audio_end"}
        assert client.get("/health").json()["active_music_rooms"] == 0
    assert FakePublisher.instances[-1].closed is True
    assert FakePublisher.instances[-1].finished is False


def test_disconnect_during_eof_aborts_source_drain():
    draining = threading.Event()

    class BlockedPublisher(FakePublisher):
        async def finish(self):
            draining.set()
            await asyncio.Event().wait()

    payload = lease_payload()
    headers = {"Authorization": f"Bearer {jwt.encode(payload, SECRET, algorithm='HS256')}"}
    with TestClient(create_app(credentials(), BlockedPublisher, active)) as client:
        with client.websocket_connect("/audio", headers=headers) as websocket:
            websocket.send_json(start(payload))
            assert websocket.receive_json()["type"] == "ready"
            websocket.send_json({"type": "end", "sessionId": payload["session_id"]})
            assert draining.wait(2)
        assert client.get("/health").json()["active_music_rooms"] == 0
    assert FakePublisher.instances[-1].closed is True


def test_expired_lease_aborts_eof_drain():
    class BlockedPublisher(FakePublisher):
        async def finish(self):
            await asyncio.Event().wait()

    async def expiry_checker(settings, lease):
        return lease.expires_at > time.time()

    payload = lease_payload()
    payload["exp"] = time.time() + 0.3
    headers = {"Authorization": f"Bearer {jwt.encode(payload, SECRET, algorithm='HS256')}"}
    with TestClient(create_app(credentials(), BlockedPublisher, expiry_checker)) as client:
        with client.websocket_connect("/audio", headers=headers) as websocket:
            websocket.send_json(start(payload))
            assert websocket.receive_json()["type"] == "ready"
            websocket.send_json({"type": "end", "sessionId": payload["session_id"]})
            assert websocket.receive_json() == {"type": "error", "message": "dj_lease_ended"}
        assert client.get("/health").json()["active_music_rooms"] == 0
    assert FakePublisher.instances[-1].closed is True

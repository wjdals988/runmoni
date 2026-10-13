import asyncio
import json
import struct
import time
from uuid import uuid4

import jwt
import pytest

from protocol import FreshAudioQueue, ProtocolError, parse_packet, validate_end, validate_start, verify_lease

SECRET = "runmoni-test-secret-never-deploy-1234567890"


def claims(**overrides):
    values = {"sub": str(uuid4()), "room": f"crew-{uuid4()}", "session_id": str(uuid4()),
              "lease_id": str(uuid4()), "role": "dj", "iat": 1000, "exp": 1900}
    values.update(overrides)
    return values


def token(payload):
    return jwt.encode(payload, SECRET, algorithm="HS256")


def test_room_and_session_are_bound_to_verified_lease():
    payload = claims()
    lease = verify_lease(token(payload), SECRET, now=1100)
    start = {"type": "start", "version": 1, "sampleRate": 48000, "channels": 2, "codec": "opus",
             "frameMs": 20, "room": lease.room, "sessionId": lease.session_id}
    validate_start(json.dumps(start), lease)
    start["room"] = f"crew-{uuid4()}"
    with pytest.raises(ProtocolError):
        validate_start(json.dumps(start), lease)


def test_eof_is_bound_to_verified_session():
    lease = verify_lease(token(claims()), SECRET, now=1100)
    validate_end(json.dumps({"type": "end", "sessionId": lease.session_id}), lease)


@pytest.mark.parametrize("message", ["invalid-json", "[]", "{}", "x" * 1025,
                                     json.dumps({"type": "end", "sessionId": str(uuid4())})])
def test_invalid_eof_is_rejected(message):
    lease = verify_lease(token(claims()), SECRET, now=1100)
    with pytest.raises(ProtocolError, match="invalid_audio_end"):
        validate_end(message, lease)


@pytest.mark.parametrize("override", [
    {"role": "member"}, {"exp": 1100}, {"iat": 1200}, {"exp": 2000},
    {"sub": "malformed"}, {"room": "another-room"}, {"session_id": ""}, {"lease_id": None},
    {"iat": True}, {"exp": False},
])
def test_invalid_permissions_or_expiry_are_rejected(override):
    with pytest.raises(ProtocolError):
        verify_lease(token(claims(**override)), SECRET, now=1100)


def test_forged_signature_and_short_configured_secret_rejected():
    signed = token(claims())
    with pytest.raises(ProtocolError):
        verify_lease(signed, "wrong-secret-" * 4, now=1100)
    with pytest.raises(ProtocolError, match="not_configured"):
        verify_lease(signed, "short", now=1100)


def test_packet_size_timestamp_and_payload():
    assert parse_packet(struct.pack(">q", 20_000) + b"opus") == (20_000, b"opus")
    for invalid in (b"", b"12345678", b"x" * 4105, struct.pack(">q", -1) + b"opus"):
        with pytest.raises(ProtocolError):
            parse_packet(invalid)


def test_queue_drops_oldest_and_rejects_stale_or_replayed_packets():
    queue = FreshAudioQueue(max_frames=2)
    assert queue.put(0, b"a", arrival=1.0)
    assert queue.put(20_000, b"b", arrival=1.02)
    assert queue.put(40_000, b"c", arrival=1.04)
    assert queue.queue.qsize() == 2
    assert queue.queue.get_nowait()[1] == b"b"
    assert not queue.put(40_000, b"replay", arrival=1.04)
    assert not queue.put(60_000, b"late", arrival=1.9)
    assert queue.dropped == 3
    assert queue.put(920_000, b"now", arrival=1.92)


def test_non_realtime_fast_upload_is_rejected():
    queue = FreshAudioQueue()
    queue.put(0, b"a", arrival=1)
    with pytest.raises(ProtocolError, match="non_realtime"):
        queue.put(2_000_000, b"b", arrival=1)


def test_queue_drain_accounts_for_discarded_frames():
    async def exercise():
        queue = FreshAudioQueue(max_frames=1)
        queue.put(0, b"overwritten")
        queue.put(20_000, b"tail")
        assert await queue.get() == b"tail"
        queue.task_done()
        await asyncio.wait_for(queue.join(), timeout=0.5)
        # Even a stale frame must release its unfinished-task count before get waits again.
        stale_queue = FreshAudioQueue()
        stale_queue.put(0, b"stale", arrival=time.monotonic() - 1)
        getter = asyncio.create_task(stale_queue.get())
        try:
            await asyncio.wait_for(stale_queue.join(), timeout=0.5)
        finally:
            getter.cancel()
            await asyncio.gather(getter, return_exceptions=True)
        assert queue.dropped == stale_queue.dropped == 1

    asyncio.run(exercise())

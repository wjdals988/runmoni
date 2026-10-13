"""Small, separately testable wire and permission contract. Never log token/audio contents."""
from __future__ import annotations

import asyncio
import json
import re
import struct
import time
from dataclasses import dataclass
from typing import Any
from uuid import UUID

import jwt


class ProtocolError(ValueError):
    pass


@dataclass(frozen=True)
class Lease:
    owner: str
    room: str
    session_id: str
    lease_id: str
    expires_at: float


def verify_lease(token: str, secret: str, *, now: float | None = None) -> Lease:
    if len(secret) < 32:
        raise ProtocolError("bridge_not_configured")
    if not token or len(token) > 4096:
        raise ProtocolError("invalid_token")
    try:
        payload = jwt.decode(token, secret, algorithms=["HS256"],
                             options={"require": ["sub", "room", "role", "session_id", "lease_id", "iat", "exp"],
                                      "verify_exp": False, "verify_iat": False})
        current = time.time() if now is None else now
        issued, expiry = payload["iat"], payload["exp"]
        if isinstance(issued, bool) or isinstance(expiry, bool) or not isinstance(issued, (float, int)) or not isinstance(expiry, (float, int)):
            raise ValueError("time")
        if expiry <= current or issued > current + 30 or not 0 < expiry - issued <= 900:
            raise ValueError("expiry")
        if payload["role"] != "dj":
            raise ValueError("role")
        room = payload["room"]
        if not isinstance(room, str) or not re.fullmatch(r"(?:crew|party)-[0-9a-f-]{36}", room):
            raise ValueError("room")
        UUID(room.split("-", 1)[1])
        for key in ("sub", "session_id", "lease_id"):
            UUID(payload[key])
        return Lease(payload["sub"], room, payload["session_id"], payload["lease_id"], float(expiry))
    except (jwt.InvalidTokenError, ValueError, TypeError, KeyError, AttributeError) as exc:
        raise ProtocolError("invalid_or_expired_dj_lease") from exc


def validate_start(message: str, lease: Lease) -> dict[str, Any]:
    try:
        start = json.loads(message)
        expected = {"type": "start", "version": 1, "sampleRate": 48000, "channels": 2,
                    "codec": "opus", "frameMs": 20, "room": lease.room, "sessionId": lease.session_id}
        if not isinstance(start, dict) or any(type(start.get(key)) is not type(value) or start.get(key) != value
                                              for key, value in expected.items()):
            raise ValueError("contract")
        return start
    except (json.JSONDecodeError, TypeError, ValueError) as exc:
        raise ProtocolError("invalid_audio_start") from exc


def validate_end(message: str, lease: Lease) -> None:
    """Only finite-file EOF may drain queued music; disconnect/stop still discard it."""
    try:
        if len(message) > 1024:
            raise ValueError("size")
        end = json.loads(message)
        if end != {"type": "end", "sessionId": lease.session_id}:
            raise ValueError("contract")
    except (json.JSONDecodeError, TypeError, ValueError) as exc:
        raise ProtocolError("invalid_audio_end") from exc


def parse_packet(data: bytes) -> tuple[int, bytes]:
    if not 9 <= len(data) <= 4104:
        raise ProtocolError("invalid_packet_size")
    timestamp, = struct.unpack(">q", data[:8])
    if timestamp < 0:
        raise ProtocolError("invalid_timestamp")
    return timestamp, data[8:]


class FreshAudioQueue:
    """Bounded latest-audio queue; clock offset is anchored on the first received packet."""
    def __init__(self, max_frames: int = 5, stale_after_s: float = 0.3):
        if not 1 <= max_frames <= 25:
            raise ValueError("queue bounds")
        self.queue: asyncio.Queue[tuple[float, bytes]] = asyncio.Queue(max_frames)
        self.stale_after_s = stale_after_s
        self.first_timestamp: int | None = None
        self.first_arrival: float = 0
        self.last_timestamp = -1
        self.dropped = 0

    def put(self, timestamp: int, packet: bytes, *, arrival: float | None = None) -> bool:
        received = time.monotonic() if arrival is None else arrival
        if timestamp <= self.last_timestamp:
            self.dropped += 1
            return False
        self.last_timestamp = timestamp
        if self.first_timestamp is None:
            self.first_timestamp, self.first_arrival = timestamp, received
        expected = self.first_arrival + (timestamp - self.first_timestamp) / 1_000_000
        if received - expected > self.stale_after_s:
            # A TCP stall is recoverable, but do not replay its entire backlog.
            self.dropped += 1
            return False
        if expected - received > 0.5:
            raise ProtocolError("non_realtime_audio")
        if self.queue.full():
            self.queue.get_nowait()
            self.queue.task_done()
            self.dropped += 1
        self.queue.put_nowait((received, packet))
        return True

    async def get(self) -> bytes:
        while True:
            arrival, packet = await self.queue.get()
            if time.monotonic() - arrival <= self.stale_after_s:
                return packet
            self.queue.task_done()
            self.dropped += 1

    def task_done(self) -> None:
        self.queue.task_done()

    async def join(self) -> None:
        await self.queue.join()

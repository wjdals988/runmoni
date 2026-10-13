"""Invite-only pilot Opus bridge. Run with one persistent uvicorn worker behind TLS."""
from __future__ import annotations

import asyncio
import contextlib
import importlib.util
import json
import os
import struct
import time
from dataclasses import dataclass
from datetime import timedelta

import httpx
from fastapi import FastAPI, WebSocket, WebSocketDisconnect

from protocol import FreshAudioQueue, Lease, ProtocolError, parse_packet, validate_end, validate_start, verify_lease


@dataclass(frozen=True)
class Settings:
    livekit_url: str = ""
    livekit_key: str = ""
    livekit_secret: str = ""
    jwt_secret: str = ""
    control_secret: str = ""
    api_url: str = "http://127.0.0.1:8787"
    source_queue_ms: int = 100
    jitter_frames: int = 5

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(os.getenv("LIVEKIT_URL", ""), os.getenv("LIVEKIT_API_KEY", ""),
                   os.getenv("LIVEKIT_API_SECRET", ""), os.getenv("AUDIO_BRIDGE_JWT_SECRET", ""),
                   os.getenv("AUDIO_BRIDGE_CONTROL_SECRET", ""), os.getenv("PILOT_API_URL", "http://127.0.0.1:8787").rstrip("/"),
                   int(os.getenv("AUDIO_SOURCE_QUEUE_MS", "100")), int(os.getenv("AUDIO_JITTER_FRAMES", "5")))

    @property
    def configured(self) -> bool:
        return bool(self.livekit_url and self.livekit_key and self.livekit_secret and
                    len(self.jwt_secret) >= 32 and len(self.control_secret) >= 32 and
                    20 <= self.source_queue_ms <= 500 and 1 <= self.jitter_frames <= 25)


class MusicPublisher:
    def __init__(self, settings: Settings, lease: Lease):
        self.settings, self.lease = settings, lease
        self.room = self.source = self.decoder = self.resampler = None

    async def connect(self) -> None:
        import av
        from livekit import api, rtc
        self.room = rtc.Room()
        identity = f"runmoni-music:{self.lease.owner}:{self.lease.session_id}"
        token = (api.AccessToken(self.settings.livekit_key, self.settings.livekit_secret)
                 .with_identity(identity).with_name("Runmoni DJ music")
                 .with_metadata(json.dumps({"kind": "music", "owner": self.lease.owner,
                                            "session_id": self.lease.session_id}))
                 .with_ttl(timedelta(minutes=5))
                 .with_grants(api.VideoGrants(room_join=True, room=self.lease.room,
                                             can_publish=True, can_subscribe=False, can_publish_data=False,
                                             can_publish_sources=["screen_share_audio"])).to_jwt())
        await self.room.connect(self.settings.livekit_url, token)
        self.source = rtc.AudioSource(48000, 2, queue_size_ms=self.settings.source_queue_ms)
        track = rtc.LocalAudioTrack.create_audio_track("runmoni-music", self.source)
        options = rtc.TrackPublishOptions(source=rtc.TrackSource.SOURCE_SCREENSHARE_AUDIO)
        options.audio_encoding.max_bitrate = 128000
        options.dtx = False
        options.red = False
        await self.room.local_participant.publish_track(track, options)
        self.decoder = av.CodecContext.create("opus", "r")
        # RFC 7845 OpusHead: version=1, stereo, pre-skip=0, original rate=48k, gain=0, mapping=0.
        self.decoder.extradata = b"OpusHead" + struct.pack("<BBHIhB", 1, 2, 0, 48000, 0, 0)
        self.decoder.open()
        self.resampler = av.AudioResampler(format="s16", layout="stereo", rate=48000)

    async def publish(self, packet: bytes) -> None:
        import av
        for decoded in self.decoder.decode(av.Packet(packet)):
            for frame in self.resampler.resample(decoded):
                await self._capture(frame)

    async def _capture(self, frame) -> None:
        from livekit import rtc
        if frame.samples > 5760:
            raise ProtocolError("oversized_audio_frame")
        pcm = bytes(frame.planes[0])[:frame.samples * 2 * 2]
        await self.source.capture_frame(rtc.AudioFrame(pcm, 48000, 2, frame.samples))

    async def finish(self) -> None:
        # Codec/resampler/native source latency must finish before the EOF acknowledgement.
        for decoded in self.decoder.decode(None):
            for frame in self.resampler.resample(decoded):
                await self._capture(frame)
        for frame in self.resampler.resample(None):
            await self._capture(frame)
        await self.source.wait_for_playout()

    async def close(self) -> None:
        if self.source is not None:
            self.source.clear_queue()
            await self.source.aclose()
        if self.room is not None:
            await self.room.disconnect()


async def lease_is_active(settings: Settings, lease: Lease) -> bool:
    if lease.expires_at <= time.time():
        return False
    try:
        async with httpx.AsyncClient(timeout=3) as client:
            response = await client.get(f"{settings.api_url}/v1/music/lease/{lease.lease_id}/status",
                                        headers={"Authorization": f"Bearer {settings.control_secret}"})
            response.raise_for_status()
            state = response.json()
            return state.get("active") is True and state.get("expires_at_ms", 0) > time.time() * 1000
    except (httpx.HTTPError, ValueError, TypeError):
        return False  # Fail closed on revoked membership, expiry, or unreachable control plane.


def create_app(settings: Settings | None = None, publisher_factory=MusicPublisher, lease_checker=lease_is_active) -> FastAPI:
    settings = settings or Settings.from_env()
    app = FastAPI(title="Runmoni music bridge", version="0.1.0")
    active_rooms: set[str] = set()
    admission_lock = asyncio.Lock()

    @app.get("/health")
    async def health():
        return {"status": "ok", "version": "0.1.0", "configured": settings.configured,
                "livekit_installed": importlib.util.find_spec("livekit") is not None,
                "opus_decoder_installed": importlib.util.find_spec("av") is not None,
                "active_music_rooms": len(active_rooms), "recording": False}

    @app.websocket("/audio")
    async def audio(websocket: WebSocket):
        await websocket.accept()
        publisher = None
        room_reserved = False
        tasks: list[asyncio.Task] = []
        lease = None
        try:
            if not settings.configured:
                raise ProtocolError("bridge_not_configured")
            authorization = websocket.headers.get("authorization", "")
            if not authorization.startswith("Bearer "):
                raise ProtocolError("missing_dj_lease")
            lease = verify_lease(authorization[7:], settings.jwt_secret)
            if not await lease_checker(settings, lease):
                raise ProtocolError("inactive_dj_lease")
            start = await asyncio.wait_for(websocket.receive_text(), timeout=5)
            if len(start) > 1024:
                raise ProtocolError("oversized_start")
            validate_start(start, lease)
            async with admission_lock:
                if lease.room in active_rooms:
                    raise ProtocolError("music_room_already_publishing")
                active_rooms.add(lease.room)
                room_reserved = True
            publisher = publisher_factory(settings, lease)
            await asyncio.wait_for(publisher.connect(), timeout=15)
            await websocket.send_json({"type": "ready", "sessionId": lease.session_id})
            queue = FreshAudioQueue(settings.jitter_frames)

            async def receive():
                while True:
                    message = await asyncio.wait_for(websocket.receive(), timeout=10)
                    if message["type"] == "websocket.disconnect":
                        raise WebSocketDisconnect(message.get("code", 1000))
                    if message.get("bytes") is not None:
                        timestamp, packet = parse_packet(message["bytes"])
                        queue.put(timestamp, packet)
                    elif message.get("text") is not None:
                        validate_end(message["text"], lease)
                        return
                    else:
                        raise ProtocolError("invalid_audio_frame")

            async def play():
                while True:
                    packet = await queue.get()
                    try:
                        await publisher.publish(packet)
                    finally:
                        queue.task_done()

            async def authorize():
                while True:
                    await asyncio.sleep(min(5, max(0, lease.expires_at - time.time())))
                    if not await lease_checker(settings, lease):
                        raise ProtocolError("dj_lease_ended")

            tasks = [asyncio.create_task(receive()), asyncio.create_task(play()), asyncio.create_task(authorize())]
            completed, _ = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
            for task in completed:
                task.result()
            # A normal receive completion is the authenticated EOF message. Keep play and
            # authorization live during its bounded drain; their failures abort the tail.
            async def drain():
                await queue.join()
                await publisher.finish()

            async def watch_end():
                message = await websocket.receive()
                if message["type"] == "websocket.disconnect":
                    raise WebSocketDisconnect(message.get("code", 1000))
                raise ProtocolError("audio_after_end")

            tasks.append(asyncio.create_task(watch_end()))
            drain_task = asyncio.create_task(asyncio.wait_for(drain(), timeout=5))
            tasks.append(drain_task)
            completed, _ = await asyncio.wait(tasks[1:], return_when=asyncio.FIRST_COMPLETED)
            for task in completed:
                task.result()
            await websocket.send_json({"type": "finished", "sessionId": lease.session_id})
            await websocket.close(code=1000)
        except (ProtocolError, asyncio.TimeoutError) as exc:
            with contextlib.suppress(Exception):
                await websocket.send_json({"type": "error", "message": str(exc) or "audio_timeout"})
                await websocket.close(code=1008)
        except WebSocketDisconnect:
            pass
        except Exception:
            # Deliberately avoid dumping credential-bearing exceptions or audio into logs.
            with contextlib.suppress(Exception):
                await websocket.send_json({"type": "error", "message": "audio_bridge_failed"})
                await websocket.close(code=1011)
        finally:
            for task in tasks:
                task.cancel()
            if tasks:
                await asyncio.gather(*tasks, return_exceptions=True)
            if publisher:
                with contextlib.suppress(Exception):
                    await publisher.close()
            if room_reserved and lease:
                async with admission_lock:
                    active_rooms.discard(lease.room)

    return app


app = create_app()

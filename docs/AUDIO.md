# Runmoni audio pilot v0.1.0

## Implemented paths and honest boundaries

- `AudioController.status` is a `StateFlow<AudioStatus>` for connection, microphone, voice/music gains, source, outgoing bytes, queued bytes and error text.
- `connect(url, token, room)` joins a real LiveKit room. Missing configuration reports `NOT_CONFIGURED`. It never invents a successful room connection.
- `setMicrophoneEnabled`, `setVoiceVolume`, `setMusicVolume` operate microphone and remote audio independently. Gains are clamped to 0–1.
- `playLocalMusicFile(uri)` decodes and plays a selected local audio file without a server. `musicSending=false` explicitly distinguishes this from broadcasting.
- `startMusicFile(uri, bridgeUrl, bridgeToken)` sends the same decoded PCM used for local playback through Android's Opus encoder and the authenticated bridge.
- `createExternalCaptureIntent()` requests Android's MediaProjection consent. Pass its Activity result to `startExternalCapture(resultCode, data, packageName, bridgeUrl, bridgeToken)`. The controller starts the correct foreground type before obtaining MediaProjection. A revoked projection ends transmission.
- `stopMusic`, `disconnect`, `close` stop the relevant resources. The persistent notification can end the entire audio session.
- Set `onBroadcastStopped = { leaseId -> ... }` to revoke that exact backend DJ lease on file EOF, capture revocation, network failure and explicit stop. The ID is snapshotted and cleared before invoking the callback, so a rapid source switch cannot delete the newly created lease. Do not infer lease cleanup solely from conflated `StateFlow` transitions.

No LiveKit project or external music-app compatibility is provisioned or verified by these files. YouTube Music, Spotify, Melon, Genie, FLO and Samsung Music are six separate **unverified** capture sources. Samsung Music file playback and Runmoni's own file decoder are distinct paths. Silent capture may mean paused playback, app opt-out, protected content or routing behavior; the app does not claim to distinguish them. Android's capture restrictions are respected without DRM/opt-out bypass.

## Independent tracks

```
Android microphone ──────────────────────────> LiveKit voice track
Selected app AudioPlaybackCapture ─┐
                                   ├─> PCM → Opus 128kbps → WSS bridge
Selected file decoder/local output ─┘                         │
                                                     Python Opus decode
                                                              │
                                                     PCM AudioSource
                                                              │
                                   LiveKit music participant / separate track
```

Publisher identity is `runmoni-music:{ownerUUID}:{sessionUUID}`, participant metadata is `{kind:"music",owner:"ownerUUID",session_id:"sessionUUID"}`, and track name is `runmoni-music`. The receiver identifies music by its track name and uses a separate gain from voice. During broadcasting the DJ's own music track is muted to avoid hearing both the local source and delayed return audio. External-app source selection is filtered by its UID; the Runmoni UID is explicitly rejected so returned room audio is not intentionally recaptured.

The room uses AudioSwitch routing with `manageAudioFocus=false` to avoid automatically taking the selected music player's audio focus, and disables microphone prewarming before explicit microphone enablement. This is an intentional pilot tradeoff: audio focus interruption and Bluetooth routing/phone-call behavior require device checks. These options do not force a third-party music app to continue playing or override its capture settings.

48kHz stereo PCM16 is 1,536kbps / 691.2 MB per hour. Opus configured at 128kbps is approximately 57.6 MB per hour of payload before overhead, retransmissions and voice. This is a bitrate calculation, not measured device battery consumption. Opus is encoded on Android, decoded on the bridge and encoded again by LiveKit; lossless or sample-synchronized playback is not promised.

## Local server setup

From `audio-bridge/`, create an isolated Python 3.12 environment, install `requirements.txt`, export the values in `.env.example`, then run:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn server:app --host 127.0.0.1 --port 8790 --workers 1 --ws-max-size 8192 --no-access-log
```

`.env.example` is only a template: uvicorn does not load it automatically. Load actual values into the process environment or use an ignored `.env` with uvicorn's `--env-file`. Use the same `AUDIO_BRIDGE_JWT_SECRET` and `AUDIO_BRIDGE_CONTROL_SECRET` as the pilot backend. USB Android testing needs `adb reverse tcp:8790 tcp:8790` in addition to the backend port. The bridge URL is `ws://127.0.0.1:8790/audio` for USB and `ws://10.0.2.2:8790/audio` for the emulator; externally hosted endpoints require `wss://`.

The standalone bridge requires LiveKit credentials. Its health endpoint reports configuration/dependency status separately. No API keys belong in an APK. This design decrypts and processes music on the bridge and must not be advertised as end-to-end encrypted; TLS and WebRTC transport protect their respective links.

## Backend contract

Call `POST /v1/music/lease` with authenticated membership, owner/DJ role, scope and `pilot_test_acknowledged:true`. This acknowledges an adult invite-only technical test; it does not prove music rights or compliance with a music service's terms. The returned short-lived JWT contains `sub`, `room`, `session_id`, `lease_id`, `role:"dj"`, `iat`, `exp`. The Android socket supplies it through `Authorization: Bearer`, not a logged URL query string. The signed room/session must match the start message. The bridge additionally asks `GET /v1/music/lease/{lease_id}/status` with its control secret before publishing and every 5 seconds, closing on revocation, expiry or control-plane failure. Leases expire after 15 minutes and currently require a newly issued lease to continue; automatic renewal is not implemented.

One active music publisher per room is enforced by the backend lease and by the bridge's single-process admission guard. This pilot is designed for `--workers 1`; multiple instances require a distributed guard. Revoke the backend lease when the DJ stops or leaves. A valid lease proves app authorization, not music retransmission rights. The first pilot is invite-only adult testers; public/Play distribution and future child accounts require their own product/legal review.

## Wire protocol v1

First text frame:

```json
{"type":"start","version":1,"sampleRate":48000,"channels":2,"codec":"opus","frameMs":20,"room":"crew-UUID","sessionId":"UUID"}
```

The bridge replies `{"type":"ready","sessionId":"..."}` after publishing the music track. Each subsequent binary frame is an 8-byte **big-endian signed monotonic microsecond timestamp** followed by one raw Opus packet. MediaCodec codec configuration output is omitted. Audio is processed in memory; files, token text and audio payloads are not logged or saved by the bridge.

Finite-file EOF sends `{"type":"end","sessionId":"..."}` after Android's Opus encoder tail. The bridge finishes all accepted fresh packets, drains its decoder/resampler and waits for the native AudioSource playout before replying `{"type":"finished","sessionId":"..."}` and closing. The server drain is bounded to 5 seconds; Android waits at most 7 seconds for the reply. Lease authorization stays active during draining, and disconnect/revocation aborts it. Explicit stop and live-capture termination discard stale queued audio immediately. A finished reply confirms bridge playout completion; it does not prove a remote speaker rendered the final sample.

The Android socket aborts when its outgoing queue exceeds 16 KiB. The bridge keeps at most 5 packets, drops old/replayed/stale frames and rejects obviously faster-than-realtime input. Python `AudioSource(..., queue_size_ms=100)` is explicit: the Python SDK's default is 1,000ms, despite the general publishing guide's 50ms explanation. That maximum is not a fixed latency. The first-frame clock anchor cannot measure absolute phone-to-server latency; end-to-end latency still needs two-device measurement. On handover/stop the source queue is cleared. WebSocket uses TCP and can stall in mobile network changes; dropped old audio is preferable to replaying a long backlog.

## Verification and release gates

Automated tests check JWT roles, signature, lifetime, room/session binding, expired/revoked lease handling, duplicate music room rejection, bounded queues, invalid payloads and cleanup. `test_opus.py` performs a real local Opus encode/decode roundtrip when PyAV is installed; a skipped codec test is not a verified roundtrip. A second codec test uses the pinned LiveKit SDK's real stereo `AudioSource`, raw frame queue and 128kbps publish options with a fake room connection. Fake publisher/room tests do not validate an actual LiveKit cloud call.

The current bridge run passed **32 tests, 0 skipped** in 1.87 seconds. Eleven added cases cover authenticated EOF, queue drain accounting, inflight-packet completion, wrong-session EOF, disconnect and lease expiry during drain. Two Android instrumentation tests are saved in `AudioLocalPlaybackTest.kt` for generated PCM WAV playback, independent gains, local-only status, natural EOF/foreground-service cleanup and explicit stop/restart. Their execution result is owned by the root build record; saving or compiling these tests is not an emulator/device pass.

Before a 20-person field session, run a two-device 15-minute test with a permitted local MP3: independent gains, DJ voice with music, self-return muted, Bluetooth microphone ON/OFF, projection revocation, notification stop, screen lock, network loss/recovery and lease expiry. Record p50/p95 voice/music latency, receiver skew, interruption duration, bytes and device battery change; no latency or battery target has been measured yet. Then test all six external sources on the exact app/OS versions. Bluetooth Classic music output may degrade when its microphone is used; LE Audio support depends on phone/headset compatibility. Read-only mute is not promised to release the Bluetooth microphone route automatically.

## Sources checked 2026-10-13

- [LiveKit Android v2.29.0 API](https://docs.livekit.io/reference/client-sdk-android/livekit-android-sdk/)
- [RemoteAudioTrack.setVolume](https://docs.livekit.io/reference/client-sdk-android/livekit-android-sdk/io.livekit.android.room.track/-remote-audio-track/set-volume.html)
- [Backend PCM publishing](https://docs.livekit.io/transport/media/publish/)
- [Python AudioSource queue API](https://docs.livekit.io/reference/python/livekit/rtc/audio_source.html)
- [Android playback capture](https://developer.android.com/media/platform/av-capture)
- [Android supported codecs](https://developer.android.com/media/platform/supported-formats)
- [Bluetooth microphone quality limits](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/overview)

## History

- 2026-10-13 v0.1.0: real LiveKit room/mic APIs, independent remote gains, local decoder/playback, UID-filtered consented playback capture, Opus upload and signed/active lease checks implemented. Credentials, six music-app compatibility, physical-device quality and cloud publishing remain unverified.
- 2026-10-13 verification: Python bridge tests **21 passed**, including real PyAV Opus and pinned LiveKit native `AudioSource` frame handling. The initial Android compile attempt failed before compilation while downloading Gradle; the root agent owns the subsequent Android build and device verification. No cloud room was contacted during these tests.
- 2026-10-13 EOF integration: bridge tests **32 passed, 0 skipped**; finite-file encoder/local AudioTrack tail and session-bound bridge acknowledgement added. Normal EOF waits for queued playout; explicit stop, socket failure and lease expiry retain immediate cleanup. Foreground start requests now report their own completion/failure, and MediaProjection creation rejects a null result before use. Two local Android PCM pipeline instrumentation tests added for root execution. No microphone, external-app capture, audible quality or cloud fanout result is inferred from the local bridge run.

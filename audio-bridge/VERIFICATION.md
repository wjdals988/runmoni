# Audio verification 2026-10-13

Implementation ownership: `app/src/main/java/com/runmoni/app/audio/`, `audio-bridge/`, `docs/AUDIO.md`. No Git commit was made by the audio agent; the root agent owns commits and the app build.

## Executed checks

1. Bridge contract tests using backend test runtime: **19 passed, 1 skipped**. Skip was the unavailable PyAV codec.
2. Project-local PyAV 16.0.1 installed; codec roundtrip included: **20 passed**. First test execution approval review timed out; one retry succeeded. No action remains blocked by that review.
3. All pinned `requirements.txt` installed into ignored `.test-deps/`; real LiveKit 1.0.23 native stereo AudioSource and livekit-api 1.0.7 publish/token options included: **21 passed**. The room network connection is deliberately fake; audio codec and native PCM queue are real.
4. `:app:compileDebugKotlin` initially attempted before root's exclusive build instruction. It failed while downloading Gradle 8.13 with a network socket exception, before Kotlin compilation. No further Gradle invocation was made by this agent. Root receives any Kotlin corrections separately.
5. EOF integration regression run: **32 passed, 0 skipped, 1 dependency deprecation warning in 1.87s**. Eleven added cases verify valid/wrong-session EOF, malformed EOF control frames, dropped-frame queue accounting, inflight packet completion, disconnect and lease expiry during drain. The native LiveKit source test now calls `MusicPublisher.finish()` and confirms `queued_duration == 0`. No actual room network connection occurred.
6. Added `app/src/androidTest/java/com/runmoni/app/audio/AudioLocalPlaybackTest.kt` with 2 instrumented pipeline cases for generated cache WAV playback. Natural EOF checks at least 1,750ms elapsed for a 2-second file; stop/restart uses an 8-second first file and a 2-second replacement. These tests are pending root execution at this record's update; emulator audio output can validate control flow without establishing audible quality.

Reproduce the Python checks with an isolated Python environment and `python -m pytest -q tests` in `audio-bridge/`. `run_tests.py` optionally puts ignored project-local `.test-deps/` first; it does not contact the network or read cloud credentials. Dependency deprecation/cache permission warnings did not cause test failures; the runner now disables pytest's cache provider.

## Still not verified

- LiveKit cloud credentials / actual room music fanout / two physical phones.
- Android MediaCodec encoder packets from real hardware (PyAV packets alone are not hardware encoder evidence).
- PlaybackCapture for YouTube Music, Spotify, Melon, Genie, FLO and Samsung Music, including screen lock and Bluetooth microphone modes.
- Battery drain, measured end-to-end audio latency, recipient synchronization and 20-person field performance.
- Public music distribution rights or store eligibility. A successful private technical capture does not establish either.

## Review corrections made

- Identical microphone/custom-audio mixing was excluded because independent music/voice gains are required.
- Explicit AudioSource queue cap replaces the Python SDK's default 1,000ms queue.
- Music generation guards prevent a canceled previous source's completion/error callback from stopping a new source.
- Finite-file EOF pads the final partial encoder frame by less than 20ms, drains codec output and waits for the local AudioTrack tail. Session-bound `end`/`finished` control messages drain the bridge within 5s, with a 7s Android acknowledgement deadline. Native bridge completion is not proof of final receiver speaker output.
- Draining counts dropped/stale packets correctly so an empty queue cannot deadlock `join()`. Authorization and peer-disconnect tasks remain active while EOF drains; explicit stop still clears the native source queue.
- Each foreground-service start uses a distinct completion request; a denied foreground start is returned to the controller. Intentional shutdown belongs to the service instance, avoiding a shared stop flag across replacement instances.
- `onBroadcastStopped(leaseId)` snapshots the exact old lease before clearing it; UI/API cleanup cannot mistake a new lease for the old one.
- Android sender rejects oversized queued output; server rejects wrong-room tokens, non-realtime/replayed timestamps and duplicate music room publishers.
- Local development WebSocket allowance checks the parsed hostname exactly, rather than accepting a `127.0.0.1...` string prefix.
- Incoming room audio is excluded from the selected app UID capture; the DJ's own delayed music return is muted.
- No bearer URL query strings, audio files or PCM/token dumps are written by the bridge.

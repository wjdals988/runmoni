"""Real codec roundtrip, independent of a provisioned LiveKit cloud room."""
import struct
import asyncio
import time
from uuid import uuid4

import pytest


def test_opus_decodes_to_stereo_pcm_for_audio_source():
    av = pytest.importorskip("av")
    encoder = av.CodecContext.create("libopus", "w")
    encoder.sample_rate = 48000
    encoder.layout = "stereo"
    encoder.format = "s16"
    encoder.bit_rate = 128000
    encoder.open()
    frame = av.AudioFrame(format="s16", layout="stereo", samples=960)
    frame.sample_rate = 48000
    frame.planes[0].update(b"\x00" * 3840)
    packets = list(encoder.encode(frame)) + list(encoder.encode(None))
    decoder = av.CodecContext.create("opus", "r")
    decoder.extradata = b"OpusHead" + struct.pack("<BBHIhB", 1, 2, 0, 48000, 0, 0)
    decoder.open()
    resampler = av.AudioResampler(format="s16", layout="stereo", rate=48000)
    outputs = [converted for packet in packets for decoded in decoder.decode(av.Packet(bytes(packet)))
               for converted in resampler.resample(decoded)]
    assert outputs
    assert outputs[0].sample_rate == 48000
    assert len(outputs[0].layout.channels) == 2
    assert len(bytes(outputs[0].planes[0])[:outputs[0].samples * 4]) == outputs[0].samples * 4


def test_pinned_livekit_sdk_music_options_source_and_codec(monkeypatch):
    av = pytest.importorskip("av")
    rtc = pytest.importorskip("livekit.rtc")
    from protocol import Lease
    from server import MusicPublisher, Settings

    class LocalParticipant:
        async def publish_track(self, track, options):
            assert track.name == "runmoni-music"
            assert options.audio_encoding.max_bitrate == 128000
            assert options.source == rtc.TrackSource.SOURCE_SCREENSHARE_AUDIO
            assert options.dtx is False

    class FakeRoom:
        local_participant = LocalParticipant()

        async def connect(self, url, token):
            assert url == "wss://test.invalid"
            assert len(token.split(".")) == 3

        async def disconnect(self):
            pass

    monkeypatch.setattr(rtc, "Room", FakeRoom)
    lease = Lease(str(uuid4()), f"crew-{uuid4()}", str(uuid4()), str(uuid4()), time.time() + 900)
    settings = Settings("wss://test.invalid", "dev-key", "dev-secret-" * 4)

    async def exercise():
        publisher = MusicPublisher(settings, lease)
        try:
            await publisher.connect()
            encoder = av.CodecContext.create("libopus", "w")
            encoder.sample_rate, encoder.layout, encoder.format, encoder.bit_rate = 48000, "stereo", "s16", 128000
            encoder.open()
            frame = av.AudioFrame(format="s16", layout="stereo", samples=960)
            frame.sample_rate = 48000
            frame.planes[0].update(b"\x00" * 3840)
            packets = list(encoder.encode(frame)) + list(encoder.encode(None))
            for packet in packets:
                await publisher.publish(bytes(packet))
            assert publisher.source.num_channels == 2
            assert publisher.source.sample_rate == 48000
            await publisher.finish()
            assert publisher.source.queued_duration == 0
        finally:
            await publisher.close()

    asyncio.run(exercise())

package com.runmoni.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer

internal class MusicFilePump {
    @Volatile private var player: AudioTrack? = null
    @Volatile private var volume = 0.7f
    @Volatile private var stopped = false

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        runCatching { player?.setVolume(volume) }
    }

    /** Interrupt a blocking AudioTrack.write without releasing a track still owned by its worker. */
    fun stop() {
        stopped = true
        runCatching { player?.pause() }
        runCatching { player?.flush() }
    }

    suspend fun run(context: Context, uri: Uri, encoder: OpusMusicEncoder?) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var track: AudioTrack? = null
        try {
            extractor.setDataSource(context, uri, null)
            val audioIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("오디오가 있는 음악 파일을 선택해 주세요")
            extractor.selectTrack(audioIndex)
            val inputFormat = extractor.getTrackFormat(audioIndex)
            val mime = checkNotNull(inputFormat.getString(MediaFormat.KEY_MIME))
            require(inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) in 1..2) { "모노/스테레오 음악 파일만 지원합니다" }
            inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(48_000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(19_200, AudioTrack.getMinBufferSize(48_000,
                    AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)))
                .build()
            require(track.state == AudioTrack.STATE_INITIALIZED) { "음악 출력 장치를 초기화하지 못했습니다" }
            player = track
            currentCoroutineContext().ensureActive()
            check(!stopped) { "음악 재생이 종료되었습니다" }
            track.setVolume(volume)
            track.play()
            var inputEnded = false
            var outputEnded = false
            var normalizer = PcmNormalizer(inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            val info = MediaCodec.BufferInfo()
            val origin = SystemClock.elapsedRealtimeNanos() / 1000
            var outputFrames = 0L
            while (!outputEnded) {
                currentCoroutineContext().ensureActive()
                if (!inputEnded) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = checkNotNull(decoder.getInputBuffer(index))
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val format = decoder.outputFormat
                    val pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    require(pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) { "이 기기의 음악 디코더 PCM 형식은 파일럿에서 지원하지 않습니다" }
                    normalizer = PcmNormalizer(format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                } else if (index >= 0) {
                    try {
                        if (info.size > 0) {
                            val buffer: ByteBuffer = checkNotNull(decoder.getOutputBuffer(index))
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val decoded = ByteArray(info.size)
                            buffer.get(decoded)
                            val pcm = normalizer.convert(decoded)
                            val timestamp = origin + outputFrames * 1_000_000 / 48_000
                            // Playback blocks to pace the finite file in realtime, then the same PCM enters the encoder.
                            var offset = 0
                            while (offset < pcm.size) {
                                currentCoroutineContext().ensureActive()
                                val written = track.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
                                check(written > 0) { "음악 출력이 중단되었습니다" }
                                offset += written
                            }
                            encoder?.write(pcm, timestamp)
                            outputFrames += pcm.size / 4
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally {
                        decoder.releaseOutputBuffer(index, false)
                    }
                }
            }
            // EOF is different from an explicit stop: preserve the final encoder packet and local sink tail.
            encoder?.finish()
            withTimeout(5_000) {
                while ((track.playbackHeadPosition.toLong() and 0xffffffffL) < outputFrames) {
                    currentCoroutineContext().ensureActive()
                    delay(10)
                }
            }
        } finally {
            player = null
            runCatching { track?.stop() }
            track?.release()
            runCatching { decoder?.stop() }
            decoder?.release()
            extractor.release()
        }
    }
}

package com.runmoni.app.audio

import android.media.MediaCodec
import android.media.MediaFormat
import java.io.Closeable

internal class OpusMusicEncoder(private val onPacket: (ByteArray, Long) -> Unit) : Closeable {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
    private val info = MediaCodec.BufferInfo()
    private val pending = ByteArray(FRAME_BYTES)
    private var pendingSize = 0
    private var samplesSent = 0L
    private var originMicros = 0L
    private var closed = false
    private var finished = false

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, 48_000, 2)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME_BYTES)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (failure: Exception) {
            codec.release()
            throw failure
        }
    }

    fun write(pcm: ByteArray, timestampMicros: Long) {
        check(!closed && !finished)
        if (originMicros == 0L) originMicros = timestampMicros
        var offset = 0
        while (offset < pcm.size) {
            val count = minOf(FRAME_BYTES - pendingSize, pcm.size - offset)
            pcm.copyInto(pending, pendingSize, offset, offset + count)
            pendingSize += count
            offset += count
            if (pendingSize == FRAME_BYTES) {
                val inputIndex = codec.dequeueInputBuffer(100_000)
                check(inputIndex >= 0) { "음악 인코더가 지연되었습니다" }
                val buffer = checkNotNull(codec.getInputBuffer(inputIndex))
                buffer.clear()
                buffer.put(pending)
                codec.queueInputBuffer(inputIndex, 0, FRAME_BYTES, samplesSent * 1_000_000 / 48_000, 0)
                samplesSent += SAMPLES_PER_FRAME
                pendingSize = 0
                drain()
            }
        }
    }

    /** Finite-file EOF pads at most 20ms and drains codec latency; explicit close still discards stale audio. */
    fun finish() {
        check(!closed)
        if (finished) return
        finished = true
        if (pendingSize > 0) {
            pending.fill(0, pendingSize)
            val index = codec.dequeueInputBuffer(100_000)
            check(index >= 0) { "음악 인코더가 지연되었습니다" }
            checkNotNull(codec.getInputBuffer(index)).apply { clear(); put(pending) }
            codec.queueInputBuffer(index, 0, FRAME_BYTES, samplesSent * 1_000_000 / 48_000, 0)
            samplesSent += SAMPLES_PER_FRAME
            pendingSize = 0
            drain()
        }
        if (samplesSent == 0L) return
        val index = codec.dequeueInputBuffer(100_000)
        check(index >= 0) { "음악 인코더가 지연되었습니다" }
        codec.queueInputBuffer(index, 0, 0, samplesSent * 1_000_000 / 48_000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        val deadline = System.nanoTime() + 2_000_000_000L
        while (System.nanoTime() < deadline) {
            if (drain(10_000)) return
        }
        throw IllegalStateException("음악 인코더 종료가 지연되었습니다")
    }

    private fun drain(timeoutUs: Long = 0): Boolean {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, timeoutUs)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
            if (index < 0) return false
            try {
                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    val buffer = checkNotNull(codec.getOutputBuffer(index))
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val packet = ByteArray(info.size)
                    buffer.get(packet)
                    onPacket(packet, originMicros + info.presentationTimeUs)
                }
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
            } finally {
                codec.releaseOutputBuffer(index, false)
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        // Live broadcasting intentionally discards an unfinished frame on stop, rather than replaying stale audio.
        runCatching { codec.stop() }
        codec.release()
    }

    companion object {
        const val SAMPLES_PER_FRAME = 960
        const val FRAME_BYTES = SAMPLES_PER_FRAME * 2 * 2
    }
}

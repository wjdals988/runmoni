package com.runmoni.app.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Streaming linear resampler for the pilot. Keep interpolation state across decoder output buffers. */
internal class PcmNormalizer(private val inputRate: Int, private val inputChannels: Int) {
    private var previous: ShortArray? = null
    private var nextPosition = 0.0
    private var baseIndex = 0L

    init {
        require(inputRate in 8_000..192_000 && inputChannels in 1..2) {
            "파일럿 음악은 모노/스테레오 PCM만 지원합니다"
        }
    }

    fun convert(input: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frames = buffer.remaining() / inputChannels
        if (frames == 0) return ByteArray(0)
        val output = ArrayList<Short>((frames * 48_000L / inputRate * 2 + 8).toInt())
        for (index in 0 until frames) {
            val left = buffer.get()
            val right = if (inputChannels == 2) buffer.get() else left
            val current = shortArrayOf(left, right)
            val prev = previous
            if (prev != null) {
                val leftIndex = baseIndex - 1
                while (nextPosition <= baseIndex) {
                    if (nextPosition >= leftIndex) {
                        val fraction = (nextPosition - leftIndex).coerceIn(0.0, 1.0)
                        for (channel in 0..1) {
                            output.add((prev[channel] + (current[channel] - prev[channel]) * fraction)
                                .roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                        }
                    }
                    nextPosition += inputRate / 48_000.0
                }
            }
            previous = current
            baseIndex++
        }
        val result = ByteBuffer.allocate(output.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        output.forEach { result.putShort(it) }
        return result.array()
    }
}

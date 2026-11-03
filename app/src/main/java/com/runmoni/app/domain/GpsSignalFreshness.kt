package com.runmoni.app.domain

/** A delivered fix is not a permanent claim that GPS and current pace are live. */
object GpsSignalFreshness {
    const val STALE_AFTER_MILLIS = 15_000L

    fun measurementElapsedMillis(
        fixElapsedRealtimeNanos: Long,
        fixWallMillis: Long,
        nowWallMillis: Long,
        nowElapsedMillis: Long,
    ): Long? {
        val measured = if (fixElapsedRealtimeNanos > 0) fixElapsedRealtimeNanos / 1_000_000
        else {
            val age = nowWallMillis - fixWallMillis
            if (age < 0) return null
            nowElapsedMillis - age
        }
        return measured.takeIf { it >= 0 && it <= nowElapsedMillis }
    }

    fun isStale(lastSignalElapsedMillis: Long?, nowElapsedMillis: Long): Boolean =
        lastSignalElapsedMillis != null &&
            (nowElapsedMillis < lastSignalElapsedMillis || nowElapsedMillis - lastSignalElapsedMillis >= STALE_AFTER_MILLIS)
}

package com.runmoni.app.domain

import kotlin.math.*

/** Deterministic distance/time kernel. Network and audio cannot alter this state. */
class RunEngine {
    var distanceMeters: Double = 0.0; private set
    var activeDurationMillis: Long = 0; private set
    var segment: Int = 0; private set
    private var lastPoint: TrackPoint? = null
    private var lastClockMillis: Long? = null
    private val recent = ArrayDeque<Pair<Double, Long>>()
    val splits = mutableListOf<KmSplit>()
    private var previousSplitTime = 0L

    fun restore(distance: Double, active: Long, lastSegment: Int, restoredSplits: List<KmSplit>) {
        distanceMeters = distance
        activeDurationMillis = active
        segment = lastSegment + 1
        splits.clear(); splits.addAll(restoredSplits)
        previousSplitTime = splits.sumOf { it.durationMillis }
        lastPoint = null; lastClockMillis = null; recent.clear()
    }

    fun tick(monotonicMillis: Long, running: Boolean) {
        lastClockMillis?.let { before ->
            // A long sleep/kill is an unobserved gap, never invented exercise time.
            val delta = monotonicMillis - before
            if (running && delta in 0..5_000) activeDurationMillis += delta
        }
        lastClockMillis = monotonicMillis
    }

    fun breakSegment() { segment++; lastPoint = null; recent.clear() }

    fun addPoint(raw: TrackPoint): TrackPoint {
        val point = raw.copy(segment = segment)
        val before = lastPoint
        val accurate = point.accuracyMeters.isFinite() && point.accuracyMeters in 0f..30f &&
            point.latitude.isFinite() && point.longitude.isFinite() &&
            point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0
        if (!accurate) { breakSegment(); return point }
        if (before != null) {
            val deltaMillis = point.recordedAtMillis - before.recordedAtMillis
            if (deltaMillis <= 0 || deltaMillis > 60_000) {
                breakSegment(); lastPoint = point.copy(segment = segment)
                return lastPoint!!
            }
            val delta = haversineMeters(before.latitude, before.longitude, point.latitude, point.longitude)
            if (delta / (deltaMillis / 1000.0) > 8.0) {
                breakSegment(); lastPoint = point.copy(segment = segment); return lastPoint!!
            }
            val beforeDistance = distanceMeters
            distanceMeters += delta
            recent.addLast(delta to deltaMillis)
            while (recent.size > 10) recent.removeFirst()
            while ((splits.size + 1) * 1000.0 <= distanceMeters) {
                val boundary = (splits.size + 1) * 1000.0
                val fraction = if (delta > 0) ((boundary - beforeDistance) / delta).coerceIn(0.0, 1.0) else 1.0
                val atTime = (activeDurationMillis - deltaMillis * (1.0 - fraction)).toLong().coerceAtLeast(previousSplitTime)
                splits += KmSplit(splits.size + 1, atTime - previousSplitTime)
                previousSplitTime = atTime
            }
        }
        lastPoint = point
        return point
    }

    fun currentPaceSecondsPerKm(): Int? {
        val distance = recent.sumOf { it.first }
        val millis = recent.sumOf { it.second }
        return if (distance >= 5 && millis > 0) ((millis / 1000.0) / distance * 1000).roundToInt().coerceIn(60, 3600) else null
    }

    companion object {
        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val radians = Math.PI / 180.0
            val a = sin((lat2-lat1)*radians/2).pow(2) + cos(lat1*radians)*cos(lat2*radians)*sin((lon2-lon1)*radians/2).pow(2)
            return 6_371_000.0 * 2 * atan2(sqrt(a.coerceIn(0.0, 1.0)), sqrt((1-a).coerceIn(0.0, 1.0)))
        }
        fun averagePaceSecondsPerKm(distanceMeters: Double, activeMillis: Long): Int? =
            if (distanceMeters > 0) (activeMillis / distanceMeters).roundToInt() else null
    }
}

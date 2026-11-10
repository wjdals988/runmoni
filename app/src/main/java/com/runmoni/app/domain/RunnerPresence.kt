package com.runmoni.app.domain

import kotlin.math.floor
import kotlin.math.roundToInt

/** Only this rounded grid is ever sent for public discovery, never the original fix. */
data class RunnerPresenceCell(val latitudeE3: Int, val longitudeE3: Int) {
    init { require(latitudeE3 in -85_000..85_000 && longitudeE3 in -180_000..179_999) }
    val latitude: Double get() = latitudeE3 / 1000.0
    val longitude: Double get() = longitudeE3 / 1000.0
    val band: RunnerPresenceBand get() = RunnerPresenceBand(floor(latitudeE3 / 1000.0).toInt(), floor(longitudeE3 / 1000.0).toInt())
    companion object {
        fun from(latitude: Double, longitude: Double): RunnerPresenceCell {
            require(latitude.isFinite() && longitude.isFinite() && latitude in -85.0..85.0 && longitude in -180.0..180.0)
            val lat = (latitude * 1000).roundToInt()
            val lon = (longitude * 1000).roundToInt().let { if (it >= 180_000) -180_000 else it }
            return RunnerPresenceCell(lat, lon)
        }
    }
}

data class RunnerPresenceBand(val latitude: Int, val longitude: Int)

/** Ephemeral consent for one locally started run; not an account/route identifier on the server. */
class RunnerShareSession internal constructor(val uid: String, val runId: String, internal val token: String, internal val generation: Long)

data class RunnerDiscoveryResult(
    val runners: List<LiveRunner> = emptyList(),
    val partial: Boolean = false,
    val requiresZoom: Boolean = false,
    val checkedAtMillis: Long = 0,
)

object RunnerPresencePolicy {
    const val PUBLISH_INTERVAL_MILLIS = 15_000L
    const val EXPIRY_MILLIS = 60_000L
    const val QUERY_FUTURE_MILLIS = 30_000L
    const val MAX_FIX_AGE_MILLIS = 30_000L
    const val MAX_MARKER_AGE_MILLIS = 45_000L
    const val MAX_PER_BAND = 100L
    const val MAX_BANDS = 9

    fun shareable(point: TrackPoint, now: Long): Boolean = point.latitude.isFinite() && point.longitude.isFinite() &&
        point.latitude in -85.0..85.0 && point.longitude in -180.0..180.0 && point.accuracyMeters.isFinite() &&
        point.accuracyMeters in 0f..100f && now - point.recordedAtMillis in 0..MAX_FIX_AGE_MILLIS

    /** Null means the map must be zoomed in. Querying the entire world is never a fallback. */
    fun bands(bounds: RouteMapBounds): List<RunnerPresenceBand>? {
        if (!listOf(bounds.south, bounds.north, bounds.west, bounds.east).all { it.isFinite() } ||
            bounds.south > bounds.north || bounds.south < -90 || bounds.north > 90 ||
            bounds.west !in -180.0..180.0 || bounds.east !in -180.0..180.0) return null
        val longitudeSpan = if (bounds.crossesAntimeridian) 360 - bounds.west + bounds.east else bounds.east - bounds.west
        if (bounds.north - bounds.south > 2 || longitudeSpan !in 0.0..2.0) return null
        val latitudes = floor(bounds.south.coerceAtLeast(-85.0)).toInt()..floor(bounds.north.coerceAtMost(85.0)).toInt()
        val longitudes = if (bounds.crossesAntimeridian) {
            (floor(bounds.west).toInt()..179).toList() + (-180..floor(bounds.east).toInt()).toList()
        } else (floor(bounds.west).toInt()..floor(bounds.east).toInt()).toList()
        val result = latitudes.flatMap { lat -> longitudes.map { lon -> RunnerPresenceBand(lat, if (lon == 180) -180 else lon) } }.distinct()
        return result.takeIf { it.size in 1..MAX_BANDS }
    }

    fun fresh(updatedAt: Long, expiresAt: Long, now: Long): Boolean = expiresAt > now &&
        now - updatedAt in -15_000L..MAX_MARKER_AGE_MILLIS
}

package com.runmoni.app.domain

/** Native uses 512 logical pixels per world tile; RouteViewport and the
 * bounded raster provider use 256. Preserve the same visible geography. */
internal object VectorMapCoordinates {
    fun nativeZoom(routeZoom: Double) = (routeZoom - 1.0).coerceIn(0.0, 17.0)
    fun viewport(latitude: Double, longitude: Double, nativeZoom: Double): RouteViewport? {
        if (!latitude.isFinite() || !longitude.isFinite() || !nativeZoom.isFinite() ||
            latitude !in -90.0..90.0 || nativeZoom !in 0.0..17.0) return null
        return RouteViewport(Mercator.x(longitude), Mercator.y(latitude), nativeZoom + 1.0)
    }
    // A browse starting point, never a location fix or a published runner.
    val seoulOverview = RouteMapCenter(37.5665, 126.9780, 12.0)
}

internal enum class VectorMapFailure { INITIALIZATION, STYLE, RENDERER }

/** Quiet map surfaces leave the genuine route and runner artwork in front. */
internal object RunningMapColors {
    val ground = 0xFFF5F7F4.toInt()
    val road = 0xFFFFFFFF.toInt()
    val park = 0xFFE0EAD9.toInt()
    val water = 0xFFD1E4E8.toInt()
    val ink = 0xFF344B53.toInt()
    val secondary = 0xFF50666D.toInt()
    val route = 0xFF206A75.toInt()
    val roadEdge = 0xFFDAE1DC.toInt()
}

/** A failed background read must neither become a repeated warning nor refresh
 * the age of previously confirmed public markers. The caller retains the list. */
internal fun discoveryReadFailed(previous: RunnerDiscoveryUi, userInitiated: Boolean): RunnerDiscoveryUi =
    previous.copy(loading = false, error = if (userInitiated)
        "새로고침하지 못했어요. 연결을 확인한 뒤 다시 시도해 주세요." else null)

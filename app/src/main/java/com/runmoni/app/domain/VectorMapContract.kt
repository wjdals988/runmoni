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

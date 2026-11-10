package com.runmoni.app.domain

import kotlin.math.*

/** Web Mercator coordinates, not a locally invented street/grid background. */
data class RouteMapPoint(val point: TrackPoint, val x: Double, val y: Double)
data class RouteGeometry(val segments: List<List<RouteMapPoint>>) {
    val points: List<RouteMapPoint> get() = segments.flatten()

    companion object {
        fun from(points: List<TrackPoint>): RouteGeometry {
            val ambiguous = points.groupingBy { it.sequence }.eachCount().filterValues { it > 1 }.keys
            val usable = points.filter { it.validForMap() && it.sequence !in ambiguous }
            if (usable.isEmpty()) return RouteGeometry(emptyList())
            // Cut the empty largest longitude arc, keeping a crossing of180deg
            // close together rather than fitting almost the entire earth.
            val xs = usable.map { Mercator.x(it.longitude) }.distinct().sorted()
            val origin = xs.indices.maxByOrNull { i -> (if (i == xs.lastIndex) xs.first() + 1 else xs[i + 1]) - xs[i] }
                ?.let { xs[(it + 1) % xs.size] } ?: xs.first()
            val groups = mutableListOf<MutableList<RouteMapPoint>>()
            var previous: TrackPoint? = null
            points.sortedBy { it.sequence }.forEach { p ->
                if (!p.validForMap() || p.sequence in ambiguous) {
                    previous = null
                } else {
                    val before = previous
                    if (before == null || p.segment != before.segment || p.sequence.toLong() != before.sequence.toLong() + 1 ||
                        p.recordedAtMillis < before.recordedAtMillis) groups.add(mutableListOf())
                    val x = Mercator.x(p.longitude).let { if (it < origin) it + 1 else it }
                    groups.last() += RouteMapPoint(p, x, Mercator.y(p.latitude))
                    previous = p
                }
            }
            return RouteGeometry(groups)
        }
    }
}

fun TrackPoint.validForMap(): Boolean = sequence >= 0 && segment >= 0 &&
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
    accuracyMeters.isFinite() && accuracyMeters in 0f..30f

object Mercator {
    const val MAX_LATITUDE = 85.0511287798066
    fun x(longitude: Double): Double = ((longitude + 180.0) / 360.0).let { if (it == 1.0) 0.0 else it }
    fun y(latitude: Double): Double {
        val radians = Math.toRadians(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE))
        return ((1 - ln(tan(radians) + 1 / cos(radians)) / Math.PI) / 2).coerceIn(0.0, 1.0)
    }
    fun longitude(x: Double): Double = ((x - floor(x)) * 360) - 180
    fun latitude(y: Double): Double = Math.toDegrees(atan(sinh(Math.PI * (1 - 2 * y.coerceIn(0.0, 1.0)))))
    fun around(x: Double, center: Double): Double = x + round(center - x)
}

data class MapTileKey(val zoom: Int, val x: Int, val y: Int) {
    init { require(zoom in 0..18 && x in 0 until (1 shl zoom) && y in 0 until (1 shl zoom)) }
    val cacheKey: String get() = "$zoom-$x-$y"
}
data class RouteTilePlacement(val tile: MapTileKey, val left: Double, val top: Double, val size: Double)
data class RouteScreenPoint(val x: Double, val y: Double)

/** An actual location supplied by the host; invalid fixes are ignored by UI. */
data class RouteMapCenter(val latitude: Double, val longitude: Double, val zoom: Double = 15.5) {
    fun valid(): Boolean = latitude.isFinite() && longitude.isFinite() && zoom.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 && zoom in 0.0..18.0
    fun viewport(): RouteViewport? = if (valid()) RouteViewport(Mercator.x(longitude), Mercator.y(latitude), zoom) else null
}

/** Visible geography, including a date-line crossing rather than a huge
 * longitude box. A whole-world viewport uses west=-180/east=180. */
data class RouteMapBounds(val south: Double, val west: Double, val north: Double, val east: Double,
    val crossesAntimeridian: Boolean = west > east) {
    fun contains(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in south..north || longitude !in -180.0..180.0) return false
        fun within(value: Double) = if (crossesAntimeridian) value >= west || value <= east else value in west..east
        return within(longitude) || when (longitude) {
            180.0 -> within(-180.0)
            -180.0 -> within(180.0)
            else -> false
        }
    }
}

data class RouteViewport(val centerX: Double, val centerY: Double, val zoom: Double) {
    private fun scale(tileSize: Double) = tileSize * 2.0.pow(zoom)
    fun screen(x: Double, y: Double, width: Double, height: Double, tileSize: Double = 256.0): RouteScreenPoint =
        RouteScreenPoint(width / 2 + (Mercator.around(x, centerX) - centerX) * scale(tileSize), height / 2 + (y - centerY) * scale(tileSize))
    fun pan(dx: Double, dy: Double, tileSize: Double = 256.0): RouteViewport {
        if (!dx.isFinite() || !dy.isFinite() || !tileSize.isFinite() || tileSize <= 0) return this
        val x = centerX - dx / scale(tileSize)
        return copy(centerX = x - floor(x), centerY = (centerY - dy / scale(tileSize)).coerceIn(0.0, 1.0))
    }
    fun zoomAt(factor: Double, x: Double, y: Double, width: Double, height: Double, tileSize: Double = 256.0): RouteViewport {
        if (!factor.isFinite() || factor <= 0 || !x.isFinite() || !y.isFinite() || !width.isFinite() ||
            !height.isFinite() || width <= 0 || height <= 0 || !tileSize.isFinite() || tileSize <= 0) return this
        val next = (zoom + log2(factor)).coerceIn(0.0, 18.0)
        val oldScale = scale(tileSize)
        val newScale = tileSize * 2.0.pow(next)
        return RouteViewport(centerX + (x - width / 2) * (1 / oldScale - 1 / newScale),
            (centerY + (y - height / 2) * (1 / oldScale - 1 / newScale)).coerceIn(0.0, 1.0), next)
    }

    fun bounds(width: Double, height: Double, tileSize: Double = 256.0): RouteMapBounds? {
        if (!width.isFinite() || !height.isFinite() || width <= 0 || height <= 0 || !tileSize.isFinite() || tileSize <= 0 ||
            !centerX.isFinite() || !centerY.isFinite() || !zoom.isFinite()) return null
        val span = width / scale(tileSize)
        val halfHeight = height / (2 * scale(tileSize))
        val north = Mercator.latitude(centerY - halfHeight)
        val south = Mercator.latitude(centerY + halfHeight)
        if (span >= 1.0) return RouteMapBounds(south, -180.0, north, 180.0)
        val west = centerX - span / 2
        val east = centerX + span / 2
        return RouteMapBounds(south, Mercator.longitude(west), north, Mercator.longitude(east), floor(west) != floor(east))
    }

    /** Only rectangles intersecting the visible canvas. Lower raster resolution
     * on a huge display bounds requests without prefetching surrounding tiles. */
    fun tiles(width: Double, height: Double, tileSize: Double = 256.0, maximum: Int = 16): List<RouteTilePlacement> {
        if (width <= 0 || height <= 0 || tileSize <= 0) return emptyList()
        require(maximum >= 4)
        var rasterZoom = floor(zoom.coerceIn(0.0, 18.0)).toInt()
        while (true) {
            val count = 1 shl rasterZoom
            val drawnSize = tileSize * 2.0.pow(zoom - rasterZoom)
            val left = centerX * count - width / (2 * drawnSize)
            val top = centerY * count - height / (2 * drawnSize)
            val firstX = floor(left).toInt()
            val lastX = ceil(left + width / drawnSize).toInt() - 1
            val firstY = max(0, floor(top).toInt())
            val lastY = min(count - 1, ceil(top + height / drawnSize).toInt() - 1)
            val number = (lastX.toLong() - firstX + 1) * (lastY.toLong() - firstY + 1).coerceAtLeast(0)
            if (number <= maximum || rasterZoom == 0) {
                return buildList {
                    for (y in firstY..lastY) for (x in firstX..lastX) {
                        if (size >= maximum) break
                        add(RouteTilePlacement(MapTileKey(rasterZoom, Math.floorMod(x, count), y),
                            (x - left) * drawnSize, (y - top) * drawnSize, drawnSize))
                    }
                }
            }
            rasterZoom--
        }
    }

    companion object {
        fun fit(points: List<RouteMapPoint>, width: Double, height: Double, padding: Double = 24.0, tileSize: Double = 256.0): RouteViewport? {
            if (points.isEmpty() || width <= 0 || height <= 0 || tileSize <= 0) return null
            val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
            val availableWidth = (width - padding * 2).coerceAtLeast(1.0)
            val availableHeight = (height - padding * 2).coerceAtLeast(1.0)
            val scale = min(availableWidth / (maxX - minX).coerceAtLeast(1e-9), availableHeight / (maxY - minY).coerceAtLeast(1e-9))
            return RouteViewport((minX + maxX) / 2, (minY + maxY) / 2, log2(scale / tileSize).coerceIn(0.0, 18.0))
        }
        fun follow(point: RouteMapPoint) = RouteViewport(point.x, point.y, 16.0)
        fun world() = RouteViewport(.5, .5, 0.0)
    }
}

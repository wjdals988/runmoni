package com.runmoni.app.domain

/** The recorder's measured kilometer boundary on a specific accepted GPS edge. */
data class RunLapAnchor(val kilometer: Int, val beforeSequence: Int, val afterSequence: Int, val fraction: Double) {
    internal fun valid() = kilometer >= 0 && beforeSequence >= 0 && fraction.isFinite() && fraction in 0.0..1.0 &&
        if (kilometer == 0) beforeSequence == afterSequence && fraction == 0.0
        else afterSequence.toLong() == beforeSequence.toLong() + 1L
}

/** Clip the existing route by recorded edges. Keep gaps and rejected points from the full route. */
internal fun RouteGeometry.lapRoute(kilometer: Int, anchors: List<RunLapAnchor>): RouteGeometry? {
    if (kilometer < 1) return null
    val start = anchors.singleOrNull { it.kilometer == kilometer - 1 }?.takeIf(RunLapAnchor::valid) ?: return null
    val end = anchors.singleOrNull { it.kilometer == kilometer }?.takeIf(RunLapAnchor::valid) ?: return null
    val from = start.beforeSequence + start.fraction
    val to = end.beforeSequence + end.fraction
    if (from >= to) return null

    fun boundary(segment: List<RouteMapPoint>, anchor: RunLapAnchor): RouteMapPoint? {
        val index = segment.binarySearchBy(anchor.beforeSequence) { it.point.sequence }
        if (index < 0) return null
        val before = segment[index]
        if (anchor.beforeSequence == anchor.afterSequence) return before
        val after = segment.getOrNull(index + 1)?.takeIf { it.point.sequence == anchor.afterSequence } ?: return null
        val fraction = anchor.fraction
        if (fraction == 0.0) return before
        if (fraction == 1.0) return after
        val x = before.x + (Mercator.around(after.x, before.x) - before.x) * fraction
        val y = before.y + (after.y - before.y) * fraction
        return RouteMapPoint(before.point.copy(latitude = Mercator.latitude(y), longitude = Mercator.longitude(x)), x, y)
    }

    var foundStart = false
    var foundEnd = false
    val clipped = segments.mapNotNull { segment ->
        val first = boundary(segment, start)
        val last = boundary(segment, end)
        foundStart = foundStart || first != null
        foundEnd = foundEnd || last != null
        buildList {
            first?.let(::add)
            segment.filterTo(this) { it.point.sequence > from && it.point.sequence < to }
            last?.let(::add)
        }.takeIf { it.size >= 2 }
    }
    return if (foundStart && foundEnd && clipped.isNotEmpty()) RouteGeometry(clipped) else null
}

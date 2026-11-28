package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RunLapRouteTest {
    private fun point(sequence: Int, distance: Double, segment: Int = 0, accuracy: Float = 5f) =
        TrackPoint(sequence, 0.0, Math.toDegrees(distance / 6_371_000), accuracy, sequence * 45_000L, segment)

    @Test fun kilometerAnchorsUseTheEdgesThatContributedToRecordedDistance() {
        val engine = RunEngine()
        val points = (0..4).map { index ->
            engine.tick(index * 45_000L, true)
            engine.addPoint(point(index, index * 300.0))
        }

        assertEquals(listOf(0, 1), engine.lapAnchors.map(RunLapAnchor::kilometer))
        val end = engine.lapAnchors.last()
        assertEquals(3, end.beforeSequence)
        assertEquals(4, end.afterSequence)
        assertEquals(1.0 / 3, end.fraction, 1e-8)
        val visible = requireNotNull(RouteGeometry.from(points).lapRoute(1, engine.lapAnchors)).segments.single()
        val first = visible.first().point
        val last = visible.last().point
        assertEquals(1_000.0, RunEngine.haversineMeters(first.latitude, first.longitude, last.latitude, last.longitude), .01)
        assertNull(RouteGeometry.from(points).lapRoute(1, emptyList()))
    }

    @Test fun clippingKeepsSeparateGpsSegmentsAndRejectsAnAnchorSpanningTheGap() {
        val points = listOf(point(0, 0.0), point(1, 300.0), point(2, 600.0, accuracy = 90f),
            point(3, 1_500.0, 1), point(4, 1_800.0, 1), point(5, 2_100.0, 1))
        val geometry = RouteGeometry.from(points)
        val start = RunLapAnchor(0, 0, 0, 0.0)
        val end = RunLapAnchor(1, 4, 5, .5)
        val selected = requireNotNull(geometry.lapRoute(1, listOf(start, end)))
        assertEquals(listOf(0, 1), selected.segments.map { it.first().point.segment })
        assertEquals(2, selected.segments.size)
        assertNull(geometry.lapRoute(1, listOf(start, RunLapAnchor(1, 1, 2, .5))))
        assertNull(geometry.lapRoute(1, listOf(start, end, end)))
    }

    @Test fun aLegacyRecoveryCannotInventTheStartOfASplit() {
        val engine = RunEngine()
        engine.restore(950.0, 300_000, 0, emptyList())
        val first = engine.addPoint(point(9, 0.0))
        engine.tick(345_000L, true)
        val second = engine.addPoint(point(10, 300.0))
        assertEquals(listOf(1), engine.lapAnchors.map(RunLapAnchor::kilometer))
        assertNull(RouteGeometry.from(listOf(first, second)).lapRoute(1, engine.lapAnchors))
    }
}

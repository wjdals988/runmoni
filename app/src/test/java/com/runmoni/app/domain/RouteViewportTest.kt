package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RouteViewportTest {
    private fun p(sequence: Int, lat: Double = 37.5665, lon: Double = 126.978, segment: Int = 0, accuracy: Float = 5f) =
        TrackPoint(sequence, lat, lon, accuracy, sequence.toLong() * 1000, segment)

    @Test fun realMercatorKnownEquatorAndRoundTrip() {
        assertEquals(.5, Mercator.x(0.0), 1e-12)
        assertEquals(.5, Mercator.y(0.0), 1e-12)
        for (lat in listOf(-80.0, 0.0, 37.5665, 80.0)) assertEquals(lat, Mercator.latitude(Mercator.y(lat)), 1e-9)
    }
    @Test fun polarLatitudeClampsAndNeverCreatesInfiniteTiles() {
        assertEquals(0.0, Mercator.y(90.0), 1e-9)
        assertEquals(1.0, Mercator.y(-90.0), 1e-9)
        assertEquals(Mercator.MAX_LATITUDE, Mercator.latitude(0.0), 1e-9)
    }
    @Test fun invalidAccuracyAndMissingSequenceNeverBridgeRoute() {
        val g = RouteGeometry.from(listOf(p(0), p(1, accuracy = 40f), p(2), p(4), p(5, segment = 1)))
        assertEquals(listOf(listOf(0), listOf(2), listOf(4), listOf(5)), g.segments.map { s -> s.map { it.point.sequence } })
        assertTrue(RouteGeometry.from(listOf(p(0, lat = Double.NaN), p(1, lon = 181.0), p(2, accuracy = Float.POSITIVE_INFINITY))).points.isEmpty())
    }
    @Test fun duplicateSequencesAreAmbiguousAndBreakBothConnections() {
        val g = RouteGeometry.from(listOf(p(0), p(1), p(1, lon = 127.0), p(2)))
        assertEquals(listOf(listOf(0), listOf(2)), g.segments.map { s -> s.map { it.point.sequence } })
    }
    @Test fun savedSequenceIsAuthoritativeAndSegmentReturnDoesNotJoinOlderGroup() {
        val g = RouteGeometry.from(listOf(p(3, segment = 0), p(1), p(0), p(2, segment = 1)))
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3)), g.segments.map { s -> s.map { it.point.sequence } })
    }
    @Test fun antimeridianCrossingFitsTheShortArcAndAllPoints() {
        val points = RouteGeometry.from(listOf(p(0, lat = 0.0, lon = 179.9), p(1, lat = 0.0, lon = -179.9))).points
        assertEquals(.2 / 360, points.maxOf { it.x } - points.minOf { it.x }, 1e-9)
        val view = RouteViewport.fit(points, 320.0, 240.0, 24.0)!!
        assertTrue(view.zoom > 9)
        points.forEach { val at = view.screen(it.x, it.y, 320.0, 240.0); assertTrue(at.x in 23.999..296.001 && at.y in 23.999..216.001) }
    }
    @Test fun fitPreservesTheEntireTallAndWideRoute() {
        val points = RouteGeometry.from(listOf(p(0, lat = 37.5, lon = 126.9), p(1, lat = 37.6, lon = 127.1))).points
        for ((width, height) in listOf(320.0 to 180.0, 180.0 to 320.0)) {
            val view = RouteViewport.fit(points, width, height, 24.0)!!
            points.forEach { val at = view.screen(it.x, it.y, width, height); assertTrue(at.x in 23.999..(width - 23.999) && at.y in 23.999..(height - 23.999)) }
        }
    }
    @Test fun zoomKeepsTheGestureFocusAtTheSameGeographicPosition() {
        val view = RouteViewport(.8, .4, 12.0)
        val at = view.screen(.8001, .4002, 400.0, 300.0)
        val zoomed = view.zoomAt(2.0, at.x, at.y, 400.0, 300.0)
        val after = zoomed.screen(.8001, .4002, 400.0, 300.0)
        assertEquals(at.x, after.x, 1e-7); assertEquals(at.y, after.y, 1e-7)
        assertEquals(13.0, zoomed.zoom, 1e-12)
    }
    @Test fun visibleTilesAreBoundedAndIntersectTheViewportEvenAtDateLine() {
        val tiles = RouteViewport(1.0001, .4, 14.5).tiles(1280.0, 720.0)
        assertTrue(tiles.isNotEmpty() && tiles.size <= 16)
        tiles.forEach { assertTrue(it.left < 1280 && it.top < 720 && it.left + it.size > 0 && it.top + it.size > 0) }
        assertTrue(tiles.any { it.tile.x < 10 })
    }
    @Test fun emptyRouteNeverInventsACenterOrSampleTrack() {
        assertNull(RouteViewport.fit(emptyList(), 320.0, 240.0))
        assertTrue(RouteGeometry.from(emptyList()).segments.isEmpty())
    }
    @Test fun oneFingerPanMovesTheGeographicContentWithTheFinger() {
        val view = RouteViewport(.8, .4, 14.0)
        val before = view.screen(.80001, .40002, 400.0, 300.0)
        val after = view.pan(48.0, -32.0).screen(.80001, .40002, 400.0, 300.0)
        assertEquals(before.x + 48, after.x, 1e-7)
        assertEquals(before.y - 32, after.y, 1e-7)
        assertEquals(view, view.pan(Double.NaN, 0.0))
        assertEquals(view, view.zoomAt(2.0, Double.NaN, 100.0, 400.0, 300.0))
        val focusX = 120.0; val focusY = 110.0
        val combined = view.zoomAt(2.0, focusX, focusY, 400.0, 300.0).pan(30.0, -20.0)
            .screen(.80001, .40002, 400.0, 300.0)
        assertEquals(focusX + (before.x - focusX) * 2 + 30, combined.x, 1e-7)
        assertEquals(focusY + (before.y - focusY) * 2 - 20, combined.y, 1e-7)
    }
    @Test fun visibleBoundsMatchAllFourScreenCornersAndCrossTheDateLine() {
        val view = RouteViewport(.9999, .4, 10.0)
        val bounds = view.bounds(400.0, 300.0)!!
        assertTrue(bounds.crossesAntimeridian)
        assertTrue(bounds.contains(Mercator.latitude(.4), 179.99))
        assertTrue(bounds.contains(Mercator.latitude(.4), -179.99))
        assertFalse(bounds.contains(Mercator.latitude(.4), 0.0))
        assertTrue(RouteMapBounds(-10.0, -180.0, 10.0, -170.0).contains(0.0, 180.0))
        assertTrue(RouteMapBounds(-10.0, 170.0, 10.0, 180.0).contains(0.0, -180.0))
        val northWest = view.screen(Mercator.x(bounds.west), Mercator.y(bounds.north), 400.0, 300.0)
        val southEast = view.screen(Mercator.x(bounds.east), Mercator.y(bounds.south), 400.0, 300.0)
        assertEquals(0.0, northWest.x, 1e-7); assertEquals(0.0, northWest.y, 1e-7)
        assertEquals(400.0, southEast.x, 1e-7); assertEquals(300.0, southEast.y, 1e-7)
    }
    @Test fun aWorldBrowseViewportHasNoInventedUserLocationAndRejectsInvalidHostCenter() {
        val world = RouteViewport.world().bounds(320.0, 480.0)!!
        assertEquals(-180.0, world.west, 0.0); assertEquals(180.0, world.east, 0.0)
        assertFalse(world.crossesAntimeridian)
        assertNull(RouteMapCenter(Double.NaN, 126.0).viewport())
        assertNull(RouteMapCenter(37.5, 181.0).viewport())
        assertEquals(15.5, RouteMapCenter(37.5, 126.0).viewport()!!.zoom, 0.0)
    }
}

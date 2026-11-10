package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RunnerPresenceTest {
    @Test fun publicLocationUsesOnlyRoundedCellsIncludingNegativeLatitudes() {
        val position = RunnerPresenceCell.from(37.566534, 126.978134)
        assertEquals(37567, position.latitudeE3); assertEquals(126978, position.longitudeE3)
        assertEquals(37.567, position.latitude, 1e-12)
        assertEquals(RunnerPresenceBand(37, 126), position.band)
        assertEquals(RunnerPresenceBand(-1, -1), RunnerPresenceCell.from(-.0006, -.0006).band)
    }
    @Test fun longitudeAtDateLineHasOneCanonicalPublicCell() {
        assertEquals(RunnerPresenceCell.from(0.0, -180.0), RunnerPresenceCell.from(0.0, 180.0))
        assertEquals(-180000, RunnerPresenceCell.from(0.0, 179.9999).longitudeE3)
    }
    @Test fun worldMapAndOversizedViewNeverDownloadGlobalPresence() {
        assertNull(RunnerPresencePolicy.bands(RouteMapBounds(-80.0, -180.0, 80.0, 180.0)))
        assertNull(RunnerPresencePolicy.bands(RouteMapBounds(37.0, 126.0, 40.0, 127.0)))
        assertNull(RunnerPresencePolicy.bands(RouteMapBounds(37.0, Double.NaN, 38.0, 127.0)))
    }
    @Test fun boundedViewQueriesAtMostNineBandsAndCrossesDatelineCorrectly() {
        assertEquals(9, RunnerPresencePolicy.bands(RouteMapBounds(36.0, 125.0, 38.0, 127.0))!!.size)
        assertEquals(setOf(RunnerPresenceBand(0,179),RunnerPresenceBand(0,-180)),
            RunnerPresencePolicy.bands(RouteMapBounds(.1,179.5,.2,-179.5,true))!!.toSet())
    }
    @Test fun staleInaccurateOrFutureFixCannotBeRepublishedAsCurrent() {
        val point = TrackPoint(1, 37.5, 127.0, 5f, 100000)
        assertTrue(RunnerPresencePolicy.shareable(point, 100000))
        assertTrue(RunnerPresencePolicy.shareable(point, 130000))
        assertFalse(RunnerPresencePolicy.shareable(point, 130001))
        assertFalse(RunnerPresencePolicy.shareable(point, 99999))
        assertFalse(RunnerPresencePolicy.shareable(point.copy(accuracyMeters = 101f), 100000))
        assertFalse(RunnerPresencePolicy.shareable(point.copy(latitude = Double.NaN), 100000))
    }
    @Test fun markerExpiryAndClockToleranceDoNotTurnCachedRowsIntoLiveRunners() {
        assertTrue(RunnerPresencePolicy.fresh(100000,160000,145000))
        assertFalse(RunnerPresencePolicy.fresh(100000,160000,145001))
        assertFalse(RunnerPresencePolicy.fresh(100000,145000,145000))
        assertTrue(RunnerPresencePolicy.fresh(115000,175000,100000))
        assertFalse(RunnerPresencePolicy.fresh(115001,175000,100000))
    }
}

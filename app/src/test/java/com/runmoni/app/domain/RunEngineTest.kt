package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RunEngineTest {
    private fun point(seq: Int, longitude: Double, time: Long, accuracy: Float = 5f) = TrackPoint(seq, 0.0, longitude, accuracy, time)
    @Test fun knownEquatorialDistance() { assertEquals(111.195, RunEngine.haversineMeters(0.0, 0.0, 0.0, 0.001), 0.01) }
    @Test fun pauseAndRecoveryDoNotBridgeDistance() {
        val engine = RunEngine()
        engine.addPoint(point(0, 0.0, 1000)); engine.addPoint(point(1, 0.00001, 2000))
        val before = engine.distanceMeters
        engine.breakSegment(); engine.addPoint(point(2, 1.0, 3000))
        assertEquals(before, engine.distanceMeters, 0.0001)
    }
    @Test fun jumpAndBadAccuracyDoNotReward() {
        val engine = RunEngine()
        engine.addPoint(point(0, 0.0, 1000)); engine.addPoint(point(1, 1.0, 2000))
        engine.addPoint(point(2, 0.0, 3000, 100f))
        assertEquals(0.0, engine.distanceMeters, 0.0001)
    }
    @Test fun pausedTimeIsExcludedAcrossALongManualPause() {
        val engine = RunEngine()
        engine.tick(1000, true); engine.tick(2000, true)
        engine.tick(3000, true) // Repository pause() counts time up to the pause.
        engine.tick(103000, false) // Repository resume() excludes the whole paused interval.
        engine.tick(104000, true)
        assertEquals(3000L, engine.activeDurationMillis)
    }
    @Test fun averageUsesTotalTimeAndDistance() { assertEquals(360, RunEngine.averagePaceSecondsPerKm(5000.0, 1_800_000)) }
    @Test fun recoveryStartsNewSegment() {
        val engine = RunEngine(); engine.restore(500.0, 100000, 2, emptyList())
        assertEquals(3, engine.segment)
        engine.addPoint(point(5, 1.0, 1000)); assertEquals(500.0, engine.distanceMeters, 0.0)
    }

    @Test fun anEightSecondSchedulingDelayInTheSameLiveSessionStillCounts() {
        val engine = RunEngine()
        engine.tick(1000, true)
        engine.tick(9000, true)
        assertEquals(8000L, engine.activeDurationMillis)
    }

    @Test fun processRecoveryDropsDowntimeButCountsLongIntervalsAfterTheNewBaseline() {
        val engine = RunEngine()
        engine.tick(1000, true); engine.tick(9000, true)
        engine.restore(100.0, engine.activeDurationMillis, engine.segment, emptyList())
        engine.tick(100000, true) // The process gap has no clock baseline.
        assertEquals(8000L, engine.activeDurationMillis)
        engine.tick(108000, true)
        assertEquals(16000L, engine.activeDurationMillis)
    }

    @Test fun currentSpeedUsesWeightedAcceptedRouteDistanceAndFixTime() {
        val engine = RunEngine()
        engine.addPoint(point(0, 0.0, 1000))
        assertNull(engine.currentSpeedMetersPerSecond())
        engine.addPoint(point(1, 0.00001, 2000))
        engine.addPoint(point(2, 0.00004, 4000))
        val expected = RunEngine.haversineMeters(0.0, 0.0, 0.0, 0.00004) / 3.0
        assertEquals(expected, engine.currentSpeedMetersPerSecond()!!, 0.00001)
    }

    @Test fun stationaryAcceptedFixesCanShowZeroSpeedWithoutInventingAPace() {
        val engine = RunEngine()
        engine.addPoint(point(0, 0.0, 1000)); engine.addPoint(point(1, 0.0, 2000))
        assertEquals(0.0, engine.currentSpeedMetersPerSecond()!!, 0.0)
        assertNull(engine.currentPaceSecondsPerKm())
    }

    @Test fun badAccuracyOrInvalidCoordinatesClearTheCurrentSpeed() {
        val engine = RunEngine()
        engine.addPoint(point(0, 0.0, 1000)); engine.addPoint(point(1, 0.00002, 2000))
        assertNotNull(engine.currentSpeedMetersPerSecond())
        engine.addPoint(point(2, 0.00003, 3000, 100f))
        assertNull(engine.currentSpeedMetersPerSecond())
        engine.addPoint(point(3, 0.00004, 4000)); engine.addPoint(point(4, 0.00005, 5000))
        assertNotNull(engine.currentSpeedMetersPerSecond())
        engine.addPoint(point(5, Double.NaN, 6000))
        assertNull(engine.currentSpeedMetersPerSecond())
    }

    @Test fun segmentBreakJumpAndRestoreClearSpeedUntilNewRouteSamplesArrive() {
        val engine = RunEngine()
        engine.addPoint(point(0, 0.0, 1000)); engine.addPoint(point(1, 0.00002, 2000))
        engine.breakSegment()
        assertNull(engine.currentSpeedMetersPerSecond())
        engine.addPoint(point(2, 0.00003, 3000)); engine.addPoint(point(3, 0.00004, 4000))
        assertNotNull(engine.currentSpeedMetersPerSecond())
        engine.addPoint(point(4, 1.0, 5000))
        assertNull(engine.currentSpeedMetersPerSecond())
        engine.addPoint(point(5, 1.00001, 6000))
        assertNotNull(engine.currentSpeedMetersPerSecond())
        engine.restore(engine.distanceMeters, 10000, engine.segment, emptyList())
        assertNull(engine.currentSpeedMetersPerSecond())
    }
}

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
    @Test fun pausedTimeAndUnobservedGapAreExcluded() {
        val engine = RunEngine()
        engine.tick(1000, true); engine.tick(2000, true); engine.tick(3000, false)
        engine.tick(103000, true); engine.tick(104000, true)
        assertEquals(2000L, engine.activeDurationMillis)
    }
    @Test fun averageUsesTotalTimeAndDistance() { assertEquals(360, RunEngine.averagePaceSecondsPerKm(5000.0, 1_800_000)) }
    @Test fun recoveryStartsNewSegment() {
        val engine = RunEngine(); engine.restore(500.0, 100000, 2, emptyList())
        assertEquals(3, engine.segment)
        engine.addPoint(point(5, 1.0, 1000)); assertEquals(500.0, engine.distanceMeters, 0.0)
    }
}

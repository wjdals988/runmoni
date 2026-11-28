package com.runmoni.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunLapCueTest {
    private val initial = ActiveRun(id = "running", phase = RunPhase.RUNNING)

    @Test fun defaultCueSpeaksDistanceOnceAtTheCompletedKilometer() {
        val tracker = RunLapCueTracker().apply { start(initial) }
        assertNull(tracker.observe(initial.copy(distanceMeters = 999.99), true, true, false, false))
        val oneKm = initial.copy(distanceMeters = 1_000.0)
        assertEquals(RunLapCue(1, "1킬로미터 달렸어요", false), tracker.observe(oneKm, true, true, false, false))
        assertNull(tracker.observe(oneKm, true, true, false, false))
        assertNull(tracker.observe(oneKm.copy(distanceMeters = 1_090.0), true, true, false, false))
    }

    @Test fun paceUsesOnlyTheCompletedSplitAndVibrationIsIndependent() {
        val tracker = RunLapCueTracker().apply { start(initial) }
        val oneKm = initial.copy(distanceMeters = 1_010.0, activeDurationMillis = 344_000,
            lastCompletedSplit = KmSplit(1, 342_750), currentPaceSecondsPerKm = 303)
        assertEquals(RunLapCue(1, "1킬로미터 달렸어요. 이번 1킬로미터는 5분 42초예요.", true),
            tracker.observe(oneKm, true, true, true, true))

        val twoKm = oneKm.copy(distanceMeters = 2_000.0, activeDurationMillis = 680_000,
            lastCompletedSplit = KmSplit(2, 336_000))
        assertEquals(RunLapCue(2, null, true), tracker.observe(twoKm, true, false, true, true))
    }

    @Test fun staleMissingOrInvalidSplitsFallBackToDistanceOnly() {
        val oneKm = initial.copy(distanceMeters = 1_060.0, activeDurationMillis = 420_000)
        for (split in listOf(null, KmSplit(0, 340_000), KmSplit(1, -1), KmSplit(1, 0),
            KmSplit(1, 999), KmSplit(1, 420_001), KmSplit(2, 300_000))) {
            val tracker = RunLapCueTracker().apply { start(initial) }
            assertEquals(RunLapCue(1, "1킬로미터 달렸어요", false),
                tracker.observe(oneKm.copy(lastCompletedSplit = split), true, true, true, false))
        }
    }

    @Test fun mutedPausedAndInactiveBoundariesAreConsumedWithoutReplay() {
        val tracker = RunLapCueTracker().apply { start(initial) }
        val oneKm = initial.copy(distanceMeters = 1_000.0)
        assertNull(tracker.observe(oneKm, true, false, false, false))
        assertNull(tracker.observe(oneKm, true, true, true, true))

        val twoKm = initial.copy(distanceMeters = 2_000.0)
        assertNull(tracker.observe(twoKm.copy(phase = RunPhase.PAUSED), true, true, true, true))
        assertNull(tracker.observe(twoKm, true, true, true, true))

        val threeKm = initial.copy(distanceMeters = 3_000.0)
        assertNull(tracker.observe(threeKm, false, true, true, true))
        assertNull(tracker.observe(threeKm, true, true, true, true))
        assertEquals(RunLapCue(4, "4킬로미터 달렸어요", false),
            tracker.observe(initial.copy(distanceMeters = 4_000.0), true, true, false, false))
    }

    @Test fun recoveryStartsAfterTheLastRecordedDistanceAndIgnoresAnotherRun() {
        val recovered = initial.copy(distanceMeters = 5_360.0, activeDurationMillis = 2_040_000,
            lastCompletedSplit = KmSplit(5, 373_000))
        val tracker = RunLapCueTracker().apply { start(recovered) }
        assertNull(tracker.observe(recovered, true, true, true, true))
        assertNull(tracker.observe(recovered.copy(id = "earlier", distanceMeters = 8_000.0), true, true, true, true))
        val next = recovered.copy(distanceMeters = 6_002.0, activeDurationMillis = 2_300_000,
            lastCompletedSplit = KmSplit(6, 391_000))
        assertEquals(RunLapCue(6, "6킬로미터 달렸어요. 이번 1킬로미터는 6분 31초예요.", true),
            tracker.observe(next, true, true, true, true))
    }

    @Test fun invalidDistanceAndOutOfOrderObservationsDoNotMoveTheBoundaryBack() {
        val tracker = RunLapCueTracker().apply { start(initial) }
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, -2_000.0))
            assertNull(tracker.observe(initial.copy(distanceMeters = invalid), true, true, false, true))
        val next = initial.copy(distanceMeters = 2_030.0)
        assertEquals(RunLapCue(2, "2킬로미터 달렸어요", true), tracker.observe(next, true, true, false, true))
        assertNull(tracker.observe(initial.copy(distanceMeters = 1_004.0), true, true, false, true))
        assertNull(tracker.observe(next, true, true, false, true))
    }
}

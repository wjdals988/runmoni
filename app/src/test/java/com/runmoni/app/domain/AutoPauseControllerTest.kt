package com.runmoni.app.domain

import com.runmoni.app.domain.AutoPauseController.Decision.*
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoPauseControllerTest {
    private val detector = AutoPauseController()
    private fun sample(time: Long, speed: Float? = 0f, accuracy: Float? = 5f) =
        detector.onLocation(time, true, RunPhase.RUNNING, null, speed, accuracy)
    private fun stoppedUntil(time: Long) { for (second in 0..time / 1000) assertEquals(NONE, sample(second * 1000)) }

    @Test fun pausesAfterTenSecondsOfContinuousStationaryFixes() {
        stoppedUntil(9000)
        assertEquals(PAUSE, sample(10000))
    }

    @Test fun slowMovementResetsTheStopInterval() {
        stoppedUntil(9000)
        assertEquals(NONE, sample(9500, 0.7f))
        for (time in 10000L..19000L step 1000) assertEquals(NONE, sample(time))
        assertEquals(PAUSE, sample(20000))
    }

    @Test fun unreliableOrMissingSpeedAndAccuracyRestartObservation() {
        for ((speed, accuracy) in listOf(0f to 21f, null to 5f, 0f to null, Float.NaN to 5f,
            -1f to 5f, 0f to Float.NaN, 0f to -1f)) {
            detector.reset()
            stoppedUntil(9000)
            assertEquals(NONE, sample(9500, speed, accuracy))
            assertEquals(NONE, sample(10000))
        }
    }

    @Test fun signalGapCannotCountAsStandingStill() {
        stoppedUntil(9000)
        assertEquals(NONE, sample(15000))
        for (time in 16000L..24000L step 1000) assertEquals(NONE, sample(time))
        assertEquals(PAUSE, sample(25000))
    }

    @Test fun clockRollbackStartsFresh() {
        stoppedUntil(9000)
        assertEquals(NONE, sample(1000))
        assertEquals(NONE, sample(5000))
    }

    @Test fun onlyAutomaticPauseResumesOnReliableMovement() {
        for (speed in listOf(0f, .7f, 1f, 3f)) {
            assertEquals(NONE, detector.onLocation(1000, true, RunPhase.PAUSED, PauseReason.MANUAL, speed, 5f))
        }
        assertEquals(NONE, detector.onLocation(2000, true, RunPhase.PAUSED, PauseReason.AUTOMATIC, .9f, 5f))
        assertEquals(NONE, detector.onLocation(3000, true, RunPhase.PAUSED, PauseReason.AUTOMATIC, 2f, 50f))
        assertEquals(RESUME, detector.onLocation(4000, true, RunPhase.PAUSED, PauseReason.AUTOMATIC, 1f, 5f))
    }

    @Test fun disablingSettingReleasesOnlyAutomaticPauseWithoutGps() {
        assertEquals(RESUME, detector.onTick(1000, false, RunPhase.PAUSED, PauseReason.AUTOMATIC))
        assertEquals(NONE, detector.onTick(1000, false, RunPhase.PAUSED, PauseReason.MANUAL))
        assertEquals(NONE, detector.onTick(1000, false, RunPhase.RUNNING, null))
    }

    @Test fun ticksDoNotInterruptRecentStationarySamples() {
        for (time in 0L..9000L step 1000) {
            assertEquals(NONE, sample(time))
            assertEquals(NONE, detector.onTick(time + 500, true, RunPhase.RUNNING, null))
        }
        assertEquals(PAUSE, sample(10000))
    }

    @Test fun manualResetDoesNotReuseEarlierStationaryTime() {
        stoppedUntil(9000)
        detector.reset()
        assertEquals(NONE, sample(10000))
    }
}

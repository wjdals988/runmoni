package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class GpsSignalFreshnessTest {
    @Test fun missingFirstFixIsNotInventedAsAnOldLiveFix() {
        assertFalse(GpsSignalFreshness.isStale(null, 100_000))
    }

    @Test fun deliveredFixBecomesStaleAtFifteenSecondsOnTheMonotonicClock() {
        assertFalse(GpsSignalFreshness.isStale(50_000, 64_999))
        assertTrue(GpsSignalFreshness.isStale(50_000, 65_000))
        assertTrue(GpsSignalFreshness.isStale(50_000, 200_000))
    }

    @Test fun aClockEarlierThanTheFixIsNeverTreatedAsFresh() {
        assertTrue(GpsSignalFreshness.isStale(50_000, 49_000))
    }

    @Test fun delayedDeliveryUsesMeasurementTimeEvenWhenWallTimeChanges() {
        val measured = GpsSignalFreshness.measurementElapsedMillis(40_000_000_000, 2_000_000, 1_000_000, 60_000)
        assertEquals(40_000L, measured)
        assertTrue(GpsSignalFreshness.isStale(measured, 60_000))
    }

    @Test fun missingMonotonicTimestampUsesBoundedWallAgeAndFutureFixIsRejected() {
        assertEquals(58_000L, GpsSignalFreshness.measurementElapsedMillis(0, 98_000, 100_000, 60_000))
        assertNull(GpsSignalFreshness.measurementElapsedMillis(0, 101_000, 100_000, 60_000))
        assertNull(GpsSignalFreshness.measurementElapsedMillis(61_000_000_000, 100_000, 100_000, 60_000))
    }
}

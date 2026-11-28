package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RunResultAnalysisTest {
    private val baseline = RunRecord("analysis", 1_000, 1_400_000, 3_230.0, 1_080_000, 1_200_000,
        splits = listOf(KmSplit(3, 348_000), KmSplit(1, 335_000), KmSplit(2, 303_000)))

    @Test fun completeKilometersAreSortedWithoutCreatingPaceForTheRemainder() {
        val result = RunResultAnalysis.from(baseline)

        assertEquals(listOf(1, 2, 3), result.splits.map { it.kilometer })
        assertEquals(listOf(335L, 303L, 348L), result.splits.map { it.paceSeconds })
        assertEquals(listOf(2), result.fastestSplits.map { it.kilometer })
        assertEquals(334L, result.averagePaceSeconds)
        assertEquals(1_200_000L, result.elapsedMillis)
        assertFalse(result.hasMissingSplits)
        val ticks = requireNotNull(result.paceScale).ticksSeconds
        assertTrue(ticks.first() <= 303)
        assertTrue(ticks.last() >= 348)
        assertTrue(ticks.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun ambiguousAndUnfinishedKilometersAndInvalidPaceAreNotPlotted() {
        val record = baseline.copy(distanceMeters = 4_010.0, splits = listOf(
            KmSplit(0, 1_000), KmSplit(1, 300_000), KmSplit(1, 250_000), KmSplit(2, 0),
            KmSplit(3, 302_000), KmSplit(4, 1_080_001), KmSplit(5, 200_000),
        ))
        val result = RunResultAnalysis.from(record)

        assertEquals(listOf(RunResultSplit(3, 302)), result.splits)
        assertTrue(result.fastestSplits.isEmpty())
        assertNull(result.paceScale)
        assertTrue(result.hasMissingSplits)
    }

    @Test fun rankingUsesDisplayedPrecisionAndDoesNotRankAnInterruptedRecord() {
        val record = baseline.copy(splits = listOf(KmSplit(1, 302_012), KmSplit(2, 302_986), KmSplit(3, 340_001)))
        val result = RunResultAnalysis.from(record)
        assertEquals(listOf(1, 2), result.fastestSplits.map { it.kilometer })

        val interrupted = RunResultAnalysis.from(record.copy(interrupted = true))
        assertEquals(result.splits, interrupted.splits)
        assertTrue(interrupted.fastestSplits.isEmpty())
    }

    @Test fun contradictoryTotalsAndInvalidDistanceDoNotCreatePublicMetricValues() {
        val contradictory = baseline.copy(splits = listOf(KmSplit(1, Long.MAX_VALUE - 1), KmSplit(2, Long.MAX_VALUE - 1)),
            activeDurationMillis = Long.MAX_VALUE)
        val result = RunResultAnalysis.from(contradictory)
        assertTrue(result.splits.isEmpty())
        assertTrue(result.hasMissingSplits)

        val invalidDistance = RunResultAnalysis.from(baseline.copy(distanceMeters = Double.NaN))
        assertTrue(invalidDistance.splits.isEmpty())
        assertNull(invalidDistance.averagePaceSeconds)
    }

    @Test fun identicalPacesStillHaveAReadableScaleAndUnchangedElapsedTimeIsHidden() {
        val record = baseline.copy(distanceMeters = 3_000.0, activeDurationMillis = 900_000, elapsedDurationMillis = 900_999,
            splits = (1..3).map { KmSplit(it, 300_000L) })
        val result = RunResultAnalysis.from(record)

        assertNull(result.elapsedMillis)
        val scale = requireNotNull(result.paceScale)
        assertTrue(scale.fastestSeconds < 300L)
        assertTrue(scale.slowestSeconds > 300L)
        assertEquals(3, result.fastestSplits.size)
    }
}

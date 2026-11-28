package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RunGoalTest {
    @Test fun distanceGoalDoesNotMarkAnUnfinishedMetreAsReached() {
        val goal = RunGoal(RunGoalKind.DISTANCE, 5)
        val close = goal.progress(4_999.8, 30 * 60_000L)
        assertFalse(close.reached)
        assertEquals("1 m", close.remainingLabel)
        assertTrue(goal.progress(5_000.0, 0).reached)
        assertEquals(1f, goal.progress(5_200.0, 0).fraction, 0f)
        assertEquals(0f, goal.progress(Double.POSITIVE_INFINITY, 0).fraction, 0f)
    }

    @Test fun timeGoalUsesActiveTimeAndKeepsOneSecondUntilReached() {
        val goal = RunGoal(RunGoalKind.DURATION, 30)
        val close = goal.progress(40_000.0, 30 * 60_000L - 1)
        assertFalse(close.reached)
        assertEquals("0:01", close.remainingLabel)
        assertTrue(goal.progress(0.0, 30 * 60_000L).reached)
        assertEquals("30:00", goal.progress(0.0, -1).remainingLabel)
    }

    @Test fun oldRecordsAndInvalidStoredValuesHaveNoGoal() {
        assertNull(RunGoal.fromStorage(null, 0))
        assertNull(RunGoal.fromStorage("other", 5))
        assertNull(RunGoal.fromStorage("DISTANCE", 0))
        assertNull(RunGoal.fromStorage("DURATION", Int.MAX_VALUE))
        assertEquals(RunGoal(RunGoalKind.DISTANCE, 5), RunGoal.fromStorage("DISTANCE", 5))
    }
}

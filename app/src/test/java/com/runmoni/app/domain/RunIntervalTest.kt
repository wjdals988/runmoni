package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RunIntervalTest {
    private val plan = RunIntervalPlan(runSeconds = 10, recoverySeconds = 20, repeats = 2)
    private val run = ActiveRun(id = "interval", phase = RunPhase.RUNNING, interval = plan)

    @Test fun activeClockCountsAlternatingStagesAndFinalRecovery() {
        val first = plan.progress(-1)
        assertEquals(RunIntervalStage.RUN, first.stage)
        assertEquals(10_000L, first.stageRemainingMillis)
        assertEquals(1L, plan.progress(9_999).stageRemainingMillis)
        val recovery = plan.progress(10_000)
        assertEquals(RunIntervalStage.RECOVERY, recovery.stage)
        assertEquals(1, recovery.completedRuns)
        assertEquals(0, recovery.completedRecoveries)
        val second = plan.progress(30_000)
        assertEquals(RunIntervalStage.RUN, second.stage)
        assertEquals(2, second.repeat)
        assertEquals(1, second.completedRecoveries)
        assertEquals(RunIntervalStage.RECOVERY, plan.progress(40_000).stage)
        assertEquals(1L, plan.progress(59_999).stageRemainingMillis)
        val complete = plan.progress(Long.MAX_VALUE)
        assertEquals(RunIntervalStage.COMPLETE, complete.stage)
        assertEquals(2, complete.completedRecoveries)
        assertEquals(2, complete.completedRuns)
        assertEquals(0L, complete.stageRemainingMillis)
    }

    @Test fun oldAndInvalidStorageRowsBehaveAsRegularRuns() {
        assertNull(RunIntervalPlan.fromStorage(0, 0, 0, false))
        assertNull(RunIntervalPlan.fromStorage(600, 601, 20, true))
        assertNull(RunIntervalPlan.fromStorage(10, 10, 1, false))
        assertEquals(plan.copy(vibration = true), RunIntervalPlan.fromStorage(10, 20, 2, true))
    }

    @Test fun resultIncludesOnlyActuallyStartedSetsAndPreservesPartialTime() {
        assertTrue(plan.setResults(0).isEmpty())
        assertEquals(listOf(RunIntervalSetResult(1, 10_000, 20_000)), plan.setResults(30_000))
        assertEquals(listOf(RunIntervalSetResult(1, 10_000, 20_000), RunIntervalSetResult(2, 3_472, 0)),
            plan.setResults(33_472))
        assertEquals(listOf(RunIntervalSetResult(1, 10_000, 20_000), RunIntervalSetResult(2, 10_000, 20_000)),
            plan.setResults(99_000))
    }

    @Test fun newCueWaitsForItsDurableBoundaryAndIsConsumedOnlyOnce() {
        val tracker = RunIntervalCueTracker()
        tracker.start(run)
        val transition = run.copy(activeDurationMillis = 10_012)
        assertNull(tracker.observe(transition, live = true, checkpointedActiveMillis = null))
        assertNull(tracker.observe(transition, live = true, checkpointedActiveMillis = 9_999))
        assertEquals(RunIntervalStage.RECOVERY, tracker.observe(transition, true, 10_000)?.stage)
        assertNull(tracker.observe(transition, true, 10_012))

        val replacement = RunIntervalCueTracker()
        replacement.start(run.copy(activeDurationMillis = 10_000))
        assertNull(replacement.observe(transition, true, 10_012))
        assertNull(replacement.observe(transition.copy(id = "different"), true, 10_012))
        assertEquals(RunIntervalStage.RUN, replacement.observe(run.copy(activeDurationMillis = 30_000), true, 30_000)?.stage)
    }

    @Test fun pauseAndDelayedCallbacksDoNotReplayPastCues() {
        val tracker = RunIntervalCueTracker()
        tracker.start(run)
        val paused = run.copy(phase = RunPhase.PAUSED, activeDurationMillis = 10_000)
        assertNull(tracker.observe(paused, false, 10_000))
        assertNull(tracker.observe(paused.copy(phase = RunPhase.RUNNING), true, 10_000))
        assertNull(tracker.observe(run.copy(activeDurationMillis = 43_000), true, 43_000))
        assertNull(tracker.observe(run.copy(activeDurationMillis = 44_000), true, 44_000))
        val completed = run.copy(activeDurationMillis = 60_000)
        assertEquals(RunIntervalStage.COMPLETE, tracker.observe(completed, true, 60_000)?.stage)
        assertNull(tracker.observe(completed, true, 60_000))
    }
}

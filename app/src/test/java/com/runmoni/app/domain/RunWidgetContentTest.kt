package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RunWidgetContentTest {
    private val healthy = RunPersistenceState(initialized = true)
    private val run = ActiveRun(id = "widget-run", phase = RunPhase.RUNNING, distanceMeters = 1234.0,
        activeDurationMillis = 75_000, currentSpeedMetersPerSecond = 2.5)
    private fun snapshot(active: ActiveRun = run, storage: RunPersistenceState = healthy) = RunWidgetSnapshot.from(active, storage, 1000)

    @Test fun runningSnapshotHasKilometersTimeAndSpeedButNoRouteOrAccountInput() {
        val content = RunWidgetContent.from(snapshot(), live = true)
        assertEquals("1.23 km", content.distance)
        assertEquals("01:15", content.duration)
        assertEquals("현재 9.0 km/h", content.speed)
        assertEquals(RunWidgetControl.PAUSE, content.control)
        assertTrue(content.canFinish)
        assertFalse(content.stale)
    }

    @Test fun bothPauseKindsStopSpeedAndOfferResumeWithoutAdvancingSnapshotTime() {
        for (reason in PauseReason.entries) {
            val paused = snapshot(run.copy(phase = RunPhase.PAUSED, pauseReason = reason))
            val content = RunWidgetContent.from(paused, live = true)
            assertNull(paused.speedMetersPerSecond)
            assertEquals("현재 속도 —", content.speed)
            assertEquals("01:15", content.duration)
            assertEquals(RunWidgetControl.RESUME, content.control)
            assertEquals(reason == PauseReason.AUTOMATIC, content.title.contains("자동"))
        }
    }

    @Test fun coldProcessAndRecoveryOnlyShowPastMetricsAndCannotOfferAnOperation() {
        for (state in listOf(snapshot(), snapshot(run.copy(phase = RunPhase.RECOVERY)))) {
            val content = RunWidgetContent.from(state, live = false)
            assertEquals("1.23 km", content.distance)
            assertEquals("01:15", content.duration)
            assertEquals("현재 속도 —", content.speed)
            assertTrue(content.stale)
            assertNull(content.control)
            assertFalse(content.canFinish)
        }
        assertNull(RunWidgetContent.from(snapshot(run.copy(phase = RunPhase.IDLE)), live = true).control)
        assertNull(RunWidgetContent.from(null, live = true).control)
    }

    @Test fun savingAndCheckpointFailuresReplaceBothControlsWithAppReview() {
        for (storage in listOf(healthy.copy(finishing = true), healthy.copy(initialized = false),
            healthy.copy(issue = RunPersistenceIssue.CHECKPOINT), healthy.copy(issue = RunPersistenceIssue.FINISH))) {
            val content = RunWidgetContent.from(snapshot(storage = storage), live = true)
            assertNull(content.control)
            assertFalse(content.canFinish)
            assertEquals("현재 속도 —", content.speed)
        }
        assertEquals(RunWidgetControl.PAUSE, RunWidgetContent.from(snapshot(storage = healthy.copy(issue = RunPersistenceIssue.CREDIT)), true).control)
    }

    @Test fun invalidNumbersAreNotPublishedAndZeroIsAValidStationarySpeed() {
        for (invalid in listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -1.0)) {
            val state = snapshot(run.copy(distanceMeters = invalid, currentSpeedMetersPerSecond = invalid, activeDurationMillis = -1))
            val content = RunWidgetContent.from(state, true)
            assertEquals("0.00 km", content.distance)
            assertEquals("00:00", content.duration)
            assertEquals("현재 속도 —", content.speed)
        }
        assertEquals("현재 0.0 km/h", RunWidgetContent.from(snapshot(run.copy(currentSpeedMetersPerSecond = 0.0)), true).speed)
    }

    @Test fun hoursAreSnapshotsRatherThanAChronometerThatKeepsRunningAfterDeath() {
        val state = snapshot(run.copy(activeDurationMillis = 3_661_000))
        assertEquals("1:01:01", RunWidgetContent.from(state, true).duration)
        assertEquals("1:01:01", RunWidgetContent.from(state, false).duration)
    }

    @Test fun oldActionsNeverTargetANewRunOrAnUninitializedSession() {
        val session = RunWidgetSession()
        val current = snapshot()
        assertFalse(session.allows(run.id, current, RunWidgetControl.PAUSE))
        session.observe(current, live = true, 10)
        assertTrue(session.allows(run.id, current, RunWidgetControl.PAUSE))
        assertFalse(session.allows(run.id, current, RunWidgetControl.RESUME))
        val newer = snapshot(run.copy(id = "new-run"))
        session.observe(newer, live = true, 20)
        assertFalse(session.allows(run.id, current, RunWidgetControl.PAUSE))
        assertFalse(session.allows(run.id, newer, RunWidgetControl.PAUSE))
        assertTrue(session.allows("new-run", newer, RunWidgetControl.PAUSE))
    }

    @Test fun longManualPauseKeepsResumeWhileProcessDeathAlwaysInvalidatesIt() {
        val session = RunWidgetSession()
        val paused = snapshot(run.copy(phase = RunPhase.PAUSED, pauseReason = PauseReason.MANUAL))
        session.observe(paused, live = true, 100)
        assertTrue(session.isLive(paused, 86_400_100))
        assertTrue(session.allows(run.id, paused, RunWidgetControl.RESUME))
        session.processStarted()
        assertFalse(session.isLive(paused, 86_400_100))
        assertFalse(session.allows(run.id, paused, RunWidgetControl.RESUME))
        val automatic = paused.copy(automaticPause = true)
        session.observe(automatic, true, 100)
        assertFalse(session.isLive(automatic, 86_400_100))
    }

    @Test fun oldServiceStopCannotInvalidateTheNewServiceRunAndRunningAgeOnlyAffectsPresentation() {
        val session = RunWidgetSession()
        val state = snapshot()
        session.observe(state, true, 100)
        session.stopped("other-run")
        assertTrue(session.isLive(state, 200))
        assertFalse(session.isLive(state, 30_100))
        assertTrue(session.allows(run.id, state, RunWidgetControl.PAUSE))
        session.stopped(run.id)
        assertFalse(session.allows(run.id, state, RunWidgetControl.PAUSE))
    }

    @Test fun aliveMarkerCannotBypassStorageFailureOrResumeTheWrongPhase() {
        val session = RunWidgetSession()
        val blocked = snapshot(storage = healthy.copy(finishing = true))
        session.observe(blocked, true, 0)
        assertFalse(session.allows(run.id, blocked, RunWidgetControl.PAUSE))
        val paused = snapshot(run.copy(phase = RunPhase.PAUSED))
        session.observe(paused, true, 1)
        assertFalse(session.allows(run.id, paused, RunWidgetControl.PAUSE))
        assertTrue(session.allows(run.id, paused, RunWidgetControl.RESUME))
    }
}

package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RunNotificationTest {
    private val healthy = RunPersistenceState(initialized = true)
    private val running = ActiveRun(
        id = "current-run", phase = RunPhase.RUNNING, distanceMeters = 1234.0,
        activeDurationMillis = 75_000, elapsedDurationMillis = 9_000_000,
        currentSpeedMetersPerSecond = 2.5, currentPaceSecondsPerKm = 400,
    )

    @Test fun runningShowsActiveTimeKilometresSpeedAndPaceInTheirOwnUnits() {
        val content = RunNotificationContent.from(running, healthy)
        assertEquals("1.23 km · 01:15", content.metrics)
        assertEquals("현재 9.0 km/h · 페이스 6:40 /km", content.detail)
        assertEquals(RunNotificationControl.PAUSE, content.control)
        assertTrue(content.canFinish)
    }

    @Test fun durationKeepsSecondsAcrossAnHourAndDoesNotWrapAtOneDay() {
        for ((millis, time) in listOf(59_999L to "00:59", 3_599_000L to "59:59",
            3_600_000L to "1:00:00", 3_661_000L to "1:01:01", 90_061_000L to "25:01:01")) {
            assertEquals("duration=$millis", "1.23 km · $time",
                RunNotificationContent.from(running.copy(activeDurationMillis = millis), healthy).metrics)
        }
    }

    @Test fun invalidDistanceAndNegativeDurationCannotBecomeNaNOrNegativeMetrics() {
        for (distance in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0)) {
            val content = RunNotificationContent.from(running.copy(distanceMeters = distance, activeDurationMillis = Long.MIN_VALUE), healthy)
            assertEquals("distance=$distance", "0.00 km · 00:00", content.metrics)
        }
    }

    @Test fun unavailableOrInvalidSpeedAlsoSuppressesAnOldPace() {
        for (speed in listOf<Double?>(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.5)) {
            val content = RunNotificationContent.from(running.copy(currentSpeedMetersPerSecond = speed), healthy)
            assertTrue("speed=$speed", content.detail.contains("현재 속도 —"))
            assertFalse(content.detail.contains("6:40"))
            assertFalse(content.detail.contains("NaN"))
            assertFalse(content.detail.contains("Infinity"))
        }
    }

    @Test fun stationaryValidSpeedDoesNotInventAPace() {
        val content = RunNotificationContent.from(running.copy(currentSpeedMetersPerSecond = 0.0, currentPaceSecondsPerKm = null), healthy)
        assertEquals("현재 0.0 km/h · 페이스 — /km", content.detail)
    }

    @Test fun manualAndAutomaticPauseHideStaleSpeedButKeepTotalsAndResume() {
        for (reason in PauseReason.entries) {
            val content = RunNotificationContent.from(running.copy(phase = RunPhase.PAUSED, pauseReason = reason), healthy)
            assertEquals("1.23 km · 01:15", content.metrics)
            assertEquals(RunNotificationControl.RESUME, content.control)
            assertTrue(content.canFinish)
            assertFalse(content.detail.contains("km/h"))
            assertFalse(content.detail.contains("6:40"))
            assertEquals(reason == PauseReason.AUTOMATIC, content.title.contains("자동"))
        }
    }

    @Test fun finishAndCheckpointProblemsOfferSaveCheckingInsteadOfRunControls() {
        for (storage in listOf(healthy.copy(finishing = true), healthy.copy(issue = RunPersistenceIssue.FINISH),
            healthy.copy(issue = RunPersistenceIssue.CHECKPOINT))) {
            for (phase in listOf(RunPhase.RUNNING, RunPhase.PAUSED)) {
                val content = RunNotificationContent.from(running.copy(phase = phase), storage)
                assertTrue(content.title.contains("저장 확인"))
                assertTrue(content.detail.contains("앱을 열어 저장 상태"))
                assertNull(content.control)
                assertFalse(content.canFinish)
            }
        }
    }

    @Test fun inactiveOrRecoverableRunsNeverOfferPauseResumeOrFinish() {
        for (phase in listOf(RunPhase.IDLE, RunPhase.RECOVERY)) {
            val run = running.copy(phase = phase)
            val content = RunNotificationContent.from(run, healthy)
            assertNull(content.control)
            assertFalse(content.canFinish)
            for (control in RunNotificationControl.entries) {
                assertFalse(RunControlPolicy.allows(control, run.id, run.id, run, healthy))
            }
        }
    }

    @Test fun currentLiveSessionAllowsPauseAndOnlyPausedSessionsAllowResume() {
        assertTrue(RunControlPolicy.allows(RunNotificationControl.PAUSE, running.id, running.id, running, healthy))
        assertFalse(RunControlPolicy.allows(RunNotificationControl.RESUME, running.id, running.id, running, healthy))
        val paused = running.copy(phase = RunPhase.PAUSED, pauseReason = PauseReason.AUTOMATIC)
        // A delayed pause can pin an automatic pause as manual; it must never toggle to resume.
        assertTrue(RunControlPolicy.allows(RunNotificationControl.PAUSE, paused.id, paused.id, paused, healthy))
        assertTrue(RunControlPolicy.allows(RunNotificationControl.RESUME, paused.id, paused.id, paused, healthy))
    }

    @Test fun missingStaleOrUnownedRunIdsCannotOperateEvenWhenTheRunLooksActive() {
        for (phase in listOf(RunPhase.RUNNING, RunPhase.PAUSED)) {
            val run = running.copy(phase = phase)
            for (control in RunNotificationControl.entries) {
                for (expected in listOf(null, "", " ", "finished-run")) {
                    assertFalse("$control expected=$expected", RunControlPolicy.allows(control, expected, run.id, run, healthy))
                }
                for (service in listOf(null, "", "finished-run")) {
                    assertFalse("$control service=$service", RunControlPolicy.allows(control, run.id, service, run, healthy))
                }
            }
        }
    }

    @Test fun savingAFinishOrFailedCheckpointBlocksEveryDeliveredRunControl() {
        for (storage in listOf(healthy.copy(finishing = true), healthy.copy(issue = RunPersistenceIssue.FINISH),
            healthy.copy(issue = RunPersistenceIssue.CHECKPOINT))) {
            for (phase in listOf(RunPhase.RUNNING, RunPhase.PAUSED)) {
                val run = running.copy(phase = phase)
                for (control in RunNotificationControl.entries) {
                    assertFalse("$control $phase $storage", RunControlPolicy.allows(control, run.id, run.id, run, storage))
                }
            }
        }
    }

    @Test fun ordinarySavingOrEarlierCreditProblemsDoNotPreventPausingTheCurrentRun() {
        for (storage in listOf(healthy.copy(saving = true), healthy.copy(issue = RunPersistenceIssue.CREDIT, creditPending = true),
            healthy.copy(historyUnavailable = true))) {
            assertTrue(RunControlPolicy.allows(RunNotificationControl.PAUSE, running.id, running.id, running, storage))
            val paused = running.copy(phase = RunPhase.PAUSED)
            assertTrue(RunControlPolicy.allows(RunNotificationControl.RESUME, paused.id, paused.id, paused, storage))
        }
    }
}

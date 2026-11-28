package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RunTrainingPreparationTest {
    private val goal = RunGoal(RunGoalKind.DISTANCE, 5)
    private val interval = RunIntervalPlan(45, 60, 4, vibration = true)
    private val saved = RunRecord("saved-training", 100, 200, goal = goal, interval = interval)
    private val ready = AppState(records = listOf(saved))

    @Test fun onlyTheCurrentCompletedHistoryEntryWithMatchingTrainingCanBeReused() {
        assertTrue(RunTrainingPreparation.available(ready, saved))
        assertTrue(RunTrainingPreparation.available(ready.copy(records = listOf(saved.copy(synced = true))), saved))
        for (replacement in listOf(saved.copy(id = "other"), saved.copy(startedAtMillis = 101),
            saved.copy(finishedAtMillis = 201), saved.copy(interval = interval.copy(repeats = 5)), saved.copy(goal = null))) {
            assertFalse(RunTrainingPreparation.available(ready.copy(records = listOf(replacement)), saved))
        }
        assertFalse(RunTrainingPreparation.available(ready.copy(records = emptyList()), saved))
        val summary = saved.copy(goal = null, interval = null)
        val incomplete = saved.copy(finishedAtMillis = 0)
        for (unsupported in listOf(summary, incomplete)) {
            assertFalse(RunTrainingPreparation.available(ready.copy(records = listOf(unsupported)), unsupported))
        }
    }

    @Test fun runningPausedRecoveryAndUnavailableHistoryCannotPrepareADraft() {
        for (phase in listOf(RunPhase.RUNNING, RunPhase.PAUSED, RunPhase.RECOVERY)) {
            assertFalse(RunTrainingPreparation.available(ready.copy(activeRun = ActiveRun("another-run", phase)), saved))
        }
        for (storage in listOf(RunPersistenceState(), RunPersistenceState(initialized = true, finishing = true),
            RunPersistenceState(initialized = true, historyUnavailable = true),
            RunPersistenceState(initialized = true, issue = RunPersistenceIssue.CHECKPOINT))) {
            assertFalse(RunTrainingPreparation.available(ready.copy(runPersistence = storage), saved))
        }
        assertFalse(RunTrainingPreparation.available(ready.copy(busy = true), saved))
        assertTrue(RunTrainingPreparation.available(ready.copy(runPersistence = RunPersistenceState(initialized = true,
            issue = RunPersistenceIssue.CREDIT, creditPending = true)), saved))
    }

    @Test fun draftAppliesOnlyStoredTrainingAndRetainsCurrentCueAndPrivacySettings() {
        val current = ready.copy(publicLocation = true, sharingPreferenceEnabled = true, voiceGuidance = false,
            autoPause = true, lapVibration = true, plannedRunGoal = RunGoal(RunGoalKind.DURATION, 15))
        assertEquals(current.copy(plannedRunGoal = goal, plannedRunInterval = interval.copy(vibration = false)),
            RunTrainingPreparation.applyTo(current, saved))
        val alreadyVibrating = current.copy(plannedRunInterval = RunIntervalPlan(50, 50, 2, vibration = true))
        val intervalOnly = saved.copy(goal = null, interval = interval.copy(vibration = false))
        assertEquals(alreadyVibrating.copy(plannedRunGoal = null, plannedRunInterval = interval),
            RunTrainingPreparation.applyTo(alreadyVibrating, intervalOnly))
        val goalOnly = saved.copy(interval = null)
        assertEquals(alreadyVibrating.copy(plannedRunGoal = goal, plannedRunInterval = null),
            RunTrainingPreparation.applyTo(alreadyVibrating, goalOnly))
    }
}

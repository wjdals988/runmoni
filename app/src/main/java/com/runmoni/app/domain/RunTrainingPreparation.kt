package com.runmoni.app.domain

/** Authorizes a preparation draft from the exact saved training the runner selected. */
internal object RunTrainingPreparation {
    fun available(state: AppState, expected: RunRecord): Boolean {
        if (state.busy || !recorderReady(state.activeRun, state.runPersistence)) return false
        val current = state.records.singleOrNull { it.id == expected.id } ?: return false
        return matches(expected, current)
    }

    fun recorderReady(run: ActiveRun, storage: RunPersistenceState): Boolean =
        run.phase == RunPhase.IDLE && !storage.blocksNewRun && !storage.historyUnavailable &&
            storage.issue !in setOf(RunPersistenceIssue.RECOVERY, RunPersistenceIssue.CHECKPOINT, RunPersistenceIssue.FINISH) &&
            !(storage.saving && storage.issue != null)

    fun matches(expected: RunRecord, saved: RunRecord): Boolean =
        expected.id.isNotBlank() && expected.id == saved.id && expected.startedAtMillis > 0 &&
            expected.startedAtMillis == saved.startedAtMillis && expected.finishedAtMillis >= expected.startedAtMillis &&
            expected.finishedAtMillis == saved.finishedAtMillis && (saved.goal != null || saved.interval != null) &&
            expected.goal == saved.goal && expected.interval == saved.interval

    fun applyTo(state: AppState, saved: RunRecord): AppState = state.copy(
        plannedRunGoal = saved.goal,
        plannedRunInterval = saved.interval?.copy(vibration = state.plannedRunInterval?.vibration ?: false),
    )
}

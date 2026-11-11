package com.runmoni.app.domain

/** A retained detail-page snapshot is not authorization to export a deleted or changed record. */
object RunShareAccess {
    fun allowed(state: AppState, expected: RunRecord): Boolean {
        if (state.busy || !state.runPersistence.initialized || state.runPersistence.historyUnavailable ||
            expected.id.isBlank() || RunShareContent.from(expected) == null ||
            state.activeRun.id == expected.id && state.activeRun.phase != RunPhase.IDLE) return false
        val current = state.records.firstOrNull { it.id == expected.id } ?: return false
        return current.startedAtMillis == expected.startedAtMillis && current.finishedAtMillis == expected.finishedAtMillis &&
            current.distanceMeters == expected.distanceMeters && current.activeDurationMillis == expected.activeDurationMillis &&
            current.interrupted == expected.interrupted
    }
}

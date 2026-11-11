package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RunShareAccessTest {
    private val record = RunRecord("share-access-fixture", 1_730_000_000_000, 1_730_001_000_000,
        distanceMeters = 3210.0, activeDurationMillis = 1_000_000)

    @Test fun missingUnloadedAndUnavailableHistoryCannotAuthorizeARetainedSnapshot() {
        val state = AppState(records = listOf(record))
        assertTrue(RunShareAccess.allowed(state, record))
        assertFalse(RunShareAccess.allowed(state.copy(records = emptyList()), record))
        assertFalse(RunShareAccess.allowed(state.copy(runPersistence = RunPersistenceState(initialized = false)), record))
        assertFalse(RunShareAccess.allowed(state.copy(runPersistence = RunPersistenceState(initialized = true, historyUnavailable = true)), record))
        assertFalse(RunShareAccess.allowed(state.copy(busy = true), record))
    }

    @Test fun recordIdAloneDoesNotAuthorizeOldMetricsAndUnfinishedRecordsAreRejected() {
        val state = AppState(records = listOf(record))
        for (changed in listOf(record.copy(distanceMeters = 4000.0), record.copy(activeDurationMillis = 900_000),
            record.copy(startedAtMillis = record.startedAtMillis + 1), record.copy(finishedAtMillis = 0),
            record.copy(interrupted = true), record.copy(id = "other-device-record"))) {
            assertFalse(RunShareAccess.allowed(state.copy(records = listOf(changed)), record))
        }
        assertFalse(RunShareAccess.allowed(state.copy(records = listOf(record.copy(finishedAtMillis = 0))), record.copy(finishedAtMillis = 0)))
        assertFalse(RunShareAccess.allowed(state.copy(activeRun = ActiveRun(id = record.id, phase = RunPhase.RUNNING)), record))
        assertTrue(RunShareAccess.allowed(state.copy(activeRun = ActiveRun(id = "another-live-run", phase = RunPhase.RUNNING)), record))
    }

    @Test fun independentBackupAndCreditStateDoNotRewriteOrInvalidateSavedRunningMetrics() {
        val updated = record.copy(synced = true, mileage = 55, elapsedDurationMillis = 0,
            points = listOf(TrackPoint(0, 37.5, 127.0, 5f, record.startedAtMillis)))
        assertTrue(RunShareAccess.allowed(AppState(records = listOf(updated)), record))
        assertEquals(3210.0, record.distanceMeters, 0.0)
        assertTrue(record.points.isEmpty())
    }
}

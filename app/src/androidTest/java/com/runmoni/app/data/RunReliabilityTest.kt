package com.runmoni.app.data

import android.content.Context
import android.location.Location
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runmoni.app.domain.RunPersistenceIssue
import com.runmoni.app.domain.RunPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room with deterministic write/credit failures; synthetic fixes are not outdoor GPS evidence. */
@RunWith(AndroidJUnit4::class)
class RunReliabilityTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun database() = Room.inMemoryDatabaseBuilder(context, RunDatabase::class.java).build()
    private suspend fun ready(repository: RunRepository) {
        withTimeout(5000) { repository.persistence.first { it.initialized && !it.saving } }
    }

    @Test fun failedPointWritePausesThenRetriesTheSamePointAndKeepsTheNextSequence() = runBlocking {
        val db = database()
        var failCheckpoint = false
        val faulty = object : RunDao by db.runs() {
            override suspend fun saveCheckpoint(run: StoredRun, points: List<StoredPoint>) {
                if (failCheckpoint) throw IllegalStateException("injected checkpoint failure")
                db.runs().saveCheckpoint(run, points)
            }
        }
        val repository = RunRepository(db, faulty, credit = { _, _ -> })
        try {
            ready(repository)
            repository.begin()
            val id = repository.active.value.id
            val time = System.currentTimeMillis()
            failCheckpoint = true
            repository.location(fix(time, 127.0))
            assertEquals(RunPhase.PAUSED, repository.active.value.phase)
            assertEquals(RunPersistenceIssue.CHECKPOINT, repository.persistence.value.issue)
            assertEquals(1, repository.persistence.value.unsavedPointCount)
            assertEquals(0, db.runs().pointCount(id))
            failCheckpoint = false
            repository.retryPersistence()
            assertEquals(1, db.runs().pointCount(id))
            assertEquals(0, repository.persistence.value.unsavedPointCount)
            assertEquals(RunPhase.PAUSED, repository.active.value.phase)
            repository.resume()
            repository.location(fix(time + 1000, 127.00001))
            repository.finish()
            assertEquals(listOf(0, 1), db.runs().points(id).map { it.sequence })
            assertEquals(2, repository.record(id)!!.points.size)
            assertEquals(RunPhase.IDLE, repository.active.value.phase)
        } finally { repository.close(); db.close() }
    }

    @Test fun finishFailureFreezesItsOriginalTimeAndRetryDoesNotCreditTwice() = runBlocking {
        val db = database()
        val id = "finish-retry"
        db.runs().save(StoredRun(id, System.currentTimeMillis() - 60_000, distanceMeters = 1234.0, activeDurationMillis = 40_000))
        var failFinal = true
        val attempts = mutableListOf<StoredRun>()
        val credits = mutableListOf<Pair<String, Int>>()
        val faulty = object : RunDao by db.runs() {
            override suspend fun saveCheckpoint(run: StoredRun, points: List<StoredPoint>) {
                if (run.finishedAtMillis > 0) {
                    attempts += run
                    if (failFinal) throw IllegalStateException("injected finish failure")
                }
                db.runs().saveCheckpoint(run, points)
            }
        }
        val repository = RunRepository(db, faulty, credit = { runId, points -> credits += runId to points })
        try {
            ready(repository)
            repository.finish()
            assertTrue(repository.persistence.value.finishing)
            assertEquals(RunPersistenceIssue.FINISH, repository.persistence.value.issue)
            assertNull(repository.lastFinished.value)
            assertTrue(credits.isEmpty())
            assertEquals(0L, db.runs().get(id)!!.finishedAtMillis)
            delay(20)
            repository.finish()
            assertEquals(attempts.first(), attempts.last())
            failFinal = false
            repository.retryPersistence()
            repository.finish()
            assertEquals(attempts.first(), db.runs().get(id))
            assertEquals(listOf(id to 123), credits)
            assertEquals(RunPhase.IDLE, repository.active.value.phase)
            assertFalse(repository.persistence.value.finishing)
            assertEquals(id, repository.lastFinished.value!!.id)
        } finally { repository.close(); db.close() }
    }

    @Test fun committedRecordSurvivesCreditFailureAndReplayKeepsOneCredit() = runBlocking {
        val db = database()
        val id = "credit-retry"
        db.runs().save(StoredRun(id, System.currentTimeMillis() - 60_000, distanceMeters = 1234.0))
        val ledger = mutableSetOf<String>()
        var pointsTotal = 0
        var failCredit = true
        val repository = RunRepository(db, credit = { runId, points ->
            if (ledger.add(runId)) pointsTotal += points
            if (failCredit) throw IllegalStateException("injected credit acknowledgement failure")
        })
        try {
            ready(repository)
            repository.finish()
            assertTrue(db.runs().get(id)!!.finishedAtMillis > 0)
            assertEquals(RunPhase.IDLE, repository.active.value.phase)
            assertEquals(id, repository.lastFinished.value!!.id)
            assertEquals(RunPersistenceIssue.CREDIT, repository.persistence.value.issue)
            assertEquals(setOf(id), repository.persistence.value.pendingCreditRunIds)
            assertFalse(repository.persistence.value.blocksNewRun)
            try { repository.delete(id); fail("unverified credit must not be discarded") } catch (_: IllegalStateException) { }
            assertNotNull(db.runs().get(id))
            repository.begin()
            assertEquals(RunPhase.RUNNING, repository.active.value.phase)
            failCredit = false
            repository.finish()
            repository.retryPersistence()
            assertEquals(123, pointsTotal)
            assertFalse(repository.persistence.value.creditPending)
            assertTrue(repository.persistence.value.pendingCreditRunIds.isEmpty())
            repository.delete(id)
            assertNull(db.runs().get(id))
        } finally { repository.close(); db.close() }
    }

    @Test fun startupCreditFailureDoesNotHideAnUnfinishedRunAndReadFailureBlocksANewId() = runBlocking {
        val db = database()
        db.runs().save(StoredRun("finished", 1000, finishedAtMillis = 2000, mileage = 10))
        db.runs().save(StoredRun("unfinished", 3000, distanceMeters = 20.0))
        var failRead = true
        val faulty = object : RunDao by db.runs() {
            override suspend fun unfinished(): StoredRun? {
                if (failRead) throw IllegalStateException("injected recovery read failure")
                return db.runs().unfinished()
            }
        }
        val repository = RunRepository(db, faulty, credit = { _, _ -> throw IllegalStateException("injected wallet failure") })
        try {
            withTimeout(5000) { repository.persistence.first { it.issue == RunPersistenceIssue.RECOVERY } }
            assertFalse(repository.persistence.value.initialized)
            try { repository.begin(); fail("recovery must be checked first") } catch (_: IllegalStateException) { }
            failRead = false
            repository.retryPersistence()
            assertEquals(RunPhase.RECOVERY, repository.active.value.phase)
            assertEquals("unfinished", repository.active.value.id)
            assertTrue(repository.persistence.value.initialized)
            assertEquals(RunPersistenceIssue.CREDIT, repository.persistence.value.issue)
            assertEquals("unfinished", db.runs().unfinished()!!.id)
        } finally { repository.close(); db.close() }
    }

    @Test fun failedStartAcknowledgementReloadsItsCommittedIdInsteadOfCreatingAnotherRun() = runBlocking {
        val db = database()
        var failStartAcknowledgement = true
        var startedId: String? = null
        val faulty = object : RunDao by db.runs() {
            override suspend fun save(run: StoredRun) {
                db.runs().save(run)
                startedId = run.id
                if (failStartAcknowledgement) throw IllegalStateException("start committed before failure")
            }
        }
        val repository = RunRepository(db, faulty, credit = { _, _ -> })
        try {
            ready(repository)
            try { repository.begin(); fail("start acknowledgement should fail") } catch (_: IllegalStateException) { }
            assertFalse(repository.persistence.value.initialized)
            assertEquals(RunPersistenceIssue.RECOVERY, repository.persistence.value.issue)
            failStartAcknowledgement = false
            repository.retryPersistence()
            assertEquals(RunPhase.RECOVERY, repository.active.value.phase)
            assertEquals(startedId, repository.active.value.id)
            repository.begin(recover = true)
            assertEquals(startedId, repository.active.value.id)
        } finally { repository.close(); db.close() }
    }

    @Test fun creditRetryFromOldRecordDeletionDoesNotClearCurrentCheckpointFailure() = runBlocking {
        val db = database()
        db.runs().save(StoredRun("old-pending-credit", 1000, finishedAtMillis = 2000, mileage = 10))
        var failCredit = true
        var failCheckpoint = false
        val faulty = object : RunDao by db.runs() {
            override suspend fun saveCheckpoint(run: StoredRun, points: List<StoredPoint>) {
                if (failCheckpoint) throw IllegalStateException("injected current checkpoint failure")
                db.runs().saveCheckpoint(run, points)
            }
        }
        val repository = RunRepository(db, faulty, credit = { _, _ -> if (failCredit) throw IllegalStateException("injected old credit failure") })
        try {
            // Recovery publishes initialized before the separate startup credit scan runs.
            // Wait for this test's injected credit result, not that intermediate emission.
            val pendingCredit = withTimeout(5000) {
                repository.persistence.first {
                    it.initialized && !it.saving && it.creditPending && "old-pending-credit" in it.pendingCreditRunIds
                }
            }
            assertEquals(RunPersistenceIssue.CREDIT, pendingCredit.issue)
            repository.begin()
            val currentId = repository.active.value.id
            failCheckpoint = true
            repository.location(fix(System.currentTimeMillis(), 127.0))
            assertEquals(RunPersistenceIssue.CHECKPOINT, repository.persistence.value.issue)
            assertEquals(1, repository.persistence.value.unsavedPointCount)
            failCredit = false
            repository.delete("old-pending-credit")
            assertFalse(repository.persistence.value.creditPending)
            assertEquals(RunPersistenceIssue.CHECKPOINT, repository.persistence.value.issue)
            assertEquals(1, repository.persistence.value.unsavedPointCount)
            assertEquals(0, db.runs().pointCount(currentId))
            failCheckpoint = false
            repository.retryPersistence()
            assertNull(repository.persistence.value.issue)
            assertEquals(1, db.runs().pointCount(currentId))
        } finally { repository.close(); db.close() }
    }

    private fun fix(timeMillis: Long, longitude: Double) = Location("test").apply {
        latitude = 37.0
        this.longitude = longitude
        accuracy = 5f
        time = timeMillis
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }
}

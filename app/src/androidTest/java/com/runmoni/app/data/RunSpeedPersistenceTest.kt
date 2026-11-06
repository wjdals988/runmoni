package com.runmoni.app.data

import android.content.Context
import android.location.Location
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runmoni.app.domain.RunEngine
import com.runmoni.app.domain.RunPhase
import com.runmoni.app.domain.RunPersistenceIssue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated in-memory Room and synthetic fixes; this does not measure outdoor or screen-off GPS. */
@RunWith(AndroidJUnit4::class)
class RunSpeedPersistenceTest {
    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), RunDatabase::class.java,
    ).build()

    private suspend fun ready(repository: RunRepository) {
        withTimeout(5000) { repository.persistence.first { it.initialized && !it.saving } }
    }

    @Test fun missingRecoveryCannotCreateANewRunOrCredit() = runBlocking {
        val db = database()
        val credits = mutableListOf<String>()
        val repository = RunRepository(db, credit = { id, _ -> credits += id })
        try {
            ready(repository)
            var refused = false
            try { repository.begin(recover = true) }
            catch (failure: IllegalStateException) {
                refused = true
                assertTrue(failure.message.orEmpty().contains("복구할 러닝 기록이 없어요"))
            }
            assertTrue(refused)
            assertEquals(RunPhase.IDLE, repository.active.value.phase)
            assertTrue(repository.active.value.id.isBlank())
            assertNull(db.runs().unfinished())
            assertTrue(db.runs().records().first().isEmpty())
            assertTrue(credits.isEmpty())
        } finally { repository.close(); db.close() }
    }

    @Test fun speedUsesAcceptedRouteAndClearsOnPauseSignalLossAndFinishWithoutLosingPoints() = runBlocking {
        val db = database()
        val credits = mutableListOf<String>()
        val repository = RunRepository(db, credit = { id, _ -> credits += id })
        try {
            ready(repository)
            repository.begin()
            val id = repository.active.value.id
            val time = System.currentTimeMillis()
            repository.location(fix(time, 127.0))
            assertNull(repository.active.value.currentSpeedMetersPerSecond)
            repository.location(fix(time + 1000, 127.00003))
            val expected = RunEngine.haversineMeters(37.0, 127.0, 37.0, 127.00003)
            assertEquals(expected, repository.active.value.currentSpeedMetersPerSecond!!, 0.00001)
            // A short but valid route can have speed before there is enough distance for pace.
            assertNull(repository.active.value.currentPaceSecondsPerKm)
            repository.pause()
            assertNull(repository.active.value.currentSpeedMetersPerSecond)
            repository.resume()
            assertNull(repository.active.value.currentSpeedMetersPerSecond)
            repository.location(fix(time + 2000, 127.00006))
            assertNull(repository.active.value.currentSpeedMetersPerSecond)
            repository.location(fix(time + 3000, 127.00009))
            assertNotNull(repository.active.value.currentSpeedMetersPerSecond)
            repository.interruptLocation("GPS 신호 확인 중")
            assertNull(repository.active.value.currentSpeedMetersPerSecond)
            repository.tick()
            assertNull(repository.active.value.currentSpeedMetersPerSecond)
            repository.location(fix(time + 4000, 127.00012))
            assertNull(repository.active.value.currentSpeedMetersPerSecond)
            repository.location(fix(time + 5000, 127.00015))
            assertNotNull(repository.active.value.currentSpeedMetersPerSecond)
            repository.finish()
            assertEquals(RunPhase.IDLE, repository.active.value.phase)
            assertNull(repository.active.value.currentSpeedMetersPerSecond)
            assertEquals(6, repository.record(id)!!.points.size)
            assertEquals(6, db.runs().pointCount(id))
            assertEquals(listOf(id), credits)
        } finally { repository.close(); db.close() }
    }

    @Test fun staleRunActionsAndStoppedCallbackCannotChangeANewerRunOrFlushItsFailedWrite() = runBlocking {
        val db = database()
        var failWrites = false
        var attempts = 0
        val faulty = object : RunDao by db.runs() {
            override suspend fun saveCheckpoint(run: StoredRun, points: List<StoredPoint>) {
                attempts++
                if (failWrites) throw IllegalStateException("injected checkpoint failure")
                db.runs().saveCheckpoint(run, points)
            }
        }
        val credits = mutableListOf<String>()
        val repository = RunRepository(db, faulty, credit = { id, _ -> credits += id })
        try {
            ready(repository)
            repository.begin()
            val old = repository.active.value.id
            repository.finish(expectedRunId = old)
            repository.begin()
            val current = repository.active.value.id
            assertNotEquals(old, current)
            repository.pause(expectedRunId = old)
            repository.resume(expectedRunId = old)
            repository.finish(expectedRunId = old)
            repository.recorderStopped(old, SystemClock.elapsedRealtime()).join()
            assertEquals(RunPhase.RUNNING, repository.active.value.phase)
            assertEquals(current, repository.active.value.id)
            assertEquals(0L, db.runs().get(current)!!.finishedAtMillis)

            failWrites = true
            repository.location(fix(System.currentTimeMillis(), 127.0))
            assertEquals(RunPersistenceIssue.CHECKPOINT, repository.persistence.value.issue)
            assertEquals(1, repository.persistence.value.unsavedPointCount)
            val before = attempts
            repository.retryPersistence(expectedRunId = old)
            repository.resume(expectedRunId = old)
            repository.pause(expectedRunId = old)
            repository.finish(expectedRunId = old)
            assertEquals(before, attempts)
            assertFalse(repository.persistence.value.finishing)
            assertEquals(1, repository.persistence.value.unsavedPointCount)
            assertEquals(0, db.runs().pointCount(current))
            assertEquals(listOf(old), credits)

            failWrites = false
            repository.retryPersistence(expectedRunId = current)
            repository.finish(expectedRunId = current)
            assertEquals(1, repository.record(current)!!.points.size)
            assertEquals(listOf(old, current), credits)
        } finally { repository.close(); db.close() }
    }

    @Test fun unexpectedRecorderStopPreservesTheRunAndResumeDoesNotBridgeItsGap() = runBlocking {
        val db = database()
        val credits = mutableListOf<String>()
        val repository = RunRepository(db, credit = { id, _ -> credits += id })
        try {
            ready(repository)
            repository.begin()
            val id = repository.active.value.id
            val time = System.currentTimeMillis()
            repository.location(fix(time, 127.0))
            repository.location(fix(time + 1000, 127.00003))
            val distance = repository.active.value.distanceMeters
            assertNotNull(repository.active.value.currentSpeedMetersPerSecond)
            repository.recorderStopped(id, SystemClock.elapsedRealtime()).join()
            assertEquals(RunPhase.RECOVERY, repository.active.value.phase)
            assertNull(repository.active.value.currentSpeedMetersPerSecond)
            assertNull(repository.active.value.currentPaceSecondsPerKm)
            assertTrue(db.runs().get(id)!!.interrupted)
            assertEquals(2, db.runs().pointCount(id))
            repository.begin(recover = true)
            assertEquals(id, repository.active.value.id)
            assertEquals(RunPhase.RUNNING, repository.active.value.phase)
            repository.location(fix(time + 2000, 127.00006))
            assertEquals(distance, repository.active.value.distanceMeters, 0.00001)
            assertNull(repository.active.value.currentSpeedMetersPerSecond)
            repository.location(fix(time + 3000, 127.00009))
            assertTrue(repository.active.value.distanceMeters > distance)
            repository.finish(expectedRunId = id)
            assertEquals(4, repository.record(id)!!.points.size)
            assertTrue(repository.record(id)!!.interrupted)
            assertEquals(listOf(id), credits)
        } finally { repository.close(); db.close() }
    }

    @Test fun failedStopCheckpointCannotReloadAndLoseQueuedPointsOnWarmRecovery() = runBlocking {
        val db = database()
        var failWrites = false
        val faulty = object : RunDao by db.runs() {
            override suspend fun saveCheckpoint(run: StoredRun, points: List<StoredPoint>) {
                if (failWrites) throw IllegalStateException("injected checkpoint failure")
                db.runs().saveCheckpoint(run, points)
            }
        }
        val repository = RunRepository(db, faulty, credit = { _, _ -> })
        try {
            ready(repository)
            repository.begin()
            val id = repository.active.value.id
            failWrites = true
            repository.location(fix(System.currentTimeMillis(), 127.0))
            repository.recorderStopped(id, SystemClock.elapsedRealtime()).join()
            assertEquals(RunPhase.RECOVERY, repository.active.value.phase)
            assertEquals(1, repository.persistence.value.unsavedPointCount)
            var refused = false
            try { repository.begin(recover = true) }
            catch (_: IllegalStateException) { refused = true }
            assertTrue(refused)
            assertEquals(id, repository.active.value.id)
            assertEquals(RunPhase.RECOVERY, repository.active.value.phase)
            assertEquals(1, repository.active.value.points.size)
            assertEquals(1, repository.persistence.value.unsavedPointCount)
            assertEquals(0, db.runs().pointCount(id))
            failWrites = false
            repository.begin(recover = true)
            assertEquals(id, repository.active.value.id)
            assertEquals(1, db.runs().pointCount(id))
            assertEquals(0, repository.persistence.value.unsavedPointCount)
            assertEquals(1, repository.active.value.points.size)
        } finally { repository.close(); db.close() }
    }

    @Test fun beginWaitsForThePriorRecorderCleanupBeforeRestoringItsCheckpoint() = runBlocking {
        val db = database()
        val saving = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var blockWrite = false
        val slow = object : RunDao by db.runs() {
            override suspend fun saveCheckpoint(run: StoredRun, points: List<StoredPoint>) {
                if (blockWrite) { saving.complete(Unit); release.await() }
                db.runs().saveCheckpoint(run, points)
            }
        }
        val repository = RunRepository(db, slow, credit = { _, _ -> })
        try {
            ready(repository)
            repository.begin()
            val id = repository.active.value.id
            blockWrite = true
            val cleanup = repository.recorderStopped(id, SystemClock.elapsedRealtime())
            withTimeout(5000) { saving.await() }
            val resume = async(start = CoroutineStart.UNDISPATCHED) { repository.begin(recover = true) }
            assertFalse(resume.isCompleted)
            blockWrite = false
            release.complete(Unit)
            withTimeout(5000) { cleanup.join(); resume.await() }
            assertEquals(id, repository.active.value.id)
            assertEquals(RunPhase.RUNNING, repository.active.value.phase)
            assertTrue(db.runs().get(id)!!.interrupted)
        } finally { release.complete(Unit); repository.close(); db.close() }
    }

    private fun fix(timeMillis: Long, longitude: Double) = Location("test").apply {
        latitude = 37.0
        this.longitude = longitude
        accuracy = 5f
        time = timeMillis
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }
}

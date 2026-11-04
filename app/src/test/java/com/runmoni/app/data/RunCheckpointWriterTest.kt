package com.runmoni.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RunCheckpointWriterTest {
    @Test fun failedWriteRetainsAcceptedPointsUntilWholeCheckpointSucceeds() = runBlocking {
        val points = linkedMapOf<Int, StoredPoint>()
        var saved: StoredRun? = null
        var failWrite = true
        val writer = RunCheckpointWriter { row, batch ->
            if (failWrite) throw IllegalStateException("injected disk failure")
            saved = row
            batch.forEach { points[it.sequence] = it }
        }
        val row = StoredRun("run", 1000, distanceMeters = 2.0)
        val point = StoredPoint(row.id, 0, 37.0, 127.0, 5f, 1000, 0)
        writer.enqueue(point)
        try { writer.save(row); fail("write should fail") } catch (_: IllegalStateException) { }
        assertEquals(1, writer.unsavedPointCount)
        assertNull(saved)
        assertTrue(points.isEmpty())
        failWrite = false
        writer.save(row)
        assertEquals(row, saved)
        assertEquals(listOf(point), points.values.toList())
        assertEquals(0, writer.unsavedPointCount)
    }

    @Test fun ambiguousCommitReplayDoesNotDuplicatePointsOrLoseFollowingPoint() = runBlocking {
        val points = linkedMapOf<Int, StoredPoint>()
        var failAfterCommit = true
        val writer = RunCheckpointWriter { _, batch ->
            batch.forEach { points[it.sequence] = it }
            if (failAfterCommit) throw IllegalStateException("acknowledgement failed")
        }
        val row = StoredRun("run", 1000)
        val first = StoredPoint(row.id, 0, 37.0, 127.0, 5f, 1000, 0)
        val next = first.copy(sequence = 1, longitude = 127.00001, recordedAtMillis = 2000)
        writer.enqueue(first)
        try { writer.save(row); fail("acknowledgement should fail") } catch (_: IllegalStateException) { }
        assertEquals(1, writer.unsavedPointCount)
        writer.enqueue(next)
        failAfterCommit = false
        writer.save(row.copy(finishedAtMillis = 3000))
        assertEquals(listOf(first, next), points.values.toList())
        assertEquals(0, writer.unsavedPointCount)
    }

    @Test fun cancellationPropagatesAndLeavesThePendingPointAvailableForRetry() = runBlocking {
        val writer = RunCheckpointWriter { _, _ -> throw CancellationException("cancelled writer") }
        val row = StoredRun("run", 1000)
        writer.enqueue(StoredPoint(row.id, 0, 37.0, 127.0, 5f, 1000, 0))
        try { writer.save(row); fail("cancellation must propagate") } catch (_: CancellationException) { }
        assertEquals(1, writer.unsavedPointCount)
    }
}

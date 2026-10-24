package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class CloudRunSummaryTest {
    private fun record() = RunRecord(
        id = "run-001_example",
        startedAtMillis = 1_000,
        finishedAtMillis = 6_401,
        activeDurationMillis = 5_401,
        distanceMeters = 12.25,
        points = listOf(TrackPoint(0, 37.123456, 127.123456, 5f, 1_000)),
        mileage = 999_999,
        synced = true,
        interrupted = true,
    )

    private fun rejects(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java, block)
    }

    @Test fun backupPreservesExactMillisAndOnlyWhitelistedSummaryFields() {
        val summary = CloudRunSummary.fromRunRecord(record())
        assertEquals(5_401L, summary.activeDurationMillis)
        assertEquals(1, summary.pointCount)
        val fields = summary.toFields()
        assertEquals(setOf("schemaVersion", "runId", "startedAtMillis", "finishedAtMillis", "activeDurationMillis", "distanceMeters", "pointCount"), fields.keys)
        assertFalse(fields.values.any { it is TrackPoint || it is List<*> })
        assertFalse(fields.keys.any { it in setOf("points", "latitude", "longitude", "mileage", "reward_points", "verified", "synced", "interrupted", "backupAt") })
    }

    @Test fun activeRecordsCannotBeBackedUp() {
        rejects { CloudRunSummary.fromRunRecord(record().copy(finishedAtMillis = 0)) }
    }

    @Test fun nonFiniteAndOutOfRangeDistanceAreRejected() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.01, 10_000_000.01).forEach { distance ->
            rejects { CloudRunSummary.fromRunRecord(record().copy(distanceMeters = distance)) }
        }
        assertEquals(0.0, CloudRunSummary.fromRunRecord(record().copy(distanceMeters = 0.0)).distanceMeters, 0.0)
        assertEquals(10_000_000.0, CloudRunSummary.fromRunRecord(record().copy(distanceMeters = 10_000_000.0)).distanceMeters, 0.0)
    }

    @Test fun unsafeOrOversizedDocumentIdsAreRejected() {
        listOf("", ".", "..", "run/other", "한글", "run id", "run.id", "a".repeat(129)).forEach { id ->
            rejects { CloudRunSummary.fromRunRecord(record().copy(id = id)) }
        }
        assertEquals("a".repeat(128), CloudRunSummary.fromRunRecord(record().copy(id = "a".repeat(128))).runId)
    }

    @Test fun timestampBoundsAndElapsedTimeCannotOverflow() {
        rejects { CloudRunSummary.fromRunRecord(record().copy(startedAtMillis = -1)) }
        rejects { CloudRunSummary.fromRunRecord(record().copy(startedAtMillis = Long.MIN_VALUE)) }
        rejects { CloudRunSummary.fromRunRecord(record().copy(finishedAtMillis = Long.MAX_VALUE)) }
        rejects { CloudRunSummary.fromRunRecord(record().copy(finishedAtMillis = 999)) }
        val upper = CloudRunSummary.fromRunRecord(record().copy(startedAtMillis = CloudRunSummary.MAX_TIMESTAMP_MILLIS, finishedAtMillis = CloudRunSummary.MAX_TIMESTAMP_MILLIS, activeDurationMillis = 0))
        assertEquals(CloudRunSummary.MAX_TIMESTAMP_MILLIS, upper.finishedAtMillis)
        assertEquals(0L, CloudRunSummary.fromRunRecord(record().copy(startedAtMillis = 0, activeDurationMillis = 0)).startedAtMillis)
    }

    @Test fun activeDurationCannotBeNegativeOrExceedActualElapsedMillis() {
        rejects { CloudRunSummary.fromRunRecord(record().copy(activeDurationMillis = -1)) }
        rejects { CloudRunSummary.fromRunRecord(record().copy(activeDurationMillis = 5_402)) }
        assertEquals(0L, CloudRunSummary.fromRunRecord(record().copy(activeDurationMillis = 0)).activeDurationMillis)
    }

    @Test fun constructorAlsoValidatesSchemaAndPointCount() {
        val summary = CloudRunSummary.fromRunRecord(record())
        rejects { summary.copy(schemaVersion = 2) }
        rejects { summary.copy(pointCount = -1) }
        rejects { summary.copy(pointCount = 1_000_001) }
        assertEquals(1_000_000, summary.copy(pointCount = 1_000_000).pointCount)
    }

    @Test fun factoryDoesNotReadOrCopyRawGpsPoints() {
        val inaccessiblePoints = object : AbstractList<TrackPoint>() {
            override val size: Int = 1_000_000
            override fun get(index: Int): TrackPoint = error("Raw points must not be read")
        }
        assertEquals(1_000_000, CloudRunSummary.fromRunRecord(record().copy(points = inaccessiblePoints)).pointCount)
        val oversizedPoints = object : AbstractList<TrackPoint>() {
            override val size: Int = 1_000_001
            override fun get(index: Int): TrackPoint = error("Raw points must not be read")
        }
        rejects { CloudRunSummary.fromRunRecord(record().copy(points = oversizedPoints)) }
    }

    @Test fun defaultCloudStateDoesNotGrantPilotOrCloudAccess() {
        val state = CloudAccountState()
        assertFalse(state.configured)
        assertEquals("", state.uid)
        assertFalse(state.busy)
        assertTrue(state.summaries.isEmpty())
    }
}

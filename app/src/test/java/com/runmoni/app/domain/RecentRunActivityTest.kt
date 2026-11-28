package com.runmoni.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class RecentRunActivityTest {
    private val today = LocalDate.of(2026, 11, 14)
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun run(id: String, at: String, meters: Double = 1000.0, duration: Long = 360_000) =
        RunRecord(id, Instant.parse(at).toEpochMilli(), distanceMeters = meters, activeDurationMillis = duration)
    private fun RunRecord.completed() = copy(finishedAtMillis = startedAtMillis + activeDurationMillis)

    @Test fun calendarWindowIncludesTodayAndSixPreviousDatesOnly() {
        val result = recentRunActivity(listOf(run("outside", "2026-11-07T14:59:59Z"),
            run("first", "2026-11-07T15:00:00Z"), run("today", "2026-11-14T14:59:59Z", 2500.0),
            run("tomorrow", "2026-11-14T15:00:00Z")), today, seoul, true, false)
        assertEquals((0L..6L).map { today.minusDays(6).plusDays(it) }, result.days.map { it.date })
        assertEquals(RecentRunAvailability.READY, result.availability)
        assertEquals(2, result.days.sumOf { it.runCount })
        assertEquals(3500.0, result.days.sumOf { it.distanceMeters }, 0.0)
        assertEquals(RecentRunTotals(3500.0, 2, 720_000L), result.totals)
        assertEquals(0, result.days[1].runCount)
    }

    @Test fun timezoneChangesReassignTheSameRecordedInstantAndKeepLegacyHistory() {
        val records = listOf(run("legacy", "2026-11-13T15:30:00Z")) // No finishedAt field in legacy history.
        val local = recentRunActivity(records, today, seoul, true, false)
        val utc = recentRunActivity(records, today, ZoneId.of("UTC"), true, false)
        assertEquals(1, local.days.last().runCount)
        assertEquals(1, utc.days[5].runCount)
        assertEquals(0, utc.days.last().runCount)
        assertEquals(360_000L, local.days.last().activeDurationMillis)
        assertNull("A legacy summary may count without being a verified latest result", local.latestCompleted)
    }

    @Test fun unknownHistoryIsNotSevenZeroDaysAndCachedDataStaysLabelled() {
        assertEquals(RecentRunAvailability.LOADING, recentRunActivity(emptyList(), today, seoul, false, false).availability)
        val missing = recentRunActivity(emptyList(), today, seoul, true, true)
        assertEquals(RecentRunAvailability.UNAVAILABLE, missing.availability)
        assertTrue(missing.days.isEmpty())
        val cached = recentRunActivity(listOf(run("one", "2026-11-13T15:30:00Z")), today, seoul, true, true)
        assertEquals(RecentRunAvailability.CACHED, cached.availability)
        assertEquals(1, cached.days.last().runCount)
        assertEquals(7, recentRunActivity(emptyList(), today, seoul, true, false).days.size)
    }

    @Test fun invalidMetricsAreNotPublishedAsPlausibleZerosOrInfiniteTotals() {
        for (bad in listOf(run("bad", "2026-11-13T15:30:00Z", Double.NaN),
            run("bad", "2026-11-13T15:30:00Z", -1.0), run("bad", "2026-11-13T15:30:00Z", duration = -1))) {
            val result = recentRunActivity(listOf(bad), today, seoul, true, false)
            assertEquals(RecentRunAvailability.UNAVAILABLE, result.availability)
            assertTrue(result.days.isEmpty())
        }
        val overflow = listOf(run("a", "2026-11-13T15:30:00Z", Double.MAX_VALUE), run("b", "2026-11-13T15:30:00Z", Double.MAX_VALUE))
        assertEquals(RecentRunAvailability.UNAVAILABLE, recentRunActivity(overflow, today, seoul, true, false).availability)
    }

    @Test fun aDuplicateLegacyCopyFutureRecordsAndRecordOrderCannotReplaceTheLatestCompletedSummary() {
        val restored = run("restored", "2026-11-13T16:00:00Z", 2500.0, 840_000).completed()
        val older = run("older", "2026-11-11T15:30:00Z").completed()
        val future = run("future", "2026-11-14T15:00:00Z", 9000.0).completed()
        val legacyCopy = restored.copy(finishedAtMillis = 0, distanceMeters = 9999.0)
        val sameSummaryAgain = restored.copy(synced = true)
        val legacyOnly = run("legacy-newer", "2026-11-14T08:00:00Z", 500.0, 180_000)
        val invalidFinish = run("bad-time", "2026-11-14T04:00:00Z", 8888.0).let { it.copy(finishedAtMillis = it.startedAtMillis - 1) }
        val result = recentRunActivity(listOf(legacyCopy, future, restored, sameSummaryAgain,
            invalidFinish, legacyOnly, older), today, seoul, initialized = true, historyUnavailable = true)
        assertEquals(RecentRunAvailability.CACHED, result.availability)
        assertEquals(restored, result.latestCompleted)
        assertEquals(RecentRunTotals(4000.0, 3, 1_380_000L), result.totals)
        assertEquals(RecentRunDay(today, 3000.0, 2, 1_020_000L), result.days.last())
    }

    @Test fun anOlderCompletedResultRemainsReachableWhenTheSevenDaysAreEmpty() {
        val older = run("older", "2026-11-07T14:59:59Z").completed()
        val result = recentRunActivity(listOf(older), today, seoul, initialized = true, historyUnavailable = false)
        assertEquals(older, result.latestCompleted)
        assertEquals(RecentRunTotals(0.0, 0, 0L), result.totals)
        assertTrue(result.days.all { it.runCount == 0 })
    }

    @Test fun durationsThatOverflowOnlyWhenCombinedAcrossDaysHaveNoWeeklyTotal() {
        val records = listOf(run("first", "2026-11-12T00:00:00Z", duration = Long.MAX_VALUE),
            run("second", "2026-11-13T00:00:00Z", duration = 1L))
        val result = recentRunActivity(records, today, seoul, initialized = true, historyUnavailable = false)
        assertEquals(RecentRunAvailability.UNAVAILABLE, result.availability)
        assertNull(result.totals)
    }
}

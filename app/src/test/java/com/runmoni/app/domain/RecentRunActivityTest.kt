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

    @Test fun calendarWindowIncludesTodayAndSixPreviousDatesOnly() {
        val result = recentRunActivity(listOf(run("outside", "2026-11-07T14:59:59Z"),
            run("first", "2026-11-07T15:00:00Z"), run("today", "2026-11-14T14:59:59Z", 2500.0),
            run("tomorrow", "2026-11-14T15:00:00Z")), today, seoul, true, false)
        assertEquals((0L..6L).map { today.minusDays(6).plusDays(it) }, result.days.map { it.date })
        assertEquals(RecentRunAvailability.READY, result.availability)
        assertEquals(2, result.days.sumOf { it.runCount })
        assertEquals(3500.0, result.days.sumOf { it.distanceMeters }, 0.0)
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
}

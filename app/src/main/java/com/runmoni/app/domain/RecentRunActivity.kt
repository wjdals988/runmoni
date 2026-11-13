package com.runmoni.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class RecentRunDay(val date: LocalDate, val distanceMeters: Double, val runCount: Int, val activeDurationMillis: Long)
enum class RecentRunAvailability { READY, CACHED, LOADING, UNAVAILABLE }
data class RecentRunActivity(val days: List<RecentRunDay>, val availability: RecentRunAvailability)

/** Calendar days from the caller's canonical history, never the active recording. */
fun recentRunActivity(records: List<RunRecord>, today: LocalDate, zone: ZoneId,
    initialized: Boolean, historyUnavailable: Boolean): RecentRunActivity {
    val availability = when {
        !initialized && records.isEmpty() -> RecentRunAvailability.LOADING
        historyUnavailable && records.isEmpty() -> RecentRunAvailability.UNAVAILABLE
        historyUnavailable || !initialized -> RecentRunAvailability.CACHED
        else -> RecentRunAvailability.READY
    }
    if (availability in setOf(RecentRunAvailability.LOADING, RecentRunAvailability.UNAVAILABLE))
        return RecentRunActivity(emptyList(), availability)
    val first = today.minusDays(6)
    val relevant = records.groupBy { Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).toLocalDate() }
        .filterKeys { !it.isBefore(first) && !it.isAfter(today) }
    val days = (0L..6L).map { offset ->
        val date = first.plusDays(offset)
        val runs = relevant[date].orEmpty()
        if (runs.any { !it.distanceMeters.isFinite() || it.distanceMeters < 0 || it.activeDurationMillis < 0 })
            return RecentRunActivity(emptyList(), RecentRunAvailability.UNAVAILABLE)
        val distance = runs.sumOf { it.distanceMeters }
        val duration = try { runs.fold(0L) { total, run -> Math.addExact(total, run.activeDurationMillis) } }
            catch (_: ArithmeticException) { return RecentRunActivity(emptyList(), RecentRunAvailability.UNAVAILABLE) }
        if (!distance.isFinite()) return RecentRunActivity(emptyList(), RecentRunAvailability.UNAVAILABLE)
        RecentRunDay(date, distance, runs.size, duration)
    }
    return RecentRunActivity(days, availability)
}

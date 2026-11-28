package com.runmoni.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class RecentRunDay(val date: LocalDate, val distanceMeters: Double, val runCount: Int, val activeDurationMillis: Long)
enum class RecentRunAvailability { READY, CACHED, LOADING, UNAVAILABLE }
data class RecentRunTotals(val distanceMeters: Double, val runCount: Int, val activeDurationMillis: Long)
data class RecentRunActivity(
    val days: List<RecentRunDay>,
    val availability: RecentRunAvailability,
    val totals: RecentRunTotals? = null,
    val latestCompleted: RunRecord? = null,
)

/** Calendar days from the caller's canonical history, never the active recording. */
fun recentRunActivity(records: List<RunRecord>, today: LocalDate, zone: ZoneId,
    initialized: Boolean, historyUnavailable: Boolean): RecentRunActivity {
    val datedRuns = records.mapNotNull { record ->
        if (record.id.isBlank() || record.startedAtMillis <= 0 ||
            (record.finishedAtMillis != 0L && record.finishedAtMillis < record.startedAtMillis)) return@mapNotNull null
        val date = Instant.ofEpochMilli(record.startedAtMillis).atZone(zone).toLocalDate()
        if (date > today) null else record to date
    }
    // Old saved summaries may lack a finish timestamp. Prefer a completed copy
    // of the same ID when both exist, while keeping saved legacy week totals.
    val completed = datedRuns.filter { (record, _) -> record.finishedAtMillis > 0 }
    val uniqueRuns = (completed + datedRuns).distinctBy { (record, _) -> record.id }
    val availability = when {
        !initialized && uniqueRuns.isEmpty() -> RecentRunAvailability.LOADING
        historyUnavailable && uniqueRuns.isEmpty() -> RecentRunAvailability.UNAVAILABLE
        historyUnavailable || !initialized -> RecentRunAvailability.CACHED
        else -> RecentRunAvailability.READY
    }
    if (availability in setOf(RecentRunAvailability.LOADING, RecentRunAvailability.UNAVAILABLE))
        return RecentRunActivity(emptyList(), availability)
    val latest = uniqueRuns.asSequence().map { (record, _) -> record }
        .filter { it.finishedAtMillis > 0 && it.hasReadableActivityMetrics() }
        .maxWithOrNull(compareBy(RunRecord::startedAtMillis, RunRecord::finishedAtMillis, RunRecord::id))
    val first = today.minusDays(6)
    val thisWeek = uniqueRuns.filter { (_, date) -> date >= first }
    val runs = thisWeek.map { (record, _) -> record }
    val unavailable = RecentRunActivity(emptyList(), RecentRunAvailability.UNAVAILABLE, latestCompleted = latest)
    if (runs.any { !it.hasReadableActivityMetrics() }) return unavailable
    val weeklyDistance = runs.sumOf { it.distanceMeters }
    if (!weeklyDistance.isFinite()) return unavailable
    val weeklyDuration = try { runs.fold(0L) { total, run -> Math.addExact(total, run.activeDurationMillis) } }
        catch (_: ArithmeticException) { return unavailable }
    val relevant = thisWeek.groupBy({ (_, date) -> date }, { (record, _) -> record })
    val days = (0L..6L).map { offset ->
        val date = first.plusDays(offset)
        val dailyRuns = relevant[date].orEmpty()
        RecentRunDay(date, dailyRuns.sumOf { it.distanceMeters }, dailyRuns.size, dailyRuns.sumOf { it.activeDurationMillis })
    }
    return RecentRunActivity(days, availability, RecentRunTotals(weeklyDistance, runs.size, weeklyDuration), latest)
}

private fun RunRecord.hasReadableActivityMetrics() = distanceMeters.isFinite() && distanceMeters >= 0 && activeDurationMillis >= 0

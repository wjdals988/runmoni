package com.runmoni.app.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

internal data class RunResultSplit(val kilometer: Int, val paceSeconds: Long)

internal data class RunPaceScale(val ticksSeconds: List<Long>) {
    val fastestSeconds: Long get() = ticksSeconds.first()
    val slowestSeconds: Long get() = ticksSeconds.last()
}

internal data class RunResultAnalysis(
    val splits: List<RunResultSplit>,
    val fastestSplits: List<RunResultSplit>,
    val averagePaceSeconds: Long?,
    val paceScale: RunPaceScale?,
    val elapsedMillis: Long?,
    val hasMissingSplits: Boolean,
) {
    companion object {
        fun from(record: RunRecord): RunResultAnalysis {
            val distance = record.distanceMeters.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            val activeMillis = record.activeDurationMillis.coerceAtLeast(0)
            val wholeKilometers = (distance / 1_000.0).toInt()
            val candidates = record.splits.groupBy(KmSplit::kilometer)
                .mapNotNull { (kilometer, entries) ->
                    entries.singleOrNull()?.takeIf { kilometer in 1..wholeKilometers && it.durationMillis in 1_000L..activeMillis }
                }
                .sortedBy(KmSplit::kilometer)

            // The durations of disjoint completed kilometers fit inside the active timer.
            // Subtraction also handles malformed historical values without an overflow.
            var timeRemaining = activeMillis
            val splits = if (candidates.all { split ->
                    if (split.durationMillis > timeRemaining) false
                    else {
                        timeRemaining -= split.durationMillis
                        true
                    }
                }) candidates.map { RunResultSplit(it.kilometer, it.durationMillis / 1_000) } else emptyList()

            val fastestPace = splits.minOfOrNull { it.paceSeconds }
            val fastest = if (splits.size >= 2 && !record.interrupted) splits.filter { it.paceSeconds == fastestPace }
                else emptyList()
            val average = if (distance >= 10 && activeMillis > 0) (activeMillis / distance).toLong().takeIf { it > 0 }
                else null
            val elapsed = record.elapsedDurationMillis.takeIf { activeMillis > 0 && it / 1_000 > activeMillis / 1_000 }
            val scale = if (splits.size >= 2) scaleFor(splits.map { it.paceSeconds } + listOfNotNull(average)) else null
            return RunResultAnalysis(splits, fastest, average, scale, elapsed, wholeKilometers > splits.size)
        }

        private fun scaleFor(paces: List<Long>): RunPaceScale {
            val minimum = paces.min().toDouble()
            val maximum = paces.max().toDouble()
            val wantedStep = ((maximum - minimum) / 3).coerceAtLeast(10.0)
            val magnitude = 10.0.pow(floor(log10(wantedStep)))
            val step = listOf(1, 2, 3, 5, 10).first { it * magnitude >= wantedStep } * magnitude
            val first = (floor(minimum / step).toLong() - 1).coerceAtLeast(0)
            val last = ceil(maximum / step).toLong().coerceAtLeast(first + 2)
            return RunPaceScale((first..last).map { (it * step).toLong() })
        }
    }
}

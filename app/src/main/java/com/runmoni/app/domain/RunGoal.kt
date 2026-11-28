package com.runmoni.app.domain

import java.util.Locale
import kotlin.math.ceil

enum class RunGoalKind { DISTANCE, DURATION }

/** A whole-kilometre or whole-minute target attached to a single local run. */
data class RunGoal(val kind: RunGoalKind, val target: Int) {
    init { require(validTarget(kind, target)) }

    val label: String get() = when (kind) {
        RunGoalKind.DISTANCE -> "$target km"
        RunGoalKind.DURATION -> "${target}분"
    }

    fun progress(distanceMeters: Double, activeDurationMillis: Long): RunGoalProgress {
        val distanceBased = kind == RunGoalKind.DISTANCE
        val end = target * if (distanceBased) 1_000.0 else 60_000.0
        val current = if (distanceBased) distanceMeters.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            else activeDurationMillis.coerceAtLeast(0L).toDouble()
        val remaining = (end - current).coerceAtLeast(0.0)
        val label = if (distanceBased) {
            if (remaining < 1_000) "${ceil(remaining).toInt()} m"
            else String.format(Locale.KOREA, "%.2f km", ceil(remaining / 10) / 100)
        } else {
            val seconds = ceil(remaining / 1_000).toLong()
            String.format(Locale.KOREA, "%d:%02d", seconds / 60, seconds % 60)
        }
        return RunGoalProgress(current >= end, (current / end).coerceIn(0.0, 1.0).toFloat(), label)
    }

    companion object {
        fun fromStorage(kind: String?, target: Int): RunGoal? {
            val type = RunGoalKind.entries.firstOrNull { it.name == kind } ?: return null
            return if (validTarget(type, target)) RunGoal(type, target) else null
        }

        private fun validTarget(kind: RunGoalKind, target: Int) = when (kind) {
            RunGoalKind.DISTANCE -> target in 1..100
            RunGoalKind.DURATION -> target in 1..600
        }
    }
}

data class RunGoalProgress(val reached: Boolean, val fraction: Float, val remainingLabel: String)

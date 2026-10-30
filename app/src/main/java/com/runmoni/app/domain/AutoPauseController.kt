package com.runmoni.app.domain

/** Decisions use monotonic time and consecutive, recent fixes; the repository owns pause state. */
class AutoPauseController {
    enum class Decision { NONE, PAUSE, RESUME }

    private var stationarySince: Long? = null
    private var lastSampleAt: Long? = null

    fun reset() { stationarySince = null; lastSampleAt = null }

    fun onTick(now: Long, enabled: Boolean, phase: RunPhase, reason: PauseReason?): Decision {
        if (!enabled || phase != RunPhase.RUNNING || lastSampleAt?.let { now - it !in 0..MAX_GAP_MS } == true) reset()
        return if (!enabled && phase == RunPhase.PAUSED && reason == PauseReason.AUTOMATIC) Decision.RESUME else Decision.NONE
    }

    fun onLocation(now: Long, enabled: Boolean, phase: RunPhase, reason: PauseReason?, speed: Float?, accuracy: Float?): Decision {
        val settingDecision = onTick(now, enabled, phase, reason)
        if (!enabled) return settingDecision
        if (speed == null || !speed.isFinite() || speed < 0 || accuracy == null || !accuracy.isFinite() || accuracy !in 0f..20f) {
            reset()
            return Decision.NONE
        }
        lastSampleAt = now
        if (phase == RunPhase.PAUSED) {
            return if (reason == PauseReason.AUTOMATIC && speed >= 1f) Decision.RESUME else Decision.NONE
        }
        if (phase != RunPhase.RUNNING || speed >= 0.5f) {
            stationarySince = null
            return Decision.NONE
        }
        val start = stationarySince ?: now.also { stationarySince = it }
        return if (now - start >= STOP_DURATION_MS) {
            reset()
            Decision.PAUSE
        } else Decision.NONE
    }

    companion object {
        private const val STOP_DURATION_MS = 10_000L
        private const val MAX_GAP_MS = 5_000L
    }
}

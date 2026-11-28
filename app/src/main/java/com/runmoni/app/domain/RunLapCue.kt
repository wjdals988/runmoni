package com.runmoni.app.domain

internal data class RunLapCue(val kilometer: Int, val speech: String?, val vibrate: Boolean)

/** Observes the live recording once per completed kilometer, even when its cues are disabled. */
internal class RunLapCueTracker {
    private var runId = ""
    private var observedKilometer = 0

    fun start(run: ActiveRun) {
        runId = run.id
        observedKilometer = completedKilometer(run.distanceMeters)
    }

    fun observe(
        run: ActiveRun,
        live: Boolean,
        voice: Boolean,
        includePace: Boolean,
        vibration: Boolean,
    ): RunLapCue? {
        if (runId.isBlank() || run.id != runId) return null
        val kilometer = completedKilometer(run.distanceMeters)
        if (kilometer <= observedKilometer) return null
        // A pause, muted option or unavailable engine must not queue past cues for later.
        observedKilometer = kilometer
        if (!live || run.phase != RunPhase.RUNNING || (!voice && !vibration)) return null

        val speech = if (voice) distanceSpeech(kilometer, run, includePace) else null
        return RunLapCue(kilometer, speech, vibration)
    }

    private fun distanceSpeech(kilometer: Int, run: ActiveRun, includePace: Boolean): String {
        val distance = "${kilometer}킬로미터 달렸어요"
        val split = run.lastCompletedSplit
        if (!includePace || split == null || split.kilometer != kilometer || split.durationMillis !in 1_000L..run.activeDurationMillis)
            return distance
        val paceSeconds = split.durationMillis / 1_000
        val minutes = paceSeconds / 60
        val seconds = paceSeconds % 60
        return "$distance. 이번 1킬로미터는 ${minutes}분 ${seconds}초예요."
    }

    private fun completedKilometer(meters: Double): Int =
        if (meters.isFinite() && meters >= 1_000) (meters / 1_000).toInt() else 0
}

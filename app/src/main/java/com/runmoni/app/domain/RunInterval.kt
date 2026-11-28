package com.runmoni.app.domain

/** One set is a timed run followed by timed recovery, including the final set. */
data class RunIntervalPlan(
    val runSeconds: Int,
    val recoverySeconds: Int,
    val repeats: Int,
    val vibration: Boolean = false,
) {
    init {
        require(runSeconds in PhaseSeconds && recoverySeconds in PhaseSeconds && repeats in RepeatCount)
    }

    private val runMillis get() = runSeconds * 1_000L
    private val setMillis get() = runMillis + recoverySeconds * 1_000L

    internal fun progress(activeMillis: Long): RunIntervalProgress {
        val elapsed = activeMillis.coerceAtLeast(0)
        val total = setMillis * repeats
        if (elapsed >= total) return RunIntervalProgress(
            RunIntervalStage.COMPLETE, repeats * 2, repeats, repeats, repeats, elapsed - total, 0,
        )

        val completeSets = (elapsed / setMillis).toInt()
        val inSet = elapsed % setMillis
        val recovering = inSet >= runMillis
        return RunIntervalProgress(
            stage = if (recovering) RunIntervalStage.RECOVERY else RunIntervalStage.RUN,
            index = completeSets * 2 + if (recovering) 1 else 0,
            repeat = completeSets + 1,
            completedRuns = completeSets + if (recovering) 1 else 0,
            completedRecoveries = completeSets,
            stageElapsedMillis = if (recovering) inSet - runMillis else inSet,
            stageRemainingMillis = (if (recovering) setMillis else runMillis) - inSet,
        )
    }

    internal fun setResults(activeMillis: Long): List<RunIntervalSetResult> = (1..repeats).mapNotNull { repeat ->
        val elapsed = activeMillis.coerceAtLeast(0) - (repeat - 1) * setMillis
        if (elapsed <= 0) null else RunIntervalSetResult(
            repeat, elapsed.coerceAtMost(runMillis), (elapsed - runMillis).coerceIn(0, setMillis - runMillis),
        )
    }

    companion object {
        internal val PhaseSeconds = 10..600
        internal val RepeatCount = 2..20
        internal val Default = RunIntervalPlan(runSeconds = 60, recoverySeconds = 60, repeats = 6)

        internal fun fromStorage(runSeconds: Int, recoverySeconds: Int, repeats: Int, vibration: Boolean): RunIntervalPlan? =
            if (runSeconds in PhaseSeconds && recoverySeconds in PhaseSeconds && repeats in RepeatCount)
                RunIntervalPlan(runSeconds, recoverySeconds, repeats, vibration)
            else null
    }
}

internal enum class RunIntervalStage(val label: String) {
    RUN("달리기"), RECOVERY("회복"), COMPLETE("인터벌 완료"),
}

internal data class RunIntervalProgress(
    val stage: RunIntervalStage,
    val index: Int,
    val repeat: Int,
    val completedRuns: Int,
    val completedRecoveries: Int,
    val stageElapsedMillis: Long,
    val stageRemainingMillis: Long,
)

internal data class RunIntervalSetResult(val repeat: Int, val runMillis: Long, val recoveryMillis: Long)

/** A recovered run starts at its saved stage. Missed, paused and muted cues never queue. */
internal class RunIntervalCueTracker {
    private var runId = ""
    private var observedIndex = 0

    fun start(run: ActiveRun) {
        runId = run.id
        observedIndex = run.interval?.progress(run.activeDurationMillis)?.index ?: 0
    }

    fun observe(run: ActiveRun, live: Boolean, checkpointedActiveMillis: Long?): RunIntervalProgress? {
        if (runId.isBlank() || run.id != runId) return null
        val plan = run.interval ?: return null
        val progress = plan.progress(run.activeDurationMillis)
        if (progress.index <= observedIndex) return null
        // Announcing an unsaved boundary could replay it after an abrupt process stop.
        if (live && (checkpointedActiveMillis == null || plan.progress(checkpointedActiveMillis).index < progress.index)) return null
        observedIndex = progress.index
        return progress.takeIf {
            live && run.phase == RunPhase.RUNNING && it.stageElapsedMillis <= MaxCueDelayMillis
        }
    }

    private companion object {
        const val MaxCueDelayMillis = 2_500L
    }
}

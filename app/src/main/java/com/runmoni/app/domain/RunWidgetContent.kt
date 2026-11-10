package com.runmoni.app.domain

import java.util.Locale

internal enum class RunWidgetControl { PAUSE, RESUME }

/** Only the last widget metrics. No locations, route, profile, or credentials. */
internal data class RunWidgetSnapshot(
    val runId: String,
    val phase: RunPhase,
    val distanceMeters: Double,
    val activeDurationMillis: Long,
    val speedMetersPerSecond: Double?,
    val automaticPause: Boolean,
    val blocked: Boolean,
    val finishing: Boolean,
    val updatedAtMillis: Long,
) {
    companion object {
        fun from(run: ActiveRun, storage: RunPersistenceState, nowMillis: Long): RunWidgetSnapshot = RunWidgetSnapshot(
            runId = run.id,
            phase = run.phase,
            distanceMeters = run.distanceMeters.takeIf { it.isFinite() && it >= 0 } ?: 0.0,
            activeDurationMillis = run.activeDurationMillis.coerceAtLeast(0),
            speedMetersPerSecond = run.currentSpeedMetersPerSecond?.takeIf {
                run.phase == RunPhase.RUNNING && it.isFinite() && it >= 0
            },
            automaticPause = run.pauseReason == PauseReason.AUTOMATIC,
            blocked = !storage.initialized || storage.issue in setOf(RunPersistenceIssue.CHECKPOINT, RunPersistenceIssue.FINISH),
            finishing = storage.finishing,
            updatedAtMillis = nowMillis.coerceAtLeast(0),
        )
    }
}

internal data class RunWidgetContent(
    val title: String,
    val distance: String,
    val duration: String,
    val speed: String,
    val control: RunWidgetControl?,
    val canFinish: Boolean,
    val stale: Boolean,
) {
    companion object {
        fun from(snapshot: RunWidgetSnapshot?, live: Boolean): RunWidgetContent {
            val idle = snapshot == null || snapshot.runId.isBlank() || snapshot.phase == RunPhase.IDLE
            if (idle) return RunWidgetContent(
                if (snapshot?.blocked == true || snapshot?.finishing == true) "러닝 저장 상태 확인" else "러닝 시작 준비",
                "— km", "—", "현재 속도 —", null, false, false)
            requireNotNull(snapshot)
            val stale = !live || snapshot.phase == RunPhase.RECOVERY
            val controlled = !stale && !snapshot.blocked && !snapshot.finishing &&
                snapshot.phase in setOf(RunPhase.RUNNING, RunPhase.PAUSED)
            val seconds = snapshot.activeDurationMillis.coerceAtLeast(0) / 1000
            val duration = if (seconds >= 3600) String.format(Locale.KOREA, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                else String.format(Locale.KOREA, "%02d:%02d", seconds / 60, seconds % 60)
            val speed = snapshot.speedMetersPerSecond?.takeIf {
                controlled && snapshot.phase == RunPhase.RUNNING && it.isFinite() && it >= 0
            }
            return RunWidgetContent(
                title = when {
                    snapshot.phase == RunPhase.RECOVERY -> "중단된 러닝 · 앱에서 확인"
                    stale -> "마지막 러닝 정보"
                    snapshot.blocked || snapshot.finishing -> "러닝 저장 상태 확인"
                    snapshot.phase == RunPhase.PAUSED && snapshot.automaticPause -> "자동 일시정지"
                    snapshot.phase == RunPhase.PAUSED -> "일시정지 중"
                    else -> "러닝 중"
                },
                distance = String.format(Locale.KOREA, "%.2f km", snapshot.distanceMeters.takeIf { it.isFinite() && it >= 0 }?.div(1000) ?: 0.0),
                duration = duration,
                speed = speed?.let { String.format(Locale.KOREA, "현재 %.1f km/h", it * 3.6) } ?: "현재 속도 —",
                control = if (!controlled) null else if (snapshot.phase == RunPhase.PAUSED) RunWidgetControl.RESUME else RunWidgetControl.PAUSE,
                canFinish = controlled,
                stale = stale,
            )
        }
    }
}

/** This marker is never persisted: a widget update cannot resurrect a recorder. */
internal class RunWidgetSession(private val runningFreshnessMillis: Long = 20_000) {
    private var liveRunId: String? = null
    private var heartbeatElapsed = 0L

    @Synchronized fun processStarted() { liveRunId = null; heartbeatElapsed = 0 }

    @Synchronized fun observe(snapshot: RunWidgetSnapshot, live: Boolean, elapsedMillis: Long) {
        if (live && elapsedMillis >= 0 && snapshot.runId.isNotBlank() && snapshot.phase in setOf(RunPhase.RUNNING, RunPhase.PAUSED)) {
            liveRunId = snapshot.runId
            heartbeatElapsed = elapsedMillis
        } else if (snapshot.runId == liveRunId || snapshot.phase == RunPhase.IDLE) {
            liveRunId = null
        }
    }

    @Synchronized fun stopped(expectedRunId: String? = null) {
        if (expectedRunId == null || expectedRunId == liveRunId) liveRunId = null
    }

    @Synchronized fun isLive(snapshot: RunWidgetSnapshot?, elapsedMillis: Long): Boolean {
        if (snapshot == null || snapshot.runId.isBlank() || snapshot.runId != liveRunId) return false
        // A manually paused recorder releases its wake lock. Sleeping must not
        // make Resume unavailable while the same Service is still alive.
        if (snapshot.phase == RunPhase.PAUSED && !snapshot.automaticPause) return true
        return elapsedMillis >= heartbeatElapsed && elapsedMillis - heartbeatElapsed <= runningFreshnessMillis
    }

    @Synchronized fun allows(expectedRunId: String?, snapshot: RunWidgetSnapshot?, control: RunWidgetControl): Boolean {
        if (expectedRunId.isNullOrBlank() || expectedRunId != liveRunId || expectedRunId != snapshot?.runId) return false
        // The Service performs the second check against its actual current run,
        // foreground state and persistence transaction, after delivery.
        return RunWidgetContent.from(snapshot, live = true).control == control
    }
}

package com.runmoni.app.domain

import java.util.Locale

data class RunNotificationAccess(val checked: Boolean = false, val enabled: Boolean = false) {
    val isBlocked: Boolean get() = checked && !enabled
}

data class RunNavigationRequest(val token: Long, val runId: String, val confirmFinish: Boolean)

internal enum class RunNotificationControl { PAUSE, RESUME }

/** Plain text for the platform notification; never includes coordinates or account details. */
internal data class RunNotificationContent(
    val title: String,
    val metrics: String,
    val detail: String,
    val control: RunNotificationControl?,
    val canFinish: Boolean,
) {
    companion object {
        fun from(run: ActiveRun, storage: RunPersistenceState): RunNotificationContent {
            val saving = storage.finishing || storage.issue == RunPersistenceIssue.FINISH
            val blocked = storage.issue == RunPersistenceIssue.CHECKPOINT
            val active = run.phase in setOf(RunPhase.RUNNING, RunPhase.PAUSED)
            val title = when {
                saving -> "러닝 종료 · 저장 확인"
                blocked -> "러닝 기록 · 저장 확인"
                run.phase == RunPhase.RECOVERY -> "러닝 복구 확인"
                run.phase == RunPhase.IDLE -> "러닝 시작 준비"
                run.phase == RunPhase.PAUSED && run.pauseReason == PauseReason.AUTOMATIC -> "러닝 · 자동 일시정지"
                run.phase == RunPhase.PAUSED -> "러닝 · 일시정지"
                else -> "러닝 중"
            }
            val distance = run.distanceMeters.takeIf { it.isFinite() && it >= 0 } ?: 0.0
            val seconds = run.activeDurationMillis.coerceAtLeast(0) / 1000
            val time = if (seconds >= 3600) String.format(Locale.KOREA, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                else String.format(Locale.KOREA, "%02d:%02d", seconds / 60, seconds % 60)
            val metrics = String.format(Locale.KOREA, "%.2f km · %s", distance / 1000, time)
            val speed = run.currentSpeedMetersPerSecond?.takeIf { run.phase == RunPhase.RUNNING && it.isFinite() && it >= 0 }
            val pace = run.currentPaceSecondsPerKm?.takeIf { run.phase == RunPhase.RUNNING && speed != null && it > 0 }
            val detail = when {
                saving || blocked -> "앱을 열어 저장 상태를 확인해 주세요"
                run.phase == RunPhase.RECOVERY -> "저장한 지점에서 이어갈 수 있어요"
                run.phase == RunPhase.PAUSED -> "러닝 시간과 거리를 잠시 멈췄어요"
                speed == null -> "GPS 신호 확인 중 · 현재 속도 —"
                else -> String.format(Locale.KOREA, "현재 %.1f km/h · 페이스 %s /km", speed * 3.6,
                    pace?.let { "%d:%02d".format(Locale.KOREA, it / 60, it % 60) } ?: "—")
            }
            return RunNotificationContent(title, metrics, detail, when {
                !active || saving || blocked -> null
                run.phase == RunPhase.PAUSED -> RunNotificationControl.RESUME
                else -> RunNotificationControl.PAUSE
            }, active && !saving && !blocked)
        }
    }
}

/** A delivered old notification may never operate on another run or resurrect a recorder. */
internal object RunControlPolicy {
    fun allows(control: RunNotificationControl, expectedRunId: String?, serviceRunId: String?, run: ActiveRun,
               storage: RunPersistenceState): Boolean {
        if (expectedRunId.isNullOrBlank() || expectedRunId != run.id || serviceRunId != run.id || storage.finishing ||
            storage.issue in setOf(RunPersistenceIssue.FINISH, RunPersistenceIssue.CHECKPOINT)) return false
        return when (control) {
            RunNotificationControl.PAUSE -> run.phase in setOf(RunPhase.RUNNING, RunPhase.PAUSED)
            RunNotificationControl.RESUME -> run.phase == RunPhase.PAUSED
        }
    }
}

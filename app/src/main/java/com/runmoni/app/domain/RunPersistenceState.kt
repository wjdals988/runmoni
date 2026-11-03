package com.runmoni.app.domain

enum class RunPersistenceIssue { RECOVERY, CHECKPOINT, FINISH, CREDIT }

/** Local durability and reward processing are separate from GPS and cloud backup. */
data class RunPersistenceState(
    val issue: RunPersistenceIssue? = null,
    val initialized: Boolean = false,
    val saving: Boolean = false,
    val lastSavedAtMillis: Long? = null,
    val unsavedPointCount: Int = 0,
    val finishing: Boolean = false,
    val creditPending: Boolean = false,
    val pendingCreditRunIds: Set<String> = emptySet(),
    val historyUnavailable: Boolean = false,
) {
    val blocksNewRun: Boolean get() = !initialized || finishing
    val canRetry: Boolean get() = (issue != null || historyUnavailable) && !saving
    val message: String? get() = when (issue) {
        RunPersistenceIssue.RECOVERY -> "기기 기록을 다시 확인해야 해요. 확인을 마친 뒤 러닝을 시작할 수 있어요."
        RunPersistenceIssue.CHECKPOINT -> "기기 기록을 저장하지 못해 러닝을 멈췄어요. 앱을 닫지 말고 저장을 다시 시도해 주세요."
        RunPersistenceIssue.FINISH -> "러닝 종료 기록을 저장하지 못했어요. 앱을 닫지 말고 저장을 다시 시도해 주세요."
        RunPersistenceIssue.CREDIT -> if (pendingCreditRunIds.isEmpty()) "이전 기록의 포인트 적립 내역을 확인하지 못했어요. 다시 확인해 주세요."
            else "러닝 기록은 이 기기에 저장됐어요. 포인트 적립을 다시 확인해 주세요."
        null -> if (historyUnavailable) "기기 기록 목록을 읽지 못했어요. 다시 불러와 주세요." else null
    }
}

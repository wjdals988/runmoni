package com.runmoni.app.domain

/** A self-reported backup. It contains no route, wallet or server-verification data. */
data class CloudRunSummary(
    val schemaVersion: Int = 1,
    val runId: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val activeDurationMillis: Long,
    val distanceMeters: Double,
    val pointCount: Int,
) {
    init {
        require(schemaVersion == 1) { "지원하지 않는 백업 형식입니다." }
        validateRunId(runId)
        require(startedAtMillis in 0..MAX_TIMESTAMP_MILLIS) { "시작 시각을 확인해 주세요." }
        require(finishedAtMillis in 1..MAX_TIMESTAMP_MILLIS && finishedAtMillis >= startedAtMillis) { "완료 시각을 확인해 주세요." }
        require(activeDurationMillis in 0..(finishedAtMillis - startedAtMillis)) { "활동 시간을 확인해 주세요." }
        require(distanceMeters.isFinite() && distanceMeters in 0.0..MAX_DISTANCE_METERS) { "거리를 확인해 주세요." }
        require(pointCount in 0..MAX_POINT_COUNT) { "GPS 측정 개수를 확인해 주세요." }
    }

    /** Explicit whitelist: never serialize RunRecord directly into the cloud. */
    fun toFields(): Map<String, Any> = linkedMapOf(
        "schemaVersion" to schemaVersion,
        "runId" to runId,
        "startedAtMillis" to startedAtMillis,
        "finishedAtMillis" to finishedAtMillis,
        "activeDurationMillis" to activeDurationMillis,
        "distanceMeters" to distanceMeters,
        "pointCount" to pointCount,
    )

    companion object {
        const val MAX_TIMESTAMP_MILLIS = 253402300799999L
        const val MAX_DISTANCE_METERS = 10_000_000.0
        const val MAX_POINT_COUNT = 1_000_000
        private val SAFE_RUN_ID = Regex("^[A-Za-z0-9_-]{1,128}$")

        fun validateRunId(runId: String) {
            require(SAFE_RUN_ID.matches(runId)) { "러닝 기록 ID를 확인해 주세요." }
        }

        fun fromRunRecord(record: RunRecord): CloudRunSummary {
            require(record.finishedAtMillis > 0) { "완료된 기록만 백업할 수 있습니다." }
            return CloudRunSummary(
                runId = record.id,
                startedAtMillis = record.startedAtMillis,
                finishedAtMillis = record.finishedAtMillis,
                activeDurationMillis = record.activeDurationMillis,
                distanceMeters = record.distanceMeters,
                pointCount = record.points.size,
            )
        }
    }
}

data class CloudAccountState(
    val configured: Boolean = false,
    val uid: String = "",
    val accountLabel: String = "",
    val busy: Boolean = false,
    val status: String = "클라우드 백업 설정 준비 중",
    val summaries: List<CloudRunSummary> = emptyList(),
    val interactiveSignInAt: Long = 0,
    val sessionCheckedAt: Long = 0,
)

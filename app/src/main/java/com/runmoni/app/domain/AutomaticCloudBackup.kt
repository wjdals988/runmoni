package com.runmoni.app.domain

import java.security.MessageDigest

enum class AutomaticBackupRecordState { WAITING, SAVED, RETRYING, CONFLICT, EXCLUDED }

/** Local queue/ACK state only; the cloud account remains the identity authority. */
data class AutomaticCloudBackupState(
    val records: Map<String, AutomaticBackupRecordState> = emptyMap(),
    val initialized: Boolean = false,
) {
    val waitingCount get() = records.values.count { it == AutomaticBackupRecordState.WAITING || it == AutomaticBackupRecordState.RETRYING }
    val savedCount get() = records.values.count { it == AutomaticBackupRecordState.SAVED }
    val reviewCount get() = records.values.count { it == AutomaticBackupRecordState.CONFLICT }
    fun record(id: String) = records[id] ?: if (initialized) AutomaticBackupRecordState.EXCLUDED else AutomaticBackupRecordState.WAITING
}

data class CloudBackupSession(val uid: String, val epoch: Long)
enum class CloudBackupWriteResult { ACKNOWLEDGED, RETRY, CONFLICT, STALE_SESSION }
enum class CloudBackupReconcileResult { COMPLETE, MORE_PENDING, RETRY, STALE_SESSION }
data class PendingCloudBackup(val summary: CloudRunSummary, val fingerprint: String = summary.backupFingerprint())

/** Fingerprint only the existing public summary whitelist; never GPS or a wallet. */
fun CloudRunSummary.backupFingerprint(): String = MessageDigest.getInstance("SHA-256").digest(
    listOf(schemaVersion, runId, startedAtMillis, finishedAtMillis, activeDurationMillis,
        distanceMeters.toBits(), pointCount).joinToString("|").toByteArray(Charsets.UTF_8),
).joinToString("") { "%02x".format(it) }

interface AutomaticCloudBackupAccount {
    fun backupSession(expectedUid: String? = null): CloudBackupSession?
    fun isBackupSessionCurrent(session: CloudBackupSession): Boolean
    suspend fun writeAutomaticSummary(summary: CloudRunSummary, session: CloudBackupSession): CloudBackupWriteResult
}

interface AutomaticCloudBackupLedger {
    suspend fun claimUnowned(uid: String)
    suspend fun pending(uid: String, limit: Int): List<PendingCloudBackup>
    suspend fun acknowledge(uid: String, pending: PendingCloudBackup): Boolean
    suspend fun failure(uid: String, runId: String, conflict: Boolean)
}

/** Worker-independent policy. A completed Task is not an ACK for a different session. */
class CloudBackupReconciler(
    private val ledger: AutomaticCloudBackupLedger,
    private val account: AutomaticCloudBackupAccount,
) {
    suspend fun reconcile(expectedUid: String? = null): CloudBackupReconcileResult {
        val session = account.backupSession(expectedUid) ?: return CloudBackupReconcileResult.STALE_SESSION
        ledger.claimUnowned(session.uid)
        if (!account.isBackupSessionCurrent(session)) return CloudBackupReconcileResult.STALE_SESSION
        val entries = ledger.pending(session.uid, BATCH_SIZE + 1)
        if (!account.isBackupSessionCurrent(session)) return CloudBackupReconcileResult.STALE_SESSION
        val more = entries.size > BATCH_SIZE
        for (pending in entries.take(BATCH_SIZE)) {
            if (!account.isBackupSessionCurrent(session)) return CloudBackupReconcileResult.STALE_SESSION
            val result = account.writeAutomaticSummary(pending.summary, session)
            when (result) {
                CloudBackupWriteResult.STALE_SESSION -> return CloudBackupReconcileResult.STALE_SESSION
                CloudBackupWriteResult.ACKNOWLEDGED -> {
                    if (!account.isBackupSessionCurrent(session)) return CloudBackupReconcileResult.STALE_SESSION
                    ledger.acknowledge(session.uid, pending)
                }
                CloudBackupWriteResult.RETRY, CloudBackupWriteResult.CONFLICT -> {
                    if (!account.isBackupSessionCurrent(session)) return CloudBackupReconcileResult.STALE_SESSION
                    ledger.failure(session.uid, pending.summary.runId, conflict = result == CloudBackupWriteResult.CONFLICT)
                    // A network/SDK retry must not create fifty long-lived offline
                    // Tasks or consume the worker's full runtime on one failed batch.
                    if (result == CloudBackupWriteResult.RETRY) return CloudBackupReconcileResult.RETRY
                }
            }
        }
        return if (more) CloudBackupReconcileResult.MORE_PENDING else CloudBackupReconcileResult.COMPLETE
    }

    companion object { const val BATCH_SIZE = 50 }
}

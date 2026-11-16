package com.runmoni.app.data

import androidx.room.*
import com.runmoni.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/** Created only by local recording (or the explicit v1 local-record migration). */
@Entity(tableName = "cloud_backup_origins", foreignKeys = [ForeignKey(entity = StoredRun::class,
    parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE)])
data class StoredCloudBackupOrigin(
    @PrimaryKey val runId: String,
    @ColumnInfo(defaultValue = "''") val ownerUid: String = "",
    @ColumnInfo(defaultValue = "''") val acknowledgedFingerprint: String = "",
    @ColumnInfo(defaultValue = "0") val acknowledgedAtMillis: Long = 0,
    @ColumnInfo(defaultValue = "0") val suppressed: Boolean = false,
    @ColumnInfo(defaultValue = "''") val failure: String = "",
    @ColumnInfo(defaultValue = "0") val pointCount: Int = 0,
)

data class CloudBackupCandidate(
    val runId: String, val startedAtMillis: Long, val finishedAtMillis: Long,
    val distanceMeters: Double, val activeDurationMillis: Long, val pointCount: Int,
    val ownerUid: String, val acknowledgedFingerprint: String, val suppressed: Boolean, val failure: String,
) {
    fun summary() = CloudRunSummary(runId = runId, startedAtMillis = startedAtMillis,
        finishedAtMillis = finishedAtMillis, activeDurationMillis = activeDurationMillis,
        distanceMeters = distanceMeters, pointCount = pointCount)
}

@Dao
interface CloudBackupDao {
    @Query("""SELECT r.id AS runId, r.startedAtMillis, r.finishedAtMillis, r.distanceMeters, r.activeDurationMillis,
        o.pointCount,
        o.ownerUid, o.acknowledgedFingerprint, o.suppressed, o.failure
        FROM runs r INNER JOIN cloud_backup_origins o ON r.id = o.runId
        WHERE r.finishedAtMillis > 0 ORDER BY r.startedAtMillis DESC""")
    fun completed(): Flow<List<CloudBackupCandidate>>
    @Query("""UPDATE cloud_backup_origins SET ownerUid = :uid WHERE ownerUid = '' AND suppressed = 0
        AND runId IN (SELECT id FROM runs WHERE finishedAtMillis > 0)""")
    suspend fun claimUnowned(uid: String)
    @Query("""UPDATE cloud_backup_origins SET acknowledgedFingerprint = :fingerprint,
        acknowledgedAtMillis = :now, failure = '' WHERE runId = :id AND ownerUid = :uid AND suppressed = 0""")
    suspend fun acknowledge(id: String, uid: String, fingerprint: String, now: Long): Int
    @Query("UPDATE cloud_backup_origins SET failure = :failure WHERE runId = :id AND ownerUid = :uid AND suppressed = 0")
    suspend fun failure(id: String, uid: String, failure: String)
    @Query("""UPDATE cloud_backup_origins SET suppressed = 1, ownerUid = :uid, failure = ''
        WHERE runId = :id AND (ownerUid = :uid OR ownerUid = '')""")
    suspend fun suppress(id: String, uid: String)
    @Query("UPDATE cloud_backup_origins SET failure = '' WHERE runId = :id AND ownerUid = :uid AND suppressed = 0")
    suspend fun retry(id: String, uid: String)
    @Query("SELECT * FROM cloud_backup_origins WHERE runId = :id") suspend fun origin(id: String): StoredCloudBackupOrigin?
}

class CloudBackupRoomLedger(private val dao: CloudBackupDao) : AutomaticCloudBackupLedger {
    override suspend fun claimUnowned(uid: String) { require(uid.isNotBlank()); dao.claimUnowned(uid) }
    override suspend fun pending(uid: String, limit: Int) = dao.completed().first().asSequence()
        .filter { it.ownerUid == uid && !it.suppressed && it.failure != "conflict" }
        .mapNotNull { row -> runCatching { row.summary() }.getOrNull()?.let { row to it } }
        .filter { (row, summary) -> row.acknowledgedFingerprint != summary.backupFingerprint() }
        .take(limit).map { (_, summary) -> PendingCloudBackup(summary) }.toList()
    override suspend fun acknowledge(uid: String, pending: PendingCloudBackup) =
        dao.acknowledge(pending.summary.runId, uid, pending.fingerprint, System.currentTimeMillis()) == 1
    override suspend fun failure(uid: String, runId: String, conflict: Boolean) =
        dao.failure(runId, uid, if (conflict) "conflict" else "retry")
    suspend fun suppress(runId: String, uid: String) = dao.suppress(runId, uid)
    suspend fun retry(runId: String, uid: String) = dao.retry(runId, uid)
    fun observe(uid: String): Flow<AutomaticCloudBackupState> = dao.completed().distinctUntilChanged().map { rows ->
        AutomaticCloudBackupState(rows.associate { row ->
            val fingerprint = runCatching { row.summary().backupFingerprint() }.getOrNull()
            row.runId to when {
                (row.ownerUid.isNotBlank() && row.ownerUid != uid) || row.suppressed || fingerprint == null -> AutomaticBackupRecordState.EXCLUDED
                fingerprint == row.acknowledgedFingerprint -> AutomaticBackupRecordState.SAVED
                row.failure == "conflict" -> AutomaticBackupRecordState.CONFLICT
                row.failure.isNotBlank() -> AutomaticBackupRecordState.RETRYING
                else -> AutomaticBackupRecordState.WAITING
            }
        }, initialized = true)
    }
}

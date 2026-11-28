package com.runmoni.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "runs")
data class StoredRun(
    @PrimaryKey val id: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long = 0,
    val distanceMeters: Double = 0.0,
    val activeDurationMillis: Long = 0,
    val elapsedDurationMillis: Long = 0,
    val segment: Int = 0,
    val synced: Boolean = false,
    val interrupted: Boolean = false,
    val mileage: Int = 0,
    val splitsJson: String = "[]",
    val goalKind: String? = null,
    @ColumnInfo(defaultValue = "0") val goalTarget: Int = 0,
)
@Entity(tableName = "track_points", primaryKeys = ["runId", "sequence"], foreignKeys = [ForeignKey(entity = StoredRun::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE)])
data class StoredPoint(val runId: String, val sequence: Int, val latitude: Double, val longitude: Double, val accuracyMeters: Float, val recordedAtMillis: Long, val segment: Int)

@Dao
interface RunDao {
    @Query("SELECT * FROM runs WHERE finishedAtMillis > 0 ORDER BY startedAtMillis DESC") fun records(): Flow<List<StoredRun>>
    @Query("SELECT * FROM runs WHERE finishedAtMillis = 0 ORDER BY startedAtMillis DESC LIMIT 1") suspend fun unfinished(): StoredRun?
    @Query("SELECT * FROM runs WHERE id = :id") suspend fun get(id: String): StoredRun?
    @Query("SELECT * FROM track_points WHERE runId = :id ORDER BY sequence") suspend fun points(id: String): List<StoredPoint>
    @Query("SELECT COUNT(*) FROM track_points WHERE runId = :id") suspend fun pointCount(id: String): Int
    @Upsert suspend fun save(run: StoredRun)
    @Upsert suspend fun savePoints(points: List<StoredPoint>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun addPoint(point: StoredPoint)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun registerCloudOrigin(origin: StoredCloudBackupOrigin)
    @Query("DELETE FROM runs WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE runs SET synced = 1, mileage = :mileage WHERE id = :id") suspend fun markSynced(id: String, mileage: Int)
    @Query("UPDATE cloud_backup_origins SET ownerUid = :uid WHERE runId = :id AND ownerUid = '' AND suppressed = 0")
    suspend fun bindCloudOwnerIfUnowned(id: String, uid: String)
    @Query("UPDATE cloud_backup_origins SET pointCount = (SELECT COUNT(*) FROM track_points WHERE runId = :id) WHERE runId = :id")
    suspend fun snapshotCloudPointCount(id: String)
    @Transaction suspend fun append(run: StoredRun, point: StoredPoint) { save(run); addPoint(point) }
    @Transaction suspend fun saveCheckpoint(run: StoredRun, points: List<StoredPoint>) {
        save(run); savePoints(points)
        if (run.finishedAtMillis > 0) snapshotCloudPointCount(run.id)
    }
    @Transaction suspend fun saveOwnedCompletion(run: StoredRun, points: List<StoredPoint>, finishedOwner: String) {
        save(run); savePoints(points)
        if (run.finishedAtMillis > 0) snapshotCloudPointCount(run.id)
        if (run.finishedAtMillis > 0 && finishedOwner.isNotBlank()) bindCloudOwnerIfUnowned(run.id, finishedOwner)
    }
    @Transaction suspend fun createLocal(run: StoredRun, ownerUid: String) {
        save(run)
        registerCloudOrigin(StoredCloudBackupOrigin(run.id, ownerUid = ownerUid))
    }
}

@Database(entities = [StoredRun::class, StoredPoint::class, StoredCloudBackupOrigin::class], version = 3, exportSchema = false)
abstract class RunDatabase : RoomDatabase() {
    abstract fun runs(): RunDao
    abstract fun cloudBackups(): CloudBackupDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS cloud_backup_origins (
                    runId TEXT NOT NULL PRIMARY KEY, ownerUid TEXT NOT NULL DEFAULT '',
                    acknowledgedFingerprint TEXT NOT NULL DEFAULT '', acknowledgedAtMillis INTEGER NOT NULL DEFAULT 0,
                    suppressed INTEGER NOT NULL DEFAULT 0, failure TEXT NOT NULL DEFAULT '', pointCount INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(runId) REFERENCES runs(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                // Production v1 only created local recordings; it never imported cloud summaries.
                // Future imports must not register this local-origin ledger automatically.
                db.execSQL("""INSERT INTO cloud_backup_origins(runId, pointCount)
                    SELECT r.id, (SELECT COUNT(*) FROM track_points p WHERE p.runId = r.id) FROM runs r""")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE runs ADD COLUMN goalKind TEXT")
                db.execSQL("ALTER TABLE runs ADD COLUMN goalTarget INTEGER NOT NULL DEFAULT 0")
            }
        }
        fun create(context: Context) = Room.databaseBuilder(context, RunDatabase::class.java, "runmoni-runs.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3).setJournalMode(JournalMode.WRITE_AHEAD_LOGGING).build()
    }
}

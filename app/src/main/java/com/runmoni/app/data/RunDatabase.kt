package com.runmoni.app.data

import android.content.Context
import androidx.room.*
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
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun addPoint(point: StoredPoint)
    @Query("DELETE FROM runs WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE runs SET synced = 1, mileage = :mileage WHERE id = :id") suspend fun markSynced(id: String, mileage: Int)
    @Transaction suspend fun append(run: StoredRun, point: StoredPoint) { save(run); addPoint(point) }
}

@Database(entities = [StoredRun::class, StoredPoint::class], version = 1, exportSchema = false)
abstract class RunDatabase : RoomDatabase() {
    abstract fun runs(): RunDao
    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, RunDatabase::class.java, "runmoni-runs.db").setJournalMode(JournalMode.WRITE_AHEAD_LOGGING).build()
    }
}

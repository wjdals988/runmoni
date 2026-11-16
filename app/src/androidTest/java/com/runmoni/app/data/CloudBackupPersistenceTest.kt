package com.runmoni.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.runmoni.app.domain.*
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Own temporary databases only. No Firebase, real account, GPS or WorkManager network request. */
class CloudBackupPersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun completed(id: String) = StoredRun(id, 1000, finishedAtMillis = 6000,
        distanceMeters = 25.5, activeDurationMillis = 4000, elapsedDurationMillis = 5000,
        interrupted = true, mileage = 17, splitsJson = "[{\"km\":1,\"duration\":4000}]")
    private fun open(name: String) = Room.databaseBuilder(context, RunDatabase::class.java, name)
        .addMigrations(RunDatabase.MIGRATION_1_2).build()

    @Test fun v1MigrationPreservesCompletedUnfinishedPointsAndPendingLocalFields() = runBlocking {
        val name = "owned-cloud-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val finished = completed("legacy-complete")
        val unfinished = completed("legacy-active").copy(finishedAtMillis = 0, synced = true, segment = 3)
        fun SQLiteDatabase.row(run: StoredRun) {
            val values = ContentValues().apply {
                put("id", run.id); put("startedAtMillis", run.startedAtMillis); put("finishedAtMillis", run.finishedAtMillis)
                put("distanceMeters", run.distanceMeters); put("activeDurationMillis", run.activeDurationMillis)
                put("elapsedDurationMillis", run.elapsedDurationMillis); put("segment", run.segment)
                put("synced", if (run.synced) 1 else 0); put("interrupted", if (run.interrupted) 1 else 0)
                put("mileage", run.mileage); put("splitsJson", run.splitsJson)
            }
            insertOrThrow("runs", null, values)
        }
        var database: RunDatabase? = null
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                old.execSQL("""CREATE TABLE runs (id TEXT NOT NULL PRIMARY KEY, startedAtMillis INTEGER NOT NULL,
                    finishedAtMillis INTEGER NOT NULL, distanceMeters REAL NOT NULL, activeDurationMillis INTEGER NOT NULL,
                    elapsedDurationMillis INTEGER NOT NULL, segment INTEGER NOT NULL, synced INTEGER NOT NULL,
                    interrupted INTEGER NOT NULL, mileage INTEGER NOT NULL, splitsJson TEXT NOT NULL)""")
                old.execSQL("""CREATE TABLE track_points (runId TEXT NOT NULL, sequence INTEGER NOT NULL,
                    latitude REAL NOT NULL, longitude REAL NOT NULL, accuracyMeters REAL NOT NULL,
                    recordedAtMillis INTEGER NOT NULL, segment INTEGER NOT NULL, PRIMARY KEY(runId,sequence),
                    FOREIGN KEY(runId) REFERENCES runs(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                old.row(finished); old.row(unfinished)
                old.execSQL("INSERT INTO track_points VALUES ('legacy-active',0,37.0,127.0,5.0,1000,0)")
                old.execSQL("INSERT INTO track_points VALUES ('legacy-active',1,37.1,127.1,7.0,2000,3)")
                old.execSQL("INSERT INTO track_points VALUES ('legacy-complete',0,36.0,126.0,5.0,1000,0)")
                old.version = 1
            }
            val migrated = open(name)
            database = migrated
            val runs = migrated.runs()
            assertEquals(finished, runs.get(finished.id))
            assertEquals(unfinished, runs.unfinished())
            val points = runs.points(unfinished.id)
            assertEquals(listOf(0, 3), points.map { it.segment })
            assertEquals(listOf(37.0, 37.1), points.map { it.latitude })
            assertEquals("", migrated.cloudBackups().origin(finished.id)!!.ownerUid)
            assertEquals("", migrated.cloudBackups().origin(unfinished.id)!!.ownerUid)
            assertEquals(1, migrated.cloudBackups().origin(finished.id)!!.pointCount)
            assertEquals(2, migrated.cloudBackups().origin(unfinished.id)!!.pointCount)
            migrated.cloudBackups().claimUnowned("google-a")
            assertEquals("", migrated.cloudBackups().origin(unfinished.id)!!.ownerUid)
            assertEquals("google-a", migrated.cloudBackups().origin(finished.id)!!.ownerUid)
            assertEquals(finished, runs.get(finished.id)) // No wallet/credit or record rewrite.
        } finally { database?.close(); context.deleteDatabase(name) }
    }

    @Test fun fixedOwnerAndPendingAckSurviveDatabaseReopenWithoutUploadingAnImportedRow() = runBlocking {
        val name = "owned-cloud-queue-${UUID.randomUUID()}.db"
        var db = open(name)
        try {
            db.runs().createLocal(completed("pending"), "google-a")
            db.runs().createLocal(completed("saved"), "google-a")
            db.runs().save(completed("imported")) // Future imports do not gain a local-origin row.
            var ledger = CloudBackupRoomLedger(db.cloudBackups())
            val saved = ledger.pending("google-a", 50).single { it.summary.runId == "saved" }
            assertTrue(ledger.acknowledge("google-a", saved))
            db.close(); db = open(name); ledger = CloudBackupRoomLedger(db.cloudBackups())
            ledger.claimUnowned("google-b")
            assertEquals("google-a", db.cloudBackups().origin("pending")!!.ownerUid)
            assertEquals(listOf("pending"), ledger.pending("google-a", 50).map { it.summary.runId })
            assertTrue(ledger.pending("google-b", 50).isEmpty())
            assertNull(db.cloudBackups().origin("imported"))
            assertEquals(AutomaticBackupRecordState.SAVED, ledger.observe("google-a").first().record("saved"))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun suppressedSummaryRejectsLateOrForeignAckAndStaysSuppressedAfterReopen() = runBlocking {
        val name = "owned-cloud-delete-${UUID.randomUUID()}.db"
        var db = open(name)
        try {
            db.runs().createLocal(completed("deleted-summary"), "google-a")
            var ledger = CloudBackupRoomLedger(db.cloudBackups())
            val pending = ledger.pending("google-a", 50).single()
            assertFalse(ledger.acknowledge("google-b", pending))
            ledger.suppress("deleted-summary", "google-a")
            assertFalse(ledger.acknowledge("google-a", pending))
            db.close(); db = open(name); ledger = CloudBackupRoomLedger(db.cloudBackups())
            ledger.claimUnowned("google-b")
            assertTrue(ledger.pending("google-a", 50).isEmpty())
            assertTrue(ledger.pending("google-b", 50).isEmpty())
            assertEquals(AutomaticBackupRecordState.EXCLUDED, ledger.observe("google-a").first().record("deleted-summary"))
            assertNotNull(db.runs().get("deleted-summary"))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun completedRecordingRequestsBackupAfterItsDurableCommitWithoutAViewModel() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, RunDatabase::class.java).build()
        var requests = 0
        val repo = RunRepository(db, credit = { _, _ -> }, backupOwner = { "google-a" }, completedBackup = { requests++ })
        try {
            repo.begin()
            val id = repo.active.value.id
            assertEquals("google-a", db.cloudBackups().origin(id)!!.ownerUid)
            repo.finish(id)
            assertEquals(1, requests)
            assertTrue(db.runs().get(id)!!.finishedAtMillis > 0)
            assertEquals(id, CloudBackupRoomLedger(db.cloudBackups()).pending("google-a", 50).single().summary.runId)
            repo.finish(id)
            assertEquals(1, requests)
        } finally { repo.close(); db.close() }
    }

    @Test fun aGuestRunBindsAtCompletionWhileAnAlreadyOwnedRunNeverMovesToAnotherAccount() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, RunDatabase::class.java).build()
        var uid = ""
        val repo = RunRepository(db, credit = { _, _ -> }, backupOwner = { uid })
        try {
            repo.begin()
            val guestId = repo.active.value.id
            uid = "google-a"
            db.cloudBackups().claimUnowned(uid)
            assertEquals("", db.cloudBackups().origin(guestId)!!.ownerUid)
            uid = "google-b"
            repo.finish(guestId)
            assertEquals("google-b", db.cloudBackups().origin(guestId)!!.ownerUid)
            uid = "google-a"
            repo.begin()
            val ownedId = repo.active.value.id
            uid = "google-b"
            repo.finish(ownedId)
            assertEquals("google-a", db.cloudBackups().origin(ownedId)!!.ownerUid)
        } finally { repo.close(); db.close() }
    }

    @Test fun aFailedOwnedCompletionKeepsItsFirstUidAndEnqueuesOnlyAfterRetryCommits() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, RunDatabase::class.java).build()
        var uid = ""; var failWrite = true; var requests = 0
        val faulty = object : RunDao by db.runs() {
            override suspend fun saveOwnedCompletion(run: StoredRun, points: List<StoredPoint>, finishedOwner: String) {
                if (failWrite) throw IllegalStateException("owned completion fixture failure")
                db.runs().saveOwnedCompletion(run, points, finishedOwner)
            }
        }
        val repo = RunRepository(db, faulty, credit = { _, _ -> }, backupOwner = { uid }, completedBackup = { requests++ })
        try {
            repo.begin()
            val id = repo.active.value.id
            uid = "google-a"
            repo.finish(id)
            assertEquals(0, requests)
            assertEquals(0L, db.runs().get(id)!!.finishedAtMillis)
            uid = "google-b"; failWrite = false
            repo.retryPersistence(id)
            assertEquals(1, requests)
            assertEquals("google-a", db.cloudBackups().origin(id)!!.ownerUid)
            val ledger = CloudBackupRoomLedger(db.cloudBackups())
            assertEquals(id, ledger.pending("google-a", 50).single().summary.runId)
            assertTrue(ledger.pending("google-b", 50).isEmpty())
        } finally { repo.close(); db.close() }
    }
}

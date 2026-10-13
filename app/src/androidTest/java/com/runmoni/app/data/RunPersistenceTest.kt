package com.runmoni.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RunPersistenceTest {
    @Test fun parentUpdatePreservesEntireTrackAndCascadeDeleteRemovesIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, RunDatabase::class.java).build()
        try {
            val dao = db.runs()
            var run = StoredRun("route-regression", 1000)
            repeat(50) { i ->
                run = run.copy(distanceMeters = i * 2.0, activeDurationMillis = i * 1000L)
                dao.append(run, StoredPoint(run.id, i, 37.0, 127.0 + i * .00001, 5f, 1000 + i * 1000L, 0))
                dao.save(run)
            }
            assertEquals(50, dao.pointCount(run.id))
            assertEquals(49, dao.points(run.id).last().sequence)
            dao.delete(run.id)
            assertEquals(0, dao.pointCount(run.id))
        } finally { db.close() }
    }
    @Test fun unfinishedTrackSurvivesDatabaseReopenWithSegments() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "recovery-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, RunDatabase::class.java, name).build()
        try {
            val run = StoredRun("recover", 1000, distanceMeters = 123.0, activeDurationMillis = 30000, segment = 2)
            db.runs().append(run, StoredPoint(run.id, 0, 37.0, 127.0, 5f, 1000, 0))
            db.runs().append(run, StoredPoint(run.id, 1, 37.1, 127.1, 5f, 2000, 2))
            db.close()
            db = Room.databaseBuilder(context, RunDatabase::class.java, name).build()
            assertEquals(123.0, db.runs().unfinished()!!.distanceMeters, 0.0)
            assertEquals(listOf(0, 2), db.runs().points(run.id).map { it.segment })
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun walletReplayedCompletionCreditsExactlyOnceAndDoesNotResetCosmetics() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wallet = LocalWallet(context)
        val before = wallet.read()
        val id = "wallet-test-${UUID.randomUUID()}"
        try {
            assertEquals(before.mileage + 123, wallet.credit(id, 123).mileage)
            assertEquals(before.mileage + 123, LocalWallet(context).credit(id, 123).mileage)
            assertEquals(before.ownedItems, wallet.read().ownedItems)
        } finally { wallet.update { before } }
    }
}

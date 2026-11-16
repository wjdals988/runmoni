package com.runmoni.app.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Queue policy, no Firebase/network/Android account or production writes. */
class CloudBackupReconcilerTest {
    private fun summary(id: String = "local-run") = CloudRunSummary(runId = id,
        startedAtMillis = 1000, finishedAtMillis = 6000, activeDurationMillis = 4000,
        distanceMeters = 32.5, pointCount = 4)

    private class Entry(val summary: CloudRunSummary, var owner: String = "", val local: Boolean = true,
                        val complete: Boolean = true, var suppressed: Boolean = false,
                        var fingerprint: String = "", var failure: String = "")
    private class Ledger(vararg entries: Entry) : AutomaticCloudBackupLedger {
        val rows = entries.toList()
        var claims = 0
        val acknowledgements = mutableListOf<String>()
        var duringClaim: (() -> Unit)? = null
        override suspend fun claimUnowned(uid: String) {
            claims++
            rows.filter { it.local && it.complete && it.owner.isBlank() && !it.suppressed }.forEach { it.owner = uid }
            duringClaim?.invoke()
        }
        override suspend fun pending(uid: String, limit: Int) = rows.filter {
            it.local && it.complete && !it.suppressed && it.owner == uid && it.failure != "conflict" &&
                it.fingerprint != it.summary.backupFingerprint()
        }.take(limit).map { PendingCloudBackup(it.summary) }
        override suspend fun acknowledge(uid: String, pending: PendingCloudBackup): Boolean {
            val row = rows.singleOrNull { it.summary.runId == pending.summary.runId && it.owner == uid && !it.suppressed } ?: return false
            row.fingerprint = pending.fingerprint; row.failure = ""; acknowledgements += row.summary.runId
            return true
        }
        override suspend fun failure(uid: String, runId: String, conflict: Boolean) {
            rows.singleOrNull { it.summary.runId == runId && it.owner == uid && !it.suppressed }?.failure = if (conflict) "conflict" else "retry"
        }
    }
    private class Account(var uid: String = "google-a", var epoch: Long = 1) : AutomaticCloudBackupAccount {
        val uploads = mutableListOf<Pair<String, String>>()
        var result = CloudBackupWriteResult.ACKNOWLEDGED
        var duringWrite: (() -> Unit)? = null
        override fun backupSession(expectedUid: String?) = uid.takeIf { it.isNotBlank() && (expectedUid == null || expectedUid == it) }
            ?.let { CloudBackupSession(it, epoch) }
        override fun isBackupSessionCurrent(session: CloudBackupSession) = session.uid == uid && session.epoch == epoch
        override suspend fun writeAutomaticSummary(summary: CloudRunSummary, session: CloudBackupSession): CloudBackupWriteResult {
            uploads += session.uid to summary.runId
            duringWrite?.invoke()
            return result
        }
    }

    @Test fun guestAndAnOldAccountQueueNeverClaimOrUploadForTheCurrentAccount() = runBlocking {
        val ledger = Ledger(Entry(summary()))
        val account = Account(uid = "")
        assertEquals(CloudBackupReconcileResult.STALE_SESSION, CloudBackupReconciler(ledger, account).reconcile())
        account.uid = "google-b"
        assertEquals(CloudBackupReconcileResult.STALE_SESSION, CloudBackupReconciler(ledger, account).reconcile("google-a"))
        assertEquals(0, ledger.claims); assertTrue(account.uploads.isEmpty())
    }

    @Test fun firstConnectionBackfillsOnlyCompletedLocalSourcesAndNeverRebindsAnOwnedRun() = runBlocking {
        val local = Entry(summary("local"))
        val running = Entry(summary("active"), complete = false)
        val imported = Entry(summary("imported"), local = false)
        val other = Entry(summary("other"), owner = "google-b")
        val ledger = Ledger(local, running, imported, other)
        val account = Account()
        val worker = CloudBackupReconciler(ledger, account)
        assertEquals(CloudBackupReconcileResult.COMPLETE, worker.reconcile())
        assertEquals(listOf("google-a" to "local"), account.uploads)
        assertEquals("", running.owner); assertEquals("google-b", other.owner)
        assertEquals("", imported.owner)
        worker.reconcile()
        assertEquals(1, account.uploads.size) // Same ACK does not enqueue another remote write.
    }

    @Test fun offlineRetryRemainsPendingAcrossCoordinatorRecreation() = runBlocking {
        val row = Entry(summary())
        val ledger = Ledger(row, Entry(summary("later"))); val account = Account().apply { result = CloudBackupWriteResult.RETRY }
        assertEquals(CloudBackupReconcileResult.RETRY, CloudBackupReconciler(ledger, account).reconcile())
        assertEquals("", row.fingerprint); assertEquals("retry", row.failure)
        assertEquals(1, account.uploads.size)
        account.result = CloudBackupWriteResult.ACKNOWLEDGED
        assertEquals(CloudBackupReconcileResult.COMPLETE, CloudBackupReconciler(ledger, account).reconcile())
        assertEquals(row.summary.backupFingerprint(), row.fingerprint)
        assertEquals(2, ledger.acknowledgements.size)
    }

    @Test fun lateAcknowledgementAfterUidSwitchCannotMarkTheOldRunSavedOrMoveItToTheNewUid() = runBlocking {
        val row = Entry(summary()); val ledger = Ledger(row); val account = Account()
        account.duringWrite = { account.uid = "google-b"; account.epoch++ }
        assertEquals(CloudBackupReconcileResult.STALE_SESSION, CloudBackupReconciler(ledger, account).reconcile())
        assertTrue(ledger.acknowledgements.isEmpty()); assertEquals("google-a", row.owner)
        account.duringWrite = null
        CloudBackupReconciler(ledger, account).reconcile()
        assertEquals(listOf("google-a" to row.summary.runId), account.uploads)
        assertEquals("", row.fingerprint)
    }

    @Test fun logoutAndReturnToTheSameUidStillInvalidateAnOlderSessionAck() = runBlocking {
        val ledger = Ledger(Entry(summary())); val account = Account()
        account.duringWrite = { account.epoch += 2 }
        assertEquals(CloudBackupReconcileResult.STALE_SESSION, CloudBackupReconciler(ledger, account).reconcile())
        assertTrue(ledger.acknowledgements.isEmpty())
    }

    @Test fun aUidChangeDuringTheLocalClaimCannotStartRemoteWrites() = runBlocking {
        val ledger = Ledger(Entry(summary())); val account = Account()
        ledger.duringClaim = { account.uid = "google-b"; account.epoch++ }
        assertEquals(CloudBackupReconcileResult.STALE_SESSION, CloudBackupReconciler(ledger, account).reconcile())
        assertTrue(account.uploads.isEmpty())
    }

    @Test fun deletionSuppressionDuringAWriteRejectsItsAckAndEveryLaterReconcile() = runBlocking {
        val row = Entry(summary()); val ledger = Ledger(row); val account = Account()
        account.duringWrite = { row.suppressed = true }
        CloudBackupReconciler(ledger, account).reconcile()
        account.duringWrite = null
        CloudBackupReconciler(ledger, account).reconcile()
        assertEquals(1, account.uploads.size); assertTrue(ledger.acknowledgements.isEmpty())
    }

    @Test fun conflictingRemoteSummaryRequiresReviewAndIsNotAutomaticallyRetriedOrAcknowledged() = runBlocking {
        val row = Entry(summary()); val ledger = Ledger(row)
        val account = Account().apply { result = CloudBackupWriteResult.CONFLICT }
        assertEquals(CloudBackupReconcileResult.COMPLETE, CloudBackupReconciler(ledger, account).reconcile())
        assertEquals("conflict", row.failure); assertTrue(ledger.acknowledgements.isEmpty())
        CloudBackupReconciler(ledger, account).reconcile()
        assertEquals(1, account.uploads.size)
    }

    @Test fun aBoundedBackfillKeepsUnacknowledgedRunsForTheNextWorker() = runBlocking {
        val ledger = Ledger(*(0..CloudBackupReconciler.BATCH_SIZE).map { Entry(summary("local-$it")) }.toTypedArray())
        val account = Account(); val worker = CloudBackupReconciler(ledger, account)
        assertEquals(CloudBackupReconcileResult.MORE_PENDING, worker.reconcile())
        assertEquals(50, ledger.acknowledgements.size)
        assertEquals(CloudBackupReconcileResult.COMPLETE, worker.reconcile())
        assertEquals(51, account.uploads.distinct().size)
    }

    @Test fun cancellationLeavesTheQueueUnacknowledged() = runBlocking {
        val ledger = Ledger(Entry(summary())); val account = Account()
        account.duringWrite = { throw CancellationException("owned cancellation") }
        try { CloudBackupReconciler(ledger, account).reconcile(); fail("Cancellation must propagate") }
        catch (_: CancellationException) { assertTrue(ledger.acknowledgements.isEmpty()) }
    }

    @Test fun fingerprintChangesOnlyWhenWhitelistedSummaryContentChanges() {
        val record = RunRecord("local", 1000, 6000, 32.5, 4000,
            points = listOf(TrackPoint(0, 37.0, 127.0, 5f, 1000)), mileage = 100)
        val first = CloudRunSummary.fromRunRecord(record)
        val moved = record.copy(points = listOf(TrackPoint(0, -37.0, -127.0, 99f, 1000)), mileage = 999)
        assertEquals(first.backupFingerprint(), CloudRunSummary.fromRunRecord(moved).backupFingerprint())
        assertNotEquals(first.backupFingerprint(), first.copy(distanceMeters = 33.0).backupFingerprint())
    }
}

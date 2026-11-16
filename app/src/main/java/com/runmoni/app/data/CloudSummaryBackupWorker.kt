package com.runmoni.app.data

import android.content.Context
import androidx.work.*
import com.runmoni.app.BuildConfig
import com.runmoni.app.RunmoniApplication
import com.runmoni.app.domain.CloudBackupReconcileResult
import com.runmoni.app.domain.CloudBackupReconciler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** Private completed summaries only. The HTTP pilot worker is intentionally separate. */
class CloudSummaryBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.FIREBASE_ENABLED) return Result.success()
        return try {
            runMutex.withLock {
                val app = applicationContext as RunmoniApplication
                val cloud = app.cloud // Owns an Auth listener even without an Activity/ViewModel.
                if (!cloud.state.value.configured) return@withLock Result.success()
                // Stored Firebase sessions are asynchronous observations; never create an account.
                if (withTimeoutOrNull(15_000) { cloud.state.first { !it.busy } } == null) return@withLock Result.retry()
                val uid = cloud.backupSession(inputData.getString(EXPECTED_UID))?.uid ?: return@withLock Result.success()
                // Do not initialize RunRepository's eager route/wallet flows in a
                // background summary job. This DAO projects counts and summary fields only.
                val ledger = CloudBackupRoomLedger(app.runDatabase.cloudBackups())
                when (CloudBackupReconciler(ledger, cloud).reconcile(uid)) {
                    CloudBackupReconcileResult.RETRY -> Result.retry()
                    CloudBackupReconcileResult.MORE_PENDING -> {
                        scheduleNow(applicationContext, uid, ExistingWorkPolicy.APPEND_OR_REPLACE)
                        Result.success()
                    }
                    CloudBackupReconcileResult.COMPLETE, CloudBackupReconcileResult.STALE_SESSION -> Result.success()
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }

    companion object {
        private val runMutex = Mutex()
        private const val EXPECTED_UID = "expectedUid"
        private const val IMMEDIATE = "runmoni-private-summary-now"
        private const val PERIODIC = "runmoni-private-summary-reconcile"
        private fun connected() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun enqueue(context: Context, expectedUid: String? = null) {
            scheduleNow(context, expectedUid, ExistingWorkPolicy.REPLACE)
        }
        private fun scheduleNow(context: Context, expectedUid: String?, policy: ExistingWorkPolicy) {
            if (!BuildConfig.FIREBASE_ENABLED) return
            val data = Data.Builder().apply { expectedUid?.takeIf { it.isNotBlank() }?.let { putString(EXPECTED_UID, it) } }.build()
            val work = OneTimeWorkRequestBuilder<CloudSummaryBackupWorker>().setInputData(data).setConstraints(connected())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            // Replace only the wake-up request; Room owns every pending run.
            // An older network backoff must not delay a newly finished recording.
            WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE, policy, work)
        }
        fun schedulePeriodic(context: Context) {
            if (!BuildConfig.FIREBASE_ENABLED) return
            val work = PeriodicWorkRequestBuilder<CloudSummaryBackupWorker>(15, TimeUnit.MINUTES).setConstraints(connected())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, work)
        }
    }
}

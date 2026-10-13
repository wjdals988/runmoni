package com.runmoni.app.data

import android.content.Context
import androidx.work.*
import com.runmoni.app.BuildConfig
import com.runmoni.app.RunmoniApplication
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class RunSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val token = SecureSession(applicationContext).read()
        if (token.isBlank()) return Result.success()
        val prefs = applicationContext.getSharedPreferences("runmoni", Context.MODE_PRIVATE)
        val client = PilotClient(prefs.getString("apiUrl", BuildConfig.PILOT_API_URL)!!, token)
        val repo = (applicationContext as RunmoniApplication).runs
        return try {
            // Read directly from the DAO; do not race the eagerly initialized history flow.
            val records = repo.dao.records().first()
            for (stored in records.filter { !it.synced }) {
                val record = repo.record(stored.id) ?: continue
                val mileage = client.syncRun(record)
                repo.dao.markSynced(record.id, mileage)
            }
            Result.success()
        } catch (failure: PilotException) {
            if (failure.status in setOf(400, 401, 403, 409, 422)) Result.failure() else Result.retry()
        } catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<RunSyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("runmoni-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

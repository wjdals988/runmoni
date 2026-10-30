package com.runmoni.app.data

import android.content.Context
import androidx.work.*
import com.runmoni.app.BuildConfig
import com.runmoni.app.RunmoniApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class RunSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val token = SecureSession(applicationContext).read()
        if (token.isBlank()) return Result.success()
        val prefs = applicationContext.getSharedPreferences("runmoni", Context.MODE_PRIVATE)
        if (prefs.getBoolean("communityGuest", false)) return Result.success()
        val client = PilotClient(prefs.getString("apiUrl", BuildConfig.PILOT_API_URL)!!, token)
        val repo = (applicationContext as RunmoniApplication).runs
        return try {
            if (client.request("GET", "/v1/me").optString("auth_mode") == "guest") return Result.success()
            // Read directly from the DAO; do not race the eagerly initialized history flow.
            val records = repo.dao.records().first()
            for (stored in records.filter { !it.synced }) {
                if (prefs.getBoolean("communityGuest", false) || SecureSession(applicationContext).read() != token) return Result.success()
                val record = repo.record(stored.id) ?: continue
                val mileage = client.syncRun(record)
                repo.dao.markSynced(record.id, mileage)
            }
            Result.success()
        } catch (failure: PilotException) {
            if (failure.status in setOf(400, 401, 403, 409, 422)) Result.failure() else Result.retry()
        } catch (failure: Exception) { if (failure is CancellationException) throw failure; Result.retry() }
    }
    companion object {
        fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork("runmoni-sync") }
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<RunSyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("runmoni-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

package com.runmoni.app.data

import android.content.Context
import com.runmoni.app.domain.AppRelease
import com.runmoni.app.domain.latestPublishedRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Public, bounded metadata requests only. No APK download, token or account data. */
interface AppUpdateSource {
    fun cached(): AppUpdateRepository.Cached
    suspend fun check(includeDevelopment: Boolean): AppUpdateRepository.Cached
}

class AppUpdateRepository(context: Context) : AppUpdateSource {
    private val preferences = context.getSharedPreferences("runmoni-app-updates", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequests = 1; maxRequestsPerHost = 1 })
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS).build()

    data class Cached(val release: AppRelease?, val checkedAtMillis: Long)

    override fun cached(): Cached {
        val release = runCatching { parseManifest(JSONObject(preferences.getString("manifest", "")!!)) }.getOrNull()
        return Cached(release?.takeIf { it.isTrusted() }, preferences.getLong("checkedAt", 0))
    }

    override suspend fun check(includeDevelopment: Boolean): Cached = checkMutex.withLock { withContext(Dispatchers.IO) {
        // This file is updated only after the release APK is published and verified.
        // One small request avoids the API's ordering and prerelease/latest ambiguity.
        val feed = JSONObject(readJson("https://raw.githubusercontent.com/wjdals988/runmoni/main/updates/android.json", 32 * 1024))
        if (feed.getInt("schemaVersion") != 1) throw IOException("Unsupported update feed")
        val stable = feed.optJSONObject("stable")?.let(::parseManifest)
        val preview = feed.optJSONObject("preview")?.let(::parseManifest)
        val release = latestPublishedRelease(stable, preview, includeDevelopment)
            ?: throw IOException("No compatible published release metadata")
        val checked = System.currentTimeMillis()
        preferences.edit().putString("manifest", manifestJson(release).toString()).putLong("checkedAt", checked).apply()
        Cached(release, checked)
    } }

    private fun readJson(url: String, maxBytes: Int): String {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Runmoni-Android-Update-Check").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Update metadata HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty update metadata")
            if (body.contentLength() > maxBytes) throw IOException("Update metadata too large")
            body.byteStream().use { input ->
                val result = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (result.size() + count > maxBytes) throw IOException("Update metadata too large")
                    result.write(buffer, 0, count)
                }
                return result.toString(Charsets.UTF_8.name())
            }
        }
    }

    private fun parseManifest(json: JSONObject) = AppRelease(
        version = json.getString("version"), versionCode = json.getLong("versionCode"),
        fileName = json.getString("fileName"), sizeBytes = json.getLong("sizeBytes"),
        sha256 = json.getString("sha256"), certificateSha256 = json.getString("certificateSha256"),
        signed = json.getBoolean("signed"), packageName = json.getString("package"),
        downloadUrl = json.getString("downloadUrl"), releaseUrl = json.getString("releaseUrl"),
        notes = json.optString("notes").take(16_000),
    )

    private fun manifestJson(release: AppRelease) = JSONObject().apply {
        put("version", release.version); put("versionCode", release.versionCode)
        put("fileName", release.fileName); put("sizeBytes", release.sizeBytes)
        put("sha256", release.sha256); put("certificateSha256", release.certificateSha256)
        put("signed", release.signed); put("package", release.packageName)
        put("downloadUrl", release.downloadUrl); put("releaseUrl", release.releaseUrl); put("notes", release.notes)
    }

    companion object {
        // Also serializes checks across Activity recreation while an old call finishes.
        private val checkMutex = Mutex()
    }
}

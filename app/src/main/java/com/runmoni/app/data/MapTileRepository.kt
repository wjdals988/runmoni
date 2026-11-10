package com.runmoni.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.runmoni.app.BuildConfig
import com.runmoni.app.domain.MapTileKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.coroutineContext

data class MapTileResult(val bitmap: Bitmap? = null, val stale: Boolean = false)
fun interface MapTileSource {
    suspend fun load(tile: MapTileKey): MapTileResult
    fun retry() = Unit
}

/** Only the visible tile xyz is sent. No raw GPS samples, accounts or headers
 * identifying a runner are uploaded. Tile xyz still reveals the viewed area. */
internal class MapTileRepository private constructor(context: Context) : MapTileSource {
    private val directory = File(context.applicationContext.cacheDir, "osm-visible-tiles")
    private val requests = Mutex() // One HTTP request at a time across map instances.
    @Volatile private var retryAfter = 0L
    private var serverRetryAfter = 0L
    override fun retry() { retryAfter = 0L } // Explicit retry never bypasses server throttling.
    private data class Entry(val bitmap: Bitmap, val metadata: Metadata)
    private data class Metadata(val savedAt: Long, val control: String?, val expires: String?, val date: String?,
        val age: Long, val etag: String?, val modified: String?) {
        fun policy() = TileHttpCachePolicy.from(savedAt, control, expires, date, age)
        fun json() = JSONObject().put("savedAt", savedAt).put("control", control).put("expires", expires)
            .put("date", date).put("age", age).put("etag", etag).put("modified", modified)
        companion object {
            fun read(file: File): Metadata {
                val json = JSONObject(file.readText())
                fun optional(key: String) = if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotBlank() }
                return Metadata(json.getLong("savedAt"), optional("control"), optional("expires"), optional("date"),
                    json.optLong("age", 0), optional("etag"), optional("modified"))
            }
        }
    }
    private val memory = object : LruCache<MapTileKey, Entry>(8 * 1024 * 1024) {
        override fun sizeOf(key: MapTileKey, value: Entry) = value.bitmap.allocationByteCount
        // Never recycle an evicted bitmap that a displayed Canvas still owns.
        // Evicted/removed viewport entries are reclaimed normally by the GC.
    }

    override suspend fun load(tile: MapTileKey): MapTileResult = withContext(Dispatchers.IO) {
        requests.withLock {
            coroutineContext.ensureActive()
            val now = System.currentTimeMillis()
            val cached = memory.get(tile) ?: disk(tile)?.also { memory.put(tile, it) }
            if (cached != null && cached.metadata.policy().fresh(now)) return@withLock MapTileResult(cached.bitmap)
            fun unavailable(): MapTileResult = if (cached != null && cached.metadata.policy().mayUseStaleOnError)
                MapTileResult(cached.bitmap, stale = true) else MapTileResult()
            if (now < maxOf(retryAfter, serverRetryAfter)) return@withLock unavailable()
            var connection: HttpURLConnection? = null
            try {
                val http = URL("https://tile.openstreetmap.org/${tile.zoom}/${tile.x}/${tile.y}.png").openConnection() as HttpURLConnection
                connection = http
                http.instanceFollowRedirects = false
                http.connectTimeout = 5_000
                http.readTimeout = 7_000
                // Keep URLConnection's normal cache behavior: useCaches=false
                // can itself inject a forbidden blanket request no-cache header.
                http.setRequestProperty("User-Agent", "Runmoni/${BuildConfig.VERSION_NAME} (Android; https://github.com/wjdals988/runmoni)")
                http.setRequestProperty("Accept", "image/png")
                cached?.metadata?.etag?.let { http.setRequestProperty("If-None-Match", it) }
                cached?.metadata?.modified?.let { http.setRequestProperty("If-Modified-Since", it) }
                val code = http.responseCode
                coroutineContext.ensureActive()
                if (code == 429 || code == 503) {
                    val retry = http.getHeaderField("Retry-After")
                    val seconds = retry?.toLongOrNull()?.coerceIn(0, 365L * 24 * 3600)
                    val date = runCatching { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                        .apply { timeZone = TimeZone.getTimeZone("GMT"); isLenient = false }.parse(retry.orEmpty())?.time }.getOrNull()
                    serverRetryAfter = now + maxOf(30_000L, seconds?.times(1000) ?: date?.minus(now) ?: 60_000L)
                    return@withLock unavailable()
                }
                fun metadata(previous: Metadata? = null): Metadata = Metadata(now,
                    http.getHeaderField("Cache-Control") ?: previous?.control,
                    http.getHeaderField("Expires") ?: previous?.expires,
                    http.getHeaderField("Date") ?: previous?.date,
                    http.getHeaderFieldLong("Age", 0).coerceAtLeast(0),
                    http.getHeaderField("ETag") ?: previous?.etag,
                    http.getHeaderField("Last-Modified") ?: previous?.modified)
                if (code == HttpURLConnection.HTTP_NOT_MODIFIED && cached != null) {
                    val updated = Entry(cached.bitmap, metadata(cached.metadata))
                    if (updated.metadata.policy().store) {
                        memory.put(tile, updated)
                        if (body(tile).isFile) atomic(meta(tile), updated.metadata.json().toString().toByteArray())
                    } else remove(tile)
                    return@withLock MapTileResult(updated.bitmap)
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    retryAfter = now + 30_000
                    return@withLock unavailable()
                }
                require(http.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) == "image/png")
                require(http.contentLengthLong <= MAX_TILE_BYTES)
                val bytes = http.inputStream.use { input ->
                    val result = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(result.size() + count <= MAX_TILE_BYTES)
                        result.write(buffer, 0, count)
                    }
                    result.toByteArray()
                }
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                require(options.outWidth == 256 && options.outHeight == 256)
                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                val entry = Entry(bitmap, metadata())
                if (entry.metadata.policy().store) {
                    directory.mkdirs()
                    atomic(body(tile), bytes)
                    atomic(meta(tile), entry.metadata.json().toString().toByteArray())
                    memory.put(tile, entry)
                    prune()
                } else remove(tile)
                MapTileResult(bitmap)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                retryAfter = System.currentTimeMillis() + 30_000
                unavailable()
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun body(tile: MapTileKey) = File(directory, tile.cacheKey + ".png")
    private fun meta(tile: MapTileKey) = File(directory, tile.cacheKey + ".json")
    private fun remove(tile: MapTileKey) { memory.remove(tile); body(tile).delete(); meta(tile).delete() }
    private fun disk(tile: MapTileKey): Entry? = try {
        val file = body(tile)
        if (!file.isFile || file.length() !in 1..MAX_TILE_BYTES.toLong()) null else {
            val metadata = Metadata.read(meta(tile))
            if (!metadata.policy().store) { remove(tile); null } else {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, options)
                if (options.outWidth != 256 || options.outHeight != 256) { remove(tile); null }
                else BitmapFactory.decodeFile(file.path)?.let { Entry(it, metadata) }
            }
        }
    } catch (_: Exception) { remove(tile); null }
    private fun atomic(file: File, bytes: ByteArray) {
        directory.mkdirs()
        val temporary = File(directory, file.name + ".tmp")
        FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
        check(temporary.renameTo(file))
    }
    private fun prune() {
        val files = directory.listFiles().orEmpty()
        files.filter { it.name.endsWith(".tmp") }.forEach { it.delete() }
        var bytes = files.sumOf { it.length() }
        val tiles = files.filter { it.name.endsWith(".png") }.sortedBy { it.lastModified() }
        var count = tiles.size
        for (file in tiles) {
            if (count <= 64 && bytes <= 24 * 1024 * 1024) break
            val metadata = File(directory, file.name.removeSuffix(".png") + ".json")
            bytes -= file.length() + metadata.length()
            file.delete(); metadata.delete(); count--
        }
    }
    companion object {
        private const val MAX_TILE_BYTES = 512 * 1024
        @Volatile private var instance: MapTileRepository? = null
        fun get(context: Context): MapTileRepository = instance ?: synchronized(this) {
            instance ?: MapTileRepository(context).also { instance = it }
        }
    }
}

/** Honor server freshness/no-cache/no-store/must-revalidate; seven days is only
 * the OSM fallback when usable freshness headers were not supplied. */
internal data class TileHttpCachePolicy(val store: Boolean, val validUntil: Long, val revalidate: Boolean,
    val mayUseStaleOnError: Boolean) {
    fun fresh(now: Long) = store && !revalidate && now < validUntil
    companion object {
        fun from(now: Long, control: String?, expires: String?, date: String?, ageSeconds: Long = 0): TileHttpCachePolicy {
            val directives = control.orEmpty().split(',').map { it.trim().lowercase(Locale.ROOT) }
            val noStore = "no-store" in directives
            val noCache = directives.any { it == "no-cache" || it.startsWith("no-cache=") }
            val mustRevalidate = "must-revalidate" in directives
            val maxAge = directives.firstNotNullOfOrNull { directive ->
                if (directive.startsWith("max-age=")) directive.substringAfter('=').trim('"').toLongOrNull()?.coerceAtLeast(0) else null
            }
            fun httpDate(raw: String?): Long? = runCatching {
                if (raw == null) null else SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("GMT"); isLenient = false }.parse(raw)?.time
            }.getOrNull()
            val lifetime = when {
                maxAge != null -> (maxAge.coerceAtMost(365L * 24 * 3600) * 1000 - maxOf(
                    ageSeconds.coerceIn(0, 365L * 24 * 3600) * 1000, (now - (httpDate(date) ?: now)).coerceAtLeast(0))).coerceAtLeast(0)
                httpDate(expires) != null -> ((httpDate(expires)!! - (httpDate(date) ?: now)) - maxOf(
                    ageSeconds.coerceIn(0, 365L * 24 * 3600) * 1000, (now - (httpDate(date) ?: now)).coerceAtLeast(0))).coerceAtLeast(0)
                else -> 7L * 24 * 3600 * 1000
            }
            return TileHttpCachePolicy(!noStore, now + lifetime, noCache, !noStore && !noCache && !mustRevalidate)
        }
    }
}

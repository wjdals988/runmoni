package com.runmoni.app.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.SystemClock
import com.runmoni.app.domain.AreaNameFormatter
import com.runmoni.app.domain.AreaNameParts
import com.runmoni.app.domain.WeatherLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** Device geocoding, approximate coordinates, memory-only cache. No app-owned address API or extra GPS request. */
internal class CurrentAreaRepository(
    context: Context,
    private val lookup: suspend (WeatherLocation) -> String? = DeviceAreaLookup(context.applicationContext)::resolve,
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
    private val timeoutMillis: Long = 5_000,
) {
    private data class Entry(val name: String?, val expiresAt: Long)
    private val cache = LinkedHashMap<WeatherLocation, Entry>()
    private val gate = Mutex()

    suspend fun resolve(location: WeatherLocation): String? = withContext(Dispatchers.IO) {
        gate.withLock {
            currentCoroutineContext().ensureActive()
            cache[location]?.takeIf { elapsedMillis() < it.expiresAt }?.let { return@withLock it.name }
            val name = try { withTimeoutOrNull(timeoutMillis) { AreaNameFormatter.clean(lookup(location)) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            currentCoroutineContext().ensureActive()
            cache[location] = Entry(name, elapsedMillis() + if (name != null) 30 * 60_000L else 60_000L)
            while (cache.size > 4) cache.remove(cache.keys.first())
            name
        }
    }
}

private class DeviceAreaLookup(private val app: Context) {
    suspend fun resolve(location: WeatherLocation): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(app, Locale.KOREAN)
        return suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            val pending = AtomicReference<Future<*>?>()
            fun result(addresses: List<Address>?) {
                val address = addresses?.firstOrNull()
                val name = address?.let { AreaNameFormatter.from(AreaNameParts(it.adminArea, it.subAdminArea,
                    it.locality, it.subLocality, it.countryName, it.countryCode)) }
                if (completed.compareAndSet(false, true) && continuation.isActive) continuation.resume(name)
            }
            continuation.invokeOnCancellation {
                completed.set(true)
                pending.get()?.let { task -> task.cancel(true); if (task is Runnable) legacy.remove(task) }
            }
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = result(addresses)
                        override fun onError(errorMessage: String?) = result(null)
                    })
                } else {
                    val future = legacy.submit {
                        try {
                            @Suppress("DEPRECATION")
                            result(geocoder.getFromLocation(location.latitude, location.longitude, 1))
                        } catch (_: Exception) { result(null) }
                    }
                    pending.set(future)
                    if (!continuation.isActive) { future.cancel(true); if (future is Runnable) legacy.remove(future) }
                }
            } catch (_: Exception) { result(null) }
        }
    }

    companion object {
        // Older synchronous platform calls can ignore interruption. Bound both the worker and pending queue;
        // a late result cannot update a cancelled caller or accumulate an unbounded set of threads/tasks.
        private val legacy = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(1),
            { work -> Thread(work, "RunmoniAreaLookup").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    }
}

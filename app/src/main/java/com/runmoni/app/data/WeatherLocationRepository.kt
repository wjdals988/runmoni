package com.runmoni.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.runmoni.app.domain.WeatherLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class WeatherLocationFailure { PERMISSION_REQUIRED, LOCATION_DISABLED, UNAVAILABLE }

class WeatherLocationException(val reason: WeatherLocationFailure, message: String) : Exception(message)

/** Foreground, bounded one-shot lookup. No recorder, service, auth or location persistence. */
class WeatherLocationRepository(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(LocationManager::class.java)

    suspend fun currentLocation(): WeatherLocation = withContext(Dispatchers.Main.immediate) {
        val coarse = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val precise = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!coarse && !precise) throw WeatherLocationException(WeatherLocationFailure.PERMISSION_REQUIRED, "현재 위치의 날씨를 보려면 위치 권한을 허용해 주세요.")
        val locationManager = manager ?: throw WeatherLocationException(WeatherLocationFailure.UNAVAILABLE, "현재 위치를 찾을 수 없어요.")
        val enabled = if (Build.VERSION.SDK_INT >= 28) locationManager.isLocationEnabled
            else locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!enabled) throw WeatherLocationException(WeatherLocationFailure.LOCATION_DISABLED, "기기의 위치를 켜고 다시 확인해 주세요.")
        val providers = locationManager.getProviders(true).filter {
            it == LocationManager.NETWORK_PROVIDER || it == "fused" || precise && it == LocationManager.GPS_PROVIDER
        }
        fun usable(value: Location?): Boolean {
            if (value == null || !value.hasAccuracy() || !value.accuracy.isFinite() || value.accuracy !in 0f..5_000f) return false
            val age = SystemClock.elapsedRealtimeNanos() - value.elapsedRealtimeNanos
            return age in 0..600_000_000_000L && value.latitude.isFinite() && value.longitude.isFinite() &&
                value.latitude in -90.0..90.0 && value.longitude in -180.0..180.0
        }
        val cached = (providers + LocationManager.PASSIVE_PROVIDER).distinct().mapNotNull { provider ->
            try { locationManager.getLastKnownLocation(provider)?.takeIf(::usable) } catch (_: SecurityException) { null } catch (_: IllegalArgumentException) { null }
        }.maxByOrNull { it.elapsedRealtimeNanos }
        if (cached != null) return@withContext WeatherLocation.approximate(cached.latitude, cached.longitude)
        if (providers.isEmpty()) throw WeatherLocationException(WeatherLocationFailure.UNAVAILABLE, "현재 위치를 찾지 못했어요. 잠시 후 다시 확인해 주세요.")
        val found = withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine<WeatherLocation> { continuation ->
                val completed = AtomicBoolean(false)
                lateinit var listener: LocationListener
                fun cleanup() { runCatching { locationManager.removeUpdates(listener) } }
                listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (usable(location) && completed.compareAndSet(false, true)) {
                            cleanup()
                            if (continuation.isActive) continuation.resume(WeatherLocation.approximate(location.latitude, location.longitude))
                        }
                    }
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                    @Deprecated("Required by older LocationManager implementations")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                continuation.invokeOnCancellation { completed.set(true); cleanup() }
                var registered = false
                for (provider in providers) {
                    if (!continuation.isActive || completed.get()) break
                    try {
                        @Suppress("MissingPermission")
                        locationManager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
                        registered = true
                    } catch (_: SecurityException) { /* Another authorized provider may still work. */ }
                    catch (_: IllegalArgumentException) { /* Provider disappeared during the request. */ }
                }
                if (!registered && completed.compareAndSet(false, true)) {
                    cleanup()
                    if (continuation.isActive) continuation.resumeWithException(WeatherLocationException(
                        WeatherLocationFailure.UNAVAILABLE, "현재 위치를 찾지 못했어요. 잠시 후 다시 확인해 주세요."))
                }
            }
        }
        found ?: throw WeatherLocationException(WeatherLocationFailure.UNAVAILABLE, "현재 위치를 찾는 데 시간이 걸려요. 위치가 잘 잡히는 곳에서 다시 확인해 주세요.")
    }
}

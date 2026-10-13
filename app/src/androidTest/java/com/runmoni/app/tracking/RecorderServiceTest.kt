package com.runmoni.app.tracking

import android.Manifest
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.runmoni.app.MainActivity
import com.runmoni.app.RunmoniApplication
import com.runmoni.app.domain.RunPhase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android service + system test provider. Synthetic GPS is not outdoor accuracy evidence. */
@RunWith(AndroidJUnit4::class)
class RecorderServiceTest {
    @Suppress("DEPRECATION")
    @Test fun foregroundRecorderStoresBackgroundLocationsAndSeparatesPause() {
        val app = ApplicationProvider.getApplicationContext<RunmoniApplication>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS).forEach { device.executeShellCommand("pm grant ${app.packageName} $it") }
        device.executeShellCommand("appops set ${app.packageName} android:mock_location allow")
        val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var runId = ""
        try {
            manager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            manager.addTestProvider(LocationManager.NETWORK_PROVIDER, false, true, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_COARSE)
            manager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
            scenario.onActivity { RunTrackingService.command(it, RunTrackingService.START) }
            until { app.runs.active.value.phase == RunPhase.RUNNING }
            runId = app.runs.active.value.id
            Thread.sleep(500) // begin publishes state just before the service registers listeners.
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 37.5665; longitude = 126.978; accuracy = 5f
                time = app.runs.active.value.startedAtMillis - 10000; elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            })
            Thread.sleep(1100) // Respect the actual GPS request's 1-second delivery interval.
            assertTrue("cached GPS predating run start must not poison sync", app.runs.active.value.points.isEmpty())
            fun emit(longitude: Double) {
                manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
                    latitude = 37.5665; this.longitude = longitude; accuracy = 5f; speed = 2.2f
                    time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                })
            }
            emit(126.978)
            until { app.runs.active.value.points.isNotEmpty() }
            device.pressHome()
            repeat(7) { i -> Thread.sleep(1100); emit(126.978 + (i + 1) * .000025) }
            until { app.runs.active.value.points.size >= 8 }
            val beforePause = app.runs.active.value.distanceMeters
            assertTrue("accepted route distance", beforePause in 14.0..17.0)
            assertTrue("foreground service advances active time", app.runs.active.value.activeDurationMillis >= 7000)
            RunTrackingService.command(app, RunTrackingService.PAUSE)
            until { app.runs.active.value.phase == RunPhase.PAUSED }
            val pausedCount = app.runs.active.value.points.size
            Thread.sleep(1100); emit(126.979)
            Thread.sleep(300)
            assertEquals(pausedCount, app.runs.active.value.points.size)
            RunTrackingService.command(app, RunTrackingService.RESUME)
            until { app.runs.active.value.phase == RunPhase.RUNNING }
            Thread.sleep(1100); emit(126.979)
            until { app.runs.active.value.points.size > pausedCount }
            assertEquals(beforePause, app.runs.active.value.distanceMeters, .05)
            Thread.sleep(1100); emit(126.979025)
            until { app.runs.active.value.distanceMeters > beforePause + 2 }
            RunTrackingService.command(app, RunTrackingService.FINISH)
            until { app.runs.active.value.phase == RunPhase.IDLE }
            val record = runBlocking { app.runs.record(runId) }!!
            assertEquals(10, record.points.size)
            assertTrue(record.points.last().segment > record.points.first().segment)
            assertTrue(record.finishedAtMillis >= record.startedAtMillis)
            assertEquals(false, app.getSharedPreferences("runmoni", Context.MODE_PRIVATE).getBoolean("publicLocation", true))
        } finally {
            if (app.runs.active.value.phase != RunPhase.IDLE) {
                RunTrackingService.command(app, RunTrackingService.FINISH)
                until { app.runs.active.value.phase == RunPhase.IDLE }
            }
            if (runId.isNotBlank()) runBlocking { app.runs.delete(runId) }
            runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
            runCatching { manager.removeTestProvider(LocationManager.NETWORK_PROVIDER) }
            device.executeShellCommand("appops set ${app.packageName} android:mock_location default")
            scenario.close()
        }
    }
    private fun until(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
        assertTrue("state arrived before timeout", condition())
    }
}

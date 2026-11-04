package com.runmoni.app.tracking

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import com.runmoni.app.R
import com.runmoni.app.RunmoniApplication
import com.runmoni.app.data.LocalWallet
import com.runmoni.app.domain.ActiveRun
import com.runmoni.app.domain.RunPhase
import com.runmoni.app.domain.PauseReason
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Real Android service + system test provider. Synthetic GPS is not outdoor accuracy evidence. */
@RunWith(AndroidJUnit4::class)
class RecorderServiceTest {
    @Suppress("DEPRECATION")
    @Test fun recoveryRetryAndLateIdleActionsStopTheirServiceAndRemoveTheRecorderNotification() {
        val app = ApplicationProvider.getApplicationContext<RunmoniApplication>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val services = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val channel = "recorder-regression-${UUID.randomUUID()}"
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var originalActive: ActiveRun? = null
        var ownsService = false
        var createdChannel = false
        fun recorderIsRunning() = services.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == RunTrackingService::class.java.name
        }
        try {
            // This test never takes over a real run or an existing recorder service.
            runBlocking { withTimeout(10_000) { app.runs.retryPersistence() } }
            instrumentation.waitForIdleSync()
            assertEquals("the synthetic recovery fixture requires an idle repository", RunPhase.IDLE, app.runs.active.value.phase)
            assertFalse("another recorder service must not be running", recorderIsRunning())
            assertTrue("notification permission is required to observe actual cleanup", notifications.areNotificationsEnabled())
            assertFalse("do not replace an existing recorder notification", notifications.activeNotifications.any { it.id == 1001 })
            val idle = app.runs.active.value
            originalActive = idle
            val originalProfile = LocalWallet(app).read()
            val originalRows = runBlocking { app.runs.dao.records().first() }
            val originalPoints = runBlocking { originalRows.associate { it.id to app.runs.dao.points(it.id) } }
            assertNull("no persisted unfinished run may be replaced by a synthetic fixture", runBlocking { app.runs.dao.unfinished() })

            notifications.createNotificationChannel(NotificationChannel(channel, "Recorder regression test", NotificationManager.IMPORTANCE_LOW))
            createdChannel = true
            val recovery = ActiveRun(id = "recorder-recovery-fixture", phase = RunPhase.RECOVERY,
                startedAtMillis = 1000, distanceMeters = 12.0, activeDurationMillis = 6000, gpsStatus = "테스트 복구 대기")
            listOf(recovery to RunTrackingService.RETRY, idle to RunTrackingService.PAUSE,
                idle to RunTrackingService.RESUME).forEach { (fixture, action) ->
                app.runs.active.value = fixture
                // An actual notification is a completion barrier: an unprocessed command
                // cannot pass merely because the service has not been created yet.
                notifications.notify(1001, Notification.Builder(app, channel).setSmallIcon(R.drawable.ic_stat_runmoni)
                    .setContentTitle("테스트 · 남아 있는 기록 알림").build())
                until { notifications.activeNotifications.any { it.id == 1001 } }
                ownsService = true
                scenario.onActivity { RunTrackingService.command(it, action) }
                until {
                    instrumentation.waitForIdleSync()
                    notifications.activeNotifications.none { it.id == 1001 } && !recorderIsRunning()
                }
                assertEquals("$action must preserve the inactive run state", fixture, app.runs.active.value)
                assertFalse("$action must not leave a recording notification", notifications.activeNotifications.any { it.id == 1001 })
                assertFalse("$action must stop the unneeded service", recorderIsRunning())
            }
            assertEquals(originalProfile, LocalWallet(app).read())
            assertEquals(originalRows, runBlocking { app.runs.dao.records().first() })
            assertEquals(originalPoints, runBlocking { originalRows.associate { it.id to app.runs.dao.points(it.id) } })
            assertNull(runBlocking { app.runs.dao.unfinished() })
        } finally {
            try {
                if (ownsService) {
                    app.stopService(Intent(app, RunTrackingService::class.java))
                    instrumentation.waitForIdleSync()
                    notifications.cancel(1001)
                    until { !recorderIsRunning() && notifications.activeNotifications.none { it.id == 1001 } }
                }
            } finally {
                originalActive?.let { app.runs.active.value = it }
                try { if (createdChannel) notifications.deleteNotificationChannel(channel) }
                finally { scenario.close() }
            }
        }
    }

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
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
            manager.addTestProvider(LocationManager.NETWORK_PROVIDER, false, true, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_COARSE)
            manager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
            scenario.onActivity { RunTrackingService.command(it, RunTrackingService.START) }
            until { app.runs.active.value.phase == RunPhase.RUNNING }
            runId = app.runs.active.value.id
            Thread.sleep(500) // begin publishes state just before the service registers listeners.
            assertNull("disabled provider must not show old accuracy", app.runs.active.value.accuracyMeters)
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            Thread.sleep(500)
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
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
            until { app.runs.active.value.accuracyMeters == null }
            Thread.sleep(1100)
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            until { app.runs.active.value.gpsStatus == "GPS 신호 기다리는 중" }
            emit(126.978225) // Plausible 4.4m gap: a speed/jump filter alone would accept it.
            until { app.runs.active.value.points.size >= 9 }
            assertEquals("GPS-off gap must not bridge distance", beforePause, app.runs.active.value.distanceMeters, .05)
            assertTrue(app.runs.active.value.points[8].segment > app.runs.active.value.points[7].segment)
            RunTrackingService.command(app, RunTrackingService.PAUSE)
            until { app.runs.active.value.phase == RunPhase.PAUSED }
            assertEquals(PauseReason.MANUAL, app.runs.active.value.pauseReason)
            val pausedCount = app.runs.active.value.points.size
            Thread.sleep(1100); emit(126.979)
            Thread.sleep(300)
            assertEquals(pausedCount, app.runs.active.value.points.size)
            RunTrackingService.command(app, RunTrackingService.RESUME)
            until { app.runs.active.value.phase == RunPhase.RUNNING }
            assertNull(app.runs.active.value.pauseReason)
            Thread.sleep(1100); emit(126.979)
            until { app.runs.active.value.points.size > pausedCount }
            assertEquals(beforePause, app.runs.active.value.distanceMeters, .05)
            Thread.sleep(1100); emit(126.979025)
            until { app.runs.active.value.distanceMeters > beforePause + 2 }
            RunTrackingService.command(app, RunTrackingService.FINISH)
            until { app.runs.active.value.phase == RunPhase.IDLE }
            val record = runBlocking { app.runs.record(runId) }!!
            assertEquals(11, record.points.size)
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

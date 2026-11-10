package com.runmoni.app.tracking

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.runmoni.app.RunmoniApplication
import com.runmoni.app.domain.*
import com.runmoni.app.data.PilotClient
import com.runmoni.app.data.SecureSession
import com.runmoni.app.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.Locale

/** The recorder belongs to this location FGS, not to an Activity's visible lifecycle. */
class RunTrackingService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository get() = (application as RunmoniApplication).runs
    private val preferences by lazy { getSharedPreferences("runmoni", MODE_PRIVATE) }
    private data class Command(val action: String, val expectedRunId: String?, val startId: Int)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var destroyed = false
    private var foreground = false
    private var pendingCommands = 0
    private var recordingRunId: String? = null
    private var tts: TextToSpeech? = null
    private var ticker: Job? = null
    private var presenceJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var renewWakeAt = 0L
    private var locationSubscribed = false
    private var timerKilometer = 0
    private var lastNotificationAt = 0L
    private val autoPause = AutoPauseController()
    private val manager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RunNotifications.createChannel(this)
        tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale.KOREAN }
        scope.launch {
            for (command in commands) {
                try { handle(command) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (repository.persistence.value.issue == null)
                        repository.gpsStatus("러닝을 시작하지 못했어요. 앱에서 위치 권한과 저장 상태를 확인해 주세요.")
                    syncResources(); updateNotification(true)
                } finally {
                    pendingCommands--
                    if (!destroyed && pendingCommands == 0) stopIfFinished()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action !in setOf(START, RECOVER, PAUSE, RESUME, FINISH, RETRY)) {
            if (!foreground) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (action == START || action == RECOVER) {
            if (!foreground) {
                try {
                    check(hasPreciseLocation())
                    startForeground(RunNotifications.ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    foreground = true
                } catch (_: RuntimeException) {
                    repository.gpsStatus("앱을 열고 정밀 위치 권한을 확인한 뒤 다시 시작해 주세요.")
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
            }
        } else if (!foreground && action in setOf(PAUSE, RESUME)) {
            // A delayed old PendingIntent may create a Service, but must not restart GPS.
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        pendingCommands++
        if (commands.trySend(Command(action!!, intent?.getStringExtra(RunNotifications.RUN_ID), startId)).isFailure) pendingCommands--
        // System/user process termination needs explicit recovery; it is not continuous tracking.
        return START_NOT_STICKY
    }

    private suspend fun handle(command: Command) {
        when (command.action) {
            START, RECOVER -> {
                if (!hasPreciseLocation()) {
                    repository.gpsStatus("정밀 위치 권한을 확인한 뒤 다시 시작해 주세요.")
                    if (recordingRunId != null) repository.pause(expectedRunId = recordingRunId)
                    syncResources(); return
                }
                if (recordingRunId == repository.active.value.id && recordingRunId != null) {
                    updateNotification(true); return
                }
                repository.begin(command.action == RECOVER)
                recordingRunId = repository.active.value.id
                autoPause.reset()
                timerKilometer = (repository.active.value.distanceMeters / 1000).toInt()
                syncResources(); startTicker(); startPresence()
            }
            PAUSE, RESUME -> {
                val control = if (command.action == PAUSE) RunNotificationControl.PAUSE else RunNotificationControl.RESUME
                if (!RunControlPolicy.allows(control, command.expectedRunId, recordingRunId, repository.active.value, repository.persistence.value)) return
                autoPause.reset()
                if (control == RunNotificationControl.PAUSE) recorderOperation { repository.pause(expectedRunId = command.expectedRunId) }
                else if (hasPreciseLocation()) recorderOperation { repository.resume(expectedRunId = command.expectedRunId) }
                else repository.gpsStatus("정밀 위치 권한을 확인한 뒤 계속 달릴 수 있어요.")
                syncResources()
            }
            FINISH -> {
                if (command.expectedRunId.isNullOrBlank() || command.expectedRunId != repository.active.value.id) return
                (application as com.runmoni.app.RunmoniApplication).runnerSharing.stopForRun(recordingRunId)
                preferences.edit().putBoolean("publicLocation", false).apply()
                presenceJob?.cancel(); presenceJob = null
                stopRecordingResources()
                recorderOperation(RunPersistenceIssue.FINISH) { repository.finish(expectedRunId = command.expectedRunId) }
                withTimeoutOrNull(5000) { revokePresence() }
            }
            RETRY -> {
                recorderOperation(repository.persistence.value.issue ?: RunPersistenceIssue.CHECKPOINT) {
                    repository.retryPersistence(expectedRunId = command.expectedRunId)
                }
                syncResources()
            }
        }
        updateNotification(true)
    }

    private fun hasPreciseLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun syncResources() {
        val run = repository.active.value
        val storage = repository.persistence.value
        val wantsFixes = !destroyed && foreground && recordingRunId == run.id && !storage.finishing && storage.issue != RunPersistenceIssue.CHECKPOINT &&
            (run.phase == RunPhase.RUNNING || run.phase == RunPhase.PAUSED && run.pauseReason == PauseReason.AUTOMATIC) && hasPreciseLocation()
        if (wantsFixes) { if (!locationSubscribed) startLocation(); renewWakeLease() }
        else { stopLocation(); releaseWakeLock() }
    }

    private fun renewWakeLease() {
        val now = SystemClock.elapsedRealtime()
        if (wakeLock == null) wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "runmoni:recording").apply { setReferenceCounted(false) }
        // Renew before expiry rather than relying on a sleeping CPU to notice an expired lease.
        if (wakeLock?.isHeld != true || now >= renewWakeAt) {
            wakeLock?.acquire(60 * 60 * 1000L)
            renewWakeAt = now + 30 * 60 * 1000L
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                val run = repository.active.value
                recorderOperation {
                    if (!hasPreciseLocation() && run.phase in setOf(RunPhase.RUNNING, RunPhase.PAUSED)) {
                        autoPause.reset()
                        repository.interruptLocation("정밀 위치 권한이 해제됐어요. 앱에서 권한을 확인해 주세요.")
                        repository.pause(expectedRunId = recordingRunId)
                    } else applyAutoPause(autoPause.onTick(SystemClock.elapsedRealtime(), preferences.getBoolean("autoPause", false), run.phase, run.pauseReason))
                    repository.tick()
                }
                syncResources(); updateNotification()
                val km = (repository.active.value.distanceMeters / 1000).toInt()
                if (km > timerKilometer && preferences.getBoolean("voiceGuidance", true)) {
                    timerKilometer = km
                    tts?.speak("${km}킬로미터 달렸어요", TextToSpeech.QUEUE_FLUSH, null, "km:$km")
                }
            }
        }
    }
    private fun pilotClient(): PilotClient? {
        if (preferences.getString("communityBackend", BuildConfig.COMMUNITY_BACKEND) != "http") return null
        val token = SecureSession(this).read()
        if (token.isBlank()) return null
        return PilotClient(preferences.getString("apiUrl", BuildConfig.PILOT_API_URL)!!, token)
    }
    private suspend fun revokePresence() {
        try { pilotClient()?.request("DELETE", "/v1/presence") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Optional pilot presence cannot invalidate local recording. */ }
    }
    private fun startPresence() {
        presenceJob?.cancel()
        presenceJob = scope.launch {
            var sharing = false
            while (isActive) {
                val state = repository.active.value
                val point = state.points.lastOrNull()
                val enabled = preferences.getString("communityBackend", BuildConfig.COMMUNITY_BACKEND) == "http" && !preferences.getBoolean("communityGuest", false) && preferences.getBoolean("publicLocation", false) && state.phase == RunPhase.RUNNING && point != null && point.accuracyMeters <= 30 && System.currentTimeMillis() - point.recordedAtMillis in 0..30_000
                if (enabled) {
                    // The location recorder remains independent of every network failure.
                    try {
                        pilotClient()?.let { client ->
                            client.request("POST", "/v1/runs", JSONObject().put("id", state.id).put("started_at_ms", state.startedAtMillis))
                            client.request("PUT", "/v1/presence", JSONObject().put("run_id", state.id).put("scope_type", "pilot").put("exact_opt_in", true).put("latitude", point!!.latitude).put("longitude", point.longitude))
                            sharing = true
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* The next bounded presence attempt may recover. */ }
                } else if (sharing) { revokePresence(); sharing = false }
                delay(15_000)
            }
        }
    }
    @Suppress("MissingPermission")
    private fun startLocation() {
        try {
            if (LocationManager.GPS_PROVIDER in manager.allProviders) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
                locationSubscribed = true
                if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) repository.gpsStatus("기기의 GPS를 켜 주세요.")
            } else repository.gpsStatus("이 기기에서 GPS를 사용할 수 없어요.")
            if (LocationManager.NETWORK_PROVIDER in manager.allProviders) {
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, this, Looper.getMainLooper())
                locationSubscribed = true
            }
        } catch (_: SecurityException) { repository.gpsStatus("위치 권한이 해제됐어요.") }
    }

    override fun onLocationChanged(location: Location) {
        scope.launch {
            recorderOperation {
                val now = SystemClock.elapsedRealtime()
                val run = repository.active.value
                val fresh = now - location.elapsedRealtimeNanos / 1_000_000 in 0..5_000 && location.time >= run.startedAtMillis
                applyAutoPause(autoPause.onLocation(now, preferences.getBoolean("autoPause", false), run.phase, run.pauseReason,
                    location.speed.takeIf { fresh && location.hasSpeed() }, location.accuracy.takeIf { fresh && location.hasAccuracy() }))
                repository.location(location)
            }
            syncResources(); updateNotification()
        }
    }

    private suspend fun applyAutoPause(decision: AutoPauseController.Decision) {
        if (repository.persistence.value.finishing || repository.persistence.value.issue == RunPersistenceIssue.CHECKPOINT) {
            autoPause.reset(); return
        }
        when (decision) {
            AutoPauseController.Decision.PAUSE -> repository.pause(PauseReason.AUTOMATIC, expectedRunId = recordingRunId)
            AutoPauseController.Decision.RESUME -> repository.resume(expectedRunId = recordingRunId)
            AutoPauseController.Decision.NONE -> return
        }
        syncResources(); updateNotification(true)
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            autoPause.reset()
            scope.launch { recorderOperation { repository.interruptLocation("GPS 꺼짐 · 기존 기록은 보존돼요.") }; updateNotification(true) }
        }
    }
    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            autoPause.reset()
            scope.launch { recorderOperation { repository.interruptLocation("GPS 신호 기다리는 중") }; updateNotification(true) }
        }
    }

    private fun notification(): Notification = RunNotifications.build(this, repository.active.value, repository.persistence.value)
    private fun updateNotification(force: Boolean = false) {
        (application as com.runmoni.app.RunmoniApplication).runnerSharing.observe(repository.active.value,
            live = foreground && !destroyed && recordingRunId == repository.active.value.id && recordingRunId != null, ownerRunId = recordingRunId)
        if (!foreground || destroyed) return
        com.runmoni.app.widget.RunWidgetPublisher.publish(this, repository.active.value, repository.persistence.value,
            live = recordingRunId == repository.active.value.id && recordingRunId != null)
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastNotificationAt < 1000) return
        try { getSystemService(NotificationManager::class.java).notify(RunNotifications.ID, notification()); lastNotificationAt = now }
        catch (_: SecurityException) { /* Notification permission is separate from local recording. */ }
    }

    private suspend fun recorderOperation(issue: RunPersistenceIssue = RunPersistenceIssue.CHECKPOINT, block: suspend () -> Unit) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { repository.reportOperationFailure(issue) }
    }
    private fun stopLocation() {
        try { manager.removeUpdates(this) } catch (_: RuntimeException) { /* Cleanup must not prevent a durable finish. */ }
        locationSubscribed = false
    }
    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: RuntimeException) { }
        renewWakeAt = 0L
    }
    private fun stopRecordingResources() { ticker?.cancel(); ticker = null; stopLocation(); releaseWakeLock() }
    private fun stopIdleService() {
        (application as com.runmoni.app.RunmoniApplication).runnerSharing.stopForRun(recordingRunId)
        com.runmoni.app.widget.RunWidgetPublisher.stopped(this, recordingRunId)
        stopRecordingResources()
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        recordingRunId = null
        getSystemService(NotificationManager::class.java).cancel(RunNotifications.ID)
        stopSelf()
    }
    private fun stopIfFinished() {
        if (repository.active.value.phase in setOf(RunPhase.IDLE, RunPhase.RECOVERY) && !repository.persistence.value.finishing)
            stopIdleService()
        else updateNotification(true)
    }

    override fun onDestroy() {
        (application as com.runmoni.app.RunmoniApplication).runnerSharing.observe(repository.active.value, live = false, ownerRunId = recordingRunId)
        com.runmoni.app.widget.RunWidgetPublisher.stopped(this, recordingRunId)
        destroyed = true
        foreground = false
        val id = recordingRunId
        stopRecordingResources(); presenceJob?.cancel(); tts?.shutdown(); commands.close(); scope.cancel()
        if (!id.isNullOrBlank()) repository.recorderStopped(id, SystemClock.elapsedRealtime())
        super.onDestroy()
    }

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        val run = repository.active.value
        writer.println("Runmoni recorder phase=${run.phase} foreground=$foreground locationSubscribed=$locationSubscribed wakeLockHeld=${wakeLock?.isHeld == true}")
        writer.println("distanceMeters=${run.distanceMeters} activeDurationMillis=${run.activeDurationMillis} pointCount=${run.points.size} speedMetersPerSecond=${run.currentSpeedMetersPerSecond}")
        writer.println("persistenceIssue=${repository.persistence.value.issue} finishing=${repository.persistence.value.finishing}")
    }

    companion object {
        const val START = "runmoni.START"; const val RECOVER = "runmoni.RECOVER"
        const val PAUSE = "runmoni.PAUSE"; const val RESUME = "runmoni.RESUME"; const val FINISH = "runmoni.FINISH"
        const val RETRY = "runmoni.RETRY"
        fun command(context: Context, action: String, expectedRunId: String? = null): Boolean {
            val id = expectedRunId ?: (context.applicationContext as RunmoniApplication).runs.active.value.id
            val intent = Intent(context, RunTrackingService::class.java).setAction(action).putExtra(RunNotifications.RUN_ID, id)
            return try {
                if (action == START || action == RECOVER) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
                true
            } catch (_: RuntimeException) { false }
        }
    }
}

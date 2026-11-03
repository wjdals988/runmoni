package com.runmoni.app.tracking

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.runmoni.app.MainActivity
import com.runmoni.app.R
import com.runmoni.app.RunmoniApplication
import com.runmoni.app.domain.RunPhase
import com.runmoni.app.domain.PauseReason
import com.runmoni.app.domain.AutoPauseController
import com.runmoni.app.domain.RunPersistenceIssue
import com.runmoni.app.data.PilotClient
import com.runmoni.app.data.SecureSession
import com.runmoni.app.BuildConfig
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.Locale

class RunTrackingService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository get() = (application as RunmoniApplication).runs
    private val preferences by lazy { getSharedPreferences("runmoni", MODE_PRIVATE) }
    private var tts: TextToSpeech? = null
    private var ticker: Job? = null
    private var presenceJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var timerKilometer = 0
    private val autoPause = AutoPauseController()
    private val manager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL, "러닝 기록", NotificationManager.IMPORTANCE_LOW))
        tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale.KOREAN }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        when (action) {
            START, RECOVER -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    repository.gpsStatus("정밀 위치 권한이 필요합니다."); stopSelf(); return START_NOT_STICKY
                }
                startForeground(1001, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                scope.launch {
                    try {
                        repository.begin(action == RECOVER)
                        autoPause.reset()
                        timerKilometer = (repository.active.value.distanceMeters / 1000).toInt()
                        if (wakeLock?.isHeld == true) wakeLock?.release()
                        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "runmoni:recording").apply { acquire(2 * 60 * 60 * 1000L) }
                        startLocation(); startTicker(); startPresence()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        if (repository.persistence.value.issue == null) repository.gpsStatus(
                            (failure as? IllegalStateException)?.message ?: "러닝을 시작하지 못했어요. 다시 확인해 주세요.")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            PAUSE -> scope.launch { autoPause.reset(); recorderOperation { repository.pause() }; updateNotification() }
            RESUME -> scope.launch {
                autoPause.reset()
                recorderOperation { repository.resume() }
                if (repository.active.value.phase == RunPhase.RUNNING) { startLocation(); startTicker(); startPresence() }
                updateNotification()
            }
            FINISH -> scope.launch {
                preferences.edit().putBoolean("publicLocation", false).apply()
                presenceJob?.cancel()
                stopRecordingResources()
                recorderOperation(RunPersistenceIssue.FINISH) { repository.finish() }
                withTimeoutOrNull(5000) { revokePresence() }
                stopIfFinished()
            }
            RETRY -> scope.launch {
                recorderOperation(repository.persistence.value.issue ?: RunPersistenceIssue.CHECKPOINT) { repository.retryPersistence() }
                if (repository.active.value.phase == RunPhase.IDLE) stopIfFinished() else updateNotification()
            }
        }
        // OS force-stop is not represented as continuous tracking.
        return START_NOT_STICKY
    }
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                val run = repository.active.value
                recorderOperation {
                    applyAutoPause(autoPause.onTick(SystemClock.elapsedRealtime(), preferences.getBoolean("autoPause", false), run.phase, run.pauseReason))
                    repository.tick()
                }
                updateNotification()
                if (wakeLock?.isHeld != true) wakeLock?.acquire(2 * 60 * 60 * 1000L)
                val state = repository.active.value
                val km = (state.distanceMeters / 1000).toInt()
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
            // Subscribe even while disabled so enabling GPS later delivers fixes without restarting the run.
            if (LocationManager.GPS_PROVIDER in manager.allProviders) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
                if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) repository.gpsStatus("기기의 GPS를 켜 주세요.")
            } else repository.gpsStatus("이 기기에서 GPS를 사용할 수 없어요.")
            if (LocationManager.NETWORK_PROVIDER in manager.allProviders) manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, this, Looper.getMainLooper())
        } catch (_: SecurityException) { repository.gpsStatus("위치 권한이 해제되었습니다.") }
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
        }
    }
    private suspend fun applyAutoPause(decision: AutoPauseController.Decision) {
        if (repository.persistence.value.finishing || repository.persistence.value.issue == RunPersistenceIssue.CHECKPOINT) {
            autoPause.reset()
            return
        }
        when (decision) {
            AutoPauseController.Decision.PAUSE -> repository.pause(PauseReason.AUTOMATIC)
            AutoPauseController.Decision.RESUME -> repository.resume()
            AutoPauseController.Decision.NONE -> return
        }
        updateNotification()
    }
    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            autoPause.reset()
            scope.launch { recorderOperation { repository.interruptLocation("GPS 꺼짐 · 기존 기록은 보존됩니다.") } }
        }
    }
    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            autoPause.reset()
            scope.launch { recorderOperation { repository.interruptLocation("GPS 신호 기다리는 중") } }
        }
    }
    private fun notification(): Notification {
        val state = repository.active.value
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val paused = state.phase == RunPhase.PAUSED
        val pause = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(if (paused) RESUME else PAUSE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val storage = repository.persistence.value
        val title = if (storage.finishing) "런모니 · 종료 기록 저장 확인" else if (storage.issue == RunPersistenceIssue.CHECKPOINT) "런모니 · 기록 저장 확인" else if (!paused) "런모니 · 러닝 기록 중" else if (state.pauseReason == PauseReason.AUTOMATIC) "런모니 · 자동 일시정지" else "런모니 · 일시정지"
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_runmoni).setContentTitle(title)
            .setContentText(String.format(Locale.KOREA, "%.2f km · %d분", state.distanceMeters / 1000, state.activeDurationMillis / 60_000))
            .setContentIntent(open).setOngoing(true).apply {
                if (!storage.finishing && storage.issue != RunPersistenceIssue.CHECKPOINT) addAction(0, if (paused) "재개" else "일시정지", pause)
            }.build()
    }
    private fun updateNotification() {
        try { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1001, notification()) }
        catch (_: SecurityException) { /* Notification permission does not change local durability. */ }
    }
    private suspend fun recorderOperation(issue: RunPersistenceIssue = RunPersistenceIssue.CHECKPOINT, block: suspend () -> Unit) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { repository.reportOperationFailure(issue) }
    }
    private fun stopRecordingResources() {
        ticker?.cancel(); ticker = null
        manager.removeUpdates(this)
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }
    private fun stopIfFinished() {
        if (repository.active.value.phase == RunPhase.IDLE && !repository.persistence.value.finishing) {
            stopRecordingResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else updateNotification()
    }
    override fun onDestroy() {
        manager.removeUpdates(this); ticker?.cancel(); presenceJob?.cancel(); if (wakeLock?.isHeld == true) wakeLock?.release(); tts?.shutdown(); scope.cancel(); super.onDestroy()
    }
    companion object {
        const val START = "runmoni.START"; const val RECOVER = "runmoni.RECOVER"
        const val PAUSE = "runmoni.PAUSE"; const val RESUME = "runmoni.RESUME"; const val FINISH = "runmoni.FINISH"
        const val RETRY = "runmoni.RETRY"
        private const val CHANNEL = "runmoni-tracking"
        fun command(context: Context, action: String) {
            val intent = Intent(context, RunTrackingService::class.java).setAction(action)
            if (action == START || action == RECOVER) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        }
    }
}

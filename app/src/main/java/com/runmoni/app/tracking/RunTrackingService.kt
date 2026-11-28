package com.runmoni.app.tracking

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
    private data class Command(val action: String, val expectedRunId: String?, val goal: RunGoal?)
    private data class LapSpeech(val utteranceId: String, val focus: AudioFocusRequest)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var destroyed = false
    private var foreground = false
    private var pendingCommands = 0
    private var recordingRunId: String? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lapSpeech: LapSpeech? = null
    private var lapSpeechTimeout: Job? = null
    private var lapVibrationEnd = 0L
    private var ticker: Job? = null
    private var presenceJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var renewWakeAt = 0L
    private var locationSubscribed = false
    private var lastNotificationAt = 0L
    private val autoPause = AutoPauseController()
    private val lapCueTracker = RunLapCueTracker()
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val lapSpeechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val lapVibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(VibratorManager::class.java)?.defaultVibrator
        else getSystemService(Vibrator::class.java)
    }
    private val manager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RunNotifications.createChannel(this)
        tts = runCatching {
            TextToSpeech(this) { status -> scope.launch(Dispatchers.Main) { initializeLapSpeech(status) } }
        }.getOrNull()
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
            // Remove the orphaned notification when there is no active recorder or pending save.
            val phase = repository.active.value.phase
            if (pendingCommands == 0 && !repository.persistence.value.finishing &&
                (phase == RunPhase.IDLE || phase == RunPhase.RECOVERY)) {
                getSystemService(NotificationManager::class.java).cancel(RunNotifications.ID)
            }
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        pendingCommands++
        val goal = if (action == START) {
            RunGoal.fromStorage(intent?.getStringExtra(GOAL_KIND), intent?.getIntExtra(GOAL_TARGET, 0) ?: 0)
        } else null
        if (commands.trySend(Command(action!!, intent?.getStringExtra(RunNotifications.RUN_ID), goal)).isFailure) pendingCommands--
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
                repository.begin(command.action == RECOVER, command.goal)
                val run = repository.active.value
                recordingRunId = run.id
                autoPause.reset()
                lapCueTracker.start(run)
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
        val canRecord = !destroyed && foreground && recordingRunId == run.id && !storage.finishing &&
            storage.issue != RunPersistenceIssue.CHECKPOINT && hasPreciseLocation()
        val wantsFixes = canRecord &&
            (run.phase == RunPhase.RUNNING || run.phase == RunPhase.PAUSED && run.pauseReason == PauseReason.AUTOMATIC)
        if (wantsFixes) { if (!locationSubscribed) startLocation(); renewWakeLease() }
        else { stopLocation(); releaseWakeLock() }
        val canAnnounce = canRecord && run.phase == RunPhase.RUNNING && ticker?.isActive == true
        // A newer fix can publish its distance before the Room checkpoint completes.
        if (!canAnnounce || !storage.saving) updateLapCue(run, canAnnounce)
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
            }
        }
    }

    private fun initializeLapSpeech(status: Int) {
        val engine = tts ?: return
        if (destroyed || status != TextToSpeech.SUCCESS) return
        ttsReady = runCatching {
            engine.setLanguage(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE &&
                engine.setAudioAttributes(lapSpeechAttributes) == TextToSpeech.SUCCESS &&
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) = Unit
                    override fun onDone(utteranceId: String) = releaseFinishedLapSpeech(utteranceId)
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String) = releaseFinishedLapSpeech(utteranceId)
                    override fun onStop(utteranceId: String, interrupted: Boolean) = releaseFinishedLapSpeech(utteranceId)
                }) == TextToSpeech.SUCCESS
        }.getOrDefault(false)
    }

    private fun releaseFinishedLapSpeech(utteranceId: String) {
        scope.launch {
            if (lapSpeech?.utteranceId == utteranceId) stopLapSpeech(stopEngine = false)
        }
    }

    private fun updateLapCue(run: ActiveRun, live: Boolean) {
        val voice = preferences.getBoolean("voiceGuidance", true)
        val vibration = preferences.getBoolean("lapVibration", false)
        if (!live || !voice) stopLapSpeech()
        if (!live || !vibration) stopLapVibration()
        val cue = lapCueTracker.observe(run, live, voice, preferences.getBoolean("lapPaceGuidance", false), vibration) ?: return
        val interruptionFilter = runCatching {
            getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
        }.getOrNull()
        if (interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
            interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS) return
        if (cue.vibrate) deliverLapVibration()
        cue.speech?.let { deliverLapSpeech(cue.kilometer, it) }
    }

    private fun deliverLapSpeech(kilometer: Int, text: String) {
        val engine = tts?.takeIf { ttsReady } ?: return
        stopLapSpeech()
        val utteranceId = "lap:$kilometer:${SystemClock.elapsedRealtimeNanos()}"
        try {
            val audio = audioManager ?: return
            if (audio.isStreamMute(AudioManager.STREAM_MUSIC) || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) return
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(lapSpeechAttributes)
                .setOnAudioFocusChangeListener({ change ->
                    if (change < 0 && lapSpeech?.utteranceId == utteranceId) stopLapSpeech()
                }, mainHandler)
                .build()
            if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
            lapSpeech = LapSpeech(utteranceId, request)
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) != TextToSpeech.SUCCESS) {
                stopLapSpeech(stopEngine = false)
                return
            }
            lapSpeechTimeout = scope.launch {
                delay(LAP_SPEECH_TIMEOUT_MILLIS)
                if (lapSpeech?.utteranceId == utteranceId) stopLapSpeech()
            }
        } catch (_: RuntimeException) { stopLapSpeech() }
    }

    private fun stopLapSpeech(stopEngine: Boolean = true) {
        val speech = lapSpeech ?: return
        lapSpeech = null
        lapSpeechTimeout?.cancel()
        lapSpeechTimeout = null
        if (stopEngine) runCatching { tts?.stop() }
        runCatching { audioManager?.abandonAudioFocusRequest(speech.focus) }
    }

    @Suppress("DEPRECATION")
    private fun deliverLapVibration() {
        runCatching {
            val audio = audioManager ?: return
            if (audio.ringerMode == AudioManager.RINGER_MODE_SILENT) return
            val vibrator = lapVibrator?.takeIf { it.hasVibrator() } ?: return
            val effect = VibrationEffect.createOneShot(LAP_VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
            } else {
                val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                vibrator.vibrate(effect, attributes)
            }
            lapVibrationEnd = SystemClock.elapsedRealtime() + LAP_VIBRATION_MILLIS
        }
    }

    private fun stopLapVibration() {
        val end = lapVibrationEnd
        lapVibrationEnd = 0L
        if (end > SystemClock.elapsedRealtime()) runCatching { lapVibrator?.cancel() }
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
    private fun stopRecordingResources() {
        ticker?.cancel(); ticker = null
        stopLapSpeech(); stopLapVibration()
        stopLocation(); releaseWakeLock()
    }
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
        stopRecordingResources(); presenceJob?.cancel(); runCatching { tts?.shutdown() }; commands.close(); scope.cancel()
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
        private const val LAP_SPEECH_TIMEOUT_MILLIS = 10_000L
        private const val LAP_VIBRATION_MILLIS = 180L
        private const val GOAL_KIND = "runmoni.goal.kind"
        private const val GOAL_TARGET = "runmoni.goal.target"
        const val START = "runmoni.START"; const val RECOVER = "runmoni.RECOVER"
        const val PAUSE = "runmoni.PAUSE"; const val RESUME = "runmoni.RESUME"; const val FINISH = "runmoni.FINISH"
        const val RETRY = "runmoni.RETRY"
        fun command(context: Context, action: String, expectedRunId: String? = null, goal: RunGoal? = null): Boolean {
            val id = expectedRunId ?: (context.applicationContext as RunmoniApplication).runs.active.value.id
            val intent = Intent(context, RunTrackingService::class.java).setAction(action).putExtra(RunNotifications.RUN_ID, id)
            if (action == START && goal != null) intent.putExtra(GOAL_KIND, goal.kind.name).putExtra(GOAL_TARGET, goal.target)
            return try {
                if (action == START || action == RECOVER) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
                true
            } catch (_: RuntimeException) { false }
        }
    }
}

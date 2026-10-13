package com.runmoni.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** User-visible foreground lifetime only; PCM and credentials remain in the controller, never Intent extras. */
class AudioProjectionService : Service() {
    private var intentionalStop = false
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runningService = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "러닝 음성·음악", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            intentionalStop = true
            onUserStop?.invoke()
            stopSelf()
            return START_NOT_STICKY
        }

        val types = intent?.getIntExtra(EXTRA_TYPES, 0) ?: 0
        if (types == 0) {
            intentionalStop = true
            stopSelf()
            return START_NOT_STICKY
        }
        val stopIntent = Intent(this, AudioProjectionService::class.java).setAction(ACTION_STOP)
        val stop = PendingIntent.getService(this, 4202, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("러닝 음성·음악 사용 중")
            .setContentText("알림에서 음성·음악을 종료할 수 있습니다")
            .setOngoing(true).addAction(Notification.Action.Builder(null, "종료", stop).build()).build()
        val requestId = intent?.getLongExtra(EXTRA_REQUEST_ID, 0) ?: 0
        intentionalStop = false
        try {
            startForeground(4201, notification, types)
            pendingStarts[requestId]?.complete(Unit)
        } catch (failure: Exception) {
            // Return the FGS denial to the controller instead of crashing or reporting active audio.
            pendingStarts[requestId]?.completeExceptionally(
                IllegalStateException("음성·음악 알림 서비스를 시작하지 못했습니다. 앱을 화면에서 다시 실행해 주세요"))
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (runningService === this) runningService = null
        if (!intentionalStop) onUserStop?.invoke()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "runmoni_audio"
        private const val ACTION_STOP = "com.runmoni.audio.STOP"
        private const val EXTRA_TYPES = "types"
        private const val EXTRA_REQUEST_ID = "request_id"
        private val requestIds = AtomicLong(0)
        private val pendingStarts = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()
        internal var onUserStop: (() -> Unit)? = null
        private var runningService: AudioProjectionService? = null

        internal fun types(microphone: Boolean, projection: Boolean, playback: Boolean): Int {
            var value = 0
            if (microphone && Build.VERSION.SDK_INT >= 30) value = value or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (projection) value = value or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (playback) value = value or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            // API 29 has no microphone FGS type. Media playback covers its active call audio lifetime.
            if (microphone && Build.VERSION.SDK_INT == 29) value = value or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            return value
        }

        internal suspend fun ensureStarted(context: Context, types: Int) {
            check(types != 0)
            val requestId = requestIds.incrementAndGet()
            val started = CompletableDeferred<Unit>()
            pendingStarts[requestId] = started
            try {
                ContextCompat.startForegroundService(context, Intent(context, AudioProjectionService::class.java)
                    .putExtra(EXTRA_TYPES, types).putExtra(EXTRA_REQUEST_ID, requestId))
                withTimeout(5_000) { started.await() }
            } finally {
                pendingStarts.remove(requestId)
            }
        }

        internal fun stop(context: Context) {
            runningService?.intentionalStop = true
            context.stopService(Intent(context, AudioProjectionService::class.java))
        }
    }
}

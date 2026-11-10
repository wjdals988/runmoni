package com.runmoni.app.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.runmoni.app.MainActivity
import com.runmoni.app.R
import com.runmoni.app.domain.ActiveRun
import com.runmoni.app.domain.RunNotificationContent
import com.runmoni.app.domain.RunNotificationControl
import com.runmoni.app.domain.RunPersistenceState

internal object RunNotifications {
    const val CHANNEL = "runmoni-tracking"
    const val ID = 1001
    const val OPEN_RUN = "runmoni.OPEN_RUN"
    const val RUN_ID = "runmoni.runId"
    const val CONFIRM_FINISH = "runmoni.confirmFinish"
    const val EVENT = "runmoni.notificationEvent"
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "러닝 기록", NotificationManager.IMPORTANCE_LOW)
        channel.description = "진행 중인 러닝 정보와 일시정지·재개"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun enabled(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    private fun identity(id: String, verb: String): Uri = Uri.Builder().scheme("runmoni").authority("recording")
        .appendPath(id).appendPath(verb).build()

    fun activity(context: Context, id: String, confirm: Boolean): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).setAction(OPEN_RUN).setData(identity(id, if (confirm) "confirm-finish" else "open"))
            .putExtra(RUN_ID, id).putExtra(CONFIRM_FINISH, confirm).putExtra(EVENT, android.os.SystemClock.elapsedRealtimeNanos().toString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), FLAGS)

    internal fun control(context: Context, id: String, action: String): PendingIntent = PendingIntent.getService(context, 0,
        Intent(context, RunTrackingService::class.java).setAction(action).setData(identity(id, action))
            .putExtra(RUN_ID, id), FLAGS)

    fun build(context: Context, run: ActiveRun, storage: RunPersistenceState): Notification {
        val content = RunNotificationContent.from(run, storage)
        val open = activity(context, run.id, false)
        val public = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_runmoni)
            .setContentTitle("러닝 진행 중").setContentText("앱을 열어 확인해 주세요")
            .setContentIntent(open).setOngoing(true).setSilent(true).setShowWhen(false).build()
        return NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_runmoni)
            .setContentTitle(content.title).setContentText(content.metrics)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.metrics + "\n" + content.detail))
            .setContentIntent(open).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(false)
            .apply {
                when (content.control) {
                    RunNotificationControl.PAUSE -> addAction(0, "일시정지", control(context, run.id, RunTrackingService.PAUSE))
                    RunNotificationControl.RESUME -> addAction(0, "재개", control(context, run.id, RunTrackingService.RESUME))
                    null -> Unit
                }
                if (content.canFinish) addAction(0, "종료", activity(context, run.id, true))
                else if (storage.issue != null || storage.finishing) addAction(0, "저장 확인", open)
            }.build()
    }
}

package com.runmoni.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.runmoni.app.MainActivity
import com.runmoni.app.domain.RunWidgetOperation
import com.runmoni.app.domain.RunWidgetTap
import com.runmoni.app.tracking.RunNotifications
import com.runmoni.app.tracking.RunTrackingService

internal object RunWidgetIntents {
    const val TAP = "com.runmoni.app.widget.TAP"
    private const val AUTHORITY = "widget-command"
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun pending(context: Context, tap: RunWidgetTap): PendingIntent = if (tap.operation == RunWidgetOperation.START) {
        PendingIntent.getForegroundService(context, 0, service(context, tap), FLAGS)
    } else PendingIntent.getBroadcast(context, 0, Intent(context, RunWidgetProvider::class.java)
        .setAction(TAP).setData(data(tap)), FLAGS)

    fun service(context: Context, tap: RunWidgetTap) = Intent(context, RunTrackingService::class.java)
        .setAction(serviceAction(tap.operation)).setData(data(tap)).putExtra(RunNotifications.RUN_ID, tap.runId)

    fun serviceAction(operation: RunWidgetOperation): String? = when (operation) {
        RunWidgetOperation.START -> RunTrackingService.START
        RunWidgetOperation.PAUSE -> RunTrackingService.PAUSE
        RunWidgetOperation.RESUME -> RunTrackingService.RESUME
        RunWidgetOperation.FINISH -> RunTrackingService.FINISH
        RunWidgetOperation.RETRY -> RunTrackingService.RETRY
        RunWidgetOperation.ASK_FINISH, RunWidgetOperation.CANCEL_FINISH -> null
    }

    fun marked(intent: Intent?) = intent?.data?.scheme == "runmoni" && intent.data?.authority == AUTHORITY

    fun decode(intent: Intent?): RunWidgetTap? {
        if (!marked(intent)) return null
        val uri = intent?.data ?: return null
        val operation = RunWidgetOperation.entries.firstOrNull { it.name == uri.pathSegments.singleOrNull() } ?: return null
        val token = uri.getQueryParameter("token")?.takeIf { it.length == 36 } ?: return null
        val runId = uri.getQueryParameter("run")?.takeIf { it.length <= 256 } ?: return null
        if ((operation == RunWidgetOperation.START) != runId.isEmpty()) return null
        return RunWidgetTap(operation, runId, token).takeIf { data(it) == uri }
    }

    fun prepare(context: Context): PendingIntent = activity(context, RunNotifications.WIDGET_PREPARE, "")
    fun result(context: Context, id: String): PendingIntent = activity(context, RunNotifications.WIDGET_RESULT, id)

    private fun data(tap: RunWidgetTap) = Uri.Builder().scheme("runmoni").authority(AUTHORITY)
        .appendPath(tap.operation.name).appendQueryParameter("run", tap.runId).appendQueryParameter("token", tap.token).build()

    private fun activity(context: Context, action: String, id: String): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).setAction(action)
            .setData(Uri.Builder().scheme("runmoni").authority("widget").appendPath(action).appendPath(id).build())
            .putExtra(RunNotifications.RUN_ID, id).putExtra(RunNotifications.EVENT, android.os.SystemClock.elapsedRealtimeNanos().toString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), FLAGS)
}

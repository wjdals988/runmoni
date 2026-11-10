package com.runmoni.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.runmoni.app.domain.RunWidgetControl
import com.runmoni.app.tracking.RunNotifications
import com.runmoni.app.tracking.RunTrackingService

/** All cold callbacks read only metrics. No Location, Auth or RunRepository access. */
class RunWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        RunWidgetPublisher.updateWidgets(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        RunWidgetPublisher.updateWidgets(context, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        val control = when (intent.action) {
            ACTION_PAUSE -> RunWidgetControl.PAUSE
            ACTION_RESUME -> RunWidgetControl.RESUME
            else -> null
        }
        if (control == null) {
            super.onReceive(context, intent)
            return
        }
        val id = intent.getStringExtra(RunNotifications.RUN_ID)
        val verb = if (control == RunWidgetControl.PAUSE) "pause" else "resume"
        val expectedData = RunWidgetViews.identity(id.orEmpty(), verb)
        if (intent.data != expectedData || !RunWidgetPublisher.allows(context, id, control)) {
            RunWidgetPublisher.updateWidgets(context)
            return
        }
        try {
            RunNotifications.control(context, id!!, if (control == RunWidgetControl.PAUSE) RunTrackingService.PAUSE else RunTrackingService.RESUME).send()
        } catch (_: android.app.PendingIntent.CanceledException) {
            // A lost action is retried from the app; it never creates a new run.
        } catch (_: RuntimeException) {
            // Background platform restrictions must not trigger a START fallback.
        }
    }

    companion object {
        internal const val ACTION_PAUSE = "com.runmoni.app.widget.PAUSE"
        internal const val ACTION_RESUME = "com.runmoni.app.widget.RESUME"
    }
}

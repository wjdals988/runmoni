package com.runmoni.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.runmoni.app.domain.RunWidgetOperation

/** All cold callbacks read only metrics. No Location, Auth or RunRepository access. */
class RunWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        RunWidgetPublisher.updateWidgets(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        RunWidgetPublisher.updateWidgets(context, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RunWidgetIntents.TAP) {
            val tap = RunWidgetIntents.decode(intent) ?: return
            when (tap.operation) {
                RunWidgetOperation.ASK_FINISH, RunWidgetOperation.CANCEL_FINISH -> RunWidgetPublisher.applyConfirmation(context, tap)
                RunWidgetOperation.START -> Unit
                else -> if (RunWidgetPublisher.canDispatch(context, tap)) {
                    try { context.startService(RunWidgetIntents.service(context, tap)) }
                    catch (_: RuntimeException) { RunWidgetPublisher.requestApp(context, tap.runId) }
                } else RunWidgetPublisher.updateWidgets(context)
            }
            return
        }
        if (intent.action == ACTION_PAUSE || intent.action == ACTION_RESUME) {
            // A launcher can retain the previous version's tokenless views. A
            // recovered session can have the same ID, so refresh without control.
            RunWidgetPublisher.updateWidgets(context)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        internal const val ACTION_PAUSE = "com.runmoni.app.widget.PAUSE"
        internal const val ACTION_RESUME = "com.runmoni.app.widget.RESUME"
    }
}

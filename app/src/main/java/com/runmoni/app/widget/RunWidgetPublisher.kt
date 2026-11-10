package com.runmoni.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import com.runmoni.app.domain.ActiveRun
import com.runmoni.app.domain.RunPersistenceState
import com.runmoni.app.domain.RunWidgetControl
import com.runmoni.app.domain.RunWidgetSession
import com.runmoni.app.domain.RunWidgetSnapshot

/** Called by the existing recorder. Widget callbacks never initialize that recorder. */
object RunWidgetPublisher {
    private val session = RunWidgetSession()
    private var lastPublishedElapsed: Long? = null
    private var lastState: List<Any?>? = null

    @Synchronized fun publish(context: Context, run: ActiveRun, storage: RunPersistenceState, live: Boolean) {
        val elapsed = SystemClock.elapsedRealtime()
        val snapshot = RunWidgetSnapshot.from(run, storage, System.currentTimeMillis())
        session.observe(snapshot, live, elapsed)
        val state = listOf(snapshot.runId, snapshot.phase, snapshot.automaticPause, snapshot.blocked, snapshot.finishing, live)
        val last = lastPublishedElapsed
        if (state == lastState && last != null && elapsed >= last && elapsed - last < 5_000) return
        lastState = state
        lastPublishedElapsed = elapsed
        try {
            RunWidgetCache.from(context).write(snapshot)
            updateWidgets(context)
        } catch (_: RuntimeException) { /* Optional widget cache cannot stop canonical recording. */ }
    }

    @Synchronized fun stopped(context: Context, expectedRunId: String? = null) {
        session.stopped(expectedRunId)
        lastState = null
        updateWidgets(context)
    }

    @Synchronized fun processStarted(context: Context) {
        session.processStarted()
        lastPublishedElapsed = null
        lastState = null
        updateWidgets(context)
    }

    fun requestPin(context: Context): Boolean = try {
        val manager = AppWidgetManager.getInstance(context)
        manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context, RunWidgetProvider::class.java), null, null)
    } catch (_: RuntimeException) { false }

    internal fun allows(context: Context, id: String?, control: RunWidgetControl): Boolean = try {
        session.allows(id, RunWidgetCache.from(context).read(), control)
    } catch (_: RuntimeException) { false }

    internal fun updateWidgets(context: Context, ids: IntArray? = null) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = ids ?: manager.getAppWidgetIds(ComponentName(context, RunWidgetProvider::class.java))
            if (widgetIds.isEmpty()) return
            val snapshot = RunWidgetCache.from(context).read()
            val live = session.isLive(snapshot, SystemClock.elapsedRealtime())
            for (id in widgetIds) {
                val options = manager.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160)
                manager.updateAppWidget(id, RunWidgetViews.forSize(context, snapshot, live, width, height))
            }
        } catch (_: RuntimeException) { /* Launcher failure must not interrupt canonical GPS/Room recording. */ }
    }
}

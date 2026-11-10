package com.runmoni.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.runmoni.app.domain.ActiveRun
import com.runmoni.app.domain.RunPersistenceState
import com.runmoni.app.domain.RunWidgetControl
import com.runmoni.app.domain.RunWidgetSession
import com.runmoni.app.domain.RunWidgetSnapshot

/** Called by the existing recorder. Widget callbacks never initialize that recorder. */
object RunWidgetPublisher {
    private val session = RunWidgetSession()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var renderGeneration = 0L
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
        renderGeneration++
        try {
            RunWidgetCache.from(context).write(snapshot)
            updateWidgets(context)
        } catch (_: RuntimeException) { /* Optional widget cache cannot stop canonical recording. */ }
    }

    @Synchronized fun stopped(context: Context, expectedRunId: String? = null) {
        session.stopped(expectedRunId)
        renderGeneration++
        lastState = null
        updateWidgets(context)
    }

    @Synchronized fun processStarted(context: Context) {
        session.processStarted()
        renderGeneration++
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            val app = context.applicationContext
            val requestedIds = ids?.copyOf()
            // Do not wait for main while publish/stopped owns this object's
            // monitor. Read the current cache/session only when main runs.
            mainHandler.post { updateWidgets(app, requestedIds) }
            return
        }
        try {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = ids ?: manager.getAppWidgetIds(ComponentName(context, RunWidgetProvider::class.java))
            if (widgetIds.isEmpty()) return
            val state = synchronized(this) {
                val snapshot = RunWidgetCache.from(context).read()
                RenderState(renderGeneration, snapshot, session.isLive(snapshot, SystemClock.elapsedRealtime()))
            }
            for (id in widgetIds) {
                val options = manager.getAppWidgetOptions(id)
                // AppWidgetHostView.updateAppWidgetSize has already removed
                // the launcher's default padding from these content bounds.
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                val views = RunWidgetViews.forSize(context, state.snapshot, state.live, width, height)
                val published = synchronized(this) {
                    if (state.generation != renderGeneration ||
                        state.live != session.isLive(state.snapshot, SystemClock.elapsedRealtime())) false
                    else {
                        manager.updateAppWidget(id, views)
                        true
                    }
                }
                if (!published) {
                    // A different run/phase arrived during measurement. Drop
                    // these old controls and request a fresh read, not a replay
                    // of a captured snapshot or a service start fallback.
                    val app = context.applicationContext
                    val requestedIds = widgetIds.copyOf()
                    mainHandler.post { updateWidgets(app, requestedIds) }
                    return
                }
            }
        } catch (_: RuntimeException) { /* Launcher failure must not interrupt canonical GPS/Room recording. */ }
    }

    private data class RenderState(val generation: Long, val snapshot: RunWidgetSnapshot?, val live: Boolean)
}

package com.runmoni.app.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.runmoni.app.data.AppEntryStore
import com.runmoni.app.domain.ActiveRun
import com.runmoni.app.domain.AppEntryChoice
import com.runmoni.app.domain.RunPersistenceIssue
import com.runmoni.app.domain.RunPersistenceState
import com.runmoni.app.domain.RunPhase
import com.runmoni.app.domain.RunWidgetActions
import com.runmoni.app.domain.RunWidgetContent
import com.runmoni.app.domain.RunWidgetControl
import com.runmoni.app.domain.RunWidgetOperation
import com.runmoni.app.domain.RunWidgetSession
import com.runmoni.app.domain.RunWidgetSnapshot
import com.runmoni.app.domain.RunWidgetTap
import java.util.UUID

/** Called by the existing recorder. Widget callbacks never initialize that recorder. */
object RunWidgetPublisher {
    private val session = RunWidgetSession()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var renderGeneration = 0L
    private var lastPublishedElapsed: Long? = null
    private var lastState: List<Any?>? = null
    private var starting = false
    private var appRequiredFor: String? = null
    private var finishRetryable = false

    @Synchronized fun publish(context: Context, run: ActiveRun, storage: RunPersistenceState, live: Boolean) {
        val elapsed = SystemClock.elapsedRealtime()
        val snapshot = RunWidgetSnapshot.from(run, storage, System.currentTimeMillis())
        session.observe(snapshot, live, elapsed)
        finishRetryable = storage.finishing && !storage.saving && storage.issue == RunPersistenceIssue.FINISH
        val state = listOf(snapshot.runId, snapshot.phase, snapshot.automaticPause, snapshot.blocked, snapshot.finishing, live, finishRetryable)
        val last = lastPublishedElapsed
        if (state == lastState && last != null && elapsed >= last && elapsed - last < 5_000) return
        lastState = state
        lastPublishedElapsed = elapsed
        renderGeneration++
        try {
            val cache = RunWidgetCache.from(context)
            cache.write(snapshot)
            if (run.phase == RunPhase.IDLE && !snapshot.blocked && !snapshot.finishing) {
                if (!starting && appRequiredFor != "" && cache.startToken() == null) cache.writeStartToken(UUID.randomUUID().toString())
            } else {
                if (cache.startToken() != null) cache.writeStartToken(null)
                if (cache.savedRunId() != null) cache.saveReceipt(null)
            }
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
        appRequiredFor = null
        starting = false
        finishRetryable = false
        updateWidgets(context)
    }

    /** Called only after the app has verified local Room state. */
    @Synchronized internal fun verified(context: Context, run: ActiveRun, storage: RunPersistenceState) {
        appRequiredFor = null
        lastState = null
        publish(context, run, storage, live = false)
    }

    internal fun locationReady(context: Context): Boolean = runCatching {
        val choice = context.getSharedPreferences("runmoni", Context.MODE_PRIVATE).getString(AppEntryStore.KEY, null)
        AppEntryChoice.fromWire(choice) != AppEntryChoice.CHOOSE &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            context.getSystemService(LocationManager::class.java)?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }.getOrDefault(false)

    @Synchronized internal fun claimStart(context: Context, tap: RunWidgetTap): Boolean = runCatching {
        val cache = RunWidgetCache.from(context)
        val snapshot = cache.read()
        if (starting || appRequiredFor == "" || !locationReady(context) || snapshot?.phase != RunPhase.IDLE ||
            snapshot.blocked || snapshot.finishing || !cache.consumeStart(tap.token)) return@runCatching false
        starting = true
        renderGeneration++
        mainHandler.post { updateWidgets(context) }
        true
    }.getOrDefault(false)

    @Synchronized internal fun startSettled(context: Context, success: Boolean) {
        starting = false
        if (!success) appRequiredFor = ""
        renderGeneration++
        updateWidgets(context)
    }

    @Synchronized internal fun requestApp(context: Context, runId: String) {
        appRequiredFor = runId
        renderGeneration++
        updateWidgets(context)
    }

    @Synchronized internal fun activityVisible(context: Context) {
        if (appRequiredFor != null) runCatching {
            val cache = RunWidgetCache.from(context)
            val snapshot = cache.read()
            if (snapshot?.phase == RunPhase.IDLE && !snapshot.blocked && !snapshot.finishing && cache.startToken() == null && !starting)
                cache.writeStartToken(UUID.randomUUID().toString())
            appRequiredFor = null
            renderGeneration++
        }
        updateWidgets(context)
    }

    @Synchronized internal fun saved(context: Context, runId: String) {
        if (!session.finishResolved(runId)) return
        runCatching { RunWidgetCache.from(context).saveReceipt(runId) }
        renderGeneration++
    }

    @Synchronized internal fun discardReceipt(context: Context, id: String) {
        runCatching {
            val cache = RunWidgetCache.from(context)
            if (cache.savedRunId() == id) cache.saveReceipt(null)
        }
        renderGeneration++
        updateWidgets(context)
    }

    @Synchronized internal fun canDispatch(context: Context, tap: RunWidgetTap): Boolean = runCatching {
        val snapshot = RunWidgetCache.from(context).read()
        if (snapshot?.runId != tap.runId || appRequiredFor == tap.runId) return@runCatching false
        val actions = session.actions(snapshot, SystemClock.elapsedRealtime(), finishRetryable)
        when (tap.operation) {
            RunWidgetOperation.PAUSE -> actions.live == tap.token && RunWidgetContent.from(snapshot, live = true).control == RunWidgetControl.PAUSE
            RunWidgetOperation.RESUME -> actions.live == tap.token && locationReady(context) && RunWidgetContent.from(snapshot, live = true).control == RunWidgetControl.RESUME
            RunWidgetOperation.ASK_FINISH -> actions.live == tap.token
            RunWidgetOperation.CANCEL_FINISH, RunWidgetOperation.FINISH -> actions.confirmation == tap.token
            RunWidgetOperation.RETRY -> actions.retry == tap.token
            RunWidgetOperation.START -> false
        }
    }.getOrDefault(false)

    @Synchronized internal fun accept(context: Context, tap: RunWidgetTap): Boolean = runCatching {
        val accepted = canDispatch(context, tap) &&
            session.accept(tap, RunWidgetCache.from(context).read(), SystemClock.elapsedRealtime(), finishRetryable)
        if (accepted && tap.operation == RunWidgetOperation.RETRY) finishRetryable = false
        accepted
    }.getOrDefault(false)

    internal fun applyConfirmation(context: Context, tap: RunWidgetTap) {
        synchronized(this) { if (accept(context, tap)) renderGeneration++ }
        updateWidgets(context)
    }

    fun requestPin(context: Context): Boolean = try {
        val manager = AppWidgetManager.getInstance(context)
        manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context, RunWidgetProvider::class.java), null, null)
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
                val cache = RunWidgetCache.from(context)
                val snapshot = cache.read()
                val elapsed = SystemClock.elapsedRealtime()
                val idle = snapshot?.phase == RunPhase.IDLE && !snapshot.blocked && !snapshot.finishing
                val ready = locationReady(context)
                val startToken = if (idle && !starting && ready) cache.startToken() else null
                val liveActions = session.actions(snapshot, elapsed, finishRetryable)
                val needsApp = (appRequiredFor != null && appRequiredFor == snapshot?.runId.orEmpty()) ||
                    ((idle || snapshot == null) && !starting && startToken == null) ||
                    (snapshot?.phase == RunPhase.PAUSED && !ready && !liveActions.saving && liveActions.retry == null)
                val actions = if (needsApp) RunWidgetActions(needsApp = true, savedRunId = if (idle) cache.savedRunId() else null)
                else liveActions.copy(
                    start = startToken,
                    starting = starting,
                    savedRunId = if (idle) cache.savedRunId() else null,
                )
                RenderState(renderGeneration, snapshot, session.isLive(snapshot, elapsed), actions)
            }
            for (id in widgetIds) {
                val options = manager.getAppWidgetOptions(id)
                // AppWidgetHostView.updateAppWidgetSize has already removed
                // the launcher's default padding from these content bounds.
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                val views = RunWidgetViews.forSize(context, state.snapshot, state.live, width, height, state.actions)
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

    private data class RenderState(val generation: Long, val snapshot: RunWidgetSnapshot?, val live: Boolean, val actions: RunWidgetActions)
}

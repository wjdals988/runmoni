package com.runmoni.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.RemoteViews
import android.widget.TextView
import com.runmoni.app.R
import com.runmoni.app.domain.RunPhase
import com.runmoni.app.domain.RunWidgetActions
import com.runmoni.app.domain.RunWidgetContent
import com.runmoni.app.domain.RunWidgetControl
import com.runmoni.app.domain.RunWidgetOperation
import com.runmoni.app.domain.RunWidgetSnapshot
import com.runmoni.app.domain.RunWidgetTap
import com.runmoni.app.tracking.RunNotifications
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

internal object RunWidgetViews {
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun identity(id: String, verb: String): Uri = Uri.Builder().scheme("runmoni").authority("widget")
        .appendPath(id).appendPath(verb).build()

    internal fun control(context: Context, id: String, control: RunWidgetControl): PendingIntent {
        val pause = control == RunWidgetControl.PAUSE
        return PendingIntent.getBroadcast(context, 0, Intent(context, RunWidgetProvider::class.java)
            .setAction(if (pause) RunWidgetProvider.ACTION_PAUSE else RunWidgetProvider.ACTION_RESUME)
            .setData(identity(id, if (pause) "pause" else "resume")).putExtra(RunNotifications.RUN_ID, id), FLAGS)
    }

    internal fun open(context: Context, snapshot: RunWidgetSnapshot?): PendingIntent = if (!snapshot?.runId.isNullOrBlank())
        RunNotifications.activity(context, snapshot!!.runId, false)
    else RunWidgetIntents.prepare(context)

    /** Measure every candidate, including the compact fallback, using the
     * launcher's available bounds and the user's actual font configuration.
     * Never shrink text below that font setting to make a widget fit. */
    fun forSize(context: Context, snapshot: RunWidgetSnapshot?, live: Boolean, widthDp: Int, heightDp: Int,
                actions: RunWidgetActions = RunWidgetActions()): RemoteViews {
        // The publisher dispatches to main without blocking its session lock.
        // Unknown bounds/incorrect callers get a labelled icon action, rather
        // than an unmeasured block of potentially clipped Korean text.
        if (widthDp <= 0 || heightDp <= 0 || Looper.myLooper() != Looper.getMainLooper())
            return iconOnly(context, snapshot, live, actions)
        val content = RunWidgetContent.from(snapshot, live)
        val directFinish = widthDp >= 240 && snapshot != null && actions.live != null &&
            fits(context, confirmation(context, snapshot.runId), widthDp, heightDp)
        val candidates = sequence {
            if (actions.confirmation != null && directFinish) {
                yield(confirmation(context, snapshot.runId, actions.confirmation, content))
                yield(confirmation(context, snapshot.runId, actions.confirmation))
            }
            if (widthDp >= 240 && actions.savedRunId != null && actions.start != null)
                yield(receipt(context, actions.savedRunId, actions.start))
            if (widthDp >= 240 && heightDp <= 180 && content.control != null)
                yield(populate(context, snapshot, live, R.layout.run_widget_wide, compact = true, iconControls = true, actions = actions, directFinish = directFinish))
            if (widthDp >= 208) yield(populate(context, snapshot, live, R.layout.run_widget, compact = false, actions = actions, directFinish = directFinish))
            yield(populate(context, snapshot, live, R.layout.run_widget_compact, compact = true, actions = actions))
            if (content.control != null) {
                // Keep the elapsed time and primary action on short hosts.
                yield(populate(context, snapshot, live, R.layout.run_widget_compact, compact = true, actions = actions).apply {
                    setViewVisibility(R.id.run_widget_title, View.GONE)
                })
            }
            val minimal = { populate(context, snapshot, live, R.layout.run_widget_minimal, compact = true, minimal = true, actions = actions) }
            yield(minimal())
            yield(minimal().apply {
                setViewVisibility(R.id.run_widget_footer, View.GONE)
            })
            yield(minimal().apply {
                setViewVisibility(R.id.run_widget_title, View.GONE)
                setViewVisibility(R.id.run_widget_footer, View.GONE)
                // Retain the running cue when enlarged text leaves room only
                // for a short app action, rather than an empty branded panel.
                setViewVisibility(R.id.run_widget_brand, View.GONE)
                if (actions.start == null && actions.retry == null && actions.savedRunId == null)
                    setTextViewText(R.id.run_widget_open, context.getString(R.string.run_widget_open_short))
            })
        }
        return candidates.firstOrNull { fits(context, it, widthDp, heightDp) } ?: iconOnly(context, snapshot, live, actions)
    }

    private fun fits(context: Context, views: RemoteViews, widthDp: Int, heightDp: Int): Boolean = try {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        val root = views.apply(context, FrameLayout(context)) as ViewGroup
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        fun fitsChild(view: View): Boolean {
            if (view.visibility != View.VISIBLE) return true
            val rect = Rect().also { view.getDrawingRect(it); root.offsetDescendantRectToMyCoords(view, it) }
            if (rect.left < 0 || rect.top < 0 || rect.right > width || rect.bottom > height) return false
            val minimumTarget = (48 * density).roundToInt()
            if ((view is Button || view is ImageButton) && (view.width < minimumTarget || view.height < minimumTarget)) return false
            if (view is TextView && view.text.isNotEmpty()) {
                val layout = view.layout ?: return false
                val textWidth = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
                if (layout.lineCount == 0 || layout.getLineEnd(layout.lineCount - 1) != view.text.length ||
                    layout.getLineBottom(layout.lineCount - 1) > view.height - view.compoundPaddingTop - view.compoundPaddingBottom ||
                    (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 ||
                        layout.getLineWidth(it) > textWidth + 0.5f }) return false
            }
            return view !is ViewGroup || (0 until view.childCount).all { fitsChild(view.getChildAt(it)) }
        }
        fun childrenFit(): Boolean = (0 until root.childCount).all { fitsChild(root.getChildAt(it)) }
        // Keep the natural-height check: an exact host height alone could
        // conceal content that was compressed or placed above its bounds.
        val naturalFits = root.measuredHeight <= height && childrenFit()
        // CENTER_VERTICAL only takes effect when the launcher supplies its
        // final height. Recheck every line and complete 48dp target after
        // that layout, retaining both results even if either one fails.
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val exactFits = root.measuredWidth == width && root.measuredHeight == height && childrenFit()
        naturalFits && exactFits
    } catch (_: RuntimeException) { false }

    fun create(context: Context, snapshot: RunWidgetSnapshot?, live: Boolean, minimal: Boolean): RemoteViews =
        populate(context, snapshot, live, if (minimal) R.layout.run_widget_minimal else R.layout.run_widget,
            compact = minimal, minimal = minimal)

    private fun isIdle(snapshot: RunWidgetSnapshot?): Boolean =
        snapshot == null || snapshot.runId.isBlank() || snapshot.phase == RunPhase.IDLE

    private fun description(content: RunWidgetContent, footer: String, idle: Boolean): String =
        if (idle) "런모니, ${content.title}"
        else listOf("런모니", content.title, "러닝 시간 ${content.duration}", content.distance, content.speed, footer)
            .filter { it.isNotEmpty() }.joinToString(", ")

    private fun footer(snapshot: RunWidgetSnapshot?, compact: Boolean): String = snapshot?.updatedAtMillis?.takeIf { it > 0 }?.let {
        SimpleDateFormat(if (compact) "HH:mm" else "HH:mm:ss", Locale.KOREA).format(Date(it)) + " 기준"
    }.orEmpty()

    private fun iconOnly(context: Context, snapshot: RunWidgetSnapshot?, live: Boolean, actions: RunWidgetActions): RemoteViews =
        RemoteViews(context.packageName, R.layout.run_widget_action_only).apply {
            val content = RunWidgetContent.from(snapshot, live)
            val action = actions.savedRunId?.let { RunWidgetIntents.result(context, it) } ?: open(context, snapshot)
            val idle = isIdle(snapshot)
            val label = context.getString(when {
                actions.savedRunId != null -> R.string.run_widget_result
                actions.needsApp && idle -> R.string.run_widget_prepare
                else -> R.string.run_widget_open
            })
            val status = actions.status(context) ?: description(content, footer(snapshot, compact = true), idle)
            setContentDescription(R.id.run_widget_open, context.getString(R.string.run_widget_action_summary, label, status))
            setOnClickPendingIntent(R.id.run_widget_open, action)
        }

    private fun confirmation(context: Context, id: String, token: String? = null, metrics: RunWidgetContent? = null) = actionPair(context,
        R.string.run_widget_confirm_title,
        R.string.run_widget_cancel, token?.let { RunWidgetIntents.pending(context, RunWidgetTap(RunWidgetOperation.CANCEL_FINISH, id, it)) },
        R.string.run_widget_confirm, token?.let { RunWidgetIntents.pending(context, RunWidgetTap(RunWidgetOperation.FINISH, id, it)) },
        token?.let { RunNotifications.activity(context, id, false) }, metrics)

    private fun receipt(context: Context, id: String, startToken: String) = actionPair(context,
        R.string.run_widget_saved,
        R.string.run_widget_result, RunWidgetIntents.result(context, id),
        R.string.run_widget_start, RunWidgetIntents.pending(context, RunWidgetTap(RunWidgetOperation.START, "", startToken)),
        RunWidgetIntents.result(context, id))

    private fun actionPair(context: Context, title: Int, firstLabel: Int, first: PendingIntent?,
                           secondLabel: Int, second: PendingIntent?, open: PendingIntent?, metrics: RunWidgetContent? = null) =
        RemoteViews(context.packageName, R.layout.run_widget_pair).apply {
            setTextViewText(R.id.run_widget_title, context.getString(title))
            setTextViewText(R.id.run_widget_control, context.getString(firstLabel))
            setTextViewText(R.id.run_widget_finish, context.getString(secondLabel))
            if (metrics != null) {
                setViewVisibility(R.id.run_widget_confirm_metrics, View.VISIBLE)
                setTextViewText(R.id.run_widget_confirm_metrics, "${metrics.duration} · ${metrics.distance}")
                setContentDescription(R.id.run_widget_confirm_metrics, context.getString(
                    R.string.run_widget_metrics_summary, metrics.duration, metrics.distance))
            }
            if (open != null) setOnClickPendingIntent(R.id.run_widget_root, open)
            if (first != null) setOnClickPendingIntent(R.id.run_widget_control, first)
            if (second != null) setOnClickPendingIntent(R.id.run_widget_finish, second)
        }

    private fun RunWidgetActions.status(context: Context): String? = when {
        starting -> context.getString(R.string.run_widget_starting)
        saving -> context.getString(R.string.run_widget_saving)
        retry != null -> context.getString(R.string.run_widget_save_failed)
        savedRunId != null -> context.getString(R.string.run_widget_saved)
        else -> null
    }

    private fun populate(context: Context, snapshot: RunWidgetSnapshot?, live: Boolean, layout: Int,
                         compact: Boolean, minimal: Boolean = false, iconControls: Boolean = false,
                         actions: RunWidgetActions = RunWidgetActions(), directFinish: Boolean = false): RemoteViews {
        val content = RunWidgetContent.from(snapshot, live)
        val idle = isIdle(snapshot)
        val actionStatus = actions.status(context)
        val idleReady = idle && snapshot?.blocked != true && snapshot?.finishing != true && actionStatus == null
        val durationOnly = layout == R.layout.run_widget_compact
        val views = RemoteViews(context.packageName, layout)
        val open = open(context, snapshot)
        views.setViewVisibility(R.id.run_widget_identity, if (idle || minimal) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.run_widget_title, if (idleReady) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.run_widget_title, actionStatus ?: if (!compact) content.title else when {
            snapshot?.phase == RunPhase.RECOVERY -> "중단된 러닝"
            content.stale -> "마지막 러닝 정보"
            snapshot?.blocked == true || snapshot?.finishing == true -> "저장 확인"
            content.control == RunWidgetControl.PAUSE -> "러닝 중"
            content.control == RunWidgetControl.RESUME -> if (snapshot?.automaticPause == true) "자동 일시정지" else "일시정지 중"
            else -> content.title
        })
        val footer = footer(snapshot, compact)
        views.setTextViewText(R.id.run_widget_footer, footer)
        views.setViewVisibility(R.id.run_widget_footer,
            if (!idle && footer.isNotEmpty() && (!compact || content.control == null)) View.VISIBLE else View.GONE)
        views.setContentDescription(R.id.run_widget_root, actionStatus ?: description(content, footer, idle))
        views.setOnClickPendingIntent(R.id.run_widget_root, open)
        val (primaryLabel, primaryAction) = when {
            actions.retry != null && snapshot != null -> R.string.run_widget_retry to
                RunWidgetIntents.pending(context, RunWidgetTap(RunWidgetOperation.RETRY, snapshot.runId, actions.retry))
            actions.savedRunId != null -> R.string.run_widget_result to RunWidgetIntents.result(context, actions.savedRunId)
            actions.start != null -> R.string.run_widget_start to
                RunWidgetIntents.pending(context, RunWidgetTap(RunWidgetOperation.START, "", actions.start))
            actions.needsApp && idle -> R.string.run_widget_prepare to open
            else -> R.string.run_widget_open to open
        }
        views.setOnClickPendingIntent(R.id.run_widget_open, primaryAction)
        views.setContentDescription(R.id.run_widget_open, context.getString(primaryLabel))
        views.setTextViewText(R.id.run_widget_open, context.getString(primaryLabel))
        if (!minimal) {
            // Empty placeholders are not run metrics. Keep idle focused on one app action.
            views.setViewVisibility(R.id.run_widget_metrics, if (idle) View.GONE else View.VISIBLE)
            if (!durationOnly) views.setTextViewText(R.id.run_widget_distance, content.distance)
            views.setTextViewText(R.id.run_widget_duration, content.duration)
            views.setContentDescription(R.id.run_widget_duration, "러닝 시간 ${content.duration}")
            if (!compact) {
                views.setTextViewText(R.id.run_widget_speed, content.speed)
                views.setViewVisibility(R.id.run_widget_speed,
                    if (content.control == RunWidgetControl.PAUSE) View.VISIBLE else View.GONE)
            }
            val controls = content.control != null && content.canFinish && !actions.starting && !actions.saving &&
                !actions.needsApp && actions.retry == null
            views.setViewVisibility(R.id.run_widget_actions, if (controls) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.run_widget_open, if (controls) View.GONE else View.VISIBLE)
            if (controls) {
                val pause = content.control == RunWidgetControl.PAUSE
                val controlLabel = context.getString(if (pause) R.string.run_widget_pause else R.string.run_widget_resume)
                if (iconControls) views.setImageViewResource(R.id.run_widget_control,
                    if (pause) R.drawable.run_widget_pause_icon else R.drawable.run_widget_resume_icon)
                else views.setTextViewText(R.id.run_widget_control, controlLabel)
                views.setContentDescription(R.id.run_widget_control, "러닝 $controlLabel")
                val runId = requireNotNull(snapshot).runId
                val operation = if (pause) RunWidgetOperation.PAUSE else RunWidgetOperation.RESUME
                val control = actions.live?.let { RunWidgetIntents.pending(context, RunWidgetTap(operation, runId, it)) }
                    ?: control(context, runId, requireNotNull(content.control))
                views.setOnClickPendingIntent(R.id.run_widget_control, control)
                if (!durationOnly) {
                    val finish = if (directFinish && actions.live != null) RunWidgetIntents.pending(context,
                        RunWidgetTap(RunWidgetOperation.ASK_FINISH, runId, actions.live))
                    else RunNotifications.activity(context, runId, true)
                    if (directFinish) views.setContentDescription(R.id.run_widget_finish, context.getString(R.string.run_widget_finish))
                    views.setOnClickPendingIntent(R.id.run_widget_finish, finish)
                }
            }
        }
        return views
    }
}

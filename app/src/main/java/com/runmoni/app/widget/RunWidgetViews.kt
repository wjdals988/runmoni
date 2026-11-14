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
import android.widget.RemoteViews
import android.widget.TextView
import com.runmoni.app.MainActivity
import com.runmoni.app.R
import com.runmoni.app.domain.RunPhase
import com.runmoni.app.domain.RunWidgetContent
import com.runmoni.app.domain.RunWidgetControl
import com.runmoni.app.domain.RunWidgetSnapshot
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
    else PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).setData(identity("", "open"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), FLAGS)

    /** Measure every candidate, including the compact fallback, using the
     * launcher's available bounds and the user's actual font configuration.
     * Never shrink text below that font setting to make a widget fit. */
    fun forSize(context: Context, snapshot: RunWidgetSnapshot?, live: Boolean, widthDp: Int, heightDp: Int): RemoteViews {
        // The publisher dispatches to main without blocking its session lock.
        // Unknown bounds/incorrect callers get a labelled icon action, rather
        // than an unmeasured block of potentially clipped Korean text.
        if (widthDp <= 0 || heightDp <= 0 || Looper.myLooper() != Looper.getMainLooper())
            return iconOnly(context, snapshot, live)
        val candidates = sequence {
            yield(create(context, snapshot, live, minimal = false))
            yield(populate(context, snapshot, live, R.layout.run_widget_compact, compact = true))
            if (RunWidgetContent.from(snapshot, live).control != null) {
                // Keep current metrics and controls before giving up to an
                // app link. Stale/blocked information must retain its title.
                yield(populate(context, snapshot, live, R.layout.run_widget_compact, compact = true).apply {
                    setViewVisibility(R.id.run_widget_title, View.GONE)
                })
            }
            yield(create(context, snapshot, live, minimal = true))
            yield(create(context, snapshot, live, minimal = true).apply {
                setViewVisibility(R.id.run_widget_footer, View.GONE)
            })
            yield(create(context, snapshot, live, minimal = true).apply {
                setViewVisibility(R.id.run_widget_title, View.GONE)
                setViewVisibility(R.id.run_widget_footer, View.GONE)
                setTextViewText(R.id.run_widget_open, "열기")
            })
        }
        return candidates.firstOrNull { fits(context, it, widthDp, heightDp) } ?: iconOnly(context, snapshot, live)
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
            if (view is Button && (view.width < minimumTarget || view.height < minimumTarget)) return false
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
        if (idle) "런모니, ${content.title}, 앱에서 확인"
        else "런모니, ${content.title}, ${content.distance}, 러닝 시간 ${content.duration}, ${content.speed}, $footer"

    private fun footer(snapshot: RunWidgetSnapshot?, compact: Boolean): String = snapshot?.updatedAtMillis?.takeIf { it > 0 }?.let {
        SimpleDateFormat(if (compact) "HH:mm" else "HH:mm:ss", Locale.KOREA).format(Date(it)) + " 기준"
    } ?: if (compact) "러닝 시작 준비" else "앱에서 러닝을 시작하세요"

    private fun iconOnly(context: Context, snapshot: RunWidgetSnapshot?, live: Boolean): RemoteViews =
        RemoteViews(context.packageName, R.layout.run_widget_action_only).apply {
            val content = RunWidgetContent.from(snapshot, live)
            val action = open(context, snapshot)
            setContentDescription(R.id.run_widget_root, description(content, footer(snapshot, compact = true), isIdle(snapshot)))
            setContentDescription(R.id.run_widget_open, "앱에서 러닝 확인")
            setOnClickPendingIntent(R.id.run_widget_root, action)
            setOnClickPendingIntent(R.id.run_widget_open, action)
        }

    private fun populate(context: Context, snapshot: RunWidgetSnapshot?, live: Boolean, layout: Int,
                         compact: Boolean, minimal: Boolean = false): RemoteViews {
        val content = RunWidgetContent.from(snapshot, live)
        val idle = isIdle(snapshot)
        val views = RemoteViews(context.packageName, layout)
        val open = open(context, snapshot)
        views.setTextViewText(R.id.run_widget_title, if (!compact) content.title else when {
            snapshot?.phase == RunPhase.RECOVERY -> "중단된 러닝"
            content.stale -> "마지막 러닝 정보"
            snapshot?.blocked == true || snapshot?.finishing == true -> "저장 확인"
            content.control == RunWidgetControl.PAUSE -> "러닝 중"
            content.control == RunWidgetControl.RESUME -> if (snapshot?.automaticPause == true) "자동 멈춤" else "일시정지"
            else -> content.title
        })
        val footer = footer(snapshot, compact)
        views.setTextViewText(R.id.run_widget_footer, footer)
        if (idle) views.setViewVisibility(R.id.run_widget_footer, View.GONE)
        views.setContentDescription(R.id.run_widget_root, description(content, footer, idle))
        views.setOnClickPendingIntent(R.id.run_widget_root, open)
        views.setOnClickPendingIntent(R.id.run_widget_open, open)
        views.setTextViewText(R.id.run_widget_open, when {
            minimal -> "앱 열기"
            idle -> "런모니 열기"
            else -> "앱에서 확인"
        })
        if (!minimal) {
            // Empty placeholders are not run metrics. Keep idle focused on one app action.
            views.setViewVisibility(R.id.run_widget_metrics, if (idle) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.run_widget_distance, content.distance)
            views.setTextViewText(R.id.run_widget_duration, content.duration)
            views.setContentDescription(R.id.run_widget_duration, "러닝 시간 ${content.duration}")
            views.setTextViewText(R.id.run_widget_speed, content.speed)
            // Speed is useful during a live run. Paused/past data has no current speed.
            views.setViewVisibility(R.id.run_widget_speed,
                if (!compact && content.control == RunWidgetControl.PAUSE) View.VISIBLE else View.GONE)
            val controls = content.control != null && content.canFinish
            views.setViewVisibility(R.id.run_widget_actions, if (controls) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.run_widget_open, if (controls) View.GONE else View.VISIBLE)
            if (controls) {
                val pause = content.control == RunWidgetControl.PAUSE
                views.setTextViewText(R.id.run_widget_control, if (pause) "멈춤" else "재개")
                views.setContentDescription(R.id.run_widget_control, if (pause) "러닝 일시정지" else "러닝 재개")
                views.setOnClickPendingIntent(R.id.run_widget_control, control(context, snapshot!!.runId, content.control!!))
                views.setOnClickPendingIntent(R.id.run_widget_finish, RunNotifications.activity(context, snapshot.runId, true))
            }
        }
        return views
    }
}

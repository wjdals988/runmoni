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
import com.runmoni.app.domain.RunWidgetContent
import com.runmoni.app.domain.RunWidgetControl
import com.runmoni.app.domain.RunWidgetSnapshot
import com.runmoni.app.tracking.RunNotifications
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    /** The launcher bounds and scaled Korean font metrics decide which layout
     * fits. A fixed scale threshold alone misses narrow150% configurations. */
    fun forSize(context: Context, snapshot: RunWidgetSnapshot?, live: Boolean, widthDp: Int, heightDp: Int): RemoteViews {
        if (context.resources.configuration.fontScale >= 1.6f || widthDp <= 0 || heightDp <= 0 ||
            Looper.myLooper() != Looper.getMainLooper()) return create(context, snapshot, live, minimal = true)
        val full = create(context, snapshot, live, minimal = false)
        return if (fits(context, full, widthDp, heightDp)) full else create(context, snapshot, live, minimal = true)
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
            if (view is Button && (view.width < 48 * density || view.height < 48 * density)) return false
            if (view is TextView && view.text.isNotEmpty()) {
                val layout = view.layout ?: return false
                if (layout.lineCount == 0 || layout.getLineEnd(layout.lineCount - 1) != view.text.length ||
                    layout.getLineBottom(layout.lineCount - 1) > view.height - view.compoundPaddingTop - view.compoundPaddingBottom ||
                    (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }) return false
            }
            return view !is ViewGroup || (0 until view.childCount).all { fitsChild(view.getChildAt(it)) }
        }
        root.measuredHeight <= height && (0 until root.childCount).all { fitsChild(root.getChildAt(it)) }
    } catch (_: RuntimeException) { false }

    fun create(context: Context, snapshot: RunWidgetSnapshot?, live: Boolean, minimal: Boolean): RemoteViews {
        val content = RunWidgetContent.from(snapshot, live)
        val views = RemoteViews(context.packageName, if (minimal) R.layout.run_widget_minimal else R.layout.run_widget)
        val open = open(context, snapshot)
        views.setTextViewText(R.id.run_widget_title, if (!minimal) content.title else when {
            content.stale -> "지난 정보"
            snapshot?.blocked == true || snapshot?.finishing == true -> "저장 확인"
            content.control == RunWidgetControl.PAUSE -> "러닝 중"
            content.control == RunWidgetControl.RESUME -> "일시정지"
            else -> "런모니"
        })
        views.setTextViewText(R.id.run_widget_footer, snapshot?.updatedAtMillis?.takeIf { it > 0 }?.let {
            SimpleDateFormat(if (minimal) "HH:mm" else "HH:mm:ss", Locale.KOREA).format(Date(it)) + " 기준"
        } ?: if (minimal) "러닝 시작 준비" else "앱에서 러닝을 시작하세요")
        views.setContentDescription(R.id.run_widget_root, "런모니, ${content.title}, ${content.distance}, 러닝 시간 ${content.duration}, ${content.speed}")
        views.setOnClickPendingIntent(R.id.run_widget_root, open)
        views.setOnClickPendingIntent(R.id.run_widget_open, open)
        views.setTextViewText(R.id.run_widget_open, if (minimal) "앱 열기" else "앱에서 확인")
        if (!minimal) {
            views.setTextViewText(R.id.run_widget_distance, content.distance)
            views.setTextViewText(R.id.run_widget_duration, content.duration)
            views.setContentDescription(R.id.run_widget_duration, "러닝 시간 ${content.duration}")
            views.setTextViewText(R.id.run_widget_speed, content.speed)
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

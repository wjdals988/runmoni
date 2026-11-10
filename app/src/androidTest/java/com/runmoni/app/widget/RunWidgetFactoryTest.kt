package com.runmoni.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.R
import com.runmoni.app.domain.*
import com.runmoni.app.tracking.RunNotifications
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Inflates factory output only. Never pins a widget, sends an action, starts
 * a Service, or changes the application's real widget cache/run/profile data. */
@RunWith(AndroidJUnit4::class)
class RunWidgetFactoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val created = mutableSetOf<PendingIntent>()
    private val preferencesName = "widget-test-${UUID.randomUUID()}"
    private val storage = RunPersistenceState(initialized = true)
    private val run = ActiveRun(id = "widget-factory-${UUID.randomUUID()}", phase = RunPhase.RUNNING,
        distanceMeters = 1234.0, activeDurationMillis = 75_000, currentSpeedMetersPerSecond = 2.5,
        gpsStatus = "PRIVATE_COORDINATE 37.1234567,127.7654321",
        points = listOf(TrackPoint(0, 37.1234567, 127.7654321, 5f, 1000)))

    private fun snapshot(active: ActiveRun = run) = RunWidgetSnapshot.from(active, storage, 1000)

    private fun inflate(state: RunWidgetSnapshot?, live: Boolean, minimal: Boolean = false, fontScale: Float = 1f): View {
        val display = context.createConfigurationContext(Configuration(context.resources.configuration).apply { this.fontScale = fontScale })
        lateinit var view: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = RunWidgetViews.create(display, state, live, minimal).apply(display, null)
            val density = display.resources.displayMetrics.density
            view.measure(View.MeasureSpec.makeMeasureSpec(((if (minimal) 128 else 180) * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(((if (minimal) 140 else 200) * density).toInt(), View.MeasureSpec.AT_MOST))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        created += RunWidgetViews.open(context, state)
        state?.let {
            created += RunWidgetViews.control(context, it.runId, RunWidgetControl.PAUSE)
            created += RunWidgetViews.control(context, it.runId, RunWidgetControl.RESUME)
            created += RunNotifications.activity(context, it.runId, true)
        }
        return view
    }

    @After fun removeOnlyThisTestsCacheAndPendingIntents() {
        context.deleteSharedPreferences(preferencesName)
        created.forEach { it.cancel() }
    }

    @Test fun metricsCacheRoundTripsOnlyTheExplicitSafeFieldsAndRejectsCorruption() {
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val cache = RunWidgetCache(preferences)
        val state = snapshot()
        cache.write(state)
        assertEquals(state, cache.read())
        val encoded = preferences.getString(RunWidgetCache.KEY, "")!!
        val json = JSONObject(encoded)
        assertEquals(setOf("version", "runId", "phase", "distanceMeters", "activeDurationMillis", "speedMetersPerSecond",
            "automaticPause", "blocked", "finishing", "updatedAtMillis"), json.keys().asSequence().toSet())
        for (privateValue in listOf("37.1234567", "127.7654321", "PRIVATE_COORDINATE", "latitude", "longitude", "points", "profile"))
            assertFalse(encoded.contains(privateValue))
        preferences.edit().putString(RunWidgetCache.KEY, "broken").commit()
        assertNull(cache.read())
        preferences.edit().putString(RunWidgetCache.KEY, json.put("version", 2).toString()).commit()
        assertNull(cache.read())
    }

    @Test fun runningAndPausedViewsExposeCurrentMetricsAndSeparateControls() {
        val running = inflate(snapshot(), live = true)
        assertEquals("1.23 km", running.findViewById<TextView>(R.id.run_widget_distance).text.toString())
        assertEquals("01:15", running.findViewById<TextView>(R.id.run_widget_duration).text.toString())
        assertEquals("현재 9.0 km/h", running.findViewById<TextView>(R.id.run_widget_speed).text.toString())
        assertEquals(View.VISIBLE, running.findViewById<View>(R.id.run_widget_actions).visibility)
        assertEquals(View.GONE, running.findViewById<View>(R.id.run_widget_open).visibility)
        assertEquals("멈춤", running.findViewById<Button>(R.id.run_widget_control).text.toString())
        val paused = inflate(snapshot(run.copy(phase = RunPhase.PAUSED)), live = true)
        assertEquals("재개", paused.findViewById<Button>(R.id.run_widget_control).text.toString())
        assertEquals("현재 속도 —", paused.findViewById<TextView>(R.id.run_widget_speed).text.toString())
        for (id in listOf(R.id.run_widget_control, R.id.run_widget_finish))
            assertTrue(running.findViewById<View>(id).measuredHeight >= 48 * context.resources.displayMetrics.density)
    }

    @Test fun coldOrRecoveryViewsHaveNoServiceControlsAndNeverUseAChronometer() {
        for (state in listOf(snapshot(), snapshot(run.copy(phase = RunPhase.RECOVERY)), snapshot(run.copy(phase = RunPhase.IDLE)))) {
            val view = inflate(state, live = false)
            assertEquals(View.GONE, view.findViewById<View>(R.id.run_widget_actions).visibility)
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.run_widget_open).visibility)
            assertFalse(view.findViewById<View>(R.id.run_widget_duration) is android.widget.Chronometer)
            assertTrue(view.findViewById<TextView>(R.id.run_widget_speed).text.toString().contains("—"))
        }
    }

    @Test fun largeTextFallbackRetainsAnAccessibleAppActionWithoutTwoClippedButtons() {
        val view = inflate(snapshot(), live = true, minimal = true, fontScale = 2f)
        assertNull(view.findViewById<View>(R.id.run_widget_control))
        val button = view.findViewById<Button>(R.id.run_widget_open)
        assertEquals("앱 열기", button.text.toString())
        assertTrue(button.isClickable)
        assertTrue(button.measuredHeight >= 48 * context.resources.displayMetrics.density)
        assertTrue(button.measuredWidth >= 48 * context.resources.displayMetrics.density)
        assertTrue(button.left >= view.paddingLeft)
        assertTrue(button.right <= view.width - view.paddingRight)
        assertTrue(button.bottom <= view.height - view.paddingBottom)
        assertTrue(view.findViewById<View>(R.id.run_widget_footer).bottom <= view.height - view.paddingBottom)
        assertTrue(view.contentDescription.toString().contains("1.23 km"))
        assertTrue(view.contentDescription.toString().contains("01:15"))
    }

    @Test fun immutableVerbAndRunIdentitiesSeparateOldControlsAndConfirmedFinish() {
        val first = RunWidgetViews.control(context, run.id, RunWidgetControl.PAUSE).also { created += it }
        val repeat = RunWidgetViews.control(context, run.id, RunWidgetControl.PAUSE).also { created += it }
        val resume = RunWidgetViews.control(context, run.id, RunWidgetControl.RESUME).also { created += it }
        val other = RunWidgetViews.control(context, run.id + "-new", RunWidgetControl.PAUSE).also { created += it }
        val open = RunWidgetViews.open(context, snapshot()).also { created += it }
        val finish = RunNotifications.activity(context, run.id, true).also { created += it }
        assertEquals(first, repeat)
        assertNotEquals(first, resume)
        assertNotEquals(first, other)
        assertNotEquals(open, finish)
        if (Build.VERSION.SDK_INT >= 31) {
            for (intent in created) assertTrue(intent.isImmutable)
            assertTrue(first.isBroadcast)
            assertTrue(open.isActivity)
            assertTrue(finish.isActivity)
        }
    }

    @Test fun narrowWidgetMatrixMeasuresWholeTextAndButtonsAtNormalAndEnlargedFontSizes() {
        val state = snapshot()
        created += RunWidgetViews.open(context, state)
        created += RunWidgetViews.control(context, state.runId, RunWidgetControl.PAUSE)
        created += RunNotifications.activity(context, state.runId, true)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (widthDp in listOf(128, 160, 180)) for (scale in listOf(1f, 1.3f, 1.5f)) {
                val display = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = scale })
                val density = display.resources.displayMetrics.density
                val expectedWidth = (widthDp * density).toInt()
                val maximumHeight = (156 * density).toInt()
                val remote = RunWidgetViews.forSize(display, state, live = true, widthDp = widthDp, heightDp = 156)
                val root = remote.apply(display, FrameLayout(display)) as ViewGroup
                root.measure(View.MeasureSpec.makeMeasureSpec(expectedWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                val label = "width=${widthDp}dp fontScale=$scale"
                assertTrue("Widget exceeds launcher bounds: $label", root.measuredHeight <= maximumHeight)
                fun inspect(view: View) {
                    if (view.visibility != View.VISIBLE) return
                    val rect = Rect().also { view.getDrawingRect(it); root.offsetDescendantRectToMyCoords(view, it) }
                    assertTrue("Clipped child at $label: $rect", rect.left >= 0 && rect.top >= 0 && rect.right <= expectedWidth && rect.bottom <= maximumHeight)
                    if (view is Button) {
                        assertTrue("Button height at $label", view.height >= 48 * density)
                        assertTrue("Button width at $label", view.width >= 48 * density)
                    }
                    if (view is TextView && view.text.isNotEmpty()) {
                        val layout = requireNotNull(view.layout)
                        assertEquals("Truncated text at $label", view.text.length, layout.getLineEnd(layout.lineCount - 1))
                        assertTrue("Text height at $label", layout.getLineBottom(layout.lineCount - 1) <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
                    }
                    if (view is ViewGroup) (0 until view.childCount).forEach { inspect(view.getChildAt(it)) }
                }
                (0 until root.childCount).forEach { inspect(root.getChildAt(it)) }
                if (widthDp == 180 && scale == 1f) assertEquals("Normal widgets retain direct controls", R.layout.run_widget, remote.layoutId)
            }
        }
    }
}

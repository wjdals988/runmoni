package com.runmoni.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetHostView
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.graphics.ColorUtils
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
import java.io.File
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
            view = (if (minimal) RunWidgetViews.forSize(display, state, live, 128, 140)
                else RunWidgetViews.create(display, state, live, minimal = false)).apply(display, null)
            val density = display.resources.displayMetrics.density
            view.measure(View.MeasureSpec.makeMeasureSpec(((if (minimal) 128 else 180) * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(((if (minimal) 140 else 200) * density).toInt(), View.MeasureSpec.AT_MOST))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        // The idle open action has a shared identity used by a real pinned widget.
        // Do not cancel that existing application action as test cleanup.
        if (!state?.runId.isNullOrBlank()) created += RunWidgetViews.open(context, state)
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
        assertEquals(View.VISIBLE, running.findViewById<View>(R.id.run_widget_metrics).visibility)
        val distance = running.findViewById<TextView>(R.id.run_widget_distance)
        val duration = running.findViewById<TextView>(R.id.run_widget_duration)
        val speed = running.findViewById<TextView>(R.id.run_widget_speed)
        assertTrue("Distance remains the primary value", distance.textSize > duration.textSize)
        assertTrue("Secondary metrics keep a different weight", duration.textSize > speed.textSize)
        assertEquals("러닝 시간 01:15", duration.contentDescription)
        val surface = context.getColor(R.color.run_widget_surface)
        for (id in listOf(R.id.run_widget_title, R.id.run_widget_distance, R.id.run_widget_duration, R.id.run_widget_speed))
            assertTrue("Readable widget text contrast for $id", ColorUtils.calculateContrast(
                running.findViewById<TextView>(id).currentTextColor, surface) >= 4.5)
        assertTrue("Primary control remains readable", ColorUtils.calculateContrast(
            running.findViewById<Button>(R.id.run_widget_control).currentTextColor,
            context.getColor(R.color.run_widget_primary)) >= 4.5)
        assertTrue("Secondary finish remains readable", ColorUtils.calculateContrast(
            running.findViewById<Button>(R.id.run_widget_finish).currentTextColor,
            context.getColor(R.color.run_widget_secondary)) >= 4.5)
        val paused = inflate(snapshot(run.copy(phase = RunPhase.PAUSED)), live = true)
        assertEquals("재개", paused.findViewById<Button>(R.id.run_widget_control).text.toString())
        assertEquals("현재 속도 —", paused.findViewById<TextView>(R.id.run_widget_speed).text.toString())
        assertEquals("Pause does not show a redundant empty speed row", View.GONE,
            paused.findViewById<View>(R.id.run_widget_speed).visibility)
        for (id in listOf(R.id.run_widget_control, R.id.run_widget_finish))
            assertTrue(running.findViewById<View>(id).measuredHeight >= 48 * context.resources.displayMetrics.density)
        captureFixture("widget-refresh-running-256x220-fixture", snapshot(), true, 256, 220)
        captureFixture("widget-refresh-paused-128x140-fixture", snapshot(run.copy(phase = RunPhase.PAUSED)), true, 128, 140)
    }

    @Test fun coldOrRecoveryViewsHaveNoServiceControlsAndNeverUseAChronometer() {
        for (state in listOf(snapshot(), snapshot(run.copy(phase = RunPhase.RECOVERY)), snapshot(run.copy(phase = RunPhase.IDLE)))) {
            val view = inflate(state, live = false)
            assertEquals(View.GONE, view.findViewById<View>(R.id.run_widget_actions).visibility)
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.run_widget_open).visibility)
            assertFalse(view.findViewById<View>(R.id.run_widget_duration) is android.widget.Chronometer)
            assertTrue(view.findViewById<TextView>(R.id.run_widget_speed).text.toString().contains("—"))
            if (state.phase == RunPhase.IDLE) {
                assertEquals("Idle does not publish empty run statistics", View.GONE,
                    view.findViewById<View>(R.id.run_widget_metrics).visibility)
                assertEquals(View.GONE, view.findViewById<View>(R.id.run_widget_footer).visibility)
                assertEquals("런모니 열기", view.findViewById<Button>(R.id.run_widget_open).text.toString())
                assertFalse(view.contentDescription.toString().contains("—"))
                captureFixture("widget-refresh-idle-256x160-fixture", state, false, 256, 160)
            } else assertEquals(View.VISIBLE, view.findViewById<View>(R.id.run_widget_metrics).visibility)
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
        view.findViewById<View>(R.id.run_widget_footer)?.takeIf { it.visibility == View.VISIBLE }?.let {
            assertTrue(it.bottom <= view.height - view.paddingBottom)
        }
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
        val states = listOf(
            null to false,
            snapshot() to true,
            snapshot(run.copy(phase = RunPhase.PAUSED, pauseReason = PauseReason.AUTOMATIC)) to true,
            snapshot(run.copy(phase = RunPhase.RECOVERY)) to false,
            snapshot().copy(blocked = true, finishing = true) to true,
            snapshot(run.copy(distanceMeters = 123_456_789.0, activeDurationMillis = 123L * 3600_000 + 45 * 60_000 + 56_000)) to true,
        )
        for ((state, _) in states) rememberIntents(state)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // 112x124 also represents a 128x140 cell with the common 8dp
            // launcher margins removed. The padding-specific test below uses
            // this platform's actual value rather than assuming all hosts do.
            val sizes = listOf(96 to 96, 112 to 124, 128 to 140, 160 to 156, 180 to 156, 256 to 220)
            for ((state, live) in states) for ((widthDp, heightDp) in sizes) for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
                val display = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = scale })
                val density = display.resources.displayMetrics.density
                val expectedWidth = (widthDp * density).toInt()
                val maximumHeight = (heightDp * density).toInt()
                val remote = RunWidgetViews.forSize(display, state, live, widthDp, heightDp)
                val root = remote.apply(display, FrameLayout(display)) as ViewGroup
                root.measure(View.MeasureSpec.makeMeasureSpec(expectedWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                val label = "${widthDp}x${heightDp}dp fontScale=$scale phase=${state?.phase} live=$live"
                assertFits(root, expectedWidth, maximumHeight, density, label)
                // Measure the final result exactly as a launcher does too.
                root.measure(View.MeasureSpec.makeMeasureSpec(expectedWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(maximumHeight, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                assertFits(root, expectedWidth, maximumHeight, density, label + " exact host")
                assertTrue(root.contentDescription.toString().contains(RunWidgetContent.from(state, live).title))
                if (widthDp == 256 && heightDp == 220 && scale == 1f && state == snapshot())
                    assertEquals("A roomy widget retains direct controls", View.VISIBLE, root.findViewById<View>(R.id.run_widget_actions).visibility)
                if (widthDp == 128 && heightDp == 140 && scale == 1f && state == snapshot()) {
                    assertEquals("Default widget retains distance", View.VISIBLE, root.findViewById<View>(R.id.run_widget_distance).visibility)
                    assertEquals("Default widget retains duration", View.VISIBLE, root.findViewById<View>(R.id.run_widget_duration).visibility)
                    assertEquals("Default widget retains direct controls", View.VISIBLE, root.findViewById<View>(R.id.run_widget_actions).visibility)
                }
            }
        }
    }

    @Test fun launcherPaddingAndTinyFallbackKeepTheAppActionAccessible() {
        val state = snapshot()
        rememberIntents(state)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (scale in listOf(1f, 1.5f, 2f)) {
                val display = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = scale })
                val density = display.resources.displayMetrics.density
                val padding = AppWidgetHostView.getDefaultPaddingForWidget(display,
                    ComponentName(display, RunWidgetProvider::class.java), null)
                val width = 128 - kotlin.math.ceil((padding.left + padding.right) / density).toInt()
                val height = 140 - kotlin.math.ceil((padding.top + padding.bottom) / density).toInt()
                val remote = RunWidgetViews.forSize(display, state, true, width, height)
                val root = remote.apply(display, FrameLayout(display)) as ViewGroup
                val pixelsWide = (width * density).toInt()
                val pixelsHigh = (height * density).toInt()
                root.measure(View.MeasureSpec.makeMeasureSpec(pixelsWide, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(pixelsHigh, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                assertFits(root, pixelsWide, pixelsHigh, density, "actual launcher padding, font=$scale")
                assertTrue(root.findViewById<View>(R.id.run_widget_open).isClickable)
                assertTrue(root.contentDescription.toString().contains("01:15"))
            }
            for ((width, height) in listOf(64 to 48, 0 to 0)) {
                val remote = RunWidgetViews.forSize(context, state, true, width, height)
                assertEquals(R.layout.run_widget_action_only, remote.layoutId)
                val root = remote.apply(context, FrameLayout(context)) as ViewGroup
                val density = context.resources.displayMetrics.density
                root.measure(View.MeasureSpec.makeMeasureSpec((64 * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((48 * density).toInt(), View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                assertFits(root, root.width, root.height, density, "icon-only app action")
                assertTrue(root.findViewById<ImageButton>(R.id.run_widget_open).isClickable)
                assertEquals("앱에서 러닝 확인", root.findViewById<View>(R.id.run_widget_open).contentDescription)
                assertTrue(root.contentDescription.toString().contains("1.23 km"))
            }
        }
    }

    /** Native RemoteViews draw with synthetic metrics. Not a launcher/MainActivity screenshot. */
    private fun captureFixture(name: String, state: RunWidgetSnapshot, live: Boolean, widthDp: Int, heightDp: Int) {
        val display = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 1f })
        var output: Bitmap? = null
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val density = display.resources.displayMetrics.density
                val width = (widthDp * density).toInt()
                val height = (heightDp * density).toInt()
                val root = RunWidgetViews.forSize(display, state, live, widthDp, heightDp)
                    .apply(display, FrameLayout(display)) as ViewGroup
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
                assertFits(root, width, height, density, "native widget render fixture $name")
                val rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                output = rendered
                root.draw(Canvas(rendered))
            }
            val target = File(context.cacheDir, "widget-review/$name.png")
            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
            target.outputStream().use { assertTrue(requireNotNull(output).compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { output?.recycle() }
    }

    private fun rememberIntents(state: RunWidgetSnapshot?) {
        // The idle open action has a shared identity used by a real pinned widget.
        // Do not cancel that existing application action as test cleanup.
        if (!state?.runId.isNullOrBlank()) created += RunWidgetViews.open(context, state)
        state?.let {
            created += RunWidgetViews.control(context, it.runId, RunWidgetControl.PAUSE)
            created += RunWidgetViews.control(context, it.runId, RunWidgetControl.RESUME)
            created += RunNotifications.activity(context, it.runId, true)
        }
    }

    private fun assertFits(root: ViewGroup, width: Int, height: Int, density: Float, label: String) {
        assertTrue("Widget exceeds launcher bounds: $label", root.measuredHeight <= height)
        fun inspect(view: View) {
            if (view.visibility != View.VISIBLE) return
            val rect = Rect().also { view.getDrawingRect(it); root.offsetDescendantRectToMyCoords(view, it) }
            assertTrue("Clipped child at $label: $rect", rect.left >= 0 && rect.top >= 0 && rect.right <= width && rect.bottom <= height)
            if (view is Button || view is ImageButton) {
                assertTrue("Button height at $label", view.height + 0.5f >= 48 * density)
                assertTrue("Button width at $label", view.width + 0.5f >= 48 * density)
            }
            if (view is TextView && view.text.isNotEmpty()) {
                val layout = requireNotNull(view.layout)
                assertTrue("No text lines at $label", layout.lineCount > 0)
                assertEquals("Truncated text at $label", view.text.length, layout.getLineEnd(layout.lineCount - 1))
                assertTrue("Text height at $label", layout.getLineBottom(layout.lineCount - 1) <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
                val available = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
                val originY = view.baseline - layout.getLineBaseline(0)
                for (line in 0 until layout.lineCount) {
                    assertEquals("Ellipsized text at $label", 0, layout.getEllipsisCount(line))
                    assertTrue("Horizontal text clipping at $label", layout.getLineWidth(line) <= available + 0.5f)
                    val text = view.text.toString().substring(layout.getLineStart(line), layout.getLineEnd(line)).trimEnd()
                    if (text.isEmpty()) continue
                    val ink = Rect().also { view.paint.getTextBounds(text, 0, text.length, it) }
                    val baseline = originY + layout.getLineBaseline(line)
                    assertTrue("Glyph top clipping at $label: $ink", baseline + ink.top >= view.compoundPaddingTop - 1)
                    assertTrue("Glyph bottom clipping at $label: $ink", baseline + ink.bottom <= view.height - view.compoundPaddingBottom + 1)
                }
            }
            if (view is ViewGroup) (0 until view.childCount).forEach { inspect(view.getChildAt(it)) }
        }
        (0 until root.childCount).forEach { inspect(root.getChildAt(it)) }
    }
}

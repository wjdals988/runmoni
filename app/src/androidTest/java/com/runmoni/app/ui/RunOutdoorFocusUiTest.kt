package com.runmoni.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.ActiveRun
import com.runmoni.app.domain.AppState
import com.runmoni.app.domain.LocationAccess
import com.runmoni.app.domain.RunIntervalPlan
import com.runmoni.app.domain.RunPersistenceState
import com.runmoni.app.domain.RunPhase
import org.junit.Assert.*
import org.junit.Test

/** Real Compose geometry and line pixels with injected recorder state, not an outdoor GPS test. */
class RunOutdoorFocusUiTest : RunmoniDesignTestBase() {
    @Test fun shortLargeTextWindowKeepsDistanceGpsAndRunControlsUsable() {
        val state = mutableStateOf(AppState(activeRun = ActiveRun(id = "outdoor-focus", phase = RunPhase.RUNNING,
            distanceMeters = 5_230.0, activeDurationMillis = 1_200_000, currentPaceSecondsPerKm = 233, accuracyMeters = 6f),
            locationAccess = LocationAccess(true, true), runPersistence = RunPersistenceState(initialized = true)))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { ViewportBox(Viewport(320, 2f)) {
            Box(Modifier.height(520.dp)) { RunmoniApp(state.value, actions) }
        } }
        compose.onNodeWithTag("nav_run").performClick()
        compose.onNodeWithTag("open_run_outdoor").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        fullyVisible("run_distance", "running_scroll_viewport").assertTextEquals("5.23")
        fullyVisible("gps_status", "running_scroll_viewport").assertTextEquals("GPS 수신 중")
        fullyVisible("close_run_outdoor", "running_scroll_viewport", minimumTarget = true)
        fullyVisible("pause_run", "run_control_bar", minimumTarget = true)
        fullyVisible("finish_run", "run_control_bar", minimumTarget = true)
        capture("run-outdoor-distance-320-font2-fixture")

        compose.runOnIdle { state.value = state.value.copy(locationAccess = LocationAccess(true, false)) }
        fullyVisible("gps_status", "running_scroll_viewport").assertTextEquals("GPS 꺼짐")
        compose.onNodeWithTag("pause_run").performClick()
        fullyVisible("resume_run", "run_control_bar", minimumTarget = true)
        compose.onNodeWithTag("finish_run").performClick()
        compose.onNodeWithTag("cancel_finish_run").performClick()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("run_outdoor_panel").assertDoesNotExist()
        compose.onNodeWithTag("open_run_outdoor").assertExists()
        compose.runOnIdle {
            assertEquals("outdoor-focus", state.value.activeRun.id)
            assertEquals(1, actions.pauses)
            assertEquals(0, actions.finishes)
            assertEquals(0, actions.starts)
            assertEquals(0, actions.locationSettings)
        }
        compose.runOnIdle {
            state.value = state.value.copy(activeRun = ActiveRun(id = "outdoor-interval-focus", phase = RunPhase.RUNNING,
                activeDurationMillis = 62_000, interval = RunIntervalPlan(60, 60, 6), gpsStatus = "GPS 재수신 중"),
                locationAccess = LocationAccess(true, true))
        }
        compose.onNodeWithTag("open_run_outdoor").performClick()
        fullyVisible("outdoor_interval_stage", "running_scroll_viewport").assertTextEquals("회복 1/6회")
        fullyVisible("outdoor_interval_remaining", "running_scroll_viewport").assertContentDescriptionEquals("남은 시간 0분 58초")
        fullyVisible("outdoor_interval_next", "running_scroll_viewport").assertTextEquals("다음 달리기 01:00")
        fullyVisible("pause_run", "run_control_bar", minimumTarget = true)
        fullyVisible("finish_run", "run_control_bar", minimumTarget = true)
        capture("run-outdoor-interval-320-font2-fixture")
    }

    @Test fun intervalFocusUsesCurrentAndNextPhaseAndMovesOnlyOnTheAdjacentLiveTransition() {
        val run = mutableStateOf(ActiveRun(id = "outdoor-interval", phase = RunPhase.RUNNING,
            activeDurationMillis = 9_000, interval = RunIntervalPlan(10, 10, 2)))
        val motion = mutableStateOf(true)
        compose.setContent { ViewportBox(Viewport(320, 2f)) {
            CompositionLocalProvider(LocalRunmoniMotionEnabled provides motion.value) {
                RunmoniTheme(darkTheme = true) {
                    OutdoorRunPanel(run.value, "기록 중", "GPS 재수신 중", null, onClose = {})
                }
            }
        } }
        val lane = compose.onNodeWithTag("outdoor_interval_lane", useUnmergedTree = true)
        fun activeLineCenter(): Float {
            val bitmap = lane.captureToImage().asAndroidBitmap()
            var first = bitmap.width
            var last = -1
            var samples = 0
            var sum = 0L
            val colors = mutableMapOf<Int, Int>()
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                colors[pixel] = colors.getOrDefault(pixel, 0) + 1
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                // Count the lime stroke even when a thin edge is composited over teal.
                if (green - blue >= 40 && green >= red && red - blue >= 15) {
                    first = minOf(first, x)
                    last = maxOf(last, x)
                    samples++
                    sum += x
                }
            }
            if (samples == 0 || last - first < bitmap.width / 8) {
                capture("run-outdoor-marker-missing-fixture")
                val palette = colors.entries.sortedByDescending { it.value }.take(8)
                    .joinToString { "#${it.key.toUInt().toString(16)}=${it.value}" }
                fail("Lime lane stroke missing in ${bitmap.width}×${bitmap.height}px capture ($samples samples, palette $palette)")
            }
            return sum.toFloat() / samples / bitmap.width
        }
        val stage = compose.onNodeWithTag("outdoor_interval_stage")
        val time = compose.onNodeWithTag("outdoor_interval_remaining")
        val next = compose.onNodeWithTag("outdoor_interval_next")
        stage.assertTextEquals("달리기 1/2회")
        time.assertContentDescriptionEquals("남은 시간 0분 1초")
        next.assertTextEquals("다음 회복 00:10")
        val start = activeLineCenter()
        assertTrue("The run marker begins on the left", start < .25f)

        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { run.value = run.value.copy(activeDurationMillis = 10_000) }
            val intermediate = List(3) {
                compose.mainClock.advanceTimeBy(32)
                activeLineCenter()
            }
            stage.assertTextEquals("회복 1/2회")
            time.assertContentDescriptionEquals("남은 시간 0분 10초")
            next.assertTextEquals("다음 달리기 00:10")
            compose.mainClock.advanceTimeBy(200)
            val settled = activeLineCenter()
            assertTrue("The recovery marker settles on the right", settled > .75f)
            assertTrue("The line has an intermediate rendered frame during its short transition",
                intermediate.any { it > start + .02f && it < settled - .02f })

            compose.runOnIdle { run.value = run.value.copy(activeDurationMillis = 19_000) }
            compose.mainClock.advanceTimeBy(48)
            assertEquals(settled, activeLineCenter(), .01f)
            compose.runOnIdle {
                motion.value = false
                run.value = run.value.copy(activeDurationMillis = 20_000)
            }
            compose.mainClock.advanceTimeBy(32)
            stage.assertTextEquals("달리기 2/2회")
            assertEquals(start, activeLineCenter(), .01f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun fullyVisible(tag: String, scope: String, minimumTarget: Boolean = false): SemanticsNodeInteraction {
        val interaction = compose.onNodeWithTag(tag).assertIsDisplayed()
        if (minimumTarget) interaction.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val element = interaction.fetchSemanticsNode()
        val viewport = compose.onNodeWithTag(scope).fetchSemanticsNode().boundsInRoot
        val origin = element.positionInRoot
        assertTrue("$tag stays fully visible in $scope", origin.x >= viewport.left - 1f && origin.y >= viewport.top - 1f &&
            origin.x + element.size.width <= viewport.right + 1f && origin.y + element.size.height <= viewport.bottom + 1f)
        return interaction
    }
}

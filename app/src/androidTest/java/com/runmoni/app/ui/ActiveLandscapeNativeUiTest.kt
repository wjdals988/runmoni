package com.runmoni.app.ui

import android.content.res.Configuration
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Requires root-configured native landscape short side320dp/font2. No
 * ViewportBox/local density overrides. A Compose callback fixture does not
 * prove a real foreground recorder, service survival or GPS correctness. */
class ActiveLandscapeNativeUiTest : RunmoniDesignTestBase() {
    private fun requireNativeWindow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = context.resources.configuration
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, config.orientation)
        assertTrue("Requires actual system fontScale2", config.fontScale in 1.9f..2.1f)
        assertTrue("Requires an actual low landscape window", config.screenHeightDp <= 330 && config.screenWidthDp >= 480)
        assertTrue(device.displayWidth > device.displayHeight)
        val shortDp = device.displayHeight / context.resources.displayMetrics.density
        assertTrue("Actual physical short side must be320dp: $shortDp", shortDp in 315f..325f)
    }

    /** positionInWindow + measured size include the whole target, even when
     * semantics.boundsInRoot is clipped by the scrolling parent. */
    private fun assertFullyReadable(tag: String, insideScrollViewport: Boolean = true) {
        val target = compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
        val x = target.positionInWindow.x
        val y = target.positionInWindow.y
        val right = x + target.size.width
        val bottom = y + target.size.height
        if (insideScrollViewport) {
            val viewport = compose.onNodeWithTag("running_scroll_viewport").fetchSemanticsNode()
            val vx = viewport.positionInWindow.x
            val vy = viewport.positionInWindow.y
            assertTrue("Entire $tag fits the scrolling viewport horizontally", x >= vx - 1 && right <= vx + viewport.size.width + 1)
            assertTrue("Entire $tag line fits the scrolling viewport vertically: $y..$bottom / $vy..${vy + viewport.size.height}",
                y >= vy - 1 && bottom <= vy + viewport.size.height + 1)
        }
        val decor = compose.activity.window.decorView
        val insets = ViewCompat.getRootWindowInsets(decor)
        assertNotNull("Actual Android OS insets are required", insets)
        val bars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        assertTrue("Entire $tag is inside the OS content horizontally", x >= bars.left - 1 && right <= decor.width - bars.right + 1)
        assertTrue("Entire $tag is inside the OS content vertically", y >= bars.top - 1 && bottom <= decor.height - bars.bottom + 1)
    }

    private fun assertFixedActions() {
        for (tag in listOf("pause_run", "finish_run")) {
            compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            assertFullyReadable(tag, insideScrollViewport = false)
            val action = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val nav = compose.onNodeWithTag("nav_run").getUnclippedBoundsInRoot()
            assertTrue("Full $tag stays above app navigation", action.bottom <= nav.top)
        }
    }

    @Test fun distanceTimePaceAndSpeedEachFitEntirelyAboveFixedActionsAtNativeFont2() {
        requireNativeWindow()
        val state = mutableStateOf(AppState(activeRun = ActiveRun(id = "landscape-fixture", phase = RunPhase.RUNNING,
            distanceMeters = 12345.0, activeDurationMillis = 3_723_000, currentPaceSecondsPerKm = 321,
            currentSpeedMetersPerSecond = 3.2, accuracyMeters = 7f),
            locationAccess = LocationAccess(true, true), runPersistence = RunPersistenceState(initialized = true)))
        compose.setContent { RunmoniApp(state.value, RunmoniUiTest.FakeActions(state)) }
        compose.onNodeWithTag("nav_run").performClick()
        for (tag in listOf("run_distance", "run_duration", "run_pace", "run_speed")) {
            compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
            assertFullyReadable(tag)
            assertFixedActions()
            captureDevice("active-landscape-native-font2-$tag-fixture")
        }
        noTextOverflow("actual low landscape/font2 run metrics and five navigation labels")
    }

    @Test fun allFiveTabsRemainReachableAndNavigationDoesNotPauseOrFinishTheRun() {
        requireNativeWindow()
        val original = ActiveRun(id = "landscape-navigation-fixture", phase = RunPhase.RUNNING,
            distanceMeters = 12345.0, activeDurationMillis = 3_723_000)
        val state = mutableStateOf(AppState(activeRun = original, runPersistence = RunPersistenceState(initialized = true)))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { RunmoniApp(state.value, actions) }
        for (name in listOf("run", "home", "explore", "crew", "me", "run")) {
            for (tab in listOf("home", "explore", "run", "crew", "me")) {
                compose.onNodeWithTag("nav_$tab").assertIsDisplayed().assertIsEnabled()
                    .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                assertFullyReadable("nav_$tab", insideScrollViewport = false)
            }
            compose.onNodeWithTag("nav_$name").performClick().assertIsSelected()
            compose.runOnIdle {
                assertEquals(original, state.value.activeRun)
                assertEquals(0, actions.pauses)
                assertEquals(0, actions.finishes)
            }
            if (name == "home") compose.onNodeWithTag("settings_button").assertIsDisplayed().assertIsEnabled()
        }
        assertFixedActions()
        compose.onNodeWithTag("pause_run").performClick()
        compose.onNodeWithTag("resume_run").assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {
            assertEquals(1, actions.pauses)
            assertEquals(1, actions.resumes)
            assertEquals(0, actions.finishes)
            assertEquals(original, state.value.activeRun)
            assertTrue(state.value.records.isEmpty())
        }
    }
}

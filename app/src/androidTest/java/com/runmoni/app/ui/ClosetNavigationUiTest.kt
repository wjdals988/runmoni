package com.runmoni.app.ui

import android.view.KeyEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Compares unclipped physical window positions, not just partially-visible semantics. */
internal fun assertClosetActionInsideSafeArea(compose: OfflineMapComposeRule, tag: String) {
    val target = compose.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(48.dp).fetchSemanticsNode()
    val safe = compose.onNodeWithTag("closet_safe_content").fetchSemanticsNode()
    val list = compose.onNodeWithTag("closet_item_list").fetchSemanticsNode()
    val top = target.positionInWindow.y
    val bottom = top + target.size.height
    val safeBottom = safe.positionInWindow.y + safe.size.height
    val listTop = list.positionInWindow.y
    assertTrue("Full $tag must be below the fixed closet header: $top / $listTop", top + .5f >= listTop)
    assertTrue("Full $tag must remain inside safe content: $bottom / $safeBottom", bottom <= safeBottom + .5f)
    val decor = compose.activity.window.decorView
    val insets = ViewCompat.getRootWindowInsets(decor)
    assertNotNull("Actual Android window insets are required", insets)
    val bars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
    val navigationTop = decor.height - bars.bottom
    assertTrue("Full $tag must be above actual system navigation: $bottom / $navigationTop", bottom <= navigationTop + .5f)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    File(context.cacheDir, "design-review").apply { mkdirs() }.resolve("closet-navigation-window-bounds.txt")
        .appendText("$tag topPx=$top bottomPx=$bottom safeBottomPx=$safeBottom systemNavigationTopPx=$navigationTop bottomInsetPx=${bars.bottom}\n")
}

class ClosetNavigationUiTest : RunmoniDesignTestBase() {
    @Test fun rootClosetRemovesMainNavigationAndBackRestoresTheSelectedHistoryPeriod() {
        val original = Profile(name = "옷장 검토", mileage = 777, avatar = AvatarStyle(top = 4, costume = 3),
            ownedItems = AvatarCatalogue.defaultOwned + setOf("top:4", "costume:3"))
        val state = mutableStateOf(AppState(profile = original))
        compose.setContent { RunmoniApp(state.value, RunmoniUiTest.FakeActions(state)) }
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("history_period_0").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("open_closet").performScrollTo().performClick()
        for (key in listOf("home", "explore", "run", "crew", "me")) compose.onNodeWithTag("nav_$key").assertDoesNotExist()
        compose.onNodeWithTag("app_wordmark").assertDoesNotExist()
        compose.onNodeWithTag("settings_button").assertDoesNotExist()
        compose.onNodeWithTag("open_avatar_reference").assertDoesNotExist()
        compose.onNodeWithTag("closet_top_4").assertDoesNotExist()
        compose.onNodeWithTag("closet_artwork_status").assertTextContains("보관 중", substring = true)
        compose.runOnIdle { assertEquals(original, state.value.profile) }
        compose.onNodeWithTag("closet_close").performClick()
        compose.onNodeWithTag("nav_me").assertIsSelected()
        compose.onNodeWithTag("history_period_0").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("open_closet").performScrollTo().performClick()
        compose.onNodeWithTag("closet_close").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(compose.activity.isFinishing)
            assertTrue("Closet back callback must be registered before actual key injection", compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // TCG can show a platform ANR late. Acknowledge only this exact system
        // dialog, record it, and require real app focus before native key input.
        val systemError = device.findObject(UiSelector().textMatches("System UI (isn't|isn’t) responding"))
        if (systemError.exists()) {
            val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "design-review").apply { mkdirs() }
            device.takeScreenshot(File(folder, "native-back-system-ui-dialog.png"))
            assertTrue(device.findObject(UiSelector().text("Wait")).click())
            File(folder, "native-back-environment.txt").appendText("Acknowledged exact System UI not-responding dialog with Wait before key injection.\n")
        }
        compose.waitUntil(30_000) { compose.activity.hasWindowFocus() }
        assertTrue("Native Back requires app window focus", compose.activity.hasWindowFocus())
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "design-review").apply { mkdirs() }
            .resolve("native-back-focus.txt").writeText("appWindowFocusedBeforeNativeBack=true\n")
        assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_BACK))
        compose.waitUntil(30_000) {
            assertFalse("Actual Back must keep the Activity alive", compose.activity.isFinishing)
            runCatching { compose.onAllNodesWithTag("nav_me").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.onNodeWithTag("nav_me").assertIsSelected()
        compose.onNodeWithTag("history_period_0").performScrollTo().assertIsSelected()
        compose.runOnIdle { assertEquals(original, state.value.profile) }
    }

    @Test fun lastAvailableItemAndColorCanBeTouchedAboveSystemNavigation() {
        val original = Profile(mileage = 777)
        val state = mutableStateOf(AppState(profile = original))
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun setRunningKitColor(region: RunningKitRegion, color: Int) {
                val profile = state.value.profile
                state.value = state.value.copy(profile = profile.copy(avatar = profile.avatar.copy(
                    runningKitColors = profile.avatar.runningKitColors.withColor(region, color))))
            }
        }
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("open_closet").performScrollTo().performClick()
        compose.onNodeWithTag("closet_animation").performClick()
        compose.onNodeWithTag("closet_slot_shoes").performScrollTo().performClick()
        compose.onNodeWithTag("closet_shoes_3").performScrollTo().assertIsEnabled()
        assertClosetActionInsideSafeArea(compose, "closet_shoes_3")
        compose.onNodeWithTag("closet_shoes_3").performClick().assertIsSelected()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("kit_color_shoes_5") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("kit_color_shoes_5").performScrollTo()
        assertClosetActionInsideSafeArea(compose, "kit_color_shoes_5")
        compose.onNodeWithTag("kit_color_shoes_5").performClick().assertIsSelected()
        compose.onNodeWithTag("closet_content_end").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("closet_avatar_preview").assertIsDisplayed()
        noTextOverflow("root closet final item and color actions")
        capture("closet-navigation-last-actions-fixture")
        captureDevice("closet-navigation-last-actions-device")
        compose.runOnIdle {
            assertEquals(3, state.value.profile.avatar.shoes)
            assertEquals(5, state.value.profile.avatar.runningKitColors.shoes)
            assertEquals(777, state.value.profile.mileage)
            assertEquals(original.ownedItems + "shoes:3", state.value.profile.ownedItems)
        }
    }
}

/** Uses the actual system window at320dp/fontScale2; no logical-only viewport override. */
class ClosetNavigationNativeUiTest : RunmoniDesignTestBase() {
    @Test fun largeSystemTextKeepsTheLastItemAndPaletteAboveSystemNavigation() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assertTrue("Requires actual system fontScale2", config.fontScale >= 1.9f)
        assertTrue("Requires actual320dp Android window", config.screenWidthDp in 315..325)
        val state = mutableStateOf(AppState(profile = Profile(mileage = 777)))
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun setRunningKitColor(region: RunningKitRegion, color: Int) {
                val profile = state.value.profile
                state.value = state.value.copy(profile = profile.copy(avatar = profile.avatar.copy(
                    runningKitColors = profile.avatar.runningKitColors.withColor(region, color))))
            }
        }
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("open_closet").performScrollTo().performClick()
        compose.onNodeWithTag("closet_animation").performClick()
        compose.onNodeWithTag("closet_slot_shoes").performScrollTo().performClick()
        compose.onNodeWithTag("closet_shoes_3").performScrollTo().assertIsEnabled()
        assertClosetActionInsideSafeArea(compose, "closet_shoes_3")
        compose.onNodeWithTag("closet_shoes_3").performClick().assertIsSelected()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("kit_color_shoes_5") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("kit_color_shoes_5").performScrollTo()
        assertClosetActionInsideSafeArea(compose, "kit_color_shoes_5")
        compose.onNodeWithTag("kit_color_shoes_5").performClick().assertIsSelected()
        compose.onNodeWithTag("closet_content_end").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("nav_me").assertDoesNotExist()
        noTextOverflow("native320/font2 root closet safe navigation")
        capture("closet-navigation-native-320-font2-fixture")
        captureDevice("closet-navigation-native-320-font2-device")
        compose.runOnIdle { assertEquals(777, state.value.profile.mileage); assertEquals(3, state.value.profile.avatar.shoes); assertEquals(5, state.value.profile.avatar.runningKitColors.shoes) }
    }
}

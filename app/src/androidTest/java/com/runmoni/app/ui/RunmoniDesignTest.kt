package com.runmoni.app.ui

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.view.WindowManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.runmoni.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Offline visual/accessibility fixtures. Never claim real GPS or Firebase verification. */
abstract class RunmoniDesignTestBase {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    protected data class Viewport(val width: Int = 360, val scale: Float = 1f)

    @Composable protected fun ViewportBox(viewport: Viewport, content: @Composable () -> Unit) {
        val density = LocalDensity.current.density
        val configuration = Configuration(LocalConfiguration.current).apply { screenWidthDp = viewport.width; screenHeightDp = 640; fontScale = viewport.scale }
        CompositionLocalProvider(LocalDensity provides Density(density, viewport.scale), LocalConfiguration provides configuration) {
            Box(Modifier.width(viewport.width.dp).height(640.dp).testTag("audit_viewport")) { content() }
        }
    }

    protected fun noTextOverflow(context: String) {
        val matcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
        val nodes = compose.onAllNodes(matcher, useUnmergedTree = true)
        val count = nodes.fetchSemanticsNodes().size
        assertTrue("No rendered text in $context", count > 0)
        for (index in 0 until count) {
            val layouts = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach {
                // Fractional font metrics can round to a pixel boundary. Reject clipping
                // beyond one physical pixel and all max-line truncation, without hiding it.
                // Paragraph width may include unused layout space. Check actual lines.
                val right = (0 until it.lineCount).maxOfOrNull { line -> it.getLineRight(line) } ?: 0f
                val left = (0 until it.lineCount).minOfOrNull { line -> it.getLineLeft(line) } ?: 0f
                val bottom = (0 until it.lineCount).maxOfOrNull { line -> it.getLineBottom(line) } ?: 0f
                val dx = right - it.size.width
                val dy = bottom - it.size.height
                if (it.hasVisualOverflow) {
                    val target = InstrumentationRegistry.getInstrumentation().targetContext
                    File(target.cacheDir, "design-review").apply { mkdirs() }
                        .resolve("text-metrics.txt").appendText("$context: ${it.layoutInput.text.text}; size=${it.size}; paragraph=${it.multiParagraph.width}x${it.multiParagraph.height}; lines=$left..$right/$bottom; delta=$dx,$dy; maxLines=${it.multiParagraph.didExceedMaxLines}\n")
                }
                assertTrue("Text overflow in $context: ${it.layoutInput.text.text}; lines=$left..$right/$bottom; size=${it.size}; maxLines=${it.multiParagraph.didExceedMaxLines}", left >= -1f && dx <= 1f && dy <= 1f && !it.multiParagraph.didExceedMaxLines)
            }
        }
    }

    protected fun capture(name: String) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "design-review").apply { mkdirs() }
        val roots = compose.onAllNodes(isRoot())
        val forms = compose.onAllNodesWithTag("form_dialog")
        val chats = compose.onAllNodesWithTag("chat_dialog")
        val viewports = compose.onAllNodesWithTag("audit_viewport")
        val root = when {
            forms.fetchSemanticsNodes().isNotEmpty() -> forms[forms.fetchSemanticsNodes().lastIndex]
            chats.fetchSemanticsNodes().isNotEmpty() -> chats[chats.fetchSemanticsNodes().lastIndex]
            viewports.fetchSemanticsNodes().isNotEmpty() -> viewports[0]
            else -> roots[roots.fetchSemanticsNodes().lastIndex]
        }
        File(directory, "$name.png").outputStream().use {
            root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    protected fun captureDevice(name: String) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "design-review").apply { mkdirs() }
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(directory, "$name.png")))
    }
}

class RunmoniDesignTest : RunmoniDesignTestBase() {

    @Test fun runningActionsStayVisibleAcrossNineViewportAndFontCases() {
        val viewport = mutableStateOf(Viewport())
        val state = mutableStateOf(AppState(activeRun = ActiveRun(id = "design-running-fixture", phase = RunPhase.RUNNING,
            distanceMeters = 3210.0, activeDurationMillis = 1_200_000, gpsStatus = "GPS 수신 중")))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { ViewportBox(viewport.value) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_run").performClick()
        compose.onNodeWithTag("run_options").performScrollTo().performClick()
        capture("running-active-fixture-360-100")
        for (width in listOf(320, 360, 411)) for (scale in listOf(1f, 1.5f, 2f)) {
            compose.runOnIdle { viewport.value = Viewport(width, scale) }
            compose.onNodeWithTag("pause_run").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
            compose.onNodeWithTag("finish_run").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
            compose.onNodeWithText("음성 기록 안내").performScrollTo()
            compose.onNodeWithTag("pause_run").assertIsDisplayed()
            compose.onNodeWithTag("finish_run").assertIsDisplayed()
            noTextOverflow("running $width / $scale")
        }
    }

    @Test fun fiveTabsFitNineViewportAndFontCases() {
        val viewport = mutableStateOf(Viewport())
        val state = mutableStateOf(AppState())
        compose.setContent { ViewportBox(viewport.value) { RunmoniApp(state.value, RunmoniUiTest.FakeActions(state)) } }
        val tabs = listOf("home", "explore", "run", "crew", "me")
        for (width in listOf(320, 360, 411)) for (scale in listOf(1f, 1.5f, 2f)) {
            compose.runOnIdle { viewport.value = Viewport(width, scale) }
            for (tab in tabs) {
                compose.onNodeWithTag("nav_$tab").performClick().assertIsSelected()
                if (tab == "home") compose.onNodeWithTag("home_start_run").assertIsDisplayed()
                noTextOverflow("$tab $width / $scale")
                if (width == 360 && scale == 1f) capture("$tab-360-100")
                if (width == 320 && scale == 2f) capture("$tab-320-200")
            }
        }
    }

}

/** Run separately on a real 320dp AVD configuration with system fontScale=2. */
class RunmoniDialogDesignTest : RunmoniDesignTestBase() {
    private fun requireNativeLargeViewport() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assertTrue("Run this pass with system font_scale=2", config.fontScale >= 1.9f)
        assertTrue("Run this pass with actual screen width 320dp", config.screenWidthDp in 315..325)
    }

    @Test fun largeFontSettingsAndClosetKeepSingleCloseAndSelectableSlots() {
        requireNativeLargeViewport()
        val owned = AvatarCatalogue.slots.flatMap { slot -> slot.variants.indices.map { "${slot.key}:$it" } }.toSet()
        val state = mutableStateOf(AppState(profile = Profile(ownedItems = owned)))
        compose.setContent { ViewportBox(Viewport(320, 2f)) { RunmoniApp(state.value, RunmoniUiTest.FakeActions(state)) } }
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("google_sign_in").performScrollTo().assertIsNotEnabled()
        compose.onAllNodesWithText("닫기").assertCountEquals(1)
        compose.onNodeWithText("취소").assertDoesNotExist()
        noTextOverflow("large settings")
        capture("settings-320-200")
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("open_closet").performScrollTo().performClick()
        val previewBounds = compose.onNodeWithTag("closet_avatar_preview").fetchSemanticsNode().boundsInRoot
        AvatarCatalogue.tabs.forEach { (key, _) ->
            compose.onNodeWithTag("closet_slot_$key").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("closet_avatar_preview").assertIsDisplayed()
            assertEquals(previewBounds, compose.onNodeWithTag("closet_avatar_preview").fetchSemanticsNode().boundsInRoot)
        }
        AvatarCatalogue.accessoryKeys.forEach { key ->
            compose.onNodeWithTag("closet_slot_$key").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("closet_avatar_preview").assertIsDisplayed()
        }
        compose.onNodeWithTag("closet_slot_costume").performScrollTo().performClick()
        compose.onNodeWithTag("closet_costume_1").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, state.value.profile.avatar.costume) }
        compose.onNodeWithTag("closet_slot_top").performScrollTo().performClick()
        compose.onNodeWithTag("closet_top_1").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, state.value.profile.avatar.costume) }
        compose.onNodeWithTag("closet_avatar_preview").assertIsDisplayed()
        noTextOverflow("large closet")
        capture("closet-320-200")
    }

    @Test fun arrivingChatDoesNotMoveReadersAwayFromOlderMessages() {
        requireNativeLargeViewport()
        compose.runOnUiThread { compose.activity.enableEdgeToEdge(); compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
        val now = System.currentTimeMillis()
        val room = "crew:design-chat"
        val state = mutableStateOf(AppState(openedRoomId = room, openedRoomTitle = "스크롤 검증 크루",
            crews = listOf(Crew("design-chat", "스크롤 검증 크루", "", joined = true, isOwner = true)),
            messages = (1..30).map { ChatMessage("message-$it", room, "테스트 러너", "이전 메시지 $it · 함께 달릴 일정을 이야기해요", now + it) }))
        val sent = mutableListOf<String>()
        var closes = 0
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun sendMessage(body: String, notice: Boolean) { sent += body }
            override fun closeChat() { closes++ }
        }
        compose.setContent { RunmoniTheme { ChatScreen(state.value, actions) } }
        compose.onNodeWithText("이전 메시지 2 · 함께 달릴 일정을 이야기해요").performScrollTo()
        val before = compose.onNodeWithTag("chat_history").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.runOnIdle { state.value = state.value.copy(messages = state.value.messages + ChatMessage("new-message", room, "테스트 러너", "새 메시지", now + 100)) }
        compose.onNodeWithTag("chat_jump_latest").assertIsDisplayed()
        val after = compose.onNodeWithTag("chat_history").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals("Reader position changed on arrival", before, after, 1f)
        compose.onNodeWithTag("chat_jump_latest").performClick()
        compose.onNodeWithText("새 메시지").assertIsDisplayed()
        compose.onNodeWithTag("chat_jump_latest").assertDoesNotExist()
        compose.onNodeWithTag("chat_input").performClick().performTextInput("러닝 준비됐어요")
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val imePackage = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
        assertTrue("The real soft keyboard did not open", device.wait(Until.hasObject(By.pkg(imePackage)), 5000))
        // A partially exposed control can pass assertIsDisplayed. Wait for the real
        // IME animation and verify the complete input and send bounds above it.
        device.waitForIdle()
        var geometry = ""
        try {
            compose.waitUntil(timeoutMillis = 5000) {
                val keyboardBounds = device.findObject(By.pkg(imePackage))?.visibleBounds
                val keyboardTop = keyboardBounds?.top ?: 0
                val inputBounds = device.findObject(By.res("chat_input"))?.visibleBounds
                val sendBounds = device.findObject(By.res("send_message"))?.visibleBounds
                geometry = "IME=$keyboardBounds; input=$inputBounds; send=$sendBounds"
                val inputHeight = compose.onNodeWithTag("chat_input").fetchSemanticsNode().size.height
                val sendHeight = compose.onNodeWithTag("send_message").fetchSemanticsNode().size.height
                keyboardTop > 0 && inputBounds != null && sendBounds != null &&
                    inputBounds.bottom <= keyboardTop && sendBounds.bottom <= keyboardTop &&
                    inputBounds.height() >= inputHeight - 1 && sendBounds.height() >= sendHeight - 1
            }
        } finally {
            val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "design-review").apply { mkdirs() }
            File(directory, "chat-keyboard-geometry.txt").writeText(geometry)
            capture("chat-input-default")
            captureDevice("chat-ime-system-200")
        }
        compose.onNodeWithTag("send_message").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("send_message").performClick()
        compose.runOnIdle { assertEquals(listOf("러닝 준비됐어요"), sent) }
        assertEquals("", compose.onNodeWithTag("chat_input").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        device.pressBack()
        assertTrue("Back did not close the keyboard", device.wait(Until.gone(By.pkg(imePackage)), 5000))
        compose.onNodeWithTag("chat_filters").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, closes) }
        device.pressBack()
        compose.runOnIdle { assertEquals(1, closes) }
    }

    @Test fun largeLocationConsentRemainsScrollableWithoutEnablingSharing() {
        requireNativeLargeViewport()
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { RunmoniTheme { LocationSharingToggle(state.value, actions) } }
        compose.onNodeWithTag("share_location").performClick()
        val message = compose.onNodeWithTag("dialog_message")
        val range = message.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("Large disclosure must be scrollable", range.maxValue() > 0)
        captureDevice("location-consent-system-200")
        message.performTouchInput { swipeUp() }
        assertTrue(range.value() > 0)
        compose.onNodeWithTag("cancel_share_location").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(actions.locationChanges.isEmpty()); assertEquals(false, state.value.publicLocation) }
    }

    @Test fun nativePartyPickersAndStrictDateErrorRemainUsable() {
        requireNativeLargeViewport()
        val state = mutableStateOf(AppState())
        compose.setContent { RunmoniTheme { NewPartyDialog("design-native", RunmoniUiTest.FakeActions(state)) {} } }
        compose.onNodeWithTag("pick_party_date").performScrollTo().performClick()
        captureDevice("party-date-picker-system-200")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.onNodeWithTag("pick_party_time").performScrollTo().performClick()
        captureDevice("party-time-picker-system-200")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.onNodeWithTag("party_time").performScrollTo().performTextClearance()
        compose.onNodeWithTag("party_time").performTextInput("2026-02-30 12:00")
        compose.onNodeWithTag("party_time_support", useUnmergedTree = true).performScrollTo().assertTextEquals("날짜와 시간을 yyyy-MM-dd HH:mm 형식으로 입력해 주세요")
        compose.onNodeWithTag("confirm_create_party").assertIsNotEnabled()
    }
}

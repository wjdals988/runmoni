@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.runmoni.app.ui

import android.content.res.Configuration
import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.runmoni.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Offline ComponentActivity fixtures. No real links, account operations or DB writes. */
class ModalDismissUiTest : RunmoniDesignTestBase() {
    @Test fun informationChildrenHaveOneCloseOwnerAndReturnToTheirParent() {
        val visible = mutableStateOf(true)
        compose.setContent { RunmoniTheme {
            if (visible.value) AppInformationDialog(AppUpdateUiState(), {}, { visible.value = false }, onExternalLink = {})
        } }
        compose.onNodeWithTag("open_version_history").performScrollTo().performClick()
        compose.onNodeWithTag("close_app_information").assertDoesNotExist()
        compose.onAllNodesWithTag("form_dialog").assertCountEquals(1)
        compose.onNodeWithTag("close_version_history").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("open_licenses").performScrollTo().performClick()
        compose.onNodeWithTag("close_app_information").assertDoesNotExist()
        compose.onNodeWithTag("license_entry_0").performScrollTo().performClick()
        compose.onNodeWithTag("close_licenses").assertDoesNotExist()
        compose.onAllNodesWithTag("form_dialog").assertCountEquals(1)
        compose.onNodeWithTag("close_license_detail").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("close_licenses").assertIsDisplayed().performClick()
        compose.onNodeWithTag("link_issue").performScrollTo()
        // Closing must not require scrolling the long content back to the top.
        compose.onNodeWithTag("close_app_information").assertIsDisplayed().performClick()
        compose.onAllNodesWithTag("form_dialog").assertCountEquals(0)
        compose.runOnIdle { assertFalse(visible.value) }
    }

    @Test fun closingCloudDeletionDoesNotDeleteAndAccountChangeRevokesTheConfirmation() {
        val summary = CloudRunSummary(runId = "modal-summary", startedAtMillis = 1000, finishedAtMillis = 3000,
            activeDurationMillis = 1000, distanceMeters = 200.0, pointCount = 2)
        val cloud = mutableStateOf(CloudAccountState(configured = true, uid = "account-a", accountLabel = "계정 A", summaries = listOf(summary)))
        val state = mutableStateOf(AppState())
        val deletes = mutableListOf<Pair<String, String>>()
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun deleteCloudSummary(runId: String, expectedUid: String) { deletes += runId to expectedUid }
        }
        compose.setContent { RunmoniTheme { CloudSummariesDialog(cloud.value, actions) {} } }
        compose.onNodeWithTag("delete_cloud_modal-summary").performScrollTo().performClick()
        compose.onNodeWithTag("close_cloud_summaries").assertDoesNotExist()
        compose.onNodeWithTag("confirmation_close").assertIsDisplayed().performClick()
        compose.onNodeWithTag("close_cloud_summaries").assertIsDisplayed()
        compose.runOnIdle { assertTrue(deletes.isEmpty()) }
        compose.onNodeWithTag("delete_cloud_modal-summary").performScrollTo().performClick()
        compose.runOnIdle { cloud.value = cloud.value.copy(uid = "account-b", accountLabel = "계정 B") }
        compose.onNodeWithTag("confirm_delete_cloud").assertDoesNotExist()
        compose.runOnIdle { assertTrue(deletes.isEmpty()) }
    }

    @Test fun blankPageBodyDoesNotDismissAndPlatformBackDismissesWithoutSubmitting() {
        val visible = mutableStateOf(true)
        var submitted = 0
        compose.setContent { RunmoniTheme {
            if (visible.value) FormDialog("긴 입력 양식", { visible.value = false }, confirm = {
                TextButton(onClick = { submitted++ }) { Text("저장") }
            }) { repeat(30) { Text("본문 $it") } }
        } }
        compose.onNodeWithTag("modal_window").assertDoesNotExist()
        compose.onNodeWithTag("form_page").assertIsDisplayed()
        // The left body padding is page space, not a modal backdrop.
        compose.onNodeWithTag("modal_scroll_body").performTouchInput { click(Offset(1f, center.y)) }
        compose.onNodeWithTag("form_dialog_close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.runOnIdle { assertTrue(visible.value); assertEquals(0, submitted) }
        noTextOverflow("in-tree form blank body and Back fixture")
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressKeyCode(KeyEvent.KEYCODE_BACK))
        compose.onNodeWithTag("form_dialog").assertDoesNotExist()
        compose.runOnIdle { assertFalse(visible.value); assertEquals(0, submitted) }
    }

    @Test fun confirmationOutsideTapAndPlatformBackDismissWithoutConfirming() {
        val visible = mutableStateOf(true)
        var confirmed = 0
        compose.setContent { RunmoniTheme {
            if (visible.value) DismissibleAlertDialog(onDismissRequest = { visible.value = false },
                title = { Text("변경 사항 적용") }, text = { DialogMessage("확인하면 변경 사항을 적용해요.") },
                confirmButton = { TextButton(onClick = { confirmed++ }) { Text("확인") } })
        } }
        compose.onNodeWithTag("form_page").assertDoesNotExist()
        compose.onNodeWithTag("modal_window").performTouchInput { click(Offset(1f, center.y)) }
        compose.runOnIdle { assertFalse(visible.value); assertEquals(0, confirmed) }
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag("confirmation_close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressKeyCode(KeyEvent.KEYCODE_BACK))
        compose.onNodeWithTag("form_dialog").assertDoesNotExist()
        compose.runOnIdle { assertFalse(visible.value); assertEquals(0, confirmed) }
    }
}

@RequiresApi(29)
private fun focusedModalWindow(): View? = WindowInspector.getGlobalWindowViews()
    .singleOrNull { it.isAttachedToWindow && it.hasWindowFocus() }

@RequiresApi(29)
private fun assertModalActionInsideWindow(
    compose: OfflineMapComposeRule,
    tag: String,
    host: ModalContentHost = ModalContentHost.CONFIRMATION,
    requireIme: Boolean = false,
    fixtureWindowBefore: ModalFixtureWindowBefore? = null,
) {
    try {
        compose.waitUntil(30_000) { focusedModalWindow() != null }
        val action = compose.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp).fetchSemanticsNode()
        compose.onAllNodesWithTag(host.safeTag).assertCountEquals(1)
        val otherHost = ModalContentHost.entries.single { it != host }
        compose.onNodeWithTag(otherHost.safeTag).assertDoesNotExist()
        val safe = compose.onNodeWithTag(host.safeTag).fetchSemanticsNode()
        val page = compose.onNodeWithTag(if (host == ModalContentHost.PAGE) "form_page" else "form_dialog").fetchSemanticsNode()
        val scroll = compose.onNodeWithTag("modal_scroll_body").fetchSemanticsNode()
        // Observe both coordinate APIs in the same UI-thread snapshot. Assertions
        // still use the original semantic positions/sizes and original tolerance.
        compose.runOnIdle {
            val window = requireNotNull(focusedModalWindow())
            val insets = requireNotNull(ViewCompat.getRootWindowInsets(window))
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            val left = action.positionInWindow.x
            val top = action.positionInWindow.y
            val right = left + action.size.width
            val bottom = top + action.size.height
            val activityWindow = compose.activity.window
            val activityDecor = activityWindow.decorView
            val content = window.findViewById<View>(android.R.id.content)
            val visibleFrame = Rect().also { window.getWindowVisibleDisplayFrame(it) }
            val focusedAttributes = window.layoutParams as? WindowManager.LayoutParams
            val layoutMask = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            fun viewGeometry(view: View?): Any = view?.let {
                JSONObject().apply {
                    put("class", it.javaClass.name)
                    put("sizePx", "${it.width}x${it.height}")
                    put("locationInWindow", IntArray(2).also(it::getLocationInWindow).joinToString(","))
                    put("locationOnScreen", IntArray(2).also(it::getLocationOnScreen).joinToString(","))
                    put("scrollPx", "${it.scrollX},${it.scrollY}")
                    put("translationPx", "${it.translationX},${it.translationY}")
                    put("hasWindowFocus", it.hasWindowFocus())
                }
            } ?: JSONObject.NULL
            fun nodeGeometry(node: SemanticsNode): JSONObject = JSONObject().apply {
                put("semanticPositionInWindow", node.positionInWindow.toString())
                put("semanticSize", node.size.toString())
                put("semanticPositionInRoot", node.positionInRoot.toString())
                put("clippedBoundsInRoot", node.boundsInRoot.toString())
                val coordinates = node.layoutInfo.coordinates
                put("layoutCoordinatesAttached", coordinates.isAttached)
                if (coordinates.isAttached) {
                    put("layoutPositionInWindow", coordinates.positionInWindow().toString())
                    put("layoutSize", coordinates.size.toString())
                }
            }
            fun edge(actual: Float, limit: Float, lower: Boolean) = JSONObject().apply {
                val margin = if (lower) actual - limit else limit - actual
                put("actualPx", actual)
                put("limitPx", limit)
                put("inwardMarginPx", margin)
                put("allowedTolerancePx", .5f)
                put("passesOriginalInequality", margin >= -.5f)
            }
            val scrollAncestors = JSONArray()
            var ancestor = action.parent
            while (ancestor != null) {
                val current = ancestor
                if (current.config.contains(SemanticsProperties.VerticalScrollAxisRange)) {
                    val range = current.config[SemanticsProperties.VerticalScrollAxisRange]
                    scrollAncestors.put(nodeGeometry(current).apply {
                        put("value", range.value())
                        put("maxValue", range.maxValue())
                        put("reverseScrolling", range.reverseScrolling)
                    })
                }
                ancestor = current.parent
            }
            val report = JSONObject().apply {
                put("sourceKind", "Native ComponentActivity fixture geometry; no account or content text")
                put("tag", tag)
                put("host", host.name)
                put("uptimeMillis", android.os.SystemClock.uptimeMillis())
                put("fontScale", compose.activity.resources.configuration.fontScale)
                put("screenWidthDp", compose.activity.resources.configuration.screenWidthDp)
                put("screenHeightDp", compose.activity.resources.configuration.screenHeightDp)
                put("density", compose.activity.resources.displayMetrics.density)
                put("focusedViewIsActivityDecor", window === activityDecor)
                put("focusedViewSharesActivityWindowToken", window.windowToken == activityDecor.windowToken)
                put("focusedDecor", viewGeometry(window))
                put("focusedContent", viewGeometry(content))
                put("activityDecor", viewGeometry(activityDecor))
                put("activityContent", viewGeometry(activityDecor.findViewById<View>(android.R.id.content)))
                put("visibleDisplayFrame", visibleFrame.toString())
                put("focusedWindowFlags", focusedAttributes?.flags ?: JSONObject.NULL)
                put("focusedSoftInputMode", focusedAttributes?.softInputMode ?: JSONObject.NULL)
                put("activityWindowFlags", activityWindow.attributes.flags)
                put("activitySoftInputMode", activityWindow.attributes.softInputMode)
                put("systemUiVisibility", window.systemUiVisibility)
                put("activitySystemUiVisibility", activityDecor.systemUiVisibility)
                put("focusedLayoutFlags", window.systemUiVisibility and layoutMask)
                put("activityLayoutFlags", activityDecor.systemUiVisibility and layoutMask)
                put("fixtureWindowBefore", fixtureWindowBefore?.let {
                    JSONObject().put("softInputMode", it.softInputMode).put("layoutFlags", it.layoutFlags)
                        .put("systemUiVisibility", it.systemUiVisibility).put("windowFlags", it.windowFlags)
                } ?: JSONObject.NULL)
                put("systemBars", insets.getInsets(WindowInsetsCompat.Type.systemBars()).toString())
                put("displayCutout", insets.getInsets(WindowInsetsCompat.Type.displayCutout()).toString())
                put("ime", insets.getInsets(WindowInsetsCompat.Type.ime()).toString())
                put("combinedInsetsUsedByOriginalAssertion", bars.toString())
                put("imeVisible", insets.isVisible(WindowInsetsCompat.Type.ime()))
                put("requireIme", requireIme)
                put("action", nodeGeometry(action))
                put("safeContent", nodeGeometry(safe))
                put("page", nodeGeometry(page))
                put("scrollBody", nodeGeometry(scroll))
                put("actionScrollAncestors", scrollAncestors)
                put("originalSafeEdges", JSONObject()
                    .put("left", edge(left, safe.positionInWindow.x, true))
                    .put("top", edge(top, safe.positionInWindow.y, true))
                    .put("right", edge(right, safe.positionInWindow.x + safe.size.width, false))
                    .put("bottom", edge(bottom, safe.positionInWindow.y + safe.size.height, false)))
                put("originalOsEdges", JSONObject()
                    .put("left", edge(left, bars.left.toFloat(), true))
                    .put("top", edge(top, bars.top.toFloat(), true))
                    .put("right", edge(right, (window.width - bars.right).toFloat(), false))
                    .put("bottom", edge(bottom, (window.height - bars.bottom).toFloat(), false)))
            }
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "design-review").apply { mkdirs() }
                .resolve("modal-page-window-bounds.jsonl").appendText(report.toString() + "\n")
            if (requireIme) assertTrue("Actual OS IME must remain visible", insets.isVisible(WindowInsetsCompat.Type.ime()) && bars.bottom > 0)
            assertTrue("Full $tag stays inside ${host.safeTag}", left >= safe.positionInWindow.x - .5f && top >= safe.positionInWindow.y - .5f &&
                right <= safe.positionInWindow.x + safe.size.width + .5f && bottom <= safe.positionInWindow.y + safe.size.height + .5f)
            assertTrue("Full $tag stays outside actual OS/IME bounds", left >= bars.left - .5f && top >= bars.top - .5f &&
                right <= window.width - bars.right + .5f && bottom <= window.height - bars.bottom + .5f)
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "design-review").apply { mkdirs() }
                .resolve("modal-action-window-bounds.txt").appendText("$tag host=$host safeTag=${host.safeTag} boundsPx=$left,$top,$right,$bottom window=${window.width}x${window.height} insetBottom=${bars.bottom} ime=${insets.isVisible(WindowInsetsCompat.Type.ime())}\n")
        }
    } catch (failure: AssertionError) {
        runCatching {
            val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "design-review").apply { mkdirs() }
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .takeScreenshot(File(directory, "modal-page-bounds-private-failure.png"))
        }
        throw failure
    }
}

private enum class ModalContentHost(val safeTag: String) {
    PAGE("form_page_safe_content"),
    CONFIRMATION("modal_safe_content"),
}

private data class ModalFixtureWindowBefore(
    val softInputMode: Int,
    val layoutFlags: Int,
    val systemUiVisibility: Int,
    val windowFlags: Int,
)

@SdkSuppress(minSdkVersion = 29)
class ModalDismissNativeUiTest : RunmoniDesignTestBase() {
    private fun requireNativeLargeText() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assertTrue("Actual320dp window required", config.screenWidthDp in 315..325)
        assertTrue("Actual system fontScale2 required", config.fontScale >= 1.9f)
    }

    /** Match MainActivity's edge-to-edge/manifest resize setup, then restore our fixture's owned flags. */
    private fun withMainWindowPolicy(visible: MutableState<Boolean>, test: (ModalFixtureWindowBefore) -> Unit) {
        val window = compose.activity.window
        val layoutMask = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        val before = ModalFixtureWindowBefore(window.attributes.softInputMode,
            window.decorView.systemUiVisibility and layoutMask,
            window.decorView.systemUiVisibility, window.attributes.flags)
        try {
            compose.runOnUiThread {
                compose.activity.enableEdgeToEdge()
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            test(before)
        } finally {
            try {
                compose.runOnUiThread { visible.value = false }
                compose.waitForIdle()
            } finally {
                compose.runOnUiThread {
                    val decor = window.decorView
                    decor.systemUiVisibility = (decor.systemUiVisibility and layoutMask.inv()) or before.layoutFlags
                    window.setSoftInputMode(before.softInputMode)
                    ViewCompat.requestApplyInsets(decor)
                }
            }
        }
    }

    @Test fun informationCloseStaysInTheWindowAfterScrollingLargeContent() {
        requireNativeLargeText()
        val visible = mutableStateOf(true)
        withMainWindowPolicy(visible) { before ->
            compose.setContent { RunmoniTheme {
                if (visible.value) AppInformationDialog(AppUpdateUiState(), {}, { visible.value = false }, onExternalLink = {})
            } }
            compose.onNodeWithTag("link_issue").performScrollTo().assertIsDisplayed()
            assertModalActionInsideWindow(compose, "close_app_information", host = ModalContentHost.PAGE,
                fixtureWindowBefore = before)
            noTextOverflow("native320/font2 information fixed close")
            captureDevice("modal-about-close-native320-font2-fixture")
            compose.onNodeWithTag("close_app_information").performClick()
            compose.onNodeWithTag("form_dialog").assertDoesNotExist()
        }
    }

    @Test fun imeKeepsCloseAndSubmitReachableWithoutSubmittingOnDismiss() {
        requireNativeLargeText()
        val visible = mutableStateOf(true)
        val value = mutableStateOf("")
        var submitted = 0
        var showKeyboard: (() -> Unit)? = null
        withMainWindowPolicy(visible) { before ->
            compose.setContent { RunmoniTheme {
                if (visible.value) FormDialog("입력 내용 확인", { visible.value = false }, confirm = {
                    TextButton(onClick = { submitted++ }, modifier = Modifier.heightIn(min = 48.dp).testTag("modal_native_submit")) { Text("저장") }
                }) {
                    val keyboard = LocalSoftwareKeyboardController.current
                    SideEffect { showKeyboard = { keyboard?.show() } }
                    OutlinedTextField(value.value, { value.value = it }, label = { Text("검토 입력") },
                        modifier = Modifier.fillMaxWidth().testTag("modal_native_input"))
                    repeat(10) { Text("스크롤 가능한 안내 $it", modifier = Modifier.testTag("modal_text_$it")) }
                }
            } }
            compose.onNodeWithTag("modal_native_input").performClick().performTextInput("저장 전 내용")
            compose.runOnIdle { requireNotNull(showKeyboard).invoke() }
            compose.waitUntil(30_000) {
                focusedModalWindow()?.let { ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) } == true
            }
            compose.onNodeWithTag("modal_text_9").performScrollTo()
            val submit = compose.onNodeWithTag("modal_native_submit")
            // In a short IME window the page moves its actions into the body.
            // A fixed footer has no scroll parent and must already be reachable.
            if (generateSequence(submit.fetchSemanticsNode().parent) { it.parent }
                    .any { it.config.contains(SemanticsActions.ScrollBy) }) submit.performScrollTo()
            assertModalActionInsideWindow(compose, "modal_native_submit", host = ModalContentHost.PAGE, requireIme = true,
                fixtureWindowBefore = before)
            assertModalActionInsideWindow(compose, "form_dialog_close", host = ModalContentHost.PAGE, requireIme = true,
                fixtureWindowBefore = before)
            noTextOverflow("native320/font2 in-tree form with actual IME")
            captureDevice("modal-ime-close-native320-font2-fixture")
            compose.onNodeWithTag("form_dialog_close").performClick()
            compose.onNodeWithTag("form_dialog").assertDoesNotExist()
            compose.runOnIdle { assertEquals(0, submitted); assertEquals("저장 전 내용", value.value) }
        }
    }
}

@SdkSuppress(minSdkVersion = 29)
class ModalDismissLandscapeUiTest : RunmoniDesignTestBase() {
    @Test fun shortLandscapeKeepsTheCloseFixedAndTheConfirmationScrollable() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, config.orientation)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val realShortSideDp = minOf(device.displayWidth, device.displayHeight) * 160f / config.densityDpi
        assertTrue("Actual display short side is320dp", kotlin.math.abs(realShortSideDp - 320f) <= 1f)
        // API29's app configuration excludes system bars; the real display is checked above.
        assertTrue(config.screenHeightDp in 1..330 && config.fontScale >= 1.9f)
        val visible = mutableStateOf(true)
        var confirmed = 0
        compose.setContent { RunmoniTheme {
            if (visible.value) DismissibleAlertDialog(onDismissRequest = { visible.value = false },
                title = { Text("긴 확인 내용도 취소할 수 있어요") }, text = { DialogMessage("확인할 내용\n".repeat(35)) },
                confirmButton = { TextButton(onClick = { confirmed++ }, modifier = Modifier.heightIn(min = 48.dp).testTag("modal_landscape_confirm")) { Text("확인") } })
        } }
        compose.onNodeWithTag("modal_scroll_body").performTouchInput { swipeUp() }
        assertModalActionInsideWindow(compose, "modal_landscape_confirm")
        assertModalActionInsideWindow(compose, "confirmation_close")
        captureDevice("modal-close-landscape-font2-fixture")
        compose.onNodeWithTag("confirmation_close").performClick()
        compose.runOnIdle { assertFalse(visible.value); assertEquals(0, confirmed) }
    }
}

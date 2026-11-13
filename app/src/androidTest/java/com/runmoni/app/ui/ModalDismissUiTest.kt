@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.runmoni.app.ui

import android.content.res.Configuration
import android.view.KeyEvent
import android.view.View
import android.view.inspector.WindowInspector
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.runmoni.app.domain.*
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
) {
    compose.waitUntil(30_000) { focusedModalWindow() != null }
    val action = compose.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        .assertWidthIsAtLeast(48.dp).fetchSemanticsNode()
    compose.onAllNodesWithTag(host.safeTag).assertCountEquals(1)
    val otherHost = ModalContentHost.entries.single { it != host }
    compose.onNodeWithTag(otherHost.safeTag).assertDoesNotExist()
    val safe = compose.onNodeWithTag(host.safeTag).fetchSemanticsNode()
    val window = requireNotNull(focusedModalWindow())
    val insets = requireNotNull(ViewCompat.getRootWindowInsets(window))
    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
    if (requireIme) assertTrue("Actual OS IME must remain visible", insets.isVisible(WindowInsetsCompat.Type.ime()) && bars.bottom > 0)
    val left = action.positionInWindow.x
    val top = action.positionInWindow.y
    val right = left + action.size.width
    val bottom = top + action.size.height
    assertTrue("Full $tag stays inside ${host.safeTag}", left >= safe.positionInWindow.x - .5f && top >= safe.positionInWindow.y - .5f &&
        right <= safe.positionInWindow.x + safe.size.width + .5f && bottom <= safe.positionInWindow.y + safe.size.height + .5f)
    assertTrue("Full $tag stays outside actual OS/IME bounds", left >= bars.left - .5f && top >= bars.top - .5f &&
        right <= window.width - bars.right + .5f && bottom <= window.height - bars.bottom + .5f)
    File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "design-review").apply { mkdirs() }
        .resolve("modal-action-window-bounds.txt").appendText("$tag host=$host safeTag=${host.safeTag} boundsPx=$left,$top,$right,$bottom window=${window.width}x${window.height} insetBottom=${bars.bottom} ime=${insets.isVisible(WindowInsetsCompat.Type.ime())}\n")
}

private enum class ModalContentHost(val safeTag: String) {
    PAGE("form_page_safe_content"),
    CONFIRMATION("modal_safe_content"),
}

@SdkSuppress(minSdkVersion = 29)
class ModalDismissNativeUiTest : RunmoniDesignTestBase() {
    private fun requireNativeLargeText() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assertTrue("Actual320dp window required", config.screenWidthDp in 315..325)
        assertTrue("Actual system fontScale2 required", config.fontScale >= 1.9f)
    }

    @Test fun informationCloseStaysInTheWindowAfterScrollingLargeContent() {
        requireNativeLargeText()
        val visible = mutableStateOf(true)
        compose.setContent { RunmoniTheme {
            if (visible.value) AppInformationDialog(AppUpdateUiState(), {}, { visible.value = false }, onExternalLink = {})
        } }
        compose.onNodeWithTag("link_issue").performScrollTo().assertIsDisplayed()
        assertModalActionInsideWindow(compose, "close_app_information", host = ModalContentHost.PAGE)
        noTextOverflow("native320/font2 information fixed close")
        captureDevice("modal-about-close-native320-font2-fixture")
        compose.onNodeWithTag("close_app_information").performClick()
        compose.onNodeWithTag("form_dialog").assertDoesNotExist()
    }

    @Test fun imeKeepsCloseAndSubmitReachableWithoutSubmittingOnDismiss() {
        requireNativeLargeText()
        val visible = mutableStateOf(true)
        val value = mutableStateOf("")
        var submitted = 0
        var showKeyboard: (() -> Unit)? = null
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
        assertModalActionInsideWindow(compose, "modal_native_submit", host = ModalContentHost.PAGE, requireIme = true)
        assertModalActionInsideWindow(compose, "form_dialog_close", host = ModalContentHost.PAGE, requireIme = true)
        noTextOverflow("native320/font2 in-tree form with actual IME")
        captureDevice("modal-ime-close-native320-font2-fixture")
        compose.onNodeWithTag("form_dialog_close").performClick()
        compose.onNodeWithTag("form_dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, submitted); assertEquals("저장 전 내용", value.value) }
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

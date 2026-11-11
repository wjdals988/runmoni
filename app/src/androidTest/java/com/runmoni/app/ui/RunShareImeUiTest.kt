package com.runmoni.app.ui

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import org.json.JSONObject
import java.io.File
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.graphics.Rect
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.domain.RunShareDraft
import com.runmoni.app.domain.RunShareRatio
import org.junit.Assert.*
import org.junit.Test

/** Actual API29 native font2 and font1 phases with real IME; synthetic preview/fake callbacks, not MainActivity. */
class RunShareImeUiTest : RunmoniDesignTestBase() {
    @Test fun realKeyboardKeepsCloseAndScrollableExportActionsOutsideImeAndSystemBars() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assertTrue("Separate native320/font2 phase required", config.screenWidthDp in 315..325 && config.fontScale >= 1.9f)
        val draft = mutableStateOf(RunShareDraft())
        val editorShown = mutableStateOf(true)
        val preview = Bitmap.createBitmap(16, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.DKGRAY) }
        val oldMode = compose.activity.window.attributes.softInputMode
        val layoutMask = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        val oldTestLayoutFlags = compose.activity.window.decorView.systemUiVisibility and layoutMask
        var editorOriginalLayoutFlags = 0
        var editorOriginalSoftInputMode = 0
        var saves = 0
        var shares = 0
        try {
            compose.runOnUiThread {
                compose.activity.enableEdgeToEdge()
                compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                editorOriginalLayoutFlags = compose.activity.window.decorView.systemUiVisibility and layoutMask
                editorOriginalSoftInputMode = compose.activity.window.attributes.softInputMode
            }
            compose.setContent { RunmoniTheme {
                if (editorShown.value) RunShareEditorUi(draft.value, 1, preview, false, true, false, true, null, null,
                    onDraft = { draft.value = it }, onTool = {}, onPickPhoto = {}, onRemovePhoto = {},
                    onTransform = { _, _, _ -> }, onSave = { saves++ }, onShare = { shares++ },
                    onBack = { editorShown.value = false })
            } }
            compose.onNodeWithTag("run_share_caption").performScrollTo().performClick().performTextInput("키보드 접근성 검토")
            compose.waitUntil(30_000) {
                ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            fun withinWindow(tag: String) {
                try {
                val node = compose.onNodeWithTag(tag).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                    .fetchSemanticsNode()
                compose.runOnIdle {
                    val decor = compose.activity.window.decorView
                    val insets = requireNotNull(ViewCompat.getRootWindowInsets(decor))
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val coordinates = node.layoutInfo.coordinates
                    val pos = coordinates.positionInWindow()
                    val size = coordinates.size
                    assertTrue(tag, pos.y >= bars.top - 1f)
                    assertTrue(tag, pos.y + size.height <= decor.height - maxOf(bars.bottom, ime.bottom) + 1f)
                    assertTrue(tag, pos.x >= bars.left - 1f && pos.x + size.width <= decor.width - bars.right + 1f)
                }
                } catch (failure: AssertionError) {
                    // Keep the first failed expectation. Diagnostics run before test teardown,
                    // only on failure, without adding an artificial successful settling delay.
                    runCatching {
                val target = compose.onNodeWithTag(tag).fetchSemanticsNode()
                val page = compose.onNodeWithTag("run_share_page").fetchSemanticsNode()
                val safe = compose.onNodeWithTag("run_share_safe_content").fetchSemanticsNode()
                compose.runOnIdle {
                    val window = compose.activity.window
                    val decor = window.decorView
                    val content = decor.findViewById<View>(android.R.id.content)
                    val insets = ViewCompat.getRootWindowInsets(decor)
                    val visible = Rect().also { decor.getWindowVisibleDisplayFrame(it) }
                    val decorOnScreen = IntArray(2).also { decor.getLocationOnScreen(it) }
                    val decorInWindow = IntArray(2).also { decor.getLocationInWindow(it) }
                    val contentOnScreen = IntArray(2).also { content.getLocationOnScreen(it) }
                    val imm = compose.activity.getSystemService(InputMethodManager::class.java)
                    fun nodeBounds(node: androidx.compose.ui.semantics.SemanticsNode): JSONObject =
                        JSONObject().apply {
                            put("semanticPositionInWindow", node.positionInWindow.toString())
                            put("semanticSize", node.size.toString())
                            put("clippedBoundsInRoot", node.boundsInRoot.toString())
                            put("coordinatesAttached", node.layoutInfo.coordinates.isAttached)
                            if (node.layoutInfo.coordinates.isAttached) {
                                put("layoutPositionInWindow", node.layoutInfo.coordinates.positionInWindow().toString())
                                put("layoutSize", node.layoutInfo.coordinates.size.toString())
                            }
                        }
                    val report = JSONObject().apply {
                        put("sourceKind", "Native ComponentActivity IME; synthetic preview and fake export callbacks")
                        put("tag", tag)
                        put("uptimeMillis", android.os.SystemClock.uptimeMillis())
                        put("fontScale", config.fontScale)
                        put("screenWidthDp", config.screenWidthDp)
                        put("screenHeightDp", config.screenHeightDp)
                        put("density", compose.activity.resources.displayMetrics.density)
                        put("windowFlags", window.attributes.flags)
                        put("softInputMode", window.attributes.softInputMode)
                        put("activityHasWindowFocus", compose.activity.hasWindowFocus())
                        put("decorSize", "${decor.width}x${decor.height}")
                        put("decorOnScreen", decorOnScreen.joinToString(","))
                        put("decorInWindow", decorInWindow.joinToString(","))
                        put("decorScroll", "${decor.scrollX},${decor.scrollY}")
                        put("contentSize", "${content.width}x${content.height}")
                        put("contentOnScreen", contentOnScreen.joinToString(","))
                        put("contentScroll", "${content.scrollX},${content.scrollY}")
                        put("visibleDisplayFrame", visible.toString())
                        put("systemBars", insets?.getInsets(WindowInsetsCompat.Type.systemBars()).toString())
                        put("ime", insets?.getInsets(WindowInsetsCompat.Type.ime()).toString())
                        put("imeVisible", insets?.isVisible(WindowInsetsCompat.Type.ime()))
                        put("immFullscreen", imm?.isFullscreenMode)
                        put("immActive", imm?.isActive)
                        put("immAcceptingText", imm?.isAcceptingText)
                        put("target", nodeBounds(target))
                        put("page", nodeBounds(page))
                        put("safeContent", nodeBounds(safe))
                    }
                    File(compose.activity.cacheDir, "design-review").apply { mkdirs() }
                        .resolve("run-share-native-ime-private-diagnostics.jsonl").appendText(report.toString() + "\n")
                }
                captureDevice("run-share-native-ime-private-before-$tag")

                    }
                    throw failure
                }
            }
            withinWindow("close_run_share")
            compose.onNodeWithTag("save_run_share").performScrollTo()
            withinWindow("save_run_share")
            compose.onNodeWithTag("save_run_share").performClick()
            compose.onNodeWithTag("send_run_share").performScrollTo()
            withinWindow("send_run_share")
            compose.onNodeWithTag("send_run_share").performClick()
            compose.runOnIdle { assertEquals(1, saves); assertEquals(1, shares); assertEquals("키보드 접근성 검토", draft.value.caption) }
            captureDevice("run-share-native320-font2-ime-fixture")
            compose.onNodeWithTag("close_run_share").performClick()
            compose.onNodeWithTag("run_share_page").assertDoesNotExist()
            compose.runOnIdle {
                val window = compose.activity.window
                assertEquals("Leaving the editor restores its original owned layout flags",
                    editorOriginalLayoutFlags, window.decorView.systemUiVisibility and layoutMask)
                assertEquals("Leaving the editor restores its original soft input mode",
                    editorOriginalSoftInputMode, window.attributes.softInputMode)
            }
        } finally {
            try {
                compose.runOnUiThread { editorShown.value = false }
                compose.waitForIdle()
            } finally {
                compose.runOnUiThread {
                    val window = compose.activity.window
                    val decor = window.decorView
                    decor.systemUiVisibility = (decor.systemUiVisibility and layoutMask.inv()) or oldTestLayoutFlags
                    window.setSoftInputMode(oldMode)
                    ViewCompat.requestApplyInsets(decor)
                }
            }
            // No borrowed input or export is ever recycled by the product; this case owns this tiny fixture.
        }
    }

    @Test fun normalTextStoryCaptionStaysVisibleAndAcceptsRealKeysAcrossImeResize() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val config = instrumentation.targetContext.resources.configuration
        assertTrue("Separate native480/font1 tall-window phase required",
            config.screenWidthDp in 475..485 && config.fontScale in .99f..1.01f && config.screenHeightDp >= 700)
        val draft = mutableStateOf(RunShareDraft(ratio = RunShareRatio.STORY))
        val editorShown = mutableStateOf(true)
        val preview = Bitmap.createBitmap(16, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.DKGRAY)
        }
        val window = compose.activity.window
        val layoutMask = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        val oldMode = window.attributes.softInputMode
        val oldTestLayoutFlags = window.decorView.systemUiVisibility and layoutMask
        var editorOriginalLayoutFlags = 0
        var editorOriginalSoftInputMode = 0
        try {
            compose.runOnUiThread {
                compose.activity.enableEdgeToEdge()
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                editorOriginalLayoutFlags = window.decorView.systemUiVisibility and layoutMask
                editorOriginalSoftInputMode = window.attributes.softInputMode
            }
            compose.setContent { RunmoniTheme {
                if (editorShown.value) RunShareEditorUi(draft.value, 1, preview, false, true, false, true, null, null,
                    onDraft = { draft.value = it }, onTool = {}, onPickPhoto = {}, onRemovePhoto = {},
                    onTransform = { _, _, _ -> }, onSave = {}, onShare = {},
                    onBack = { editorShown.value = false })
            } }
            val density = compose.activity.resources.displayMetrics.density
            val initialHeight = compose.onNodeWithTag("run_share_safe_content").fetchSemanticsNode().size.height
            assertTrue("The real editor starts tall before the IME", initialHeight / density >= 700f)
            compose.onNodeWithTag("run_share_caption").performScrollTo().performClick()

            var previous: String? = null
            var stableSince = 0L
            compose.waitUntil(30_000) {
                val field = compose.onNodeWithTag("run_share_caption").fetchSemanticsNode()
                val safe = compose.onNodeWithTag("run_share_safe_content").fetchSemanticsNode()
                val observation = compose.runOnIdle {
                    val decor = window.decorView
                    val insets = ViewCompat.getRootWindowInsets(decor)
                    val imm = compose.activity.getSystemService(InputMethodManager::class.java)
                    val attached = field.layoutInfo.coordinates.isAttached
                    val accepting = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true &&
                        imm?.isActive == true && imm?.isAcceptingText == true && attached
                    val signature = "${decor.width}x${decor.height};" +
                        "${insets?.getInsets(WindowInsetsCompat.Type.ime())};${safe.size};" +
                        if (attached) "${field.layoutInfo.coordinates.positionInWindow()};${field.size}" else "detached"
                    accepting to signature
                }
                val now = android.os.SystemClock.uptimeMillis()
                if (!observation.first || previous != observation.second) {
                    previous = observation.second
                    stableSince = now
                    false
                } else now - stableSince >= 250L
            }
            val reducedHeight = compose.onNodeWithTag("run_share_safe_content").fetchSemanticsNode().size.height
            assertTrue("The actual IME changes the tall editor into a short window",
                reducedHeight < initialHeight && reducedHeight / density < 500f)

            fun entireTargetWithinWindow(tag: String) {
                val target = compose.onNodeWithTag(tag).assertIsDisplayed()
                    .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).fetchSemanticsNode()
                compose.runOnIdle {
                    val decor = window.decorView
                    val insets = requireNotNull(ViewCompat.getRootWindowInsets(decor))
                    assertTrue("The real IME remains visible", insets.isVisible(WindowInsetsCompat.Type.ime()))
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val coordinates = target.layoutInfo.coordinates
                    val pos = coordinates.positionInWindow()
                    val size = coordinates.size
                    assertTrue("Entire $tag clears the status bar", pos.y >= bars.top - 1f)
                    assertTrue("Entire $tag clears the keyboard/navigation bar",
                        pos.y + size.height <= decor.height - maxOf(bars.bottom, ime.bottom) + 1f)
                    assertTrue("Entire $tag fits horizontally",
                        pos.x >= bars.left - 1f && pos.x + size.width <= decor.width - bars.right + 1f)
                    val top = target.positionInRoot.y
                    val bottom = top + target.size.height
                    val vertical = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
                    var ancestor = target.parent
                    while (ancestor != null) {
                        val viewport = ancestor
                        if (vertical.matches(viewport)) assertTrue("Entire $tag fits its scrolling viewport",
                            top >= viewport.boundsInRoot.top - 1f && bottom <= viewport.boundsInRoot.bottom + 1f)
                        ancestor = viewport.parent
                    }
                }
            }

            // No post-show performScrollTo, focus request, IME dismissal or injected Compose text.
            compose.onNodeWithTag("run_share_caption").assertIsFocused()
            entireTargetWithinWindow("run_share_caption")
            entireTargetWithinWindow("close_run_share")
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("input text Morning%sMiles")
            ).use { it.readBytes() }
            compose.waitUntil(30_000) { draft.value.caption == "Morning Miles" }
            val field = compose.onNodeWithTag("run_share_caption").assertIsFocused().fetchSemanticsNode()
            assertEquals("Actual shell keys reach the focused caption", "Morning Miles",
                field.config[SemanticsProperties.EditableText].text)
            entireTargetWithinWindow("run_share_caption")
            entireTargetWithinWindow("close_run_share")
            captureDevice("run-share-native480-font1-ime-caption-fixture")
            compose.onNodeWithTag("close_run_share").performClick()
            compose.onNodeWithTag("run_share_page").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(editorOriginalLayoutFlags, window.decorView.systemUiVisibility and layoutMask)
                assertEquals(editorOriginalSoftInputMode, window.attributes.softInputMode)
                assertEquals("Morning Miles", draft.value.caption)
            }
        } catch (failure: AssertionError) {
            runCatching { captureDevice("run-share-native480-font1-ime-caption-private-failure") }
            throw failure
        } finally {
            try {
                compose.runOnUiThread { editorShown.value = false }
                compose.waitForIdle()
            } finally {
                compose.runOnUiThread {
                    val decor = window.decorView
                    decor.systemUiVisibility = (decor.systemUiVisibility and layoutMask.inv()) or oldTestLayoutFlags
                    window.setSoftInputMode(oldMode)
                    ViewCompat.requestApplyInsets(decor)
                }
            }
        }
    }
}

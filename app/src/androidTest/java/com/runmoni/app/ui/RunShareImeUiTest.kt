package com.runmoni.app.ui

import android.graphics.Bitmap
import org.json.JSONObject
import java.io.File
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.graphics.Rect
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.domain.RunShareDraft
import org.junit.Assert.*
import org.junit.Test

/** Actual API29 320dp/font2 and real IME; synthetic preview/fake export callbacks, not MainActivity. */
class RunShareImeUiTest : RunmoniDesignTestBase() {
    @Test fun realKeyboardKeepsCloseAndScrollableExportActionsOutsideImeAndSystemBars() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assertTrue("Separate native320/font2 phase required", config.screenWidthDp in 315..325 && config.fontScale >= 1.9f)
        val draft = mutableStateOf(RunShareDraft())
        val preview = Bitmap.createBitmap(16, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.DKGRAY) }
        val oldMode = compose.activity.window.attributes.softInputMode
        var saves = 0
        var shares = 0
        try {
            compose.runOnUiThread {
                compose.activity.enableEdgeToEdge()
                compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            compose.setContent { RunmoniTheme {
                RunShareEditorUi(draft.value, 1, preview, false, true, false, true, null, null,
                    onDraft = { draft.value = it }, onTool = {}, onPickPhoto = {}, onRemovePhoto = {},
                    onTransform = { _, _, _ -> }, onSave = { saves++ }, onShare = { shares++ }, onBack = {})
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
        } finally {
            compose.runOnUiThread { compose.activity.window.setSoftInputMode(oldMode) }
            // No borrowed input or export is ever recycled by the product; this case owns this tiny fixture.
        }
    }
}

package com.runmoni.app.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/**
 * ComponentActivity UI fixtures. The first three methods inject a tiny synthetic preview and fake
 * callbacks: they are not MainActivity, a photo-picker selection, a renderer or a MediaStore save.
 * The fourth exercises the real root navigation with in-memory records/fake actions and an actual
 * approved-avatar preview, without changing a stored record, wallet, auth account or gallery row.
 */
class RunShareUiTest : RunmoniDesignTestBase() {
    private fun preview() = Bitmap.createBitmap(16, 20, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.rgb(32, 86, 65))
    }

    @Composable
    private fun ShortViewport(content: @Composable () -> Unit) {
        val density = LocalDensity.current.density
        val config = Configuration(LocalConfiguration.current).apply {
            screenWidthDp = 320
            screenHeightDp = 240
            fontScale = 2f
        }
        CompositionLocalProvider(LocalDensity provides Density(density, 2f), LocalConfiguration provides config) {
            Box(Modifier.width(320.dp).height(240.dp).testTag("audit_viewport")) { content() }
        }
    }

    private fun action(tag: String) = compose.onNodeWithTag(tag).assertIsDisplayed()
        .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)

    /** Assert the measured target, not its already-clipped semantic bounds, fits before tapping. */
    private fun fullyVisible(tag: String): SemanticsNodeInteraction {
        val interaction = action(tag)
        val target = interaction.fetchSemanticsNode()
        val x = target.positionInRoot.x
        val y = target.positionInRoot.y
        val right = x + target.size.width
        val bottom = y + target.size.height
        val safe = compose.onNodeWithTag("run_share_safe_content").fetchSemanticsNode().boundsInRoot
        assertTrue("Entire $tag fits safe content horizontally: $x..$right / $safe",
            x >= safe.left - 1f && right <= safe.right + 1f)
        assertTrue("Entire $tag fits safe content vertically: $y..$bottom / $safe",
            y >= safe.top - 1f && bottom <= safe.bottom + 1f)
        val vertical = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        val horizontal = SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)
        var ancestor = target.parent
        while (ancestor != null) {
            val viewport = ancestor
            val bounds = viewport.boundsInRoot
            if (vertical.matches(viewport)) assertTrue(
                "Entire $tag fits its vertical scroll viewport: $y..$bottom / $bounds",
                y >= bounds.top - 1f && bottom <= bounds.bottom + 1f)
            if (horizontal.matches(viewport)) assertTrue(
                "Entire $tag fits its horizontal scroll viewport: $x..$right / $bounds",
                x >= bounds.left - 1f && right <= bounds.right + 1f)
            ancestor = viewport.parent
        }
        return interaction
    }

    /** The child's nearest scroll ancestor is horizontal; first reveal its row vertically. */
    private fun choose(parentTag: String, tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag(parentTag).performScrollTo()
        compose.onNodeWithTag(tag).performScrollTo()
        fullyVisible(tag).performClick().assertIsSelected()
        // The selected radio option gains a check icon. Keep that entire result visible too.
        compose.onNodeWithTag(parentTag).performScrollTo()
        compose.onNodeWithTag(tag).performScrollTo()
        return fullyVisible(tag).assertIsSelected()
    }

    @Test fun everyToolKeepsTextAndActionsUsableAcrossNineViewportAndFontCases() {
        val viewport = mutableStateOf(Viewport(320, 1f))
        val draft = mutableStateOf(RunShareDraft(caption = "내 페이스로 천천히 달리기 ".repeat(3)))
        val tool = mutableStateOf(0)
        val attached = mutableStateOf(true)
        val image = preview()
        var saves = 0
        var shares = 0
        try {
            compose.setContent {
                if (attached.value) RunmoniTheme { ViewportBox(viewport.value) {
                    RunShareEditorUi(draft.value, tool.value, image, photoPresent = true,
                        ready = true, busy = false, editable = true, error = null, message = null,
                        onDraft = { draft.value = it }, onTool = { tool.value = it },
                        onPickPhoto = {}, onRemovePhoto = {}, onTransform = { _, _, _ -> },
                        onSave = { saves++ }, onShare = { shares++ }, onBack = {},
                        previewDescription = "Synthetic UI preview; no run or photo was saved")
                } }
            }
            for (width in listOf(320, 360, 411)) for (scale in listOf(1f, 1.5f, 2f)) {
                compose.runOnIdle { viewport.value = Viewport(width, scale) }
                action("close_run_share")
                for (index in 0..2) {
                    choose("run_share_tools", "run_share_tool_$index")
                    when (index) {
                        0 -> {
                            compose.onNodeWithTag("run_share_photo").performScrollTo()
                            action("run_share_photo")
                            choose("run_share_theme_choices", "run_share_theme_2")
                            action("run_share_theme_2")
                            compose.runOnIdle { assertEquals(RunShareTheme.PAPER, draft.value.theme) }
                        }
                        1 -> {
                            choose("run_share_placement_choices", "run_share_placement_1")
                            compose.onNodeWithTag("run_share_name").performScrollTo()
                            action("run_share_name")
                            compose.runOnIdle { assertEquals(RunSharePlacement.TOP, draft.value.placement) }
                        }
                        2 -> {
                            choose("run_share_avatar_side_choices", "run_share_avatar_side_1")
                            action("run_share_avatar_side_1")
                            compose.runOnIdle { assertTrue(draft.value.avatarX >= .5f) }
                        }
                    }
                    // These are fixed at the ordinary 640dp fixture height, even with 200% text.
                    fullyVisible("save_run_share").assertIsEnabled()
                    fullyVisible("send_run_share").assertIsEnabled()
                    noTextOverflow("share editor ComponentActivity fixture $width/$scale tool=$index")
                }
                fullyVisible("save_run_share").performClick()
                fullyVisible("send_run_share").performClick()
                if (width == 320 && scale == 2f) capture("run-share-controls-320-font2-fixture")
            }
            compose.runOnIdle { assertEquals(9, saves); assertEquals(9, shares) }
        } finally {
            compose.runOnIdle { attached.value = false }
            compose.waitForIdle()
            image.recycle()
        }
    }

    @Test fun shortHeightWithLargeTextKeepsBackFixedAndBothFooterActionsReachableByScroll() {
        val attached = mutableStateOf(true)
        val tool = mutableStateOf(0)
        val draft = mutableStateOf(RunShareDraft())
        val image = preview()
        var saves = 0
        var shares = 0
        var backs = 0
        try {
            compose.setContent {
                if (attached.value) RunmoniTheme { ShortViewport {
                    RunShareEditorUi(draft.value, tool.value, image, photoPresent = false,
                        ready = true, busy = false, editable = true, error = null, message = null,
                        onDraft = { draft.value = it }, onTool = { tool.value = it },
                        onPickPhoto = {}, onRemovePhoto = {}, onTransform = { _, _, _ -> },
                        onSave = { saves++ }, onShare = { shares++ }, onBack = { backs++ })
                } }
            }
            fullyVisible("close_run_share")
            compose.onNodeWithTag("save_run_share").performScrollTo()
            fullyVisible("save_run_share").assertIsEnabled().performClick()
            fullyVisible("close_run_share")
            compose.onNodeWithTag("send_run_share").performScrollTo()
            fullyVisible("send_run_share").assertIsEnabled().performClick()
            fullyVisible("close_run_share")
            noTextOverflow("share short 320x240/font2 ComponentActivity constraint fixture; not a physical rotation")
            capture("run-share-short-320-font2-fixture")
            fullyVisible("close_run_share").performClick()
            compose.runOnIdle { assertEquals(1, saves); assertEquals(1, shares); assertEquals(1, backs) }
        } finally {
            compose.runOnIdle { attached.value = false }
            compose.waitForIdle()
            image.recycle()
        }
    }

    @Test fun failedRestoredPhotoCanBeRemovedWithoutLosingTheCaptionOrOtherDraftChoices() {
        val attached = mutableStateOf(true)
        val clearable = mutableStateOf(true)
        val original = RunShareDraft(caption = "지금의 페이스로", theme = RunShareTheme.INK)
        val draft = mutableStateOf(original)
        val tool = mutableStateOf(0)
        val image = preview()
        var removals = 0
        var saves = 0
        try {
            compose.setContent {
                if (attached.value) RunmoniTheme { ViewportBox(Viewport(320, 2f)) {
                    RunShareEditorUi(draft.value, tool.value, image, photoPresent = false,
                        ready = !clearable.value, busy = false, editable = true,
                        error = if (clearable.value) "사진을 다시 선택하거나 빼 주세요." else null, message = null,
                        onDraft = { draft.value = it }, onTool = { tool.value = it }, onPickPhoto = {},
                        onRemovePhoto = { removals++; clearable.value = false }, onTransform = { _, _, _ -> },
                        onSave = { saves++ }, onShare = {}, onBack = {}, photoClearable = clearable.value)
                } }
            }
            compose.onNodeWithTag("save_run_share").assertIsNotEnabled()
            compose.onNodeWithTag("run_share_photo_remove").performScrollTo()
            fullyVisible("run_share_photo_remove").assertIsEnabled().performClick()
            compose.onNodeWithTag("run_share_photo_remove").assertDoesNotExist()
            compose.onNodeWithTag("run_share_error").assertDoesNotExist()
            fullyVisible("save_run_share").assertIsEnabled().performClick()
            compose.runOnIdle {
                assertEquals(1, removals)
                assertEquals(1, saves)
                assertEquals(original, draft.value)
            }
            noTextOverflow("failed restored URI recovery ComponentActivity/fake callbacks")
        } finally {
            compose.runOnIdle { attached.value = false }
            compose.waitForIdle()
            image.recycle()
        }
    }

    @Test fun rootEditorBackRestoresRecordScrollAndRejectsDisposedOrRemovedRecordShareCallbacks() {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val started = maxOf(today, now - 1_200_000L)
        val record = RunRecord("share-navigation-fixture", started, maxOf(now, started + 1),
            distanceMeters = 5230.0, activeDurationMillis = 1_200_000L)
        val profile = Profile(name = "ShareNavFixture", mileage = 444)
        val state = mutableStateOf(AppState(profile = profile, records = listOf(record),
            runPersistence = RunPersistenceState(initialized = true)))
        val checks = AtomicInteger()
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun canShareRun(record: RunRecord): Boolean {
                checks.incrementAndGet()
                return RunShareAccess.allowed(state.value, record)
            }
        }
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("history_period_1").performScrollTo().performClick()
        compose.onNodeWithTag("record_${record.id}").performScrollTo().performClick()
        compose.onNodeWithTag("delete_run").performScrollTo().assertIsDisplayed()
        fun recordScroll(): Float {
            val range = compose.onNodeWithTag("record_detail_scroll").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
            return compose.runOnIdle { range.value() }
        }
        val before = recordScroll()
        assertTrue("The record really was scrolled before leaving", before > 0f)
        val oldShare = compose.onNodeWithTag("share_run_record").fetchSemanticsNode().config[RecordActionSnapshot]
        action("share_run_record").performClick()
        compose.onNodeWithTag("run_share_page").assertIsDisplayed()
        compose.onNodeWithTag("record_detail_page").assertDoesNotExist()
        listOf("home", "explore", "run", "crew", "me").forEach { compose.onNodeWithTag("nav_$it").assertDoesNotExist() }
        compose.runOnIdle {
            val count = checks.get()
            oldShare.invoke()
            assertEquals("A disposed page must stop before consulting current actions", count, checks.get())
        }
        action("close_run_share").performClick()
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
        assertEquals("Back restores the exact record scroll", before, recordScroll(), 1f)
        val removedRecordShare = compose.onNodeWithTag("share_run_record").fetchSemanticsNode().config[RecordActionSnapshot]
        compose.runOnIdle {
            // Invoke the old product lambda in the same UI turn, before recomposition can disable it.
            state.value = state.value.copy(records = emptyList())
            removedRecordShare.invoke()
        }
        compose.onNodeWithTag("run_share_page").assertDoesNotExist()
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
        compose.onNodeWithTag("share_run_record").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(records = listOf(record)) }
        compose.onNodeWithTag("close_record").performClick()
        compose.onNodeWithTag("nav_me").assertIsSelected()
        compose.onNodeWithTag("history_period_1").performScrollTo().assertIsSelected()
        compose.runOnIdle { assertEquals(profile, state.value.profile); assertEquals(listOf(record), state.value.records) }
    }
}

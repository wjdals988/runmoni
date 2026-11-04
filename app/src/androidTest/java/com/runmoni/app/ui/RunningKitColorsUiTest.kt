package com.runmoni.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Callback/loading fixtures on Android, not proof of mask quality or durable saving. */
class RunningKitColorsUiTest : RunmoniDesignTestBase() {
    @Test fun unavailableArtworkDisablesChoicesUntilExplicitRetrySucceeds() {
        val calls = AtomicInteger()
        val choices = mutableListOf<Pair<RunningKitRegion, Int>>()
        val colors = mutableStateOf(RunningKitColors(top = 2, bottom = 3, shoes = 4))
        val loader: suspend () -> Boolean = {
            if (calls.incrementAndGet() == 1) throw IOException("fixture mask unavailable")
            true
        }
        compose.setContent {
            ViewportBox(Viewport(320, 2f)) {
                RunmoniTheme {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
                        RunningKitColorControls(colors.value, RunningKitRegion.TOP,
                            onSelect = { region, color ->
                                choices += region to color
                                colors.value = colors.value.withColor(region, color)
                            }, availabilityLoader = loader)
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("kit_colors_unavailable").fetchSemanticsNodes().isNotEmpty() }
        (0..5).forEach { color -> compose.onNodeWithTag("kit_color_top_$color").performScrollTo().assertIsNotEnabled() }
        compose.runOnIdle { assertTrue(choices.isEmpty()); assertEquals(2, colors.value.top) }
        noTextOverflow("unavailable running kit colors 320/font2")
        capture("kit-colors-unavailable-fixture-320-200")
        captureDevice("kit-colors-unavailable-fixture-320-200-device")

        compose.onNodeWithTag("kit_colors_retry").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("kit_color_top_1") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("kit_color_top_1").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("kit_colors_unavailable").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(RunningKitRegion.TOP to 1), choices)
            assertEquals(RunningKitColors(top = 1, bottom = 3, shoes = 4), colors.value)
            assertEquals(2, calls.get())
        }
    }

    @Test fun namedRadioChoicesWrapWithoutShrinkingAcrossSmallAndLargeTextViewports() {
        val viewport = mutableStateOf(Viewport(320, 1f))
        val colors = mutableStateOf(RunningKitColors(bottom = 3, shoes = 4))
        compose.setContent {
            ViewportBox(viewport.value) {
                RunmoniTheme {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
                        RunningKitColorControls(colors.value, RunningKitRegion.TOP,
                            onSelect = { region, color -> colors.value = colors.value.withColor(region, color) },
                            availabilityLoader = { true })
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("kit_color_top_1") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        listOf(Viewport(320, 1f), Viewport(320, 2f), Viewport(240, 2f)).forEach { condition ->
            compose.runOnIdle { viewport.value = condition }
            listOf("원본", "민트", "코랄", "블루", "퍼플", "골드").forEachIndexed { color, label ->
                compose.onNodeWithTag("kit_color_top_$color").performScrollTo().assertIsDisplayed()
                    .assertIsEnabled().assertHeightIsAtLeast(48.dp)
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                    .assertContentDescriptionEquals("상의 색상").assertTextContains(label)
                    .performClick().assertIsSelected()
                compose.runOnIdle {
                    assertEquals(color, colors.value.top)
                    assertEquals(3, colors.value.bottom)
                    assertEquals(4, colors.value.shoes)
                }
            }
            noTextOverflow("running kit colors ${condition.width}/${condition.scale}")
            capture("kit-colors-palette-fixture-${condition.width}-${(condition.scale * 100).toInt()}")
            captureDevice("kit-colors-palette-fixture-${condition.width}-${(condition.scale * 100).toInt()}-device")
        }
    }

    @Test fun closetColorChangesKeepItemSelectionsCostumeInventoryAndGender() {
        val original = Profile(name = "색상 검토 러너", mileage = 777,
            avatar = AvatarStyle(hair = 2, top = 4, bottom = 2, shoes = 1, socks = 2, costume = 3,
                gender = AvatarGender.FEMALE, runningKitColors = RunningKitColors(2, 3, 4)),
            ownedItems = AvatarCatalogue.defaultOwned + setOf("hair:2", "top:4", "bottom:2", "shoes:1", "socks:2", "costume:3"))
        val state = mutableStateOf(AppState(profile = original))
        val changes = mutableListOf<Pair<RunningKitRegion, Int>>()
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun setRunningKitColor(region: RunningKitRegion, color: Int) {
                changes += region to color
                val profile = state.value.profile
                state.value = state.value.copy(profile = profile.copy(avatar = profile.avatar.copy(
                    runningKitColors = profile.avatar.runningKitColors.withColor(region, color))))
            }
        }
        compose.setContent { RunmoniTheme { ClosetDialog(state.value.profile, actions) {} } }
        compose.onNodeWithTag("closet_animation").performClick()
        val preview = compose.onNodeWithTag("closet_avatar_preview").getUnclippedBoundsInRoot()
        listOf(Triple("top", RunningKitRegion.TOP, 1), Triple("bottom", RunningKitRegion.BOTTOM, 5),
            Triple("shoes", RunningKitRegion.SHOES, 2)).forEach { (key, region, color) ->
            compose.onNodeWithTag("closet_slot_$key").performScrollTo().performClick()
            compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("kit_color_${key}_$color") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("kit_color_${key}_$color").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("closet_avatar_preview").assertIsDisplayed()
            assertEquals(preview, compose.onNodeWithTag("closet_avatar_preview").getUnclippedBoundsInRoot())
            compose.onNodeWithTag("kit_colors_scope").performScrollTo().assertTextContains("기본형", substring = true)
            compose.runOnIdle { assertEquals(color, state.value.profile.avatar.runningKitColors.color(region)) }
        }
        compose.runOnIdle {
            assertEquals(listOf(RunningKitRegion.TOP to 1, RunningKitRegion.BOTTOM to 5, RunningKitRegion.SHOES to 2), changes)
            assertEquals(original.copy(avatar = original.avatar.copy(runningKitColors = RunningKitColors(1, 5, 2))), state.value.profile)
            assertEquals(9, AvatarCatalogue.tabs.size)
            assertEquals(13, AvatarCatalogue.slots.size)
            assertEquals(58, AvatarCatalogue.slots.sumOf { it.variants.size })
        }
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("나의 러닝 미니미").fetchSemanticsNodes().isNotEmpty() }
        noTextOverflow("closet basic running kit color and unchanged inventory")
        capture("kit-colors-closet-female-fixture")
        captureDevice("kit-colors-closet-female-fixture-device")
        compose.onNodeWithTag("closet_slot_hair").performScrollTo().performClick()
        compose.onNodeWithTag("kit_colors_top").assertDoesNotExist()
        compose.onNodeWithTag("kit_colors_bottom").assertDoesNotExist()
        compose.onNodeWithTag("kit_colors_shoes").assertDoesNotExist()
    }
}

/** Run separately with the actual Android window at320dp and system fontScale2. */
class RunningKitColorsNativeUiTest : RunmoniDesignTestBase() {
    @Test fun actualLargeTextClosetKeepsPreviewAndAllSixColorActionsAccessible() {
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
        compose.setContent { RunmoniTheme { ClosetDialog(state.value.profile, actions) {} } }
        compose.onNodeWithTag("closet_animation").performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("kit_color_top_1") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        val preview = compose.onNodeWithTag("closet_avatar_preview").getUnclippedBoundsInRoot()
        (0..5).forEach { color ->
            compose.onNodeWithTag("kit_color_top_$color").performScrollTo().assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
            compose.onNodeWithTag("closet_avatar_preview").assertIsDisplayed()
            assertEquals(preview, compose.onNodeWithTag("closet_avatar_preview").getUnclippedBoundsInRoot())
        }
        compose.onNodeWithTag("closet_top_1").performScrollTo().assertIsEnabled()
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("나의 러닝 미니미").fetchSemanticsNodes().isNotEmpty() }
        noTextOverflow("native320dp/font2 basic running kit colors")
        capture("kit-colors-closet-native-320-font2-fixture")
        captureDevice("kit-colors-closet-native-320-font2-device")
        compose.runOnIdle { assertEquals(777, state.value.profile.mileage); assertEquals(5, state.value.profile.avatar.runningKitColors.top) }
    }
}

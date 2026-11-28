package com.runmoni.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.avatar.AvatarArtwork
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Actual Android dialog, run once at native size and once at native320dp/font2. */
class ConsumerClosetTest : RunmoniDesignTestBase() {
    @Test fun normalWidthOpensTheWornGarmentPaletteAndReturnsToTheSameItemList() {
        val state = mutableStateOf(AppState(profile = Profile(avatar = AvatarStyle(top = 1))))
        val choices = mutableListOf<Pair<RunningKitRegion, Int>>()
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun setRunningKitColor(region: RunningKitRegion, color: Int) {
                choices += region to color
                val profile = state.value.profile
                val avatar = profile.avatar.copy(runningKitColors = profile.avatar.runningKitColors.withColor(region, color))
                state.value = state.value.copy(profile = profile.copy(avatar = avatar))
            }
        }
        compose.setContent { ViewportBox(Viewport(411, 1f)) {
            RunmoniTheme { ClosetDialog(state.value.profile, actions) {} }
        } }
        compose.onNodeWithTag("closet_animation").performClick()
        compose.onNodeWithTag("closet_open_colors").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("kit_color_top_1") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("kit_color_top_1").assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithTag("closet_avatar_preview").assertIsDisplayed()
        capture("closet-color-shortcut-411-fixture")
        compose.onNodeWithTag("closet_back_to_items").assertIsDisplayed().performClick()
        compose.onNodeWithTag("closet_top_1").assertIsDisplayed().assertIsSelected()
        compose.runOnIdle {
            assertEquals(listOf(RunningKitRegion.TOP to 1), choices)
            assertEquals(1, state.value.profile.avatar.top)
            assertEquals(1, state.value.profile.avatar.runningKitColors.top)
        }
    }

    @Test fun hairThumbnailsShowDistinctCutsWhileTheFittingKeepsItsHat() {
        val state = mutableStateOf(AppState(profile = Profile(avatar = AvatarStyle(headwear = 1))))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { ViewportBox(Viewport(411, 1f)) {
            RunmoniTheme { ClosetDialog(state.value.profile, actions) {} }
        } }
        compose.onNodeWithTag("closet_animation").performClick()
        compose.onNodeWithTag("closet_slot_hair").performScrollTo().performClick()
        val thumbnails = listOf(0, 4, 5, 6).map { variant ->
            val tag = "closet_preview_hair_$variant"
            val ownChild = hasAnyAncestor(hasTestTag(tag))
            val thumbnail = compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasTestTag("minimi_loading") and ownChild, useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty()
            }
            compose.onNode(hasTestTag("minimi_retry") and ownChild, useUnmergedTree = true).assertDoesNotExist()
            val bitmap = thumbnail.captureToImage().asAndroidBitmap()
            IntArray(bitmap.width * bitmap.height).apply {
                bitmap.getPixels(this, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            }.contentHashCode()
        }
        assertEquals(4, thumbnails.toSet().size)
        capture("closet-hair-411-fixture")
        compose.onNodeWithTag("closet_hair_6").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(6, state.value.profile.avatar.hair)
            assertEquals(1, state.value.profile.avatar.headwear)
        }
    }

    @Test fun fullScreenClosetKeepsPreviewAndInventoryAcrossEveryCategory() {
        val original = Profile(name = "검토 러너", mileage = 1234)
        val state = mutableStateOf(AppState(profile = original))
        val fake = RunmoniUiTest.FakeActions(state)
        val actions = object : AppActions by fake {
            override fun saveUnifiedProfile(name: String, expectedUid: String, expectedBackend: CommunityBackend) {
                assertEquals(state.value.communityBackend, expectedBackend)
                saveUnifiedProfile(name, expectedUid)
            }
            override fun saveUnifiedProfile(name: String, expectedUid: String) {
                // This fixture models the VM's acknowledged local save only.
                // It does not reserve a nickname or authenticate an account.
                state.value = state.value.copy(profile = state.value.profile.copy(name = name),
                    unifiedProfile = state.value.unifiedProfile.copy(savedSerial = state.value.unifiedProfile.savedSerial + 1))
            }
        }
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("open_closet").performScrollTo().performClick()
        compose.onNodeWithTag("nav_me").assertDoesNotExist()
        compose.onNodeWithTag("open_avatar_reference").assertDoesNotExist()
        val preview = compose.onNodeWithTag("closet_avatar_preview").fetchSemanticsNode().boundsInRoot
        capture("consumer-closet-native")
        AvatarCatalogue.tabs.forEach { (key, _) ->
            compose.onNodeWithTag("closet_slot_$key").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("closet_avatar_preview").assertIsDisplayed()
            assertEquals(preview, compose.onNodeWithTag("closet_avatar_preview").fetchSemanticsNode().boundsInRoot)
        }
        AvatarCatalogue.accessoryKeys.forEach { key ->
            compose.onNodeWithTag("closet_slot_$key").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("closet_avatar_preview").assertIsDisplayed()
        }
        compose.onNodeWithTag("closet_slot_top").performScrollTo().performClick()
        compose.onNodeWithTag("closet_top_1").performScrollTo().assertIsEnabled()
        val lastReady = AvatarCatalogue.slots.first { it.key == "top" }.variants.indices.last {
            AvatarArtwork.itemReady("top", it, original.avatar.gender)
        }
        compose.onNodeWithTag("closet_top_$lastReady").performScrollTo().assertIsEnabled()
        assertClosetActionInsideSafeArea(compose, "closet_top_$lastReady")
        compose.onNodeWithTag("closet_content_end").performScrollTo().assertIsDisplayed()
        noTextOverflow("consumer closet actual Android viewport")
        capture("consumer-closet-native-items")
        compose.onNodeWithTag("closet_close").assertIsDisplayed().performClick()
        compose.onNodeWithTag("nav_me").assertIsSelected()
        compose.runOnIdle { assertEquals(original, state.value.profile); assertTrue(fake.purchases.isEmpty()) }
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("profile_name").assertDoesNotExist()
        compose.onNodeWithTag("settings_profile").performScrollTo().performClick()
        compose.onNodeWithTag("profile_name").performScrollTo().performTextClearance()
        compose.onNodeWithTag("profile_name").performTextInput("새 러너")
        compose.onNodeWithTag("save_profile").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("새 러너", state.value.profile.name); assertEquals(original.mileage, state.value.profile.mileage); assertEquals(original.ownedItems, state.value.profile.ownedItems) }
        compose.onNodeWithTag("profile_name").assertDoesNotExist()
        // The editor restores the Settings page that opened it, including its scroll.
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        noTextOverflow("consumer settings actual Android viewport")
        capture("consumer-settings-native")
        compose.onNodeWithTag("close_settings").performClick()
        compose.onNodeWithTag("nav_me").assertIsSelected()
    }
}

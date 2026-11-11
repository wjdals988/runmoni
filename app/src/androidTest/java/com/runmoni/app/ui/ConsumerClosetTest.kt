package com.runmoni.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.runmoni.app.avatar.AvatarArtwork
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Actual Android dialog, run once at native size and once at native320dp/font2. */
class ConsumerClosetTest : RunmoniDesignTestBase() {
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
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithTag("nav_me").assertIsSelected()
    }
}

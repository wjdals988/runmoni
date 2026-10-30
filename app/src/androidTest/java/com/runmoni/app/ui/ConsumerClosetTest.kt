package com.runmoni.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Actual Android dialog, run once at native size and once at native320dp/font2. */
class ConsumerClosetTest : RunmoniDesignTestBase() {
    @Test fun fullScreenClosetKeepsPreviewAndInventoryAcrossEveryCategory() {
        val original = Profile(name = "검토 러너", mileage = 1234)
        val state = mutableStateOf(AppState(profile = original))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("open_closet").performScrollTo().performClick()
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
        compose.onNodeWithTag("closet_top_1").performScrollTo().assertIsNotEnabled()
        noTextOverflow("consumer closet actual Android viewport")
        capture("consumer-closet-native-items")
        compose.onAllNodesWithText("닫기").assertCountEquals(1)
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithTag("nav_me").assertIsSelected()
        compose.runOnIdle { assertEquals(original, state.value.profile); assertTrue(actions.purchases.isEmpty()) }
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("profile_name").performScrollTo().performTextClearance()
        compose.onNodeWithTag("profile_name").performTextInput("새 러너")
        compose.onNodeWithTag("save_profile").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("새 러너", state.value.profile.name); assertEquals(original.mileage, state.value.profile.mileage); assertEquals(original.ownedItems, state.value.profile.ownedItems) }
        noTextOverflow("consumer settings actual Android viewport")
        capture("consumer-settings-native")
    }
}

package com.runmoni.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Real Compose inputs with local fake actions. Not a durable wallet or online profile test. */
class ClosetAtelierUiTest : RunmoniDesignTestBase() {
    @Test fun aNewShellAndSeparateVestCanBeWornTogether() {
        val original = Profile(mileage = 876, avatar = AvatarStyle(gender = AvatarGender.FEMALE, shoes = 1, wrist = 1))
        val state = mutableStateOf(AppState(profile = original))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { RunmoniTheme { ClosetDialog(state.value.profile, actions) {} } }
        compose.onNodeWithTag("closet_animation").performClick()
        compose.onNodeWithTag("closet_search").performScrollTo().performTextInput("나이트 방풍")
        compose.onNodeWithTag("closet_top_11").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("closet_fitted_item").assertTextEquals("나이트 방풍 셸")
        compose.onNodeWithTag("closet_slot_accessories").performScrollTo().performClick()
        compose.onNodeWithTag("closet_slot_hydration").performScrollTo().performClick()
        compose.onNodeWithTag("closet_search").performScrollTo().performTextInput("플라스크")
        compose.onNodeWithTag("closet_hydration_7").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("closet_fitted_item").assertTextEquals("듀얼 플라스크 조끼")
        compose.runOnIdle {
            val profile = state.value.profile
            assertEquals(original.avatar.copy(top = 11, hydration = 7), profile.avatar)
            assertEquals(original.mileage, profile.mileage)
            assertTrue(profile.ownedItems.containsAll(original.ownedItems + setOf("top:11", "hydration:7")))
            assertTrue(actions.purchases.isEmpty())
        }
    }

    @Test fun partSearchFiltersRealNamesAndSelectingKeepsOtherEquipmentAndMileage() {
        val original = Profile(mileage = 876, avatar = AvatarStyle(shoes = 1, eyewear = 1))
        val state = mutableStateOf(AppState(profile = original))
        val fake = RunmoniUiTest.FakeActions(state)
        compose.setContent { RunmoniTheme { ClosetDialog(state.value.profile, fake) {} } }
        compose.onNodeWithTag("closet_animation").performClick()
        compose.onNodeWithTag("closet_search").performScrollTo().performTextInput("  메시  ")
        compose.onNodeWithTag("closet_top_0").assertDoesNotExist()
        compose.onNodeWithTag("closet_top_6").performScrollTo().assertIsEnabled().performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(6, state.value.profile.avatar.top)
            assertEquals(original.avatar.shoes, state.value.profile.avatar.shoes)
            assertEquals(original.avatar.eyewear, state.value.profile.avatar.eyewear)
            assertEquals(876, state.value.profile.mileage)
            assertTrue(state.value.profile.ownedItems.containsAll(original.ownedItems))
            assertTrue(fake.purchases.isEmpty())
        }
        compose.onNodeWithTag("closet_search_clear").performScrollTo().assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("closet_top_0").assertExists()
        compose.onNodeWithTag("closet_top_6").assertIsSelected()
    }

    @Test fun noMatchesOffersAnAccessibleResetAndNeverChangesTheOutfit() {
        val original = Profile(mileage = 544, avatar = AvatarStyle(top = 1))
        val state = mutableStateOf(AppState(profile = original))
        compose.setContent {
            ViewportBox(Viewport(320, 2f)) {
                RunmoniTheme { ClosetDialog(state.value.profile, RunmoniUiTest.FakeActions(state)) {} }
            }
        }
        compose.onNodeWithTag("closet_search").performScrollTo().performTextInput("찾을수없는테스트아이템")
        compose.onNodeWithTag("closet_search_empty").assertExists()
        compose.onNodeWithTag("closet_item_count").assertTextEquals("검색 0개")
        compose.onNodeWithTag("closet_search_reset").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("closet_search_empty").assertDoesNotExist()
        compose.onNodeWithTag("closet_top_1").performScrollTo().assertIsSelected()
        assertClosetActionInsideSafeArea(compose, "closet_top_1")
        noTextOverflow("closet search reset 320/font2", awaitAvatarReady = false)
        compose.runOnIdle { assertEquals(original, state.value.profile) }
    }

    @Test fun movingBetweenPartsDoesNotCarryAQueryThatHidesTheNextPart() {
        val state = mutableStateOf(AppState())
        compose.setContent { RunmoniTheme { ClosetDialog(state.value.profile, RunmoniUiTest.FakeActions(state)) {} } }
        compose.onNodeWithTag("closet_search").performScrollTo().performTextInput("메시")
        compose.onNodeWithTag("closet_slot_shoes").performScrollTo().performClick()
        compose.onNodeWithTag("closet_shoes_0").performScrollTo().assertExists()
        compose.onNodeWithTag("closet_search_clear").assertDoesNotExist()
        compose.onNodeWithTag("closet_slot_accessories").performScrollTo().performClick()
        compose.onNodeWithTag("closet_slot_wrist").performScrollTo().performClick()
        compose.onNodeWithTag("closet_search").performScrollTo().performTextInput("없는워치")
        compose.onNodeWithTag("closet_slot_ear").performScrollTo().performClick()
        compose.onNodeWithTag("closet_ear_0").performScrollTo().assertExists()
        compose.onNodeWithTag("closet_search_clear").assertDoesNotExist()
        compose.runOnIdle { assertEquals(AvatarStyle(), state.value.profile.avatar) }
    }

    @Test fun changingAvailableHeightPreservesTheSearchAcrossFittingLayouts() {
        val state = mutableStateOf(AppState())
        val height = mutableStateOf(640)
        compose.setContent {
            Box(Modifier.width(360.dp).height(height.value.dp)) {
                RunmoniTheme { ClosetDialog(state.value.profile, RunmoniUiTest.FakeActions(state)) {} }
            }
        }
        compose.onNodeWithTag("closet_search").performScrollTo().performTextInput("메시")
        compose.runOnIdle { height.value = 320 }
        compose.onNodeWithTag("closet_search").performScrollTo().assertTextContains("메시")
        compose.onNodeWithTag("closet_top_0").assertDoesNotExist()
        compose.onNodeWithTag("closet_top_6").performScrollTo().assertExists()
        compose.runOnIdle { height.value = 640 }
        compose.onNodeWithTag("closet_search").performScrollTo().assertTextContains("메시")
        compose.onNodeWithTag("closet_slot_bottom").performScrollTo().performClick()
        compose.onNodeWithTag("closet_search_clear").assertDoesNotExist()
        compose.onNodeWithTag("closet_bottom_0").performScrollTo().assertExists()
    }
}

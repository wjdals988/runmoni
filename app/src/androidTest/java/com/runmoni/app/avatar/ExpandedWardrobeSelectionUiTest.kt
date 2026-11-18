package com.runmoni.app.avatar

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.runmoni.app.domain.*
import com.runmoni.app.ui.ClosetDialog
import com.runmoni.app.ui.RunmoniTheme
import com.runmoni.app.ui.RunmoniUiTest
import com.runmoni.app.ui.boundedScrollIntoView
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual closet selection, rendered preview, gender and no-spend checks.
 * Isolated from synchronous bitmap tests so a UI retry repeats only this case.
 * Ripple, actual renderer and product code are retained.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExpandedWardrobeSelectionUiTest {
    @get:Rule val compose = createComposeRule(effectContext = StandardTestDispatcher())
    private fun pixels(image: Bitmap) = IntArray(image.width * image.height).also {
        image.getPixels(it, 0, image.width, 0, 0, image.width, image.height)
    }

    private fun reveal(tag: String): SemanticsNodeInteraction =
        compose.onNodeWithTag(tag).boundedScrollIntoView(compose, tag)

    @Test fun closetSelectsNewFreeItemsAndGenderSwitchPreservesTheHiddenSavedShortsId() {
        val original = Profile(name = "검토 러너", mileage = 4242,
            ownedItems = AvatarCatalogue.defaultOwned + setOf("top:3", "shoes:2", "wrist:2", "headwear:3"),
            avatar = AvatarStyle(top = 3, bottom = 1, shoes = 2, wrist = 2, headwear = 3,
                runningKitColors = RunningKitColors(3, 0, 5)))
        val state = mutableStateOf(AppState(profile = original))
        compose.setContent { RunmoniTheme { ClosetDialog(state.value.profile, RunmoniUiTest.FakeActions(state)) {} } }
        compose.onNodeWithTag("closet_animation").performClick()
        fun preview(): IntArray {
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasContentDescription("나의 러닝 미니미") and
                    hasAnyAncestor(hasTestTag("closet_avatar_preview")), useUnmergedTree = true)
                    .fetchSemanticsNodes().size == 1
            }
            return pixels(compose.onNodeWithTag("closet_avatar_preview").captureToImage().asAndroidBitmap())
        }
        fun pick(slot: String, variant: Int) {
            reveal("closet_slot_$slot").performClick()
            reveal("closet_${slot}_$variant").assertIsEnabled().performClick()
            compose.onNodeWithTag("closet_${slot}_$variant").assertIsSelected()
        }
        var last = preview()
        for ((slot, variant) in listOf("socks" to 1, "socks" to 2, "bottom" to 2, "bottom" to 3)) {
            pick(slot, variant)
            val image = preview()
            assertFalse("Visible closet preview ignored $slot:$variant", last.contentEquals(image))
            last = image
            compose.runOnIdle {
                assertEquals(variant, state.value.profile.avatar.variantFor(slot))
                assertEquals(4242, state.value.profile.mileage)
                assertEquals(0, AvatarCatalogue.price(variant))
                assertTrue("$slot:$variant" in state.value.profile.ownedItems)
                assertEquals(original.avatar.top, state.value.profile.avatar.top)
                assertEquals(original.avatar.shoes, state.value.profile.avatar.shoes)
                assertEquals(original.avatar.wrist, state.value.profile.avatar.wrist)
                assertEquals(original.avatar.headwear, state.value.profile.avatar.headwear)
                assertEquals(original.avatar.runningKitColors.top, state.value.profile.avatar.runningKitColors.top)
                assertEquals(original.avatar.runningKitColors.shoes, state.value.profile.avatar.runningKitColors.shoes)
            }
        }
        compose.onNodeWithTag("closet_gender_female").performClick()
        compose.onNodeWithTag("closet_bottom_2").assertDoesNotExist()
        compose.onNodeWithTag("closet_bottom_3").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(AvatarGender.FEMALE, state.value.profile.avatar.gender)
            assertEquals(3, state.value.profile.avatar.bottom)
            assertEquals(2, state.value.profile.avatar.socks)
            assertTrue(state.value.profile.ownedItems.containsAll(setOf("bottom:2", "bottom:3", "socks:1", "socks:2")))
        }
        reveal("closet_slot_socks").performClick()
        reveal("closet_socks_1").assertIsEnabled()
        reveal("closet_socks_2").assertIsSelected()
        compose.onNodeWithTag("closet_socks_3").assertDoesNotExist()
        compose.onNodeWithTag("closet_gender_male").performClick()
        reveal("closet_slot_bottom").performClick()
        reveal("closet_bottom_3").assertIsSelected()
    }
}

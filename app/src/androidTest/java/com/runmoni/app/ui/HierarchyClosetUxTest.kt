package com.runmoni.app.ui

import android.view.KeyEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** UI callbacks/navigation fixtures, never production Firebase or phone evidence. */
class HierarchyClosetUxTest : RunmoniDesignTestBase() {
    private val crew = Crew("hierarchy-crew", "한강 저녁 크루", "퇴근 후 함께 달려요", "서울", "6:00/km", 3, isOpen = true)
    private val party = Party("hierarchy-party", crew.id, "화요일 한강 5K", PartyKind.LOCAL,
        System.currentTimeMillis() + 3_600_000, "한강공원 입구", capacity = 8, participantCount = 2)

    private fun pressActualBack() {
        // UiDevice.pressBack() reports whether an accessibility content-change
        // event arrives within 1s, not whether the DOWN/UP keys were injected.
        // Check actual key injection here, then the destination at each call site.
        assertTrue("KEYCODE_BACK DOWN/UP injection failed",
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressKeyCode(KeyEvent.KEYCODE_BACK))
        compose.waitForIdle()
    }

    @Test fun publicCrewShowsItsPartiesAndBackReturnsThroughBothLevels() {
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true,
            communityBackend = CommunityBackend.FIREBASE, crews = listOf(crew), parties = listOf(party)))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { ViewportBox(Viewport()) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_crew").performClick()
        compose.onNodeWithTag("create_party").assertDoesNotExist()
        compose.onNodeWithText("둘러보기").performClick()
        compose.onNodeWithTag("crew_${crew.id}").performScrollTo().performClick()
        // Public schedule metadata remains useful before joining the parent crew.
        compose.onNodeWithTag("party_${party.id}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("create_party_${crew.id}").assertDoesNotExist()
        noTextOverflow("crew hierarchy public preview")
        capture("hierarchy-crew-public-fixture")
        captureDevice("hierarchy-crew-public-device")
        compose.onNodeWithTag("party_${party.id}").performClick()
        compose.onNodeWithTag("parent_crew_name").assertTextContains(crew.name)
        compose.onNodeWithTag("join_party_${party.id}").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(party.id), actions.joinedParties) }
        // The ViewModel owns the sequential crew/party membership operation; this
        // fixture checks only that the explicit UI action reaches that operation.
        capture("hierarchy-party-public-fixture")
        captureDevice("hierarchy-party-public-device")
        pressActualBack()
        compose.onNodeWithTag("parent_crew_name").assertDoesNotExist()
        compose.onNodeWithTag("party_${party.id}").performScrollTo().assertIsDisplayed()
        captureDevice("hierarchy-back-to-crew-device")
        pressActualBack()
        compose.onNodeWithTag("crew_${crew.id}").performScrollTo().assertIsDisplayed()
        captureDevice("hierarchy-back-to-list-device")
    }

    @Test fun newPartyCarriesTheSelectedCrewInsteadOfCreatingAnIndependentGroup() {
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true,
            communityBackend = CommunityBackend.FIREBASE, crews = listOf(crew.copy(joined = true))))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { ViewportBox(Viewport()) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_crew").performClick()
        compose.onNodeWithTag("create_party").assertDoesNotExist()
        compose.onNodeWithTag("crew_${crew.id}").performScrollTo().performClick()
        compose.onNodeWithTag("create_party_${crew.id}").performScrollTo().performClick()
        compose.onNodeWithTag("party_title").performScrollTo().performTextInput("퇴근 후 5K")
        compose.onNodeWithTag("party_meeting").performScrollTo().performTextInput("한강공원 입구")
        compose.onNodeWithTag("confirm_create_party").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, actions.createdParties.size)
            assertEquals(crew.id, actions.createdParties.single().crewId)
            assertEquals("퇴근 후 5K", actions.createdParties.single().title)
        }
    }

    @Test fun partyRemovalNeedsConfirmationAndKeepsTheParentAndOtherParties() {
        val other = party.copy(id = "other-party", title = "다음 주 러닝")
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true,
            communityBackend = CommunityBackend.FIREBASE, crews = listOf(crew.copy(joined = true, isOwner = true)),
            parties = listOf(party.copy(joined = true, isOrganizer = true), other)))
        val deleted = mutableListOf<String>()
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun deleteParty(id: String) {
                deleted += id
                state.value = state.value.copy(parties = state.value.parties.filterNot { it.id == id })
            }
        }
        compose.setContent { ViewportBox(Viewport()) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_crew").performClick()
        compose.onNodeWithTag("crew_${crew.id}").performScrollTo().performClick()
        compose.onNodeWithTag("party_${party.id}").performScrollTo().performClick()
        compose.onNodeWithTag("delete_party_${party.id}").performScrollTo().performClick()
        compose.onNodeWithTag("cancel_delete_party").performClick()
        compose.runOnIdle { assertTrue(deleted.isEmpty()); assertEquals(2, state.value.parties.size) }
        compose.onNodeWithTag("delete_party_${party.id}").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_delete_party").performClick()
        compose.runOnIdle {
            assertEquals(listOf(party.id), deleted)
            assertEquals(listOf(other), state.value.parties)
            assertEquals(crew.id, state.value.crews.single().id)
        }
        compose.onNodeWithTag("party_${party.id}").assertDoesNotExist()
        compose.onNodeWithTag("party_${other.id}").performScrollTo().assertIsDisplayed()
        capture("hierarchy-party-removed-fixture")
        captureDevice("hierarchy-party-removed-device")
        // A removed modal must also clear the controller's selectedPartyId;
        // otherwise its parent BackHandler remains disabled after deletion.
        pressActualBack()
        compose.onNodeWithTag("crew_${crew.id}").performScrollTo().assertIsDisplayed()
        captureDevice("hierarchy-back-after-removal-device")
    }
}

/** Run separately with the actual Android window at 320dp and system fontScale=2. */
class HierarchyClosetLargeTextUxTest : RunmoniDesignTestBase() {
    private fun singleLineBaseline(node: SemanticsNodeInteraction, name: String): Float {
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("Exactly one text layout for $name", 1, layouts.size)
        val layout = layouts.single()
        assertEquals("Single line for $name", 1, layout.lineCount)
        assertFalse("No text clipping for $name", layout.hasVisualOverflow)
        return node.fetchSemanticsNode().boundsInRoot.top + layout.firstBaseline
    }

    @Test fun genderAndScrollableCategoriesAllowOneTapFreeSelectionWithoutLosingInventory() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assertTrue("Requires actual system fontScale=2", config.fontScale >= 1.9f)
        assertTrue("Requires actual 320dp window", config.screenWidthDp in 315..325)
        val original = Profile(name = "검토 러너", avatar = AvatarStyle(hair = 2, socks = 2, wrist = 1), mileage = 732,
            ownedItems = AvatarCatalogue.defaultOwned + setOf("hair:2", "wrist:1", "socks:2"))
        val state = mutableStateOf(AppState(profile = original))
        val selections = mutableListOf<Pair<String, Int>>()
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun setAvatarGender(gender: AvatarGender) {
                state.value = state.value.copy(profile = state.value.profile.copy(avatar = state.value.profile.avatar.copy(gender = gender)))
            }
            override fun equipItem(slot: String, variant: Int) {
                selections += slot to variant
                val p = state.value.profile
                state.value = state.value.copy(profile = p.copy(avatar = p.avatar.withItem(slot, variant), ownedItems = p.ownedItems + "$slot:$variant"))
            }
        }
        // No logical-only viewport wrapper: the full-screen Dialog's actual
        // Android window must satisfy the native configuration checks above.
        compose.setContent { RunmoniApp(state.value, actions) }
        // Check the full, unclipped active Home action in the actual 320dp
        // Android window. Partial visibility must not count as a usable CTA.
        compose.runOnIdle { state.value = state.value.copy(activeRun = ActiveRun(id = "native-home-layout-fixture", phase = RunPhase.RUNNING)) }
        compose.onNodeWithTag("home_start_run").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        noTextOverflow("native 320dp / font2 active Home action")
        captureDevice("home-active-native-320-font2-device")
        val cta = compose.onNodeWithTag("home_start_run").getUnclippedBoundsInRoot()
        val nav = compose.onNodeWithTag("nav_home").getUnclippedBoundsInRoot()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "design-review").apply { mkdirs() }
            .resolve("home-action-unclipped-native-320-font2.json")
            .writeText("""{"screenWidthDp":${config.screenWidthDp},"fontScale":${config.fontScale},"ctaTopDp":${cta.top.value},"ctaBottomDp":${cta.bottom.value},"navigationTopDp":${nav.top.value},"unit":"dp; unclipped bounds"}""")
        assertTrue("Full Home CTA must precede bottom navigation: $cta / $nav", cta.bottom <= nav.top)
        compose.runOnIdle { state.value = state.value.copy(activeRun = ActiveRun()) }
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("open_closet").performScrollTo().performClick()
        val preview = compose.onNodeWithTag("closet_avatar_preview").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("closet_gender_female").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
        compose.onNodeWithTag("closet_slot_accessories").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("closet_slot_hydration").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("closet_slot_ear").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("closet_slot_top").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("closet_top_1").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf("top" to 1), selections)
            assertEquals(AvatarGender.FEMALE, state.value.profile.avatar.gender)
            assertEquals(1, state.value.profile.avatar.top)
            assertEquals(original.mileage, state.value.profile.mileage)
            assertEquals(original.ownedItems + "top:1", state.value.profile.ownedItems)
        }
        compose.onNodeWithTag("closet_top_1").assertIsSelected()
        // Ordinary choices show their selected row rather than an equip tutorial.
        compose.onNodeWithTag("closet_artwork_status").assertDoesNotExist()
        compose.onNodeWithTag("closet_top_1").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("closet_avatar_preview").assertIsDisplayed()
        assertEquals(preview, compose.onNodeWithTag("closet_avatar_preview").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("closet_top_1").performScrollTo().assertIsSelected()
        noTextOverflow("native 320dp / font2 gender and free closet")
        capture("closet-hierarchy-native-320-font2-fixture")
        captureDevice("closet-hierarchy-native-320-font2-device")
        compose.onNodeWithTag("closet_gender_male").assertIsDisplayed().performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(original.avatar.copy(top = 1, gender = AvatarGender.MALE), state.value.profile.avatar)
            assertEquals(original.mileage, state.value.profile.mileage)
        }
        compose.waitUntil(30_000) { compose.activity.hasWindowFocus() }
        assertTrue("Native hierarchy Back requires app focus", compose.activity.hasWindowFocus())
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "design-review").apply { mkdirs() }
            .resolve("native-hierarchy-back-focus.txt").writeText("appWindowFocusedBeforeNativeBack=true\n")
        assertTrue("KEYCODE_BACK DOWN/UP injection failed",
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressKeyCode(KeyEvent.KEYCODE_BACK))
        compose.waitForIdle()
        compose.onNodeWithTag("nav_me").assertIsSelected()
        singleLineBaseline(compose.onNodeWithTag("app_wordmark"), "runmoni wordmark")
        val baselines = listOf("home" to "홈", "explore" to "탐색", "run" to "러닝", "crew" to "크루", "me" to "나").map { (key, label) ->
            singleLineBaseline(compose.onNode(hasText(label, substring = false) and hasAnyAncestor(hasTestTag("nav_$key")), useUnmergedTree = true), label)
        }
        val span = baselines.max() - baselines.min()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "design-review").apply { mkdirs() }
            .resolve("navigation-label-baselines-native-320-font2.txt")
            .writeText("screenWidthDp=${config.screenWidthDp} fontScale=${config.fontScale}\nbaselinePixels=$baselines\nspanPixels=$span\n")
        // All five labels share the same font and icon/layout dimensions, so
        // require identical measured baselines; no arbitrary pixel tolerance.
        assertEquals("Bottom navigation label baseline span in physical pixels: $baselines", 0f, span, 0f)
        noTextOverflow("native 320dp / font2 main after closet")
        captureDevice("closet-closed-native-320-font2-device")
        compose.onNodeWithTag("open_closet").performScrollTo().performClick()
        compose.onNodeWithTag("closet_gender_male").assertIsDisplayed().assertIsSelected()
        compose.runOnIdle { assertEquals(1, state.value.profile.avatar.top) }
    }
}

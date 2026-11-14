package com.runmoni.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Page/callback fixtures only; no production membership or chat authorization is exercised. */
class CommunityPageNavigationTest : RunmoniDesignTestBase() {
    private val first = Crew("page-first", "첫 크루", "첫 모임", joined = true)
    private val second = Crew("page-second", "돌아올 크루", "달리기 일정과 대화", joined = true)
    private val party = Party("page-party", second.id, "돌아올 파티", PartyKind.LOCAL,
        System.currentTimeMillis() + 3_600_000,
        meetingLabel = "한강공원 안내 표지 앞에서 모여요. ".repeat(4), joined = true)

    private fun pressPageBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun scrollPosition(tag: String): Float = compose.onNodeWithTag(tag)
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun partyChatBackReturnsToTheSamePartyScrollAndCrewTab() {
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true,
            communityBackend = CommunityBackend.FIREBASE, communityUid = "page-self",
            crews = listOf(first, second), parties = listOf(party)))
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun openChat(roomId: String, title: String) {
                state.value = state.value.copy(openedRoomId = roomId, openedRoomTitle = title)
            }
            override fun closeChat() { state.value = state.value.copy(openedRoomId = "", openedRoomTitle = "") }
        }
        compose.setContent { ViewportBox(Viewport()) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_crew").performClick()
        compose.onNodeWithTag("crew_${second.id}").performScrollTo().performClick()
        compose.onNodeWithTag("nav_crew").assertDoesNotExist()
        compose.onNodeWithTag("party_${party.id}").performScrollTo().performClick()
        compose.onNodeWithTag("crew_detail_0").assertDoesNotExist()
        // Read the last action first so this checks a real scroll round trip
        // even when the chat action originally fits above the fold.
        compose.onNodeWithTag("join_party_${party.id}").performScrollTo()
        compose.onNodeWithTag("open_party_chat").performScrollTo()
        val before = scrollPosition("party_content")
        assertTrue("The fixture must exercise a scrolled party page", before > 0f)
        compose.onNodeWithTag("open_party_chat").performClick()
        compose.onNodeWithTag("chat_dialog").assertIsDisplayed()
        compose.onNodeWithTag("party_detail").assertDoesNotExist()
        compose.runOnIdle { assertEquals("party:${party.id}", state.value.openedRoomId) }
        compose.onNodeWithTag("close_chat").performClick()
        compose.onNodeWithTag("party_detail").assertIsDisplayed()
        compose.onNodeWithText(party.title).assertExists()
        compose.onNodeWithTag("parent_crew_name").assertTextContains(second.name)
        assertEquals("Party reading position changed after chat Back", before, scrollPosition("party_content"), 1f)
        pressPageBack()
        compose.onNodeWithTag("crew_detail_0").assertIsSelected()
        compose.onNodeWithTag("create_party_${second.id}").assertExists()
        compose.onNodeWithTag("create_party_${first.id}").assertDoesNotExist()
        compose.onNodeWithTag("nav_crew").assertDoesNotExist()
        compose.onNodeWithTag("back_to_rooms").performClick()
        compose.onNodeWithTag("nav_crew").assertIsSelected()
    }

    @Test fun crewCreationBackKeepsTheDraftAndSuccessStartsTheNextDraftEmpty() {
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true, crews = listOf(second)))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { ViewportBox(Viewport(320, 2f)) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_crew").performClick()
        compose.onNodeWithTag("create_crew").performScrollTo().performClick()
        compose.onNodeWithTag("rooms_search").assertDoesNotExist()
        compose.onNodeWithTag("nav_crew").assertDoesNotExist()
        compose.onNodeWithTag("crew_name").performScrollTo().performTextInput("작성 중인 크루")
        compose.onNodeWithTag("crew_description").performScrollTo().performTextInput("취소해도 이어 쓸 소개")
        compose.onNodeWithTag("form_dialog_close").performClick()
        compose.onNodeWithTag("rooms_search").assertExists()
        compose.onNodeWithTag("create_crew").performScrollTo().performClick()
        compose.onNodeWithTag("crew_name").performScrollTo().assertTextContains("작성 중인 크루")
        compose.onNodeWithTag("crew_description").performScrollTo().assertTextContains("취소해도 이어 쓸 소개")
        compose.onNodeWithTag("confirm_create_crew").assertIsEnabled().performClick()
        val created = second.copy(id = "created-page-crew", name = "작성 중인 크루", isOwner = true)
        compose.runOnIdle { state.value = state.value.copy(crews = listOf(second, created), lastCreatedCrewId = created.id) }
        compose.onNodeWithTag("create_party_${created.id}").assertExists()
        compose.onNodeWithTag("back_to_rooms").performClick()
        compose.onNodeWithTag("create_crew").performScrollTo().performClick()
        compose.onNodeWithTag("crew_name").performScrollTo().assertIsDisplayed()
        assertEquals("", compose.onNodeWithTag("crew_name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithTag("confirm_create_crew").assertIsNotEnabled()
    }

    @Test fun partyCreationBackKeepsTheDraftInsideItsSelectedCrew() {
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true, crews = listOf(first, second)))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { ViewportBox(Viewport(320, 2f)) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_crew").performClick()
        compose.onNodeWithTag("crew_${second.id}").performScrollTo().performClick()
        compose.onNodeWithTag("create_party_${second.id}").performScrollTo().performClick()
        compose.onNodeWithTag("party_title").performScrollTo().performTextInput("이어 쓸 러닝 일정")
        compose.onNodeWithTag("party_meeting").performScrollTo().performTextInput("다리 아래 출발점")
        compose.onNodeWithTag("form_dialog_close").performClick()
        compose.onNodeWithTag("crew_detail_0").assertIsSelected()
        compose.onNodeWithTag("create_party_${second.id}").performScrollTo().performClick()
        compose.onNodeWithTag("party_title").performScrollTo().assertTextContains("이어 쓸 러닝 일정")
        compose.onNodeWithTag("party_meeting").performScrollTo().assertTextContains("다리 아래 출발점")
        compose.onNodeWithText(second.name).assertExists()
        compose.onNodeWithTag("create_party_${first.id}").assertDoesNotExist()
        compose.onNodeWithTag("crew_detail_0").assertDoesNotExist()
        compose.onNodeWithTag("nav_crew").assertDoesNotExist()
    }

    @Test fun newNavigationEpochAndInitialTupleReplaceSavedSubpagesAndDrafts() {
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true, crews = listOf(first, second)))
        val incomingCrew = mutableStateOf<String?>(second.id)
        val epoch = mutableStateOf(0)
        val reports = mutableListOf<Boolean>()
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { RunmoniTheme { ViewportBox(Viewport()) {
            CommunityRoomsScreen(state.value, actions, incomingCrew.value,
                navigationEpoch = epoch.value, onSubpageChanged = { reports += it })
        } } }
        compose.onNodeWithTag("create_party_${second.id}").performScrollTo().performClick()
        compose.onNodeWithTag("party_title").performScrollTo().performTextInput("새 탐색 전의 초안")
        compose.onNodeWithTag("form_dialog_close").performClick()
        compose.onNodeWithTag("crew_detail_1").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { incomingCrew.value = first.id; epoch.value++ }
        compose.onNodeWithTag("crew_detail_0").assertIsSelected()
        compose.onNodeWithTag("create_party_${first.id}").assertExists()
        compose.onNodeWithTag("create_party_${second.id}").assertDoesNotExist()
        // Initial IDs also identify a new request even if a caller leaves its epoch unchanged.
        compose.runOnIdle { incomingCrew.value = second.id }
        compose.onNodeWithTag("create_party_${second.id}").performScrollTo().performClick()
        assertEquals("", compose.onNodeWithTag("party_title").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithTag("confirm_create_party").assertIsNotEnabled()
        compose.onNodeWithTag("form_dialog_close").performClick()
        compose.runOnIdle { assertTrue(reports.last()) }
        compose.onNodeWithTag("back_to_rooms").performClick()
        compose.onNodeWithTag("rooms_search").assertExists()
        compose.runOnIdle { assertFalse(reports.last()) }
    }

    @Test fun profileBackReturnsToTheSamePartyWithoutJoining() {
        val publicCrew = second.copy(joined = false)
        val publicParty = party.copy(joined = false)
        val state = mutableStateOf(AppState(serverConnected = true, communityBackend = CommunityBackend.FIREBASE,
            crews = listOf(publicCrew), parties = listOf(publicParty)))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { ViewportBox(Viewport()) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_crew").performClick()
        compose.onNodeWithTag("crew_${publicCrew.id}").performScrollTo().performClick()
        compose.onNodeWithTag("party_${publicParty.id}").performScrollTo().performClick()
        compose.onNodeWithTag("join_party_${publicParty.id}").performScrollTo().performClick()
        compose.onNodeWithTag("profile_name").assertExists()
        compose.onNodeWithTag("party_detail").assertDoesNotExist()
        compose.onNodeWithTag("close_profile").performClick()
        compose.onNodeWithTag("party_detail").assertIsDisplayed()
        compose.onNodeWithTag("parent_crew_name").assertTextContains(publicCrew.name)
        compose.onNodeWithTag("join_party_${publicParty.id}").assertExists()
        compose.runOnIdle { assertFalse(state.value.signedIn); assertTrue(actions.joinedCrews.isEmpty()); assertTrue(actions.joinedParties.isEmpty()) }
    }
}

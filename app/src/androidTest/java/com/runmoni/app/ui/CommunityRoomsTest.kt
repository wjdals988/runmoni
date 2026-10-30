package com.runmoni.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class CommunityRoomsTest : RunmoniDesignTestBase() {
    private val crew = Crew("open-crew", "한강 저녁 러닝", "퇴근 후 함께 달려요", "서울", "6:00/km", 3, isOpen = true)
    private val party = Party("open-party", "party-backing", "주말 원격 5K", PartyKind.REMOTE,
        System.currentTimeMillis() + 3600000, distanceKm = 5.0, capacity = 10, participantCount = 2)

    @Test fun browseSearchAndEnterCrewWithoutInviteCode() {
        val state = mutableStateOf(AppState(serverConnected = true, crews = listOf(crew), parties = listOf(party)))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { ViewportBox(Viewport()) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_crew").performClick()
        compose.onNodeWithText(crew.name).performScrollTo().assertIsDisplayed()
        capture("rooms-list-360-100")
        compose.onNodeWithTag("rooms_search").performScrollTo().performTextInput("부산")
        compose.onNodeWithText("검색한 크루 방이 없어요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("rooms_search").performScrollTo().performTextClearance()
        compose.onNodeWithTag("crew_${crew.id}").performScrollTo().performClick()
        compose.onNodeWithTag("join_crew_${crew.id}").performScrollTo().performClick()
        compose.onNodeWithTag("invite_code").assertDoesNotExist()
        compose.onNodeWithTag("display_name").performTextClearance()
        compose.onNodeWithTag("display_name").performTextInput("방문 러너")
        compose.onNodeWithTag("adult_checkbox").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_sign_in").performClick()
        compose.runOnIdle { assertEquals("" to "방문 러너", actions.login); state.value = state.value.copy(signedIn = true, communityGuest = true) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(crew.id), actions.joinedCrews); state.value = state.value.copy(crews = listOf(crew.copy(joined = true))) }
        compose.onNodeWithText("공지 · 채팅").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("create_party_${crew.id}").assertIsEnabled()
    }

    @Test fun failedCrewCreationKeepsDraftAndSuccessOpensCreatedRoom() {
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true))
        var submitted: NewCrew? = null
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun createCrew(input: NewCrew) { submitted = input; state.value = state.value.copy(busy = true) }
        }
        compose.setContent { ViewportBox(Viewport()) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_crew").performClick()
        compose.onNodeWithTag("create_crew").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_create_crew").assertIsNotEnabled()
        compose.onNodeWithTag("crew_name").performTextInput("새로운 러닝 방")
        compose.onNodeWithTag("crew_description").performTextInput("함께 달리는 공개 모임")
        compose.onNodeWithTag("confirm_create_crew").performClick().assertIsNotEnabled()
        compose.runOnIdle { assertEquals("새로운 러닝 방", submitted?.name); state.value = state.value.copy(busy = false, error = "테스트 연결 실패") }
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithTag("crew_name").assertTextContains("새로운 러닝 방")
        compose.onNodeWithTag("confirm_create_crew").assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(crews = listOf(crew.copy(id = "created", name = submitted!!.name, joined = true, isOwner = true)), lastCreatedCrewId = "created") }
        compose.onNodeWithTag("confirm_create_crew").assertDoesNotExist()
        compose.onNodeWithText("내가 만든 방").assertExists()
        compose.onNodeWithTag("create_party_created").assertIsEnabled()
    }

    @Test fun partyCanBeCreatedWithoutCrewAndJoinedRoomOffersChat() {
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { ViewportBox(Viewport()) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_crew").performClick()
        compose.onNodeWithTag("create_party").performScrollTo().performClick()
        compose.onNodeWithTag("party_title").performTextInput("퇴근 후 원격 러닝")
        compose.onNodeWithTag("party_remote").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_create_party").performClick()
        compose.runOnIdle {
            assertNull(actions.createdParties.single().crewId)
            assertEquals(PartyKind.REMOTE, actions.createdParties.single().kind)
            state.value = state.value.copy(parties = listOf(party.copy(joined = true, isOrganizer = true)), lastCreatedPartyId = party.id)
        }
        compose.onNodeWithTag("confirm_create_party").assertDoesNotExist()
        compose.onNodeWithTag("open_party_chat").performScrollTo().assertIsDisplayed()
        capture("rooms-party-dialog-360-100")
    }

    @Test fun roomListDetailsAndCreationStayUsableAt320WithDoubleText() {
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true, crews = listOf(crew.copy(joined = true))))
        compose.setContent { ViewportBox(Viewport(320, 2f)) { RunmoniApp(state.value, RunmoniUiTest.FakeActions(state)) } }
        compose.onNodeWithTag("nav_crew").performClick()
        compose.onNodeWithTag("create_crew").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("create_party").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        noTextOverflow("rooms list 320 / 2")
        capture("rooms-list-320-200")
        compose.onNodeWithTag("crew_${crew.id}").performScrollTo().performClick()
        compose.onNodeWithTag("create_party_${crew.id}").performScrollTo().assertIsEnabled()
        noTextOverflow("crew room 320 / 2")
        capture("rooms-crew-detail-320-200")
        compose.onNodeWithTag("back_to_rooms").performScrollTo().performClick()
        compose.onNodeWithTag("create_crew").performScrollTo().performClick()
        compose.onNodeWithTag("crew_pace").performScrollTo().assertIsDisplayed()
        noTextOverflow("crew creation 320 / 2")
        capture("rooms-create-crew-320-200")
    }
}

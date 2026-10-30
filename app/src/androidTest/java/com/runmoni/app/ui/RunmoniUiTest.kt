package com.runmoni.app.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.runmoni.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** UI contract fixtures only: these tests do not simulate successful real GPS or audio capture. */
class RunmoniUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unavailableCommunityIsNotReportedAsAnEmptyServerResult() {
        val state = mutableStateOf(AppState())
        compose.setContent { RunmoniApp(state.value, FakeActions(state)) }
        compose.onNodeWithTag("nav_explore").performClick()
        compose.onNodeWithText("함께 달리기는 연결을 준비 중이에요").assertExists()
        compose.onNodeWithText("표시할 러닝 파티가 없어요").assertDoesNotExist()
        compose.onNodeWithTag("explore_layer_1").performClick()
        compose.onNodeWithText("아직 만날 크루가 없어요").assertDoesNotExist()

        // A real successful anonymous fetch can be empty. Authentication is only
        // requested when creating or joining a public room.
        compose.runOnIdle { state.value = state.value.copy(serverConnected = true) }
        compose.onNodeWithText("아직 만날 크루가 없어요").assertExists()
        compose.runOnIdle { state.value = state.value.copy(signedIn = true) }
        compose.onNodeWithText("아직 만날 크루가 없어요").assertExists()
        compose.onNodeWithTag("explore_layer_0").performClick()
        compose.onNodeWithText("표시할 러닝 파티가 없어요").assertExists()
    }

    @Test fun fiveTabsOpenTheirActualScreens() {
        val state = mutableStateOf(AppState())
        compose.setContent { RunmoniApp(state.value, FakeActions(state)) }
        val screens = listOf(
            "nav_explore" to "모임 검색",
            "nav_run" to "오늘의 러닝",
            "nav_crew" to "함께라서, 더 멀리.",
            "nav_me" to "미니미 꾸미기",
            "nav_home" to "오늘도,\n나답게 달려요.",
        )
        screens.forEach { (tag, title) ->
            compose.onNodeWithTag(tag).performClick().assertIsSelected()
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        }
        // Connection diagnostics live in settings rather than every tab header.
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("backend_status").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("기기 기록 모드").assertIsDisplayed()
    }

    @Test fun offlineRunActionsRequireFinishConfirmationAndShowSavedRecord() {
        val state = mutableStateOf(AppState())
        val actions = FakeActions(state)
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("nav_run").performClick()
        compose.onNodeWithTag("start_run").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, actions.starts) }
        compose.onNodeWithTag("pause_run").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, actions.pauses); assertEquals(RunPhase.PAUSED, state.value.activeRun.phase) }
        compose.onNodeWithTag("resume_run").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, actions.resumes) }
        compose.onNodeWithTag("finish_run").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(0, actions.finishes) }
        compose.onNodeWithTag("confirm_finish_run").performClick()
        compose.runOnIdle { assertEquals(1, actions.finishes); assertEquals(1, state.value.records.size) }
        compose.onNodeWithText("나의 러닝 기록").assertIsDisplayed()
        compose.onNodeWithTag("export_run").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("ui-contract-run"), actions.exports) }
    }

    @Test fun recordedRunIsShownAndHistoricalPeriodCanMoveBack() {
        val stamp = System.currentTimeMillis()
        val fixture = RunRecord("ui-record-fixture", stamp, stamp, distanceMeters = 3210.0, activeDurationMillis = 1_200_000)
        val state = mutableStateOf(AppState(records = listOf(fixture)))
        compose.setContent { RunmoniApp(state.value, FakeActions(state)) }
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("record_ui-record-fixture").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("history_period_0").performScrollTo().performClick()
        compose.onNodeWithTag("history_previous").performScrollTo().performClick()
        compose.onNodeWithText("이 기간에는 아직 기록이 없어요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("history_next").performScrollTo().performClick()
        compose.onNodeWithTag("record_ui-record-fixture").performScrollTo().assertIsDisplayed()
    }

    @Test fun precisePilotLocationRequiresDisclosureBeforeEnabling() {
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true))
        val actions = FakeActions(state)
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("nav_explore").performClick()
        compose.onNodeWithText("러너").performScrollTo().performClick()
        compose.onNodeWithTag("share_location").performScrollTo().performClick()
        compose.onNodeWithText("정밀 좌표", substring = true).assertIsDisplayed()
        compose.onNodeWithText("인터넷 전체에 공개되는 지도는 아니에요", substring = true).assertIsDisplayed()
        compose.onNodeWithText("15초마다", substring = true).assertIsDisplayed()
        compose.onNodeWithText("45초가 지나면 위치가 만료돼요", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertTrue(actions.locationChanges.isEmpty()) }
        compose.onNodeWithTag("confirm_share_location").performClick()
        compose.onNodeWithTag("share_location").assertIsOn()
        compose.runOnIdle { assertEquals(listOf(true), actions.locationChanges) }
    }

    @Test fun locationConsentCanBeDeclinedAndExistingSharingCanStopImmediately() {
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true))
        val actions = FakeActions(state)
        compose.setContent { RunmoniTheme { LocationSharingToggle(state.value, actions) } }
        compose.onNodeWithTag("share_location").performClick()
        compose.onNodeWithTag("cancel_share_location").performClick()
        compose.onNodeWithTag("share_location").assertIsOff()
        compose.runOnIdle { assertTrue(actions.locationChanges.isEmpty()) }
        compose.onNodeWithTag("share_location").performClick()
        compose.onNodeWithTag("confirm_share_location").performClick()
        compose.onNodeWithTag("share_location").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("confirm_share_location").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(true, false), actions.locationChanges) }
    }

    @Test fun signedOutUserCannotEnablePreciseSharing() {
        val state = mutableStateOf(AppState())
        val actions = FakeActions(state)
        compose.setContent { RunmoniTheme { LocationSharingToggle(state.value, actions) } }
        compose.onNodeWithTag("share_location").assertIsNotEnabled().assertIsOff()
        compose.onNodeWithTag("confirm_share_location").assertDoesNotExist()
        compose.runOnIdle { assertTrue(actions.locationChanges.isEmpty()) }
        compose.runOnIdle { state.value = state.value.copy(signedIn = true) }
        compose.onNodeWithTag("share_location").performClick()
        compose.onNodeWithTag("confirm_share_location").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(signedIn = false) }
        compose.onNodeWithTag("share_location").assertIsNotEnabled().assertIsOff()
        compose.onNodeWithTag("confirm_share_location").assertDoesNotExist()
        compose.runOnIdle { assertTrue(actions.locationChanges.isEmpty()) }
    }

    @Test fun storedRecordDetailsUseActualTotalsRefreshAfterSyncAndRequireDeleteConfirmation() {
        val stamp = System.currentTimeMillis()
        val fixture = RunRecord("ui-real-totals", stamp, stamp, distanceMeters = 3210.0, activeDurationMillis = 1_200_000)
        val state = mutableStateOf(AppState(records = listOf(fixture)))
        val actions = FakeActions(state)
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("record_ui-real-totals").performScrollTo().performClick()
        compose.onNodeWithTag("record_distance").assertTextEquals("3.21 km")
        compose.onNodeWithTag("record_duration_pace").assertTextEquals("러닝 시간 20:00 · 평균 6′13″ /km")
        compose.runOnIdle { state.value = state.value.copy(records = listOf(fixture.copy(synced = true))) }
        compose.onNodeWithText("서버 동기화됨").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("export_run").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("ui-real-totals"), actions.exports) }
        compose.onNodeWithTag("delete_run").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(actions.deletions.isEmpty()) }
        compose.onNodeWithTag("confirm_delete_run").performClick()
        compose.runOnIdle { assertEquals(listOf("ui-real-totals"), actions.deletions); assertTrue(state.value.records.isEmpty()) }
    }

    @Test fun malformedFutureAndExpiredPresenceDoNotBecomeVisibleRunners() {
        val now = System.currentTimeMillis()
        val state = mutableStateOf(AppState(liveRunners = listOf(
            LiveRunner("fresh", "실제 공유 러너", 37.5, 127.0, now),
            LiveRunner("expired", "만료 러너", 37.5, 127.0, now - 60_000),
            LiveRunner("invalid", "유효하지 않은 러너", Double.NaN, 127.0, now),
            LiveRunner("future", "미래 갱신 러너", 37.5, 127.0, now + 60_000),
        )))
        compose.setContent { RunmoniApp(state.value, FakeActions(state)) }
        compose.onNodeWithTag("nav_explore").performClick()
        compose.onNodeWithText("러너").performScrollTo().performClick()
        compose.onNodeWithText("위치를 공유한 러너 1명").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("실제 공유 러너").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("만료 러너").assertDoesNotExist()
        compose.onNodeWithText("유효하지 않은 러너").assertDoesNotExist()
        compose.onNodeWithText("미래 갱신 러너").assertDoesNotExist()
    }

    @Test fun partyCapacityMatchesTwentyPersonPilotLimit() {
        val state = mutableStateOf(AppState())
        val actions = FakeActions(state)
        compose.setContent { RunmoniTheme { NewPartyDialog("ui-crew", actions) {} } }
        compose.onNodeWithTag("party_title").performTextInput("용량 계약 파티")
        compose.onNodeWithTag("party_meeting").performScrollTo().performTextInput("출발 지점")
        compose.onNodeWithTag("party_capacity").performScrollTo().performTextClearance()
        compose.onNodeWithTag("party_capacity").performTextInput("21")
        compose.onNodeWithTag("confirm_create_party").assertIsNotEnabled()
        compose.onNodeWithTag("party_capacity_support", useUnmergedTree = true).performScrollTo().assertTextEquals("정원은 2~20명으로 입력해 주세요")
        compose.onNodeWithTag("party_capacity").performTextClearance()
        compose.onNodeWithTag("party_capacity").performTextInput("1")
        compose.onNodeWithTag("confirm_create_party").assertIsNotEnabled()
        compose.onNodeWithTag("party_capacity").performTextClearance()
        compose.onNodeWithTag("party_capacity").performTextInput("20")
        compose.onNodeWithTag("confirm_create_party").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(20, actions.createdParties.single().capacity); assertEquals("ui-crew", actions.createdParties.single().crewId) }
    }

    @Test fun partyOrganizerUsesConfirmedCancellationInsteadOfLeaving() {
        val fixture = Party("ui-party", "ui-crew", "주최 파티", PartyKind.LOCAL, System.currentTimeMillis() + 3_600_000, meetingLabel = "출발점", joined = true, isOrganizer = true)
        val state = mutableStateOf(AppState(signedIn = true, parties = listOf(fixture)))
        val actions = FakeActions(state)
        compose.setContent { RunmoniTheme { PartyDialog(state.value, fixture, actions, onDismiss = {}) } }
        compose.onNodeWithTag("join_party_ui-party").assertDoesNotExist()
        compose.onNodeWithTag("cancel_party_ui-party").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(actions.cancelledParties.isEmpty()); assertTrue(actions.leftParties.isEmpty()) }
        compose.onNodeWithTag("confirm_cancel_party").performClick()
        compose.runOnIdle { assertEquals(listOf("ui-party"), actions.cancelledParties) }
    }

    @Test fun crewOwnerCannotLeaveOrAssignDjToOwnerButCanManageOtherMembers() {
        val fixture = Crew("ui-crew", "내 크루", "권한 계약", joined = true, isOwner = true)
        val members = listOf(CrewMember("owner", "크루장 러너", isOwner = true), CrewMember("member", "다른 러너"))
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true, crews = listOf(fixture), crewMembers = mapOf(fixture.id to members)))
        val actions = FakeActions(state)
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("nav_crew").performClick()
        compose.onNodeWithTag("crew_ui-crew").performScrollTo().performClick()
        compose.onNodeWithTag("leave_crew_ui-crew").assertDoesNotExist()
        compose.onNodeWithTag("create_party_ui-crew").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("manage_crew_members").performScrollTo().performClick()
        compose.onNodeWithTag("assign_dj_owner").assertDoesNotExist()
        compose.onNodeWithTag("assign_dj_member").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(Triple("ui-crew", "member", true)), actions.djChanges) }
        compose.onNodeWithTag("assign_dj_member").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Triple("ui-crew", "member", false), actions.djChanges.last()); assertTrue(actions.leftCrews.isEmpty()) }
    }

    @Test fun djMusicChoicesDelegateAllSixAppPackagesAndCloseAfterRoleLoss() {
        val state = mutableStateOf(AppState(audio = AudioUiState(connected = true, canDj = true, roomId = "crew:ui-crew")))
        val actions = FakeActions(state)
        compose.setContent { RunmoniTheme { ScreenContent { AudioPanel(state.value.audio, actions, "crew:ui-crew") } } }
        compose.onNodeWithTag("share_youtube_music").performScrollTo().performClick()
        compose.onNodeWithTag("share_samsung_music").performScrollTo().performClick()
        listOf("com.spotify.music", "com.iloen.melon", "com.ktmusic.geniemusic", "skplanet.musicmate").forEach { packageName ->
            compose.onNodeWithTag("other_music_apps").performScrollTo().performClick()
            compose.onNodeWithTag("share_music_$packageName").performClick()
        }
        compose.runOnIdle { assertEquals(listOf("com.google.android.apps.youtube.music", "com.sec.android.app.music", "com.spotify.music", "com.iloen.melon", "com.ktmusic.geniemusic", "skplanet.musicmate"), actions.musicPackages) }
        compose.onNodeWithTag("other_music_apps").performScrollTo().performClick()
        compose.runOnIdle { state.value = state.value.copy(audio = state.value.audio.copy(canDj = false)) }
        compose.onNodeWithText("공유할 음악 앱").assertDoesNotExist()
        compose.onNodeWithTag("share_youtube_music").assertDoesNotExist()
        compose.onNodeWithTag("share_samsung_music").assertDoesNotExist()
    }

    @Test fun closetDisplaysPricesAndDoesNotAllowInsufficientBalance() {
        val state = mutableStateOf(AppState(profile = Profile(mileage = 1500)))
        val actions = FakeActions(state)
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("open_closet").performClick()
        compose.onNodeWithTag("closet_top_1").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("closet_top_2").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("closet_top_3").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("closet_top_1").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("top:1"), actions.purchases); assertEquals(1000, state.value.profile.mileage) }
        compose.onNodeWithTag("closet_top_1").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, state.value.profile.avatar.top) }
    }

    @Test fun guestConnectRequiresAdultAttestationWithoutInvite() {
        val state = mutableStateOf(AppState())
        val actions = FakeActions(state)
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("open_sign_in").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_sign_in").assertIsNotEnabled()
        compose.onNodeWithTag("invite_code").assertDoesNotExist()
        compose.onNodeWithTag("display_name").performTextClearance()
        compose.onNodeWithTag("display_name").performTextInput("테스트 러너")
        compose.onNodeWithTag("confirm_sign_in").assertIsNotEnabled()
        compose.onNodeWithTag("adult_checkbox").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_sign_in").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("" to "테스트 러너", actions.login) }
    }

    @Test fun missingCloudConfigurationKeepsGoogleLoginDisabled() {
        val state = mutableStateOf(AppState())
        val actions = FakeActions(state)
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("google_sign_in").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(false, state.value.signedIn); assertEquals(false, state.value.publicLocation) }
    }

    @Test fun cloudAccountDoesNotGrantPilotLocationAccess() {
        val state = mutableStateOf(AppState(cloud = CloudAccountState(configured = true, uid = "cloud-a", accountLabel = "계정 A")))
        val actions = FakeActions(state)
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("share_location").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(false, state.value.signedIn); assertEquals(false, state.value.publicLocation); assertEquals(0, actions.locationChanges.size) }
    }

    @Test fun cloudBackupNeedsFreshConfirmationAfterAccountSwitch() {
        val record = RunRecord("cloud-run-ui", 1000, 2000, distanceMeters = 100.0, activeDurationMillis = 1000)
        val state = mutableStateOf(AppState(records = listOf(record), cloud = CloudAccountState(configured = true, uid = "cloud-a", accountLabel = "계정 A")))
        val actions = FakeActions(state)
        compose.setContent { RunmoniTheme { RecordDialog(record, actions, state.value.cloud) {} } }
        compose.onNodeWithTag("backup_cloud_run").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_cloud_backup").assertExists()
        compose.runOnIdle { state.value = state.value.copy(cloud = CloudAccountState(configured = true, uid = "cloud-b", accountLabel = "계정 B")) }
        compose.onNodeWithTag("confirm_cloud_backup").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, actions.cloudBackups.size) }
        compose.onNodeWithTag("backup_cloud_run").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_cloud_backup").performClick()
        compose.runOnIdle {
            assertEquals(listOf("cloud-run-ui" to "cloud-b"), actions.cloudBackups)
            assertEquals(false, state.value.records.first().synced)
            assertEquals(0, state.value.profile.mileage)
        }
    }

    internal class FakeActions(private val state: MutableState<AppState>) : AppActions {
        var starts = 0; var pauses = 0; var resumes = 0; var finishes = 0
        var locationSettings = 0; var permissionSettings = 0
        val exports = mutableListOf<String>()
        val deletions = mutableListOf<String>()
        val purchases = mutableListOf<String>()
        val locationChanges = mutableListOf<Boolean>()
        val createdParties = mutableListOf<NewParty>()
        val cancelledParties = mutableListOf<String>()
        val leftParties = mutableListOf<String>()
        val leftCrews = mutableListOf<String>()
        val djChanges = mutableListOf<Triple<String, String, Boolean>>()
        val musicPackages = mutableListOf<String>()
        var login: Pair<String, String>? = null
        val cloudBackups = mutableListOf<Pair<String, String>>()
        val joinedCrews = mutableListOf<String>()
        val joinedParties = mutableListOf<String>()
        override fun startRun() { starts++; state.value = state.value.copy(activeRun = ActiveRun(id = "ui-contract-run", phase = RunPhase.RUNNING, startedAtMillis = System.currentTimeMillis(), gpsStatus = "UI 상태 계약 테스트")) }
        override fun pauseRun() { pauses++; state.value = state.value.copy(activeRun = state.value.activeRun.copy(phase = RunPhase.PAUSED)) }
        override fun resumeRun() { resumes++; state.value = state.value.copy(activeRun = state.value.activeRun.copy(phase = RunPhase.RUNNING)) }
        override fun finishRun() { finishes++; val run = state.value.activeRun; state.value = state.value.copy(activeRun = ActiveRun(), records = listOf(RunRecord(run.id, run.startedAtMillis, System.currentTimeMillis(), distanceMeters = run.distanceMeters, activeDurationMillis = run.activeDurationMillis))) }
        override fun recoverRun() = Unit
        override fun openLocationSettings() { locationSettings++ }
        override fun openAppPermissionSettings() { permissionSettings++ }
        override fun dismissError() { state.value = state.value.copy(error = null) }
        override fun setAutoPause(enabled: Boolean) { state.value = state.value.copy(autoPause = enabled) }
        override fun setVoiceGuidance(enabled: Boolean) { state.value = state.value.copy(voiceGuidance = enabled) }
        override fun setPublicLocation(enabled: Boolean) { locationChanges += enabled; state.value = state.value.copy(publicLocation = enabled) }
        override fun updateProfile(name: String) { state.value = state.value.copy(profile = state.value.profile.copy(name = name)) }
        override fun equipItem(slot: String, variant: Int) { state.value = state.value.copy(profile = state.value.profile.copy(avatar = state.value.profile.avatar.withItem(slot, variant))) }
        override fun buyItem(slot: String, variant: Int) { val price = AvatarCatalogue.price(variant); purchases += "$slot:$variant"; val profile = state.value.profile; state.value = state.value.copy(profile = profile.copy(mileage = profile.mileage - price, ownedItems = profile.ownedItems + "$slot:$variant")) }
        override fun deleteRun(id: String) { deletions += id; state.value = state.value.copy(records = state.value.records.filterNot { it.id == id }) }
        override fun exportRun(id: String) { exports += id }
        override fun configureServer(url: String) = Unit
        override fun signIn(inviteCode: String, name: String) { login = inviteCode to name }
        override fun connectCommunity(name: String) { login = "" to name }
        override fun signOut() = Unit
        override fun signInGoogle() = Unit
        override fun signOutGoogle() = Unit
        override fun backupCloudRun(runId: String, expectedUid: String) { cloudBackups += runId to expectedUid }
        override fun refreshCloudSummaries(expectedUid: String) = Unit
        override fun deleteCloudSummary(runId: String, expectedUid: String) = Unit
        override fun refreshCommunity() = Unit
        override fun createCrew(input: NewCrew) = Unit
        override fun joinCrew(id: String) { joinedCrews += id }
        override fun leaveCrew(id: String) { leftCrews += id }
        override fun setCrewDj(crewId: String, memberId: String, enabled: Boolean) { djChanges += Triple(crewId, memberId, enabled); state.value = state.value.copy(crewMembers = state.value.crewMembers + (crewId to state.value.crewMembers[crewId].orEmpty().map { if (it.id == memberId) it.copy(isDj = enabled) else it })) }
        override fun createParty(input: NewParty) { createdParties += input }
        override fun joinParty(id: String) { joinedParties += id }
        override fun leaveParty(id: String) { leftParties += id }
        override fun cancelParty(id: String) { cancelledParties += id; state.value = state.value.copy(parties = state.value.parties.map { if (it.id == id) it.copy(cancelled = true) else it }) }
        override fun openChat(roomId: String, title: String) = Unit
        override fun closeChat() = Unit
        override fun sendMessage(body: String, notice: Boolean) = Unit
        override fun connectVoice(roomId: String) = Unit
        override fun disconnectVoice() = Unit
        override fun setMicrophone(enabled: Boolean) = Unit
        override fun setVoiceVolume(value: Float) = Unit
        override fun setMusicVolume(value: Float) = Unit
        override fun selectMusicFile() = Unit
        override fun shareExternalMusic(packageName: String) { musicPackages += packageName }
        override fun stopMusic() = Unit
    }
}

package com.runmoni.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Offline ComponentActivity fixtures; observations below are injected, never server/auth results. */
class UnifiedProfileUiTest : RunmoniDesignTestBase() {
    private fun check(name: String, uid: String, availability: NicknameAvailability = NicknameAvailability.AVAILABLE) =
        NicknameCheck(NicknamePolicy.normalize(name), NicknamePolicy.key(name), uid, availability)

    private fun online(name: String = "첫 러너") = AppState(profile = Profile(name = name),
        communityBackend = CommunityBackend.FIREBASE, signedIn = true, communityUid = "profile-a",
        unifiedProfile = UnifiedProfileUi(check = check(name, "profile-a")))

    @Test fun onlineSaveRequiresMatchingNameUidAndAnAvailableOrOwnedObservation() {
        val state = mutableStateOf(online().copy(unifiedProfile = UnifiedProfileUi()))
        val checks = mutableListOf<String>()
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun checkNickname(name: String) { checks += name }
        }
        compose.setContent { RunmoniTheme { UnifiedProfileDialog(state.value, actions) {} } }
        compose.onNodeWithTag("save_profile").assertIsNotEnabled()
        compose.onNodeWithTag("check_nickname").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("첫 러너"), checks)
            state.value = state.value.copy(unifiedProfile = UnifiedProfileUi(check = check("첫 러너", "profile-a")))
        }
        compose.onNodeWithTag("save_profile").assertIsEnabled()
        compose.onNodeWithTag("profile_name").performScrollTo().performTextClearance()
        compose.onNodeWithTag("profile_name").performTextInput("둘째 러너")
        compose.onNodeWithTag("save_profile").assertIsNotEnabled()
        compose.onNodeWithTag("nickname_result").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(unifiedProfile = UnifiedProfileUi(check = check("둘째 러너", "profile-b")))
        }
        compose.onNodeWithTag("save_profile").assertIsNotEnabled()
        compose.onNodeWithTag("nickname_result").assertDoesNotExist()
        for (availability in listOf(NicknameAvailability.TAKEN, NicknameAvailability.UNVERIFIED)) {
            compose.runOnIdle {
                state.value = state.value.copy(unifiedProfile = UnifiedProfileUi(check = check("둘째 러너", "profile-a", availability)))
            }
            compose.onNodeWithTag("save_profile").assertIsNotEnabled()
        }
        compose.runOnIdle {
            state.value = state.value.copy(unifiedProfile = UnifiedProfileUi(check = check("둘째 러너", "profile-a", NicknameAvailability.OWNED)))
        }
        compose.onNodeWithTag("save_profile").assertIsEnabled()
    }

    @Test fun changingTheOpenedCommunityUidBlocksSavingButKeepsCloseAvailable() {
        val state = mutableStateOf(online())
        val visible = mutableStateOf(true)
        val saves = mutableListOf<Pair<String, String>>()
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun saveUnifiedProfile(name: String, expectedUid: String, expectedBackend: CommunityBackend) {
                assertEquals(state.value.communityBackend, expectedBackend)
                saveUnifiedProfile(name, expectedUid)
            }
            override fun saveUnifiedProfile(name: String, expectedUid: String) { saves += name to expectedUid }
        }
        compose.setContent { RunmoniTheme { if (visible.value) UnifiedProfileDialog(state.value, actions) { visible.value = false } } }
        compose.onNodeWithTag("save_profile").assertIsEnabled()
        compose.runOnIdle {
            state.value = state.value.copy(communityUid = "profile-b",
                unifiedProfile = UnifiedProfileUi(check = check("첫 러너", "profile-b")))
        }
        // Even a matching observation for B cannot authorize the editor opened for A.
        compose.onNodeWithTag("save_profile").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertTrue(saves.isEmpty()); assertTrue(visible.value) }
        compose.onNodeWithTag("close_profile").assertIsDisplayed().performClick()
        compose.onNodeWithTag("profile_name").assertDoesNotExist()
        compose.runOnIdle { assertFalse(visible.value); assertTrue(saves.isEmpty()) }
    }

    @Test fun onlineSavePassesExpectedUidAndClosesOnlyAfterTheSaveSerialAcknowledgement() {
        val state = mutableStateOf(online().let { it.copy(unifiedProfile = it.unifiedProfile.copy(savedSerial = 8)) })
        val visible = mutableStateOf(true)
        val saves = mutableListOf<Pair<String, String>>()
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun saveUnifiedProfile(name: String, expectedUid: String, expectedBackend: CommunityBackend) {
                assertEquals(state.value.communityBackend, expectedBackend)
                saveUnifiedProfile(name, expectedUid)
            }
            override fun saveUnifiedProfile(name: String, expectedUid: String) { saves += name to expectedUid }
        }
        compose.setContent { RunmoniTheme { if (visible.value) UnifiedProfileDialog(state.value, actions) { visible.value = false } } }
        compose.onNodeWithTag("save_profile").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf("첫 러너" to "profile-a"), saves)
            assertTrue(visible.value)
            state.value = state.value.copy(unifiedProfile = state.value.unifiedProfile.copy(saving = true))
        }
        compose.onNodeWithTag("save_profile").assertIsNotEnabled()
        compose.runOnIdle {
            state.value = state.value.copy(unifiedProfile = state.value.unifiedProfile.copy(saving = false, error = "검토용 저장 실패"))
        }
        compose.onNodeWithTag("profile_error").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue(visible.value) }
        compose.runOnIdle {
            state.value = state.value.copy(profile = state.value.profile.copy(name = "관측 이름"))
        }
        compose.onNodeWithTag("close_profile").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(visible.value)
            state.value = state.value.copy(unifiedProfile = state.value.unifiedProfile.copy(error = null, savedSerial = 9))
        }
        compose.onNodeWithTag("profile_name").assertDoesNotExist()
        compose.runOnIdle { assertFalse(visible.value); assertEquals(1, saves.size) }
    }

    @Test fun guestLocalSaveUsesAnEmptyCommunityUidAndPreservesWalletAndBackupAccount() {
        val original = Profile(name = "기기 러너", mileage = 1234)
        val backup = CloudAccountState(configured = true, uid = "independent-backup", accountLabel = "요약 백업")
        val state = mutableStateOf(AppState(profile = original, cloud = backup, communityBackend = CommunityBackend.FIREBASE))
        val visible = mutableStateOf(true)
        val saves = mutableListOf<Pair<String, String>>()
        var checks = 0
        var connections = 0
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun checkNickname(name: String) { checks++ }
            override fun connectCommunity(name: String) { connections++ }
            override fun saveUnifiedProfile(name: String, expectedUid: String, expectedBackend: CommunityBackend) {
                assertEquals(state.value.communityBackend, expectedBackend)
                saveUnifiedProfile(name, expectedUid)
            }
            override fun saveUnifiedProfile(name: String, expectedUid: String) {
                saves += name to expectedUid
                state.value = state.value.copy(profile = state.value.profile.copy(name = NicknamePolicy.normalize(name)),
                    unifiedProfile = state.value.unifiedProfile.copy(savedSerial = state.value.unifiedProfile.savedSerial + 1))
            }
        }
        compose.setContent { RunmoniTheme { if (visible.value) UnifiedProfileDialog(state.value, actions) { visible.value = false } } }
        compose.onNodeWithTag("check_nickname").assertDoesNotExist()
        compose.onNodeWithTag("confirm_sign_in").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("profile_name").performScrollTo().performTextClearance()
        compose.onNodeWithTag("profile_name").performTextInput("새 기기 러너")
        compose.onNodeWithTag("save_profile").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("profile_name").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("새 기기 러너" to ""), saves)
            assertEquals(original.copy(name = "새 기기 러너"), state.value.profile)
            assertEquals(backup, state.value.cloud)
            assertEquals("", state.value.communityUid)
            assertFalse(state.value.signedIn)
            assertFalse(visible.value)
            assertEquals(0, checks)
            assertEquals(0, connections)
        }
    }
    @Test fun failedConnectionKeepsTheNicknameAndWalletAndClosesOnlyForItsConnectionAcknowledgement() {
        val original = Profile(name = "재시도 러너", mileage = 4321)
        val state = mutableStateOf(AppState(profile = original, communityBackend = CommunityBackend.FIREBASE))
        val visible = mutableStateOf(true)
        val connections = mutableListOf<Pair<String, String>>()
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun connectUnifiedProfile(name: String, expectedUid: String, expectedBackend: CommunityBackend) {
                assertEquals(CommunityBackend.FIREBASE, expectedBackend)
                connections += name to expectedUid
                state.value = state.value.copy(unifiedProfile = state.value.unifiedProfile.copy(connecting = true, error = null))
            }
        }
        compose.setContent { RunmoniTheme { if (visible.value) UnifiedProfileDialog(state.value, actions) { visible.value = false } } }
        compose.onNodeWithTag("profile_adult").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_sign_in").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("confirm_sign_in").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(listOf("재시도 러너" to ""), connections)
            assertEquals(original, state.value.profile)
            assertTrue(visible.value)
            state.value = state.value.copy(unifiedProfile = state.value.unifiedProfile.copy(
                connecting = false, error = "검토용 프로필 권한 실패"))
        }
        compose.onNodeWithTag("profile_error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("profile_name").performScrollTo().assertTextContains("재시도 러너")
        // An unrelated local-save serial does not acknowledge an online connect.
        compose.runOnIdle {
            state.value = state.value.copy(unifiedProfile = state.value.unifiedProfile.copy(savedSerial = 1))
            assertEquals(original, state.value.profile)
        }
        compose.onNodeWithTag("close_profile").assertIsDisplayed()
        compose.runOnIdle { assertTrue(visible.value) }
        compose.onNodeWithTag("confirm_sign_in").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf("재시도 러너" to "", "재시도 러너" to ""), connections)
            assertEquals(original, state.value.profile)
            state.value = state.value.copy(signedIn = true, communityUid = "verified-retry",
                unifiedProfile = state.value.unifiedProfile.copy(connecting = false, error = null,
                    connectedSerial = 1, connectionUid = "verified-retry", connectedNickname = "재시도 러너"))
        }
        compose.onNodeWithTag("profile_name").assertDoesNotExist()
        compose.runOnIdle { assertFalse(visible.value); assertEquals(original, state.value.profile) }
    }

    @Test fun aForeignConnectionUidCannotCloseOrAuthorizeTheOpenedProfile() {
        val state = mutableStateOf(AppState(profile = Profile(name = "경계 러너"), communityBackend = CommunityBackend.FIREBASE))
        val visible = mutableStateOf(true)
        val connections = mutableListOf<Pair<String, String>>()
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun connectUnifiedProfile(name: String, expectedUid: String, expectedBackend: CommunityBackend) { assertEquals(CommunityBackend.FIREBASE, expectedBackend); connections += name to expectedUid }
        }
        compose.setContent { RunmoniTheme { if (visible.value) UnifiedProfileDialog(state.value, actions) { visible.value = false } } }
        compose.onNodeWithTag("profile_adult").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_sign_in").performScrollTo().performClick()
        compose.runOnIdle {
            state.value = state.value.copy(signedIn = true, communityUid = "foreign-current",
                unifiedProfile = UnifiedProfileUi(connectedNickname = "경계 러너", connectedSerial = 1,
                    connectionUid = "different-ack", savedSerial = 1,
                    check = check("경계 러너", "foreign-current")))
        }
        compose.onNodeWithTag("save_profile").assertIsNotEnabled()
        compose.onNodeWithTag("check_nickname").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("close_profile").assertIsDisplayed()
        compose.runOnIdle { assertTrue(visible.value); assertEquals(listOf("경계 러너" to ""), connections) }
        compose.onNodeWithTag("close_profile").performClick()
        compose.runOnIdle { assertFalse(visible.value) }
    }

    @Test fun httpConnectionAlsoRequiresItsOwnReceiptInsteadOfAnUnrelatedLocalSave() {
        val state = mutableStateOf(AppState(profile = Profile(name = "서버 러너"), communityBackend = CommunityBackend.HTTP))
        val visible = mutableStateOf(true)
        val requests = mutableListOf<Pair<String, String>>()
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun connectUnifiedProfile(name: String, expectedUid: String, expectedBackend: CommunityBackend) { assertEquals(CommunityBackend.HTTP, expectedBackend); requests += name to expectedUid }
        }
        compose.setContent { RunmoniTheme { if (visible.value) UnifiedProfileDialog(state.value, actions) { visible.value = false } } }
        compose.onNodeWithTag("profile_adult").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_sign_in").performScrollTo().performClick()
        compose.runOnIdle {
            state.value = state.value.copy(signedIn = true,
                unifiedProfile = UnifiedProfileUi(savedSerial = 1, connectedNickname = "서버 러너"))
        }
        compose.onNodeWithTag("close_profile").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(visible.value)
            assertEquals(listOf("서버 러너" to ""), requests)
            state.value = state.value.copy(unifiedProfile = state.value.unifiedProfile.copy(connectedSerial = 1, connectionUid = ""))
        }
        compose.onNodeWithTag("profile_name").assertDoesNotExist()
        compose.runOnIdle { assertFalse(visible.value) }
    }

    @Test fun changingTheBackendWithTheSameEmptyUidCannotAuthorizeTheOpenedEditor() {
        val state = mutableStateOf(AppState(profile = Profile(name = "백엔드 경계"), communityBackend = CommunityBackend.FIREBASE))
        var calls = 0
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun connectUnifiedProfile(name: String, expectedUid: String, expectedBackend: CommunityBackend) { calls++ }
            override fun saveUnifiedProfile(name: String, expectedUid: String, expectedBackend: CommunityBackend) {
                assertEquals(state.value.communityBackend, expectedBackend)
                saveUnifiedProfile(name, expectedUid)
            }
            override fun saveUnifiedProfile(name: String, expectedUid: String) { calls++ }
        }
        compose.setContent { RunmoniTheme { UnifiedProfileDialog(state.value, actions) {} } }
        compose.onNodeWithTag("profile_adult").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_sign_in").performScrollTo().assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(communityBackend = CommunityBackend.HTTP) }
        compose.onNodeWithTag("confirm_sign_in").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithTag("save_profile").assertIsNotEnabled()
        compose.onNodeWithTag("close_profile").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls) }
    }

}

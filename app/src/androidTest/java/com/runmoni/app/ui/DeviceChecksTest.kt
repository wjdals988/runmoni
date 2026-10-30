package com.runmoni.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic states test UI only; no real Google login or public server is exercised. */
class DeviceChecksTest : RunmoniDesignTestBase() {
    @Test fun missingConfigurationAndManualConfirmationStayDistinct() {
        val state = mutableStateOf(AppState())
        val original = RunmoniUiTest.FakeActions(state)
        var shares = 0
        val actions = object : AppActions by original {
            override fun checkRoomConnection() { state.value = state.value.copy(deviceChecks = state.value.deviceChecks.copy(room = RoomCheck.LOCAL_READY)) }
            override fun setPhoneCheck(check: PhoneCheck, confirmed: Boolean) {
                state.value = state.value.copy(deviceChecks = state.value.deviceChecks.copy(selfReported = if (confirmed) state.value.deviceChecks.selfReported + check else state.value.deviceChecks.selfReported - check))
            }
            override fun shareDeviceChecks() { shares++ }
        }
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("open_device_checks").performScrollTo().performClick()
        compose.onNodeWithTag("device_google_status").performScrollTo().assertTextContains("새 APK가 필요해요", substring = true)
        compose.onNodeWithTag("device_google_sign_in").assertDoesNotExist()
        compose.onNodeWithTag("check_room_connection").performScrollTo().performClick()
        compose.onNodeWithTag("room_check_status").assertTextContains("로컬 테스트 서버", substring = true)
        noTextOverflow("device checks missing and local response")
        captureDevice("device-checks-missing")
        compose.onNodeWithTag("phone_check_PHYSICAL_PHONE").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithTag("phone_check_TWO_DEVICE_ROOMS").performScrollTo().assertIsOff()
        compose.onNodeWithTag("share_device_checks").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, shares); assertEquals(setOf(PhoneCheck.PHYSICAL_PHONE), state.value.deviceChecks.selfReported) }
        noTextOverflow("device checks manual results")
        captureDevice("device-checks-share")
    }

    @Test fun restoredGoogleSessionDoesNotClaimInteractiveLogin() {
        val state = mutableStateOf(AppState(cloud = CloudAccountState(configured = true, uid = "fixture-uid", accountLabel = "private@example.com")))
        compose.setContent { RunmoniTheme { DeviceChecksScreen(state.value, RunmoniUiTest.FakeActions(state)) {} } }
        compose.onNodeWithTag("interactive_login_status").performScrollTo().assertTextContains("아직 확인하지 않았어요", substring = true)
        compose.onNodeWithTag("check_google_session").assertIsEnabled()
        compose.onAllNodesWithText("private@example.com").assertCountEquals(0)
        noTextOverflow("device checks restored session")
        captureDevice("device-checks-restored-fixture")
    }

    @Test fun firebasePublicReadDoesNotClaimGoogleOrPhoneChecks() {
        val state = mutableStateOf(AppState(communityBackend = CommunityBackend.FIREBASE,
            cloud = CloudAccountState(configured = true), deviceChecks = DeviceChecks(room = RoomCheck.FIREBASE_READY)))
        compose.setContent { RunmoniTheme { DeviceChecksScreen(state.value, RunmoniUiTest.FakeActions(state)) {} } }
        compose.onNodeWithTag("room_check_status").performScrollTo().assertTextContains("Firebase 공유 방 목록", substring = true)
        compose.onNodeWithTag("check_google_session").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("interactive_login_status").assertTextContains("아직 확인하지 않았어요", substring = true)
        noTextOverflow("Firebase public read fixture")
        captureDevice("device-checks-firebase-ready-fixture")
        compose.onNodeWithTag("phone_check_TWO_DEVICE_ROOMS").performScrollTo().assertIsOff()
        compose.runOnIdle { state.value = state.value.copy(cloud = CloudAccountState(), deviceChecks = DeviceChecks(room = RoomCheck.UNCONFIGURED)) }
        compose.onNodeWithTag("room_check_status").performScrollTo().assertTextContains("설정을 포함한 새 APK", substring = true)
        noTextOverflow("Firebase missing configuration fixture")
        captureDevice("device-checks-firebase-missing-fixture")
    }

    @Test fun anonymousRoomDisconnectRequiresExplicitConfirmation() {
        val state = mutableStateOf(AppState(communityBackend = CommunityBackend.FIREBASE, signedIn = true, communityGuest = true))
        var disconnects = 0
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) { override fun signOut() { disconnects++ } }
        compose.setContent { RunmoniTheme { SettingsScreen(state.value, actions, onDismiss = {}, onSignIn = {}) } }
        compose.onNodeWithTag("sign_out").performScrollTo().performClick()
        compose.onNodeWithText("유지하기").performClick()
        compose.runOnIdle { assertEquals(0, disconnects) }
        compose.onNodeWithTag("sign_out").performClick()
        noTextOverflow("anonymous room disconnect confirmation")
        captureDevice("firebase-anonymous-disconnect-confirmation")
        compose.onNodeWithText("연결 해제").performClick()
        compose.runOnIdle { assertEquals(1, disconnects) }
    }
}

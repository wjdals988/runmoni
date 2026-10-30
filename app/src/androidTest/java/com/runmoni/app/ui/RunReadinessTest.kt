package com.runmoni.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** Real Android Compose fixtures; synthetic state is distinct from live GPS verification. */
class RunReadinessTest : RunmoniDesignTestBase() {
    @Test fun locationRecoveryActionsAndLargeTextKeepStartAccessible() {
        val viewport = mutableStateOf(Viewport())
        val state = mutableStateOf(AppState(locationAccess = LocationAccess(true, false),
            activeRun = ActiveRun(accuracyMeters = 5f, gpsStatus = "GPS 수신 중")))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { ViewportBox(viewport.value) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_run").performClick()
        compose.onNodeWithText("GPS 꺼짐").assertExists()
        compose.onNodeWithText("정확도 ±5m").assertDoesNotExist()
        compose.onNodeWithTag("location_settings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, actions.locationSettings) }
        capture("run-gps-disabled-360-100")
        compose.runOnIdle { viewport.value = Viewport(320, 2f) }
        compose.onNodeWithTag("start_run").assertIsDisplayed().assertHeightIsAtLeast(64.dp)
        compose.onNodeWithTag("location_settings").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        noTextOverflow("GPS recovery 320 / 2")
        capture("run-gps-disabled-320-200")
        compose.runOnIdle { state.value = state.value.copy(locationAccess = LocationAccess(false, false)) }
        compose.onNodeWithTag("app_permission_settings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, actions.permissionSettings) }
        compose.onNodeWithText("위치 권한 필요").assertExists()
        compose.onNodeWithTag("start_run").assertIsDisplayed()
        noTextOverflow("permission recovery 320 / 2")
        capture("run-permission-320-200")
    }

    @Test fun pauseReasonIsExplicitAndResumeAndFinishStayReachable() {
        val viewport = mutableStateOf(Viewport())
        val state = mutableStateOf(AppState(activeRun = ActiveRun(id = "pause-fixture", phase = RunPhase.PAUSED,
            pauseReason = PauseReason.AUTOMATIC, distanceMeters = 1250.0, activeDurationMillis = 450000)))
        compose.setContent { ViewportBox(viewport.value) { RunmoniApp(state.value, RunmoniUiTest.FakeActions(state)) } }
        compose.onNodeWithTag("nav_run").performClick()
        compose.onNodeWithText("자동 일시정지 중").assertExists()
        capture("run-auto-paused-360-100")
        compose.runOnIdle { viewport.value = Viewport(320, 2f) }
        compose.onNodeWithTag("resume_run").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithTag("finish_run").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        noTextOverflow("automatic pause 320 / 2")
        capture("run-auto-paused-320-200")
        compose.runOnIdle { state.value = state.value.copy(activeRun = state.value.activeRun.copy(pauseReason = PauseReason.MANUAL)) }
        compose.onNodeWithText("잠시 쉬는 중").assertExists()
        compose.onNodeWithText("직접 일시정지했어요. 다시 달릴 때 ‘계속 달리기’를 누르세요.").assertExists()
        compose.onNodeWithTag("resume_run").performClick()
        compose.onNodeWithTag("pause_run").assertIsDisplayed()
    }
}

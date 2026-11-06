package com.runmoni.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Compose callback fixtures; no real notification tap, background GPS or storage success is claimed. */
class RunNotificationUiTest : RunmoniDesignTestBase() {
    @Test fun coldNotificationWaitsForRecoveryAndItsFinishRequestIsHandledOnce() {
        val request = RunNavigationRequest(token = 11, runId = "restored-run", confirmFinish = true)
        val state = mutableStateOf(AppState(runPersistence = RunPersistenceState(), runNavigationRequest = request))
        val delegate = RunmoniUiTest.FakeActions(state)
        val consumed = mutableListOf<Long>()
        val actions = object : AppActions by delegate {
            // Keep the fixture request present to detect duplicate UI handling.
            override fun consumeRunNavigation(token: Long) { consumed += token }
        }
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("nav_home").assertIsSelected()
        compose.onNodeWithTag("confirm_finish_run").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(consumed.isEmpty())
            state.value = state.value.copy(activeRun = ActiveRun(id = "restored-run", phase = RunPhase.RECOVERY),
                runPersistence = RunPersistenceState(initialized = true))
        }
        compose.onNodeWithTag("confirm_finish_run").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(11L), consumed); assertEquals(0, delegate.finishes) }
        compose.onNodeWithTag("cancel_finish_run").assertTextEquals("취소").performClick()
        compose.onNodeWithTag("nav_run").assertIsSelected()
        compose.runOnIdle { state.value = state.value.copy(activeRun = state.value.activeRun.copy(phase = RunPhase.PAUSED)) }
        compose.onNodeWithTag("confirm_finish_run").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(11L), consumed); assertEquals(0, delegate.finishes) }
    }

    @Test fun staleRequestLeavesSettingsAloneAndValidRequestsCloseSettingsAndCloset() {
        val original = Profile(name = "알림 이동 검토", mileage = 432,
            avatar = AvatarStyle(gender = AvatarGender.FEMALE), ownedItems = AvatarCatalogue.defaultOwned + "top:1")
        val state = mutableStateOf(AppState(profile = original, activeRun = ActiveRun(id = "current-run", phase = RunPhase.RUNNING)))
        val delegate = RunmoniUiTest.FakeActions(state)
        val consumed = mutableListOf<Long>()
        val actions = object : AppActions by delegate {
            override fun consumeRunNavigation(token: Long) {
                consumed += token
                state.value = state.value.copy(runNavigationRequest = null)
            }
        }
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("settings_button").performClick()
        compose.runOnIdle { state.value = state.value.copy(runNavigationRequest = RunNavigationRequest(21, "old-run", true)) }
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        compose.onNodeWithTag("confirm_finish_run").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(21L), consumed)
            state.value = state.value.copy(runNavigationRequest = RunNavigationRequest(22, "current-run", true))
        }
        compose.onNodeWithTag("settings_screen").assertDoesNotExist()
        compose.onNodeWithTag("confirm_finish_run").assertIsDisplayed()
        compose.onNodeWithTag("cancel_finish_run").performClick()
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("open_closet").performScrollTo().performClick()
        compose.onNodeWithTag("closet_close").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(runNavigationRequest = RunNavigationRequest(23, "current-run", false)) }
        compose.onNodeWithTag("closet_close").assertDoesNotExist()
        compose.onNodeWithTag("nav_run").assertIsSelected()
        compose.onNodeWithTag("confirm_finish_run").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(21L, 22L, 23L), consumed)
            assertEquals(0, delegate.finishes)
            assertEquals(original, state.value.profile)
        }
    }

    @Test fun openingTheCurrentRunClosesChatWithoutChangingItsData() {
        val message = ChatMessage("message", "crew:fixture", "검토 러너", "모임 메시지", 1000)
        val state = mutableStateOf(AppState(activeRun = ActiveRun(id = "chat-run", phase = RunPhase.RUNNING),
            openedRoomId = "crew:fixture", openedRoomTitle = "검토 크루", messages = listOf(message)))
        var chatCloses = 0
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun closeChat() { chatCloses++; state.value = state.value.copy(openedRoomId = "", openedRoomTitle = "") }
            override fun consumeRunNavigation(token: Long) { state.value = state.value.copy(runNavigationRequest = null) }
        }
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("chat_dialog").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(runNavigationRequest = RunNavigationRequest(31, "chat-run", false)) }
        compose.onNodeWithTag("chat_dialog").assertDoesNotExist()
        compose.onNodeWithTag("nav_run").assertIsSelected()
        compose.runOnIdle { assertEquals(1, chatCloses); assertEquals(listOf(message), state.value.messages) }
    }

    @Test fun cancellingDoesNotResumeAndFinishFailureKeepsTheSaveRetry() {
        val state = mutableStateOf(AppState(activeRun = ActiveRun(id = "first-run", phase = RunPhase.PAUSED)))
        var finishes = 0
        val finishedRunIds = mutableListOf<String>()
        var retries = 0
        val delegate = RunmoniUiTest.FakeActions(state)
        val actions = object : AppActions by delegate {
            override fun consumeRunNavigation(token: Long) { state.value = state.value.copy(runNavigationRequest = null) }
            override fun finishRun() {
                finishes++
                state.value = state.value.copy(activeRun = state.value.activeRun.copy(phase = RunPhase.PAUSED),
                    runPersistence = RunPersistenceState(initialized = true, finishing = true, issue = RunPersistenceIssue.FINISH))
            }
            override fun finishRun(expectedRunId: String) {
                finishedRunIds += expectedRunId
                assertEquals(state.value.activeRun.id, expectedRunId)
                finishRun()
            }
            override fun retryRunPersistence() { retries++; state.value = state.value.copy(runPersistence = state.value.runPersistence.copy(saving = true)) }
        }
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("nav_run").performClick()
        compose.onNodeWithTag("finish_run").performClick()
        compose.onNodeWithTag("cancel_finish_run").performClick()
        compose.onNodeWithTag("resume_run").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, finishes); assertEquals(0, delegate.resumes) }
        compose.onNodeWithTag("finish_run").performClick()
        compose.runOnIdle { state.value = state.value.copy(activeRun = ActiveRun(id = "next-run", phase = RunPhase.RUNNING)) }
        compose.onNodeWithTag("confirm_finish_run").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(runNavigationRequest = RunNavigationRequest(41, "next-run", true)) }
        compose.onNodeWithTag("confirm_finish_run").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(runPersistence = RunPersistenceState(initialized = true,
            finishing = true, issue = RunPersistenceIssue.FINISH)) }
        compose.onNodeWithTag("confirm_finish_run").assertDoesNotExist()
        compose.onNodeWithTag("retry_finish_run").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(runPersistence = RunPersistenceState(initialized = true),
            runNavigationRequest = RunNavigationRequest(42, "next-run", true)) }
        compose.onNodeWithTag("confirm_finish_run").performClick()
        compose.onNodeWithTag("confirm_finish_run").assertDoesNotExist()
        compose.onNodeWithTag("pause_run").assertDoesNotExist()
        compose.onNodeWithTag("finish_run").assertDoesNotExist()
        compose.onNodeWithTag("retry_finish_run").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("retry_finish_run").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(listOf("next-run"), finishedRunIds)
            assertEquals(1, finishes)
            assertEquals(1, retries)
            assertTrue(state.value.records.isEmpty())
        }
    }

    @Test fun blockedNotificationsOfferSettingsWithoutBlockingTheRunAtLargeFont() {
        val state = mutableStateOf(AppState())
        var settingsCalls = 0
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun openRunNotificationSettings() { settingsCalls++ }
        }
        compose.setContent { ViewportBox(Viewport(320, 2f)) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_run").performClick()
        compose.onNodeWithTag("run_notification_blocked").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(runNotificationAccess = RunNotificationAccess(checked = true, enabled = false)) }
        compose.onNodeWithTag("run_notification_settings").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("start_run").assertIsEnabled().assertIsDisplayed().assertHeightIsAtLeast(64.dp)
        val start = compose.onNodeWithTag("start_run").getUnclippedBoundsInRoot()
        val navigation = compose.onNodeWithTag("nav_run").getUnclippedBoundsInRoot()
        assertTrue("Full start action must remain above app navigation", start.bottom <= navigation.top)
        noTextOverflow("notification blocked 320dp / fontScale 2 fixture")
        capture("run-notification-blocked-320-200-fixture")
        compose.runOnIdle { state.value = state.value.copy(runNotificationAccess = RunNotificationAccess(checked = true, enabled = true)) }
        compose.onNodeWithTag("run_notification_blocked").assertDoesNotExist()
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("settings_run_notifications").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        noTextOverflow("notification settings 320dp / fontScale 2 fixture")
        compose.runOnIdle { assertEquals(2, settingsCalls) }
    }

    @Test fun speedUsesKilometersPerHourAndDoesNotRetainAPausedOrMissingFix() {
        val viewport = mutableStateOf(Viewport())
        val running = ActiveRun(id = "speed-fixture", phase = RunPhase.RUNNING, distanceMeters = 1250.0,
            activeDurationMillis = 450_000, currentPaceSecondsPerKm = 333, accuracyMeters = 5f,
            currentSpeedMetersPerSecond = 3.0)
        val state = mutableStateOf(AppState(activeRun = running, locationAccess = LocationAccess(true, true)))
        compose.setContent { ViewportBox(viewport.value) { RunmoniApp(state.value, RunmoniUiTest.FakeActions(state)) } }
        compose.onNodeWithTag("nav_run").performClick()
        compose.onNodeWithTag("run_speed").performScrollTo().assertTextEquals("10.8 km/h")
        compose.onNodeWithTag("run_pace").assertTextEquals("5′33″")
        compose.onNodeWithTag("run_duration").assertExists()
        capture("run-speed-360-100-fixture")
        compose.runOnIdle { state.value = state.value.copy(activeRun = running.copy(phase = RunPhase.PAUSED)) }
        compose.onNodeWithTag("run_speed").assertTextEquals("— km/h")
        compose.runOnIdle { state.value = state.value.copy(activeRun = running.copy(currentSpeedMetersPerSecond = null, accuracyMeters = null)) }
        compose.onNodeWithTag("run_speed").assertTextEquals("— km/h")
        compose.runOnIdle { state.value = state.value.copy(activeRun = running, locationAccess = LocationAccess(true, false)) }
        compose.onNodeWithTag("run_speed").assertTextEquals("— km/h")
        compose.runOnIdle {
            state.value = state.value.copy(activeRun = running, locationAccess = LocationAccess(true, true))
            viewport.value = Viewport(320, 2f)
        }
        compose.onNodeWithTag("run_speed").performScrollTo().assertTextEquals("10.8 km/h")
        compose.onNodeWithTag("pause_run").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithTag("finish_run").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        noTextOverflow("speed 320dp / fontScale 2 fixture")
        capture("run-speed-320-200-fixture")
    }
}

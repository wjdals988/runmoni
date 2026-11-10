package com.runmoni.app.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test

/** ComponentActivity contracts with inert map tiles; no Firebase, real GPS or public tiles. */
class RunnerDiscoveryUiTest : RunmoniDesignTestBase() {
    private class Actions(private val state: MutableState<AppState>) : AppActions by RunmoniUiTest.FakeActions(state) {
        val sharingRequests = mutableListOf<Boolean>()
        val confirmations = mutableListOf<Triple<String, Boolean, Long>>()
        val dismissedNonces = mutableListOf<Long>()
        val visibility = mutableListOf<Boolean>()
        var locates = 0
        override fun setPublicLocation(enabled: Boolean) { sharingRequests += enabled }
        override fun setDiscoveryVisible(visible: Boolean) { visibility += visible }
        override fun locateExplore() { locates++ }
        override fun confirmPublicLocation(runId: String, adultConfirmed: Boolean, requestNonce: Long) {
            confirmations += Triple(runId, adultConfirmed, requestNonce)
        }
        override fun dismissSharingConsent(requestNonce: Long) {
            dismissedNonces += requestNonce
            if (state.value.sharingConsentNonce == requestNonce) state.value = state.value.copy(sharingConsentRunId = null)
        }
    }

    @Test fun guestAlwaysHasAMapAndProfileEntryWithoutCrewPartyOrCachedRunnerLeakage() {
        val now = System.currentTimeMillis()
        val state = mutableStateOf(AppState(
            liveRunners = listOf(LiveRunner("cached", "캐시 러너", 37.567, 126.978, now)),
            crews = listOf(Crew("crew", "탐색에 보이면 안 되는 크루", "크루 탭 자료")),
            parties = listOf(Party("party", "crew", "탐색에 보이면 안 되는 파티", PartyKind.LOCAL, now)),
        ))
        val actions = Actions(state)
        var profiles = 0
        compose.setContent {
            ViewportBox(Viewport(360, 1f)) {
                RunmoniTheme { RunnerDiscoveryScreen(state.value, actions) { profiles++ } }
            }
        }
        compose.onNodeWithTag("runner_discovery").assertIsDisplayed()
        compose.onNodeWithTag("route_canvas").assertIsDisplayed()
        compose.onNodeWithTag("route_map_viewport").assertContentDescriptionContains("세계 지도", substring = true)
        compose.onNodeWithTag("runner_discovery_status").assertTextContains("프로필을 연결", substring = true)
        compose.onNodeWithText("탐색에 보이면 안 되는 크루").assertDoesNotExist()
        compose.onNodeWithText("탐색에 보이면 안 되는 파티").assertDoesNotExist()
        compose.onNodeWithTag("rooms_search").assertDoesNotExist()
        compose.onNodeWithTag("toggle_runner_sharing").assertIsEnabled().performClick()
        compose.onNodeWithTag("locate_explore").performClick()
        compose.runOnIdle {
            assertEquals(1, profiles)
            assertEquals(1, actions.locates)
            assertTrue(actions.sharingRequests.isEmpty())
            assertTrue(actions.confirmations.isEmpty())
            assertTrue(actions.visibility.contains(true))
        }
    }

    @Test fun selfExpiredAndInvalidLocationsStayHiddenWhileOnlyFreshOtherRunnerReachesTheMap() {
        val now = System.currentTimeMillis()
        val self = LiveRunner("self", "나의 서버 표시", 37.567, 126.978, now)
        val stale = LiveRunner("stale", "만료 러너", 37.567, 126.978, now - 90_000)
        val invalid = LiveRunner("invalid", "잘못된 위치", Double.NaN, 126.978, now)
        val state = mutableStateOf(AppState(signedIn = true, communityUid = "self",
            discoveryCenter = RouteMapCenter(37.567, 126.978),
            discovery = RunnerDiscoveryUi(checkedAtMillis = now), liveRunners = listOf(self, stale, invalid)))
        val actions = Actions(state)
        compose.setContent {
            ViewportBox(Viewport(360, 1f)) {
                RunmoniTheme { RunnerDiscoveryScreen(state.value, actions, onProfile = {}) }
            }
        }
        compose.onNodeWithTag("route_map_viewport").assertContentDescriptionContains("확인된 위치 주변 지도", substring = true)
        compose.onNodeWithTag("runner_discovery_status").assertTextContains("표시할 공유 위치가 없어요", substring = true)
        compose.runOnIdle {
            state.value = state.value.copy(liveRunners = listOf(self, stale, invalid,
                LiveRunner("fresh", "최근 위치 러너", 37.567, 126.978, System.currentTimeMillis())))
        }
        // The screen deliberately refuses future timestamps, including the next
        // instant before its wall-clock tick. Wait for the actual derived state.
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasText("러너 1명", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("route_map_viewport").assertContentDescriptionContains("위치를 공유한 러너 1명", substring = true)
        compose.runOnIdle { state.value = state.value.copy(liveRunners = listOf(self, stale, invalid)) }
        compose.onNodeWithTag("route_map_viewport").assertContentDescriptionContains("확인된 위치 주변 지도", substring = true)
        compose.onNodeWithTag("runner_discovery_status").assertTextContains("표시할 공유 위치가 없어요", substring = true)
    }

    @Test fun sharingButtonRequestsConsentAndCannotDirectlyConfirmOrMutateSharingState() {
        val state = mutableStateOf(AppState(signedIn = true, communityUid = "self",
            activeRun = ActiveRun(id = "running-session", phase = RunPhase.RUNNING),
            discoveryCenter = RouteMapCenter(37.567, 126.978)))
        val actions = Actions(state)
        compose.setContent {
            ViewportBox(Viewport(360, 1f)) {
                RunmoniTheme { RunnerDiscoveryScreen(state.value, actions, onProfile = {}) }
            }
        }
        compose.onNodeWithTag("toggle_runner_sharing").performClick()
        compose.runOnIdle {
            assertEquals(listOf(true), actions.sharingRequests)
            assertTrue(actions.confirmations.isEmpty())
            assertFalse(state.value.publicLocation)
        }
        compose.runOnIdle { state.value = state.value.copy(publicLocation = true) }
        compose.onNodeWithTag("toggle_runner_sharing").performClick()
        compose.runOnIdle {
            assertEquals(listOf(true, false), actions.sharingRequests)
            assertTrue(actions.confirmations.isEmpty())
        }
    }

    @Test fun centralConsentCancelDoesNotConfirmAndANewNonceRequiresFreshAdultConsent() {
        val state = mutableStateOf(AppState(signedIn = true, communityUid = "self",
            activeRun = ActiveRun(id = "run-a", phase = RunPhase.RUNNING),
            sharingConsentRunId = "run-a", sharingConsentNonce = 7))
        val actions = Actions(state)
        compose.setContent { RunmoniTheme { RunnerSharingConsent(state.value, actions) } }
        compose.onNodeWithTag("confirm_runner_sharing").assertIsNotEnabled()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithTag("confirm_runner_sharing").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(7L), actions.dismissedNonces)
            assertTrue(actions.confirmations.isEmpty())
            assertTrue(actions.sharingRequests.isEmpty())
            state.value = state.value.copy(sharingConsentRunId = "run-a", sharingConsentNonce = 8)
        }
        compose.onNodeWithTag("confirm_runner_sharing").assertIsNotEnabled()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithTag("confirm_runner_sharing").assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(sharingConsentNonce = 9) }
        compose.onNodeWithTag("confirm_runner_sharing").assertIsNotEnabled()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithTag("confirm_runner_sharing").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(Triple("run-a", true, 9L)), actions.confirmations)
            assertEquals(listOf(7L), actions.dismissedNonces)
            assertTrue(actions.sharingRequests.isEmpty())
        }
    }
}

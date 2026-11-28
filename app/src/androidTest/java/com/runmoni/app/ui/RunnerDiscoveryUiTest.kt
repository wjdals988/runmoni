package com.runmoni.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/** ComponentActivity contracts with inert map tiles; no Firebase, real GPS or public tiles. */
class RunnerDiscoveryUiTest : RunmoniDesignTestBase() {
    private class Actions(private val state: MutableState<AppState>) : AppActions by RunmoniUiTest.FakeActions(state) {
        val sharingRequests = mutableListOf<Boolean>()
        val confirmations = mutableListOf<Triple<String, Boolean, Long>>()
        val dismissedNonces = mutableListOf<Long>()
        val visibility = mutableListOf<Boolean>()
        val bounds = AtomicReference<RouteMapBounds?>()
        var locates = 0
        var refreshes = 0
        var locationSettings = 0
        var permissionSettings = 0
        override fun setPublicLocation(enabled: Boolean) { sharingRequests += enabled }
        override fun setDiscoveryVisible(visible: Boolean) { visibility += visible }
        override fun setDiscoveryBounds(bounds: RouteMapBounds) { this.bounds.set(bounds) }
        override fun locateExplore() { locates++ }
        override fun refreshRunners() { refreshes++ }
        override fun openLocationSettings() { locationSettings++ }
        override fun openAppPermissionSettings() { permissionSettings++ }
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
        compose.onNodeWithTag("runner_discovery_status").assertDoesNotExist()
        compose.onNodeWithTag("explore_current_area").assertDoesNotExist()
        compose.onNodeWithTag("toggle_runner_sharing").assertDoesNotExist()
        compose.onNodeWithText("탐색에 보이면 안 되는 크루").assertDoesNotExist()
        compose.onNodeWithText("탐색에 보이면 안 되는 파티").assertDoesNotExist()
        compose.onNodeWithTag("rooms_search").assertDoesNotExist()
        compose.onNodeWithTag("locate_explore").performClick()
        compose.onNodeWithTag("explore_current_area").assertExists()
        compose.onNodeWithTag("runner_discovery_status").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(currentArea = CurrentAreaUi(CurrentAreaPhase.PERMISSION_REQUIRED)) }
        compose.onNodeWithTag("explore_location_settings").assertTextContains("앱 권한 설정").performClick()
        compose.runOnIdle { state.value = state.value.copy(currentArea = CurrentAreaUi(CurrentAreaPhase.LOCATION_DISABLED)) }
        compose.onNodeWithTag("explore_location_settings").assertTextContains("기기 위치 설정").performClick()
        compose.onNodeWithTag("refresh_runners").assertTextContains("러너 보기").performClick()
        compose.onNodeWithTag("explore_current_area").assertDoesNotExist()
        compose.onNodeWithTag("explore_location_settings").assertDoesNotExist()
        compose.onNodeWithTag("runner_discovery_status").assertTextContains("프로필을 연결", substring = true)
        compose.onNodeWithTag("toggle_runner_sharing").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, profiles)
            assertEquals(1, actions.locates)
            assertEquals(1, actions.locationSettings)
            assertEquals(1, actions.permissionSettings)
            assertTrue(actions.sharingRequests.isEmpty())
            assertTrue(actions.confirmations.isEmpty())
            assertTrue(actions.visibility.isEmpty())
            assertEquals(0, actions.refreshes)
        }
    }

    @Test fun myLocationRecentersAgainAfterBrowsingEvenWhenTheFixCoordinatesStayTheSame() {
        val now = System.currentTimeMillis()
        val region = WeatherLocation(3757, 12698)
        val center = RouteMapCenter(region.latitude, region.longitude)
        val state = mutableStateOf(AppState(discoveryCenter = center,
            currentArea = CurrentAreaUi(CurrentAreaPhase.RESOLVING, region, checkedAtMillis = now)))
        val actions = Actions(state)
        compose.setContent { ViewportBox(Viewport()) { RunmoniTheme {
            RunnerDiscoveryScreen(state.value, actions, onProfile = {})
        } } }
        fun atFix() = actions.bounds.get()?.contains(center.latitude, center.longitude) == true
        fun panAway() {
            val pan = compose.onNodeWithTag("route_map_viewport").fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].single { it.label == "동쪽으로 이동" }
            compose.runOnIdle { assertTrue(pan.action()); assertTrue(pan.action()) }
            compose.waitUntil(30_000) { !atFix() }
        }
        fun locate() {
            val locate = compose.onNodeWithTag("locate_explore")
            if (!locate.isDisplayed()) locate.performScrollTo()
            locate.performClick()
        }
        compose.waitUntil(30_000) { atFix() }
        repeat(2) { index ->
            panAway()
            locate()
            compose.runOnIdle {
                assertFalse(atFix())
                assertEquals(index + 1, actions.locates)
                state.value = state.value.copy(currentArea = CurrentAreaUi(CurrentAreaPhase.READY, region,
                    name = "서울", checkedAtMillis = now + (index + 1) * 1000L))
            }
            compose.waitUntil(30_000) { atFix() }
        }
        panAway()
        locate()
        compose.runOnIdle {
            state.value = state.value.copy(currentArea = CurrentAreaUi(CurrentAreaPhase.PERMISSION_REQUIRED),
                exploreLocationFailureSerial = 1)
        }
        compose.onNodeWithTag("explore_location_settings").assertTextContains("앱 권한 설정")
        val cachedArea = CurrentAreaUi(CurrentAreaPhase.READY, region, name = "서울", checkedAtMillis = now + 3000)
        compose.runOnIdle {
            state.value = state.value.copy(currentArea = cachedArea, weather = RunningWeatherState(WeatherPhase.LOADING))
        }
        compose.onNodeWithTag("explore_current_area").assertTextContains("서울", substring = true)
        compose.runOnIdle { assertFalse("An unrelated recent fix must not replay a failed request", atFix()) }
        locate()
        compose.waitUntil(30_000) { atFix() }
        compose.runOnIdle {
            assertEquals("Use the verified fix without starting a second weather/location request", 3, actions.locates)
            assertEquals(cachedArea.checkedAtMillis, state.value.currentArea.checkedAtMillis)
            assertTrue(actions.visibility.isEmpty())
            assertTrue(actions.sharingRequests.isEmpty())
        }
    }

    @Test fun signedInConnectionWithoutRunnerIdentityDoesNotRequestAnotherProfile() {
        val state = mutableStateOf(AppState(signedIn = true))
        val actions = Actions(state)
        val profiles = AtomicReference(0)
        compose.setContent { ViewportBox(Viewport()) { RunmoniTheme {
            RunnerDiscoveryScreen(state.value, actions) { profiles.set(profiles.get() + 1) }
        } } }
        compose.onNodeWithTag("refresh_runners").performClick()
        compose.onNodeWithTag("runner_discovery_status").assertTextContains("현재 연결", substring = true)
        compose.onNodeWithTag("toggle_runner_sharing").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, profiles.get())
            assertEquals(0, actions.refreshes)
            assertTrue(actions.visibility.isEmpty())
        }
    }

    @Test fun selfExpiredAndInvalidLocationsStayHiddenWhileOnlyFreshOtherRunnerReachesTheMap() {
        val now = System.currentTimeMillis()
        val self = LiveRunner("self", "나의 서버 표시", 37.567, 126.978, now)
        val stale = LiveRunner("stale", "만료 러너", 37.567, 126.978, now - 90_000)
        val invalid = LiveRunner("invalid", "잘못된 위치", Double.NaN, 126.978, now)
        val fresh = LiveRunner("fresh", "최근 위치 러너", 37.567, 126.978, now)
        val state = mutableStateOf(AppState(signedIn = true, communityUid = "self",
            discoveryCenter = RouteMapCenter(37.567, 126.978),
            discovery = RunnerDiscoveryUi(checkedAtMillis = now), liveRunners = listOf(self, stale, invalid, fresh)))
        val actions = Actions(state)
        compose.setContent {
            ViewportBox(Viewport(360, 1f)) {
                RunmoniTheme { RunnerDiscoveryScreen(state.value, actions, onProfile = {}) }
            }
        }
        compose.onNodeWithTag("route_map_viewport").assertContentDescriptionContains("확인된 위치 주변 지도", substring = true)
        compose.onNodeWithTag("runner_discovery_status").assertDoesNotExist()
        compose.onNodeWithTag("refresh_runners").performClick()
        compose.onNodeWithTag("runner_discovery_status").assertTextContains("러너 1명", substring = true)
        compose.onNodeWithTag("route_map_viewport").assertContentDescriptionContains("위치를 공유한 러너 1명", substring = true)
        compose.runOnIdle { state.value = state.value.copy(liveRunners = listOf(self, stale, invalid)) }
        compose.onNodeWithTag("route_map_viewport").assertContentDescriptionContains("확인된 위치 주변 지도", substring = true)
        compose.onNodeWithTag("runner_discovery_status").assertTextContains("표시할 공유 위치가 없어요", substring = true)
        compose.runOnIdle { state.value = state.value.copy(communityUid = "new-account", liveRunners = listOf(fresh)) }
        compose.onNodeWithTag("route_map_viewport").assertContentDescriptionContains("확인된 위치 주변 지도", substring = true)
        compose.onNodeWithTag("runner_discovery_status").assertTextContains("러너 보기", substring = true)
        compose.runOnIdle { assertEquals(listOf(true, false), actions.visibility); assertEquals(1, actions.refreshes) }
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

    @Test fun explicitRefreshStaysOutsideTheMapAndCanRetryWithoutChangingConfirmedMarkersOrConsent() {
        val now = System.currentTimeMillis()
        val marker = LiveRunner("fresh-other", "확인된 러너", 37.567, 126.978, now)
        val state = mutableStateOf(AppState(signedIn = true, communityUid = "self",
            discovery = RunnerDiscoveryUi(checkedAtMillis = now), liveRunners = listOf(marker)))
        val actions = Actions(state)
        compose.setContent { ViewportBox(Viewport(360, 1f)) { RunmoniTheme {
            RunnerDiscoveryScreen(state.value, actions, onProfile = {})
        } } }
        compose.runOnIdle { assertTrue(actions.visibility.isEmpty()); assertEquals(0, actions.refreshes) }
        compose.onNodeWithTag("refresh_runners").assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, actions.refreshes)
            assertEquals(listOf(true), actions.visibility)
            state.value = state.value.copy(discovery = discoveryReadFailed(state.value.discovery, userInitiated = true))
        }
        compose.onNodeWithTag("runner_discovery_status").assertTextContains("다시 시도", substring = true)
        compose.onNodeWithTag("refresh_runners").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(2, actions.refreshes)
            assertEquals(listOf(marker), state.value.liveRunners)
            assertEquals(now, state.value.discovery.checkedAtMillis)
            assertTrue(actions.sharingRequests.isEmpty())
            assertTrue(actions.confirmations.isEmpty())
        }
        compose.onNodeWithTag("locate_explore").performClick()
        compose.onNodeWithTag("explore_current_area").assertExists()
        val hide = compose.onNodeWithTag("hide_discovered_runners")
        if (!hide.isDisplayed()) hide.performScrollTo()
        hide.performClick()
        compose.onNodeWithTag("runner_discovery_status").assertDoesNotExist()
        compose.onNodeWithTag("explore_current_area").assertExists()
        compose.runOnIdle { assertEquals(listOf(true, false), actions.visibility) }
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

    @Test fun beforeRunningConsentIsExplicitAndPreparedStateIsNotLivePresence() {
        val state = mutableStateOf(AppState(signedIn = true, communityUid = "self",
            sharingConsentRunId = com.runmoni.app.data.RunnerSharingPreferenceStore.NEXT_RUN,
            sharingConsentNonce = 42))
        val actions = Actions(state)
        compose.setContent { ViewportBox(Viewport(360, 1f)) { RunmoniTheme {
            RunnerDiscoveryScreen(state.value, actions, onProfile = {})
            RunnerSharingConsent(state.value, actions)
        } } }
        compose.onNodeWithTag("confirm_runner_sharing").assertTextContains("설정 저장").assertIsNotEnabled()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithTag("confirm_runner_sharing").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(Triple(com.runmoni.app.data.RunnerSharingPreferenceStore.NEXT_RUN, true, 42L)), actions.confirmations)
            assertTrue(actions.sharingRequests.isEmpty())
            state.value = state.value.copy(sharingConsentRunId = null, sharingPreferenceEnabled = true)
        }
        compose.onNodeWithTag("runner_sharing_prepared").assertIsDisplayed()
        compose.runOnIdle { assertFalse(state.value.publicLocation); assertEquals(RunPhase.IDLE, state.value.activeRun.phase) }
        compose.onNodeWithTag("toggle_runner_sharing").performClick()
        compose.runOnIdle { assertEquals(listOf(false), actions.sharingRequests) }
    }

    @Test fun mapAndOutsideActionsRemainReadableAcrossNarrowScreensAndLargeText() {
        val viewport = mutableStateOf(Viewport())
        val state = mutableStateOf(AppState())
        val actions = Actions(state)
        compose.setContent {
            ViewportBox(viewport.value) {
                RunmoniTheme { RunnerDiscoveryScreen(state.value, actions, onProfile = {}) }
            }
        }
        for (width in listOf(320, 360, 411)) for (scale in listOf(1f, 1.5f, 2f)) {
            compose.runOnIdle { viewport.value = Viewport(width, scale) }
            fun reachable(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag).also {
                if (scale >= 1.3f) it.performScrollTo()
            }
            reachable("route_map_viewport").assertIsDisplayed().assertHeightIsAtLeast(96.dp)
            reachable("map_options").assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("map_recenter").assertDoesNotExist()
            val map = compose.onNodeWithTag("route_map_viewport").fetchSemanticsNode().boundsInRoot
            val bar = compose.onNodeWithTag("map_control_bar").fetchSemanticsNode().boundsInRoot
            assertTrue("The map's persistent control is outside its pixels at $width/$scale", map.bottom <= bar.top)
            for (tag in listOf("locate_explore", "refresh_runners")) {
                reachable(tag).assertIsDisplayed()
                    .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                val screen = compose.onNodeWithTag("audit_viewport").fetchSemanticsNode().boundsInRoot
                assertTrue("The full outside-map action stays in the viewport at $width/$scale: $tag",
                    bounds.left >= screen.left && bounds.right <= screen.right && bounds.top >= screen.top && bounds.bottom <= screen.bottom)
            }
            noTextOverflow("map-first discovery $width / $scale")
            if (width == 320 && scale == 2f) {
                reachable("route_map_viewport")
                capture("discovery-map-clean-320-font2-fixture")
            }
        }
        compose.runOnIdle {
            assertTrue("Layout and capture do not request public sharing", actions.sharingRequests.isEmpty())
            assertTrue(actions.confirmations.isEmpty())
            assertEquals(0, actions.locates)
        }
    }

    @Test fun shortLargeTextDestinationShowsBothDiscoveryCommandsAndMapCreditWithoutScrolling() {
        val state = mutableStateOf(AppState(signedIn = true, communityBackend = CommunityBackend.FIREBASE,
            communityUid = "first-view-runner"))
        val actions = Actions(state)
        compose.setContent { ViewportBox(Viewport(320, 2f)) {
            Box(Modifier.height(520.dp).testTag("short_discovery_host")) { RunmoniApp(state.value, actions) }
        } }
        compose.onNodeWithTag("nav_explore").performClick()
        val destination = compose.onNodeWithTag("runner_discovery").getUnclippedBoundsInRoot()
        val map = compose.onNodeWithTag("route_map_viewport").assertIsDisplayed().assertHeightIsAtLeast(96.dp)
            .getUnclippedBoundsInRoot()
        val locate = compose.onNodeWithTag("locate_explore").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).getUnclippedBoundsInRoot()
        val runners = compose.onNodeWithTag("refresh_runners").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).getUnclippedBoundsInRoot()
        val credit = compose.onNodeWithTag("osm_attribution").assertIsDisplayed().getUnclippedBoundsInRoot()
        compose.onNodeWithTag("map_options").assertContentDescriptionEquals("지도 조작")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val creditLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("© OpenStreetMap contributors", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(creditLayouts) }
        val creditLayout = creditLayouts.single()
        assertFalse("Attribution must be fully readable", creditLayout.hasVisualOverflow)
        assertTrue("The provider name stays on a single line", (0 until creditLayout.lineCount).any { line ->
            creditLayout.layoutInput.text.text.substring(creditLayout.getLineStart(line), creditLayout.getLineEnd(line))
                .contains("OpenStreetMap")
        })
        assertEquals(locate.top.value, runners.top.value, 1f)
        assertTrue(locate.bottom <= map.top && runners.bottom <= map.top)
        for ((name, node) in mapOf("locate" to locate, "runners" to runners, "map" to map, "credit" to credit)) {
            assertTrue("Full $name ($node) must fit inside the discovery scroll viewport $destination",
                node.top >= destination.top && node.bottom <= destination.bottom)
        }
        compose.onNodeWithTag("runner_discovery_status").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(actions.visibility.isEmpty())
            assertEquals(0, actions.refreshes)
            assertEquals(0, actions.locates)
        }
        capture("discovery-first-view-320-font2-fixture")

        compose.runOnIdle { state.value = state.value.copy(signedIn = false, communityUid = "",
            currentArea = CurrentAreaUi(CurrentAreaPhase.PERMISSION_REQUIRED)) }
        compose.onNodeWithTag("refresh_runners").performClick()
        val response = compose.onNodeWithTag("runner_discovery_status").assertIsDisplayed()
            .assertTextContains("프로필을 연결", substring = true).getUnclippedBoundsInRoot()
        val profile = compose.onNodeWithTag("toggle_runner_sharing").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).getUnclippedBoundsInRoot()
        assertTrue("Guest feedback and its next step are fully visible below the command", response.top >= runners.bottom &&
            response.bottom <= profile.top && profile.bottom <= destination.bottom)
        compose.onNodeWithTag("locate_explore").performClick()
        compose.onNodeWithTag("explore_current_area").assertIsDisplayed()
        compose.onNodeWithTag("explore_location_settings").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.runOnIdle {
            assertTrue(actions.visibility.isEmpty())
            assertEquals(0, actions.refreshes)
            assertEquals(1, actions.locates)
        }
    }
}

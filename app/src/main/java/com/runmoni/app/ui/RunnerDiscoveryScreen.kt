package com.runmoni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.runmoni.app.domain.*
import kotlinx.coroutines.delay

private enum class DiscoveryPanel { LOCATION, RUNNERS }
private data class PendingMapLocation(val previousFixTime: Long?, val failureSerial: Long)

@Composable
internal fun RunnerDiscoveryScreen(state: AppState, actions: AppActions, onProfile: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var panel by rememberSaveable { mutableStateOf<DiscoveryPanel?>(null) }
    var lookupAccount by rememberSaveable { mutableStateOf<String?>(null) }
    var explicitLookupPending by remember { mutableStateOf(false) }
    val canLookup = state.signedIn && state.communityUid.isNotBlank()
    val lookupActive = canLookup && lookupAccount == state.communityUid
    DisposableEffect(owner, actions, lookupActive) {
        if (lookupActive) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    actions.setDiscoveryVisible(true)
                    if (explicitLookupPending) {
                        explicitLookupPending = false
                        actions.refreshRunners()
                    }
                }
                if (event == Lifecycle.Event.ON_STOP) actions.setDiscoveryVisible(false)
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer); actions.setDiscoveryVisible(false) }
        } else {
            onDispose { }
        }
    }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { clock = System.currentTimeMillis(); delay(1000) }
        }
    }
    var pendingLocation by remember { mutableStateOf<PendingMapLocation?>(null) }
    var recenterRequest by remember { mutableStateOf<MapRecenterRequest?>(null) }
    val fixTime = state.currentArea.checkedAtMillis
    val verifiedFix = state.discoveryCenter?.takeIf {
        it.valid() && state.currentArea.location == WeatherLocation.approximate(it.latitude, it.longitude)
    }
    val failureSerial = state.exploreLocationFailureSerial
    LaunchedEffect(pendingLocation, fixTime, verifiedFix, failureSerial) {
        val pending = pendingLocation ?: return@LaunchedEffect
        if (failureSerial != pending.failureSerial) pendingLocation = null
        else if (fixTime != null && fixTime != pending.previousFixTime && verifiedFix != null) {
            recenterRequest = MapRecenterRequest(verifiedFix)
            pendingLocation = null
        }
    }
    val publicRunners = if (lookupActive) state.liveRunners.filter {
        it.id != state.communityUid && it.hasValidCoordinates() &&
            clock - it.updatedAtMillis in 0..RunnerPresencePolicy.MAX_MARKER_AGE_MILLIS
    } else emptyList()
    val point = state.activeRun.points.lastOrNull()?.takeIf { RunnerPresencePolicy.shareable(it, clock) }
    val own = point?.takeIf { state.activeRun.phase in listOf(RunPhase.RUNNING, RunPhase.PAUSED) }?.let {
        LiveRunner("local-self", "나", it.latitude, it.longitude, it.recordedAtMillis, state.profile.avatar)
    }
    val center = state.discoveryCenter ?: own?.let { RouteMapCenter(it.latitude, it.longitude) }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("runner_discovery")) {
        val fontScale = LocalDensity.current.fontScale
        val constrained = maxHeight < 560.dp || fontScale >= 1.3f || (panel != null && maxHeight < 640.dp)
        val mapHeight = if (fontScale >= 1.8f) 360.dp else 280.dp
        Column(Modifier.fillMaxSize().then(if (constrained) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("탐색", style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = pageGutter()).semantics { heading() })
            MapPane(emptyList(), publicRunners + listOfNotNull(own),
                modifier = Modifier.fillMaxWidth().then(if (constrained) Modifier.height(mapHeight) else Modifier.weight(1f)),
                interactiveMap = true, followCenter = center, onBoundsChanged = actions::setDiscoveryBounds,
                recenterRequest = recenterRequest)
            Column(Modifier.fillMaxWidth().background(RunmoniColors.Paper)
                .padding(horizontal = pageGutter(), vertical = 12.dp).testTag("runner_discovery_details"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveActions(first = { m ->
                    FilledTonalButton(onClick = {
                        panel = DiscoveryPanel.LOCATION
                        if (state.weather.phase == WeatherPhase.LOADING && verifiedFix != null && state.currentArea.isRecent(clock)) {
                            pendingLocation = null
                            recenterRequest = MapRecenterRequest(verifiedFix)
                        } else {
                            pendingLocation = PendingMapLocation(fixTime, failureSerial)
                            actions.locateExplore()
                        }
                    },
                        shape = RunmoniShapes.Action,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = RunmoniColors.BlueWash,
                            contentColor = RunmoniColors.RouteBlue),
                        modifier = m.heightIn(min = 48.dp).testTag("locate_explore")) {
                        Icon(Icons.Outlined.MyLocation, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("내 위치")
                    }
                }, second = { m ->
                    OutlinedButton(onClick = {
                        panel = DiscoveryPanel.RUNNERS
                        if (canLookup) {
                            lookupAccount = state.communityUid
                            if (!state.discovery.loading) {
                                if (lookupActive) actions.refreshRunners() else explicitLookupPending = true
                            }
                        }
                    }, enabled = panel != DiscoveryPanel.RUNNERS || !canLookup || !state.discovery.loading,
                        shape = RunmoniShapes.Action,
                        modifier = m.heightIn(min = 48.dp).testTag("refresh_runners")) {
                        Icon(Icons.Outlined.Groups, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (lookupActive && panel == DiscoveryPanel.RUNNERS) "러너 새로고침" else "러너 보기")
                    }
                })
                when (panel) {
                    DiscoveryPanel.LOCATION -> {
                        CurrentAreaLabel(state.currentArea, clock, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        when (state.currentArea.phase) {
                            CurrentAreaPhase.PERMISSION_REQUIRED -> LocationSettingsAction("앱 권한 설정", actions::openAppPermissionSettings)
                            CurrentAreaPhase.LOCATION_DISABLED -> LocationSettingsAction("기기 위치 설정", actions::openLocationSettings)
                            else -> Unit
                        }
                    }
                    DiscoveryPanel.RUNNERS -> RunnerDiscoveryStatus(state, lookupActive, publicRunners.size)
                    null -> Unit
                }
                if (lookupActive) TextButton(onClick = {
                    lookupAccount = null
                    if (panel == DiscoveryPanel.RUNNERS) panel = null
                }, shape = RunmoniShapes.Action, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("hide_discovered_runners")) {
                    Text("지도에서 러너 숨기기")
                }
                val sharingEnabled = state.sharingPreferenceEnabled || state.publicLocation
                val needsProfile = panel == DiscoveryPanel.RUNNERS && !state.signedIn && !sharingEnabled
                if (canLookup || needsProfile || sharingEnabled) TextButton(
                    onClick = {
                        if (needsProfile) onProfile()
                        else actions.setPublicLocation(!sharingEnabled)
                    }, enabled = !state.sharing.busy, shape = RunmoniShapes.Action,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("toggle_runner_sharing")) {
                    Text(when {
                        needsProfile -> "프로필 연결"
                        state.sharing.busy -> "연결 중…"
                        sharingEnabled -> "공유 설정 끄기"
                        else -> "러닝 위치 공유 설정"
                    })
                }
                if (state.sharingPreferenceEnabled && !state.publicLocation) Text(
                    "공유 준비됨 · 러닝을 시작하면 약 100m 단위 위치를 공유해요.",
                    style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Primary,
                    modifier = Modifier.testTag("runner_sharing_prepared"))
                state.sharing.error?.let { Text(it, color = RunmoniColors.CoralInk, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun LocationSettingsAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, shape = RunmoniShapes.Action,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        modifier = Modifier.heightIn(min = 48.dp).testTag("explore_location_settings")) { Text(label) }
}

@Composable
private fun RunnerDiscoveryStatus(state: AppState, lookupActive: Boolean, runnerCount: Int) {
    val error = state.discovery.error
    val status = when {
        !state.signedIn -> "위치를 공유한 러너를 보려면 프로필을 연결해 주세요."
        state.communityUid.isBlank() -> "현재 연결에서는 러너 위치를 확인할 수 없어요."
        !lookupActive -> "러너 보기를 눌러 공유 위치를 확인해 주세요."
        error != null -> error
        state.discovery.loading -> "근처 러너를 확인하고 있어요…"
        state.discovery.requiresZoom -> "지도를 확대하면 근처 러너를 확인할 수 있어요."
        state.discovery.checkedAtMillis == null -> "보고 있는 지역의 위치 공유를 확인해요."
        state.discovery.partial -> "일부 러너를 표시했어요. 지도를 더 확대해 보세요."
        runnerCount == 0 -> "이 화면에 표시할 공유 위치가 없어요."
        else -> "위치를 공유한 러너 ${runnerCount}명 · 약 100m 단위"
    }
    Text(status, style = MaterialTheme.typography.bodyMedium,
        color = if (lookupActive && error != null) RunmoniColors.CoralInk else RunmoniColors.Muted,
        modifier = Modifier.fillMaxWidth().testTag("runner_discovery_status").semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
internal fun RunnerSharingConsent(state: AppState, actions: AppActions) {
    val runId = state.sharingConsentRunId ?: return
    val requestNonce = state.sharingConsentNonce
    var adult by remember(runId, requestNonce) { mutableStateOf(false) }
    DismissibleAlertDialog(onDismissRequest = { actions.dismissSharingConsent(requestNonce) }, title = { Text("근처 위치를 공유할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("러닝 중 닉네임·미니미와 약 100m 단위 위치를 다른 연결 사용자에게 보여줘요. 정확한 GPS 경로와 포인트는 공유하지 않아요.")
                Text("이 계정의 선택을 기억해요. 다음 러닝을 시작하면 공유하고, 일시정지·종료하면 위치 전송을 멈춰요. 연결이 끊기면 이전 위치가 최장 75초 남을 수 있어요.")
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(adult, role = Role.Checkbox, onValueChange = { adult = it }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(adult, onCheckedChange = null)
                    Text("성인이며 위치 공개에 동의해요.", Modifier.weight(1f))
                }
            }
        }, confirmButton = {
            TextButton(onClick = { actions.confirmPublicLocation(runId, adult, requestNonce) }, enabled = adult,
                modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_runner_sharing")) { Text(if (runId == com.runmoni.app.data.RunnerSharingPreferenceStore.NEXT_RUN) "설정 저장" else "공유 시작") }
        }, dismissButton = { TextButton(onClick = { actions.dismissSharingConsent(requestNonce) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") } })
}

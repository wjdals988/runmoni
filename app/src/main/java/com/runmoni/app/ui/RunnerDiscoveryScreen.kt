package com.runmoni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.runmoni.app.domain.*
import kotlinx.coroutines.delay

@Composable
internal fun RunnerDiscoveryScreen(state: AppState, actions: AppActions, onProfile: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(owner, actions) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) actions.setDiscoveryVisible(true)
            if (event == Lifecycle.Event.ON_STOP) actions.setDiscoveryVisible(false)
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) actions.setDiscoveryVisible(true)
        onDispose { owner.lifecycle.removeObserver(observer); actions.setDiscoveryVisible(false) }
    }
    LaunchedEffect(owner) {
        while (true) { if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) clock = System.currentTimeMillis(); delay(1000) }
    }
    val publicRunners = if (state.signedIn && state.communityUid.isNotBlank()) state.liveRunners.filter {
        it.id != state.communityUid && it.hasValidCoordinates() &&
            clock - it.updatedAtMillis in 0..RunnerPresencePolicy.MAX_MARKER_AGE_MILLIS
    } else emptyList()
    val point = state.activeRun.points.lastOrNull()?.takeIf { RunnerPresencePolicy.shareable(it, clock) }
    val own = point?.takeIf { state.activeRun.phase in listOf(RunPhase.RUNNING, RunPhase.PAUSED) }?.let {
        LiveRunner("local-self", "나", it.latitude, it.longitude, it.recordedAtMillis, state.profile.avatar)
    }
    val center = state.discoveryCenter ?: own?.let { RouteMapCenter(it.latitude, it.longitude) }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("runner_discovery")) {
        // Reserve usable map/attribution space as the region, status and actions
        // grow. A weighted map can otherwise collapse to a few pixels at 1.5x.
        val constrained = maxHeight < 520.dp || LocalDensity.current.fontScale >= 1.3f
        // Large type expands the attribution and independent map/location
        // status. Keep the map usable; the enclosing column remains scrollable.
        val mapHeight = if (LocalDensity.current.fontScale >= 1.8f) 360.dp else 280.dp
        Column(Modifier.fillMaxSize().then(if (constrained) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = pageGutter(), vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(RunmoniColors.BlueWash, RunmoniShapes.Action), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Groups, null, tint = RunmoniColors.RouteBlue, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("근처 러너", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    CurrentAreaLabel(state.currentArea, clock)
                }
            }
            MapPane(emptyList(), publicRunners + listOfNotNull(own),
                modifier = Modifier.fillMaxWidth().then(if (constrained) Modifier.height(mapHeight) else Modifier.weight(1f)),
                interactiveMap = true, followCenter = center, onBoundsChanged = actions::setDiscoveryBounds)
            val status = when {
                !state.signedIn -> "지도는 둘러볼 수 있어요. 러너를 보려면 프로필을 연결해 주세요."
                state.discovery.error != null -> state.discovery.error
                state.discovery.requiresZoom -> "지도를 확대하면 근처 러너를 확인할 수 있어요."
                state.discovery.checkedAtMillis == null -> "보고 있는 지역의 위치 공유를 확인해요."
                state.discovery.partial -> "일부 러너를 표시했어요. 지도를 더 확대해 보세요."
                publicRunners.isEmpty() -> "이 화면에 표시할 공유 위치가 없어요."
                else -> "위치를 공유한 러너 ${publicRunners.size}명 · 약 100m 단위"
            }
            Column(Modifier.fillMaxWidth().background(RunmoniColors.Paper)
                .padding(horizontal = pageGutter(), vertical = 12.dp).testTag("runner_discovery_details"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.discovery.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(status.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                    color = if (state.discovery.error != null) RunmoniColors.CoralInk else RunmoniColors.Muted,
                    modifier = Modifier.testTag("runner_discovery_status"))
                AdaptiveActions(first = { m ->
                    FilledTonalButton(onClick = actions::locateExplore, shape = RunmoniShapes.Action,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = RunmoniColors.BlueWash,
                            contentColor = RunmoniColors.RouteBlue),
                        modifier = m.heightIn(min = 48.dp).testTag("locate_explore")) {
                        Icon(Icons.Outlined.MyLocation, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("내 위치")
                    }
                }, second = { m ->
                    OutlinedButton(onClick = { if (!state.signedIn) onProfile() else actions.setPublicLocation(!state.publicLocation) },
                        enabled = !state.sharing.busy,
                        shape = RunmoniShapes.Action,
                        modifier = m.heightIn(min = 48.dp).testTag("toggle_runner_sharing")) {
                        Text(if (!state.signedIn) "프로필 연결" else if (state.sharing.busy) "연결 중…" else if (state.publicLocation) "공유 끄기" else "내 위치 공유")
                    }
                })
                state.sharing.error?.let { Text(it, color = RunmoniColors.CoralInk, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
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
                Text("일시정지·종료하면 공유를 해제해요. 연결이 끊기면 이전 위치가 최장 75초 남을 수 있어요.")
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(adult, role = Role.Checkbox, onValueChange = { adult = it }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(adult, onCheckedChange = null)
                    Text("성인이며 위치 공개에 동의해요.", Modifier.weight(1f))
                }
            }
        }, confirmButton = {
            TextButton(onClick = { actions.confirmPublicLocation(runId, adult, requestNonce) }, enabled = adult,
                modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_runner_sharing")) { Text("공유하기") }
        }, dismissButton = { TextButton(onClick = { actions.dismissSharingConsent(requestNonce) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") } })
}

package com.runmoni.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.AppActions
import com.runmoni.app.domain.AppEntryState
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.AppState
import com.runmoni.app.domain.RunPhase
import com.google.android.gms.base.R as GmsR

/** The local entry choice does not grant community membership or start a backup. */
@Composable
fun RunmoniRoot(
    state: AppState,
    actions: AppActions,
    updates: AppUpdateUiState = AppUpdateUiState(),
    onCheckUpdates: () -> Unit = {},
    onExternalAppLink: ((String) -> Unit)? = null,
) {
    if (state.entry.requiresChoice && state.activeRun.phase == RunPhase.IDLE) {
        RunmoniTheme {
            EntryScreen(state.entry, actions::startEntryGoogleSignIn, actions::continueAsGuest, state.profile.avatar)
        }
    } else {
        RunmoniApp(state, actions, updates, onCheckUpdates, onExternalAppLink)
    }
}

@Composable
internal fun EntryScreen(entry: AppEntryState, onGoogle: () -> Unit, onGuest: () -> Unit, avatar: AvatarStyle = AvatarStyle()) {
    val googleEnabled = entry.googleConfigured && !entry.googleBusy && !entry.savingChoice
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Surface(Modifier.fillMaxSize().testTag("app_entry_screen").semantics { paneTitle = "런모니 시작하기" },
        color = RunmoniColors.Cream, contentColor = RunmoniColors.Navy) {
        BoxWithConstraints(Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)).testTag("entry_safe_content")) {
            val gutter = pageGutter(maxWidth)
            val showAvatar = maxHeight >= 560.dp && !largeText
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("entry_scroll"),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = gutter, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconMark(32.dp)
                        Text("runmoni", style = MaterialTheme.typography.titleLarge, color = RunmoniColors.Primary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("런모니 시작하기", style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.semantics { heading() })
                        Text("게스트도 로그인 없이 러닝을 기록할 수 있어요.",
                            style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Navy)
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!entry.googleConfigured) EntryGuestAction(onGuest, !entry.savingChoice, primary = true)
                        EntryGoogleAction(onGoogle, googleEnabled)
                        if (entry.googleConfigured) EntryGuestAction(onGuest, !entry.savingChoice, primary = false)
                        EntryFeedback(entry)
                    }
                    Text("Google 로그인은 기록 요약 백업에 사용해요. 러닝 경로·포인트·옷장은 이 기기에 저장돼요.",
                        style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted, modifier = Modifier.testTag("entry_scope"))
                    if (showAvatar) {
                        StudioAvatarStage(avatar, Modifier.fillMaxWidth().height(112.dp), running = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryGoogleAction(onClick: () -> Unit, enabled: Boolean) {
    OutlinedButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("entry_google"),
        shape = RunmoniShapes.Action,
        border = BorderStroke(1.dp, if (enabled) RunmoniColors.Outline else RunmoniColors.Line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = RunmoniColors.Paper,
            contentColor = RunmoniColors.Navy, disabledContainerColor = RunmoniColors.Paper),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Image(painterResource(if (enabled) GmsR.drawable.googleg_standard_color_18 else GmsR.drawable.googleg_disabled_color_18),
            contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text("Google 로그인", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EntryGuestAction(onClick: () -> Unit, enabled: Boolean, primary: Boolean) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("entry_guest")
    val content: @Composable RowScope.() -> Unit = {
        Icon(Icons.Outlined.Person, null, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text("게스트로 시작", style = MaterialTheme.typography.labelLarge)
    }
    if (primary) Button(onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RunmoniShapes.Action, content = content)
    else TextButton(onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RunmoniShapes.Action, colors = ButtonDefaults.textButtonColors(contentColor = RunmoniColors.Primary), content = content)
}

/** Status follows both choices, so a pending request cannot move the Guest target. */
@Composable
private fun EntryFeedback(entry: AppEntryState) {
    when {
        entry.savingChoice || entry.googleBusy -> {
            if (rememberPageAnimationsEnabled()) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                if (entry.savingChoice) "시작하는 중" else "Google 로그인 중",
                style = MaterialTheme.typography.bodySmall,
                color = RunmoniColors.Muted,
                modifier = Modifier.testTag("entry_busy")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        !entry.issue.isNullOrBlank() -> {
            Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RunmoniShapes.Surface, modifier = Modifier.fillMaxWidth()) {
                Text(entry.issue, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp).testTag("entry_issue")
                        .semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        !entry.googleConfigured -> {
            Text("현재 Google 로그인을 사용할 수 없어요", style = MaterialTheme.typography.bodySmall,
                color = RunmoniColors.Muted, modifier = Modifier.testTag("entry_google_unavailable"))
        }
    }
}

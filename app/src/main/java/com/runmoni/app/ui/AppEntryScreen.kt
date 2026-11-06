package com.runmoni.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.runmoni.app.R
import com.runmoni.app.domain.AppActions
import com.runmoni.app.domain.AppEntryState
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
            EntryScreen(state.entry, actions::startEntryGoogleSignIn, actions::continueAsGuest)
        }
    } else {
        RunmoniApp(state, actions, updates, onCheckUpdates, onExternalAppLink)
    }
}

@Composable
internal fun EntryScreen(entry: AppEntryState, onGoogle: () -> Unit, onGuest: () -> Unit) {
    val googleEnabled = entry.googleConfigured && !entry.googleBusy && !entry.savingChoice
    Surface(
        modifier = Modifier.fillMaxSize().testTag("app_entry_screen")
            .semantics { paneTitle = "런모니 시작하기" },
        color = RunmoniColors.Cream,
        contentColor = RunmoniColors.Navy,
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .testTag("entry_safe_content"),
        ) {
            val availableHeight = maxHeight
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("entry_scroll"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(min = availableHeight)
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                ) {
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = RunmoniShapes.Surface,
                        color = colorResource(R.color.ic_launcher_background),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(96.dp))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "런모니 시작하기",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(28.dp))
                    OutlinedButton(
                        onClick = onGoogle,
                        enabled = googleEnabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("entry_google"),
                        shape = RunmoniShapes.Action,
                        border = BorderStroke(1.dp, if (googleEnabled) RunmoniColors.Outline else RunmoniColors.Line),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = RunmoniColors.Paper,
                            contentColor = RunmoniColors.Navy,
                            disabledContainerColor = RunmoniColors.Paper,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Image(
                            painterResource(if (googleEnabled) GmsR.drawable.googleg_standard_color_18 else GmsR.drawable.googleg_disabled_color_18),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Google 로그인", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onGuest,
                        enabled = !entry.savingChoice,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("entry_guest"),
                        shape = RunmoniShapes.Action,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text("게스트로 시작", style = MaterialTheme.typography.labelLarge)
                    }
                    when {
                        entry.savingChoice || entry.googleBusy -> {
                            Spacer(Modifier.height(16.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (entry.savingChoice) "시작하는 중" else "Google 로그인 중",
                                style = MaterialTheme.typography.bodySmall,
                                color = RunmoniColors.Muted,
                                modifier = Modifier.testTag("entry_busy")
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        !entry.issue.isNullOrBlank() -> {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                entry.issue,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("entry_issue")
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        !entry.googleConfigured -> {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "현재 Google 로그인을 사용할 수 없어요",
                                style = MaterialTheme.typography.bodySmall,
                                color = RunmoniColors.Muted,
                                modifier = Modifier.testTag("entry_google_unavailable"),
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Google 로그인은 기록 요약 백업에 사용해요. 러닝 경로·포인트·옷장은 이 기기에 저장돼요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RunmoniColors.Muted,
                        modifier = Modifier.testTag("entry_scope"),
                    )
                }
            }
        }
    }
}

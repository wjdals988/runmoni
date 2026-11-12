@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.runmoni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun HomeScreen(state: AppState, onRun: () -> Unit, onCrew: () -> Unit, onRecord: (RunRecord) -> Unit, onParty: (Party) -> Unit = { onCrew() }, onRetrySave: () -> Unit = {}, onWeather: () -> Unit = {}, onWeatherEntry: () -> Unit = {}) {
    val homeClock = rememberHomeClock()
    val today = java.time.Instant.ofEpochMilli(homeClock).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    LaunchedEffect(Unit) { onWeatherEntry() }
    val todaysRuns = state.records.filter { localDate(it.startedAtMillis) == today }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val todaySummary = if (!state.runPersistence.historyUnavailable && todaysRuns.isNotEmpty())
        "오늘 ${distance(todaysRuns.sumOf { it.distanceMeters })} km · ${todaysRuns.size}회" else null
    ScreenContent {
        HomeDateTime(homeClock)
        Surface(color = RunmoniColors.Paper, shape = RunmoniShapes.Surface,
            modifier = Modifier.fillMaxWidth().testTag("home_run_hero")) {
            BoxWithConstraints(Modifier.padding(if (largeText) 16.dp else 20.dp)) {
                // The measured weather layout keeps its own width beside the approved sprite.
                val besideAvatar = maxWidth >= 240.dp && !largeText
                val avatarWidth = if (maxWidth < 280.dp) 80.dp else 96.dp
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (besideAvatar) {
                        HomeHeading(todaySummary, compact = false)
                        Row(Modifier.fillMaxWidth().testTag("home_avatar_conditions_row"),
                            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeRunnerStage(state.profile.avatar, avatarWidth)
                            HomeWeatherSummary(state.weather, homeClock, Modifier.weight(1f))
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeHeading(if (largeText) null else todaySummary, compact = largeText, modifier = Modifier.weight(1f))
                            HomeRunnerStage(state.profile.avatar, if (largeText) 56.dp else 80.dp)
                        }
                    }
                    Button(onClick = onRun, shape = RunmoniShapes.Action,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RunmoniColors.Primary, contentColor = RunmoniColors.OnDark),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("home_start_run")) {
                        Icon(Icons.Outlined.PlayArrow, null, Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(if (state.activeRun.phase == RunPhase.IDLE) if (largeText) "러닝 시작" else "러닝 시작하기" else if (largeText) "러닝 보기" else "진행 중 러닝 보기",
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    }
                    if (!besideAvatar) Box(Modifier.fillMaxWidth().testTag("home_avatar_conditions_stacked")) {
                        HomeWeatherSummary(state.weather, homeClock)
                    }
                    if (largeText && todaySummary != null) Text(todaySummary,
                        style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Primary)
                }
            }
        }
        HomeWeatherDetails(state.weather, homeClock, onWeather)
        RunPersistenceNotice(state.runPersistence, onRetrySave)

        val next = state.parties.filter { it.joined && !it.cancelled && it.startsAtMillis >= System.currentTimeMillis() }.minByOrNull { it.startsAtMillis }
        if (next != null) ScreenSection("예정된 파티", action = "모두 보기", onAction = onCrew) {
            PartySummary(next) { onParty(next) }
        }
        if (state.records.isNotEmpty()) {
            ScreenSection(if (state.runPersistence.historyUnavailable) "마지막으로 불러온 러닝" else "최근 러닝") {
                Column { state.records.sortedByDescending { it.startedAtMillis }.take(2).forEach { record -> RecordRow(record) { onRecord(record) } } }
            }
        }
        ScreenSection("함께 달리기") {
            DestinationRow("크루 찾기", "", Icons.Outlined.Groups, onCrew, Modifier.testTag("home_find_rooms"))
        }
    }
}

@Composable
private fun HomeHeading(todaySummary: String?, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (compact) "러닝" else "오늘의 러닝", style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() })
        if (todaySummary != null) Text(todaySummary, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Primary)
    }
}

@Composable
private fun HomeRunnerStage(avatar: AvatarStyle, width: androidx.compose.ui.unit.Dp) {
    // The outer measured node keeps the existing layout-observation tag; the inner stage is decorative.
    Box(Modifier.width(width).height(width * 1.5f).testTag("home_avatar")) {
        StudioAvatarStage(avatar, Modifier.fillMaxSize(), running = true)
    }
}

@Composable
internal fun ExploreScreen(state: AppState, actions: AppActions, onSignIn: () -> Unit, onRooms: () -> Unit, onCrew: (String) -> Unit = { onRooms() }) {
    RunnerDiscoveryScreen(state, actions, onProfile = onSignIn)
}

@Composable
internal fun CommunityUnavailable(actions: AppActions) {
    Column(Modifier.fillMaxWidth().background(RunmoniColors.CoralWash, RunmoniShapes.Surface).padding(16.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("크루 목록을 불러오지 못했어요", style = MaterialTheme.typography.titleMedium, color = RunmoniColors.CoralInk,
            modifier = Modifier.semantics { heading() })
        Text("러닝과 내 기록은 계속 이용할 수 있어요.", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        TextButton(shape = RunmoniShapes.Action, onClick = actions::refreshCommunity, enabled = true,
            colors = ButtonDefaults.textButtonColors(contentColor = RunmoniColors.CoralInk),
            contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text("다시 불러오기"); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)) }
    }
}

@Composable
internal fun ProfileScreen(state: AppState, actions: AppActions, onRun: () -> Unit, onRecord: (RunRecord) -> Unit, onSettings: () -> Unit, updates: AppUpdateUiState, onAppInformation: () -> Unit, onCloset: () -> Unit, onProfile: () -> Unit = {}) {
    var mileageHelp by rememberSaveable { mutableStateOf(false) }
    var period by rememberSaveable { mutableIntStateOf(1) }
    var referenceEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val reference = LocalDate.ofEpochDay(referenceEpoch)
    val records = state.records.filter { val date = localDate(it.startedAtMillis); when (period) { 0 -> date == reference; 1 -> date.year == reference.year && date.month == reference.month; else -> date.year == reference.year } }
    ScreenContent {
        EditorialHeading("", "프로필")
        Surface(color = RunmoniColors.Paper, shape = RunmoniShapes.Surface) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val details: @Composable (Modifier) -> Unit = { modifier ->
                        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.profile.name, style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = onProfile, shape = RunmoniShapes.Action,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.heightIn(min = 48.dp).testTag("open_unified_profile")) {
                                Icon(Icons.Outlined.Edit, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("프로필 편집")
                            }
                        }
                    }
                    if (LocalDensity.current.fontScale >= 1.3f || maxWidth < 260.dp) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ProfileArtwork(state.profile.avatar)
                            details(Modifier.fillMaxWidth())
                        }
                    } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProfileArtwork(state.profile.avatar)
                        details(Modifier.weight(1f))
                    }
                }
                FilledTonalButton(onClick = onCloset, shape = RunmoniShapes.Action,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = RunmoniColors.MintWash, contentColor = RunmoniColors.Primary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("open_closet")) {
                    Icon(Icons.Outlined.Checkroom, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("미니미 꾸미기", modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp))
                }
            }
        }
        TextButton(shape = RunmoniShapes.Action, onClick = { mileageHelp = true },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("open_mileage_help")) {
            Text("마일리지 ${state.profile.mileage} P", style = MaterialTheme.typography.titleMedium,
                color = RunmoniColors.Navy, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Outlined.Info, "마일리지 사용 안내", Modifier.size(20.dp), tint = RunmoniColors.Muted)
        }
        ScreenSection("러닝 기록") {
            RunPersistenceNotice(state.runPersistence, actions::retryRunPersistence)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DestinationTabs(listOf("일별", "월별", "연도별"), period, { period = it }, "history_period")
                HistoryPeriodNavigation(
                    label = when (period) { 0 -> reference.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")); 1 -> reference.format(DateTimeFormatter.ofPattern("yyyy년 M월")); else -> "${reference.year}년" },
                    onPrevious = { referenceEpoch = when (period) { 0 -> reference.minusDays(1); 1 -> reference.minusMonths(1); else -> reference.minusYears(1) }.toEpochDay() },
                    onNext = { referenceEpoch = when (period) { 0 -> reference.plusDays(1); 1 -> reference.plusMonths(1); else -> reference.plusYears(1) }.toEpochDay() },
                )
            }
            Column(Modifier.fillMaxWidth().background(RunmoniColors.BlueWash, RunmoniShapes.Surface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("총 거리", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                val unavailable = state.runPersistence.historyUnavailable && state.records.isEmpty()
                DistanceNumber(if (unavailable) "—" else distance(records.sumOf { it.distanceMeters }), RunmoniColors.RouteBlue, maxSize = 48f)
                Text("km", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                HorizontalDivider(color = RunmoniColors.SecondaryMint, modifier = Modifier.padding(vertical = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (unavailable) "러닝 —회" else "러닝 ${records.size}회", style = MaterialTheme.typography.titleMedium,
                        color = RunmoniColors.RouteBlue, modifier = Modifier.testTag("history_run_count"))
                    Text(if (unavailable) "시간 —" else "시간 ${duration(records.sumOf { it.activeDurationMillis })}",
                        style = MaterialTheme.typography.titleMedium, color = RunmoniColors.Navy,
                        modifier = Modifier.testTag("history_active_duration"))
                }
                if (state.runPersistence.historyUnavailable) Text("마지막으로 불러온 기록 기준", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            }
            HorizontalDivider(color = RunmoniColors.Line)
            if (records.isEmpty() && !state.runPersistence.historyUnavailable) EmptyCard("이 기간에는 기록이 없어요", "러닝을 마치면 여기에 기록돼요.", "러닝 시작", onRun)
            if (records.isNotEmpty()) Column(Modifier.fillMaxWidth()) {
                records.sortedByDescending { it.startedAtMillis }.forEach { record -> RecordRow(record) { onRecord(record) } }
            }
        }
        ScreenSection("계정 및 앱 정보") {
            DestinationRow("설정", "계정과 러닝 옵션", Icons.Outlined.Settings, onSettings)
            AppVersionCard(updates, onAppInformation)
        }
    }
    if (mileageHelp) MileageExplanationDialog(onDismiss = { mileageHelp = false }, serverWallet = state.signedIn && !state.communityGuest)
}

@Composable
private fun ProfileArtwork(avatar: AvatarStyle) {
    StudioAvatarStage(avatar, Modifier.width(88.dp).height(132.dp), running = true)
}

@Composable
private fun HistoryPeriodNavigation(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val previous: @Composable () -> Unit = {
            IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp).testTag("history_previous")) {
                Icon(Icons.Outlined.ChevronLeft, "이전 기간")
            }
        }
        val next: @Composable () -> Unit = {
            IconButton(onClick = onNext, modifier = Modifier.size(48.dp).testTag("history_next")) {
                Icon(Icons.Outlined.ChevronRight, "다음 기간")
            }
        }
        if (LocalDensity.current.fontScale >= 1.3f || maxWidth < 320.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { previous(); next() }
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            previous()
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            next()
        }
    }
}

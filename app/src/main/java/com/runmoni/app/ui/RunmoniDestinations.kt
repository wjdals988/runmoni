@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.runmoni.app.ui

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import com.runmoni.app.avatar.AvatarArtwork
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun HomeScreen(state: AppState, onRun: () -> Unit, onCrew: () -> Unit, onExplore: () -> Unit, onRecord: (RunRecord) -> Unit, onParty: (Party) -> Unit = { onCrew() }, onRetrySave: () -> Unit = {}) {
    val today = LocalDate.now()
    val todaysRuns = state.records.filter { localDate(it.startedAtMillis) == today }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val todaySummary = if (state.runPersistence.historyUnavailable) "기록 목록 확인 필요" else if (todaysRuns.isEmpty()) "오늘 러닝 0회" else "오늘 ${distance(todaysRuns.sumOf { it.distanceMeters })} km · ${todaysRuns.size}회"
    ScreenContent {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!largeText) Text(today.format(DateTimeFormatter.ofPattern("M월 d일 E요일", Locale.KOREAN)), style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                Text("러닝", style = if (largeText) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() })
                if (!largeText) Text(todaySummary,
                    style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            }
            AvatarMini(state.profile.avatar, Modifier.width(if (largeText) 56.dp else 96.dp).height(if (largeText) 76.dp else 128.dp), running = true, decorative = true)
        }
        Button(onClick = onRun, shape = RunmoniShapes.Action, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("home_start_run")) {
            Icon(Icons.Outlined.PlayArrow, null, Modifier.size(24.dp)); Spacer(Modifier.width(10.dp))
            Text(if (state.activeRun.phase == RunPhase.IDLE) if (largeText) "러닝 시작" else "러닝 시작하기" else if (largeText) "러닝 보기" else "진행 중 러닝 보기",
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(20.dp))
        }
        if (largeText) Text(todaySummary, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        RunPersistenceNotice(state.runPersistence, onRetrySave)

        val next = state.parties.filter { it.joined && !it.cancelled && it.startsAtMillis >= System.currentTimeMillis() }.minByOrNull { it.startsAtMillis }
        if (next != null) ScreenSection("예정된 파티", action = "모두 보기", onAction = onCrew) {
            PartySummary(next) { onParty(next) }
        }
        if (state.records.isNotEmpty()) {
            ScreenSection(if (state.runPersistence.historyUnavailable) "마지막으로 불러온 러닝" else "최근 러닝") {
            state.records.sortedByDescending { it.startedAtMillis }.take(2).forEach { record -> RecordRow(record) { onRecord(record) } }
            }
        }
        ScreenSection("함께 달리기") {
            DestinationRow("크루 찾기", "크루별 파티 일정과 참가 정보", Icons.Outlined.Groups, onCrew, Modifier.testTag("home_find_rooms"))
            if (state.records.isEmpty()) DestinationRow("탐색", "크루·파티 검색과 내가 달린 길", Icons.Outlined.Explore, onExplore)
        }
        Text("러닝 기록은 이 기기에 저장돼요", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
    }
}

@Composable
internal fun ExploreScreen(state: AppState, actions: AppActions, onSignIn: () -> Unit, onRooms: () -> Unit, onCrew: (String) -> Unit = { onRooms() }) {
    LaunchedEffect(Unit) { actions.refreshCommunity() }
    var layer by rememberSaveable { mutableIntStateOf(0) }
    var kindFilter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedParty by remember { mutableStateOf<Party?>(null) }
    var mapExpanded by rememberSaveable { mutableStateOf(false) }
    var mapClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5000); mapClock = System.currentTimeMillis() } }
    val runners = state.liveRunners.filter { it.hasValidCoordinates() && mapClock - it.updatedAtMillis in 0L until 45_000L }
    val points = state.activeRun.points.ifEmpty { state.records.maxByOrNull { it.startedAtMillis }?.points.orEmpty() }
    ScreenContent {
        EditorialHeading("", "탐색")
        RoomSearch(query, { query = it }, "모임 검색", "explore_search")
        DestinationTabs(listOf("크루", "크루의 파티", "러너"), layer, { layer = it }, "explore_layer")
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("explore_loading"))
        else if (!state.serverConnected) CommunityUnavailable(actions)
        when (layer) {
            0 -> {
                val crews = state.crews.filter { "${it.name} ${it.region} ${it.paceLabel}".contains(query.trim(), true) }
                if (crews.isEmpty() && state.serverConnected && !state.busy) EmptyCard(if (query.isBlank()) "등록된 크루가 없어요" else "검색 결과가 없어요",
                    if (query.isBlank()) "크루를 만들거나 목록을 다시 불러올 수 있어요." else "이름·지역·페이스를 바꿔 검색해 보세요.", "크루 목록", onRooms)
                Column { crews.forEach { crew -> CrewRoomCard(crew) { onCrew(crew.id) } } }
            }
            1 -> {
                val parties = state.parties.filter { p -> !p.cancelled && p.startsAtMillis >= mapClock && state.crews.any { it.id == p.crewId } && "${p.title} ${p.meetingLabel} ${p.paceLabel}".contains(query.trim(), true) }.sortedBy { it.startsAtMillis }
                if (parties.isEmpty() && state.serverConnected && !state.busy) EmptyCard(if (query.isBlank()) "예정된 파티가 없어요" else "검색 결과가 없어요",
                    if (query.isBlank()) "가입한 크루에서 파티를 만들 수 있어요." else "파티 이름·장소·페이스를 바꿔 검색해 보세요.", "크루 목록", onRooms)
                Column { parties.forEach { party ->
                    Text(state.crews.first { it.id == party.crewId }.name, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Primary)
                    PartySummary(party) { selectedParty = party }
                } }
            }
            2 -> {
                SectionHeading("위치를 공유한 러너 ${runners.size}명")
                SmallNote("위치 공유에 동의한 성인 파일럿 러너만 보여요. 45초가 지난 위치는 사라져요.")
                LocationSharingToggle(state, actions)
                if (runners.isEmpty()) EmptyCard("지금 표시할 러너가 없어요", "크루에 가입해도 실시간 위치는 공유되지 않아요.")
                runners.forEach { runner ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AvatarMini(runner.avatar, Modifier.size(64.dp), decorative = true)
                        Column(Modifier.weight(1f)) { Text(runner.name, style = MaterialTheme.typography.titleMedium); Text(if (runner.memberCount > 1) "대표 미니미 · ${runner.memberCount}명" else "위치 공유 중", color = RunmoniColors.Muted, style = MaterialTheme.typography.bodyMedium) }
                        Text(clock(runner.updatedAtMillis), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        ScreenSection("내 러닝 코스") {
            DestinationRow("내가 달린 길", if (points.isEmpty()) "러닝을 기록하면 실제 경로를 확인할 수 있어요" else "최근 러닝의 GPS 경로를 확인해요", Icons.Outlined.Route, { mapExpanded = !mapExpanded }, Modifier.testTag("explore_route"))
            if (mapExpanded) MapPane(points, runners, Modifier.fillMaxWidth().height(240.dp).clip(RunmoniShapes.Surface))
        }
        TextButton(shape = RunmoniShapes.Action, onClick = actions::refreshCommunity, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("refresh_community")) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text("목록 새로고침") }
    }
    selectedParty?.let { party -> PartyDialog(state, party, actions, onSignIn = onSignIn) { selectedParty = null } }
}

@Composable
internal fun CommunityUnavailable(actions: AppActions) {
    Surface(color = RunmoniColors.Mint, shape = RunmoniShapes.Surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("크루 목록을 불러오지 못했어요", style = MaterialTheme.typography.titleMedium)
            Text("크루와 파티를 불러오지 못했어요. 혼자 달리기와 내 기록은 계속 이용할 수 있어요.", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            TextButton(shape = RunmoniShapes.Action, onClick = actions::refreshCommunity, enabled = true, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text("다시 불러오기"); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)) }
        }
    }
}

@Composable
internal fun ProfileScreen(state: AppState, actions: AppActions, onRun: () -> Unit, onRecord: (RunRecord) -> Unit, onSettings: () -> Unit, updates: AppUpdateUiState, onAppInformation: () -> Unit, onCloset: () -> Unit) {
    var mileageHelp by rememberSaveable { mutableStateOf(false) }
    var period by rememberSaveable { mutableIntStateOf(1) }
    var referenceEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val reference = LocalDate.ofEpochDay(referenceEpoch)
    val records = state.records.filter { val date = localDate(it.startedAtMillis); when (period) { 0 -> date == reference; 1 -> date.year == reference.year && date.month == reference.month; else -> date.year == reference.year } }
    ScreenContent {
        EditorialHeading("", "나의 러닝")
        ScreenSection("나의 미니미", showDivider = false) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val details: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(state.profile.name, style = MaterialTheme.typography.titleMedium)
                        Text("테스트 기간 · 아이템 모두 무료", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                        TextButton(shape = RunmoniShapes.Action, onClick = { mileageHelp = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("open_mileage_help")) { Text("${state.profile.mileage} P"); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.Info, "마일리지 사용 안내", Modifier.size(16.dp)) }
                    }
                }
                if (LocalDensity.current.fontScale >= 1.3f || maxWidth < 260.dp) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AvatarMini(state.profile.avatar, Modifier.align(Alignment.CenterHorizontally).width(72.dp).height(104.dp), running = true, decorative = true)
                        details(Modifier.fillMaxWidth())
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AvatarMini(state.profile.avatar, Modifier.width(72.dp).height(104.dp), running = true, decorative = true)
                    details(Modifier.weight(1f))
                }
            }
            FilledTonalButton(onClick = onCloset, shape = RunmoniShapes.Action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_closet")) { Icon(Icons.Outlined.Checkroom, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("미니미 꾸미기") }
        }
        ScreenSection("러닝 기록") {
            RunPersistenceNotice(state.runPersistence, actions::retryRunPersistence)
            DestinationTabs(listOf("일별", "월별", "연도별"), period, { period = it }, "history_period")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { referenceEpoch = when (period) { 0 -> reference.minusDays(1); 1 -> reference.minusMonths(1); else -> reference.minusYears(1) }.toEpochDay() }, modifier = Modifier.size(48.dp).testTag("history_previous")) { Icon(Icons.Outlined.ChevronLeft, "이전 기간") }
                Text(when (period) { 0 -> reference.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")); 1 -> reference.format(DateTimeFormatter.ofPattern("yyyy년 M월")); else -> "${reference.year}년" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { referenceEpoch = when (period) { 0 -> reference.plusDays(1); 1 -> reference.plusMonths(1); else -> reference.plusYears(1) }.toEpochDay() }, modifier = Modifier.size(48.dp).testTag("history_next")) { Icon(Icons.Outlined.ChevronRight, "다음 기간") }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("이 기간에 달린 거리", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                DistanceNumber(if (state.runPersistence.historyUnavailable && state.records.isEmpty()) "—" else distance(records.sumOf { it.distanceMeters }), RunmoniColors.Navy, maxSize = 48f)
                Text(if (state.runPersistence.historyUnavailable) "마지막으로 불러온 기록 · 목록 확인 필요" else "km · 러닝 ${records.size}회", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            }
            HorizontalDivider(color = RunmoniColors.Line)
            if (records.isEmpty() && !state.runPersistence.historyUnavailable) EmptyCard("이 기간에는 기록이 없어요", "러닝을 마치면 날짜별 기록을 확인할 수 있어요.", "러닝 시작", onRun)
            records.sortedByDescending { it.startedAtMillis }.forEach { record -> RecordRow(record) { onRecord(record) } }
        }
        ScreenSection("계정 및 앱 정보") {
            DestinationRow("계정 및 러닝 설정", "백업, 이름, 러닝 옵션을 관리해요", Icons.Outlined.Settings, onSettings)
            AppVersionCard(updates, onAppInformation)
        }
    }
    if (mileageHelp) MileageExplanationDialog(onDismiss = { mileageHelp = false }, serverWallet = state.signedIn && !state.communityGuest)
}

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.runmoni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import com.runmoni.app.avatar.AvatarArtwork
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun HomeScreen(state: AppState, onRun: () -> Unit, onCrew: () -> Unit, onExplore: () -> Unit, onRecord: (RunRecord) -> Unit) {
    val today = LocalDate.now()
    val todaysRuns = state.records.filter { localDate(it.startedAtMillis) == today }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    ScreenContent {
        if (largeText) {
            Text("오늘도, 나답게.", style = MaterialTheme.typography.headlineMedium)
        } else EditorialHeading(today.format(DateTimeFormatter.ofPattern("M월 d일 E요일", Locale.KOREAN)), "오늘도,\n나답게 달려요.",
            if (todaysRuns.isEmpty()) "빠르지 않아도 괜찮아요. 나만의 페이스로." else "오늘 ${distance(todaysRuns.sumOf { it.distanceMeters })} km를 달렸어요.")
        RunwayArtwork(state.profile.avatar, compact = largeText)
        Button(onClick = onRun, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("home_start_run")) {
            Icon(Icons.Outlined.PlayArrow, null, Modifier.size(24.dp)); Spacer(Modifier.width(10.dp))
            Text(if (state.activeRun.phase == RunPhase.IDLE) "러닝 시작하기" else "진행 중 러닝 보기", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(20.dp))
        }
        if (!AvatarArtwork.styleReady(state.profile.avatar)) SmallNote("기본 러닝복을 표시하고 있어요. 저장된 꾸미기 선택은 보관돼요.")
        Column {
            DestinationRow("함께 달리면 더 즐거워요", "크루와 파티에서 러닝 메이트를 찾아요", Icons.Outlined.Groups, onCrew, Modifier.testTag("home_find_rooms"))
            HorizontalDivider(color = RunmoniColors.Line)
        }
        val next = state.parties.filter { it.joined && !it.cancelled && it.startsAtMillis >= System.currentTimeMillis() }.minByOrNull { it.startsAtMillis }
        if (next != null) { SectionHeading("다가오는 약속", "모두 보기", onCrew); PartySummary(next, onCrew) }
        if (state.records.isNotEmpty()) {
            SectionHeading("최근의 한 걸음")
            state.records.sortedByDescending { it.startedAtMillis }.take(2).forEach { record -> RecordRow(record) { onRecord(record) } }
        } else DestinationRow("나만의 러닝을 발견해요", "함께할 모임과 내가 달린 길 둘러보기", Icons.Outlined.Explore, onExplore)
        Text("러닝 기록은 이 기기에 저장돼요", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
    }
}

@Composable
internal fun ExploreScreen(state: AppState, actions: AppActions, onSignIn: () -> Unit, onRooms: () -> Unit) {
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
        EditorialHeading("DISCOVER", "다음 러닝을\n발견하는 곳.", "함께할 사람과 달리고 싶은 순간을 찾아요.")
        RoomSearch(query, { query = it }, "모임 검색", "explore_search")
        DestinationTabs(listOf("파티", "크루", "러너"), layer, { layer = it }, "explore_layer")
        if (!state.serverConnected) CommunityUnavailable(actions)
        when (layer) {
            0 -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("전체", "현장", "원격").forEachIndexed { i, label -> FilterChip(selected = kindFilter == i, onClick = { kindFilter = i }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp)) } }
                val parties = state.parties.filter { !it.cancelled && it.startsAtMillis >= mapClock && (kindFilter == 0 || if (kindFilter == 1) it.kind == PartyKind.LOCAL else it.kind == PartyKind.REMOTE) && "${it.title} ${it.meetingLabel} ${it.paceLabel}".contains(query.trim(), true) }.sortedBy { it.startsAtMillis }
                if (parties.isEmpty() && state.serverConnected && !state.busy) EmptyCard("표시할 러닝 파티가 없어요", "다른 조건으로 찾아보거나 새로운 러닝 약속을 만들어보세요.", "파티 만들러 가기", onRooms)
                parties.forEach { party -> PartySummary(party) { selectedParty = party } }
            }
            1 -> {
                val crews = state.crews.filter { "${it.name} ${it.region} ${it.paceLabel}".contains(query.trim(), true) }
                if (crews.isEmpty() && state.serverConnected && !state.busy) EmptyCard("아직 만날 크루가 없어요", "꾸준히 함께 달릴 사람들을 모아보세요.", "크루 만들러 가기", onRooms)
                crews.forEach { crew -> CrewRoomCard(crew, onRooms) }
            }
            2 -> {
                SectionHeading("위치를 공유한 러너 ${runners.size}명")
                SmallNote("위치 공유에 동의한 성인 파일럿 러너만 보여요. 45초가 지난 위치는 사라져요.")
                LocationSharingToggle(state, actions)
                if (runners.isEmpty()) EmptyCard("지금 표시할 러너가 없어요", "일반 방에 입장해도 실시간 위치는 공유되지 않아요.")
                runners.forEach { runner ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AvatarMini(runner.avatar, Modifier.size(64.dp), decorative = true)
                        Column(Modifier.weight(1f)) { Text(runner.name, style = MaterialTheme.typography.titleMedium); Text(if (runner.memberCount > 1) "대표 미니미 · ${runner.memberCount}명" else "위치 공유 중", color = RunmoniColors.Muted, style = MaterialTheme.typography.bodyMedium) }
                        Text(clock(runner.updatedAtMillis), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        HorizontalDivider(color = RunmoniColors.Line)
        DestinationRow("내가 달린 길", if (points.isEmpty()) "러닝을 기록하면 실제 경로를 확인할 수 있어요" else "최근 러닝의 GPS 경로를 확인해요", Icons.Outlined.Route, { mapExpanded = !mapExpanded }, Modifier.testTag("explore_route"))
        if (mapExpanded) MapPane(points, runners, Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(24.dp)))
        TextButton(onClick = actions::refreshCommunity, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("refresh_community")) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text("목록 새로고침") }
    }
    selectedParty?.let { party -> PartyDialog(state, party, actions, onSignIn = onSignIn) { selectedParty = null } }
}

@Composable
internal fun CommunityUnavailable(actions: AppActions) {
    Surface(color = RunmoniColors.Mint, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("함께 달리기는 연결을 준비 중이에요", style = MaterialTheme.typography.titleMedium)
            Text("방 목록을 불러오지 못했어요. 혼자 달리기와 내 기록은 계속 이용할 수 있어요.", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            TextButton(onClick = actions::refreshCommunity, enabled = true, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text("다시 불러오기"); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)) }
        }
    }
}

@Composable
internal fun ProfileScreen(state: AppState, actions: AppActions, onRun: () -> Unit, onRecord: (RunRecord) -> Unit, onSettings: () -> Unit, updates: AppUpdateUiState, onAppInformation: () -> Unit) {
    var closet by rememberSaveable { mutableStateOf(false) }
    var mileageHelp by rememberSaveable { mutableStateOf(false) }
    var period by rememberSaveable { mutableIntStateOf(1) }
    var referenceEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val reference = LocalDate.ofEpochDay(referenceEpoch)
    val records = state.records.filter { val date = localDate(it.startedAtMillis); when (period) { 0 -> date == reference; 1 -> date.year == reference.year && date.month == reference.month; else -> date.year == reference.year } }
    ScreenContent {
        EditorialHeading("MY RUNMONI", "${state.profile.name}님의\n러닝 이야기")
        Surface(color = RunmoniColors.Paper, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AvatarMini(state.profile.avatar, Modifier.width(72.dp).height(104.dp), running = true, decorative = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("나와 함께 뛰는 미니미", style = MaterialTheme.typography.titleMedium)
                        Text("달린 만큼, 나답게 꾸며요", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                        TextButton(onClick = { mileageHelp = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("open_mileage_help")) { Text("${state.profile.mileage} P"); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.Info, "마일리지 사용 안내", Modifier.size(16.dp)) }
                    }
                }
                FilledTonalButton(onClick = { closet = true }, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_closet")) { Icon(Icons.Outlined.Checkroom, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("미니미 꾸미기") }
                if (!AvatarArtwork.styleReady(state.profile.avatar)) SmallNote("기존 선택은 보관 중이에요. 현재는 기본 러닝복을 표시해요.")
            }
        }
        SectionHeading("차곡차곡, 나의 기록")
        DestinationTabs(listOf("일별", "월별", "연도별"), period, { period = it }, "history_period")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { referenceEpoch = when (period) { 0 -> reference.minusDays(1); 1 -> reference.minusMonths(1); else -> reference.minusYears(1) }.toEpochDay() }, modifier = Modifier.size(48.dp).testTag("history_previous")) { Icon(Icons.Outlined.ChevronLeft, "이전 기간") }
            Text(when (period) { 0 -> reference.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")); 1 -> reference.format(DateTimeFormatter.ofPattern("yyyy년 M월")); else -> "${reference.year}년" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { referenceEpoch = when (period) { 0 -> reference.plusDays(1); 1 -> reference.plusMonths(1); else -> reference.plusYears(1) }.toEpochDay() }, modifier = Modifier.size(48.dp).testTag("history_next")) { Icon(Icons.Outlined.ChevronRight, "다음 기간") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("이 기간에 달린 거리", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            DistanceNumber(distance(records.sumOf { it.distanceMeters }), RunmoniColors.Navy, maxSize = 48f)
            Text("km · 러닝 ${records.size}회", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        }
        HorizontalDivider(color = RunmoniColors.Line)
        if (records.isEmpty()) EmptyCard("이 기간에는 아직 기록이 없어요", "첫 걸음부터 여기에 차곡차곡 쌓일 거예요.", "러닝하러 가기", onRun)
        records.sortedByDescending { it.startedAtMillis }.forEach { record -> RecordRow(record) { onRecord(record) } }
        DestinationRow("계정 및 러닝 설정", "백업, 이름, 러닝 옵션을 관리해요", Icons.Outlined.Settings, onSettings)
        AppVersionCard(updates, onAppInformation)
    }
    if (closet) ClosetDialog(state.profile, actions) { closet = false }
    if (mileageHelp) MileageExplanationDialog(onDismiss = { mileageHelp = false }, serverWallet = state.signedIn && !state.communityGuest)
}

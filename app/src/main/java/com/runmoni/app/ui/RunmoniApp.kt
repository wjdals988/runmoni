@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.runmoni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runmoni.app.R
import com.runmoni.app.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun RunmoniApp(state: AppState, actions: AppActions) {
    RunmoniTheme {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var settings by rememberSaveable { mutableStateOf(false) }
        var signIn by rememberSaveable { mutableStateOf(false) }
        var selectedRecord by remember { mutableStateOf<RunRecord?>(null) }
        var lastActiveId by remember { mutableStateOf(state.activeRun.id.takeIf { it.isNotBlank() }) }
        var completedId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(state.activeRun.phase, state.activeRun.id) {
            if (state.activeRun.phase == RunPhase.IDLE) {
                lastActiveId?.let { completedId = it }
                lastActiveId = null
            } else if (state.activeRun.id.isNotBlank()) lastActiveId = state.activeRun.id
        }
        LaunchedEffect(completedId, state.records) {
            completedId?.let { id -> state.records.firstOrNull { it.id == id }?.let { selectedRecord = it; completedId = null } }
        }
        val tabs = listOf("홈", "탐색", "러닝", "크루", "나")
        val icons = listOf(R.drawable.ic_nav_home, R.drawable.ic_nav_explore, R.drawable.ic_nav_run, R.drawable.ic_nav_crew, R.drawable.ic_nav_me)
        Scaffold(
            containerColor = RunmoniColors.Cream,
            topBar = {
                Column(Modifier.statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("runmoni", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                            Text("나의 러닝, 우리의 리듬", style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted)
                        }
                        StatusPill(if (state.serverConnected && state.signedIn) "파일럿 연결됨" else if (state.serverConnected) "로그인 필요" else "기기 기록 모드", if (state.serverConnected && state.signedIn) RunmoniColors.Green else RunmoniColors.Muted, "backend_status")
                        IconButton(onClick = { settings = true }, modifier = Modifier.testTag("settings_button")) { Icon(Icons.Outlined.Settings, "계정 및 서버 설정") }
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            bottomBar = {
                NavigationBar(containerColor = RunmoniColors.Paper, tonalElevation = 0.dp) {
                    tabs.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(painterResource(icons[index]), label, Modifier.size(24.dp)) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = RunmoniColors.CoralSoft, selectedIconColor = RunmoniColors.Navy, selectedTextColor = RunmoniColors.Navy, unselectedIconColor = RunmoniColors.Muted),
                            modifier = Modifier.testTag(listOf("nav_home", "nav_explore", "nav_run", "nav_crew", "nav_me")[index]),
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(state, onRun = { tab = 2 }, onCrew = { tab = 3 }, onExplore = { tab = 1 }, onRecord = { selectedRecord = it })
                    1 -> ExploreScreen(state, actions, onSignIn = { signIn = true })
                    2 -> RunningScreen(state, actions, onCrew = { tab = 3 })
                    3 -> CrewScreen(state, actions, onSignIn = { signIn = true })
                    4 -> ProfileScreen(state, actions, onRecord = { selectedRecord = it }, onSettings = { settings = true })
                }
            }
        }
        if (settings) SettingsDialog(state, actions, onDismiss = { settings = false }, onSignIn = { settings = false; signIn = true })
        if (signIn) SignInDialog(state, actions) { signIn = false }
        if (state.openedRoomId.isNotBlank()) ChatDialog(state, actions)
        selectedRecord?.let { record -> RecordDialog(state.records.firstOrNull { it.id == record.id } ?: record, actions, state.cloud) { selectedRecord = null } }
        state.error?.let { error -> AlertDialog(onDismissRequest = actions::dismissError, title = { Text("확인이 필요해요") }, text = { Text(error) }, confirmButton = { TextButton(onClick = actions::dismissError) { Text("확인") } }) }
    }
}

@Composable
private fun HomeScreen(state: AppState, onRun: () -> Unit, onCrew: () -> Unit, onExplore: () -> Unit, onRecord: (RunRecord) -> Unit) {
    ScreenContent {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("안녕하세요, ${state.profile.name} 님", style = MaterialTheme.typography.titleMedium)
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)), style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted)
        }
        Surface(color = RunmoniColors.Navy, shape = RoundedCornerShape(26.dp)) {
            Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("TODAY'S RUN", style = MaterialTheme.typography.labelMedium, color = Color(0xFFA7CEC3))
                    Text("오늘도\n함께, 나답게.", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text("혼자 가볍게, 함께 즐겁게", color = Color(0xFFD3E3E6), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onRun, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 50.dp).testTag("home_start_run")) {
                        Text(if (state.activeRun.phase == RunPhase.IDLE) "러닝 시작하기" else "진행 중 러닝 보기")
                        Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
                AvatarMini(state.profile.avatar, Modifier.width(110.dp).height(162.dp), running = true)
            }
        }
        val weekStart = LocalDate.now().minusDays(LocalDate.now().dayOfWeek.value.toLong() - 1)
        val week = state.records.filter { localDate(it.startedAtMillis) >= weekStart }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile("이번 주 거리", distance(week.sumOf { it.distanceMeters }), "km", Modifier.weight(1f))
            MetricTile("이번 주 러닝", week.size.toString(), "회", Modifier.weight(1f))
            MetricTile("러닝 마일리지", state.profile.mileage.toString(), "P", Modifier.weight(1f))
        }
        SectionHeading("다음에 함께 뛸까요", "크루 보기", onCrew)
        val next = state.parties.filter { it.joined && !it.cancelled && it.startsAtMillis >= System.currentTimeMillis() }.minByOrNull { it.startsAtMillis }
        if (next == null) EmptyCard("아직 참가한 파티가 없어요", "내 페이스에 맞는 모임을 찾아보세요", "파티 찾아보기", onExplore)
        else PartySummary(next, onClick = onCrew)
        SectionHeading("최근 러닝")
        state.records.maxByOrNull { it.startedAtMillis }?.let { RecordRow(it) { onRecord(it) } }
            ?: EmptyCard("첫 번째 러닝을 기다리고 있어요", "휴대폰에서 기록한 실제 러닝만 표시해요")
        if (!state.serverConnected) SmallNote("혼자 뛰는 기록은 기기에 저장돼요. 크루·파티·음성 기능은 서버 연결 후 사용할 수 있어요.")
    }
}

@Composable
private fun ExploreScreen(state: AppState, actions: AppActions, onSignIn: () -> Unit) {
    var layer by rememberSaveable { mutableIntStateOf(0) }
    var kindFilter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedParty by remember { mutableStateOf<Party?>(null) }
    var mapClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5000); mapClock = System.currentTimeMillis() } }
    val visibleRunners = state.liveRunners.filter { it.hasValidCoordinates() && mapClock - it.updatedAtMillis in 0L until 45_000L }
    ScreenContent {
        ScreenHeading("함께 뛰는 곳", "내 주변의 러닝, 그리고 멀리서 함께하는 러닝")
        MapPane(state.activeRun.points.ifEmpty { state.records.maxByOrNull { it.startedAtMillis }?.points.orEmpty() }, visibleRunners, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)))
        SmallNote("지도 공유는 초대받은 성인 테스터에게만 보여요. 공유에 동의한 진행 중 러닝의 실제 위치예요.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("파티", "크루", "러너").forEachIndexed { index, name -> FilterChip(selected = layer == index, onClick = { layer = index }, label = { Text(name) }) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = actions::refreshCommunity, modifier = Modifier.testTag("refresh_community")) { Icon(Icons.Outlined.Refresh, "모임 새로고침") }
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("이름, 지역, 페이스로 찾기") }, shape = RoundedCornerShape(16.dp))
        if (!state.serverConnected || !state.signedIn) CommunityNotice(state, onSignIn)
        when (layer) {
            0 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("전체", "현장", "원격").forEachIndexed { index, label -> FilterChip(selected = kindFilter == index, onClick = { kindFilter = index }, label = { Text(label) }) } }
                val parties = state.parties.filter { !it.cancelled && (kindFilter == 0 || if (kindFilter == 1) it.kind == PartyKind.LOCAL else it.kind == PartyKind.REMOTE) && (query.isBlank() || "${it.title} ${it.meetingLabel} ${it.paceLabel}".contains(query, true)) }
                if (parties.isEmpty()) EmptyCard("표시할 러닝 파티가 없어요", "연결된 서버의 실제 모집 일정이 여기에 표시돼요")
                parties.sortedBy { it.startsAtMillis }.forEach { party -> PartySummary(party) { selectedParty = party } }
            }
            1 -> {
                val crews = state.crews.filter { query.isBlank() || "${it.name} ${it.region} ${it.paceLabel}".contains(query, true) }
                if (crews.isEmpty()) EmptyCard("아직 만날 크루가 없어요", "크루를 만들거나, 초대 파일럿에 연결해 주세요")
                crews.forEach { crew -> CrewCard(crew, onJoin = { if (state.signedIn) actions.joinCrew(crew.id) else onSignIn() }) }
            }
            2 -> {
                SectionHeading("위치를 공유한 러너 ${visibleRunners.size}명")
                SmallNote("연결이 끊긴 위치는 마지막 갱신 후 45초가 지나면 지도에서 사라져요.")
                LocationSharingToggle(state, actions)
                if (visibleRunners.isEmpty()) EmptyCard("지금 표시할 러너가 없어요", "샘플 러너나 가상의 위치는 표시하지 않아요")
                visibleRunners.forEach { runner ->
                    Surface(shape = RoundedCornerShape(18.dp), color = RunmoniColors.Paper) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            AvatarMini(runner.avatar, Modifier.size(56.dp))
                            Column(Modifier.weight(1f)) { Text(runner.name, style = MaterialTheme.typography.titleMedium); Text(if (runner.memberCount > 1) "대표 미니미 · ${runner.memberCount}명" else "위치 공유 중", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted) }
                            Text(clock(runner.updatedAtMillis), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
    selectedParty?.let { party -> PartyDialog(state, party, actions, onSignIn = onSignIn) { selectedParty = null } }
}

@Composable
private fun RunningScreen(state: AppState, actions: AppActions, onCrew: () -> Unit) {
    var finishConfirmation by rememberSaveable { mutableStateOf(false) }
    val run = state.activeRun
    ScreenContent {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ScreenHeading(if (run.phase == RunPhase.IDLE) "가볍게, 출발" else "내 리듬으로 달리는 중", if (run.phase == RunPhase.IDLE) "GPS를 확인하고 오늘의 러닝을 시작하세요" else when (run.phase) { RunPhase.PAUSED -> "잠시 쉬어가는 중이에요"; RunPhase.RECOVERY -> "저장된 러닝을 이어갈 수 있어요"; else -> "기록은 휴대폰에 먼저 저장돼요" })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatusPill(run.gpsStatus, if (run.accuracyMeters != null && run.accuracyMeters <= 30f) RunmoniColors.Green else RunmoniColors.Muted, "gps_status")
            run.accuracyMeters?.let { Text("정확도 ±${it.toInt()}m", style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted) }
        }
        Surface(shape = RoundedCornerShape(26.dp), color = RunmoniColors.Paper) {
            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("달린 거리", color = RunmoniColors.Muted, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.Bottom) { Text(distance(run.distanceMeters), fontSize = 62.sp, lineHeight = 68.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.testTag("run_distance")); Text(" km", Modifier.padding(bottom = 9.dp), style = MaterialTheme.typography.titleLarge, color = RunmoniColors.Muted) }
                HorizontalDivider(color = RunmoniColors.Line)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    PlainMetric("러닝 시간", duration(run.activeDurationMillis), "run_duration")
                    PlainMetric("현재 페이스", pace(run.currentPaceSecondsPerKm), "run_pace")
                    PlainMetric("평균 페이스", averagePace(run.distanceMeters, run.activeDurationMillis))
                }
            }
        }
        MapPane(run.points, modifier = Modifier.fillMaxWidth().height(188.dp).clip(RoundedCornerShape(22.dp)))
        when (run.phase) {
            RunPhase.IDLE -> Button(onClick = actions::startRun, modifier = Modifier.fillMaxWidth().height(66.dp).testTag("start_run"), shape = RoundedCornerShape(20.dp)) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("러닝 시작", style = MaterialTheme.typography.titleLarge) }
            RunPhase.RUNNING -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = actions::pauseRun, modifier = Modifier.weight(1f).height(62.dp).testTag("pause_run"), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Outlined.Pause, null); Text(" 일시정지") }
                OutlinedButton(onClick = { finishConfirmation = true }, modifier = Modifier.weight(1f).height(62.dp).testTag("finish_run"), shape = RoundedCornerShape(18.dp)) { Text("러닝 마치기") }
            }
            RunPhase.PAUSED -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = actions::resumeRun, modifier = Modifier.weight(1f).height(62.dp).testTag("resume_run"), shape = RoundedCornerShape(18.dp)) { Text("계속 달리기") }
                OutlinedButton(onClick = { finishConfirmation = true }, modifier = Modifier.weight(1f).height(62.dp).testTag("finish_run"), shape = RoundedCornerShape(18.dp)) { Text("러닝 마치기") }
            }
            RunPhase.RECOVERY -> {
                SmallNote("앱이 중단되기 전 저장한 지점까지만 복구해요. 중단된 구간의 거리를 추정해 더하지 않아요.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = actions::recoverRun, modifier = Modifier.weight(1f).height(58.dp).testTag("recover_run")) { Text("이어 달리기") }
                    OutlinedButton(onClick = { finishConfirmation = true }, modifier = Modifier.weight(1f).height(58.dp)) { Text("저장하고 마치기") }
                }
            }
        }
        ToggleRow("자동 일시정지", "멈춰 있을 때 러닝 시간을 잠시 멈춰요", state.autoPause, actions::setAutoPause)
        ToggleRow("음성 기록 안내", "거리별 기록을 음성으로 들어요", state.voiceGuidance, actions::setVoiceGuidance)
        SectionHeading("함께 듣고 이야기해요")
        val joined = state.parties.firstOrNull { it.joined && !it.cancelled }
        if (joined != null || state.audio.roomId.isNotBlank()) AudioPanel(state.audio, actions, state.audio.roomId.ifBlank { "party:${joined!!.id}" })
        else EmptyCard("참가한 파티의 음성방을 연결하세요", "GPS 기록과 음성·음악 연결은 별도로 동작해요", "크루와 파티 보기", onCrew)
    }
    if (finishConfirmation) AlertDialog(onDismissRequest = { finishConfirmation = false }, title = { Text("오늘의 러닝을 마칠까요?") }, text = { Text("${distance(run.distanceMeters)} km · ${duration(run.activeDurationMillis)}\n저장한 기록은 ‘나’에서 다시 볼 수 있어요.") }, confirmButton = { TextButton(onClick = { finishConfirmation = false; actions.finishRun() }, modifier = Modifier.testTag("confirm_finish_run")) { Text("저장하고 마치기") } }, dismissButton = { TextButton(onClick = { finishConfirmation = false }) { Text("계속 뛰기") } })
}

@Composable
private fun CrewScreen(state: AppState, actions: AppActions, onSignIn: () -> Unit) {
    var newCrew by rememberSaveable { mutableStateOf(false) }
    var selectedCrewId by rememberSaveable { mutableStateOf<String?>(null) }
    var partyCrewId by rememberSaveable { mutableStateOf<String?>(null) }
    var manageCrewId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedParty by remember { mutableStateOf<Party?>(null) }
    val selected = state.crews.firstOrNull { it.id == selectedCrewId }
    ScreenContent {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { ScreenHeading("함께라서, 더 멀리", "페이스가 다른 날에도 같은 크루") }
            FilledTonalIconButton(onClick = { if (state.signedIn) newCrew = true else onSignIn() }, modifier = Modifier.testTag("create_crew")) { Icon(Icons.Outlined.Add, "크루 만들기") }
        }
        if (!state.serverConnected) CommunityNotice(state, onSignIn)
        val crews = state.crews.filter { it.joined }
        if (crews.isEmpty()) EmptyCard("내 크루를 만나보세요", "크루 가입 후 공지·채팅·파티·음성방을 이용해요", if (state.signedIn) "새 크루 만들기" else "초대 파일럿 로그인", { if (state.signedIn) newCrew = true else onSignIn() })
        crews.forEach { crew ->
            Surface(shape = RoundedCornerShape(22.dp), color = if (selected?.id == crew.id) RunmoniColors.CoralSoft else RunmoniColors.Paper, modifier = Modifier.clickable { selectedCrewId = if (selected?.id == crew.id) null else crew.id }.testTag("crew_${crew.id}")) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Groups, null, Modifier.size(38.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) { Text(crew.name, style = MaterialTheme.typography.titleMedium); Text("${crew.memberCount}명 · ${crew.region.ifBlank { "원격 가능" }} · ${crew.paceLabel}", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted) }
                    StatusPill(if (crew.isOwner) "크루장" else if (crew.isDj) "DJ" else "멤버", RunmoniColors.Green)
                }
            }
        }
        selected?.let { crew ->
            Text(crew.description, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions.openChat("crew:${crew.id}", crew.name) }, modifier = Modifier.weight(1f)) { Text("공지 · 채팅") }
                OutlinedButton(onClick = { partyCrewId = crew.id }, enabled = crew.isOwner, modifier = Modifier.weight(1f).testTag("create_party_${crew.id}")) { Text("파티 만들기") }
            }
            AudioPanel(state.audio, actions, "crew:${crew.id}")
            if (!crew.isOwner) SmallNote("파일럿에서는 크루장이 파티를 만들고 운영해요.")
            if (crew.isOwner) OutlinedButton(onClick = { manageCrewId = crew.id }, modifier = Modifier.fillMaxWidth().testTag("manage_crew_members")) { Text("멤버 · DJ · 초대 관리") }
            if (crew.isOwner) SmallNote("크루장은 파일럿에서 크루를 탈퇴할 수 없어요.")
            else TextButton(onClick = { actions.leaveCrew(crew.id); selectedCrewId = null }, modifier = Modifier.testTag("leave_crew_${crew.id}")) { Text("이 크루 나가기", color = RunmoniColors.Muted) }
        }
        SectionHeading("나의 러닝 파티")
        val parties = state.parties.filter { it.joined || it.crewId in crews.map(Crew::id) }
        if (parties.isEmpty()) EmptyCard("아직 예정된 파티가 없어요", "현장 모임도, 원격 함께 뛰기도 만들 수 있어요")
        parties.sortedBy { it.startsAtMillis }.forEach { party -> PartySummary(party) { selectedParty = party } }
    }
    if (newCrew) NewCrewDialog(actions) { newCrew = false }
    partyCrewId?.let { crewId -> NewPartyDialog(crewId, actions) { partyCrewId = null } }
    selectedParty?.let { party -> PartyDialog(state, party, actions, onSignIn = onSignIn) { selectedParty = null } }
    manageCrewId?.let { crewId -> CrewMembersDialog(state, crewId, actions) { manageCrewId = null } }
}

@Composable
private fun ProfileScreen(state: AppState, actions: AppActions, onRecord: (RunRecord) -> Unit, onSettings: () -> Unit) {
    var closet by rememberSaveable { mutableStateOf(false) }
    var period by rememberSaveable { mutableIntStateOf(1) }
    var referenceEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val reference = LocalDate.ofEpochDay(referenceEpoch)
    val records = state.records.filter { val date = localDate(it.startedAtMillis); when (period) { 0 -> date == reference; 1 -> date.year == reference.year && date.month == reference.month; else -> date.year == reference.year } }
    ScreenContent {
        ScreenHeading("나의 러닝 이야기", "차곡차곡 쌓이는 나만의 기록")
        Surface(shape = RoundedCornerShape(24.dp), color = RunmoniColors.Paper) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(116.dp).clip(RoundedCornerShape(22.dp)).background(RunmoniColors.Mint)) { AvatarMini(state.profile.avatar, Modifier.fillMaxSize().padding(8.dp)) }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(state.profile.name, style = MaterialTheme.typography.titleLarge)
                    Text("${state.profile.mileage} P · ${if (state.signedIn) "서버" else "기기"} 마일리지", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                    FilledTonalButton(onClick = { closet = true }, modifier = Modifier.testTag("open_closet")) { Icon(Icons.Outlined.Checkroom, null, Modifier.size(18.dp)); Text(" 미니미 꾸미기") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("일별", "월별", "연도별").forEachIndexed { index, label -> FilterChip(selected = period == index, onClick = { period = index }, label = { Text(label) }, modifier = Modifier.testTag("history_period_$index")) } }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { referenceEpoch = when (period) { 0 -> reference.minusDays(1); 1 -> reference.minusMonths(1); else -> reference.minusYears(1) }.toEpochDay() }, modifier = Modifier.testTag("history_previous")) { Icon(Icons.Outlined.ChevronLeft, "이전 기간") }
            Text(when (period) { 0 -> reference.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")); 1 -> reference.format(DateTimeFormatter.ofPattern("yyyy년 M월")); else -> "${reference.year}년" }, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { referenceEpoch = when (period) { 0 -> reference.plusDays(1); 1 -> reference.plusMonths(1); else -> reference.plusYears(1) }.toEpochDay() }, modifier = Modifier.testTag("history_next")) { Icon(Icons.Outlined.ChevronRight, "다음 기간") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile("달린 거리", distance(records.sumOf { it.distanceMeters }), "km", Modifier.weight(1f))
            MetricTile("러닝 횟수", records.size.toString(), "회", Modifier.weight(1f))
        }
        SectionHeading("러닝 기록", "설정", onSettings)
        if (records.isEmpty()) EmptyCard("이 기간에는 아직 기록이 없어요", "기록한 러닝을 일별·월별·연도별로 확인해요")
        records.sortedByDescending { it.startedAtMillis }.forEach { record -> RecordRow(record) { onRecord(record) } }
        SmallNote(if (state.signedIn) "서버 마일리지는 서버에서 GPS 기록을 검증해 계산한 파일럿 포인트예요. 유료 재화는 아니에요." else "기기 마일리지는 휴대폰에서 GPS 기록을 계산한 파일럿 포인트예요. 로그인 후 서버 검증 결과와 다를 수 있어요.")
    }
    if (closet) ClosetDialog(state.profile, actions) { closet = false }
}

@Composable
internal fun AudioPanel(audio: AudioUiState, actions: AppActions, roomId: String) {
    var sourcePicker by remember(roomId) { mutableStateOf(false) }
    val thisRoom = audio.connected && audio.roomId == roomId
    val canBroadcast = audio.canDj && thisRoom
    LaunchedEffect(canBroadcast) { if (!canBroadcast) sourcePicker = false }
    Surface(shape = RoundedCornerShape(20.dp), color = RunmoniColors.Paper) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Headphones, null); Spacer(Modifier.width(8.dp)); Text("음성 · 함께 듣기", style = MaterialTheme.typography.titleMedium) }
            Text(audio.status, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (thisRoom) actions.disconnectVoice() else actions.connectVoice(roomId) }, enabled = !audio.connecting, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("join_voice")) { Text(if (thisRoom) "음성방 나가기" else if (audio.connecting) "연결 중" else "음성방 참여") }
                OutlinedButton(onClick = { actions.setMicrophone(!audio.microphoneEnabled) }, enabled = thisRoom, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("toggle_microphone")) { Icon(if (audio.microphoneEnabled) Icons.Outlined.Mic else Icons.Outlined.MicOff, null, Modifier.size(18.dp)); Text(if (audio.microphoneEnabled) " 마이크 켜짐" else " 마이크 꺼짐") }
            }
            if (thisRoom) {
                Text("음성 음량", style = MaterialTheme.typography.labelMedium)
                Slider(value = audio.voiceVolume, onValueChange = actions::setVoiceVolume, modifier = Modifier.testTag("voice_volume"))
                Text("음악 음량", style = MaterialTheme.typography.labelMedium)
                Slider(value = audio.musicVolume, onValueChange = actions::setMusicVolume, modifier = Modifier.testTag("music_volume"))
            }
            Text(audio.sourceLabel, style = MaterialTheme.typography.bodyMedium)
            if (canBroadcast) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { actions.shareExternalMusic("com.google.android.apps.youtube.music") }, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("share_youtube_music")) { Text("YouTube Music") }
                    OutlinedButton(onClick = { actions.shareExternalMusic("com.sec.android.app.music") }, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("share_samsung_music")) { Text("Samsung Music") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = actions::selectMusicFile, modifier = Modifier.weight(1f).testTag("select_music")) { Text("음악 파일 선택") }
                    OutlinedButton(onClick = { sourcePicker = true }, modifier = Modifier.weight(1f).testTag("other_music_apps")) { Text("다른 음악 앱") }
                }
                if (audio.broadcasting) TextButton(onClick = actions::stopMusic, modifier = Modifier.testTag("stop_music")) { Text("음악 공유 중지") }
                SmallNote("외부 앱이 오디오 캡처를 허용해야 해요. 무음이거나 잠금 중 공유가 중단되면 안내 상태를 확인해 주세요.")
            } else if (thisRoom) SmallNote("음악 방송은 크루장과 DJ가 제어해요. 내 음량은 따로 바꿀 수 있어요.")
        }
    }
    if (sourcePicker && canBroadcast) AlertDialog(onDismissRequest = { sourcePicker = false }, title = { Text("공유할 음악 앱") }, text = { Column {
        listOf("Spotify" to "com.spotify.music", "멜론" to "com.iloen.melon", "지니" to "com.ktmusic.geniemusic", "FLO" to "skplanet.musicmate").forEach { (name, packageName) -> TextButton(onClick = { actions.shareExternalMusic(packageName); sourcePicker = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("share_music_$packageName")) { Text(name) } }
        SmallNote("각 앱이 캡처를 허용해야 실제 오디오가 전달돼요. 지원 여부는 기기에서 확인해 주세요.")
    } }, confirmButton = { TextButton(onClick = { sourcePicker = false }) { Text("닫기") } })
}

@Composable
internal fun ScreenContent(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable
internal fun ScreenHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.headlineMedium); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted) }
}

@Composable
internal fun SectionHeading(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); if (action != null) TextButton(onClick = onClick) { Text(action) } }
}

@Composable
internal fun StatusPill(label: String, color: Color, tag: String = "") {
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(50), modifier = Modifier.testTag(tag)) { Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(5.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(5.dp)); Text(label, color = color, style = MaterialTheme.typography.labelMedium) } }
}

@Composable
internal fun MetricTile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = RunmoniColors.Paper) { Column(Modifier.padding(horizontal = 14.dp, vertical = 17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted); Text(value, style = MaterialTheme.typography.titleLarge); Text(unit, style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted) } }
}

@Composable
private fun PlainMetric(label: String, value: String, tag: String = "") {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted); Text(value, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag(tag)) }
}

@Composable
internal fun EmptyCard(title: String, message: String, action: String? = null, onClick: () -> Unit = {}) {
    Surface(shape = RoundedCornerShape(22.dp), color = RunmoniColors.Paper, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(message, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted); if (action != null) TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text(action); Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(17.dp)) } } }
}

@Composable
internal fun SmallNote(message: String) { Text(message, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted) }

@Composable
internal fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit, enabled: Boolean = true, tag: String = "") {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted) }; Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled, modifier = Modifier.semantics { contentDescription = title }.testTag(tag)) }
}

@Composable
internal fun LocationSharingToggle(state: AppState, actions: AppActions) {
    var confirmation by remember { mutableStateOf(false) }
    LaunchedEffect(state.signedIn) { if (!state.signedIn) confirmation = false }
    ToggleRow("파일럿 위치 공유", if (state.signedIn) "초대받은 테스터에게 러닝 중 정밀 위치를 공유해요" else "초대 파일럿 로그인 후 이용할 수 있어요", state.publicLocation, { if (it) confirmation = true else actions.setPublicLocation(false) }, enabled = state.signedIn, tag = "share_location")
    if (confirmation && state.signedIn) AlertDialog(onDismissRequest = { confirmation = false }, title = { Text("러닝 위치를 공유할까요?") }, text = { Text("진행 중 러닝의 정밀 좌표, 러너 이름, 미니미를 같은 초대 파일럿의 성인 테스터에게 보여줘요. 인터넷 전체에 공개되는 지도는 아니에요.\n\n러닝 중 15초마다 위치를 갱신해요. 공유를 끄거나 러닝을 마치면 공유를 중지해요. 마지막 갱신 후 45초가 지나면 위치가 만료돼요.") }, confirmButton = { TextButton(onClick = { confirmation = false; actions.setPublicLocation(true) }, modifier = Modifier.testTag("confirm_share_location")) { Text("위치 공유 켜기") } }, dismissButton = { TextButton(onClick = { confirmation = false }, modifier = Modifier.testTag("cancel_share_location")) { Text("공유하지 않기") } })
}

@Composable
private fun CommunityNotice(state: AppState, onSignIn: () -> Unit) { EmptyCard("초대 파일럿 연결이 필요해요", state.serverStatus, if (!state.signedIn) "초대코드로 로그인" else null, onSignIn) }

@Composable
private fun CrewCard(crew: Crew, onJoin: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = RunmoniColors.Paper) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Text(crew.name, style = MaterialTheme.typography.titleLarge); Text(crew.description, style = MaterialTheme.typography.bodyMedium); Text("${crew.region} · ${crew.paceLabel} · ${crew.memberCount}명", style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted); if (!crew.joined) FilledTonalButton(onClick = onJoin, modifier = Modifier.testTag("join_crew_${crew.id}")) { Text("크루 가입") } else StatusPill("가입한 크루", RunmoniColors.Green) } }
}

@Composable
internal fun PartySummary(party: Party, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = RunmoniColors.Paper, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { StatusPill(if (party.kind == PartyKind.LOCAL) "현장 러닝" else "원격 러닝", RunmoniColors.Green); Spacer(Modifier.weight(1f)); Text(if (party.cancelled) "취소됨" else if (party.joined) "참가 중" else "${party.participantCount}/${party.capacity}명", style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted) }; Text(party.title, style = MaterialTheme.typography.titleLarge); Text(dateTime(party.startsAtMillis), style = MaterialTheme.typography.bodyMedium); Text("${party.distanceKm} km · ${party.paceLabel}${if (party.meetingLabel.isNotBlank()) " · ${party.meetingLabel}" else ""}", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted) } }
}

@Composable
private fun RecordRow(record: RunRecord, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = RunmoniColors.Paper, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("record_${record.id}")) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(dateTime(record.startedAtMillis), style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted); Text("${distance(record.distanceMeters)} km", style = MaterialTheme.typography.titleLarge); Text("${duration(record.activeDurationMillis)} · ${averagePace(record.distanceMeters, record.activeDurationMillis)} /km", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted) }; StatusPill(if (record.synced) "동기화됨" else "기기 저장", if (record.synced) RunmoniColors.Green else RunmoniColors.Muted); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp)) } }
}

internal fun topColor(variant: Int): Color = when (variant % 4) { 1 -> RunmoniColors.Green; 2 -> Color(0xFF547FB3); 3 -> Color(0xFFD6AE48); else -> RunmoniColors.Coral }
@Composable
internal fun AvatarMini(avatar: AvatarStyle, modifier: Modifier, running: Boolean = false) = MiniMi(modifier, topColor(avatar.top), accessoryName(avatar.accessory), running, avatar.hair, avatar.bottom, avatar.shoes)
internal fun accessoryName(variant: Int) = when (variant) { 1 -> "headband"; 2 -> "cap"; 3 -> "headphones"; else -> "none" }
internal fun distance(meters: Double) = String.format(Locale.KOREAN, "%.2f", meters / 1000)
internal fun duration(millis: Long): String { val seconds = millis.coerceAtLeast(0) / 1000; return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60) }
internal fun pace(seconds: Int?) = if (seconds == null || seconds <= 0) "—" else "%d′%02d″".format(seconds / 60, seconds % 60)
internal fun averagePace(meters: Double, millis: Long) = if (meters < 10 || millis <= 0) "—" else pace((millis / meters).toInt())
internal fun localDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
internal fun dateTime(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREAN))
internal fun clock(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

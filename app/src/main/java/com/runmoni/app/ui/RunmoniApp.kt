@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.runmoni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runmoni.app.R
import com.runmoni.app.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RunmoniApp(state: AppState, actions: AppActions, updates: AppUpdateUiState = AppUpdateUiState(), onCheckUpdates: () -> Unit = {}, onExternalAppLink: ((String) -> Unit)? = null) {
    RunmoniTheme {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var settings by rememberSaveable { mutableStateOf(false) }
        var signIn by rememberSaveable { mutableStateOf(false) }
        var appInformation by rememberSaveable { mutableStateOf(false) }
        var selectedRecord by remember { mutableStateOf<RunRecord?>(null) }
        var lastActiveId by remember { mutableStateOf(state.activeRun.id.takeIf { it.isNotBlank() }) }
        val chatOpen = state.openedRoomId.isNotBlank()
        val focusManager = LocalFocusManager.current
        LaunchedEffect(state.openedRoomId) { if (chatOpen) focusManager.clearFocus(force = true) }
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
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (chatOpen || settings) Modifier.clearAndSetSemantics { }.focusProperties { canFocus = false } else Modifier,
            containerColor = RunmoniColors.Cream,
            topBar = {
                Column(Modifier.statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("runmoni", style = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-0.8).sp), modifier = Modifier.weight(1f))
                        AppVersionShortcut(updates) { appInformation = true }
                        IconButton(onClick = { settings = true }, modifier = Modifier.size(48.dp).testTag("settings_button")) {
                            Icon(Icons.Outlined.Settings, "계정 및 러닝 설정", Modifier.size(22.dp))
                        }
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            bottomBar = {
                Surface(color = RunmoniColors.Paper) {
                    Column {
                        HorizontalDivider(color = RunmoniColors.Line)
                        NavigationBar(containerColor = RunmoniColors.Paper, tonalElevation = 0.dp) {
                            tabs.forEachIndexed { index, label ->
                                NavigationBarItem(
                                    selected = tab == index, onClick = { tab = index },
                                    icon = {
                                        if (index == 2) Box(Modifier.size(48.dp).clip(CircleShape).background(if (tab == index) RunmoniColors.Primary else RunmoniColors.Navy), contentAlignment = Alignment.Center) {
                                            Icon(painterResource(icons[index]), null, Modifier.size(26.dp), tint = RunmoniColors.Lime)
                                        } else Icon(painterResource(icons[index]), null, Modifier.size(23.dp))
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = if (index == 2) Color.Transparent else RunmoniColors.PrimarySoft,
                                        selectedIconColor = RunmoniColors.Primary, selectedTextColor = RunmoniColors.Primary,
                                        unselectedIconColor = RunmoniColors.Muted, unselectedTextColor = RunmoniColors.Muted),
                                    modifier = Modifier.testTag(listOf("nav_home", "nav_explore", "nav_run", "nav_crew", "nav_me")[index]),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(state, onRun = { tab = 2 }, onCrew = { tab = 3 }, onExplore = { tab = 1 }, onRecord = { selectedRecord = it })
                    1 -> ExploreScreen(state, actions, onSignIn = { signIn = true }, onRooms = { tab = 3 })
                    2 -> RunningScreen(state, actions, onCrew = { tab = 3 })
                    3 -> CrewScreen(state, actions, onSignIn = { signIn = true })
                    4 -> ProfileScreen(state, actions, onRun = { tab = 2 }, onRecord = { selectedRecord = it }, onSettings = { settings = true }, updates = updates, onAppInformation = { appInformation = true })
                }
            }
        }
        if (settings) SettingsScreen(state, actions, onDismiss = { settings = false }, onSignIn = { settings = false; signIn = true }, onAppInformation = { settings = false; appInformation = true }, updates = updates)
        if (appInformation) AppInformationDialog(updates, onCheckUpdates, onDismiss = { appInformation = false }, promptUpdateInitially = updates.updateAvailable, onExternalLink = onExternalAppLink)
        if (signIn) SignInDialog(state, actions) { signIn = false }
        if (chatOpen) ChatScreen(state, actions)
        selectedRecord?.let { record -> RecordDialog(state.records.firstOrNull { it.id == record.id } ?: record, actions, state.cloud) { selectedRecord = null } }
        state.error?.let { error -> AlertDialog(onDismissRequest = actions::dismissError, title = { Text("확인이 필요해요") }, text = { DialogMessage(error) }, confirmButton = { TextButton(onClick = actions::dismissError) { Text("확인") } }) }
        }
    }
}

@Composable
private fun RunningScreen(state: AppState, actions: AppActions, onCrew: () -> Unit) {
    var finishConfirmation by rememberSaveable { mutableStateOf(false) }
    var optionsExpanded by rememberSaveable { mutableStateOf(false) }
    val run = state.activeRun
    val permissionMissing = state.locationAccess.precisePermissionGranted == false
    val gpsDisabled = state.locationAccess.gpsEnabled == false
    val accuracy = run.accuracyMeters?.takeIf { !permissionMissing && !gpsDisabled && it.isFinite() && it >= 0f }
    val gpsLabel = when { permissionMissing -> "위치 권한 필요"; gpsDisabled -> "GPS 꺼짐"; else -> run.gpsStatus }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            ScreenContent {
                Text(if (run.phase == RunPhase.IDLE) "오늘의 러닝" else when (run.phase) { RunPhase.PAUSED -> if (run.pauseReason == PauseReason.AUTOMATIC) "자동 일시정지 중" else "잠시 쉬는 중"; RunPhase.RECOVERY -> "러닝 이어가기"; else -> "러닝 기록 중" },
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(gpsLabel, if (accuracy != null && accuracy <= 30f) RunmoniColors.Green else RunmoniColors.Muted, "gps_status")
                    accuracy?.let { Text("정확도 ±${it.toInt()}m", style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted) }
                }
                if (permissionMissing || gpsDisabled) LocationReadinessCard(permissionMissing, actions)
                if (run.phase == RunPhase.PAUSED) SmallNote(if (run.pauseReason == PauseReason.AUTOMATIC)
                    "멈춰 있어 러닝 시간을 잠시 멈췄어요. 움직임이 확인되면 자동으로 이어져요. 지금 시작하려면 ‘계속 달리기’를 누르세요."
                    else "직접 일시정지했어요. 다시 달릴 때 ‘계속 달리기’를 누르세요.")
                if (run.phase == RunPhase.RECOVERY) SmallNote("앱이 중단되기 전 저장한 지점까지만 복구해요. 중단된 구간의 거리를 추정해 더하지 않아요.")
                if (run.phase == RunPhase.IDLE) {
                    EditorialHeading("JUST RUN", "신발 끈 묶고,\n가볍게 시작해요.", "시작하면 GPS 경로와 시간을 기록해요.")
                    RunwayArtwork(state.profile.avatar, compact = LocalDensity.current.fontScale >= 1.3f)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PhoneAndroid, null, tint = RunmoniColors.Primary)
                        Text("내 기록은 기기에 안전하게 저장돼요", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                    }
                } else Surface(shape = RoundedCornerShape(28.dp), color = RunmoniColors.Navy) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("달린 거리 · km", color = RunmoniColors.Lime, style = MaterialTheme.typography.labelMedium)
                        DistanceNumber(distance(run.distanceMeters), RunmoniColors.OnDark, Modifier.testTag("run_distance"))
                        HorizontalDivider(color = RunmoniColors.OnDark.copy(alpha = 0.2f))
                        MetricGroup(listOf(UiMetric("러닝 시간", duration(run.activeDurationMillis), tag = "run_duration"), UiMetric("현재 페이스", pace(run.currentPaceSecondsPerKm), "/km", "run_pace")), onDark = true)
                        Text("평균 ${averagePace(run.distanceMeters, run.activeDurationMillis)} /km", color = RunmoniColors.OnDarkMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (run.phase != RunPhase.IDLE) MapPane(run.points, modifier = Modifier.fillMaxWidth().height(188.dp).clip(RoundedCornerShape(20.dp)))
                TextButton(onClick = { optionsExpanded = !optionsExpanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("run_options")) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (optionsExpanded) "러닝 옵션 접기" else "러닝 옵션")
                    Icon(if (optionsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                }
                if (optionsExpanded) {
                    ToggleRow("자동 일시정지", "정확한 GPS로 10초 동안 멈춤이 확인되면 시간을 멈추고, 움직이면 이어가요", state.autoPause, actions::setAutoPause)
                    ToggleRow("음성 기록 안내", "거리별 기록을 음성으로 들어요", state.voiceGuidance, actions::setVoiceGuidance)
                }
                val joined = state.parties.firstOrNull { it.joined && !it.cancelled }
                if (joined != null || state.audio.roomId.isNotBlank()) AudioPanel(state.audio, actions, state.audio.roomId.ifBlank { "party:${joined!!.id}" })
                else if (optionsExpanded) TextButton(onClick = onCrew, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Outlined.Headphones, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("크루의 음성방 둘러보기") }
            }
        }
        Surface(color = RunmoniColors.Paper, modifier = Modifier.fillMaxWidth().testTag("run_control_bar")) {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                if (run.phase == RunPhase.IDLE) {
                    Button(onClick = actions::startRun, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("start_run"), shape = RoundedCornerShape(50)) {
                        Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("러닝 시작", style = MaterialTheme.typography.titleMedium)
                    }
                } else AdaptiveActions(
                    first = { modifier -> Button(onClick = { when (run.phase) { RunPhase.RUNNING -> actions.pauseRun(); RunPhase.PAUSED -> actions.resumeRun(); else -> actions.recoverRun() } },
                        modifier = modifier.heightIn(min = 56.dp).testTag(when (run.phase) { RunPhase.RUNNING -> "pause_run"; RunPhase.PAUSED -> "resume_run"; else -> "recover_run" })) {
                        Text(when (run.phase) { RunPhase.RUNNING -> "일시정지"; RunPhase.PAUSED -> "계속 달리기"; else -> "이어 달리기" })
                    } },
                    second = { modifier -> OutlinedButton(onClick = { finishConfirmation = true }, modifier = modifier.heightIn(min = 56.dp).testTag("finish_run")) { Text("러닝 마치기") } },
                )
                }
            }
        }
    if (finishConfirmation) AlertDialog(onDismissRequest = { finishConfirmation = false }, title = { Text("오늘의 러닝을 마칠까요?") }, text = { DialogMessage("${distance(run.distanceMeters)} km · ${duration(run.activeDurationMillis)}\n저장한 기록은 ‘나’에서 다시 볼 수 있어요.") }, confirmButton = { TextButton(onClick = { finishConfirmation = false; actions.finishRun() }, modifier = Modifier.testTag("confirm_finish_run")) { Text("저장하고 마치기") } }, dismissButton = { TextButton(onClick = { finishConfirmation = false }) { Text("계속 뛰기") } })
}

@Composable
private fun LocationReadinessCard(permissionMissing: Boolean, actions: AppActions) {
    Surface(color = RunmoniColors.Mint, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (permissionMissing) "정확한 위치 권한이 필요해요" else "GPS가 꺼져 있어요", style = MaterialTheme.typography.titleMedium)
            Text(if (permissionMissing) "러닝 시작 시 위치 권한을 허용해 주세요. 권한 요청이 나오지 않으면 앱 설정에서 ‘정확한 위치’를 켜 주세요."
                else "GPS를 켜면 새 위치부터 경로를 기록해요. 신호가 없는 구간의 거리는 추정해 더하지 않아요.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = if (permissionMissing) actions::openAppPermissionSettings else actions::openLocationSettings,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(if (permissionMissing) "app_permission_settings" else "location_settings")) {
                Text(if (permissionMissing) "앱 권한 설정" else "위치 설정 열기")
            }
        }
    }
}

@Composable
private fun CrewScreen(state: AppState, actions: AppActions, onSignIn: () -> Unit) {
    CommunityRoomsScreen(state, actions)
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
            AdaptiveActions(
                first = { modifier -> Button(onClick = { if (thisRoom) actions.disconnectVoice() else actions.connectVoice(roomId) }, enabled = !audio.connecting, modifier = modifier.heightIn(min = 56.dp).testTag("join_voice")) { Text(if (thisRoom) "음성방 나가기" else if (audio.connecting) "연결 중" else "음성방 참여") } },
                second = { modifier -> OutlinedButton(onClick = { actions.setMicrophone(!audio.microphoneEnabled) }, enabled = thisRoom, modifier = modifier.heightIn(min = 56.dp).testTag("toggle_microphone")) { Text(if (audio.microphoneEnabled) "마이크 켜짐" else "마이크 꺼짐") } },
            )
            if (thisRoom) {
                Text("음성 음량 · ${(audio.voiceVolume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = audio.voiceVolume, onValueChange = actions::setVoiceVolume, modifier = Modifier.testTag("voice_volume").semantics { contentDescription = "음성 음량"; stateDescription = "${(audio.voiceVolume * 100).toInt()}퍼센트" })
                Text("음악 음량 · ${(audio.musicVolume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = audio.musicVolume, onValueChange = actions::setMusicVolume, modifier = Modifier.testTag("music_volume").semantics { contentDescription = "음악 음량"; stateDescription = "${(audio.musicVolume * 100).toInt()}퍼센트" })
            }
            Text(audio.sourceLabel, style = MaterialTheme.typography.bodyMedium)
            if (canBroadcast) {
                AdaptiveActions(
                    first = { modifier -> OutlinedButton(onClick = { actions.shareExternalMusic("com.google.android.apps.youtube.music") }, modifier = modifier.heightIn(min = 56.dp).testTag("share_youtube_music")) { Text("YouTube Music") } },
                    second = { modifier -> OutlinedButton(onClick = { actions.shareExternalMusic("com.sec.android.app.music") }, modifier = modifier.heightIn(min = 56.dp).testTag("share_samsung_music")) { Text("Samsung Music") } },
                )
                AdaptiveActions(
                    first = { modifier -> OutlinedButton(onClick = actions::selectMusicFile, modifier = modifier.heightIn(min = 48.dp).testTag("select_music")) { Text("음악 파일 선택") } },
                    second = { modifier -> OutlinedButton(onClick = { sourcePicker = true }, modifier = modifier.heightIn(min = 48.dp).testTag("other_music_apps")) { Text("다른 음악 앱") } },
                )
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
internal fun ScreenContent(spacing: androidx.compose.ui.unit.Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen_scroll").padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

@Composable
internal fun ScreenHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted) }
}

@Composable
internal fun SectionHeading(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() }); if (action != null) TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) } }
}

@Composable
internal fun StatusPill(label: String, color: Color, tag: String = "") {
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(50), modifier = Modifier.testTag(tag)) { Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(5.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(5.dp)); Text(label, color = color, style = MaterialTheme.typography.labelMedium) } }
}

@Composable
internal fun EmptyCard(title: String, message: String, action: String? = null, onClick: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(RunmoniColors.Mint), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.DirectionsRun, null, tint = RunmoniColors.Primary) }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        if (action != null) TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(action); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp)) }
    }
}

@Composable
internal fun SmallNote(message: String) { Text(message, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted) }

@Composable
internal fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit, enabled: Boolean = true, tag: String = "") {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked).testTag(tag).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted) }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun LocationSharingToggle(state: AppState, actions: AppActions) {
    var confirmation by remember { mutableStateOf(false) }
    LaunchedEffect(state.signedIn) { if (!state.signedIn) confirmation = false }
    if (state.communityGuest) { SmallNote("방에 참여해도 내 실시간 위치는 자동으로 공유되지 않아요."); return }
    ToggleRow("파일럿 위치 공유", if (state.signedIn) "초대받은 테스터에게 러닝 중 정밀 위치를 공유해요" else "초대 파일럿 로그인 후 이용할 수 있어요", state.publicLocation, { if (it) confirmation = true else actions.setPublicLocation(false) }, enabled = state.signedIn, tag = "share_location")
    if (confirmation && state.signedIn) AlertDialog(onDismissRequest = { confirmation = false }, title = { Text("러닝 위치를 공유할까요?") }, text = { DialogMessage("진행 중 러닝의 정밀 좌표, 러너 이름, 미니미를 같은 초대 파일럿의 성인 테스터에게 보여줘요. 인터넷 전체에 공개되는 지도는 아니에요.\n\n러닝 중 15초마다 위치를 갱신해요. 공유를 끄거나 러닝을 마치면 공유를 중지해요. 마지막 갱신 후 45초가 지나면 위치가 만료돼요.") }, confirmButton = { TextButton(onClick = { confirmation = false; actions.setPublicLocation(true) }, modifier = Modifier.testTag("confirm_share_location")) { Text("위치 공유 켜기") } }, dismissButton = { TextButton(onClick = { confirmation = false }, modifier = Modifier.testTag("cancel_share_location")) { Text("공유하지 않기") } })
}

@Composable
internal fun PartySummary(party: Party, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = RunmoniColors.Paper,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "파티 참가 정보 보기", onClick = onClick)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(if (party.kind == PartyKind.LOCAL) "함께 모여 달려요" else "각자의 곳에서 달려요", RunmoniColors.Primary)
                Text(if (party.cancelled) "취소됨" else if (party.joined) "참가 중" else "${party.participantCount}/${party.capacity}명", style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted)
            }
            Text(party.title, style = MaterialTheme.typography.titleLarge)
            Text(dateTime(party.startsAtMillis), style = MaterialTheme.typography.bodyMedium)
            Text("${party.distanceKm} km · ${party.paceLabel}${if (party.meetingLabel.isNotBlank()) " · ${party.meetingLabel}" else ""}", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            HorizontalDivider(color = RunmoniColors.Line)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("참가 정보 보기", color = RunmoniColors.Primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f)); Icon(Icons.Outlined.ArrowForward, null, tint = RunmoniColors.Primary, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
internal fun RecordRow(record: RunRecord, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "러닝 기록 자세히 보기", onClick = onClick).testTag("record_${record.id}").padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(dateTime(record.startedAtMillis), style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${distance(record.distanceMeters)} km", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = RunmoniColors.Muted)
            }
            Text("${duration(record.activeDurationMillis)} · ${averagePace(record.distanceMeters, record.activeDurationMillis)} /km", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            HorizontalDivider(color = RunmoniColors.Line)
    }
}

internal fun topColor(variant: Int): Color = when (variant % 4) { 1 -> RunmoniColors.Green; 2 -> Color(0xFF547FB3); 3 -> Color(0xFFD6AE48); else -> RunmoniColors.Coral }
@Composable
internal fun AvatarMini(avatar: AvatarStyle, modifier: Modifier, running: Boolean = false, decorative: Boolean = false) = MiniMi(avatar, modifier, running, if (decorative) null else "나의 러닝 미니미")
internal fun accessoryName(variant: Int) = when (variant) { 1 -> "headband"; 2 -> "cap"; 3 -> "headphones"; else -> "none" }
internal fun distance(meters: Double) = String.format(Locale.KOREAN, "%.2f", meters / 1000)
internal fun duration(millis: Long): String { val seconds = millis.coerceAtLeast(0) / 1000; return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60) }
internal fun pace(seconds: Int?) = if (seconds == null || seconds <= 0) "—" else "%d′%02d″".format(seconds / 60, seconds % 60)
internal fun averagePace(meters: Double, millis: Long) = if (meters < 10 || millis <= 0) "—" else pace((millis / meters).toInt())
internal fun localDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
internal fun dateTime(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREAN))
internal fun clock(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

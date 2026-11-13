@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.runmoni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import com.runmoni.app.avatar.AvatarArtwork
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler

@Composable
internal fun SettingsScreen(state: AppState, actions: AppActions, onDismiss: () -> Unit, onSignIn: () -> Unit, onAppInformation: () -> Unit = {}, updates: AppUpdateUiState = AppUpdateUiState(), onOpenProfile: () -> Unit = {}, scrollState: androidx.compose.foundation.ScrollState? = null) {
    var url by remember(state.apiUrl) { mutableStateOf(state.apiUrl) }
    var cloudHistory by remember(state.cloud.uid) { mutableStateOf(false) }
    var connectionDetails by remember { mutableStateOf(false) }
    var deviceChecks by remember { mutableStateOf(false) }
    var disconnectRoom by remember { mutableStateOf(false) }
    var linkCommunityGoogle by remember { mutableStateOf(false) }
    var restoreCommunityGoogle by remember { mutableStateOf(false) }
    if (deviceChecks) {
        DeviceChecksScreen(state, actions) { deviceChecks = false }
        return
    }
    BackHandler(onBack = onDismiss)
    Surface(Modifier.fillMaxSize().testTag("settings_screen"), color = RunmoniColors.Cream) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
            PageTopBar("설정", onDismiss)
            HorizontalDivider(color = RunmoniColors.Line)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val gutter = if (LocalConfiguration.current.screenWidthDp >= 600) 32.dp else 20.dp
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.TopCenter).verticalScroll(scrollState ?: rememberScrollState())
                    .padding(horizontal = gutter, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (updates.updateAvailable) {
                        AppVersionCard(updates, onClick = onAppInformation, tag = "settings_app_update")
                        HorizontalDivider(color = RunmoniColors.Line)
                    }
                    Surface(color = RunmoniColors.MintWash, shape = RunmoniShapes.Surface) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            DestinationRow(state.profile.name, "프로필과 닉네임 관리", Icons.Outlined.AccountCircle,
                                onOpenProfile, Modifier.testTag("settings_profile"))
                        }
                    }
                    SettingsGroup("러닝과 위치", Icons.Outlined.DirectionsRun) {
                        ToggleRow("자동 일시정지", "정지한 동안 러닝 시간을 잠시 멈춰요", state.autoPause, actions::setAutoPause)
                        ToggleRow("음성 기록 안내", "거리별 기록을 음성으로 들어요", state.voiceGuidance, actions::setVoiceGuidance)
                        DestinationRow("러닝 알림", when {
                            !state.runNotificationAccess.checked -> "알림 설정"
                            state.runNotificationAccess.enabled -> "켜짐"
                            else -> "꺼짐"
                        }, Icons.Outlined.Notifications, actions::openRunNotificationSettings, Modifier.testTag("settings_run_notifications"))
                        LocationSharingToggle(state, actions)
                    }
                    SettingsGroup("기록 요약 백업", Icons.Outlined.CloudDone,
                        "Google 계정에 보관한 러닝 요약") {
                        Text(if (state.cloud.configured) state.cloud.status else "이 앱에서는 Google 백업을 사용할 수 없어요", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted, modifier = Modifier.testTag("cloud_status"))
                        if (state.cloud.uid.isBlank()) {
                            OutlinedButton(onClick = actions::signInGoogle, enabled = state.cloud.configured && !state.cloud.busy && !state.communityGoogleLink.busy && !state.busy, shape = RunmoniShapes.Action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("google_sign_in")) { Text(if (state.cloud.busy) "연결 중…" else "Google로 로그인") }
                        } else {
                            Text(state.cloud.accountLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("cloud_account"))
                            OutlinedButton(shape = RunmoniShapes.Action, onClick = { actions.refreshCloudSummaries(state.cloud.uid); cloudHistory = true }, enabled = !state.cloud.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cloud_history")) { Text("백업한 요약 보기") }
                            TextButton(shape = RunmoniShapes.Action, onClick = actions::signOutGoogle, enabled = !state.cloud.busy && !state.communityGoogleLink.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("google_sign_out")) { Text("Google 로그아웃") }
                        }
                        SmallNote("기록 상세에서 요약을 백업할 수 있어요. 러닝 경로·포인트·옷장은 포함되지 않아요.")
                    }
                    SettingsGroup("온라인 계정", Icons.Outlined.Groups,
                        "크루 소속과 온라인 프로필") {
                        StatusPill(if (state.serverConnected && state.signedIn) "프로필 연결됨" else if (state.serverConnected) "둘러보기 가능" else "기기 기록 모드", if (state.serverConnected && state.signedIn) RunmoniColors.Green else RunmoniColors.Muted, "backend_status")
                        if (state.communityBackend == CommunityBackend.FIREBASE) {
                            val link = state.communityGoogleLink
                            if (link.linked || link.busy || link.status != CommunityGoogleLinkState().status)
                                Text(link.status, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                                    modifier = Modifier.testTag("community_google_status"))
                            if (link.linked && link.accountLabel.isNotBlank()) Text(link.accountLabel, style = MaterialTheme.typography.bodyMedium)
                            if (link.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (state.signedIn && !link.linked) OutlinedButton(shape = RunmoniShapes.Action, onClick = { linkCommunityGoogle = true }, enabled = !link.busy && !state.cloud.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("link_community_google")) { Text("Google로 계정 보관") }
                            if (!state.signedIn) OutlinedButton(shape = RunmoniShapes.Action, onClick = { restoreCommunityGoogle = true }, enabled = state.cloud.configured && !link.busy && !state.cloud.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("restore_community_google")) { Text("기존 온라인 계정 복구") }
                            SmallNote("Google을 연결한 크루 소속과 권한을 다시 찾을 수 있어요. 기기의 러닝 경로·포인트·옷장은 이 연결로 복원되지 않아요.")
                        }
                        if (state.signedIn) OutlinedButton(shape = RunmoniShapes.Action, onClick = { if (state.communityBackend == CommunityBackend.FIREBASE) disconnectRoom = true else actions.signOut() }, enabled = !state.communityGoogleLink.busy && !state.cloud.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sign_out")) { Text(if (state.communityGuest) "온라인 연결 해제" else "파일럿 로그아웃") }
                        else OutlinedButton(shape = RunmoniShapes.Action, onClick = onSignIn, enabled = !state.communityGoogleLink.busy && !state.cloud.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_sign_in")) { Text("닉네임으로 시작하기") }
                    }
                    SettingsGroup("앱 지원", Icons.Outlined.Info) {
                        DestinationRow("앱 정보와 업데이트", "버전 내역, 다운로드, 라이선스", Icons.Outlined.Info,
                            onAppInformation, Modifier.testTag("settings_app_information"))
                        HorizontalDivider(color = RunmoniColors.Line)
                        DestinationRow("연결·휴대폰 점검", "연결 상태와 직접 확인한 항목", Icons.Outlined.FactCheck,
                            { deviceChecks = true }, Modifier.testTag("open_device_checks"),
                            iconContainer = RunmoniColors.BlueWash, iconTint = RunmoniColors.RouteBlue)
                        TextButton(shape = RunmoniShapes.Action, onClick = { connectionDetails = !connectionDetails }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("toggle_server_details").semantics { stateDescription = if (connectionDetails) "펼침" else "접힘" }) { Text(if (connectionDetails) "개발용 연결 설정 닫기" else "개발용 연결 설정") }
                        if (connectionDetails) {
                            HorizontalDivider(color = RunmoniColors.Line)
                            Text(if (state.communityBackend == CommunityBackend.FIREBASE) "연결 방식: Firebase" else "연결 방식: HTTP 테스트 서버")
                            if (state.communityBackend != CommunityBackend.FIREBASE) OutlinedButton(shape = RunmoniShapes.Action, onClick = actions::useFirebaseCommunity, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("use_firebase_rooms")) { Text("Firebase 크루·파티 사용") }
                            SmallNote(state.serverStatus)
                            OutlinedTextField(shape = RunmoniShapes.Input, value = url, onValueChange = { url = it }, label = { Text("서버 연결 주소") }, singleLine = true, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("server_url"))
                            Button(shape = RunmoniShapes.Action, onClick = { actions.configureServer(url.trim()) }, enabled = url.startsWith("http://") || url.startsWith("https://"), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("connect_server")) { Text("서버 연결") }
                            SmallNote("USB 연결 테스트는 localhost를 사용해요. 서버 주소는 Google 계정 설정과 별개예요.")
                        }
                    }
                }
            }
        }
    }
    if (cloudHistory && state.cloud.uid.isNotBlank()) CloudSummariesDialog(state.cloud, actions) { cloudHistory = false }
    if (linkCommunityGoogle) DismissibleAlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { linkCommunityGoogle = false },
        title = { Text("현재 크루 프로필에 Google을 연결할까요?") },
        text = { DialogMessage("현재 크루 소속과 크루장·주최자 권한을 그대로 유지해요. 이미 다른 프로필에 사용한 Google 계정은 자동으로 합치지 않아요.\n\n이 연결은 기기의 GPS 기록·포인트·옷장 백업과는 별도예요.") },
        confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { linkCommunityGoogle = false; actions.linkCommunityGoogle() }, modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_link_community_google")) { Text("Google 계정 선택") } },
        dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { linkCommunityGoogle = false }) { Text("취소") } })
    if (restoreCommunityGoogle) DismissibleAlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { restoreCommunityGoogle = false },
        title = { Text("기존 크루 프로필을 다시 연결할까요?") },
        text = { DialogMessage("이전에 크루 프로필에 연결한 같은 Google 계정을 선택해 주세요. 기존 크루 소속과 권한을 확인하며 새 프로필을 만들거나 다른 프로필과 합치지 않아요.\n\n기기의 GPS 기록·포인트·옷장은 이 연결로 복원되지 않아요.") },
        confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { restoreCommunityGoogle = false; actions.restoreCommunityGoogle() }, modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_restore_community_google")) { Text("Google 계정 선택") } },
        dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { restoreCommunityGoogle = false }) { Text("취소") } })
    if (disconnectRoom) DismissibleAlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { disconnectRoom = false }, title = { Text("크루 프로필 연결을 해제할까요?") },
        text = { DialogMessage(if (state.communityGoogleLink.linked)
            "연결을 해제하면 이 기기에서 크루·파티를 이용하려면 같은 Google 계정으로 크루 프로필을 다시 연결해야 해요. 기기 러닝 기록과 포인트, 기록 요약 백업 로그인은 유지돼요."
            else "Google을 연결하지 않은 익명 프로필의 크루·파티 참여 내역과 크루장·주최자 권한은 다시 복구할 수 없어요. 기기 러닝 기록과 기록 요약 백업 로그인은 유지돼요.") },
        confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { disconnectRoom = false; actions.signOut() }) { Text("연결 해제") } },
        dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { disconnectRoom = false }) { Text("유지하기") } })
}

/** Settings groups describe a real task or account boundary, rather than a card per control. */
@Composable
internal fun SettingsGroup(title: String, icon: ImageVector, detail: String? = null,
    content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = RunmoniColors.RouteBlue)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            }
        }
        Column(Modifier.fillMaxWidth().background(RunmoniColors.Paper, RunmoniShapes.Surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
internal fun SignInDialog(state: AppState, actions: AppActions, onDismiss: () -> Unit) {
    UnifiedProfileDialog(state, actions, onDismiss)
}

@Composable
internal fun NewCrewDialog(actions: AppActions, state: AppState = AppState(), onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var pace by remember { mutableStateOf("자유 페이스") }
    var submitted by remember { mutableStateOf(false) }
    val previousId = remember { state.lastCreatedCrewId }
    LaunchedEffect(state.lastCreatedCrewId) { if (submitted && state.lastCreatedCrewId != previousId) onDismiss() }
    FormDialog("크루 만들기", onDismiss, confirm = { Button(shape = RunmoniShapes.Action, onClick = { submitted = true; actions.createCrew(NewCrew(name.trim(), description.trim(), region.trim(), pace.trim())) }, enabled = name.trim().length in 2..60 && !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_create_crew")) { Text(if (state.busy) "만드는 중…" else "만들기") } }) {
        FormField("크루 이름", name, { if (it.length <= 60) name = it }, "crew_name")
        FormField("크루 소개", description, { description = it.take(500) }, "crew_description", singleLine = false)
        FormField("활동 지역", region, { region = it.take(80) }, "crew_region")
        FormField("기본 페이스", pace, { pace = it.take(80) }, "crew_pace")
        SmallNote("이름·소개·지역·페이스는 크루 목록에 공개돼요.")
        FormRequestStatus(submitted, state.busy, state.error, "create_crew")
    }
}

@Composable
internal fun NewPartyDialog(crewId: String?, actions: AppActions, state: AppState = AppState(), onDismiss: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT) }
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(PartyKind.LOCAL) }
    var starts by remember { mutableStateOf(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0).format(formatter)) }
    var meeting by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("5") }
    var pace by remember { mutableStateOf("자유 페이스") }
    var capacity by remember { mutableStateOf("20") }
    val time = runCatching { LocalDateTime.parse(starts, formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    val km = distance.toDoubleOrNull()
    val max = capacity.toIntOrNull()
    val valid = crewId != null && state.crews.any { it.id == crewId && it.joined } && title.trim().length in 2..80 && time != null && time > System.currentTimeMillis() && km != null && km.isFinite() && km > 0 && km <= 100 && max != null && max in 2..20 && pace.trim().length in 1..80 && time <= System.currentTimeMillis() + 366L * 86400000 && (kind == PartyKind.REMOTE || meeting.trim().length >= 2)
    var submitted by remember { mutableStateOf(false) }
    val previousId = remember { state.lastCreatedPartyId }
    LaunchedEffect(state.lastCreatedPartyId) { if (submitted && state.lastCreatedPartyId != previousId) onDismiss() }
    FormDialog("파티 만들기", onDismiss, confirm = { Button(shape = RunmoniShapes.Action, onClick = { submitted = true; actions.createParty(NewParty(crewId, title.trim(), kind, time!!, if (kind == PartyKind.LOCAL) meeting.trim() else "각자의 러닝 코스", km!!, pace.trim(), max!!)) }, enabled = valid && !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_create_party")) { Text(if (state.busy) "만드는 중…" else "만들기") } }) {
        Surface(color = RunmoniColors.MintWash, shape = RunmoniShapes.Surface) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Groups, null, Modifier.size(24.dp), tint = RunmoniColors.Primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("소속 크루", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                    Text(state.crews.firstOrNull { it.id == crewId }?.name ?: "먼저 크루에 가입해 주세요", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        FormField("파티 이름", title, { if (it.length <= 80) title = it }, "party_title")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(shape = RunmoniShapes.Action, selected = kind == PartyKind.LOCAL, onClick = { kind = PartyKind.LOCAL }, label = { Text("현장 모임") }, modifier = Modifier.heightIn(min = 48.dp).testTag("party_local")); FilterChip(shape = RunmoniShapes.Action, selected = kind == PartyKind.REMOTE, onClick = { kind = PartyKind.REMOTE }, label = { Text("원격 함께 뛰기") }, modifier = Modifier.heightIn(min = 48.dp).testTag("party_remote")) }
        AdaptiveActions(
            first = { modifier -> OutlinedButton(shape = RunmoniShapes.Action, onClick = {
                val chosen = runCatching { LocalDateTime.parse(starts, formatter) }.getOrElse { LocalDateTime.now().plusHours(1) }
                DatePickerDialog(context, { _, year, month, day -> starts = chosen.withYear(year).withMonth(month + 1).withDayOfMonth(day).format(formatter) }, chosen.year, chosen.monthValue - 1, chosen.dayOfMonth).show()
            }, modifier = modifier.heightIn(min = 48.dp).testTag("pick_party_date")) { Text("날짜 선택") } },
            second = { modifier -> OutlinedButton(shape = RunmoniShapes.Action, onClick = {
                val chosen = runCatching { LocalDateTime.parse(starts, formatter) }.getOrElse { LocalDateTime.now().plusHours(1) }
                TimePickerDialog(context, { _, hour, minute -> starts = chosen.withHour(hour).withMinute(minute).format(formatter) }, chosen.hour, chosen.minute, true).show()
            }, modifier = modifier.heightIn(min = 48.dp).testTag("pick_party_time")) { Text("시간 선택") } },
        )
        FormField("시작 날짜·시간", starts, { starts = it }, "party_time", hint = "휴대폰 시간 · 예: ${LocalDateTime.now().plusHours(1).format(formatter)}", error = when { time == null -> "날짜와 시간을 yyyy-MM-dd HH:mm 형식으로 입력해 주세요"; time <= System.currentTimeMillis() -> "현재보다 나중 시간을 선택해 주세요"; else -> null })
        if (kind == PartyKind.LOCAL) FormField("집결 장소", meeting, { meeting = it.take(200) }, "party_meeting") else SmallNote("각자의 장소에서 같은 시간에 달려요.")
        FormField("목표 거리 · km", distance, { distance = it }, "party_distance", keyboardType = KeyboardType.Decimal, error = if (km == null || !km.isFinite() || km <= 0 || km > 100) "거리는 0 초과 100km 이하로 입력해 주세요" else null)
        FormField("페이스 범위", pace, { pace = it.take(80) }, "party_pace")
        FormField("정원 · 2~20명", capacity, { capacity = it }, "party_capacity", keyboardType = KeyboardType.Number, error = if (max == null || max !in 2..20) "정원은 2~20명으로 입력해 주세요" else null)
        if (!valid) SmallNote("미래의 시작 시간, 거리, 정원을 확인해 주세요. 현장 모임에는 집결 장소가 필요해요.")
        FormRequestStatus(submitted, state.busy, state.error, "create_party")
    }
}

@Composable
internal fun RecordDialog(record: RunRecord, actions: AppActions, cloud: CloudAccountState = CloudAccountState(), persistence: RunPersistenceState = RunPersistenceState(initialized = true), onDismiss: () -> Unit) {
    var deleteConfirmation by remember { mutableStateOf(false) }
    var backupConfirmation by remember(cloud.uid, record.id) { mutableStateOf(false) }
    if (!backupConfirmation && !deleteConfirmation) FormDialog("나의 러닝 기록", onDismiss, dismissLabel = null, closeTag = "close_record") {
        Text(dateTime(record.startedAtMillis), style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        Text("${distance(record.distanceMeters)} km", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("record_distance"))
        Text("러닝 시간 ${duration(record.activeDurationMillis)} · 평균 ${averagePace(record.distanceMeters, record.activeDurationMillis)} /km", modifier = Modifier.testTag("record_duration_pace"))
        MapPane(record.points, modifier = Modifier.fillMaxWidth().height(200.dp).clip(RunmoniShapes.Surface))
        StatusPill(if (record.synced) "서버 동기화됨" else "기기에 저장됨", if (record.synced) RunmoniColors.Green else RunmoniColors.Muted)
        if (record.id in persistence.pendingCreditRunIds)
            RunPersistenceNotice(RunPersistenceState(issue = RunPersistenceIssue.CREDIT,
                initialized = true, saving = persistence.saving, creditPending = true,
                pendingCreditRunIds = setOf(record.id)), actions::retryRunPersistence)
        if (record.interrupted) SmallNote("중단된 구간이 있어요. 확인되지 않은 거리는 더하지 않았어요.")
        if (record.splits.isNotEmpty()) {
            Text("킬로미터별 기록", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            record.splits.forEach { split ->
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${split.kilometer} km", style = MaterialTheme.typography.bodyMedium)
                    Text(duration(split.durationMillis), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        OutlinedButton(shape = RunmoniShapes.Action, onClick = { actions.exportRun(record.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("export_run")) { Text("GPX 경로 내보내기") }
        if (cloud.uid.isNotBlank()) {
            OutlinedButton(shape = RunmoniShapes.Action, onClick = { backupConfirmation = true }, enabled = record.finishedAtMillis > 0 && !cloud.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("backup_cloud_run")) { Text("이 기록 요약을 계정에 백업") }
            SmallNote("백업 계정: ${cloud.accountLabel}\n${cloud.status}")
        }
        TextButton(shape = RunmoniShapes.Action, onClick = { deleteConfirmation = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("delete_run")) { Text("기록 삭제", color = MaterialTheme.colorScheme.error) }
    }
    if (backupConfirmation && cloud.uid.isNotBlank()) DismissibleAlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { backupConfirmation = false }, title = { Text("이 계정에 기록 요약을 백업할까요?") }, text = { DialogMessage("계정: ${cloud.accountLabel}\n\n이 기기에서 선택한 본인의 기록인지 확인해 주세요. 날짜·거리·시간·GPS 포인트 수 요약만 저장해요. 경로 원본·마일리지는 포함하지 않으며, 보상을 검증한 기록으로 인정하지 않아요.") }, confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { backupConfirmation = false; actions.backupCloudRun(record.id, cloud.uid) }, enabled = !cloud.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_cloud_backup")) { Text("이 계정에 백업") } }, dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { backupConfirmation = false }) { Text("취소") } })
    if (deleteConfirmation) DismissibleAlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { deleteConfirmation = false }, title = { Text("이 기록을 삭제할까요?") }, text = { DialogMessage("삭제한 기기 기록은 복구할 수 없어요. 클라우드에 백업한 요약은 계정 설정에서 별도로 삭제해 주세요.") }, confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { actions.deleteRun(record.id); onDismiss() }, modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_delete_run")) { Text("삭제") } }, dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { deleteConfirmation = false }) { Text("취소") } })
}

@Composable
internal fun CloudSummariesDialog(cloud: CloudAccountState, actions: AppActions, onDismiss: () -> Unit) {
    var pendingDelete by remember(cloud.uid) { mutableStateOf<CloudRunSummary?>(null) }
    val summariesScroll = key(cloud.uid) { rememberScrollState() }
    if (pendingDelete == null) FormDialog("백업한 기록 요약", onDismiss, dismissLabel = null, closeTag = "close_cloud_summaries", scrollState = summariesScroll) {
        Text(cloud.accountLabel, style = MaterialTheme.typography.titleMedium)
        Text(cloud.status, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        SmallNote("최근 50건의 자가 기록 요약이에요. 경로 원본은 이 기기에만 있어요. 요약으로 경로를 복구하거나 마일리지를 적립하지 않아요.")
        OutlinedButton(shape = RunmoniShapes.Action, onClick = { actions.refreshCloudSummaries(cloud.uid) }, enabled = !cloud.busy && cloud.uid.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp).testTag("refresh_cloud_summaries")) { Text("새로고침") }
        if (cloud.summaries.isEmpty() && !cloud.busy) Text("표시할 백업 요약이 없어요.")
        cloud.summaries.forEach { summary ->
            BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                val details: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(dateTime(summary.startedAtMillis), style = MaterialTheme.typography.bodyMedium)
                        Text("${distance(summary.distanceMeters)} km · ${duration(summary.activeDurationMillis)}", style = MaterialTheme.typography.titleSmall)
                    }
                }
                val delete: @Composable (Modifier) -> Unit = { modifier ->
                    TextButton(shape = RunmoniShapes.Action, onClick = { pendingDelete = summary }, enabled = !cloud.busy,
                        modifier = modifier.heightIn(min = 48.dp).testTag("delete_cloud_${summary.runId}")) { Text("요약 삭제") }
                }
                if (LocalDensity.current.fontScale >= 1.3f || maxWidth < 320.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { details(Modifier.fillMaxWidth()); delete(Modifier) }
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    details(Modifier.weight(1f)); delete(Modifier)
                }
            }
            HorizontalDivider(color = RunmoniColors.Line)
        }
    }
    pendingDelete?.let { summary -> DismissibleAlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { pendingDelete = null }, title = { Text("이 백업 요약을 삭제할까요?") }, text = { DialogMessage("계정: ${cloud.accountLabel}\n선택한 클라우드 요약만 삭제해요. 기기 기록은 그대로 보관해요.") }, confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { pendingDelete = null; actions.deleteCloudSummary(summary.runId, cloud.uid) }, enabled = !cloud.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_delete_cloud")) { Text("요약 삭제") } }, dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { pendingDelete = null }) { Text("취소") } }) }
}

@Composable
internal fun CrewMembersDialog(state: AppState, crewId: String, actions: AppActions, onDismiss: () -> Unit) {
    LaunchedEffect(crewId) { actions.loadCrewMembers(crewId) }
    val crew = state.crews.firstOrNull { it.id == crewId }
    val members = state.crewMembers[crewId].orEmpty()
    FormDialog("멤버 · DJ 관리", onDismiss, dismissLabel = null, closeTag = "close_crew_members") {
        if (members.isEmpty()) SmallNote("표시할 멤버가 없어요. 크루 정보를 새로고침해 주세요.")
        members.forEach { member ->
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val details: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(member.name, style = MaterialTheme.typography.titleMedium)
                        Text(if (member.isOwner) "크루장" else if (member.isDj) "DJ" else "멤버", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                    }
                }
                val manage: @Composable (Modifier) -> Unit = { modifier ->
                    if (!member.isOwner && crew?.isOwner == true) OutlinedButton(shape = RunmoniShapes.Action,
                        onClick = { actions.setCrewDj(crewId, member.id, !member.isDj) },
                        modifier = modifier.heightIn(min = 48.dp).testTag("assign_dj_${member.id}")) { Text(if (member.isDj) "DJ 해제" else "DJ 임명") }
                }
                if (LocalDensity.current.fontScale >= 1.3f || maxWidth < 320.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { details(Modifier.fillMaxWidth()); manage(Modifier) }
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    details(Modifier.weight(1f)); manage(Modifier)
                }
            }
            HorizontalDivider(color = RunmoniColors.Line)
        }
        SmallNote(if (state.communityBackend == CommunityBackend.FIREBASE) "DJ는 공지를 작성할 수 있어요. 크루 관리 권한은 크루장에게 있어요." else "DJ는 음악 공유를 제어할 수 있어요. 크루 관리 권한은 크루장에게 있어요.")
    }
}

@Composable
private fun FormRequestStatus(submitted: Boolean, busy: Boolean, error: String?, tag: String) {
    if (submitted && busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("${tag}_progress"))
    if (submitted && !busy && !error.isNullOrBlank()) Surface(color = RunmoniColors.CoralWash, shape = RunmoniShapes.Action) {
        Text(error, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.CoralInk,
            modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("${tag}_error")
                .semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun FormField(label: String, value: String, onChange: (String) -> Unit, tag: String, singleLine: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text, hint: String? = null, error: String? = null) {
    val support: @Composable (() -> Unit)? = if (hint == null && error == null) null else { { Text(error ?: hint.orEmpty(), modifier = Modifier.testTag("${tag}_support")) } }
    OutlinedTextField(shape = RunmoniShapes.Input, value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag), singleLine = singleLine, minLines = if (singleLine) 1 else 3, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), isError = error != null, supportingText = support,
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = RunmoniColors.Paper, focusedContainerColor = RunmoniColors.Paper))
}

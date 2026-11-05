@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.runmoni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import com.runmoni.app.domain.*
import com.runmoni.app.avatar.AvatarArtwork
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler

@Composable
internal fun SettingsScreen(state: AppState, actions: AppActions, onDismiss: () -> Unit, onSignIn: () -> Unit, onAppInformation: () -> Unit = {}, updates: AppUpdateUiState = AppUpdateUiState()) {
    var url by remember(state.apiUrl) { mutableStateOf(state.apiUrl) }
    var name by remember(state.profile.name) { mutableStateOf(state.profile.name) }
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
    Surface(Modifier.fillMaxSize().testTag("settings_screen"), color = RunmoniColors.Paper) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("설정", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(shape = RunmoniShapes.Action, onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
        }
        HorizontalDivider(color = RunmoniColors.Line)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (updates.updateAvailable) {
            AppVersionCard(updates, onClick = onAppInformation, tag = "settings_app_update")
            HorizontalDivider(color = RunmoniColors.Line)
        }
        ScreenSection("내 프로필", showDivider = false) {
            OutlinedTextField(shape = RunmoniShapes.Input, value = name, onValueChange = { if (it.length <= 30) name = it }, label = { Text("러너 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("profile_name"))
            OutlinedButton(shape = RunmoniShapes.Action, onClick = { actions.updateProfile(name.trim()) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_profile")) { Text("이름 저장") }
        }
        ScreenSection("러닝과 위치") {
            ToggleRow("자동 일시정지", "정지한 동안 러닝 시간을 잠시 멈춰요", state.autoPause, actions::setAutoPause)
            ToggleRow("음성 기록 안내", "거리별 기록을 음성으로 들어요", state.voiceGuidance, actions::setVoiceGuidance)
            LocationSharingToggle(state, actions)
        }
        ScreenSection("기록 요약 백업") {
            Text(if (state.cloud.configured) state.cloud.status else "이 앱에서는 Google 백업을 사용할 수 없어요", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted, modifier = Modifier.testTag("cloud_status"))
            if (state.cloud.uid.isBlank()) {
                OutlinedButton(onClick = actions::signInGoogle, enabled = state.cloud.configured && !state.cloud.busy && !state.communityGoogleLink.busy && !state.busy, shape = RunmoniShapes.Action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("google_sign_in")) { Text(if (state.cloud.busy) "연결 중…" else "Google로 로그인") }
            } else {
                Text(state.cloud.accountLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("cloud_account"))
                OutlinedButton(shape = RunmoniShapes.Action, onClick = { actions.refreshCloudSummaries(state.cloud.uid); cloudHistory = true }, enabled = !state.cloud.busy, modifier = Modifier.fillMaxWidth().testTag("cloud_history")) { Text("백업한 요약 보기") }
                TextButton(shape = RunmoniShapes.Action, onClick = actions::signOutGoogle, enabled = !state.cloud.busy && !state.communityGoogleLink.busy, modifier = Modifier.testTag("google_sign_out")) { Text("Google 로그아웃") }
            }
            SmallNote("기록 상세에서 요약을 백업할 수 있어요. 러닝 경로·포인트·옷장은 포함되지 않아요.")
        }
        ScreenSection("크루 프로필") {
            StatusPill(if (state.serverConnected && state.signedIn) "크루 프로필 연결됨" else if (state.serverConnected) "둘러보기 가능" else "기기 기록 모드", if (state.serverConnected && state.signedIn) RunmoniColors.Green else RunmoniColors.Muted, "backend_status")
            if (state.communityBackend == CommunityBackend.FIREBASE) {
                val link = state.communityGoogleLink
                if (link.linked || link.busy || link.status != CommunityGoogleLinkState().status)
                    Text(link.status, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                        modifier = Modifier.testTag("community_google_status"))
                if (link.linked && link.accountLabel.isNotBlank()) Text(link.accountLabel, style = MaterialTheme.typography.bodyMedium)
                if (link.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.signedIn && !link.linked) OutlinedButton(shape = RunmoniShapes.Action, onClick = { linkCommunityGoogle = true }, enabled = !link.busy && !state.cloud.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("link_community_google")) { Text("크루 프로필에 Google 연결") }
                if (!state.signedIn) OutlinedButton(shape = RunmoniShapes.Action, onClick = { restoreCommunityGoogle = true }, enabled = state.cloud.configured && !link.busy && !state.cloud.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("restore_community_google")) { Text("Google로 크루 프로필 다시 연결") }
                SmallNote("Google을 연결한 크루 소속과 권한을 다시 찾을 수 있어요. 기기의 러닝 경로·포인트·옷장은 이 연결로 복원되지 않아요.")
            }
            if (state.signedIn) OutlinedButton(shape = RunmoniShapes.Action, onClick = { if (state.communityBackend == CommunityBackend.FIREBASE) disconnectRoom = true else actions.signOut() }, enabled = !state.communityGoogleLink.busy && !state.cloud.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sign_out")) { Text(if (state.communityGuest) "크루 프로필 연결 해제" else "파일럿 로그아웃") }
            else OutlinedButton(shape = RunmoniShapes.Action, onClick = onSignIn, enabled = !state.communityGoogleLink.busy && !state.cloud.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_sign_in")) { Text("닉네임으로 시작하기") }
        }
        ScreenSection("도움말 및 앱 정보") {
            OutlinedButton(shape = RunmoniShapes.Action, onClick = onAppInformation, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("settings_app_information")) { Icon(Icons.Outlined.Info, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("앱 정보 · 업데이트") }
            OutlinedButton(shape = RunmoniShapes.Action, onClick = { deviceChecks = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_device_checks")) { Text("연결·휴대폰 점검") }
            TextButton(shape = RunmoniShapes.Action, onClick = { connectionDetails = !connectionDetails }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("toggle_server_details").semantics { stateDescription = if (connectionDetails) "펼침" else "접힘" }) { Text(if (connectionDetails) "개발용 연결 설정 닫기" else "개발용 연결 설정") }
            if (connectionDetails) {
                Text(if (state.communityBackend == CommunityBackend.FIREBASE) "연결 방식: Firebase" else "연결 방식: HTTP 테스트 서버")
                if (state.communityBackend != CommunityBackend.FIREBASE) OutlinedButton(shape = RunmoniShapes.Action, onClick = actions::useFirebaseCommunity, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("use_firebase_rooms")) { Text("Firebase 크루·파티 사용") }
                SmallNote(state.serverStatus)
                OutlinedTextField(shape = RunmoniShapes.Input, value = url, onValueChange = { url = it }, label = { Text("서버 연결 주소") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("server_url"))
                Button(shape = RunmoniShapes.Action, onClick = { actions.configureServer(url.trim()) }, enabled = url.startsWith("http://") || url.startsWith("https://"), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("connect_server")) { Text("서버 연결") }
                SmallNote("USB 연결 테스트는 localhost를 사용해요. 서버 주소는 Google 계정 설정과 별개예요.")
            }
        }
        }
    }
    }
    if (cloudHistory && state.cloud.uid.isNotBlank()) CloudSummariesDialog(state.cloud, actions) { cloudHistory = false }
    if (linkCommunityGoogle) AlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { linkCommunityGoogle = false },
        title = { Text("현재 크루 프로필에 Google을 연결할까요?") },
        text = { DialogMessage("현재 크루 소속과 크루장·주최자 권한을 그대로 유지해요. 이미 다른 프로필에 사용한 Google 계정은 자동으로 합치지 않아요.\n\n이 연결은 기기의 GPS 기록·포인트·옷장 백업과는 별도예요.") },
        confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { linkCommunityGoogle = false; actions.linkCommunityGoogle() }, modifier = Modifier.testTag("confirm_link_community_google")) { Text("Google 계정 선택") } },
        dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { linkCommunityGoogle = false }) { Text("취소") } })
    if (restoreCommunityGoogle) AlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { restoreCommunityGoogle = false },
        title = { Text("기존 크루 프로필을 다시 연결할까요?") },
        text = { DialogMessage("이전에 크루 프로필에 연결한 같은 Google 계정을 선택해 주세요. 기존 크루 소속과 권한을 확인하며 새 프로필을 만들거나 다른 프로필과 합치지 않아요.\n\n기기의 GPS 기록·포인트·옷장은 이 연결로 복원되지 않아요.") },
        confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { restoreCommunityGoogle = false; actions.restoreCommunityGoogle() }, modifier = Modifier.testTag("confirm_restore_community_google")) { Text("Google 계정 선택") } },
        dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { restoreCommunityGoogle = false }) { Text("취소") } })
    if (disconnectRoom) AlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { disconnectRoom = false }, title = { Text("크루 프로필 연결을 해제할까요?") },
        text = { DialogMessage(if (state.communityGoogleLink.linked)
            "연결을 해제하면 이 기기에서 크루·파티를 이용하려면 같은 Google 계정으로 크루 프로필을 다시 연결해야 해요. 기기 러닝 기록과 포인트, 기록 요약 백업 로그인은 유지돼요."
            else "Google을 연결하지 않은 익명 프로필의 크루·파티 참여 내역과 크루장·주최자 권한은 다시 복구할 수 없어요. 기기 러닝 기록과 기록 요약 백업 로그인은 유지돼요.") },
        confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { disconnectRoom = false; actions.signOut() }) { Text("연결 해제") } },
        dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { disconnectRoom = false }) { Text("유지하기") } })
}

@Composable
internal fun SignInDialog(state: AppState, actions: AppActions, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(state.profile.name) }
    var adult by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(state.signedIn) { if (state.signedIn && submitted) onDismiss() }
    FormDialog("크루에서 쓸 닉네임", onDismiss, confirm = {
        TextButton(shape = RunmoniShapes.Action, onClick = { submitted = true; actions.connectCommunity(name.trim()) }, enabled = adult && name.trim().matches(Regex("[\\w가-힣 -]{2,24}")) && !state.busy, modifier = Modifier.testTag("confirm_sign_in")) { Text(if (state.busy) "연결 중" else "참여하기") }
    }) {
        Text("크루 멤버에게 표시할 이름이에요.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(shape = RunmoniShapes.Input, value = name, onValueChange = { if (it.length <= 24) name = it }, label = { Text("닉네임 · 2~24자") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("display_name"))
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(adult, role = Role.Checkbox, onValueChange = { adult = it }).testTag("adult_checkbox"), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = adult, onCheckedChange = null); Spacer(Modifier.width(8.dp)); Text("만 19세 이상입니다", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)) }
        SmallNote(if (state.communityBackend == CommunityBackend.FIREBASE) "이 기기의 익명 프로필로 참여해요. 설정에서 Google 계정을 연결하기 전에는 기기 변경·재설치 후 참여 내역과 크루장 권한을 복구할 수 없어요. 기록 요약 백업과는 별도예요." else "게스트 연결은 180일간 유효해요. 연결 해제·기기 변경·재설치 후에는 이 프로필의 참여 내역을 복구할 수 없어요.")
    }
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
    FormDialog("크루 만들기", onDismiss, confirm = { TextButton(shape = RunmoniShapes.Action, onClick = { submitted = true; actions.createCrew(NewCrew(name.trim(), description.trim(), region.trim(), pace.trim())) }, enabled = name.trim().length in 2..60 && !state.busy, modifier = Modifier.testTag("confirm_create_crew")) { Text("만들기") } }) {
        FormField("크루 이름", name, { if (it.length <= 60) name = it }, "crew_name")
        FormField("크루 소개", description, { description = it.take(500) }, "crew_description", singleLine = false)
        FormField("활동 지역", region, { region = it.take(80) }, "crew_region")
        FormField("기본 페이스", pace, { pace = it.take(80) }, "crew_pace")
        SmallNote("이름·소개·지역·페이스는 크루 목록에 공개돼요.")
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
    FormDialog("파티 만들기", onDismiss, confirm = { TextButton(shape = RunmoniShapes.Action, onClick = { submitted = true; actions.createParty(NewParty(crewId, title.trim(), kind, time!!, if (kind == PartyKind.LOCAL) meeting.trim() else "각자의 러닝 코스", km!!, pace.trim(), max!!)) }, enabled = valid && !state.busy, modifier = Modifier.testTag("confirm_create_party")) { Text("만들기") } }) {
        Text(state.crews.firstOrNull { it.id == crewId }?.name ?: "먼저 크루에 가입해 주세요", style = MaterialTheme.typography.titleMedium)
        FormField("파티 이름", title, { if (it.length <= 80) title = it }, "party_title")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { FilterChip(shape = RunmoniShapes.Action, selected = kind == PartyKind.LOCAL, onClick = { kind = PartyKind.LOCAL }, label = { Text("현장 모임") }, modifier = Modifier.heightIn(min = 48.dp).testTag("party_local")); FilterChip(shape = RunmoniShapes.Action, selected = kind == PartyKind.REMOTE, onClick = { kind = PartyKind.REMOTE }, label = { Text("원격 함께 뛰기") }, modifier = Modifier.heightIn(min = 48.dp).testTag("party_remote")) }
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
    }
}

@Composable
internal fun RecordDialog(record: RunRecord, actions: AppActions, cloud: CloudAccountState = CloudAccountState(), persistence: RunPersistenceState = RunPersistenceState(initialized = true), onDismiss: () -> Unit) {
    var deleteConfirmation by remember { mutableStateOf(false) }
    var backupConfirmation by remember(cloud.uid, record.id) { mutableStateOf(false) }
    FormDialog("나의 러닝 기록", onDismiss, dismissLabel = null, confirm = { TextButton(shape = RunmoniShapes.Action, onClick = onDismiss) { Text("닫기") } }) {
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
            Text("킬로미터별 기록", style = MaterialTheme.typography.titleMedium)
            record.splits.forEach { split -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${split.kilometer} km"); Text(duration(split.durationMillis)) } }
        }
        OutlinedButton(shape = RunmoniShapes.Action, onClick = { actions.exportRun(record.id) }, modifier = Modifier.fillMaxWidth().testTag("export_run")) { Text("GPX 경로 내보내기") }
        if (cloud.uid.isNotBlank()) {
            OutlinedButton(shape = RunmoniShapes.Action, onClick = { backupConfirmation = true }, enabled = record.finishedAtMillis > 0 && !cloud.busy, modifier = Modifier.fillMaxWidth().testTag("backup_cloud_run")) { Text("이 기록 요약을 계정에 백업") }
            SmallNote("백업 계정: ${cloud.accountLabel}\n${cloud.status}")
        }
        TextButton(shape = RunmoniShapes.Action, onClick = { deleteConfirmation = true }, modifier = Modifier.testTag("delete_run")) { Text("기록 삭제", color = MaterialTheme.colorScheme.error) }
    }
    if (backupConfirmation && cloud.uid.isNotBlank()) AlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { backupConfirmation = false }, title = { Text("이 계정에 기록 요약을 백업할까요?") }, text = { DialogMessage("계정: ${cloud.accountLabel}\n\n이 기기에서 선택한 본인의 기록인지 확인해 주세요. 날짜·거리·시간·GPS 포인트 수 요약만 저장해요. 경로 원본·마일리지는 포함하지 않으며, 보상을 검증한 기록으로 인정하지 않아요.") }, confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { backupConfirmation = false; actions.backupCloudRun(record.id, cloud.uid) }, enabled = !cloud.busy, modifier = Modifier.testTag("confirm_cloud_backup")) { Text("이 계정에 백업") } }, dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { backupConfirmation = false }) { Text("취소") } })
    if (deleteConfirmation) AlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { deleteConfirmation = false }, title = { Text("이 기록을 삭제할까요?") }, text = { DialogMessage("삭제한 기기 기록은 복구할 수 없어요. 클라우드에 백업한 요약은 계정 설정에서 별도로 삭제해 주세요.") }, confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { actions.deleteRun(record.id); onDismiss() }, modifier = Modifier.testTag("confirm_delete_run")) { Text("삭제") } }, dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { deleteConfirmation = false }) { Text("취소") } })
}

@Composable
internal fun CloudSummariesDialog(cloud: CloudAccountState, actions: AppActions, onDismiss: () -> Unit) {
    var pendingDelete by remember(cloud.uid) { mutableStateOf<CloudRunSummary?>(null) }
    FormDialog("백업한 기록 요약", onDismiss, dismissLabel = null, confirm = { TextButton(shape = RunmoniShapes.Action, onClick = onDismiss) { Text("닫기") } }) {
        Text(cloud.accountLabel, style = MaterialTheme.typography.titleMedium)
        Text(cloud.status, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        SmallNote("최근 50건의 자가 기록 요약이에요. 경로 원본은 이 기기에만 있어요. 요약으로 경로를 복구하거나 마일리지를 적립하지 않아요.")
        OutlinedButton(shape = RunmoniShapes.Action, onClick = { actions.refreshCloudSummaries(cloud.uid) }, enabled = !cloud.busy && cloud.uid.isNotBlank(), modifier = Modifier.testTag("refresh_cloud_summaries")) { Text("새로고침") }
        if (cloud.summaries.isEmpty() && !cloud.busy) Text("표시할 백업 요약이 없어요.")
        cloud.summaries.forEach { summary ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(dateTime(summary.startedAtMillis)); Text("${distance(summary.distanceMeters)} km · ${duration(summary.activeDurationMillis)}", style = MaterialTheme.typography.bodyMedium) }
                TextButton(shape = RunmoniShapes.Action, onClick = { pendingDelete = summary }, enabled = !cloud.busy, modifier = Modifier.testTag("delete_cloud_${summary.runId}")) { Text("요약 삭제") }
            }
        }
    }
    pendingDelete?.let { summary -> AlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { pendingDelete = null }, title = { Text("이 백업 요약을 삭제할까요?") }, text = { DialogMessage("계정: ${cloud.accountLabel}\n선택한 클라우드 요약만 삭제해요. 기기 기록은 그대로 보관해요.") }, confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { pendingDelete = null; actions.deleteCloudSummary(summary.runId, cloud.uid) }, enabled = !cloud.busy, modifier = Modifier.testTag("confirm_delete_cloud")) { Text("요약 삭제") } }, dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { pendingDelete = null }) { Text("취소") } }) }
}

@Composable
internal fun CrewMembersDialog(state: AppState, crewId: String, actions: AppActions, onDismiss: () -> Unit) {
    LaunchedEffect(crewId) { actions.loadCrewMembers(crewId) }
    val crew = state.crews.firstOrNull { it.id == crewId }
    val members = state.crewMembers[crewId].orEmpty()
    FormDialog("멤버 · DJ 관리", onDismiss, dismissLabel = null, confirm = { TextButton(shape = RunmoniShapes.Action, onClick = onDismiss) { Text("닫기") } }) {
        if (members.isEmpty()) SmallNote("표시할 멤버가 없어요. 크루 정보를 새로고침해 주세요.")
        members.forEach { member ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(member.name, style = MaterialTheme.typography.titleMedium); Text(if (member.isOwner) "크루장" else if (member.isDj) "DJ" else "멤버", style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted) }
                if (!member.isOwner && crew?.isOwner == true) OutlinedButton(shape = RunmoniShapes.Action, onClick = { actions.setCrewDj(crewId, member.id, !member.isDj) }, modifier = Modifier.testTag("assign_dj_${member.id}")) { Text(if (member.isDj) "DJ 해제" else "DJ 임명") }
            }
        }
        SmallNote(if (state.communityBackend == CommunityBackend.FIREBASE) "DJ는 공지를 작성할 수 있어요. 크루 관리 권한은 크루장에게 있어요." else "DJ는 음악 공유를 제어할 수 있어요. 크루 관리 권한은 크루장에게 있어요.")
    }
}

@Composable
internal fun FormDialog(title: String, onDismiss: () -> Unit, confirm: @Composable () -> Unit, dismissLabel: String? = "취소", content: @Composable ColumnScope.() -> Unit) {
    val width = minOf(560.dp, (LocalConfiguration.current.screenWidthDp * 0.94f).dp)
    AlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = onDismiss, modifier = Modifier.width(width).testTag("form_dialog"), properties = DialogProperties(usePlatformDefaultWidth = false), title = { Text(title) }, text = { Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = content) }, confirmButton = confirm, dismissButton = { if (dismissLabel != null) TextButton(shape = RunmoniShapes.Action, onClick = onDismiss) { Text(dismissLabel) } })
}

@Composable
private fun FormField(label: String, value: String, onChange: (String) -> Unit, tag: String, singleLine: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text, hint: String? = null, error: String? = null) {
    val support: @Composable (() -> Unit)? = if (hint == null && error == null) null else { { Text(error ?: hint.orEmpty(), modifier = Modifier.testTag("${tag}_support")) } }
    OutlinedTextField(shape = RunmoniShapes.Input, value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth().testTag(tag), singleLine = singleLine, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), isError = error != null, supportingText = support)
}

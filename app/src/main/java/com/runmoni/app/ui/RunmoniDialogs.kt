@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.runmoni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.runmoni.app.domain.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler

@Composable
internal fun SettingsScreen(state: AppState, actions: AppActions, onDismiss: () -> Unit, onSignIn: () -> Unit, onAppInformation: () -> Unit = {}) {
    var url by remember(state.apiUrl) { mutableStateOf(state.apiUrl) }
    var name by remember(state.profile.name) { mutableStateOf(state.profile.name) }
    var cloudHistory by remember(state.cloud.uid) { mutableStateOf(false) }
    var connectionDetails by remember { mutableStateOf(false) }
    BackHandler(onBack = onDismiss)
    Surface(Modifier.fillMaxSize().testTag("settings_screen"), color = RunmoniColors.Paper) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("설정", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
        }
        HorizontalDivider(color = RunmoniColors.Line)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Google 계정", style = MaterialTheme.typography.titleMedium)
        Text(state.cloud.status, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted, modifier = Modifier.testTag("cloud_status"))
        if (state.cloud.uid.isBlank()) {
            OutlinedButton(onClick = actions::signInGoogle, enabled = state.cloud.configured && !state.cloud.busy && !state.busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("google_sign_in")) { Text(if (state.cloud.busy) "연결 중…" else "Google로 로그인") }
            if (!state.cloud.configured) SmallNote("클라우드 백업 연결을 준비 중이에요. 러닝 기록은 계속 기기에 저장할 수 있어요.")
        } else {
            Text(state.cloud.accountLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("cloud_account"))
            OutlinedButton(onClick = { actions.refreshCloudSummaries(state.cloud.uid); cloudHistory = true }, enabled = !state.cloud.busy, modifier = Modifier.fillMaxWidth().testTag("cloud_history")) { Text("백업한 기록 요약 보기") }
            TextButton(onClick = actions::signOutGoogle, enabled = !state.cloud.busy, modifier = Modifier.testTag("google_sign_out")) { Text("Google 계정 로그아웃") }
        }
        SmallNote("기록 상세에서 요약을 1건씩 백업해요. GPS 경로·마일리지는 포함하지 않아요.")
        HorizontalDivider()
        Text("내 프로필", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = name, onValueChange = { if (it.length <= 30) name = it }, label = { Text("러너 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("profile_name"))
        OutlinedButton(onClick = { actions.updateProfile(name.trim()) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_profile")) { Text("이름 저장") }
        HorizontalDivider()
        Text("러닝과 위치", style = MaterialTheme.typography.titleMedium)
        ToggleRow("자동 일시정지", "정지한 동안 러닝 시간을 잠시 멈춰요", state.autoPause, actions::setAutoPause)
        ToggleRow("음성 기록 안내", "거리별 기록을 음성으로 들어요", state.voiceGuidance, actions::setVoiceGuidance)
        LocationSharingToggle(state, actions)
        HorizontalDivider()
        Text("초대 파일럿 · 크루 연결", style = MaterialTheme.typography.titleMedium)
        StatusPill(if (state.serverConnected && state.signedIn) "파일럿 연결됨" else if (state.serverConnected) "로그인 필요" else "기기 기록 모드", if (state.serverConnected && state.signedIn) RunmoniColors.Green else RunmoniColors.Muted, "backend_status")
        Text(if (state.signedIn) "초대 파일럿 로그인됨" else "크루·파티 테스트에는 별도 초대가 필요해요", style = MaterialTheme.typography.bodyMedium)
        SmallNote("Google 계정과 별도로 연결하며 위치 공유는 직접 켜야 해요.")
        Text(state.serverStatus, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
        if (state.signedIn) OutlinedButton(onClick = actions::signOut, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sign_out")) { Text("파일럿 로그아웃") }
        else OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_sign_in")) { Text("초대코드로 로그인") }
        TextButton(onClick = { connectionDetails = !connectionDetails }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("toggle_server_details").semantics { stateDescription = if (connectionDetails) "펼침" else "접힘" }) { Text(if (connectionDetails) "테스트 서버 설정 접기" else "테스트 서버 설정 펼치기") }
        if (connectionDetails) {
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("서버 연결 주소") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("server_url"))
            Button(onClick = { actions.configureServer(url.trim()) }, enabled = url.startsWith("http://") || url.startsWith("https://"), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("connect_server")) { Text("서버 연결") }
            SmallNote("USB 연결 테스트는 localhost를 사용해요. 서버 주소는 Google 계정 설정과 별개예요.")
        }
        HorizontalDivider()
        OutlinedButton(onClick = onAppInformation, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("settings_app_information")) { Icon(Icons.Outlined.Info, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("앱 정보 · 버전 · 지원") }
        }
    }
    }
    if (cloudHistory && state.cloud.uid.isNotBlank()) CloudSummariesDialog(state.cloud, actions) { cloudHistory = false }
}

@Composable
internal fun SignInDialog(state: AppState, actions: AppActions, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(state.profile.name) }
    var adult by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(state.signedIn) { if (state.signedIn && submitted) onDismiss() }
    FormDialog("초대 파일럿에 참여하기", onDismiss, confirm = {
        TextButton(onClick = { submitted = true; actions.signIn(code.trim(), name.trim()) }, enabled = adult && code.isNotBlank() && name.isNotBlank() && !state.busy, modifier = Modifier.testTag("confirm_sign_in")) { Text(if (state.busy) "연결 중" else "참여하기") }
    }) {
        Text("초대받은 성인 테스터를 위한 Android 파일럿이에요. 크루·파티·채팅은 연결된 서버에서 동작해요.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("초대코드") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("invite_code"))
        OutlinedTextField(value = name, onValueChange = { if (it.length <= 30) name = it }, label = { Text("러너 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("display_name"))
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(adult, role = Role.Checkbox, onValueChange = { adult = it }).testTag("adult_checkbox"), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = adult, onCheckedChange = null); Spacer(Modifier.width(8.dp)); Text("만 19세 이상이며 초대받은 테스터입니다", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)) }
        SmallNote("${state.apiUrl}\n${state.serverStatus}")
    }
}

@Composable
internal fun NewCrewDialog(actions: AppActions, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var pace by remember { mutableStateOf("자유 페이스") }
    FormDialog("우리 크루 만들기", onDismiss, confirm = { TextButton(onClick = { actions.createCrew(NewCrew(name.trim(), description.trim(), region.trim(), pace.trim())); onDismiss() }, enabled = name.isNotBlank(), modifier = Modifier.testTag("confirm_create_crew")) { Text("만들기") } }) {
        FormField("크루 이름", name, { if (it.length <= 40) name = it }, "crew_name")
        FormField("크루 소개", description, { description = it }, "crew_description", singleLine = false)
        FormField("활동 지역", region, { region = it }, "crew_region")
        FormField("기본 페이스", pace, { pace = it }, "crew_pace")
        SmallNote("현장 모임과 원격 모임을 모두 만들 수 있어요. 크루장은 공지와 DJ 역할을 관리해요.")
    }
}

@Composable
internal fun NewPartyDialog(crewId: String, actions: AppActions, onDismiss: () -> Unit) {
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
    val valid = title.isNotBlank() && time != null && time > System.currentTimeMillis() && km != null && km.isFinite() && km > 0 && km <= 100 && max != null && max in 2..20 && (kind == PartyKind.REMOTE || meeting.isNotBlank())
    FormDialog("러닝 파티 만들기", onDismiss, confirm = { TextButton(onClick = { actions.createParty(NewParty(crewId, title.trim(), kind, time!!, if (kind == PartyKind.LOCAL) meeting.trim() else "원격 음성방", km!!, pace.trim(), max!!)); onDismiss() }, enabled = valid, modifier = Modifier.testTag("confirm_create_party")) { Text("만들기") } }) {
        FormField("파티 이름", title, { if (it.length <= 50) title = it }, "party_title")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { FilterChip(selected = kind == PartyKind.LOCAL, onClick = { kind = PartyKind.LOCAL }, label = { Text("현장 모임") }, modifier = Modifier.heightIn(min = 48.dp).testTag("party_local")); FilterChip(selected = kind == PartyKind.REMOTE, onClick = { kind = PartyKind.REMOTE }, label = { Text("원격 함께 뛰기") }, modifier = Modifier.heightIn(min = 48.dp).testTag("party_remote")) }
        AdaptiveActions(
            first = { modifier -> OutlinedButton(onClick = {
                val chosen = runCatching { LocalDateTime.parse(starts, formatter) }.getOrElse { LocalDateTime.now().plusHours(1) }
                DatePickerDialog(context, { _, year, month, day -> starts = chosen.withYear(year).withMonth(month + 1).withDayOfMonth(day).format(formatter) }, chosen.year, chosen.monthValue - 1, chosen.dayOfMonth).show()
            }, modifier = modifier.heightIn(min = 48.dp).testTag("pick_party_date")) { Text("날짜 선택") } },
            second = { modifier -> OutlinedButton(onClick = {
                val chosen = runCatching { LocalDateTime.parse(starts, formatter) }.getOrElse { LocalDateTime.now().plusHours(1) }
                TimePickerDialog(context, { _, hour, minute -> starts = chosen.withHour(hour).withMinute(minute).format(formatter) }, chosen.hour, chosen.minute, true).show()
            }, modifier = modifier.heightIn(min = 48.dp).testTag("pick_party_time")) { Text("시간 선택") } },
        )
        FormField("시작 날짜·시간", starts, { starts = it }, "party_time", hint = "휴대폰 시간 · 예: ${LocalDateTime.now().plusHours(1).format(formatter)}", error = when { time == null -> "날짜와 시간을 yyyy-MM-dd HH:mm 형식으로 입력해 주세요"; time <= System.currentTimeMillis() -> "현재보다 나중 시간을 선택해 주세요"; else -> null })
        if (kind == PartyKind.LOCAL) FormField("집결 장소", meeting, { meeting = it }, "party_meeting") else SmallNote("각자의 장소에서 뛰며 파티 음성방으로 만나요.")
        FormField("목표 거리 · km", distance, { distance = it }, "party_distance", keyboardType = KeyboardType.Decimal, error = if (km == null || !km.isFinite() || km <= 0 || km > 100) "거리는 0 초과 100km 이하로 입력해 주세요" else null)
        FormField("페이스 범위", pace, { pace = it }, "party_pace")
        FormField("정원 · 2~20명", capacity, { capacity = it }, "party_capacity", keyboardType = KeyboardType.Number, error = if (max == null || max !in 2..20) "정원은 2~20명으로 입력해 주세요" else null)
        if (!valid) SmallNote("미래의 시작 시간, 거리, 정원을 확인해 주세요. 현장 모임에는 집결 장소가 필요해요.")
    }
}

@Composable
internal fun PartyDialog(state: AppState, original: Party, actions: AppActions, onSignIn: () -> Unit = {}, onDismiss: () -> Unit) {
    val party = state.parties.firstOrNull { it.id == original.id } ?: original
    val crew = state.crews.firstOrNull { it.id == party.crewId }
    var cancelConfirmation by remember { mutableStateOf(false) }
    FormDialog(party.title, onDismiss, dismissLabel = null, confirm = { TextButton(onClick = onDismiss) { Text("닫기") } }) {
        StatusPill(if (party.kind == PartyKind.LOCAL) "현장 러닝" else "원격 함께 뛰기", RunmoniColors.Green)
        Text(dateTime(party.startsAtMillis), style = MaterialTheme.typography.titleMedium)
        Text("${party.distanceKm} km · ${party.paceLabel}")
        Text(if (party.kind == PartyKind.LOCAL) "집결지 · ${party.meetingLabel}" else "각자의 장소에서 뛰고 음성방에서 만나요", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        Text("참가 ${party.participantCount}/${party.capacity}명")
        if (party.cancelled) Text("취소된 파티예요", color = MaterialTheme.colorScheme.error)
        else if (party.isOrganizer) SmallNote("주최자는 참가를 취소할 수 없어요. 모임을 종료하려면 파티를 취소해 주세요.")
        else Button(onClick = { if (party.joined) actions.leaveParty(party.id) else actions.joinParty(party.id) }, enabled = state.signedIn && (party.joined || party.participantCount < party.capacity), modifier = Modifier.fillMaxWidth().testTag("join_party_${party.id}")) { Text(if (party.joined) "참가 취소" else if (party.participantCount >= party.capacity) "정원이 가득 찼어요" else "파티 참가") }
        if (!state.signedIn) OutlinedButton(onClick = { onDismiss(); onSignIn() }, modifier = Modifier.fillMaxWidth()) { Text("로그인하고 참가하기") }
        if (party.joined && !party.cancelled) {
            OutlinedButton(onClick = { actions.openChat("party:${party.id}", party.title); onDismiss() }, modifier = Modifier.fillMaxWidth().testTag("open_party_chat")) { Text("파티 공지 · 채팅") }
            AudioPanel(state.audio, actions, "party:${party.id}")
        }
        if ((crew?.isOwner == true || party.isOrganizer) && !party.cancelled) TextButton(onClick = { cancelConfirmation = true }, modifier = Modifier.testTag("cancel_party_${party.id}")) { Text("파티 취소", color = MaterialTheme.colorScheme.error) }
        SmallNote("파티 음성 연결과 내 GPS 기록은 별개예요. 러닝 탭에서 각자 기록을 시작해 주세요.")
    }
    if (cancelConfirmation) AlertDialog(onDismissRequest = { cancelConfirmation = false }, title = { Text("파티를 취소할까요?") }, text = { DialogMessage("참가한 러너에게 취소 상태가 표시돼요.") }, confirmButton = { TextButton(onClick = { actions.cancelParty(party.id); cancelConfirmation = false }, modifier = Modifier.testTag("confirm_cancel_party")) { Text("파티 취소") } }, dismissButton = { TextButton(onClick = { cancelConfirmation = false }) { Text("유지하기") } })
}

@Composable
internal fun RecordDialog(record: RunRecord, actions: AppActions, cloud: CloudAccountState = CloudAccountState(), onDismiss: () -> Unit) {
    var deleteConfirmation by remember { mutableStateOf(false) }
    var backupConfirmation by remember(cloud.uid, record.id) { mutableStateOf(false) }
    FormDialog("나의 러닝 기록", onDismiss, dismissLabel = null, confirm = { TextButton(onClick = onDismiss) { Text("닫기") } }) {
        Text(dateTime(record.startedAtMillis), style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        Text("${distance(record.distanceMeters)} km", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("record_distance"))
        Text("러닝 시간 ${duration(record.activeDurationMillis)} · 평균 ${averagePace(record.distanceMeters, record.activeDurationMillis)} /km", modifier = Modifier.testTag("record_duration_pace"))
        MapPane(record.points, modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(18.dp)))
        StatusPill(if (record.synced) "서버 동기화됨" else "기기에 저장됨", if (record.synced) RunmoniColors.Green else RunmoniColors.Muted)
        if (record.interrupted) SmallNote("중단된 구간이 있어요. 확인되지 않은 거리는 더하지 않았어요.")
        if (record.splits.isNotEmpty()) {
            Text("킬로미터별 기록", style = MaterialTheme.typography.titleMedium)
            record.splits.forEach { split -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${split.kilometer} km"); Text(duration(split.durationMillis)) } }
        }
        OutlinedButton(onClick = { actions.exportRun(record.id) }, modifier = Modifier.fillMaxWidth().testTag("export_run")) { Text("GPX 경로 내보내기") }
        if (cloud.uid.isNotBlank()) {
            OutlinedButton(onClick = { backupConfirmation = true }, enabled = record.finishedAtMillis > 0 && !cloud.busy, modifier = Modifier.fillMaxWidth().testTag("backup_cloud_run")) { Text("이 기록 요약을 계정에 백업") }
            SmallNote("백업 계정: ${cloud.accountLabel}\n${cloud.status}")
        }
        TextButton(onClick = { deleteConfirmation = true }, modifier = Modifier.testTag("delete_run")) { Text("기록 삭제", color = MaterialTheme.colorScheme.error) }
    }
    if (backupConfirmation && cloud.uid.isNotBlank()) AlertDialog(onDismissRequest = { backupConfirmation = false }, title = { Text("이 계정에 기록 요약을 백업할까요?") }, text = { DialogMessage("계정: ${cloud.accountLabel}\n\n이 기기에서 선택한 본인의 기록인지 확인해 주세요. 날짜·거리·시간·GPS 포인트 수 요약만 저장해요. 경로 원본·마일리지는 포함하지 않으며, 보상을 검증한 기록으로 인정하지 않아요.") }, confirmButton = { TextButton(onClick = { backupConfirmation = false; actions.backupCloudRun(record.id, cloud.uid) }, enabled = !cloud.busy, modifier = Modifier.testTag("confirm_cloud_backup")) { Text("이 계정에 백업") } }, dismissButton = { TextButton(onClick = { backupConfirmation = false }) { Text("취소") } })
    if (deleteConfirmation) AlertDialog(onDismissRequest = { deleteConfirmation = false }, title = { Text("이 기록을 삭제할까요?") }, text = { DialogMessage("삭제한 기기 기록은 복구할 수 없어요. 클라우드에 백업한 요약은 계정 설정에서 별도로 삭제해 주세요.") }, confirmButton = { TextButton(onClick = { actions.deleteRun(record.id); onDismiss() }, modifier = Modifier.testTag("confirm_delete_run")) { Text("삭제") } }, dismissButton = { TextButton(onClick = { deleteConfirmation = false }) { Text("취소") } })
}

@Composable
internal fun CloudSummariesDialog(cloud: CloudAccountState, actions: AppActions, onDismiss: () -> Unit) {
    var pendingDelete by remember(cloud.uid) { mutableStateOf<CloudRunSummary?>(null) }
    FormDialog("백업한 기록 요약", onDismiss, dismissLabel = null, confirm = { TextButton(onClick = onDismiss) { Text("닫기") } }) {
        Text(cloud.accountLabel, style = MaterialTheme.typography.titleMedium)
        Text(cloud.status, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        SmallNote("최근 50건의 자가 기록 요약이에요. 경로 원본은 이 기기에만 있어요. 요약으로 경로를 복구하거나 마일리지를 적립하지 않아요.")
        OutlinedButton(onClick = { actions.refreshCloudSummaries(cloud.uid) }, enabled = !cloud.busy && cloud.uid.isNotBlank(), modifier = Modifier.testTag("refresh_cloud_summaries")) { Text("새로고침") }
        if (cloud.summaries.isEmpty() && !cloud.busy) Text("표시할 백업 요약이 없어요.")
        cloud.summaries.forEach { summary ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(dateTime(summary.startedAtMillis)); Text("${distance(summary.distanceMeters)} km · ${duration(summary.activeDurationMillis)}", style = MaterialTheme.typography.bodyMedium) }
                TextButton(onClick = { pendingDelete = summary }, enabled = !cloud.busy, modifier = Modifier.testTag("delete_cloud_${summary.runId}")) { Text("요약 삭제") }
            }
        }
    }
    pendingDelete?.let { summary -> AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("이 백업 요약을 삭제할까요?") }, text = { DialogMessage("계정: ${cloud.accountLabel}\n선택한 클라우드 요약만 삭제해요. 기기 기록은 그대로 보관해요.") }, confirmButton = { TextButton(onClick = { pendingDelete = null; actions.deleteCloudSummary(summary.runId, cloud.uid) }, enabled = !cloud.busy, modifier = Modifier.testTag("confirm_delete_cloud")) { Text("요약 삭제") } }, dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } }) }
}

@Composable
internal fun ClosetDialog(profile: Profile, actions: AppActions, onDismiss: () -> Unit) {
    var slot by remember { mutableStateOf("top") }
    var accessorySlot by remember { mutableStateOf("eyewear") }
    var animate by remember { mutableStateOf(true) }
    val slots = AvatarCatalogue.slots
    val width = minOf(560.dp, (LocalConfiguration.current.screenWidthDp * 0.94f).dp)
    val compactPreview = LocalDensity.current.fontScale >= 1.3f
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.width(width).testTag("form_dialog"),
        properties = DialogProperties(usePlatformDefaultWidth = false), title = { Text("나의 미니미 옷장") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(if (compactPreview) 96.dp else 144.dp).clip(RoundedCornerShape(22.dp)).background(RunmoniColors.Mint), contentAlignment = Alignment.Center) {
            AvatarMini(profile.avatar, Modifier.size(if (compactPreview) 88.dp else 136.dp).testTag("closet_avatar_preview"), running = animate)
        }
        if (compactPreview) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${profile.mileage} P", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { animate = !animate }, modifier = Modifier.size(48.dp).testTag("closet_animation")) { Icon(if (animate) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (animate) "달리기 멈춤" else "달리기 재생") }
        } else {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${profile.mileage} P", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { animate = !animate }, modifier = Modifier.heightIn(min = 48.dp).testTag("closet_animation")) { Text(if (animate) "달리기 멈춤" else "달리기 재생") }
        }
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AvatarCatalogue.tabs.forEach { (key, label) ->
                FilterChip(selected = if (key == "accessories") slot in AvatarCatalogue.accessoryKeys else slot == key,
                    onClick = { slot = if (key == "accessories") accessorySlot else key }, label = { Text(label) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("closet_slot_$key"))
            }
        }
        if (slot in AvatarCatalogue.accessoryKeys) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slots.filter { it.key in AvatarCatalogue.accessoryKeys }.forEach { item ->
                FilterChip(selected = slot == item.key, onClick = { slot = item.key; accessorySlot = item.key }, label = { Text(item.label) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("closet_slot_${item.key}"))
            }
        }
        val covered = slot in AvatarCatalogue.coveredSlots(profile.avatar.costume)
        if (profile.avatar.costume != 0) SmallNote(if (covered) "이 부위는 세트가 덮고 있어요. 일반 아이템을 착용하면 세트가 해제돼요." else "세트가 덮지 않는 얼굴·헤어·장비는 함께 바꿀 수 있어요.")
        val equipped = profile.avatar.variantFor(slot)
        slots.first { it.key == slot }.variants.indices.forEach { variant ->
            val owned = "$slot:$variant" in profile.ownedItems
            val price = AvatarCatalogue.price(variant)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AvatarMini(profile.avatar.withItem(slot, variant), Modifier.size(64.dp), decorative = true)
                    Text(slots.first { it.key == slot }.variants[variant], style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                Text(if (owned) "보유 중" else "${price} P · 러닝 마일리지", style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted)
                if (equipped == variant && !covered) StatusPill("착용 중", RunmoniColors.Green)
                else FilledTonalButton(onClick = { if (owned) actions.equipItem(slot, variant) else actions.buyItem(slot, variant) }, enabled = owned || profile.mileage >= price, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("closet_${slot}_$variant")) { Text(if (owned) "착용" else "${price} P 구매") }
            }
            HorizontalDivider(color = RunmoniColors.Line)
        }
        SmallNote("파일럿 옷장은 러닝 마일리지로 이용해요. 현금 결제와 유료 아이템 판매는 연결되어 있지 않아요.")
        SmallNote("헤어와 옷은 성별 제한 없이 고를 수 있어요. 머리 장비·선글라스·워치·벨트·조끼를 함께 착용할 수 있어요.")
        }
        }
    })
}

@Composable
internal fun CrewMembersDialog(state: AppState, crewId: String, actions: AppActions, onDismiss: () -> Unit) {
    val crew = state.crews.firstOrNull { it.id == crewId }
    val members = state.crewMembers[crewId].orEmpty()
    val clipboard = LocalClipboardManager.current
    FormDialog("멤버 · DJ · 초대 관리", onDismiss, dismissLabel = null, confirm = { TextButton(onClick = onDismiss) { Text("닫기") } }) {
        crew?.invitationCode?.takeIf { it.isNotBlank() }?.let { invitation ->
            Text("크루 초대코드", style = MaterialTheme.typography.titleMedium)
            Text(invitation, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("crew_invitation_code"))
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(invitation)) }, modifier = Modifier.fillMaxWidth().testTag("copy_crew_invitation")) { Text("초대코드 복사") }
        }
        if (members.isEmpty()) SmallNote("표시할 멤버가 없어요. 크루 정보를 새로고침해 주세요.")
        members.forEach { member ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(member.name, style = MaterialTheme.typography.titleMedium); Text(if (member.isOwner) "크루장" else if (member.isDj) "DJ" else "멤버", style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted) }
                if (!member.isOwner && crew?.isOwner == true) OutlinedButton(onClick = { actions.setCrewDj(crewId, member.id, !member.isDj) }, modifier = Modifier.testTag("assign_dj_${member.id}")) { Text(if (member.isDj) "DJ 해제" else "DJ 임명") }
            }
        }
        SmallNote("DJ는 음악 공유를 제어할 수 있어요. 크루 관리 권한은 크루장에게 있어요.")
    }
}

@Composable
internal fun FormDialog(title: String, onDismiss: () -> Unit, confirm: @Composable () -> Unit, dismissLabel: String? = "취소", content: @Composable ColumnScope.() -> Unit) {
    val width = minOf(560.dp, (LocalConfiguration.current.screenWidthDp * 0.94f).dp)
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.width(width).testTag("form_dialog"), properties = DialogProperties(usePlatformDefaultWidth = false), title = { Text(title) }, text = { Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = content) }, confirmButton = confirm, dismissButton = { if (dismissLabel != null) TextButton(onClick = onDismiss) { Text(dismissLabel) } })
}

@Composable
private fun FormField(label: String, value: String, onChange: (String) -> Unit, tag: String, singleLine: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text, hint: String? = null, error: String? = null) {
    val support: @Composable (() -> Unit)? = if (hint == null && error == null) null else { { Text(error ?: hint.orEmpty(), modifier = Modifier.testTag("${tag}_support")) } }
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth().testTag(tag), singleLine = singleLine, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), isError = error != null, supportingText = support)
}

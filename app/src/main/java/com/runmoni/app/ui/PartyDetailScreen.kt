package com.runmoni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.runmoni.app.domain.*

@Composable
internal fun PartyDialog(state: AppState, original: Party, actions: AppActions, onSignIn: () -> Unit = {}, onDismiss: () -> Unit) {
    val party = state.parties.firstOrNull { it.id == original.id } ?: original
    val crew = state.crews.firstOrNull { it.id == party.crewId }
    var confirmation by remember { mutableStateOf(false) }
    val initiallyPresent = remember { state.parties.any { it.id == original.id } }
    LaunchedEffect(state.parties) { if (initiallyPresent && state.parties.none { it.id == original.id }) onDismiss() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("party_detail"), color = RunmoniColors.Cream) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).testTag("back_party")) { Icon(Icons.Outlined.ArrowBack, "뒤로가기") }
                    Text("파티", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
                }
                HorizontalDivider(color = RunmoniColors.Line)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(crew?.name ?: "이전 버전 파티", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Primary, modifier = Modifier.testTag("parent_crew_name"))
                        Text(party.title, style = MaterialTheme.typography.headlineMedium)
                        Text(if (party.kind == PartyKind.LOCAL) "현장 러닝" else "각자의 장소에서 함께 뛰기", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        PartyFact("일정", dateTime(party.startsAtMillis))
                        PartyFact("장소", if (party.kind == PartyKind.LOCAL) party.meetingLabel else "각자의 러닝 코스")
                        PartyFact("거리 · 페이스", "${party.distanceKm} km · ${party.paceLabel}")
                        PartyFact("참가 인원", "${party.participantCount} / ${party.capacity}명")
                    }
                    if (party.cancelled) Text("종료된 파티예요", color = RunmoniColors.Muted)
                    else if (crew == null) SmallNote("이전 버전에서 만든 파티예요. 새로운 파티는 크루 안에서 만들 수 있어요.")
                    else if (!party.isOrganizer) Button(onClick = { if (!state.signedIn) onSignIn() else if (party.joined) actions.leaveParty(party.id) else actions.joinParty(party.id) },
                        enabled = !state.busy && (party.joined || party.participantCount < party.capacity && party.startsAtMillis >= System.currentTimeMillis()),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("join_party_${party.id}")) {
                        Text(if (party.joined) "파티 나가기" else if (!crew.joined) "크루 가입하고 참가" else "파티 참가하기")
                    }
                    if (party.joined && !party.cancelled && crew?.joined == true) {
                        OutlinedButton(onClick = { actions.openChat("party:${party.id}", party.title); onDismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_party_chat")) { Text("파티 공지 · 채팅") }
                        if (state.communityBackend == CommunityBackend.HTTP) AudioPanel(state.audio, actions, "party:${party.id}")
                    }
                    SmallNote("러닝 탭에서 각자 기록을 시작해요. 파티 참가만으로 GPS 위치가 공유되지는 않아요.")
                    if (crew?.isOwner == true || party.isOrganizer) {
                        HorizontalDivider(color = RunmoniColors.Line)
                        TextButton(onClick = { confirmation = true }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("delete_party_${party.id}")) { Text("파티 삭제", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    if (confirmation) AlertDialog(onDismissRequest = { confirmation = false }, title = { Text("파티를 삭제할까요?") },
        text = { DialogMessage("크루의 파티 목록에서 사라지고 더 이상 참가하거나 채팅할 수 없어요. 대화 기록과 내 러닝 기록은 보관돼요.") },
        confirmButton = { TextButton(onClick = { confirmation = false; actions.deleteParty(party.id) }, modifier = Modifier.testTag("confirm_delete_party")) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmation = false }, modifier = Modifier.testTag("cancel_delete_party")) { Text("유지하기") } })
}

@Composable
private fun PartyFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

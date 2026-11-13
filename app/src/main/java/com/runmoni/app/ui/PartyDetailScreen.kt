package com.runmoni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.runmoni.app.domain.*

@Composable
internal fun PartyDialog(state: AppState, original: Party, actions: AppActions, onSignIn: () -> Unit = {}, onDismiss: () -> Unit) {
    val party = state.parties.firstOrNull { it.id == original.id } ?: original
    val crew = state.crews.firstOrNull { it.id == party.crewId }
    var confirmation by remember(party.id, state.communityUid) { mutableStateOf(false) }
    val partyScroll = rememberScrollState()
    val initiallyPresent = remember { state.parties.any { it.id == original.id } }
    LaunchedEffect(state.parties) { if (initiallyPresent && state.parties.none { it.id == original.id }) onDismiss() }
    if (!confirmation) Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("party_detail"), color = RunmoniColors.Cream) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Row(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth().heightIn(min = 64.dp)
                        .padding(start = pageGutter(), end = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).testTag("back_party")) { Icon(Icons.Outlined.ArrowBack, "뒤로가기") }
                        Text("파티", style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).semantics { heading() })
                        TextButton(shape = RunmoniShapes.Action, onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
                    }
                }
                HorizontalDivider(color = RunmoniColors.Line)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxSize()
                        .verticalScroll(partyScroll).testTag("party_content")
                        .padding(start = pageGutter(), end = pageGutter(), top = 20.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(RunmoniLayout.PageGap)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Groups, null, tint = RunmoniColors.Primary, modifier = Modifier.size(20.dp))
                                Text(crew?.name ?: "이전 버전 파티", style = MaterialTheme.typography.bodyMedium,
                                    color = RunmoniColors.Primary, modifier = Modifier.testTag("parent_crew_name"))
                            }
                            Text(party.title, style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.semantics { heading() })
                        }
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Surface(color = RunmoniColors.MintWash, shape = RunmoniShapes.Surface) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(Icons.Outlined.Event, null, tint = RunmoniColors.Primary, modifier = Modifier.size(24.dp))
                                        Text(dateTime(party.startsAtMillis), style = MaterialTheme.typography.titleLarge,
                                            color = RunmoniColors.Primary, modifier = Modifier.weight(1f))
                                    }
                                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(Icons.Outlined.LocationOn, null, tint = RunmoniColors.Primary, modifier = Modifier.size(24.dp))
                                        PartyFact(if (party.kind == PartyKind.LOCAL) "모이는 곳" else "출발 장소",
                                            if (party.kind == PartyKind.LOCAL) party.meetingLabel.ifBlank { "모이는 장소 미정" } else "각자의 러닝 코스",
                                            Modifier.weight(1f))
                                    }
                                }
                            }
                            AdaptiveActions(
                                first = { modifier -> PartyFact("거리", "${party.distanceKm} km", modifier) },
                                second = { modifier -> PartyFact("페이스", party.paceLabel, modifier) },
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("참가 인원 ${party.participantCount} / ${party.capacity}명",
                                    style = MaterialTheme.typography.bodyLarge)
                                if (party.isOrganizer || party.joined) Text(if (party.isOrganizer) "파티 주최자" else "참가 중인 파티",
                                    style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Primary)
                            }
                        }
                        if (party.cancelled) Text("종료된 파티예요", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                        else if (crew == null) SmallNote("이전 버전에서 만든 파티예요. 새로운 파티는 크루 안에서 만들 수 있어요.")
                        else if (!party.isOrganizer && !party.joined) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(shape = RunmoniShapes.Action, onClick = { if (!state.signedIn) onSignIn() else actions.joinParty(party.id) },
                                    enabled = !state.busy && party.participantCount < party.capacity && party.startsAtMillis >= System.currentTimeMillis(),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("join_party_${party.id}")) {
                                    Text(if (!crew.joined) "크루 가입하고 참가" else "파티 참가하기")
                                }
                                if (party.participantCount >= party.capacity) SmallNote("정원이 가득 찼어요.")
                                else if (party.startsAtMillis < System.currentTimeMillis()) SmallNote("이미 시작한 파티예요.")
                            }
                        }
                        if (party.joined && !party.cancelled && crew?.joined == true) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(shape = RunmoniShapes.Action, onClick = { actions.openChat("party:${party.id}", party.title); onDismiss() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("open_party_chat")) {
                                Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp)); Text("파티 공지 · 채팅")
                            }
                            if (state.communityBackend == CommunityBackend.HTTP) AudioPanel(state.audio, actions, "party:${party.id}")
                        }
                        HorizontalDivider(color = RunmoniColors.Line)
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(if (party.kind == PartyKind.LOCAL) "현장 러닝" else "각자의 장소에서 함께 뛰기",
                                style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                            SmallNote("러닝 탭에서 각자 기록을 시작해요. 파티 참가만으로 GPS 위치가 공유되지는 않아요.")
                        }
                        if (!party.cancelled && crew != null && !party.isOrganizer && party.joined) {
                            TextButton(shape = RunmoniShapes.Action, onClick = { if (!state.signedIn) onSignIn() else actions.leaveParty(party.id) },
                                enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("join_party_${party.id}")) { Text("파티 나가기") }
                        }
                        if (crew?.isOwner == true || party.isOrganizer) ScreenSection("파티 관리") {
                            TextButton(shape = RunmoniShapes.Action, onClick = { confirmation = true }, enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("delete_party_${party.id}")) {
                                Text("파티 삭제", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmation) DismissibleAlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { confirmation = false }, title = { Text("파티를 삭제할까요?") },
        text = { DialogMessage("크루의 파티 목록에서 사라지고 더 이상 참가하거나 채팅할 수 없어요. 대화 기록과 내 러닝 기록은 보관돼요.") },
        confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { confirmation = false; actions.deleteParty(party.id) }, modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_delete_party")) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { confirmation = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("cancel_delete_party")) { Text("유지하기") } })
}

@Composable
private fun PartyFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

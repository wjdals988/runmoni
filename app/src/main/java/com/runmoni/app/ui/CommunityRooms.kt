@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.runmoni.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*

@Composable
internal fun CommunityRoomsScreen(state: AppState, actions: AppActions) {
    var type by rememberSaveable { mutableIntStateOf(0) }
    var mine by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCrewId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPartyId by rememberSaveable { mutableStateOf<String?>(null) }
    var manageId by rememberSaveable { mutableStateOf<String?>(null) }
    var newCrew by rememberSaveable { mutableStateOf(false) }
    var newParty by rememberSaveable { mutableStateOf(false) }
    var partyCrewId by rememberSaveable { mutableStateOf<String?>(null) }
    var connecting by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var seenCrew by rememberSaveable { mutableStateOf(state.lastCreatedCrewId) }
    var seenParty by rememberSaveable { mutableStateOf(state.lastCreatedPartyId) }
    fun perform(action: String) {
        when {
            action == "newCrew" -> newCrew = true
            action == "newParty" -> { partyCrewId = null; newParty = true }
            action.startsWith("joinCrew:") -> actions.joinCrew(action.substringAfter(':'))
            action.startsWith("joinParty:") -> { selectedPartyId = action.substringAfter(':'); actions.joinParty(action.substringAfter(':')) }
        }
    }
    fun connectedAction(action: String) {
        if (state.signedIn) perform(action) else { pending = action; connecting = true }
    }
    LaunchedEffect(Unit) { actions.refreshCommunity() }
    LaunchedEffect(state.signedIn) {
        if (state.signedIn && pending != null) { val action = pending!!; pending = null; connecting = false; perform(action) }
    }
    LaunchedEffect(state.lastCreatedCrewId) { if (state.lastCreatedCrewId.isNotBlank() && seenCrew != state.lastCreatedCrewId) { seenCrew = state.lastCreatedCrewId; selectedCrewId = seenCrew; newCrew = false } }
    LaunchedEffect(state.lastCreatedPartyId) { if (state.lastCreatedPartyId.isNotBlank() && seenParty != state.lastCreatedPartyId) { seenParty = state.lastCreatedPartyId; selectedPartyId = seenParty; newParty = false } }
    val selected = state.crews.firstOrNull { it.id == selectedCrewId }
    ScreenContent {
        if (selected != null) {
            TextButton(onClick = { selectedCrewId = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("back_to_rooms")) {
                Icon(Icons.Outlined.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text("방 목록")
            }
            Text(selected.name, style = MaterialTheme.typography.headlineMedium)
            Text("${selected.region.ifBlank { "지역 자유" }} · ${selected.paceLabel} · ${selected.memberCount}명", color = RunmoniColors.Muted)
            if (selected.description.isNotBlank()) Text(selected.description)
            if (!state.serverConnected) RoomConnectionNotice(state, actions)
            if (!selected.joined) {
                Button(onClick = { connectedAction("joinCrew:${selected.id}") }, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("join_crew_${selected.id}")) { Text("크루 방 입장") }
                SmallNote("참여하면 멤버들과 공지·채팅·러닝 일정을 함께 볼 수 있어요.")
            } else {
                StatusPill(if (selected.isOwner) "내가 만든 방" else "참여 중", RunmoniColors.Green)
                AdaptiveActions(
                    first = { m -> Button(onClick = { actions.openChat("crew:${selected.id}", selected.name) }, modifier = m.heightIn(min = 48.dp)) { Text("공지 · 채팅") } },
                    second = { m -> OutlinedButton(onClick = { partyCrewId = selected.id; newParty = true }, enabled = selected.isOpen || selected.isOwner,
                        modifier = m.heightIn(min = 48.dp).testTag("create_party_${selected.id}")) { Text("파티 만들기") } },
                )
                AudioPanel(state.audio, actions, "crew:${selected.id}")
                if (selected.isOwner) {
                    OutlinedButton(onClick = { manageId = selected.id }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("manage_crew_members")) { Text("멤버 · DJ 관리") }
                    SmallNote("방장은 공지와 멤버 역할을 관리해요. 현재 방장은 방을 나갈 수 없어요.")
                } else TextButton(onClick = { actions.leaveCrew(selected.id); selectedCrewId = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("leave_crew_${selected.id}")) { Text("이 크루 나가기") }
                val events = state.parties.filter { it.crewId == selected.id && !it.cancelled }
                SectionHeading("이 크루의 파티")
                if (events.isEmpty()) SmallNote("아직 파티가 없어요. 함께 달릴 일정을 만들어보세요.")
                events.forEach { p -> PartySummary(p) { selectedPartyId = p.id } }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("함께 뛸 방을 찾아요", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = actions::refreshCommunity, enabled = !state.busy, modifier = Modifier.size(48.dp).testTag("rooms_refresh")) { Icon(Icons.Outlined.Refresh, "방 목록 새로고침") }
            }
            SmallNote("크루는 꾸준히 만나는 모임, 파티는 한 번의 러닝이에요. 목록에서 방을 골라 참여하세요.")
            AdaptiveActions(
                first = { m -> Button(onClick = { connectedAction("newCrew") }, enabled = !state.busy, modifier = m.heightIn(min = 48.dp).testTag("create_crew")) { Text("크루 방 만들기") } },
                second = { m -> OutlinedButton(onClick = { connectedAction("newParty") }, enabled = !state.busy, modifier = m.heightIn(min = 48.dp).testTag("create_party")) { Text("파티 방 만들기") } },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("크루 방", "파티 방").forEachIndexed { i, label -> FilterChip(selected = type == i, onClick = { type = i }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp).testTag("rooms_type_$i")) }
            }
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("방 검색") }, placeholder = { Text("이름 · 지역 · 페이스") },
                singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth().testTag("rooms_search"), shape = RoundedCornerShape(16.dp))
            FilterChip(selected = mine, onClick = { mine = !mine }, label = { Text("내가 참여한 방") }, modifier = Modifier.heightIn(min = 48.dp).testTag("rooms_mine"))
            if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); SmallNote("방 정보를 불러오고 있어요") }
            if (!state.serverConnected && !state.busy) RoomConnectionNotice(state, actions)
            if (!state.signedIn) SmallNote("목록은 바로 둘러볼 수 있어요. 만들거나 입장할 때 닉네임으로 시작해요.")
            if (type == 0) {
                val rooms = state.crews.filter { (!mine || it.joined) && "${it.name} ${it.description} ${it.region} ${it.paceLabel}".contains(query.trim(), true) }
                if (rooms.isEmpty() && state.serverConnected && !state.busy) EmptyCard(if (query.isNotBlank()) "검색한 크루 방이 없어요" else if (mine) "참여한 크루 방이 없어요" else "첫 크루 방을 만들어보세요", "이름이나 지역을 바꾸어 찾거나 새 방을 만들 수 있어요")
                rooms.forEach { crew ->
                    Surface(shape = RoundedCornerShape(22.dp), color = RunmoniColors.Paper, border = BorderStroke(1.dp, RunmoniColors.Line),
                        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "크루 방 보기") { selectedCrewId = crew.id }.testTag("crew_${crew.id}")) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(crew.name, style = MaterialTheme.typography.titleLarge)
                            Text("${crew.region.ifBlank { "지역 자유" }} · ${crew.paceLabel}", color = RunmoniColors.Muted)
                            Text("${crew.memberCount}명 · ${if (crew.isOwner) "내가 만든 방" else if (crew.joined) "참여 중" else "입장 가능"}", style = MaterialTheme.typography.labelMedium)
                            Text("방 보기", color = RunmoniColors.Primary)
                        }
                    }
                }
            } else {
                val now = System.currentTimeMillis()
                val rooms = state.parties.filter { (if (mine) it.joined else !it.cancelled && it.startsAtMillis >= now) && "${it.title} ${it.meetingLabel} ${it.paceLabel}".contains(query.trim(), true) }.sortedBy { it.startsAtMillis }
                if (rooms.isEmpty() && state.serverConnected && !state.busy) EmptyCard(if (query.isNotBlank()) "검색한 파티 방이 없어요" else if (mine) "참여한 파티 방이 없어요" else "함께 달릴 파티 방을 만들어보세요", "크루에 가입하지 않아도 파티를 만들고 참여할 수 있어요")
                rooms.forEach { p -> PartySummary(p) { selectedPartyId = p.id } }
            }
        }
    }
    if (connecting) SignInDialog(state, actions) { connecting = false; if (!state.signedIn) pending = null }
    if (newCrew) NewCrewDialog(actions, state) { newCrew = false }
    if (newParty) NewPartyDialog(partyCrewId, actions, state) { newParty = false }
    selectedPartyId?.let { id -> state.parties.firstOrNull { it.id == id }?.let { p ->
        PartyDialog(state, p, actions, onSignIn = { pending = "joinParty:$id"; connecting = true }) { selectedPartyId = null }
    } }
    manageId?.let { id -> CrewMembersDialog(state, id, actions) { manageId = null } }
}

@Composable
private fun RoomConnectionNotice(state: AppState, actions: AppActions) {
    EmptyCard("방 목록에 연결하지 못했어요", state.serverStatus, "다시 불러오기", actions::refreshCommunity)
    if (state.crews.isNotEmpty() || state.parties.isNotEmpty()) SmallNote("아래는 마지막으로 받은 방 정보예요. 참여 전 연결을 확인해 주세요.")
}

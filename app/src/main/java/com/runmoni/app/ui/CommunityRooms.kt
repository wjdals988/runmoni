@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.runmoni.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    BackHandler(enabled = selectedCrewId != null) { selectedCrewId = null }
    ScreenContent(spacing = 16.dp) {
        if (selected != null) {
            TextButton(onClick = { selectedCrewId = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("back_to_rooms")) {
                Icon(Icons.Outlined.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text("방 목록")
            }
            EditorialHeading("RUN TOGETHER", selected.name)
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
                if (state.communityBackend == CommunityBackend.HTTP) AudioPanel(state.audio, actions, "crew:${selected.id}")
                else SmallNote("채팅으로 함께 만나요. 음성 통화는 준비 중이에요.")
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
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("RUN TOGETHER", style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Primary)
                    Text("함께라서, 더 멀리.", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = actions::refreshCommunity, enabled = !state.busy, modifier = Modifier.size(48.dp).testTag("rooms_refresh")) { Icon(Icons.Outlined.Refresh, "방 목록 새로고침") }
            }
            DestinationTabs(listOf("크루 방", "파티 방"), type, { type = it }, "rooms_type")
            RoomSearch(query, { query = it }, "방 검색", "rooms_search")
            AdaptiveActions(
                first = { m -> Button(onClick = { connectedAction(if (type == 0) "newCrew" else "newParty") }, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = m.heightIn(min = 48.dp).testTag(if (type == 0) "create_crew" else "create_party")) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (type == 0) "크루 방 만들기" else "파티 방 만들기") } },
                second = { m -> FilterChip(selected = mine, onClick = { mine = !mine }, label = { Text("내가 참여한 방") }, modifier = m.heightIn(min = 48.dp).testTag("rooms_mine")) },
            )
            if (state.busy) SmallNote("방 정보를 불러오고 있어요")
            if (!state.serverConnected && !state.busy) RoomConnectionNotice(state, actions)
            if (type == 0) {
                val rooms = state.crews.filter { (!mine || it.joined) && "${it.name} ${it.description} ${it.region} ${it.paceLabel}".contains(query.trim(), true) }
                if (rooms.isEmpty() && state.serverConnected && !state.busy) EmptyCard(if (query.isNotBlank()) "검색한 크루 방이 없어요" else if (mine) "참여한 크루 방이 없어요" else "첫 크루 방을 만들어보세요", "이름이나 지역을 바꾸어 찾거나 새 방을 만들 수 있어요")
                rooms.forEach { crew ->
                    CrewRoomCard(crew) { selectedCrewId = crew.id }
                }
            } else {
                val now = System.currentTimeMillis()
                val rooms = state.parties.filter { (if (mine) it.joined else !it.cancelled && it.startsAtMillis >= now) && "${it.title} ${it.meetingLabel} ${it.paceLabel}".contains(query.trim(), true) }.sortedBy { it.startsAtMillis }
                if (rooms.isEmpty() && state.serverConnected && !state.busy) EmptyCard(if (query.isNotBlank()) "검색한 파티 방이 없어요" else if (mine) "참여한 파티 방이 없어요" else "함께 달릴 파티 방을 만들어보세요", "크루에 가입하지 않아도 파티를 만들고 참여할 수 있어요")
                rooms.forEach { p -> PartySummary(p) { selectedPartyId = p.id } }
            }
            if (!state.signedIn) SmallNote("목록은 바로 둘러볼 수 있어요. 만들거나 입장할 때 닉네임으로 시작해요.")
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
    CommunityUnavailable(actions)
    if (state.crews.isNotEmpty() || state.parties.isNotEmpty()) SmallNote("아래는 마지막으로 받은 방 정보예요. 참여 전 연결을 확인해 주세요.")
}

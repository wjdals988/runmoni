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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.runmoni.app.domain.*

@Composable
internal fun CommunityRoomsScreen(state: AppState, actions: AppActions, initialCrewId: String? = null, initialPartyId: String? = null, navigationEpoch: Int = 0) {
    var section by rememberSaveable { mutableIntStateOf(if (state.crews.any { it.joined }) 0 else 1) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCrewId by rememberSaveable { mutableStateOf(initialCrewId) }
    var selectedPartyId by rememberSaveable { mutableStateOf(initialPartyId) }
    var detailSection by rememberSaveable { mutableIntStateOf(0) }
    var manageId by rememberSaveable { mutableStateOf<String?>(null) }
    var newCrew by rememberSaveable { mutableStateOf(false) }
    var newParty by rememberSaveable { mutableStateOf(false) }
    var connecting by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var seenCrew by rememberSaveable { mutableStateOf(state.lastCreatedCrewId) }
    var seenParty by rememberSaveable { mutableStateOf(state.lastCreatedPartyId) }
    var legacyExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(navigationEpoch, initialCrewId, initialPartyId) { selectedCrewId = initialCrewId; selectedPartyId = initialPartyId; detailSection = 0 }
    fun perform(action: String) { when {
        action == "create" -> newCrew = true
        action.startsWith("join:") -> actions.joinCrew(action.substringAfter(':'))
        action.startsWith("party:") -> actions.joinParty(action.substringAfter(':'))
    } }
    fun connected(action: String) { if (state.signedIn) perform(action) else { pending = action; connecting = true } }
    LaunchedEffect(Unit) { actions.refreshCommunity() }
    LaunchedEffect(state.signedIn) { if (state.signedIn && pending != null) { val action = pending!!; pending = null; connecting = false; perform(action) } }
    LaunchedEffect(state.lastCreatedCrewId) { if (state.lastCreatedCrewId.isNotBlank() && seenCrew != state.lastCreatedCrewId) { seenCrew = state.lastCreatedCrewId; selectedCrewId = seenCrew; detailSection = 0; newCrew = false } }
    LaunchedEffect(state.lastCreatedPartyId) { if (state.lastCreatedPartyId.isNotBlank() && seenParty != state.lastCreatedPartyId) { seenParty = state.lastCreatedPartyId; selectedPartyId = seenParty; newParty = false } }
    LaunchedEffect(state.parties) { if (selectedPartyId != null && state.parties.none { it.id == selectedPartyId }) selectedPartyId = null }
    val selected = state.crews.firstOrNull { it.id == selectedCrewId }
    LaunchedEffect(selected?.id) { selected?.let { actions.loadCrewParties(it.id) } }
    BackHandler(enabled = selectedCrewId != null && selectedPartyId == null) { selectedCrewId = null }
    Column(Modifier.fillMaxSize()) {
        if (selected != null) {
            TextButton(onClick = { selectedCrewId = null }, contentPadding = PaddingValues(0.dp),
                modifier = Modifier.padding(horizontal = 24.dp).heightIn(min = 48.dp).testTag("back_to_rooms")) {
                Icon(Icons.Outlined.ArrowBack, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("크루")
            }
            HorizontalDivider(color = RunmoniColors.Line)
        }
        Box(Modifier.weight(1f)) {
    ScreenContent(spacing = 20.dp) {
        if (selected != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selected.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text("${selected.region.ifBlank { "지역 자유" }} · ${selected.paceLabel} · ${selected.memberCount}명", color = RunmoniColors.Muted, style = MaterialTheme.typography.bodyMedium)
                if (selected.joined) Text(if (selected.isOwner) "크루장으로 활동 중" else "가입한 크루", style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Primary)
            }
            if (!state.serverConnected && !state.busy) RoomConnectionNotice(state, actions)
            if (!selected.joined) Button(onClick = { connected("join:${selected.id}") }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("join_crew_${selected.id}")) { Text("크루 가입하기") }
            DestinationTabs(listOf("파티", "소개 · 멤버"), detailSection, { detailSection = it }, "crew_detail")
            if (detailSection == 0) {
                if (selected.joined) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = { newParty = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("create_party_${selected.id}")) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("파티 만들기") }
                }
                val events = state.parties.filter { it.crewId == selected.id && !it.cancelled }.sortedBy { it.startsAtMillis }
                if (state.crewPartiesError != null) {
                    Text(state.crewPartiesError, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { actions.loadCrewParties(selected.id) }) { Text("파티 다시 불러오기") }
                }
                if (events.size >= 50) SmallNote("크루별 기본 조회는50개까지예요. 참여한 파티도 함께 표시해요.")
                if (events.isEmpty() && (state.busy || state.crewPartiesLoading)) LinearProgressIndicator(Modifier.fillMaxWidth())
                else if (events.isEmpty() && state.serverConnected && state.crewPartiesError == null) EmptyCard("아직 파티가 없어요", if (selected.joined) "이 크루에서 함께 달릴 일정을 만들어보세요." else "크루에 가입하면 함께 달릴 파티를 만들고 참가할 수 있어요.")
                Column { events.forEach { p -> PartySummary(p) { selectedPartyId = p.id } } }
            } else {
                if (selected.description.isNotBlank()) Text(selected.description, style = MaterialTheme.typography.bodyLarge)
                if (selected.joined) {
                    OutlinedButton(onClick = { actions.openChat("crew:${selected.id}", selected.name) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("크루 공지 · 채팅") }
                    if (state.communityBackend == CommunityBackend.HTTP) AudioPanel(state.audio, actions, "crew:${selected.id}")
                    if (selected.isOwner) OutlinedButton(onClick = { manageId = selected.id }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("manage_crew_members")) { Text("멤버 · DJ 관리") }
                    else TextButton(onClick = { actions.leaveCrew(selected.id); selectedCrewId = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("leave_crew_${selected.id}")) { Text("크루 탈퇴", color = MaterialTheme.colorScheme.error) }
                    if (selected.isOwner) SmallNote("현재 크루장 권한은 다른 계정으로 이전할 수 없어요.")
                } else SmallNote("크루에 가입하면 공지와 채팅에 참여할 수 있어요.")
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("크루", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = actions::refreshCommunity, enabled = !state.busy, modifier = Modifier.size(48.dp).testTag("rooms_refresh")) { Icon(Icons.Outlined.Refresh, "크루 새로고침") }
            }
            DestinationTabs(listOf("내 크루", "둘러보기"), section, { section = it }, "crews_section")
            RoomSearch(query, { query = it }, "크루 검색", "rooms_search")
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { connected("create") }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("create_crew")) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("크루 만들기") }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!state.serverConnected && !state.busy) RoomConnectionNotice(state, actions)
            val crews = state.crews.filter { (section == 1 || it.joined) && "${it.name} ${it.description} ${it.region} ${it.paceLabel}".contains(query.trim(), true) }
            if (crews.isEmpty() && state.serverConnected && !state.busy) EmptyCard(
                if (query.isNotBlank()) "검색한 크루가 없어요" else if (section == 0) "가입한 크루가 없어요" else "등록된 크루가 없어요",
                if (query.isNotBlank()) "다른 이름·지역·페이스로 검색해 보세요." else if (section == 0) "둘러보기에서 크루에 가입할 수 있어요." else "크루 만들기로 새 크루를 시작할 수 있어요.")
            Column { crews.forEach { crew -> CrewRoomCard(crew) { selectedCrewId = crew.id; detailSection = 0 } } }
            val legacy = state.parties.filter { it.joined && state.crews.none { c -> c.id == it.crewId } }
            if (legacy.isNotEmpty()) {
                TextButton(onClick = { legacyExpanded = !legacyExpanded }, modifier = Modifier.heightIn(min = 48.dp)) { Text("이전 버전 파티 관리") }
                if (legacyExpanded) Column { legacy.forEach { p -> PartySummary(p) { selectedPartyId = p.id } } }
            }
        }
    }
        }
    }
    if (connecting) SignInDialog(state, actions) { connecting = false; if (!state.signedIn) pending = null }
    if (newCrew) NewCrewDialog(actions, state) { newCrew = false }
    if (newParty && selected?.joined == true) NewPartyDialog(selected.id, actions, state) { newParty = false }
    selectedPartyId?.let { id -> state.parties.firstOrNull { it.id == id }?.let { p ->
        PartyDialog(state, p, actions, onSignIn = { pending = "party:$id"; connecting = true }) { selectedPartyId = null }
    } }
    manageId?.let { id -> CrewMembersDialog(state, id, actions) { manageId = null } }
}

@Composable
private fun RoomConnectionNotice(state: AppState, actions: AppActions) {
    CommunityUnavailable(actions)
    if (state.crews.isNotEmpty()) SmallNote("마지막으로 받은 크루 정보예요. 참가 전 연결을 확인해 주세요.")
}

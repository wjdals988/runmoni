@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.runmoni.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.runmoni.app.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun CommunityRoomsScreen(state: AppState, actions: AppActions, initialCrewId: String? = null,
    initialPartyId: String? = null, navigationEpoch: Int = 0, onSubpageChanged: (Boolean) -> Unit = {}) {
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
    var observedPartyId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialRefreshRequested by rememberSaveable { mutableStateOf(false) }
    var requestedCrewId by rememberSaveable { mutableStateOf<String?>(null) }
    var legacyExpanded by rememberSaveable { mutableStateOf(false) }
    val pageStates = rememberSaveableStateHolder()
    var handledEpoch by rememberSaveable { mutableIntStateOf(navigationEpoch) }
    var handledCrew by rememberSaveable { mutableStateOf(initialCrewId) }
    var handledParty by rememberSaveable { mutableStateOf(initialPartyId) }
    val navigationChanged = handledEpoch != navigationEpoch || handledCrew != initialCrewId || handledParty != initialPartyId
    fun closeNewCrew() { newCrew = false }
    fun closeNewParty() { newParty = false }
    fun closeConnection() {
        connecting = false
        pageStates.removeState("community_profile")
        if (!state.signedIn) pending = null
    }
    fun closeMembers() {
        manageId?.let { pageStates.removeState("members_$it") }
        manageId = null
    }
    LaunchedEffect(navigationEpoch, initialCrewId, initialPartyId) {
        // Re-entering after chat/profile restores the saved page. Only an
        // explicit, unhandled navigation request replaces that destination.
        if (navigationChanged) {
            pageStates.removeState("new_crew")
            selectedCrewId?.let { pageStates.removeState("new_party_$it") }
            closeNewCrew(); closeNewParty(); closeConnection(); closeMembers()
            pending = null
            observedPartyId = null
            requestedCrewId = null
            selectedCrewId = initialCrewId; selectedPartyId = initialPartyId; detailSection = 0
            seenCrew = state.lastCreatedCrewId; seenParty = state.lastCreatedPartyId
            handledEpoch = navigationEpoch; handledCrew = initialCrewId; handledParty = initialPartyId
        }
    }
    fun perform(action: String) { when {
        action == "create" -> newCrew = true
        action.startsWith("join:") -> actions.joinCrew(action.substringAfter(':'))
        action.startsWith("party:") -> actions.joinParty(action.substringAfter(':'))
    } }
    fun connected(action: String) { if (state.signedIn) perform(action) else { pending = action; connecting = true } }
    LaunchedEffect(Unit) {
        if (!initialRefreshRequested) { initialRefreshRequested = true; actions.refreshCommunity() }
    }
    LaunchedEffect(state.signedIn) {
        if (!navigationChanged && state.signedIn && pending != null) {
            val action = pending!!
            pending = null; closeConnection(); perform(action)
        }
    }
    LaunchedEffect(state.lastCreatedCrewId) {
        if (!navigationChanged && state.lastCreatedCrewId.isNotBlank() && seenCrew != state.lastCreatedCrewId) {
            seenCrew = state.lastCreatedCrewId; selectedCrewId = seenCrew; detailSection = 0
            closeNewCrew(); pageStates.removeState("new_crew")
        }
    }
    LaunchedEffect(state.lastCreatedPartyId) {
        if (!navigationChanged && state.lastCreatedPartyId.isNotBlank() && seenParty != state.lastCreatedPartyId) {
            seenParty = state.lastCreatedPartyId; selectedPartyId = seenParty
            closeNewParty(); selectedCrewId?.let { pageStates.removeState("new_party_$it") }
        }
    }
    LaunchedEffect(selectedPartyId, state.parties) {
        if (selectedPartyId == null) observedPartyId = null
        else if (state.parties.any { it.id == selectedPartyId }) observedPartyId = selectedPartyId
        else if (observedPartyId == selectedPartyId) { selectedPartyId = null; observedPartyId = null }
    }
    val selected = state.crews.firstOrNull { it.id == selectedCrewId }
    val selectedParty = state.parties.firstOrNull { it.id == selectedPartyId }
    val managedCrew = state.crews.firstOrNull { it.id == manageId && it.joined }
    LaunchedEffect(selected?.id, navigationChanged) {
        if (!navigationChanged) {
            if (selected == null) requestedCrewId = null
            else if (requestedCrewId != selected.id) { requestedCrewId = selected.id; actions.loadCrewParties(selected.id) }
        }
    }
    val childPage = connecting || newCrew || newParty && selected?.joined == true || managedCrew != null || selectedParty != null
    val subpage = if (navigationChanged) initialCrewId != null || initialPartyId != null else childPage || selected != null
    SideEffect { onSubpageChanged(subpage) }
    BackHandler(enabled = !navigationChanged && selected != null && !childPage) { selectedCrewId = null }
    if (navigationChanged) { Box(Modifier.fillMaxSize()); return }

    // Every long task is one page. The parent's selection and each page's
    // scroll/draft remain saved while another leaf owns the window.
    if (connecting) {
        pageStates.SaveableStateProvider("community_profile") { SignInDialog(state, actions, ::closeConnection) }
        return
    }
    if (newCrew) {
        pageStates.SaveableStateProvider("new_crew") { NewCrewDialog(actions, state, ::closeNewCrew) }
        return
    }
    if (newParty && selected?.joined == true) {
        pageStates.SaveableStateProvider("new_party_${selected.id}") { NewPartyDialog(selected.id, actions, state, ::closeNewParty) }
        return
    }
    if (managedCrew != null) {
        pageStates.SaveableStateProvider("members_${managedCrew.id}") { CrewMembersDialog(state, managedCrew.id, actions, ::closeMembers) }
        return
    }
    if (selectedParty != null) {
        pageStates.SaveableStateProvider("party_${selectedParty.id}") {
            PartyDialog(state, selectedParty, actions, onSignIn = { pending = "party:${selectedParty.id}"; connecting = true }) { selectedPartyId = null }
        }
        return
    }
    val pageKey = selected?.let { "crew_${it.id}_$detailSection" } ?: "crew_directory"
    pageStates.SaveableStateProvider(pageKey) {
        // The directory receives Scaffold insets. A crew detail hides that chrome
        // and becomes this page's sole safe-area owner.
        Column(if (selected == null) Modifier.fillMaxSize() else Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
            if (selected != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Row(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
                        .heightIn(min = 64.dp).padding(horizontal = pageGutter()), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(shape = RunmoniShapes.Action, onClick = { selectedCrewId = null }, contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("back_to_rooms")) {
                            Icon(Icons.Outlined.ArrowBack, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("크루")
                        }
                    }
                }
                HorizontalDivider(color = RunmoniColors.Line)
            }
            Box(Modifier.weight(1f)) {
                ScreenContent {
                    if (selected != null) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(selected.name, style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.semantics { heading() })
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("멤버 ${selected.memberCount}명", style = MaterialTheme.typography.bodyMedium,
                                    color = RunmoniColors.Muted)
                                if (selected.joined) Text(if (selected.isOwner) "크루장으로 활동 중" else "가입한 크루",
                                    style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Primary)
                            }
                        }
                        if (!state.serverConnected && !state.busy) RoomConnectionNotice(state, actions)
                        if (!selected.joined) Button(shape = RunmoniShapes.Action, onClick = { connected("join:${selected.id}") }, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("join_crew_${selected.id}")) { Text("크루 가입하기") }
                        DestinationTabs(listOf("파티 일정", "소개 · 멤버"), detailSection, { detailSection = it }, "crew_detail")
                        if (detailSection == 0) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (selected.joined) Button(shape = RunmoniShapes.Action, onClick = { newParty = true }, enabled = !state.busy,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("create_party_${selected.id}")) {
                                    Icon(Icons.Outlined.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("파티 만들기")
                                }
                                val events = state.parties.filter { it.crewId == selected.id && !it.cancelled }.sortedBy { it.startsAtMillis }
                                if (state.crewPartiesError != null) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RunmoniShapes.Surface) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(state.crewPartiesError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                        TextButton(shape = RunmoniShapes.Action, onClick = { actions.loadCrewParties(selected.id) },
                                            modifier = Modifier.heightIn(min = 48.dp)) { Text("파티 다시 불러오기") }
                                    }
                                }
                                if (state.busy || state.crewPartiesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                if (events.isEmpty() && !state.busy && !state.crewPartiesLoading && state.serverConnected && state.crewPartiesError == null) {
                                    EmptyCard("아직 파티가 없어요", if (selected.joined) "이 크루에서 함께 달릴 일정을 만들어보세요." else "크루에 가입하면 함께 달릴 파티를 만들고 참가할 수 있어요.")
                                }
                                if (events.size >= 50) SmallNote("기본 목록 50개와 참가한 파티를 함께 보여요.")
                                // Dates group the crew's schedule; parties remain children of this crew.
                                val days = events.groupBy { Instant.ofEpochMilli(it.startsAtMillis).atZone(ZoneId.systemDefault()).toLocalDate() }
                                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                    days.forEach { (day, parties) -> key(day) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(day.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)),
                                                style = MaterialTheme.typography.titleMedium, color = RunmoniColors.Primary,
                                                modifier = Modifier.semantics { heading() })
                                            Column { parties.forEach { party -> key(party.id) {
                                                CrewScheduleRow(party) { selectedPartyId = party.id }
                                            } } }
                                        }
                                    } }
                                }
                            }
                        } else {
                            AdaptiveActions(
                                first = { modifier -> CommunityFact("활동 지역", selected.region.ifBlank { "지역 자유" }, modifier) },
                                second = { modifier -> CommunityFact("러닝 페이스", selected.paceLabel.ifBlank { "자유 페이스" }, modifier) },
                            )
                            if (selected.description.isNotBlank()) ScreenSection("크루 소개", showDivider = false) {
                                Text(selected.description, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (selected.joined) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(shape = RunmoniShapes.Action, onClick = { actions.openChat("crew:${selected.id}", selected.name) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                    Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp)); Text("크루 공지 · 채팅")
                                }
                                if (state.communityBackend == CommunityBackend.HTTP) AudioPanel(state.audio, actions, "crew:${selected.id}")
                                HorizontalDivider(color = RunmoniColors.Line)
                                if (selected.isOwner) {
                                    OutlinedButton(shape = RunmoniShapes.Action, onClick = { manageId = selected.id },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("manage_crew_members")) { Text("멤버 · DJ 관리") }
                                    SmallNote("현재 크루장 권한은 다른 계정으로 이전할 수 없어요.")
                                } else TextButton(shape = RunmoniShapes.Action, onClick = { actions.leaveCrew(selected.id); selectedCrewId = null },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("leave_crew_${selected.id}")) { Text("크루 탈퇴", color = MaterialTheme.colorScheme.error) }
                            } else SmallNote("크루에 가입하면 공지와 채팅에 참여할 수 있어요.")
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("크루", style = MaterialTheme.typography.headlineLarge,
                                    modifier = Modifier.weight(1f).semantics { heading() })
                                IconButton(onClick = actions::refreshCommunity, enabled = !state.busy,
                                    modifier = Modifier.size(48.dp).testTag("rooms_refresh")) { Icon(Icons.Outlined.Refresh, "크루 새로고침") }
                            }
                            DestinationTabs(listOf("내 크루", "둘러보기"), section, { section = it }, "crews_section")
                        }
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoomSearch(query, { query = it }, "크루 검색", "rooms_search")
                            TextButton(shape = RunmoniShapes.Action, onClick = { connected("create") }, enabled = !state.busy,
                                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp).testTag("create_crew")) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("크루 만들기")
                            }
                        }
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (!state.serverConnected && !state.busy) RoomConnectionNotice(state, actions)
                        val crews = state.crews.filter { (section == 1 || it.joined) && "${it.name} ${it.description} ${it.region} ${it.paceLabel}".contains(query.trim(), true) }
                        if (crews.isEmpty() && state.serverConnected && !state.busy) EmptyCard(
                            if (query.isNotBlank()) "검색한 크루가 없어요" else if (section == 0) "가입한 크루가 없어요" else "등록된 크루가 없어요",
                            if (query.isNotBlank()) "다른 이름·지역·페이스로 검색해 보세요." else if (section == 0) "둘러보기에서 크루에 가입할 수 있어요." else "크루 만들기로 새 크루를 시작할 수 있어요.")
                        if (crews.isNotEmpty()) Column {
                            crews.forEach { crew -> key(crew.id) {
                                CrewDirectoryRow(crew) { selectedCrewId = crew.id; detailSection = 0 }
                            } }
                        }
                        val legacy = state.parties.filter { it.joined && state.crews.none { crew -> crew.id == it.crewId } }
                        if (legacy.isNotEmpty()) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = RunmoniColors.Line)
                            TextButton(shape = RunmoniShapes.Action, onClick = { legacyExpanded = !legacyExpanded },
                                modifier = Modifier.heightIn(min = 48.dp).semantics { stateDescription = if (legacyExpanded) "펼침" else "접힘" }) {
                                Text("이전 버전 파티 관리"); Spacer(Modifier.width(8.dp))
                                Icon(if (legacyExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
                            }
                            if (legacyExpanded) Column { legacy.forEach { party -> key(party.id) { PartySummary(party) { selectedPartyId = party.id } } } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomConnectionNotice(state: AppState, actions: AppActions) {
    CommunityUnavailable(actions)
    if (state.crews.isNotEmpty()) SmallNote("마지막으로 받은 크루 정보예요. 참가 전 연결을 확인해 주세요.")
}

@Composable
private fun CrewDirectoryRow(crew: Crew, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClickLabel = "크루 보기", onClick = onClick).testTag("crew_${crew.id}")) {
        Row(Modifier.padding(vertical = 20.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(crew.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null, tint = RunmoniColors.Primary, modifier = Modifier.size(24.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(crew.region.ifBlank { "지역 자유" }, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                    Text(crew.paceLabel.ifBlank { "자유 페이스" }, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("멤버 ${crew.memberCount}명", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                    if (crew.joined || crew.isOwner) Text(if (crew.isOwner) "크루장" else "가입한 크루",
                        style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Primary)
                }
            }
        }
        HorizontalDivider(color = RunmoniColors.Line)
    }
}

@Composable
private fun CrewScheduleRow(party: Party, onClick: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    @Composable fun Details(modifier: Modifier = Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(party.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = RunmoniColors.Muted, modifier = Modifier.size(24.dp))
            }
            Text(if (party.kind == PartyKind.LOCAL) party.meetingLabel.ifBlank { "모이는 장소 미정" } else "각자 출발",
                style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${party.distanceKm} km", style = MaterialTheme.typography.bodyMedium)
                Text(party.paceLabel, style = MaterialTheme.typography.bodyMedium)
                Text("${party.participantCount}/${party.capacity}명", style = MaterialTheme.typography.bodyMedium)
                if (party.joined) Text("참가 중", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Primary)
            }
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClickLabel = "파티 참가 정보 보기", onClick = onClick).testTag("party_${party.id}")) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            if (maxWidth < 300.dp || fontScale >= 1.3f) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(clock(party.startsAtMillis), style = MaterialTheme.typography.titleMedium, color = RunmoniColors.Primary)
                Details()
            } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Text(clock(party.startsAtMillis), style = MaterialTheme.typography.titleMedium, color = RunmoniColors.Primary,
                    modifier = Modifier.width(64.dp))
                Details(Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = RunmoniColors.Line)
    }
}

@Composable
private fun CommunityFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

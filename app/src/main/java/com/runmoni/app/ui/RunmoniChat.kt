@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.runmoni.app.ui

import androidx.activity.compose.BackHandler
import android.animation.ValueAnimator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import kotlinx.coroutines.launch

/** One Activity window owns the system bars, IME and full-screen chat. */
@Composable
internal fun ChatScreen(state: AppState, actions: AppActions) {
    var input by remember(state.openedRoomId) { mutableStateOf("") }
    var notice by remember(state.openedRoomId) { mutableStateOf(false) }
    var noticesOnly by remember(state.openedRoomId) { mutableStateOf(false) }
    var pending by remember(state.openedRoomId) { mutableStateOf<String?>(null) }
    val crewId = if (state.openedRoomId.startsWith("crew:")) state.openedRoomId.removePrefix("crew:") else state.parties.firstOrNull { "party:${it.id}" == state.openedRoomId }?.crewId
    val canNotice = if (state.communityBackend == CommunityBackend.FIREBASE) state.chatCanNotice else state.crews.any { it.id == crewId && (it.isOwner || it.isDj) } || state.parties.any { "party:${it.id}" == state.openedRoomId && it.isOrganizer }
    val messages = state.messages.filter { it.roomId == state.openedRoomId && (!noticesOnly || it.notice) }.sortedBy { it.createdAtMillis }
    val scroll = key(state.openedRoomId, noticesOnly) { rememberScrollState() }
    var followLatest by remember(state.openedRoomId, noticesOnly) { mutableStateOf(true) }
    var hasNewMessages by remember(state.openedRoomId, noticesOnly) { mutableStateOf(false) }
    val bottomThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.value }.collect { position ->
            followLatest = scroll.maxValue - position <= bottomThreshold
            if (followLatest) hasNewMessages = false
        }
    }
    LaunchedEffect(scroll, messages.lastOrNull()?.id) {
        if (followLatest) {
            withFrameNanos { }
            scroll.scrollTo(scroll.maxValue)
        } else hasNewMessages = true
    }
    LaunchedEffect(state.error) { if (state.error != null && pending != null) { if (input.isEmpty()) input = pending!!; pending = null } }
    var menuExpanded by remember(state.openedRoomId) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val close: () -> Unit = { keyboard?.hide(); focusManager.clearFocus(force = true); actions.closeChat() }
    val imeVisible = WindowInsets.isImeVisible
    BackHandler(enabled = !imeVisible, onBack = close)
    LaunchedEffect(canNotice) { if (!canNotice) notice = false }
    BoxWithConstraints(Modifier.fillMaxSize().background(RunmoniColors.Cream)
        .pointerInput(state.openedRoomId) { detectTapGestures { focusManager.clearFocus() } }
        .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
        .padding(vertical = 8.dp)) {
        val compact = imeVisible || maxHeight < 480.dp ||
            (LocalDensity.current.fontScale >= 1.3f && maxHeight < 720.dp)
        val composerMaxLines = if (maxHeight < 300.dp) 1 else if (compact) 2 else 4
        LaunchedEffect(maxHeight, compact) { if (followLatest) { withFrameNanos { }; scroll.scrollTo(scroll.maxValue) } }
        Surface(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxSize().align(Alignment.Center).testTag("chat_dialog").semantics {
            testTagsAsResourceId = true; paneTitle = "채팅 · ${state.openedRoomTitle}"
        }, color = RunmoniColors.Cream) {
            Column(Modifier.padding(horizontal = pageGutter()), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 48.dp else 64.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!compact) Text(if (state.openedRoomId.startsWith("crew:")) "크루 대화" else "파티 대화",
                            style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                        Text(if (notice) "공지 작성 · ${state.openedRoomTitle}" else state.openedRoomTitle,
                            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                            maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp).testTag("chat_options")) { Icon(Icons.Outlined.MoreVert, "채팅 보기와 공지 설정") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("전체 채팅 보기") }, onClick = { noticesOnly = false; menuExpanded = false }, trailingIcon = { if (!noticesOnly) Icon(Icons.Outlined.Check, null) }, modifier = Modifier.semantics { stateDescription = if (!noticesOnly) "선택됨" else "선택 안 됨" })
                            DropdownMenuItem(text = { Text("공지만 보기") }, onClick = { noticesOnly = true; menuExpanded = false }, trailingIcon = { if (noticesOnly) Icon(Icons.Outlined.Check, null) }, modifier = Modifier.semantics { stateDescription = if (noticesOnly) "선택됨" else "선택 안 됨" })
                            if (canNotice) DropdownMenuItem(text = { Text("공지로 보내기") }, onClick = { notice = !notice; menuExpanded = false }, trailingIcon = { if (notice) Icon(Icons.Outlined.Check, null) }, modifier = Modifier.semantics { stateDescription = if (notice) "선택됨" else "선택 안 됨" })
                        }
                    }
                    IconButton(onClick = close, modifier = Modifier.size(48.dp).testTag("close_chat")) { Icon(Icons.Outlined.Close, "채팅 닫기") }
                }
                if (state.communityBackend == CommunityBackend.FIREBASE && state.chatFromCache) Text("연결 확인 중 · 저장된 메시지", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.RouteBlue, modifier = Modifier.testTag("chat_connection_status"))
                if (!compact) Column(Modifier.testTag("chat_filters")) {
                    DestinationTabs(listOf("전체 채팅", "공지"), if (noticesOnly) 1 else 0,
                        { noticesOnly = it == 1 }, "chat_scope")
                }
                if (compact) HorizontalDivider(color = RunmoniColors.Line)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("chat_history"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (messages.isEmpty()) {
                            if (state.communityBackend == CommunityBackend.FIREBASE && state.chatFromCache) EmptyCard("메시지 불러오는 중", "")
                            else EmptyCard(if (noticesOnly) "아직 공지가 없어요" else "첫 메시지를 보내보세요", "")
                        }
                        messages.forEach { message ->
                            key(message.id) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.mine && !message.notice) Alignment.End else Alignment.Start,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (message.notice) Icon(Icons.Outlined.Campaign, null, tint = RunmoniColors.RouteBlue,
                                            modifier = Modifier.size(18.dp))
                                        Text(if (message.notice) "공지 · ${message.author}" else message.author,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (message.notice) RunmoniColors.RouteBlue else RunmoniColors.Navy)
                                    }
                                    Surface(shape = RunmoniShapes.Surface,
                                        color = if (message.notice) RunmoniColors.BlueWash else if (message.mine) RunmoniColors.MintWash else RunmoniColors.Paper,
                                        contentColor = RunmoniColors.Navy,
                                        modifier = Modifier.fillMaxWidth(if (compact || message.notice) 1f else 0.86f)) {
                                        Text(message.body, style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.padding(16.dp))
                                    }
                                    Text(clock(message.createdAtMillis), style = MaterialTheme.typography.bodySmall,
                                        color = RunmoniColors.Muted, modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                    // Keep the action available in small windows without taking
                    // height away from the message composer when the IME is open.
                    if (hasNewMessages) TextButton(shape = RunmoniShapes.Action,
                        colors = ButtonDefaults.textButtonColors(containerColor = RunmoniColors.Paper, contentColor = RunmoniColors.Primary),
                        onClick = { followLatest = true; hasNewMessages = false; scope.launch {
                            if (ValueAnimator.areAnimatorsEnabled()) scroll.animateScrollTo(scroll.maxValue) else scroll.scrollTo(scroll.maxValue)
                        } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).heightIn(min = 48.dp)
                            .border(1.dp, RunmoniColors.Outline, RunmoniShapes.Action).testTag("chat_jump_latest")
                            .semantics { contentDescription = "새 메시지 · 맨 아래로" }) {
                        Icon(Icons.Outlined.ArrowDownward, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("새 메시지")
                    }
                }
                if (canNotice && !compact) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(notice, role = Role.Checkbox, onValueChange = { notice = it }), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = notice, onCheckedChange = null); Spacer(Modifier.width(8.dp)); Text("공지로 보내기", style = MaterialTheme.typography.bodyMedium) }
                HorizontalDivider(color = RunmoniColors.Line)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = input, onValueChange = { if (it.length <= if (state.communityBackend == CommunityBackend.FIREBASE) 1000 else 2000) input = it }, label = { Text(if (notice) "공지 작성" else "메시지 작성") }, modifier = Modifier.weight(1f).testTag("chat_input"), textStyle = MaterialTheme.typography.bodyLarge, maxLines = composerMaxLines, shape = RunmoniShapes.Input)
                    FilledIconButton(shape = RunmoniShapes.Action, onClick = { followLatest = true; pending = input.trim(); actions.sendMessage(input.trim(), notice && canNotice); input = "" }, enabled = input.isNotBlank() && !state.busy, modifier = Modifier.size(52.dp).testTag("send_message")) { Icon(Icons.Outlined.Send, "메시지 보내기") }
                }
            }
        }
    }
}

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.runmoni.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val canNotice = state.crews.any { it.id == crewId && it.isOwner } || state.parties.any { "party:${it.id}" == state.openedRoomId && it.isOrganizer }
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
        .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)).padding(12.dp)) {
        val compact = imeVisible || maxHeight < 480.dp
        val composerMaxLines = if (maxHeight < 300.dp) 1 else if (compact) 2 else 4
        LaunchedEffect(maxHeight, compact) { if (followLatest) { withFrameNanos { }; scroll.scrollTo(scroll.maxValue) } }
        Surface(Modifier.widthIn(max = 640.dp).fillMaxSize().align(Alignment.Center).testTag("chat_dialog").semantics {
            testTagsAsResourceId = true; paneTitle = "채팅 · ${state.openedRoomTitle}"
        }, color = RunmoniColors.Cream) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (notice) "공지 작성 · ${state.openedRoomTitle}" else state.openedRoomTitle,
                        style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f), maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp).testTag("chat_options")) { Icon(Icons.Outlined.MoreVert, "채팅 보기와 공지 설정") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("전체 채팅 보기") }, onClick = { noticesOnly = false; menuExpanded = false }, trailingIcon = { if (!noticesOnly) Icon(Icons.Outlined.Check, null) })
                            DropdownMenuItem(text = { Text("공지만 보기") }, onClick = { noticesOnly = true; menuExpanded = false }, trailingIcon = { if (noticesOnly) Icon(Icons.Outlined.Check, null) })
                            if (canNotice) DropdownMenuItem(text = { Text("공지로 보내기") }, onClick = { notice = !notice; menuExpanded = false }, trailingIcon = { if (notice) Icon(Icons.Outlined.Check, null) }, modifier = Modifier.semantics { stateDescription = if (notice) "선택됨" else "선택 안 됨" })
                        }
                    }
                    IconButton(onClick = close, modifier = Modifier.size(48.dp).testTag("close_chat")) { Icon(Icons.Outlined.Close, "채팅 닫기") }
                }
                if (!compact) FlowRow(Modifier.testTag("chat_filters"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !noticesOnly, onClick = { noticesOnly = false }, label = { Text("전체 채팅") }, modifier = Modifier.heightIn(min = 48.dp))
                    FilterChip(selected = noticesOnly, onClick = { noticesOnly = true }, label = { Text("공지") }, modifier = Modifier.heightIn(min = 48.dp))
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).testTag("chat_history"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (messages.isEmpty()) EmptyCard(if (noticesOnly) "아직 공지가 없어요" else "첫 이야기를 남겨보세요", "이 방의 실제 메시지만 표시해요")
                    messages.forEach { message ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.mine && !message.notice) Alignment.End else Alignment.Start) {
                        Surface(shape = RoundedCornerShape(18.dp), color = if (message.notice) RunmoniColors.Mint else if (message.mine) RunmoniColors.Primary else RunmoniColors.Paper,
                            contentColor = if (message.mine && !message.notice) RunmoniColors.OnDark else RunmoniColors.Navy,
                            modifier = Modifier.fillMaxWidth(if (compact || message.notice) 1f else 0.86f)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (message.notice) "공지 · ${message.author}" else message.author, style = MaterialTheme.typography.labelMedium)
                                Text(message.body, style = MaterialTheme.typography.bodyLarge)
                                Text(clock(message.createdAtMillis), style = MaterialTheme.typography.bodySmall, color = if (message.mine && !message.notice) RunmoniColors.OnDarkMuted else RunmoniColors.Muted)
                            }
                        }
                        }
                    }
                }
                if (hasNewMessages && !compact) TextButton(onClick = { followLatest = true; hasNewMessages = false; scope.launch { scroll.animateScrollTo(scroll.maxValue) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("chat_jump_latest")) { Text("새 메시지 · 맨 아래로") }
                if (canNotice && !compact) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(notice, role = Role.Checkbox, onValueChange = { notice = it }), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = notice, onCheckedChange = null); Text("공지로 보내기", style = MaterialTheme.typography.bodyMedium) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = input, onValueChange = { if (it.length <= 2000) input = it }, placeholder = { Text(if (notice) "공지 작성" else "메시지 작성") }, modifier = Modifier.weight(1f).testTag("chat_input"), maxLines = composerMaxLines, shape = RoundedCornerShape(16.dp))
                    FilledIconButton(onClick = { followLatest = true; pending = input.trim(); actions.sendMessage(input.trim(), notice && canNotice); input = "" }, enabled = input.isNotBlank() && !state.busy, modifier = Modifier.size(52.dp).testTag("send_message")) { Icon(Icons.Outlined.Send, "메시지 보내기") }
                }
            }
        }
    }
}

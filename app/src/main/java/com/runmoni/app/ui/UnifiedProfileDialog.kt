package com.runmoni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*

/** One editor for the displayed local/online identity; auth UIDs and wallet ownership are retained. */
@Composable
internal fun UnifiedProfileDialog(state: AppState, actions: AppActions, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(state.profile.name) }
    var adultConfirmed by rememberSaveable { mutableStateOf(false) }
    var saveStartSerial by rememberSaveable { mutableStateOf<Long?>(null) }
    var connectionStartSerial by rememberSaveable { mutableStateOf<Long?>(null) }
    val openedUid = rememberSaveable { state.communityUid }
    val openedBackend = rememberSaveable { state.communityBackend }
    val identityChanged = openedUid != state.communityUid || openedBackend != state.communityBackend
    val online = state.communityBackend == CommunityBackend.FIREBASE && state.signedIn && state.communityUid.isNotBlank()
    val valid = NicknamePolicy.validationError(name) == null
    val result = state.unifiedProfile.check?.takeIf { it.matches(name, state.communityUid) }
    val reserved = result?.availability in listOf(NicknameAvailability.AVAILABLE, NicknameAvailability.OWNED)
    val busy = state.busy || state.unifiedProfile.saving || state.unifiedProfile.checking || state.unifiedProfile.connecting
    LaunchedEffect(state.unifiedProfile.savedSerial, state.unifiedProfile.connectedSerial,
        state.unifiedProfile.connectionUid, state.unifiedProfile.connectedNickname,
        state.communityUid, state.signedIn, state.communityBackend, saveStartSerial, connectionStartSerial) {
        // A name save and an online connection are distinct acknowledgements.
        // Only the requested connection may change the editor's original UID.
        val saved = saveStartSerial?.let { state.unifiedProfile.savedSerial > it && !identityChanged } == true
        val connected = connectionStartSerial?.let {
            state.unifiedProfile.connectedSerial > it && state.signedIn && openedBackend == state.communityBackend &&
                (state.communityBackend != CommunityBackend.FIREBASE || state.communityUid.isNotBlank()) &&
                state.unifiedProfile.connectionUid == state.communityUid &&
                state.unifiedProfile.connectedNickname == NicknamePolicy.normalize(name)
        } == true
        if (saved || connected) onDismiss()
    }
    FormDialog("프로필", onDismiss, closeTag = "close_profile", dismissLabel = null, confirm = {
        Button(onClick = { connectionStartSerial = null; saveStartSerial = state.unifiedProfile.savedSerial; actions.saveUnifiedProfile(name, openedUid, openedBackend) }, shape = RunmoniShapes.Action,
            enabled = valid && !busy && !identityChanged && (!online || reserved), modifier = Modifier.heightIn(min = 48.dp).testTag("save_profile")) {
            Text(if (state.unifiedProfile.saving) "저장 중…" else "저장")
        }
    }) {
        Surface(color = RunmoniColors.MintWash, shape = RunmoniShapes.Surface) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.AccountCircle, null, Modifier.size(32.dp), tint = RunmoniColors.Primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (online) "온라인 프로필 연결됨" else "이 기기에 저장된 프로필",
                        style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                }
            }
        }
        OutlinedTextField(name, { name = it; saveStartSerial = null; connectionStartSerial = null }, label = { Text("닉네임") }, singleLine = true,
            enabled = !state.unifiedProfile.saving && !state.unifiedProfile.connecting && !state.busy, isError = !valid,
            supportingText = { Text(NicknamePolicy.validationError(name) ?: "한글·영문·숫자 등 2~16자") },
            shape = RunmoniShapes.Input,
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = RunmoniColors.Paper, focusedContainerColor = RunmoniColors.Paper),
            modifier = Modifier.fillMaxWidth().testTag("profile_name"))
        state.unifiedProfile.connectedNickname?.takeIf { it != state.profile.name && it != name }?.let { serverName ->
            TextButton(onClick = { name = serverName; saveStartSerial = null; connectionStartSerial = null }, enabled = !busy && !identityChanged, shape = RunmoniShapes.Action, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("연결된 닉네임 ‘$serverName’ 사용")
            }
        }
        if (online) {
            TextButton(onClick = { actions.checkNickname(name) }, enabled = valid && !busy && !identityChanged,
                shape = RunmoniShapes.Action, modifier = Modifier.heightIn(min = 48.dp).testTag("check_nickname")) {
                Text(if (state.unifiedProfile.checking) "확인 중…" else "중복 확인")
            }
            result?.let {
                Text(when (it.availability) {
                    NicknameAvailability.AVAILABLE -> "사용할 수 있는 닉네임이에요."
                    NicknameAvailability.OWNED -> "이 프로필이 사용 중인 닉네임이에요."
                    NicknameAvailability.TAKEN -> "이미 사용 중이에요. 다른 닉네임을 골라 주세요."
                    NicknameAvailability.UNVERIFIED -> "온라인 예약을 확인하지 못했어요."
                }, color = if (it.availability == NicknameAvailability.TAKEN) RunmoniColors.CoralInk else RunmoniColors.Muted,
                    modifier = Modifier.testTag("nickname_result"))
            }
            Text("저장할 때 서버에서 다시 확인해요. 이전 기록과 크루 소속은 유지돼요.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("기기 이름을 저장하거나, 이 닉네임으로 온라인 프로필을 연결할 수 있어요.", style = MaterialTheme.typography.bodyMedium)
            Text("온라인 연결 시 서버에서 중복을 확인해요. 기존 계정과 기록을 자동으로 합치지 않아요.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .toggleable(adultConfirmed, enabled = !busy, role = Role.Checkbox, onValueChange = { adultConfirmed = it })
                .testTag("profile_adult"), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = adultConfirmed, onCheckedChange = null, enabled = !busy)
                Text("성인이며 크루·러너 기능을 이용할게요.", modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = { saveStartSerial = null; connectionStartSerial = state.unifiedProfile.connectedSerial; actions.connectUnifiedProfile(NicknamePolicy.normalize(name), openedUid, openedBackend) },
                enabled = valid && adultConfirmed && !busy && !identityChanged, shape = RunmoniShapes.Action,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("confirm_sign_in")) { Text(if (state.unifiedProfile.connecting) "연결 중…" else "이 닉네임으로 연결") }
        }
        if (identityChanged && !state.unifiedProfile.connecting) Text("연결 상태가 바뀌었어요. 닫고 프로필을 다시 열어 주세요.", color = RunmoniColors.CoralInk)
        state.unifiedProfile.error?.let {
            Surface(color = RunmoniColors.CoralWash, shape = RunmoniShapes.Action) {
                Text(it, color = RunmoniColors.CoralInk, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("profile_error")
                        .semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        HorizontalDivider(color = RunmoniColors.Line)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Palette, null, Modifier.size(20.dp), tint = RunmoniColors.Muted)
            Text("미니미와 옷은 ‘나 → 꾸미기’에서 바꿀 수 있어요.", style = MaterialTheme.typography.bodyMedium,
                color = RunmoniColors.Muted, modifier = Modifier.weight(1f))
        }
    }
}

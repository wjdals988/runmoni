package com.runmoni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*

/** One editor for the displayed local/online identity; auth UIDs and wallet ownership are retained. */
@Composable
internal fun UnifiedProfileDialog(state: AppState, actions: AppActions, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(state.profile.name) }
    var adultConfirmed by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val initialSave = remember { state.unifiedProfile.savedSerial }
    val openedUid = remember { state.communityUid }
    val identityChanged = openedUid != state.communityUid
    val online = state.communityBackend == CommunityBackend.FIREBASE && state.signedIn && state.communityUid.isNotBlank()
    val valid = NicknamePolicy.validationError(name) == null
    val result = state.unifiedProfile.check?.takeIf { it.matches(name, state.communityUid) }
    val reserved = result?.availability in listOf(NicknameAvailability.AVAILABLE, NicknameAvailability.OWNED)
    val busy = state.busy || state.unifiedProfile.saving || state.unifiedProfile.checking
    LaunchedEffect(state.unifiedProfile.savedSerial) {
        if (submitted && state.unifiedProfile.savedSerial > initialSave) onDismiss()
    }
    FormDialog("프로필", onDismiss, closeTag = "close_profile", dismissLabel = null, confirm = {
        Button(onClick = { submitted = true; actions.saveUnifiedProfile(name, openedUid) }, shape = RunmoniShapes.Action,
            enabled = valid && !busy && !identityChanged && (!online || reserved), modifier = Modifier.heightIn(min = 48.dp).testTag("save_profile")) {
            Text(if (state.unifiedProfile.saving) "저장 중…" else "저장")
        }
    }) {
        OutlinedTextField(name, { name = it; submitted = false }, label = { Text("닉네임") }, singleLine = true,
            enabled = !state.unifiedProfile.saving && !state.busy, isError = !valid,
            supportingText = { Text(NicknamePolicy.validationError(name) ?: "한글·영문·숫자 등 2~16자") },
            modifier = Modifier.fillMaxWidth().testTag("profile_name"))
        state.unifiedProfile.connectedNickname?.takeIf { it != state.profile.name && it != name }?.let { serverName ->
            TextButton(onClick = { name = serverName }, shape = RunmoniShapes.Action, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("연결된 닉네임 ‘$serverName’ 사용")
            }
        }
        if (online) {
            TextButton(onClick = { actions.checkNickname(name) }, enabled = valid && !busy,
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
            Row {
                Checkbox(checked = adultConfirmed, onCheckedChange = { adultConfirmed = it }, enabled = !busy)
                Text("성인이며 크루·러너 기능을 이용할게요.", modifier = Modifier.weight(1f).padding(top = 12.dp))
            }
            OutlinedButton(onClick = { submitted = true; actions.connectCommunity(NicknamePolicy.normalize(name)) },
                enabled = valid && adultConfirmed && !busy, shape = RunmoniShapes.Action,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("confirm_sign_in")) { Text("이 닉네임으로 연결") }
        }
        if (identityChanged && !submitted) Text("연결 상태가 바뀌었어요. 닫고 프로필을 다시 열어 주세요.", color = RunmoniColors.CoralInk)
        state.unifiedProfile.error?.let { Text(it, color = RunmoniColors.CoralInk, modifier = Modifier.testTag("profile_error")) }
        Text("미니미와 옷은 ‘나 → 꾸미기’에서 바꿀 수 있어요.", style = MaterialTheme.typography.bodySmall)
    }
}

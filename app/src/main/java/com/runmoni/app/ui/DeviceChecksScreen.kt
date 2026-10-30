package com.runmoni.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*

@Composable
internal fun DeviceChecksScreen(state: AppState, actions: AppActions, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Surface(Modifier.fillMaxSize().testTag("device_checks_screen"), color = RunmoniColors.Paper) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("연결·휴대폰 점검", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
            }
            HorizontalDivider(color = RunmoniColors.Line)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EditorialHeading("READY TO RUN", "함께 확인해요")
                Text("방 연결과 Google 계정을 확인하고, 휴대폰에서 해본 항목을 표시해 주세요.", color = RunmoniColors.Muted)
                Text("1. 크루·파티 서버", style = MaterialTheme.typography.titleMedium)
                Text(when (state.deviceChecks.room) {
                    RoomCheck.NOT_CHECKED -> "아직 확인하지 않았어요"
                    RoomCheck.CHECKING -> "서버 응답을 확인하고 있어요…"
                    RoomCheck.FIREBASE_READY -> "Firebase 공유 방 목록을 서버에서 확인했어요"
                    RoomCheck.UNCONFIGURED -> "이 빌드에는 Firebase 방 설정이 없어요 · 설정을 포함한 새 APK가 필요해요"
                    RoomCheck.PUBLIC_READY -> "공용 주소에서 방 기능 응답을 확인했어요"
                    RoomCheck.LOCAL_READY -> "로컬 테스트 서버 응답이에요 · 공용 서버는 별도로 필요해요"
                    RoomCheck.UNREACHABLE -> if (state.communityBackend == CommunityBackend.FIREBASE) "방 목록에 연결하지 못했어요 · Firebase 설정·규칙·인덱스를 확인해 주세요" else "서버에 연결하지 못했어요 · 설정에서 서버 주소를 확인해 주세요"
                    RoomCheck.UNSUPPORTED -> "방 기능을 지원하는 서버인지 확인이 필요해요"
                }, modifier = Modifier.testTag("room_check_status"))
                OutlinedButton(onClick = actions::checkRoomConnection, enabled = state.deviceChecks.room != RoomCheck.CHECKING, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("check_room_connection")) { Text("서버 연결 확인") }
                SmallNote("응답 확인 후에는 크루 탭에서 방을 만들고 다른 휴대폰으로 입장해 주세요. 방 보존·채팅 확인은 아래에 직접 표시해요.")
                HorizontalDivider()
                Text("2. Google 계정", style = MaterialTheme.typography.titleMedium)
                Text(if (!state.cloud.configured) "이 빌드에는 Google 연결 설정이 없어요 · 설정을 포함한 새 APK가 필요해요" else state.cloud.status, modifier = Modifier.testTag("device_google_status"))
                if (state.cloud.configured) {
                    OutlinedButton(onClick = actions::signInGoogle, enabled = !state.cloud.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("device_google_sign_in")) { Text("Google 계정 선택·로그인") }
                    OutlinedButton(onClick = actions::checkGoogleSession, enabled = state.cloud.uid.isNotBlank() && !state.cloud.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("check_google_session")) { Text("계정 서버 응답 확인") }
                    Text(if (state.cloud.interactiveSignInAt > 0) "이번 실행에서 계정 선택·로그인 완료" else "이번 실행의 계정 선택·로그인은 아직 확인하지 않았어요", modifier = Modifier.testTag("interactive_login_status"))
                    Text(if (state.cloud.sessionCheckedAt > 0) "이번 실행에서 계정 서버 응답을 확인했어요" else "계정 서버 응답은 아직 확인하지 않았어요")
                }
                SmallNote("Google 계정과 방 닉네임은 따로 연결해요. 기록 요약 백업은 기록 상세에서 직접 확인해 주세요.")
                HorizontalDivider()
                Text("3. 내 휴대폰에서 확인", style = MaterialTheme.typography.titleMedium)
                SmallNote("아래 표시는 직접 확인한 결과예요. 자동 검사 결과와 구분해서 공유해요. 새 버전에서는 다시 확인해 주세요.")
                Text(if (state.locationAccess.precisePermissionGranted == true && state.locationAccess.gpsEnabled == true) "정확한 위치 권한·GPS 켜짐" else "야외 기록 전 위치 권한·GPS를 확인해 주세요")
                PhoneCheck.entries.forEach { check ->
                    val confirmed = check in state.deviceChecks.selfReported
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(confirmed, role = Role.Checkbox, onValueChange = { actions.setPhoneCheck(check, it) }).testTag("phone_check_${check.name}"), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmed, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(check.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                SmallNote("공유 내용: 앱 버전·공개 서명 지문·연결 결과·직접 확인 표시. 계정 이름·이메일·토큰·GPS 경로·서버 주소는 포함하지 않아요.")
                Button(onClick = actions::shareDeviceChecks, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("share_device_checks")) { Text("점검 결과 공유") }
            }
        }
    }
}

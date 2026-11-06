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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*

@Composable
internal fun DeviceChecksScreen(state: AppState, actions: AppActions, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Surface(Modifier.fillMaxSize().testTag("device_checks_screen"), color = RunmoniColors.Cream) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
            PageTopBar("연결·휴대폰 점검", onDismiss)
            HorizontalDivider(color = RunmoniColors.Line)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val gutter = if (LocalConfiguration.current.screenWidthDp >= 600) 32.dp else 20.dp
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.TopCenter).verticalScroll(rememberScrollState())
                    .padding(horizontal = gutter, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    ScreenSection("크루·파티 연결", showDivider = false) {
                        Text(when (state.deviceChecks.room) {
                            RoomCheck.NOT_CHECKED -> "아직 확인하지 않았어요"
                            RoomCheck.CHECKING -> "서버 응답을 확인하고 있어요…"
                            RoomCheck.FIREBASE_READY -> "크루·파티 목록을 불러왔어요"
                            RoomCheck.UNCONFIGURED -> "연결 설정이 빠져 있어요. 설정을 포함한 새 APK가 필요해요."
                            RoomCheck.PUBLIC_READY -> "공용 서버 응답을 확인했어요"
                            RoomCheck.LOCAL_READY -> "로컬 테스트 서버에 연결됐어요. 공용 연결은 아직 확인하지 않았어요."
                            RoomCheck.UNREACHABLE -> "크루·파티에 연결하지 못했어요. 네트워크와 연결 설정을 확인해 주세요."
                            RoomCheck.UNSUPPORTED -> "이 서버에서는 크루·파티 기능을 확인할 수 없어요"
                        }, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("room_check_status").semantics { liveRegion = LiveRegionMode.Polite })
                        OutlinedButton(shape = RunmoniShapes.Action, onClick = actions::checkRoomConnection, enabled = state.deviceChecks.room != RoomCheck.CHECKING, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("check_room_connection")) { Text("연결 확인") }
                        SmallNote("연결 응답만 확인해요. 파티 참가와 채팅은 아래에서 직접 확인해 주세요.")
                    }
                    ScreenSection("Google 계정") {
                        Text(if (!state.cloud.configured) "Google 연결 설정이 빠져 있어요. 새 APK가 필요해요." else state.cloud.status,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("device_google_status"))
                        if (state.cloud.configured) {
                            OutlinedButton(shape = RunmoniShapes.Action, onClick = actions::signInGoogle, enabled = !state.cloud.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("device_google_sign_in")) { Text("Google 로그인") }
                            OutlinedButton(shape = RunmoniShapes.Action, onClick = actions::checkGoogleSession, enabled = state.cloud.uid.isNotBlank() && !state.cloud.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("check_google_session")) { Text("계정 연결 확인") }
                            Text(if (state.cloud.interactiveSignInAt > 0) "이번 실행에서 계정 선택·로그인 완료" else "이번 실행의 계정 선택·로그인은 아직 확인하지 않았어요",
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("interactive_login_status"))
                            Text(if (state.cloud.sessionCheckedAt > 0) "이번 실행에서 계정 서버 응답을 확인했어요" else "계정 서버 응답은 아직 확인하지 않았어요", style = MaterialTheme.typography.bodyMedium)
                        }
                        SmallNote("크루 프로필 연결과는 별도예요. 기록 요약 백업은 기록 상세에서 확인할 수 있어요.")
                    }
                    ScreenSection("내 휴대폰에서 확인") {
                        SmallNote("직접 확인한 항목을 체크해 주세요. 자동 검사와 구분해 공유돼요. 새 버전에서는 다시 확인해 주세요.")
                        Text(if (state.locationAccess.precisePermissionGranted == true && state.locationAccess.gpsEnabled == true) "정확한 위치 권한·GPS 켜짐" else "야외 기록 전 위치 권한·GPS를 확인해 주세요", style = MaterialTheme.typography.bodyMedium)
                        PhoneCheck.entries.forEach { check ->
                            val confirmed = check in state.deviceChecks.selfReported
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(confirmed, role = Role.Checkbox, onValueChange = { actions.setPhoneCheck(check, it) }).testTag("phone_check_${check.name}"), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = confirmed, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Text(check.label, Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider(color = RunmoniColors.Line)
                        }
                        SmallNote("앱 버전·서명·연결 결과·체크한 항목을 공유해요. 계정 정보·토큰·서버 주소·GPS 경로는 포함하지 않아요.")
                        Button(shape = RunmoniShapes.Action, onClick = actions::shareDeviceChecks, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("share_device_checks")) { Text("점검 결과 공유") }
                    }
                }
            }
        }
    }
}

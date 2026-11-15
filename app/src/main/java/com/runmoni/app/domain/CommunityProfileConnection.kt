package com.runmoni.app.domain

enum class CommunityProfileConnectionPhase { DISCONNECTED, PENDING, CACHED, VERIFIED }
enum class CommunityProfileOperation { AUTHENTICATION, PROFILE_READ, NICKNAME_READ, NICKNAME_SAVE, REMEMBER_CONNECTION }
enum class CommunityProfileFailureReason { PERMISSION_DENIED, AUTHENTICATION, NETWORK, TIMEOUT, LOCAL_STORAGE, SESSION_CHANGED }

/** Contains a fixed, safe explanation; never an SDK error, document path or token. */
data class CommunityProfileFailure(val operation: CommunityProfileOperation, val reason: CommunityProfileFailureReason) {
    val message: String get() = when (reason) {
        CommunityProfileFailureReason.PERMISSION_DENIED -> when (operation) {
            CommunityProfileOperation.NICKNAME_READ, CommunityProfileOperation.NICKNAME_SAVE ->
                "닉네임 예약 권한이 거부됐어요. 연결 설정을 확인해야 해요. 같은 계정과 입력한 닉네임은 유지했어요."
            else -> "온라인 프로필 접근 권한이 거부됐어요. 연결 설정을 확인해야 해요. 기존 계정은 유지했어요."
        }
        CommunityProfileFailureReason.AUTHENTICATION -> "프로필 인증을 확인하지 못했어요. 기존 연결을 지우지 않았어요."
        CommunityProfileFailureReason.NETWORK -> "프로필 서버에 연결하지 못했어요. 계정 정보는 지우지 않았어요. 네트워크를 확인해 주세요."
        CommunityProfileFailureReason.TIMEOUT -> "프로필 서버의 응답이 늦어요. 같은 계정과 닉네임을 유지했어요."
        CommunityProfileFailureReason.LOCAL_STORAGE -> "온라인 프로필은 연결됐지만 이 기기에 연결 상태를 기억하지 못했어요. 기기 저장 공간을 확인해 주세요."
        CommunityProfileFailureReason.SESSION_CHANGED -> "프로필 연결이 바뀌어 이전 요청을 적용하지 않았어요. 다시 열어 확인해 주세요."
    }
}

internal class CommunityProfileException(val failure: CommunityProfileFailure) : IllegalStateException(failure.message)

/** The server has confirmed this exact Auth UID; it is not an Auth candidate. */
internal data class CommunityProfileReceipt(val uid: String, val nickname: String?, val nicknameKey: String?)

data class CommunityProfileConnectionState(
    val phase: CommunityProfileConnectionPhase = CommunityProfileConnectionPhase.DISCONNECTED,
    val uid: String = "",
    val nickname: String? = null,
    val nicknameKey: String? = null,
    val failure: CommunityProfileFailure? = null,
) {
    val connected: Boolean get() = uid.isNotBlank() && phase in setOf(CommunityProfileConnectionPhase.CACHED, CommunityProfileConnectionPhase.VERIFIED)
    /** CACHED never means that this execution obtained a server response. */
    val serverVerified: Boolean get() = connected && phase == CommunityProfileConnectionPhase.VERIFIED
    val issue: String? get() = failure?.message
}

/** Pending/foreign Auth and an unrelated old receipt must never be promoted. */
internal fun restoredCommunityProfile(acceptedCandidateUid: String, currentAuthUid: String,
    receipt: CommunityProfileReceipt?): CommunityProfileConnectionState =
    if (receipt != null && receipt.uid.isNotBlank() && acceptedCandidateUid == receipt.uid && currentAuthUid == receipt.uid)
        CommunityProfileConnectionState(CommunityProfileConnectionPhase.CACHED, receipt.uid, receipt.nickname, receipt.nicknameKey)
    else CommunityProfileConnectionState()

internal fun CommunityProfileConnectionState.offline(failure: CommunityProfileFailure): CommunityProfileConnectionState =
    copy(phase = if (connected) CommunityProfileConnectionPhase.CACHED else phase, failure = failure)

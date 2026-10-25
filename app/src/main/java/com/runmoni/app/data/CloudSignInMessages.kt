package com.runmoni.app.data

/** Only fixed support codes enter the UI. Provider messages may contain personal data. */
internal fun cloudSignInFailureMessage(code: String): String = when (code) {
    "G01" -> "Google 계정을 불러오지 못했어요. 기기의 계정과 Google Play 서비스를 확인해 주세요. (G01)"
    "G02" -> "Google 로그인 서비스를 찾지 못했어요. Google Play 서비스가 있는 기기에서 다시 시도해 주세요. (G02)"
    "G03" -> "이 기기는 Google 로그인 방식을 지원하지 않아요. Google Play 서비스를 확인해 주세요. (G03)"
    "G04" -> "Google 계정 선택이 중단됐어요. 다시 시도해 주세요. (G04)"
    "G05" -> "Google 계정 선택창을 열지 못했어요. 기기의 Google 계정·네트워크와 앱 로그인 설정을 확인해야 해요. (G05)"
    "G06" -> "Google 로그인 응답을 확인하지 못했어요. 앱 로그인 설정을 확인해야 해요. (G06)"
    "F01" -> "Google 계정은 선택했지만 인증 서버에 연결하지 못했어요. 네트워크를 확인해 주세요. (F01)"
    "F02" -> "로그인 요청이 너무 많아요. 잠시 뒤에 다시 시도해 주세요. (F02)"
    "F03" -> "앱의 Google 로그인이 아직 활성화되지 않았어요. Firebase 로그인 제공자 설정을 확인해야 해요. (F03)"
    "F04" -> "이 앱을 인증할 수 없어요. Firebase 앱 등록·서명·API 설정을 확인해야 해요. (F04)"
    "F05" -> "인증 서버가 Google 로그인 응답을 받지 않았어요. 앱 로그인 설정을 확인해야 해요. (F05)"
    "F06" -> "다른 로그인 방식으로 연결된 계정이에요. 기존 로그인 방식을 확인해 주세요. (F06)"
    "F09" -> "인증 서버에서 로그인을 완료하지 못했어요. 앱 로그인 설정을 확인해야 해요. (F09)"
    else -> "Google 로그인 중 오류가 발생했어요. 다시 시도한 뒤 같은 문제가 있으면 문의해 주세요. (G09)"
}

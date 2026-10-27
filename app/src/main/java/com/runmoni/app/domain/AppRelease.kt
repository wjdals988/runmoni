package com.runmoni.app.domain

/** Metadata controls an external browser link, never silent APK installation. */
data class AppRelease(
    val version: String,
    val versionCode: Long,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val certificateSha256: String,
    val signed: Boolean,
    val packageName: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val notes: String = "",
) {
    fun isTrusted(): Boolean {
        if (!version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?"))) return false
        return packageName == "com.runmoni.app" && versionCode in 1..2_100_000_000L &&
            signed && certificateSha256.equals(RELEASE_CERTIFICATE, ignoreCase = true) &&
            sha256.matches(Regex("[A-Fa-f0-9]{64}")) && sizeBytes in 1..MAX_APK_BYTES &&
            fileName == "runmoni-$version.apk" &&
            downloadUrl == "$REPOSITORY/releases/download/v$version/$fileName" &&
            releaseUrl == "$REPOSITORY/releases/tag/v$version"
    }

    fun isNewerThan(installedCode: Long): Boolean = isTrusted() && versionCode > installedCode

    companion object {
        const val REPOSITORY = "https://github.com/wjdals988/runmoni"
        const val RELEASE_CERTIFICATE = "2d7f724cd20b1ff2263eb34e8e02d5fec9db674488b10b813dd239838b975830"
        const val MAX_APK_BYTES = 512L * 1024 * 1024
    }
}

fun latestPublishedRelease(stable: AppRelease?, preview: AppRelease?, includeDevelopment: Boolean): AppRelease? {
    require(stable == null || (stable.isTrusted() && !stable.version.contains('-')))
    require(preview == null || (preview.isTrusted() && preview.version.contains('-')))
    return listOfNotNull(stable, preview.takeIf { includeDevelopment }).maxByOrNull { it.versionCode }
}

data class AppVersionEntry(val version: String, val date: String, val status: String, val changes: List<String>)

val bundledAppHistory = listOf(
    AppVersionEntry("0.1.6-dev", "2026-10-27", "개발 중 · 미게시", listOf(
        "홈·나·옷장의 기본 미니미를 승인된 전신 원화로 복구했어요.",
        "새 공개 버전이 있으면 우상단 설정에 알림 점과 업데이트 안내를 표시해요.",
        "연결되지 않은 모임을 정상 조회한 빈 결과처럼 표시하던 문제를 고쳤어요.",
        "꾸미기 아이템은 원화를 준비 중이에요. 기존 보유 내역과 선택은 보관돼요.",
    )),
    AppVersionEntry("0.1.5-dev", "2026-10-25", "개발 프리뷰", listOf(
        "미니미를 원화 부품으로 구성해 얼굴과 체형을 유지하며 꾸밀 수 있게 바꿨어요.",
        "양팔과 다리가 함께 움직이는 달리기와 특수 세트 표현을 개선했어요.",
        "Google 계정 선택과 인증 응답 오류의 설명을 더 정확하게 바꿨어요.",
    )),
    AppVersionEntry("0.1.4-dev", "2026-10-25", "로컬 검증본", listOf(
        "홈을 러닝 시작 중심으로 정리하고 중복 통계 카드를 줄였어요.",
        "설정을 전체 화면으로 옮기고 제목·설명·탭 글자 크기를 조정했어요.",
        "크루와 내 기록 화면을 간결하게 정리하고 달리는 모습의 새 아이콘을 적용했어요.",
        "Google 버튼에서 계정 추가를 지원하고 로그인 오류 단계를 구분해요.",
    )),
    AppVersionEntry("0.1.3-dev", "2026-10-25", "로컬 검증본", listOf(
        "5개 탭의 색상·글자·간격을 정리하고 큰 글씨 배치를 개선했어요.",
        "러닝 시작·일시정지·재개·종료 버튼을 하단에 고정했어요.",
        "Google 계정과 기록 요약 백업을 연결하고 설정을 정리했어요.",
        "앱 정보·버전 내역·업데이트 확인과 새 아이콘을 추가했어요.",
        "도트 미니미의 8프레임 달리기와 부위별 꾸미기를 연결했어요.",
    )),
    AppVersionEntry("0.1.2-dev", "2026-10-24", "소스 개발본", listOf(
        "Google 계정과 초대 파일럿 계정을 분리했어요.",
        "선택한 완료 기록의 요약 1건을 계정에 백업하는 기능을 준비했어요.",
    )),
    AppVersionEntry("0.1.1-dev", "2026-10-24", "공개 개발 프리뷰", listOf(
        "서명 APK와 대시보드 다운로드를 공개했어요.",
        "기기 러닝 기록·미니미와 로컬 서버 기반 크루·파티를 시험할 수 있어요.",
        "이 공개 버전에는 Firebase 계정 기능이 포함되지 않았어요.",
    )),
    AppVersionEntry("0.1.0-dev", "2026-10-13", "초기 파일럿", listOf(
        "GPS 기록·일시정지·복구·GPX 내보내기를 추가했어요.",
        "크루·파티·공지·채팅·음성·음악의 개발 기반을 만들었어요.",
    )),
)

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
    AppVersionEntry("0.1.3-dev", "2026-10-25", "개발 프리뷰", listOf(
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

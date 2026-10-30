package com.runmoni.app.domain

enum class RoomCheck { NOT_CHECKED, CHECKING, FIREBASE_READY, UNCONFIGURED, PUBLIC_READY, LOCAL_READY, UNREACHABLE, UNSUPPORTED }
enum class PhoneCheck(val label: String) {
    PHYSICAL_PHONE("실제 Android 휴대폰에서 확인했어요"),
    INSTALL_LAUNCH("APK 설치 후 앱이 정상적으로 열렸어요"),
    RUN_REOPEN("야외 러닝을 저장하고 앱을 다시 열어 기록을 확인했어요"),
    GPX_EXPORT("저장한 기록의 GPX 파일을 내보냈어요"),
    TWO_DEVICE_ROOMS("휴대폰 두 대에서 같은 방에 입장하고 채팅을 주고받았어요"),
}

data class DeviceChecks(
    val room: RoomCheck = RoomCheck.NOT_CHECKED,
    val roomCheckedAt: Long = 0,
    val selfReported: Set<PhoneCheck> = emptySet(),
)

/** Deliberate allowlist: no address, account label/UID, token, route or exception text. */
fun deviceCheckReport(
    version: String, versionCode: Long, certificateSha256: String,
    checks: DeviceChecks, cloudConfigured: Boolean, accountConnected: Boolean,
    interactiveSignInAt: Long, sessionCheckedAt: Long,
    preciseLocation: Boolean, gpsEnabled: Boolean, backend: CommunityBackend = CommunityBackend.HTTP,
): String = buildString {
    appendLine("Runmoni connection check / schema=1")
    appendLine("version=${version.filter { it.isLetterOrDigit() || it in ".-_" }.take(40)} code=$versionCode")
    val certificate = certificateSha256.lowercase()
    appendLine("certificateSha256=${certificate.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: "unavailable"}")
    appendLine("room=${checks.room.name} checkedAt=${checks.roomCheckedAt}")
    appendLine("communityBackend=${backend.name}")
    appendLine("firebaseConfigured=$cloudConfigured accountConnected=$accountConnected")
    appendLine("interactiveGoogleSignInAt=$interactiveSignInAt sessionCheckedAt=$sessionCheckedAt")
    appendLine("preciseLocationPermission=$preciseLocation gpsEnabled=$gpsEnabled")
    PhoneCheck.entries.forEach { appendLine("selfReported.${it.name}=${it in checks.selfReported}") }
    append("Self reports are user confirmations. Server health/session checks do not verify physical-device flows or Firestore backup.")
}

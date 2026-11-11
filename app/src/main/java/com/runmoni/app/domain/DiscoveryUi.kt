package com.runmoni.app.domain

data class RunnerDiscoveryUi(
    val loading: Boolean = false,
    val error: String? = null,
    val requiresZoom: Boolean = false,
    val checkedAtMillis: Long? = null,
    val partial: Boolean = false,
)
data class RunnerSharingUi(val enabled: Boolean = false, val busy: Boolean = false, val error: String? = null)
data class UnifiedProfileUi(
    val connectedNickname: String? = null,
    val connectedNicknameKey: String? = null,
    val check: NicknameCheck? = null,
    val checking: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val savedSerial: Long = 0,
    val connecting: Boolean = false,
    val connectedSerial: Long = 0,
    val connectionUid: String = "",
)

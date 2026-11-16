package com.runmoni.app.domain
enum class AvatarGender(val wireValue: String, val label: String, val spriteRow: Int) {
    MALE("male", "남자", 0),
    FEMALE("female", "여자", 1);

    companion object {
        fun fromWire(value: String?): AvatarGender = entries.firstOrNull {
            it.wireValue.equals(value, ignoreCase = true)
        } ?: MALE
    }
}
data class AvatarStyle(
    val hair: Int = 0, val top: Int = 0, val bottom: Int = 0, val shoes: Int = 0,
    val accessory: Int = 0, val eyewear: Int = 0, val wrist: Int = 0,
    val waist: Int = 0, val hydration: Int = 0,
    val face: Int = 0, val socks: Int = 0, val headwear: Int? = null,
    val ear: Int? = null, val costume: Int = 0,
    val gender: AvatarGender = AvatarGender.MALE,
    val runningKitColors: RunningKitColors = RunningKitColors(),
)

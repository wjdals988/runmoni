package com.runmoni.app.domain

/** A local device preference; independent of authentication and saved running data. */
enum class AppAppearance(val wireValue: String, val label: String) {
    SYSTEM("system", "기기 설정에 맞추기"), LIGHT("light", "밝게"), DARK("dark", "어둡게");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromWire(value: String?): AppAppearance = entries.firstOrNull { it.wireValue == value } ?: SYSTEM
    }
}

/** One entrance response to an actual page change, never a repeated content reveal. */
internal object PageMotionPolicy {
    const val DURATION_MILLIS = 160
    const val TRAVEL_DP = 8f
    fun durationMillis(animationsEnabled: Boolean) = if (animationsEnabled) DURATION_MILLIS else 0
}

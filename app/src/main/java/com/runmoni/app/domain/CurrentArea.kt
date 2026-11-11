package com.runmoni.app.domain

import java.util.Locale

enum class CurrentAreaPhase { IDLE, LOCATING, RESOLVING, READY, UNAVAILABLE, PERMISSION_REQUIRED, LOCATION_DISABLED }

/** Foreground display only: the same approximate grid as the forecast, never a street address or shared presence. */
data class CurrentAreaUi(
    val phase: CurrentAreaPhase = CurrentAreaPhase.IDLE,
    val location: WeatherLocation? = null,
    val name: String? = null,
    val checkedAtMillis: Long? = null,
) {
    init {
        require(name == null || phase == CurrentAreaPhase.READY && location != null && AreaNameFormatter.clean(name) == name)
        require(phase != CurrentAreaPhase.READY || name != null)
        require(checkedAtMillis == null || checkedAtMillis > 0)
    }
    fun isRecent(nowMillis: Long): Boolean = location != null && checkedAtMillis?.let {
        it in (nowMillis - 15 * 60_000L)..(nowMillis + 60_000L)
    } == true
}

/** Only administrative fields are accepted; featureName/addressLine/road/house number are deliberately absent. */
data class AreaNameParts(val adminArea: String? = null, val subAdminArea: String? = null,
    val locality: String? = null, val subLocality: String? = null,
    val countryName: String? = null, val countryCode: String? = null)

object AreaNameFormatter {
    fun clean(value: String?): String? {
        if (value == null || value.any { it.isISOControl() }) return null
        return value.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotBlank() && it.length <= 112 }
    }

    fun from(parts: AreaNameParts): String? {
        val administrative = listOf(parts.adminArea, parts.subAdminArea, parts.locality, parts.subLocality)
            .mapNotNull { clean(it)?.takeIf { name -> name.length <= 48 } }
        if (administrative.isEmpty()) return null
        val names = if (parts.countryCode?.uppercase(Locale.ROOT) != "KR")
            listOfNotNull(clean(parts.countryName)) + administrative else administrative
        fun key(name: String) = name.lowercase(Locale.ROOT)
            // City aliases can repeat across provider fields (서울특별시/서울).
            // Provinces/districts keep their level: 제주특별자치도 and 제주시 are different places.
            .replace(Regex("(?:특별자치시|특별시|광역시|시)$"), "")
        return clean(names.distinctBy(::key).joinToString(" "))
    }
}

fun WeatherLocation.nearbyCoordinates(): String = String.format(Locale.ROOT, "%.2f, %.2f 부근", latitude, longitude)

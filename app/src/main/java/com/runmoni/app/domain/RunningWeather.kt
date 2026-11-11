package com.runmoni.app.domain

import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/** A forecast grid only. Raw GPS samples never enter the weather request/cache. */
data class WeatherLocation(val latitudeHundredths: Int, val longitudeHundredths: Int) {
    init { require(latitudeHundredths in -9000..9000 && longitudeHundredths in -18000..18000) }
    val latitude: Double get() = latitudeHundredths / 100.0
    val longitude: Double get() = longitudeHundredths / 100.0
    val query: String get() = String.format(Locale.US, "lat=%.2f&lon=%.2f", latitude, longitude)

    companion object {
        fun approximate(latitude: Double, longitude: Double): WeatherLocation {
            require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0)
            return WeatherLocation((latitude * 100).roundToInt(), (longitude * 100).roundToInt())
        }
    }
}

enum class WeatherPhase { IDLE, LOCATING, LOADING, READY, PERMISSION_REQUIRED, LOCATION_DISABLED, UNAVAILABLE }
enum class WeatherCondition { CLEAR, CLOUDY, RAIN, SNOW, THUNDER, FOG, UNKNOWN }
enum class WeatherCaution { NORMAL, CAUTION, HIGH }

data class RunningWeatherSnapshot(
    val location: WeatherLocation,
    val forecastAtMillis: Long,
    val issuedAtMillis: Long,
    val fetchedAtMillis: Long,
    val temperatureC: Double,
    val windMetersPerSecond: Double,
    val precipitationNextHourMm: Double? = null,
    val condition: WeatherCondition = WeatherCondition.UNKNOWN,
    val humidityPercent: Double? = null,
) {
    init {
        require(temperatureC.isFinite() && temperatureC in -100.0..65.0)
        require(windMetersPerSecond.isFinite() && windMetersPerSecond in 0.0..100.0)
        require(precipitationNextHourMm == null || precipitationNextHourMm.isFinite() && precipitationNextHourMm in 0.0..500.0)
        require(humidityPercent == null || humidityPercent.isFinite() && humidityPercent in 0.0..100.0)
    }

    /** A past cached forecast remains visible as old information, never as current advice. */
    fun isCurrent(nowMillis: Long): Boolean = forecastAtMillis > 0 && issuedAtMillis > 0 && fetchedAtMillis > 0 &&
        forecastAtMillis in (nowMillis - 90 * 60_000L)..(nowMillis + 60 * 60_000L) &&
        issuedAtMillis in (nowMillis - 36 * 60 * 60_000L)..(nowMillis + 10 * 60_000L) &&
        fetchedAtMillis in (nowMillis - 6 * 60 * 60_000L)..(nowMillis + 10 * 60_000L)
}

data class RunningWeatherState(
    val phase: WeatherPhase = WeatherPhase.IDLE,
    val snapshot: RunningWeatherSnapshot? = null,
    val message: String? = null,
    val area: CurrentAreaUi? = null,
) {
    init { require(area == null || snapshot != null && area.location == snapshot.location) }
}

data class RunningWeatherAdvice(val headline: String, val attire: String, val detail: String?, val caution: WeatherCaution)

object RunningWeatherGuidance {
    /** Descriptive guidance from supplied forecast values, not a medical/air-quality assessment. */
    fun from(weather: RunningWeatherSnapshot, nowMillis: Long): RunningWeatherAdvice? {
        if (!weather.isCurrent(nowMillis)) return null
        val temperature = weather.temperatureC
        val wind = weather.windMetersPerSecond
        val precipitation = weather.precipitationNextHourMm
        val rain = weather.condition == WeatherCondition.RAIN || precipitation != null && precipitation > 0.0
        val snow = weather.condition == WeatherCondition.SNOW
        val severe = weather.condition == WeatherCondition.THUNDER || temperature >= 35 || temperature <= -10 || wind >= 15 ||
            precipitation != null && precipitation >= 10
        val caution = severe || temperature >= 28 || temperature <= 0 || wind >= 8 || rain || snow || weather.condition == WeatherCondition.FOG
        val partial = precipitation == null || weather.condition == WeatherCondition.UNKNOWN
        val headline = when {
            severe -> "실내 러닝을 고려해 보세요"
            temperature >= 28 -> "더위를 피해 시간대를 골라요"
            temperature <= 0 -> "추위와 노면을 확인해요"
            snow -> "눈 예보와 미끄럼에 유의해요"
            rain -> "비 예보를 확인하고 준비해요"
            weather.condition == WeatherCondition.FOG -> "안개 예보와 시야를 확인해요"
            wind >= 8 -> "강한 바람을 고려해요"
            partial -> "기온과 바람 기준 안내예요"
            else -> "가벼운 러닝을 계획해 보세요"
        }
        var attire = when {
            temperature >= 25 -> "통기성 좋은 반팔·러닝 쇼츠"
            temperature >= 15 -> "반팔·가벼운 하의"
            temperature >= 8 -> "얇은 긴팔 또는 반팔에 경량 겉옷"
            temperature > 0 -> "긴팔·긴바지와 가벼운 바람막이"
            else -> "보온 이너·긴바지·바람막이·장갑"
        }
        if (wind >= 5 && !attire.contains("바람막이")) attire += " · 얇은 바람막이"
        val detail = when {
            weather.condition == WeatherCondition.THUNDER -> "뇌우 예보가 있어 야외 러닝은 미뤄요."
            severe -> "강도를 낮추거나 실내 운동으로 바꿔 보세요."
            snow || rain -> "젖은 노면과 체온 변화를 살피고 코스를 조절해요."
            weather.condition == WeatherCondition.FOG -> "시야를 확인하고 눈에 잘 띄는 옷을 준비해요."
            temperature >= 25 && (weather.humidityPercent ?: 0.0) >= 70 -> "습도가 높아요. 강도와 수분 섭취를 조절해요."
            precipitation == null -> "강수 정보가 없어 비 여부는 따로 확인해 주세요."
            partial -> "날씨 상태 정보가 일부 없어 주변 상황을 함께 살펴주세요."
            else -> null
        }
        return RunningWeatherAdvice(headline, attire, detail,
            if (severe) WeatherCaution.HIGH else if (caution || partial) WeatherCaution.CAUTION else WeatherCaution.NORMAL)
    }
}

/** Select the current/preceding forecast hour, never an arbitrary distant future row. */
fun currentWeatherForecastIndex(times: List<Long>, nowMillis: Long): Int? {
    val past = times.indices.filter { times[it] in (nowMillis - 90 * 60_000L)..nowMillis }
        .maxByOrNull { times[it] }
    return past ?: times.indices.filter { times[it] in nowMillis..(nowMillis + 60 * 60_000L) }
        .minByOrNull { times[it] }
}

fun millisUntilNextHomeMinute(nowMillis: Long): Long = 60_000L - Math.floorMod(nowMillis, 60_000L)

fun weatherInstant(value: String): Long = Instant.parse(value).toEpochMilli()

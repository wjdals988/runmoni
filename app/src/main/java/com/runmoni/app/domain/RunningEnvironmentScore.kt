package com.runmoni.app.domain

import kotlin.math.max
import kotlin.math.roundToInt

enum class RunningEnvironmentBand(val label: String) {
    GOOD("좋음"), CAUTION("주의"), POOR("실내 권장")
}

data class RunningEnvironmentAssessment(
    val score: Int,
    val band: RunningEnvironmentBand,
    val headline: String,
    /** Only supplied forecast values contribute. No pollution or ground-state inference. */
    val humidityIncluded: Boolean,
)

/** Runmoni's transparent forecast heuristic, not a medical or measured safety index.
 * Keep this deterministic rule independent of permissions, location and the network.
 * A failed refresh must never reuse the previous snapshot as a current score.
 */
object RunningEnvironmentScore {
    fun from(state: RunningWeatherState, nowMillis: Long): RunningEnvironmentAssessment? {
        if (state.phase != WeatherPhase.READY) return null
        val weather = state.snapshot ?: return null
        if (!weather.isCurrent(nowMillis)) return null
        val rain = weather.precipitationNextHourMm ?: return null
        if (weather.condition == WeatherCondition.UNKNOWN) return null

        val temperature = weather.temperatureC
        val wind = weather.windMetersPerSecond
        val temperaturePenalty = when {
            temperature < 8 -> ((8 - temperature) * 2).coerceAtMost(70.0)
            temperature > 20 -> ((temperature - 20) * 3).coerceAtMost(70.0)
            else -> 0.0
        }
        val windPenalty = ((wind - 3).coerceAtLeast(0.0) * 3).coerceAtMost(35.0)
        val rainPenalty = when {
            rain >= 10 -> 45.0
            rain >= 1 -> 20 + ((rain - 1) * 2.5)
            rain > 0 || weather.condition == WeatherCondition.RAIN -> 12.0
            else -> 0.0
        }
        val conditionPenalty = when (weather.condition) {
            WeatherCondition.THUNDER -> 75.0
            WeatherCondition.SNOW -> 40.0
            WeatherCondition.FOG -> 15.0
            else -> 0.0
        }
        val humidity = weather.humidityPercent
        val humidityPenalty = when {
            temperature < 25 || humidity == null -> 0.0
            humidity >= 85 -> 12.0
            humidity >= 70 -> 8.0
            else -> 0.0
        }
        val severe = weather.condition == WeatherCondition.THUNDER || temperature >= 35 ||
            temperature <= -10 || wind >= 15 || rain >= 10
        val caution = temperature >= 28 || temperature <= 0 || wind >= 8 || rain > 0 || humidityPenalty > 0 ||
            weather.condition in setOf(WeatherCondition.RAIN, WeatherCondition.SNOW, WeatherCondition.FOG)
        // Snow and rain are alternative precipitation descriptions. Do not count
        // the same precipitation twice; thunder's risk still caps the final score.
        var score = (100 - temperaturePenalty - windPenalty - max(rainPenalty, conditionPenalty) - humidityPenalty)
            .roundToInt().coerceIn(0, 100)
        if (severe) score = score.coerceAtMost(25)
        else if (caution) score = score.coerceAtMost(74)
        val band = when {
            score >= 75 -> RunningEnvironmentBand.GOOD
            score >= 40 -> RunningEnvironmentBand.CAUTION
            else -> RunningEnvironmentBand.POOR
        }
        val headline = when {
            severe || band == RunningEnvironmentBand.POOR -> "오늘은 실내 러닝을 고려해요"
            weather.condition == WeatherCondition.SNOW -> "눈 예보와 노면을 확인해요"
            weather.condition == WeatherCondition.RAIN || rain > 0 -> "비와 노면을 확인하고 준비해요"
            weather.condition == WeatherCondition.FOG -> "안개와 시야를 확인해요"
            temperature >= 28 -> "더위를 피해 시간대를 골라요"
            temperature <= 0 -> "추위에 대비해 짧게 달려요"
            wind >= 8 -> "바람이 강해요. 코스를 조절해요"
            humidityPenalty > 0 -> "덥고 습해요. 강도를 낮춰요"
            band == RunningEnvironmentBand.CAUTION -> "강도를 낮추고 가볍게 출발해요"
            else -> "가벼운 러닝을 계획해 보세요"
        }
        return RunningEnvironmentAssessment(score, band, headline, humidity != null)
    }
}

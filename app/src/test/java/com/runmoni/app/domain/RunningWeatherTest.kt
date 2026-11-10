package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RunningWeatherTest {
    private val now = weatherInstant("2026-11-10T08:30:00Z")
    private val place = WeatherLocation.approximate(37.5665, 126.9780)
    private fun weather(temperature: Double = 15.0, wind: Double = 2.0, rain: Double? = 0.0,
        condition: WeatherCondition = WeatherCondition.CLEAR, forecastAt: Long = now - 30 * 60_000L) =
        RunningWeatherSnapshot(place, forecastAt, now - 3_600_000L, now, temperature, wind, rain, condition)

    @Test fun weatherCoordinatesHaveOnlyHundredthDegreePrecision() {
        assertEquals(37.57, place.latitude, 0.0)
        assertEquals(126.98, place.longitude, 0.0)
        assertEquals("lat=37.57&lon=126.98", place.query)
        assertFalse(place.query.contains("37.5665"))
        assertEquals("lat=0.00&lon=0.00", WeatherLocation.approximate(-.0001, .0001).query)
    }

    @Test(expected = IllegalArgumentException::class) fun invalidCoordinatesCannotEnterWeatherRequest() {
        WeatherLocation.approximate(Double.NaN, 126.0)
    }

    @Test fun currentForecastSelectsLastPastHourEvenWhenRowsAreUnsorted() {
        val times = listOf(now + 30 * 60_000L, now - 90 * 60_000L, now - 30 * 60_000L)
        assertEquals(2, currentWeatherForecastIndex(times, now))
        assertEquals(0, currentWeatherForecastIndex(listOf(now + 30 * 60_000L), now))
        assertNull(currentWeatherForecastIndex(listOf(now - 91 * 60_000L, now + 61 * 60_000L), now))
    }

    @Test fun staleOrClockSkewedForecastNeverProducesCurrentAdvice() {
        val stale = weather(forecastAt = now - 91 * 60_000L)
        assertFalse(stale.isCurrent(now))
        assertNull(RunningWeatherGuidance.from(stale, now))
        assertNull(RunningWeatherGuidance.from(weather().copy(fetchedAtMillis = now + 11 * 60_000L), now))
        assertNull(RunningWeatherGuidance.from(weather().copy(issuedAtMillis = now - 37 * 3_600_000L), now))
    }

    @Test fun missingRainDataNeverBecomesADryOrOrdinaryRunningRecommendation() {
        val advice = requireNotNull(RunningWeatherGuidance.from(weather(rain = null), now))
        assertEquals(WeatherCaution.CAUTION, advice.caution)
        assertTrue(advice.headline.contains("기온과 바람"))
        assertTrue(advice.detail.orEmpty().contains("강수 정보"))
    }

    @Test fun dangerousHeatColdWindRainAndThunderPreferIndoorRunning() {
        for (value in listOf(weather(35.0), weather(-10.0), weather(wind = 15.0), weather(rain = 10.0),
            weather(condition = WeatherCondition.THUNDER))) {
            val advice = requireNotNull(RunningWeatherGuidance.from(value, now))
            assertEquals(WeatherCaution.HIGH, advice.caution)
            assertTrue(advice.headline.contains("실내"))
        }
    }

    @Test fun forecastRainFogAndWindChangeClothingOrCautionWithoutInventedAirQuality() {
        assertEquals(WeatherCaution.CAUTION, RunningWeatherGuidance.from(weather(condition = WeatherCondition.RAIN), now)!!.caution)
        assertTrue(RunningWeatherGuidance.from(weather(condition = WeatherCondition.FOG), now)!!.headline.contains("안개"))
        assertTrue(RunningWeatherGuidance.from(weather(wind = 6.0), now)!!.attire.contains("바람막이"))
        assertTrue(RunningWeatherGuidance.from(weather(-3.0), now)!!.attire.contains("장갑"))
        assertTrue(RunningWeatherGuidance.from(weather(26.0), now)!!.attire.contains("반팔"))
    }

    @Test fun clockWaitAlignsToMinuteBoundaryIncludingMidnightAndNegativeEpoch() {
        assertEquals(60_000L, millisUntilNextHomeMinute(0))
        assertEquals(1L, millisUntilNextHomeMinute(59_999L))
        assertEquals(60_000L, millisUntilNextHomeMinute(86_400_000L))
        assertEquals(1L, millisUntilNextHomeMinute(-1L))
    }

    @Test(expected = IllegalArgumentException::class) fun nonFiniteTemperatureCannotBePresentedAsARealForecast() {
        weather(Double.POSITIVE_INFINITY)
    }
}

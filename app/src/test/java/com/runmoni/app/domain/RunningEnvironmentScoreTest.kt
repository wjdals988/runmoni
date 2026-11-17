package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RunningEnvironmentScoreTest {
    private val now = weatherInstant("2026-11-17T05:30:00Z")
    private val ideal = RunningWeatherSnapshot(WeatherLocation(3757, 12698), now, now, now,
        15.0, 2.0, 0.0, WeatherCondition.CLEAR)

    private fun score(weather: RunningWeatherSnapshot = ideal) =
        requireNotNull(RunningEnvironmentScore.from(RunningWeatherState(WeatherPhase.READY, weather), now))

    @Test fun suppliedComfortableForecastHasGoodLabelAndShortDepartureAdvice() {
        val assessment = score()
        assertEquals(100, assessment.score)
        assertEquals(RunningEnvironmentBand.GOOD, assessment.band)
        assertTrue(assessment.headline.contains("러닝"))
        assertFalse(assessment.humidityIncluded)
    }

    @Test fun failedPendingAndPermissionStatesNeverReusePreviousScore() {
        for (phase in WeatherPhase.entries.filter { it != WeatherPhase.READY }) {
            assertNull("No current score during $phase", RunningEnvironmentScore.from(RunningWeatherState(phase, ideal), now))
        }
        assertNull(RunningEnvironmentScore.from(RunningWeatherState(WeatherPhase.READY), now))
    }

    @Test fun incompleteStaleAndFutureSkewedForecastsNeverInventAScore() {
        for (weather in listOf(
            ideal.copy(precipitationNextHourMm = null),
            ideal.copy(condition = WeatherCondition.UNKNOWN),
            ideal.copy(forecastAtMillis = now - 91 * 60_000L),
            ideal.copy(issuedAtMillis = now - 37 * 3_600_000L),
            ideal.copy(fetchedAtMillis = now + 11 * 60_000L),
        )) assertNull(RunningEnvironmentScore.from(RunningWeatherState(WeatherPhase.READY, weather), now))
    }

    @Test fun severeHazardsCannotBeCancelledByOtherComfortableValues() {
        for (weather in listOf(
            ideal.copy(condition = WeatherCondition.THUNDER),
            ideal.copy(temperatureC = 35.0),
            ideal.copy(temperatureC = -10.0),
            ideal.copy(windMetersPerSecond = 15.0),
            ideal.copy(precipitationNextHourMm = 10.0),
        )) {
            val assessment = score(weather)
            assertTrue("Severe hazard cap for $weather", assessment.score <= 25)
            assertEquals(RunningEnvironmentBand.POOR, assessment.band)
            assertTrue(assessment.headline.contains("실내"))
        }
    }

    @Test fun rainSnowFogHeatColdAndStrongWindAreNeverGreen() {
        for (weather in listOf(
            ideal.copy(condition = WeatherCondition.RAIN),
            ideal.copy(condition = WeatherCondition.SNOW),
            ideal.copy(condition = WeatherCondition.FOG),
            ideal.copy(precipitationNextHourMm = 0.01),
            ideal.copy(temperatureC = 28.0),
            ideal.copy(temperatureC = 0.0),
            ideal.copy(windMetersPerSecond = 8.0),
            ideal.copy(temperatureC = 25.0, humidityPercent = 70.0),
        )) {
            assertNotEquals("No green for $weather", RunningEnvironmentBand.GOOD, score(weather).band)
        }
    }

    @Test fun increasingHeatColdWindOrRainNeverImprovesItsOwnScore() {
        val sweeps = listOf(
            (20..50).map { ideal.copy(temperatureC = it.toDouble()) },
            (0..38).map { ideal.copy(temperatureC = 8.0 - it) },
            (0..40).map { ideal.copy(windMetersPerSecond = it / 2.0) },
            (0..40).map { ideal.copy(precipitationNextHourMm = it / 2.0) },
        )
        sweeps.forEach { values ->
            values.map { score(it).score }.zipWithNext().forEach { (before, after) -> assertTrue(after <= before) }
        }
    }

    @Test fun statedThresholdsApplyAtTheBoundaryAndNeverOneStepLater() {
        val advisoryBoundaries = listOf(
            ideal.copy(temperatureC = 27.9) to ideal.copy(temperatureC = 28.0),
            ideal.copy(temperatureC = 0.1) to ideal.copy(temperatureC = 0.0),
            ideal.copy(windMetersPerSecond = 7.9) to ideal.copy(windMetersPerSecond = 8.0),
        )
        advisoryBoundaries.forEach { (before, after) ->
            assertEquals(RunningEnvironmentBand.GOOD, score(before).band)
            assertNotEquals(RunningEnvironmentBand.GOOD, score(after).band)
            assertTrue(score(after).score <= score(before).score)
        }
        listOf(
            ideal.copy(temperatureC = 34.9) to ideal.copy(temperatureC = 35.0),
            ideal.copy(temperatureC = -9.9) to ideal.copy(temperatureC = -10.0),
            ideal.copy(windMetersPerSecond = 14.9) to ideal.copy(windMetersPerSecond = 15.0),
            ideal.copy(precipitationNextHourMm = 9.9) to ideal.copy(precipitationNextHourMm = 10.0),
        ).forEach { (before, after) ->
            assertTrue(score(before).score > 25)
            assertTrue(score(after).score <= 25)
        }
    }

    @Test fun missingHumidityIsOmittedAndWarmHumidityOnlyLowersTheScore() {
        val missing = score(ideal.copy(temperatureC = 26.0))
        val moderate = score(ideal.copy(temperatureC = 26.0, humidityPercent = 50.0))
        val humid = score(ideal.copy(temperatureC = 26.0, humidityPercent = 85.0))
        assertEquals(missing.score, moderate.score)
        assertFalse(missing.humidityIncluded)
        assertTrue(moderate.humidityIncluded)
        assertTrue(humid.score < moderate.score)
        assertEquals(score().score, score(ideal.copy(humidityPercent = 100.0)).score)
    }

    @Test fun supportedForecastExtremesRemainBoundedAndMatchTheirNamedBand() {
        for (temperature in listOf(-100.0, -10.0, -0.1, 8.0, 20.0, 27.9, 35.0, 65.0))
            for (wind in listOf(0.0, 3.0, 7.9, 8.0, 14.9, 15.0, 100.0))
                for (rain in listOf(0.0, 0.1, 1.0, 9.9, 10.0, 500.0)) {
                    val assessment = score(ideal.copy(temperatureC = temperature, windMetersPerSecond = wind,
                        precipitationNextHourMm = rain))
                    assertTrue(assessment.score in 0..100)
                    assertEquals(when (assessment.score) {
                        in 75..100 -> RunningEnvironmentBand.GOOD
                        in 40..74 -> RunningEnvironmentBand.CAUTION
                        else -> RunningEnvironmentBand.POOR
                    }, assessment.band)
                }
    }
}

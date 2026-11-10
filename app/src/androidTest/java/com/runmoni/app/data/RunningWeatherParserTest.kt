package com.runmoni.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runmoni.app.domain.WeatherCondition
import com.runmoni.app.domain.WeatherLocation
import com.runmoni.app.domain.weatherInstant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic provider-shaped JSON. No HTTP calls or claim of real weather verification. */
@RunWith(AndroidJUnit4::class)
class RunningWeatherParserTest {
    private val location = WeatherLocation(3757, 12698)
    private val now = weatherInstant("2026-11-10T08:30:00Z")
    private fun row(time: String, temperature: String = "13.5", extras: String = "", next: String = "") = """
        {"time":"$time","data":{"instant":{"details":{"air_temperature":$temperature,"wind_speed":3.2$extras}}$next}}
    """.trimIndent()
    private fun data(rows: String, temperatureUnit: String = "celsius", updated: String = "2026-11-10T07:10:00Z") = """
        {"geometry":{"type":"Point","coordinates":[126.98,37.57,0]},"properties":{"meta":{
          "updated_at":"$updated","units":{"air_temperature":"$temperatureUnit","wind_speed":"m/s","precipitation_amount":"mm","relative_humidity":"%"}},
          "timeseries":[$rows]}}
    """.trimIndent()

    @Test fun parserChoosesCurrentHourAndPreservesActualForecastAndIssueTimes() {
        val next = """, "next_1_hours":{"summary":{"symbol_code":"lightrain_day"},"details":{"precipitation_amount":0.3}}"""
        val body = data(row("2026-11-10T09:00:00Z", "20") + "," +
            row("2026-11-10T08:00:00Z", extras = ",\"relative_humidity\":76", next = next))
        val snapshot = MetWeatherParser.parse(body, location, now, now)
        assertEquals(weatherInstant("2026-11-10T08:00:00Z"), snapshot.forecastAtMillis)
        assertEquals(weatherInstant("2026-11-10T07:10:00Z"), snapshot.issuedAtMillis)
        assertEquals(13.5, snapshot.temperatureC, 0.0)
        assertEquals(0.3, snapshot.precipitationNextHourMm!!, 0.0)
        assertEquals(76.0, snapshot.humidityPercent!!, 0.0)
        assertEquals(WeatherCondition.RAIN, snapshot.condition)
        assertEquals(location, snapshot.location)
    }

    @Test fun absentPrecipitationAndSymbolStayUnknownInsteadOfBeingInventedAsDry() {
        val value = MetWeatherParser.parse(data(row("2026-11-10T08:00:00Z")), location, now, now)
        assertNull(value.precipitationNextHourMm)
        assertNull(value.humidityPercent)
        assertEquals(WeatherCondition.UNKNOWN, value.condition)
    }

    @Test fun wrongUnitsOldBulletinAndDistantForecastCannotBecomeCurrentWeather() {
        val cases = listOf(data(row("2026-11-10T08:00:00Z"), temperatureUnit = "kelvin"),
            data(row("2026-11-10T08:00:00Z"), updated = "2026-11-08T07:00:00Z"),
            data(row("2026-11-11T08:00:00Z")), data(row("2026-11-10T08:00:00Z", "1e309")))
        for (body in cases) {
            assertTrue(runCatching { MetWeatherParser.parse(body, location, now, now) }.isFailure)
        }
    }

    @Test fun thunderAndFogAreNotLostInGenericCloudClassification() {
        fun code(symbol: String) = MetWeatherParser.parse(data(row("2026-11-10T08:00:00Z", next =
            """, "next_1_hours":{"summary":{"symbol_code":"$symbol"},"details":{"precipitation_amount":0}}""")), location, now, now).condition
        assertEquals(WeatherCondition.THUNDER, code("lightsnowandthunder_day"))
        assertEquals(WeatherCondition.FOG, code("fog"))
    }
}

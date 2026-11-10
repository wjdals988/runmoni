package com.runmoni.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Synthetic forecast/viewport fixtures; no actual location, weather or server request. */
class HomeWeatherLayoutTest : RunmoniDesignTestBase() {
    private fun snapshot(now: Long = System.currentTimeMillis()) = RunningWeatherSnapshot(
        WeatherLocation(3757, 12698), now, now, now, 7.0, 7.2, 3.6, WeatherCondition.RAIN)

    @Test fun valuesStayBesideTheAvatarAtPhoneWidthsAndReflowWithLargeText() {
        val viewport = mutableStateOf(Viewport(320, 1f))
        val state = AppState(weather = RunningWeatherState(WeatherPhase.READY, snapshot()))
        var entries = 0
        compose.setContent {
            RunmoniTheme {
                ViewportBox(viewport.value) {
                    HomeScreen(state, {}, {}, {}, onWeatherEntry = { entries++ })
                }
            }
        }
        for (width in listOf(320, 360, 411)) for (scale in listOf(1f, 1.5f, 2f)) {
            compose.runOnIdle { viewport.value = Viewport(width, scale) }
            // Previous scroll positions belong to the same route; bring the
            // heading back into view before comparing real measured bounds.
            compose.onNodeWithTag("home_date_time").performScrollTo()
            val avatar = compose.onNodeWithTag("home_avatar").getUnclippedBoundsInRoot()
            if (scale == 1f) {
                compose.onNodeWithTag("home_avatar_conditions_row").assertExists()
                compose.onNodeWithTag("home_avatar_conditions_stacked").assertDoesNotExist()
                val weather = compose.onNodeWithTag("home_weather_summary").getUnclippedBoundsInRoot()
                assertTrue("Avatar must precede weather at $width", avatar.right <= weather.left)
                assertTrue("Avatar and weather must align at top at $width", abs(avatar.top.value - weather.top.value) <= 1f)
                if (width == 320) capture("home-weather-beside-320-fixture")
            } else {
                compose.onNodeWithTag("home_avatar_conditions_row").assertDoesNotExist()
                compose.onNodeWithTag("home_avatar_conditions_stacked").assertExists()
                compose.onNodeWithTag("home_start_run").assertIsDisplayed()
                val action = compose.onNodeWithTag("home_start_run").getUnclippedBoundsInRoot()
                val weather = compose.onNodeWithTag("home_weather_summary").getUnclippedBoundsInRoot()
                assertTrue("Large text weather must follow the primary action", weather.top >= action.bottom)
                if (width == 320 && scale == 2f) capture("home-weather-stacked-320-font2-fixture")
            }
            compose.onNodeWithTag("home_weather_temperature").assertTextEquals("7°C")
            compose.onNodeWithTag("home_weather_wind").assertTextEquals("바람 7.2 m/s")
            compose.onNodeWithTag("home_weather_rain").assertTextEquals("1시간 강수 3.6 mm")
            compose.onNodeWithTag("home_header_temperature").assertDoesNotExist()
            compose.onNodeWithTag("home_attire_guidance").assertExists()
            compose.onNodeWithTag("home_weather_source").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            noTextOverflow("home weather $width / $scale")
        }
        compose.runOnIdle { assertEquals("Resizing must not request location again", 1, entries) }
    }

    @Test fun failedRefreshAndOldForecastKeepValuesWithoutFreshClothingAdvice() {
        val fresh = snapshot()
        val state = mutableStateOf(AppState(weather = RunningWeatherState(WeatherPhase.READY, fresh)))
        var refreshes = 0
        var runClicks = 0
        compose.setContent {
            RunmoniTheme {
                ViewportBox(Viewport(320, 1f)) {
                    HomeScreen(state.value, { runClicks++ }, {}, {}, onWeather = { refreshes++ })
                }
            }
        }
        compose.onNodeWithTag("home_attire_guidance").assertExists()
        compose.runOnIdle { state.value = state.value.copy(weather = RunningWeatherState(WeatherPhase.LOADING, fresh)) }
        compose.onNodeWithTag("home_attire_guidance").assertDoesNotExist()
        compose.onNodeWithTag("home_running_guidance").assertDoesNotExist()
        compose.onNodeWithTag("refresh_home_weather").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(weather = RunningWeatherState(WeatherPhase.UNAVAILABLE, fresh)) }
        compose.onNodeWithTag("home_weather_temperature").assertTextEquals("7°C")
        compose.onNodeWithTag("home_weather_status").assertExists()
        compose.onNodeWithTag("home_attire_guidance").assertDoesNotExist()
        compose.onNodeWithTag("home_running_guidance").assertDoesNotExist()
        compose.onNodeWithTag("refresh_home_weather").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
        compose.runOnIdle {
            state.value = state.value.copy(weather = RunningWeatherState(WeatherPhase.READY,
                fresh.copy(forecastAtMillis = fresh.forecastAtMillis - 2 * 60 * 60_000L)))
        }
        compose.onNodeWithTag("home_weather_forecast").assertTextContains("이전 정보")
        compose.onNodeWithTag("home_attire_guidance").assertDoesNotExist()
        compose.onNodeWithTag("home_running_guidance").assertDoesNotExist()
        compose.onNodeWithTag("home_start_run").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, runClicks) }
    }
}

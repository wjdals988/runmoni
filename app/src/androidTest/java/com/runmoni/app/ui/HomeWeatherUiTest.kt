package com.runmoni.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.runmoni.app.domain.RunningWeatherSnapshot
import com.runmoni.app.domain.RunningWeatherState
import com.runmoni.app.domain.WeatherCondition
import com.runmoni.app.domain.WeatherLocation
import com.runmoni.app.domain.WeatherPhase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeWeatherUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun refreshFailureKeepsThePreviousValueButNeverTheFreshRunningRecommendation() {
        val now = System.currentTimeMillis()
        val snapshot = RunningWeatherSnapshot(WeatherLocation(3757, 12698), now, now, now, 18.0, 2.0, 0.0, WeatherCondition.CLEAR)
        val weather = mutableStateOf(RunningWeatherState(WeatherPhase.READY, snapshot))
        var refreshes = 0
        compose.setContent {
            RunmoniTheme {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    HomeConditions(weather.value, { refreshes += 1 })
                }
            }
        }
        compose.onNodeWithTag("home_running_guidance").assertExists()
        compose.onNodeWithTag("refresh_home_weather").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, refreshes); weather.value = RunningWeatherState(WeatherPhase.LOADING, snapshot) }
        compose.onNodeWithTag("refresh_home_weather").assertIsNotEnabled()
        compose.onNodeWithTag("home_running_guidance").assertDoesNotExist()
        compose.runOnIdle { weather.value = RunningWeatherState(WeatherPhase.UNAVAILABLE, snapshot) }
        compose.onNodeWithTag("home_weather_temperature").assertExists()
        compose.onNodeWithTag("home_running_guidance").assertDoesNotExist()
        compose.onNodeWithTag("home_weather_status").assertExists()
    }
}

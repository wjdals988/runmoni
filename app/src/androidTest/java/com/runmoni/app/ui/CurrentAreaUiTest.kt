package com.runmoni.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic administrative labels/positions. OfflineMapComposeRule supplies inert tiles, never OSM/GPS/geocoding. */
class CurrentAreaUiTest : RunmoniDesignTestBase() {
    private val region = "서울특별시 성북구 길음동"
    private val cell = WeatherLocation(3760, 12702)
    private fun area(now: Long) = CurrentAreaUi(CurrentAreaPhase.READY, cell, region, now)
    private fun snapshot(now: Long) = RunningWeatherSnapshot(cell, now, now, now, 15.0, 2.0, 0.0, WeatherCondition.CLEAR)

    @Test fun namedHomeAreaRemainsReadableAndStartActionReachableAcrossNineViewportFontCases() {
        val now = System.currentTimeMillis()
        val viewport = mutableStateOf(Viewport(320, 1f))
        val state = mutableStateOf(AppState(weather = RunningWeatherState(WeatherPhase.READY, snapshot(now), area = area(now))))
        var entries = 0
        compose.setContent {
            RunmoniTheme { ViewportBox(viewport.value) {
                HomeScreen(state.value, {}, {}, {}, onWeatherEntry = { entries++ })
            } }
        }
        for (width in listOf(320, 360, 411)) for (scale in listOf(1f, 1.5f, 2f)) {
            compose.runOnIdle {
                viewport.value = Viewport(width, scale)
                val fresh = System.currentTimeMillis()
                state.value = state.value.copy(weather = RunningWeatherState(WeatherPhase.READY, snapshot(fresh), area = area(fresh)))
            }
            compose.onNodeWithTag("home_current_area").performScrollTo().assertIsDisplayed()
                .assertTextEquals("현재 위치 · " + region + " 부근")
            compose.onNodeWithTag("home_start_run").performScrollTo().assertIsDisplayed().assertIsEnabled()
                .assertHeightIsAtLeast(56.dp)
            noTextOverflow("current administrative area " + width + " / " + scale)
            if (width == 320 && (scale == 1f || scale == 2f)) {
                // The independent CTA reachability check can change scroll position.
                // These two review images must show the actual administrative label.
                compose.onNodeWithTag("home_current_area").performScrollTo().assertIsDisplayed()
                capture(if (scale == 1f) "home-current-area-320-fixture" else "home-current-area-320-font2-fixture")
            }
        }
        compose.runOnIdle { assertEquals("Layout changes must not repeat the foreground location entry", 1, entries) }
    }

    @Test fun previousForecastPlaceIsDistinguishedAndPermissionFailureInventsNoAreaName() {
        val now = System.currentTimeMillis()
        val weather = mutableStateOf(RunningWeatherState(WeatherPhase.READY, snapshot(now), area = area(now)))
        compose.setContent {
            RunmoniTheme { ViewportBox(Viewport(320, 1.5f)) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { HomeConditions(weather.value, {}) }
            } }
        }
        compose.onNodeWithTag("home_current_area").assertTextEquals("현재 위치 · " + region + " 부근")
        compose.runOnIdle { weather.value = RunningWeatherState(WeatherPhase.UNAVAILABLE, snapshot(now), area = area(now)) }
        compose.onNodeWithTag("home_current_area").assertTextEquals("예보 위치 · " + region + " 부근")
        compose.onNodeWithTag("home_attire_guidance").assertDoesNotExist()
        compose.runOnIdle {
            weather.value = RunningWeatherState(WeatherPhase.READY, snapshot(now).copy(forecastAtMillis = now - 2 * 60 * 60_000L),
                area = area(now - 16 * 60_000L))
        }
        compose.onNodeWithTag("home_current_area").assertTextEquals("예보 위치 · " + region + " 부근")
        compose.onNodeWithTag("home_attire_guidance").assertDoesNotExist()
        compose.runOnIdle { weather.value = RunningWeatherState(WeatherPhase.PERMISSION_REQUIRED) }
        compose.onNodeWithTag("home_current_area").assertDoesNotExist()
        compose.onAllNodesWithText(region, substring = true).assertCountEquals(0)
        compose.onNodeWithTag("home_weather_status").assertTextContains("위치 권한", substring = true)
        noTextOverflow("permission denied without an invented place")
    }

    @Test fun mapAreaLabelUsesDeviceFreshnessAndApproximateFallbackWithoutConstructingAMap() {
        val originalNow = System.currentTimeMillis()
        val now = mutableStateOf(originalNow)
        val current = mutableStateOf(area(originalNow))
        compose.setContent {
            RunmoniTheme { ViewportBox(Viewport(320, 2f)) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { CurrentAreaLabel(current.value, now.value) }
            } }
        }
        compose.onNodeWithTag("explore_current_area").assertTextEquals("현재 위치 · " + region + " 부근")
        noTextOverflow("fresh device administrative label")
        compose.runOnIdle { now.value = originalNow + 15 * 60_000L + 1 }
        compose.onNodeWithTag("explore_current_area").assertTextEquals("마지막 확인 위치 · " + region + " 부근")
        compose.runOnIdle {
            current.value = CurrentAreaUi(CurrentAreaPhase.UNAVAILABLE, cell, checkedAtMillis = now.value)
        }
        compose.onNodeWithTag("explore_current_area").assertTextEquals("현재 위치 · 37.60, 127.02 부근")
        compose.onNodeWithTag("area_name_unavailable").assertIsDisplayed()
        compose.onAllNodesWithText(region, substring = true).assertCountEquals(0)
        noTextOverflow("unavailable geocoder approximate coordinates")
        compose.runOnIdle { current.value = CurrentAreaUi(CurrentAreaPhase.PERMISSION_REQUIRED) }
        compose.onNodeWithTag("explore_current_area").assertTextContains("위치 권한", substring = true)
        compose.onNodeWithTag("area_name_unavailable").assertDoesNotExist()
        compose.runOnIdle { current.value = CurrentAreaUi(CurrentAreaPhase.LOCATION_DISABLED) }
        compose.onNodeWithTag("explore_current_area").assertTextEquals("기기의 위치를 켜 주세요.")
        noTextOverflow("location disabled without a map or fabricated district")
    }
}

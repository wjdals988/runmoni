package com.runmoni.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.RunningWeatherSnapshot
import com.runmoni.app.domain.RunningWeatherState
import com.runmoni.app.domain.WeatherCondition
import com.runmoni.app.domain.WeatherLocation
import com.runmoni.app.domain.WeatherPhase
import org.junit.Test

/** Synthetic local forecasts only; no location or provider request. */
class RunningEnvironmentUiTest : RunmoniDesignTestBase() {
    @Test fun currentScoreExplainsItsCriteriaAndDisappearsDuringFailedRefresh() {
        val now = System.currentTimeMillis()
        val snapshot = RunningWeatherSnapshot(WeatherLocation(3757, 12698), now, now, now,
            15.0, 2.0, 0.0, WeatherCondition.CLEAR)
        val weather = mutableStateOf(RunningWeatherState(WeatherPhase.READY, snapshot))
        compose.setContent {
            RunmoniTheme {
                ViewportBox(Viewport(320, 1f)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        HomeWeatherDetails(weather.value, now, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("home_environment_score").assertTextEquals("100점 / 100")
        compose.onNodeWithTag("home_environment_band").assertTextEquals("좋음")
        compose.onNodeWithTag("home_environment_scale").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(100f, 0f..100f)))
        compose.onNodeWithTag("home_environment_criteria").assertDoesNotExist()
        compose.onNodeWithTag("home_environment_criteria_toggle").performScrollTo()
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("home_environment_criteria").assertExists()
        compose.onNodeWithText("런모니 자체 예보 기준", substring = true).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { weather.value = RunningWeatherState(WeatherPhase.LOADING, snapshot) }
        compose.onNodeWithTag("home_environment_score").assertDoesNotExist()
        compose.onNodeWithTag("home_environment_scale").assertDoesNotExist()
        compose.runOnIdle { weather.value = RunningWeatherState(WeatherPhase.UNAVAILABLE, snapshot) }
        compose.onNodeWithTag("home_environment_score").assertDoesNotExist()
        compose.onNodeWithTag("home_weather_status").assertExists()
        compose.runOnIdle { weather.value = RunningWeatherState(WeatherPhase.READY,
            snapshot.copy(precipitationNextHourMm = null)) }
        compose.onNodeWithTag("home_environment_score").assertDoesNotExist()
        compose.onNodeWithTag("home_environment_incomplete").assertExists()
        compose.onNodeWithTag("home_running_guidance").assertExists()
    }

    @Test fun hazardScoreAndInlineCriteriaReflowAtNarrowWidthWithLargeText() {
        val now = System.currentTimeMillis()
        val viewport = mutableStateOf(Viewport(320, 1f))
        val dark = mutableStateOf(false)
        val weather = RunningWeatherState(WeatherPhase.READY, RunningWeatherSnapshot(
            WeatherLocation(3757, 12698), now, now, now, 35.0, 2.0, 0.0, WeatherCondition.CLEAR))
        compose.setContent {
            RunmoniTheme(darkTheme = dark.value) {
                ViewportBox(viewport.value) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        HomeWeatherDetails(weather, now, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("home_environment_band").assertTextEquals("실내 권장")
        compose.onNodeWithTag("home_running_guidance").assertTextContains("실내", substring = true)
        for (night in listOf(false, true)) {
            for (scale in listOf(1f, 1.5f, 2f)) {
                compose.runOnIdle { dark.value = night; viewport.value = Viewport(320, scale) }
                compose.onNodeWithTag("home_environment_score").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("home_environment_criteria_toggle").performScrollTo()
                    .assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                noTextOverflow("environment score 320 / $scale / dark=$night")
            }
            compose.onNodeWithTag("home_environment_criteria_toggle").performClick()
            compose.onNodeWithText("건강·안전 점수가 아니에요.", substring = true).performScrollTo().assertIsDisplayed()
            noTextOverflow("expanded environment criteria 320 / 2 / dark=$night")
            compose.onNodeWithTag("home_environment_criteria_toggle").performScrollTo()
                .assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag("home_environment_criteria").assertDoesNotExist()
            // The default score, rather than the tail of its optional explanation,
            // is the fixture to review. A large-text panel may exceed the viewport,
            // so anchor the heading after returning to this collapsed panel.
            compose.onNodeWithTag("home_environment_panel").performScrollTo()
            compose.onNodeWithText("러닝 환경").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("home_environment_score").assertIsDisplayed()
            compose.onNodeWithTag("home_environment_band").assertIsDisplayed()
            compose.onNodeWithTag("home_environment_scale").assertIsDisplayed()
            compose.onNodeWithTag("home_running_guidance").assertIsDisplayed()
            noTextOverflow("collapsed environment score capture 320 / 2 / dark=$night")
            capture("environment-score-320-font2-${if (night) "dark" else "light"}-fixture")
        }
    }
}

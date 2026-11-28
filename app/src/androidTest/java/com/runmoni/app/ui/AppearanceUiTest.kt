package com.runmoni.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.data.AppearancePreferences
import com.runmoni.app.domain.AppAppearance
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppearanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun oppositeThemeRootsKeepTheirOwnSemanticColoursAndReadableContrast() {
        var light: Map<String, Pair<Color, Color>> = emptyMap()
        var dark: Map<String, Pair<Color, Color>> = emptyMap()
        compose.setContent {
            Column {
                RunmoniTheme(darkTheme = false) {
                    val values = textColorPairs()
                    SideEffect { light = values }
                }
                RunmoniTheme(darkTheme = true) {
                    val values = textColorPairs()
                    SideEffect { dark = values }
                }
            }
        }
        compose.waitForIdle()
        assertNotEquals(light.getValue("body on canvas").second, dark.getValue("body on canvas").second)
        for ((theme, pairs) in listOf("light" to light, "dark" to dark)) {
            for ((role, colors) in pairs) {
                val ratio = contrast(colors.first, colors.second)
                assertTrue("$theme $role contrast is $ratio", ratio >= 4.5)
            }
        }
    }

    @Composable
    private fun textColorPairs(): Map<String, Pair<Color, Color>> = with(RunmoniColors) {
        mapOf(
            "body on canvas" to (Ink to Chalk),
            "muted on canvas" to (Muted to Chalk),
            "body on paper" to (Ink to Paper),
            "muted on paper" to (Muted to Paper),
            "primary button" to (OnPrimary to Primary),
            "selected navigation" to (OnPrimarySoft to PrimarySoft),
            "selected navigation border" to (Primary to PrimarySoft),
            "route" to (RouteBlue to BlueWash),
            "information" to (Muted to MintWash),
            "positive" to (EnvironmentGood to EnvironmentGoodWash),
            "caution" to (EnvironmentCaution to EnvironmentCautionWash),
            "error" to (EnvironmentPoor to EnvironmentPoorWash),
            "outdoor number" to (RunmoniTrack.Highlight to RunmoniTrack.Board),
            "outdoor detail" to (RunmoniTrack.Detail to RunmoniTrack.Board),
            "outdoor phase" to (RunmoniTrack.Recovery to RunmoniTrack.Board),
            "outdoor warning" to (RunmoniTrack.Attention to RunmoniTrack.Board),
        )
    }

    @Test fun choosingThemePersistsAcrossFreshReaderAndRecomposesCurrentContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = AppearancePreferences(context)
        val hadMode = original.preferences.contains(AppearancePreferences.KEY)
        val oldMode = original.preferences.getString(AppearancePreferences.KEY, null)
        var currentBackground: Color? = null
        try {
            original.write(AppAppearance.LIGHT)
            compose.setContent {
                RunmoniTheme {
                    val background = MaterialTheme.colorScheme.background
                    SideEffect { currentBackground = background }
                    AppearanceSettingsSection()
                }
            }
            compose.waitForIdle()
            val light = currentBackground!!
            compose.onNodeWithTag("appearance_dark").performClick()
            compose.waitUntil(5_000) { currentBackground != null && currentBackground != light }
            assertEquals(AppAppearance.DARK, AppearancePreferences(context).read())
            compose.onNodeWithTag("appearance_light").performClick()
            compose.waitUntil(5_000) { currentBackground == light }
            assertEquals(AppAppearance.LIGHT, AppearancePreferences(context).read())
        } finally {
            original.preferences.edit().apply {
                if (hadMode) putString(AppearancePreferences.KEY, oldMode) else remove(AppearancePreferences.KEY)
            }.commit()
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val high = maxOf(a.luminance(), b.luminance())
        val low = minOf(a.luminance(), b.luminance())
        return (high + .05) / (low + .05)
    }
}

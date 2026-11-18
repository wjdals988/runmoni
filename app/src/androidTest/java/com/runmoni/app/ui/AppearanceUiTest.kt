package com.runmoni.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
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
        var light: List<Color>? = null
        var dark: List<Color>? = null
        compose.setContent {
            Column {
                RunmoniTheme(darkTheme = false) {
                    val values = listOf(RunmoniColors.Navy, RunmoniColors.Cream, RunmoniColors.Muted,
                        RunmoniColors.Paper, RunmoniColors.Primary, RunmoniColors.OnPrimary,
                        RunmoniColors.EnvironmentGood, RunmoniColors.EnvironmentGoodWash,
                        RunmoniColors.EnvironmentCaution, RunmoniColors.EnvironmentCautionWash,
                        RunmoniColors.EnvironmentPoor, RunmoniColors.EnvironmentPoorWash)
                    SideEffect { light = values }
                }
                RunmoniTheme(darkTheme = true) {
                    val values = listOf(RunmoniColors.Navy, RunmoniColors.Cream, RunmoniColors.Muted,
                        RunmoniColors.Paper, RunmoniColors.Primary, RunmoniColors.OnPrimary,
                        RunmoniColors.EnvironmentGood, RunmoniColors.EnvironmentGoodWash,
                        RunmoniColors.EnvironmentCaution, RunmoniColors.EnvironmentCautionWash,
                        RunmoniColors.EnvironmentPoor, RunmoniColors.EnvironmentPoorWash)
                    SideEffect { dark = values }
                }
            }
        }
        compose.waitForIdle()
        assertNotEquals(light!![1], dark!![1])
        for (values in listOf(light!!, dark!!)) {
            for ((foreground, background) in listOf(0 to 1, 2 to 1, 0 to 3, 2 to 3, 4 to 5, 6 to 7, 8 to 9, 10 to 11))
                assertTrue("semantic contrast $foreground/$background", contrast(values[foreground], values[background]) >= 4.5)
        }
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

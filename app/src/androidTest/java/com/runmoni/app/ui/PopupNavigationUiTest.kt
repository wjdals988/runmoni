package com.runmoni.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline ComponentActivity navigation fixture; no real auth, links or DB writes. */
class PopupNavigationUiTest : RunmoniDesignTestBase() {
    @Test fun closingInformationReturnsToTheSameSettingsScrollAndSelectedTab() {
        val state = mutableStateOf(AppState())
        compose.setContent { RunmoniApp(state.value, RunmoniUiTest.FakeActions(state), onExternalAppLink = {}) }
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("settings_app_information").performScrollTo().assertIsDisplayed()
        val settingsScroll = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
            hasAnyDescendant(hasTestTag("settings_app_information"))
        val before = compose.onNode(settingsScroll).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("Fixture must leave settings scrolled before opening information", before > 0f)
        compose.onNodeWithTag("settings_app_information").performClick()
        compose.onNodeWithTag("settings_screen").assertDoesNotExist()
        compose.onNodeWithTag("close_app_information").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        // No performScrollTo here: the original content position must survive.
        compose.onNodeWithTag("settings_app_information").assertIsDisplayed()
        val after = compose.onNode(settingsScroll).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals("Closing a child must keep the settings scroll", before, after, .5f)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("settings_screen").assertDoesNotExist()
        compose.onNodeWithTag("nav_me").assertIsSelected()
    }
}

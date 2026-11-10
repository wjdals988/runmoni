package com.runmoni.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.runmoni.app.data.MapTileResult
import com.runmoni.app.data.MapTileSource
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** ComponentActivity fixtures never fall back to the production tile client.
 * A test can still supply its own tile spy/fake in a nested provider.
 * Actual MainActivity startup tests keep their separate activity rule.
 */
class OfflineMapComposeRule(
    private val delegate: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity> =
        createAndroidComposeRule<ComponentActivity>(),
) : ComposeContentTestRule by delegate {
    val activity: ComponentActivity get() = delegate.activity
    val activityRule: ActivityScenarioRule<ComponentActivity> get() = delegate.activityRule

    fun runOnUiThread(action: () -> Unit) = delegate.runOnUiThread(action)

    override fun apply(base: Statement, description: Description): Statement = delegate.apply(base, description)

    override fun setContent(composable: @Composable () -> Unit) {
        delegate.setContent {
            CompositionLocalProvider(LocalMapTileSource provides offlineTiles) { composable() }
        }
    }

    private companion object {
        val offlineTiles = MapTileSource { MapTileResult() }
    }
}

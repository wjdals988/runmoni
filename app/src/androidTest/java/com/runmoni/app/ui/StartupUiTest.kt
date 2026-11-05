package com.runmoni.app.ui

import android.content.Intent
import android.content.ComponentName
import android.util.TypedValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runmoni.app.BuildConfig
import com.runmoni.app.MainActivity
import com.runmoni.app.R
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real MainActivity lifecycle checks, using the offline local debug app only. */
@RunWith(AndroidJUnit4::class)
class StartupUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    companion object {
        @BeforeClass @JvmStatic fun requireOfflineDebug() {
            check(!BuildConfig.FIREBASE_ENABLED) { "Run only the unconfigured local debug build" }
        }
    }

    private fun assertOrdinaryCanvas() {
        compose.runOnIdle {
            val color = TypedValue()
            assertTrue(compose.activity.theme.resolveAttribute(android.R.attr.windowBackground, color, true))
            assertTrue("The launch drawable must not remain as the Activity background",
                color.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT)
            assertEquals(compose.activity.getColor(R.color.runmoni_canvas), color.data)
        }
    }

    @Test fun launchAndRecreationRestoreTheAppThemeAndSelectedDestination() {
        compose.onNodeWithTag("app_wordmark").assertIsDisplayed()
        assertOrdinaryCanvas()
        compose.onNodeWithTag("nav_me").performClick().assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav_me").assertIsSelected()
        assertOrdinaryCanvas()
    }

    @Test fun returningToTheAppKeepsItsDestinationWithoutAnotherSplashActivity() {
        val activity = compose.activity
        val info = activity.packageManager.getActivityInfo(ComponentName(activity, MainActivity::class.java), 0)
        assertEquals(R.style.Theme_Runmoni_Launch, info.themeResource)
        val launchers = activity.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(activity.packageName), 0)
        assertEquals(listOf(MainActivity::class.java.name), launchers.map { it.activityInfo.name })
        compose.onNodeWithTag("nav_me").performClick()
        val task = activity.taskId
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("nav_me").assertIsSelected()
        assertEquals(task, compose.activity.taskId)
        assertOrdinaryCanvas()
    }
}

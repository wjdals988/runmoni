package com.runmoni.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.domain.ActiveRun
import com.runmoni.app.domain.AppActions
import com.runmoni.app.domain.AppEntryChoice
import com.runmoni.app.domain.AppEntryState
import com.runmoni.app.domain.AppState
import com.runmoni.app.domain.AvatarCatalogue
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.CloudAccountState
import com.runmoni.app.domain.Profile
import com.runmoni.app.domain.RunPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Offline UI contracts; callback fixtures do not authenticate or upload anything. */
class AppEntryUiTest : RunmoniDesignTestBase() {
    @Test fun googleClickDoesNotEnterUntilTheAuthoritativeChoiceCompletes() {
        val original = Profile(name = "시작 검토", mileage = 345,
            avatar = AvatarStyle(gender = AvatarGender.FEMALE),
            ownedItems = AvatarCatalogue.defaultOwned + "top:1")
        val state = mutableStateOf(AppState(profile = original, entry = AppEntryState(googleConfigured = true)))
        var googleCalls = 0
        var guestCalls = 0
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun startEntryGoogleSignIn() { googleCalls++ }
            override fun continueAsGuest() { guestCalls++ }
        }
        compose.setContent { RunmoniRoot(state.value, actions) }
        compose.onNodeWithText("런모니 시작하기").assertIsDisplayed()
        compose.onNodeWithTag("entry_google").assertIsEnabled().performClick()
        compose.onNodeWithTag("entry_guest").assertIsEnabled()
        compose.onNodeWithTag("nav_home").assertDoesNotExist()
        compose.onNodeWithTag("settings_button").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, googleCalls)
            assertEquals(0, guestCalls)
            assertEquals(original, state.value.profile)
            state.value = state.value.copy(entry = state.value.entry.copy(choice = AppEntryChoice.GOOGLE))
        }
        compose.onNodeWithTag("app_entry_screen").assertDoesNotExist()
        compose.onNodeWithTag("nav_home").assertIsDisplayed()
        compose.runOnIdle { assertEquals(original, state.value.profile) }
    }

    @Test fun busyGoogleKeepsGuestAvailableAndFailureRemainsRetryable() {
        val state = mutableStateOf(AppState(entry = AppEntryState(googleConfigured = true, googleBusy = true)))
        var googleCalls = 0
        var guestCalls = 0
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun startEntryGoogleSignIn() { googleCalls++ }
            override fun continueAsGuest() { guestCalls++ }
        }
        compose.setContent { RunmoniRoot(state.value, actions) }
        compose.onNodeWithTag("entry_google").assertIsNotEnabled()
        compose.onNodeWithTag("entry_busy").assertTextEquals("Google 로그인 중")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("entry_guest").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("nav_home").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, guestCalls)
            assertEquals(0, googleCalls)
            state.value = state.value.copy(entry = state.value.entry.copy(googleBusy = false,
                issue = "Google 로그인에 실패했어요. 다시 시도해 주세요."))
        }
        compose.onNodeWithTag("entry_issue").performScrollTo()
            .assertTextEquals("Google 로그인에 실패했어요. 다시 시도해 주세요.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("entry_busy").assertDoesNotExist()
        compose.onNodeWithTag("entry_google").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("entry_guest").assertIsEnabled()
        compose.onNodeWithTag("nav_home").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, googleCalls)
            state.value = state.value.copy(entry = AppEntryState(choice = AppEntryChoice.GUEST))
        }
        compose.onNodeWithTag("app_entry_screen").assertDoesNotExist()
        compose.onNodeWithTag("nav_home").assertIsDisplayed()
    }

    @Test fun unconfiguredGoogleDoesNotBlockGuestAndSavingPreventsDuplicateChoices() {
        val state = mutableStateOf(AppState(entry = AppEntryState()))
        var guestCalls = 0
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun continueAsGuest() {
                guestCalls++
                state.value = state.value.copy(entry = state.value.entry.copy(savingChoice = true))
            }
        }
        compose.setContent { RunmoniRoot(state.value, actions) }
        compose.onNodeWithTag("entry_google").assertIsNotEnabled()
        compose.onNodeWithTag("entry_google_unavailable").assertTextEquals("현재 Google 로그인을 사용할 수 없어요")
        compose.onNodeWithTag("entry_guest").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("entry_google").assertIsNotEnabled()
        compose.onNodeWithTag("entry_guest").assertIsNotEnabled()
        compose.onNodeWithTag("entry_busy").assertTextEquals("시작하는 중")
        compose.onNodeWithTag("nav_home").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, guestCalls)
            state.value = state.value.copy(entry = AppEntryState(choice = AppEntryChoice.GUEST))
        }
        compose.onNodeWithTag("app_entry_screen").assertDoesNotExist()
        compose.onNodeWithTag("nav_home").assertIsDisplayed()
    }

    @Test fun returningOfflineUsersAndAnActiveRunDoNotWaitForEntryOrCloud() {
        val original = Profile(name = "기존 기록", mileage = 678, ownedItems = AvatarCatalogue.defaultOwned + "shoes:1")
        val state = mutableStateOf(AppState(profile = original,
            entry = AppEntryState(choice = AppEntryChoice.EXISTING),
            cloud = CloudAccountState(configured = false, busy = true), signedIn = false, serverConnected = false))
        var entryCalls = 0
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun startEntryGoogleSignIn() { entryCalls++ }
            override fun continueAsGuest() { entryCalls++ }
        }
        compose.setContent { RunmoniRoot(state.value, actions) }
        for (choice in listOf(AppEntryChoice.EXISTING, AppEntryChoice.GUEST, AppEntryChoice.GOOGLE)) {
            compose.runOnIdle { state.value = state.value.copy(entry = AppEntryState(choice = choice)) }
            compose.onNodeWithTag("app_entry_screen").assertDoesNotExist()
            compose.onNodeWithTag("nav_home").assertIsDisplayed()
        }
        for (phase in listOf(RunPhase.RUNNING, RunPhase.PAUSED, RunPhase.RECOVERY)) {
            compose.runOnIdle {
                state.value = state.value.copy(entry = AppEntryState(),
                    activeRun = ActiveRun(id = "entry-active-fixture", phase = phase))
            }
            compose.onNodeWithTag("app_entry_screen").assertDoesNotExist()
            compose.onNodeWithTag("nav_run").performClick().assertIsDisplayed()
        }
        compose.runOnIdle {
            assertEquals(0, entryCalls)
            assertEquals(original, state.value.profile)
        }
    }
}

/** Run separately at actual system width 320dp/fontScale 2; this is a ComponentActivity fixture. */
class AppEntryNativeUiTest : RunmoniDesignTestBase() {
    @Test fun bothChoicesRemainReachableAboveSystemNavigationAtLargeFont() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assertTrue("Run this pass with actual system font_scale=2", config.fontScale >= 1.9f)
        assertTrue("Run this pass at actual screen width 320dp", config.screenWidthDp in 315..325)
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        val original = Profile(name = "큰 글씨 검토", mileage = 123)
        val state = mutableStateOf(AppState(profile = original, entry = AppEntryState(googleConfigured = true)))
        var googleCalls = 0
        var guestCalls = 0
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun startEntryGoogleSignIn() { googleCalls++ }
            override fun continueAsGuest() { guestCalls++ }
        }
        compose.setContent { RunmoniRoot(state.value, actions) }
        compose.onNodeWithTag("entry_google").performScrollTo()
        assertEntryInsideSafeArea(compose, "entry_google")
        compose.onNodeWithTag("entry_google").performClick()
        compose.onNodeWithTag("entry_guest").performScrollTo()
        assertEntryInsideSafeArea(compose, "entry_guest")
        compose.onNodeWithTag("entry_guest").performClick()
        compose.onNodeWithTag("entry_scope").performScrollTo().assertIsDisplayed()
        assertEntryInsideSafeArea(compose, "entry_scope", isAction = false)
        noTextOverflow("native entry 320dp / fontScale 2")
        capture("entry-native-320-font2-fixture")
        captureDevice("entry-native-320-font2-device")
        compose.onNodeWithTag("nav_home").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, googleCalls)
            assertEquals(1, guestCalls)
            assertEquals(original, state.value.profile)
        }
    }
}

/** Uses full physical bounds so a partially clipped action cannot pass as displayed. */
private fun assertEntryInsideSafeArea(
    compose: AndroidComposeTestRule<*, ComponentActivity>,
    tag: String,
    isAction: Boolean = true,
) {
    val interaction = compose.onNodeWithTag(tag).assertIsDisplayed()
    if (isAction) interaction.assertHeightIsAtLeast(48.dp)
    val target = interaction.fetchSemanticsNode()
    val safe = compose.onNodeWithTag("entry_safe_content").fetchSemanticsNode()
    val top = target.positionInWindow.y
    val bottom = top + target.size.height
    val left = target.positionInWindow.x
    val right = left + target.size.width
    val safeTop = safe.positionInWindow.y
    val safeBottom = safeTop + safe.size.height
    assertTrue("Full $tag must remain below safe top: $top / $safeTop", top + .5f >= safeTop)
    assertTrue("Full $tag must remain above safe bottom: $bottom / $safeBottom", bottom <= safeBottom + .5f)
    val decor = compose.activity.window.decorView
    val insets = ViewCompat.getRootWindowInsets(decor)
    assertNotNull("Actual Android window insets are required", insets)
    val bars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars() or
        WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
    assertTrue("This pass must include the actual navigation inset", bars.bottom > 0)
    val navigationTop = decor.height - bars.bottom
    assertTrue("Full $tag must remain below actual status bar: $top / ${bars.top}", top + .5f >= bars.top)
    assertTrue("Full $tag must remain above actual navigation: $bottom / $navigationTop", bottom <= navigationTop + .5f)
    assertTrue("Full $tag must fit actual horizontal insets", left + .5f >= bars.left && right <= decor.width - bars.right + .5f)
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    File(targetContext.cacheDir, "design-review").apply { mkdirs() }.resolve("entry-window-bounds.txt")
        .appendText("$tag topPx=$top bottomPx=$bottom safeTopPx=$safeTop safeBottomPx=$safeBottom navigationTopPx=$navigationTop bottomInsetPx=${bars.bottom}\n")
}

package com.runmoni.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.BuildConfig
import com.runmoni.app.domain.AppRelease
import com.runmoni.app.domain.AppState
import com.runmoni.app.domain.AppActions
import com.runmoni.app.domain.Crew
import org.junit.Assert.*
import org.junit.Test
import java.io.File

private fun newerReleaseFixture(): AppUpdateUiState {
    val version = "99.0.0-dev"
    return AppUpdateUiState(AppRelease(version, BuildConfig.VERSION_CODE.toLong() + 1,
        "runmoni-$version.apk", 200_000_000, "a".repeat(64), AppRelease.RELEASE_CERTIFICATE,
        true, "com.runmoni.app", "${AppRelease.REPOSITORY}/releases/download/v$version/runmoni-$version.apk",
        "${AppRelease.REPOSITORY}/releases/tag/v$version", "업데이트 알림을 검증하는 합성 데이터입니다."))
}

/** Bounds and full glyph layout, not merely the presence of a red-dot node. */
private fun assertVersionIndicatorClearance(compose: ComposeContentTestRule, direction: LayoutDirection, settings: Boolean = false) {
    val label = compose.onNodeWithTag("top_app_version_label", useUnmergedTree = true)
    val layouts = mutableListOf<TextLayoutResult>()
    label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    val layout = layouts.single()
    // As in noTextOverflow(), paragraph width can include unused layout space.
    // Audit actual glyph lines, retaining the existing one-physical-pixel
    // rounding tolerance and rejecting every max-line truncation.
    val left = layout.getLineLeft(0)
    val right = layout.getLineRight(0)
    val bottom = layout.getLineBottom(0)
    val target = InstrumentationRegistry.getInstrumentation().targetContext
    File(target.cacheDir, "design-review").apply { mkdirs() }
        .resolve("update-indicator-layout.jsonl").appendText(org.json.JSONObject()
            .put("direction", direction.name).put("fontScale", layout.layoutInput.density.fontScale)
            .put("sizeWidthPx", layout.size.width).put("sizeHeightPx", layout.size.height)
            .put("paragraphWidthPx", layout.multiParagraph.width).put("rawHasVisualOverflow", layout.hasVisualOverflow)
            .put("lineLeftPx", left).put("lineRightPx", right).put("lineBottomPx", bottom)
            .put("maxLinesExceeded", layout.multiParagraph.didExceedMaxLines).toString() + "\n")
    assertTrue("Installed version must remain readable: lines=$left..$right/$bottom; size=${layout.size}",
        left >= -1f && right <= layout.size.width + 1f && bottom <= layout.size.height + 1f && !layout.multiParagraph.didExceedMaxLines)
    assertEquals(1, layout.lineCount)
    val text = label.fetchSemanticsNode().boundsInRoot
    val dot = compose.onNodeWithTag("top_app_version_red_dot", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    val action = compose.onNodeWithTag("top_app_version").assertHeightIsAtLeast(48.dp)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "새 버전 업데이트 가능"))
        .fetchSemanticsNode().boundsInRoot
    val minimumGap = 4f * layout.layoutInput.density.density
    assertTrue("Dot needs visible space beside the version", if (direction == LayoutDirection.Ltr)
        dot.left - text.right >= minimumGap else text.left - dot.right >= minimumGap)
    assertTrue("Entire dot stays inside its own action", dot.left >= action.left && dot.top >= action.top && dot.right <= action.right && dot.bottom <= action.bottom)
    if (settings) {
        val gear = compose.onNodeWithTag("settings_button").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertFalse("Version action must not overlap settings", action.overlaps(gear))
        assertFalse("Dot must not overlap settings", dot.overlaps(gear))
    }
}

class AppVersionShortcutLayoutTest : RunmoniDesignTestBase() {
    @Test fun updateIndicatorKeepsItsOwnSpaceInBothDirectionsAndLargeText() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val scale = mutableStateOf(1f)
        val updates = mutableStateOf(newerReleaseFixture())
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale.value), LocalLayoutDirection provides direction.value) {
                RunmoniTheme { AppVersionShortcut(updates.value) {} }
            }
        }
        for (readingDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) for (fontScale in listOf(1f, 2f)) {
            compose.runOnIdle { direction.value = readingDirection; scale.value = fontScale; updates.value = newerReleaseFixture() }
            assertVersionIndicatorClearance(compose, readingDirection)
            compose.runOnIdle { updates.value = AppUpdateUiState() }
            compose.onNodeWithTag("top_app_version_red_dot", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("top_app_version").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        }
    }
}

class AppInformationTest : RunmoniDesignTestBase() {
    @Test fun topVersionOpensConfirmationAndDownloadsOnlyAfterExplicitChoice() {
        val state = mutableStateOf(AppState())
        val links = mutableListOf<String>()
        val fixture = newerReleaseFixture()
        compose.setContent { RunmoniApp(state.value, RunmoniUiTest.FakeActions(state), fixture, onExternalAppLink = { links += it }) }
        compose.onNodeWithTag("top_app_version").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("v${BuildConfig.VERSION_NAME}").assertExists()
        compose.onNodeWithTag("top_app_version_red_dot", useUnmergedTree = true).assertExists()
        captureDevice("version-shortcut-home-fixture")
        compose.onNodeWithTag("top_app_version").performClick()
        compose.onNodeWithTag("confirm_download_update").assertIsDisplayed()
        compose.runOnIdle { assertTrue(links.isEmpty()) }
        captureDevice("version-shortcut-confirmation-fixture")
        compose.onNodeWithTag("cancel_app_update").performClick()
        compose.onNodeWithTag("close_app_information").performClick()
        compose.onNodeWithTag("top_app_version_red_dot", useUnmergedTree = true).assertExists()
        compose.runOnIdle { assertTrue(links.isEmpty()) }
        compose.onNodeWithTag("top_app_version").performClick()
        compose.onNodeWithTag("confirm_download_update").performClick()
        compose.runOnIdle { assertEquals(listOf(fixture.release!!.downloadUrl), links) }
        noTextOverflow("version shortcut and update prompt")
    }

    @Test fun topVersionWithoutUpdateShowsInformationAndHistory() {
        val state = mutableStateOf(AppState())
        compose.setContent { RunmoniApp(state.value, RunmoniUiTest.FakeActions(state)) }
        compose.onNodeWithTag("top_app_version").performClick()
        compose.onNodeWithTag("confirm_download_update").assertDoesNotExist()
        compose.onNodeWithTag("open_version_history").performScrollTo().performClick()
        compose.onNodeWithText("현재 ${BuildConfig.VERSION_NAME} · 빌드 ${BuildConfig.VERSION_CODE}").assertIsDisplayed()
    }

    @Test fun topRightUpdateDotLeadsToVisibleSettingsNoticeAndConfirmation() {
        val state = mutableStateOf(AppState())
        compose.setContent { RunmoniApp(state.value, RunmoniUiTest.FakeActions(state), newerReleaseFixture()) }
        compose.onNodeWithTag("top_app_version_red_dot", useUnmergedTree = true).assertExists()
        captureDevice("home-update-dot-fixture-not-a-published-release")
        // The same global entry point must work from every tab.
        listOf("explore", "run", "crew", "me", "home").forEach {
            compose.onNodeWithTag("nav_$it").performClick()
            assertVersionIndicatorClearance(compose, LayoutDirection.Ltr, settings = true)
        }
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("settings_app_update").assertIsDisplayed()
        captureDevice("settings-update-notice-fixture-not-a-published-release")
        compose.onNodeWithTag("settings_app_update").performClick()
        compose.onNodeWithTag("confirm_download_update").assertIsDisplayed()
        compose.onNodeWithTag("cancel_app_update").performClick()
        compose.onNodeWithTag("close_app_information").performClick()
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("top_app_version_red_dot", useUnmergedTree = true).assertExists()
    }

    @Test fun topRightDotRequiresATrustedNewerReleaseAndTracksStateChanges() {
        val state = mutableStateOf(AppState())
        val updates = mutableStateOf(AppUpdateUiState())
        compose.setContent { RunmoniApp(state.value, RunmoniUiTest.FakeActions(state), updates.value) }
        fun noDot() = compose.onNodeWithTag("top_app_version_red_dot", useUnmergedTree = true).assertDoesNotExist()
        noDot()
        val trusted = newerReleaseFixture().release!!
        for (release in listOf(trusted.copy(versionCode = BuildConfig.VERSION_CODE.toLong()),
            trusted.copy(versionCode = 1), trusted.copy(certificateSha256 = "b".repeat(64)))) {
            compose.runOnIdle { updates.value = AppUpdateUiState(release = release) }; noDot()
        }
        compose.runOnIdle { updates.value = AppUpdateUiState(error = "연결 후 다시 확인해 주세요") }; noDot()
        // A last verified newer release stays useful when rechecking fails.
        compose.runOnIdle { updates.value = AppUpdateUiState(release = trusted, error = "다시 확인하지 못했어요") }
        compose.onNodeWithTag("top_app_version_red_dot", useUnmergedTree = true).assertExists()
        compose.runOnIdle { updates.value = AppUpdateUiState(release = trusted.copy(versionCode = BuildConfig.VERSION_CODE.toLong())) }; noDot()
    }

    @Test fun fullScreenChatKeepsCrewSelectionAndHidesBackgroundControls() {
        val state = mutableStateOf(AppState(signedIn = true, serverConnected = true, crews = listOf(
            Crew("first", "첫 크루", "", joined = true, isOwner = true),
            Crew("second", "둘째 크루", "", joined = true, isOwner = true),
        )))
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun openChat(roomId: String, title: String) { state.value = state.value.copy(openedRoomId = roomId, openedRoomTitle = title) }
            override fun closeChat() { state.value = state.value.copy(openedRoomId = "", openedRoomTitle = "") }
        }
        compose.setContent { RunmoniApp(state.value, actions) }
        compose.onNodeWithTag("nav_crew").performClick()
        compose.onNodeWithTag("crew_second").performScrollTo().performClick()
        compose.onNodeWithTag("create_party_second").assertIsDisplayed()
        compose.onNodeWithTag("crew_detail_1").performScrollTo().performClick()
        compose.onNodeWithText("크루 공지 · 채팅").performScrollTo().performClick()
        compose.onNodeWithTag("chat_dialog").assertIsDisplayed()
        compose.runOnIdle { assertEquals("crew:second", state.value.openedRoomId) }
        compose.onNodeWithTag("nav_crew").assertDoesNotExist()
        compose.onNodeWithTag("close_chat").performClick()
        compose.onNodeWithTag("crew_detail_1").assertIsSelected()
        compose.onNodeWithTag("crew_detail_0").performScrollTo().performClick()
        compose.onNodeWithTag("create_party_second").assertIsDisplayed()
        compose.onNodeWithTag("create_party_first").assertDoesNotExist()
        compose.onNodeWithTag("nav_crew").assertDoesNotExist()
        compose.onNodeWithTag("back_to_rooms").performClick()
        compose.onNodeWithTag("nav_crew").assertIsSelected()
    }

    @Test fun downloadNeedsConfirmationAndLaunchesOnlyThePinnedApkLinkOnce() {
        val links = mutableListOf<String>()
        var checks = 0
        val fixture = newerReleaseFixture()
        compose.setContent { RunmoniTheme { AppInformationDialog(fixture, { checks++ }, {}, onExternalLink = { links += it }) } }
        compose.onNodeWithTag("recheck_app_update").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, checks); assertTrue(links.isEmpty()) }
        compose.onNodeWithTag("about_app_version").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(links.isEmpty()) }
        compose.onNodeWithTag("cancel_app_update").performClick()
        compose.runOnIdle { assertTrue(links.isEmpty()) }
        compose.onNodeWithTag("about_app_version").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_download_update").performClick()
        compose.runOnIdle { assertEquals(listOf(fixture.release!!.downloadUrl), links) }
    }

    @Test fun aboutShowsInstalledVersionHistoryLinksLicensesAndHonestFailure() {
        val updates = mutableStateOf(AppUpdateUiState())
        var checks = 0
        compose.setContent { RunmoniTheme { AppInformationDialog(updates.value, { checks++ }, {}) } }
        val packageInfo = InstrumentationRegistry.getInstrumentation().targetContext.let { it.packageManager.getPackageInfo(it.packageName, 0) }
        compose.onNodeWithText("버전 ${packageInfo.versionName}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("빌드 ${packageInfo.longVersionCode}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("about_app_version").assertIsDisplayed()
        compose.onNodeWithTag("about_app_version_red_dot").assertDoesNotExist()
        compose.onNodeWithTag("check_app_update").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, checks); updates.value = AppUpdateUiState(error = "연결 후 다시 확인해 주세요") }
        compose.onAllNodesWithText("연결 후 다시 확인해 주세요").fetchSemanticsNodes().also { assertTrue(it.isNotEmpty()) }
        compose.onNodeWithTag("open_version_history").performScrollTo().performClick()
        compose.onNodeWithText("0.1.1-dev", substring = false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("close_version_history").performClick()
        listOf("link_dashboard", "link_github", "link_issue").forEach { compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithTag("open_licenses").performScrollTo().performClick()
        compose.onNodeWithTag("license_entry_0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("close_licenses").performClick()
        noTextOverflow("about information")
        capture("about-information-fixture")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 256, 256); icon.draw(android.graphics.Canvas(bitmap))
        val directory = File(context.cacheDir, "design-review").apply { mkdirs() }
        File(directory, "launcher-icon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun newerVersionBadgeOpensConfirmationAndCanBeDeclined() {
        val state = mutableStateOf(AppState())
        compose.setContent { RunmoniApp(state.value, RunmoniUiTest.FakeActions(state), newerReleaseFixture()) }
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("app_version").performScrollTo()
        compose.onNodeWithTag("app_version_red_dot", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("app_version").performClick()
        compose.onNodeWithTag("confirm_download_update").assertIsDisplayed()
        captureDevice("update-prompt-fixture-not-a-published-release")
        compose.onNodeWithTag("cancel_app_update").performClick()
        compose.onNodeWithTag("confirm_download_update").assertDoesNotExist()
        compose.onNodeWithTag("close_app_information").performClick()
        compose.onNodeWithTag("open_mileage_help").performScrollTo().performClick()
        compose.onNodeWithText("1km → 100P", substring = true).performScrollTo().assertIsDisplayed()
        capture("mileage-explanation")
    }
}

/** Must run in the native 320dp/font2 pass, not a local density override. */
class AppInformationNativeTest : RunmoniDesignTestBase() {
    @Test fun enlargedHeaderIndicatorStaysSeparateAndOpensTheUpdatePrompt() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assertTrue(config.screenWidthDp in 315..325 && config.fontScale >= 1.9f)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val state = mutableStateOf(AppState())
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                RunmoniApp(state.value, RunmoniUiTest.FakeActions(state), newerReleaseFixture())
            }
        }
        for (readingDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { direction.value = readingDirection }
            assertVersionIndicatorClearance(compose, readingDirection, settings = true)
            captureDevice("update-header-native-320-font2-${readingDirection.name.lowercase()}-fixture")
        }
        compose.onNodeWithTag("top_app_version").performClick()
        compose.onNodeWithTag("confirm_download_update").assertIsDisplayed()
        compose.onNodeWithTag("cancel_app_update").performClick()
        compose.onNodeWithTag("close_app_information").performClick()
        assertVersionIndicatorClearance(compose, LayoutDirection.Rtl, settings = true)
    }

    @Test fun largeAboutAndUpdateConfirmationRemainReadableAndScrollable() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assertTrue(config.screenWidthDp in 315..325 && config.fontScale >= 1.9f)
        compose.setContent { RunmoniTheme { AppInformationDialog(newerReleaseFixture(), {}, {}) } }
        listOf("link_dashboard", "link_github", "open_licenses", "link_issue").forEach { compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed() }
        noTextOverflow("native large app information")
        capture("about-320-200")
        compose.onNodeWithTag("about_app_version").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_download_update").assertIsDisplayed()
        captureDevice("update-large-fixture-not-a-published-release")
        compose.onNodeWithTag("cancel_app_update").performClick()
    }
}

package com.runmoni.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
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

class AppInformationTest : RunmoniDesignTestBase() {
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
        compose.onNodeWithText("공지 · 채팅").performClick()
        compose.onNodeWithTag("chat_dialog").assertIsDisplayed()
        compose.onNodeWithTag("nav_crew").assertDoesNotExist()
        compose.onNodeWithTag("close_chat").performClick()
        compose.onNodeWithTag("create_party_second").assertIsDisplayed()
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
        compose.onNodeWithText("설치 버전 ${packageInfo.versionName} · 빌드 ${packageInfo.longVersionCode}").performScrollTo().assertIsDisplayed()
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

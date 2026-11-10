package com.runmoni.app.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.runmoni.app.data.MapTileResult
import com.runmoni.app.data.MapTileSource
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Synthetic current-day data for navigation only, never GPS or a DB write. */
private fun navigationRecord(): RunRecord {
    val now = System.currentTimeMillis()
    val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val started = maxOf(today, now - 600_000)
    return RunRecord("record-navigation-fixture", startedAtMillis = started, finishedAtMillis = now,
        distanceMeters = 1234.0, activeDurationMillis = now - started, elapsedDurationMillis = now - started)
}

@Composable
private fun RecordNavigationFixture(content: @Composable () -> Unit) {
    val unavailable = remember { MapTileSource { MapTileResult() } }
    CompositionLocalProvider(LocalMapTileSource provides unavailable, content = content)
}

/** Root Compose navigation with fake callbacks and no online tiles, auth, GPS or DB writes. */
class RunRecordNavigationUiTest : RunmoniDesignTestBase() {
    @Test fun detailReplacesMainNavigationAndBothBackActionsPreserveTheProfilePeriod() {
        val record = navigationRecord()
        val profile = Profile(name = "기록 검토", mileage = 777)
        val state = mutableStateOf(AppState(profile = profile, records = listOf(record),
            runPersistence = RunPersistenceState(initialized = true)))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { RecordNavigationFixture { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("history_period_1").performScrollTo().performClick()
        compose.onNodeWithTag("record_${record.id}").performScrollTo().performClick()
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
        listOf("home", "explore", "run", "crew", "me").forEach {
            compose.onNodeWithTag("nav_$it").assertDoesNotExist()
        }
        compose.onNodeWithTag("settings_button").assertDoesNotExist()
        captureDevice("root-record-detail-no-main-navigation-fixture")
        compose.onNodeWithTag("close_record").performClick()
        compose.onNodeWithTag("record_detail_page").assertDoesNotExist()
        compose.onNodeWithTag("nav_me").assertIsSelected()
        compose.onNodeWithTag("history_period_1").performScrollTo().assertIsSelected()

        compose.onNodeWithTag("record_${record.id}").performScrollTo().performClick()
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
        compose.waitUntil(30_000) { compose.activity.hasWindowFocus() }
        assertTrue("Native Back requires the actual app window focus", compose.activity.hasWindowFocus())
        assertTrue("Actual Back key injection", UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .pressKeyCode(KeyEvent.KEYCODE_BACK))
        compose.waitForIdle()
        compose.onNodeWithTag("record_detail_page").assertDoesNotExist()
        compose.onNodeWithTag("nav_me").assertIsSelected()
        compose.onNodeWithTag("history_period_1").performScrollTo().assertIsSelected()
        compose.runOnIdle { assertEquals(profile, state.value.profile); assertEquals(listOf(record), state.value.records) }
    }

    @Test fun failedDeleteKeepsDetailAndOnlyCompletedActualRemovalReturnsToTheList() {
        val record = navigationRecord()
        val state = mutableStateOf(AppState(records = listOf(record), runPersistence = RunPersistenceState(initialized = true)))
        val requested = mutableListOf<String>()
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun deleteRun(id: String) {
                requested += id
                // Explicitly controlled asynchronous fixture: deleting is not
                // a successful list update until the test supplies that update.
                state.value = state.value.copy(busy = true)
            }
        }
        compose.setContent { RecordNavigationFixture { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_me").performClick()
        compose.onNodeWithTag("record_${record.id}").performScrollTo().performClick()
        compose.onNodeWithTag("delete_run").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_delete_run").performClick()
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
        compose.onNodeWithTag("delete_run").performScrollTo().assertIsNotEnabled()
        val failure = "기기 기록을 삭제하지 못했어요. 다시 시도해 주세요."
        compose.runOnIdle { state.value = state.value.copy(busy = false, error = failure) }
        compose.onNodeWithText(failure).assertIsDisplayed()
        compose.onNodeWithText("확인", substring = false).performClick()
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
        compose.onNodeWithTag("nav_me").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(record), state.value.records); assertEquals(listOf(record.id), requested) }

        compose.onNodeWithTag("delete_run").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_delete_run").performClick()
        compose.runOnIdle { state.value = state.value.copy(records = emptyList()) }
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
        // An empty unavailable/uninitialized list is not deletion success.
        compose.runOnIdle { state.value = state.value.copy(busy = false,
            runPersistence = state.value.runPersistence.copy(historyUnavailable = true)) }
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(
            runPersistence = state.value.runPersistence.copy(initialized = false, historyUnavailable = false)) }
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
        // Preserve the last rendered entity until canonical history has loaded
        // successfully and the actual removal has settled.
        compose.runOnIdle { state.value = state.value.copy(
            runPersistence = state.value.runPersistence.copy(initialized = true)) }
        compose.onNodeWithTag("record_detail_page").assertDoesNotExist()
        compose.onNodeWithTag("nav_me").assertIsSelected()
        compose.onNodeWithTag("record_${record.id}").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(record.id, record.id), requested); assertTrue(state.value.records.isEmpty()) }
    }
}

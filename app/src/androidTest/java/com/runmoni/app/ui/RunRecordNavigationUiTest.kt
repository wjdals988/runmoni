package com.runmoni.app.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.runmoni.app.data.MapTileResult
import com.runmoni.app.data.MapTileSource
import com.runmoni.app.domain.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

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
    @Test fun savedTrainingOpensPreparationWithOnlyItsTrainingAndDoesNotStartTheRecorder() {
        val goal = RunGoal(RunGoalKind.DISTANCE, 5)
        val interval = RunIntervalPlan(45, 60, 4, vibration = true)
        val record = navigationRecord().copy(goal = goal, interval = interval)
        val original = AppState(records = listOf(record), voiceGuidance = false, autoPause = true)
        val state = mutableStateOf(original)
        val delegate = RunmoniUiTest.FakeActions(state)
        val actions = object : AppActions by delegate {
            override suspend fun prepareTrainingAgain(record: RunRecord, isCurrent: () -> Boolean): Boolean {
                if (!isCurrent() || !RunTrainingPreparation.available(state.value, record)) return false
                state.value = RunTrainingPreparation.applyTo(state.value, record)
                return true
            }
        }
        compose.setContent { ViewportBox(Viewport(320, 2f)) {
            RecordNavigationFixture { RunmoniApp(state.value, actions) }
        } }
        compose.onNodeWithTag("record_${record.id}").performScrollTo().performClick()
        compose.onNodeWithTag("record_prepare_again").performScrollTo().assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("record_detail_page").assertDoesNotExist()
        compose.onNodeWithTag("nav_run").assertIsSelected()
        compose.onNodeWithTag("choose_run_goal").performScrollTo().assertTextContains("목표 5 km 변경")
        compose.onNodeWithTag("choose_run_interval").performScrollTo().assertTextContains("인터벌 변경")
        compose.onNodeWithText("달리기 00:45 · 회복 01:00 · 4회").assertExists()
        compose.onNodeWithTag("start_run").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle {
            assertEquals(0, delegate.starts)
            assertEquals(original.copy(plannedRunGoal = goal, plannedRunInterval = interval.copy(vibration = false)), state.value)
        }
    }

    @Test fun closedAndReopenedSameRecordRejectsOldClickAndPendingPreparation() {
        val record = navigationRecord().copy(goal = RunGoal(RunGoalKind.DURATION, 20))
        val original = AppState(records = listOf(record))
        val state = mutableStateOf(original)
        val reached = AtomicInteger()
        val verify = CompletableDeferred<Unit>()
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override suspend fun prepareTrainingAgain(record: RunRecord, isCurrent: () -> Boolean): Boolean {
                reached.incrementAndGet()
                verify.await()
                if (!isCurrent() || !RunTrainingPreparation.available(state.value, record)) return false
                state.value = RunTrainingPreparation.applyTo(state.value, record)
                return true
            }
        }
        compose.setContent { RecordNavigationFixture { RunmoniApp(state.value, actions) } }
        val savedRow = compose.onNodeWithTag("record_${record.id}")
        savedRow.performScrollTo().performClick()
        val shortcut = compose.onNodeWithTag("record_prepare_again").performScrollTo()
        val oldPlanClick = shortcut.fetchSemanticsNode().config[RecordActionSnapshot]
        compose.runOnIdle { state.value = original.copy(records = listOf(record.copy(goal = RunGoal(RunGoalKind.DURATION, 30)))) }
        shortcut.assertIsDisplayed()
        compose.runOnIdle { oldPlanClick(); assertEquals(0, reached.get()) }
        compose.runOnIdle { state.value = original }
        val delayedClick = shortcut.fetchSemanticsNode().config[RecordActionSnapshot]
        shortcut.performClick()
        compose.waitUntil(5_000) { reached.get() == 1 }
        compose.onNodeWithTag("close_record").performClick()
        savedRow.performScrollTo().performClick()
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
        compose.runOnIdle { delayedClick(); verify.complete(Unit) }
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, reached.get()); assertEquals(original, state.value) }

        compose.runOnIdle { state.value = original.copy(activeRun = ActiveRun("unfinished", RunPhase.RECOVERY)) }
        compose.onNodeWithTag("record_prepare_again").assertDoesNotExist()
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
    }

    @Test fun profileHistoryShortcutScrollsWithinTheProfileAndPreservesPeriodAndReturnPosition() {
        val record = navigationRecord()
        val state = mutableStateOf(AppState(records = listOf(record), runPersistence = RunPersistenceState(initialized = true)))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { ViewportBox(Viewport(320, 2f)) {
            RecordNavigationFixture { RunmoniApp(state.value, actions) }
        } }
        compose.onNodeWithTag("nav_me").performClick()
        val shortcut = compose.onNodeWithTag("profile_history_shortcut")
        val heading = compose.onNodeWithTag("profile_history_heading")
        shortcut.performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        heading.assertIsDisplayed()
        compose.onNodeWithTag("history_period_1").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("history_previous").assertIsDisplayed()
        compose.onNodeWithTag("history_period_2").performScrollTo().performClick()
        compose.onNodeWithTag("history_previous").performScrollTo().performClick()
        shortcut.performScrollTo().performClick()
        heading.assertIsDisplayed()
        compose.onNodeWithTag("history_period_2").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("${LocalDate.now().year - 1}년").assertExists()
        compose.onNodeWithTag("history_next").performScrollTo().performClick()
        val recordRow = compose.onNodeWithTag("record_${record.id}").performScrollTo().assertIsDisplayed()
        fun scrollOffset() = compose.onNodeWithTag("screen_scroll").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        val originalOffset = scrollOffset()
        recordRow.performClick()
        compose.onNodeWithTag("close_record").performClick()
        compose.onNodeWithTag("nav_me").assertIsSelected()
        compose.onNodeWithTag("history_period_2").assertIsSelected()
        assertEquals(originalOffset, scrollOffset(), 1f)
    }

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

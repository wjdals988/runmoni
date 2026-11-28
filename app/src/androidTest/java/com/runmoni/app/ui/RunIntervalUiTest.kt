package com.runmoni.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class RunIntervalUiTest : RunmoniDesignTestBase() {
    @Test fun intervalSetupShowsRecoveryAndRecordsOnlyStartedStagesAtLargeText() {
        val state = mutableStateOf(AppState())
        val delegate = RunmoniUiTest.FakeActions(state)
        val actions = object : AppActions by delegate {
            override fun setRunInterval(interval: RunIntervalPlan?) {
                state.value = state.value.copy(plannedRunInterval = interval)
            }

            override fun startRun() {
                val plan = state.value.plannedRunInterval
                delegate.startRun()
                state.value = state.value.copy(plannedRunInterval = null,
                    activeRun = state.value.activeRun.copy(interval = plan, activeDurationMillis = 12_500))
            }

            override fun finishRun(expectedRunId: String) {
                val run = state.value.activeRun
                assertEquals(run.id, expectedRunId)
                val record = RunRecord(run.id, run.startedAtMillis, System.currentTimeMillis(),
                    activeDurationMillis = run.activeDurationMillis, interval = run.interval)
                state.value = state.value.copy(activeRun = ActiveRun(), records = listOf(record))
            }
        }
        compose.setContent { ViewportBox(Viewport(320, 2f)) {
            Box(Modifier.height(520.dp)) { RunmoniApp(state.value, actions) }
        } }
        compose.onNodeWithTag("nav_run").performClick()
        compose.onNodeWithTag("choose_run_interval").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        val intervalRun = compose.onNodeWithTag("run_interval_run")
        intervalRun.performScrollTo().performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("9")) }
        compose.onNodeWithTag("run_interval_confirm").assertIsNotEnabled()
        intervalRun.performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("10")) }
        capture("run-interval-setup-320-font2-fixture")
        compose.onNodeWithTag("run_interval_confirm").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(RunIntervalPlan(10, 60, 6), state.value.plannedRunInterval) }

        compose.onNodeWithTag("start_run").assertIsDisplayed().performClick()
        compose.onNodeWithTag("active_run_interval_stage").performScrollTo().assertTextEquals("회복 · 1/6회")
        compose.onNodeWithTag("active_run_interval_remaining").performScrollTo().assertTextEquals("남은 시간 00:58")
        compose.onNodeWithTag("pause_run").assertIsDisplayed().performClick()
        compose.onNodeWithTag("active_run_interval_remaining").assertTextEquals("남은 시간 00:58")
        compose.onNodeWithTag("resume_run").assertIsDisplayed().performClick()
        capture("run-interval-recovery-320-font2-fixture")

        compose.onNodeWithTag("finish_run").performClick()
        compose.onNodeWithTag("confirm_finish_run").assertIsDisplayed().performClick()
        compose.onNodeWithTag("run_interval_result_expand").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("run_interval_result_set_1").performScrollTo()
        compose.onNodeWithText("달리기 00:10").assertIsDisplayed()
        compose.onNodeWithText("회복 00:02 · 미완료").assertIsDisplayed()
        compose.onNodeWithTag("run_interval_result_set_2").assertDoesNotExist()
        capture("run-interval-result-320-font2-fixture")
    }
}

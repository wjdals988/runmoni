package com.runmoni.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class RunGoalUiTest : RunmoniDesignTestBase() {
    @Test fun oneGoalCanBeChangedAndReachedWithoutStoppingTheRun() {
        val state = mutableStateOf(AppState())
        val delegate = RunmoniUiTest.FakeActions(state)
        val actions = object : AppActions by delegate {
            override fun setRunGoal(goal: RunGoal?) {
                state.value = state.value.copy(plannedRunGoal = goal)
            }
            override fun startRun() {
                val goal = state.value.plannedRunGoal
                delegate.startRun()
                state.value = state.value.copy(plannedRunGoal = null,
                    activeRun = state.value.activeRun.copy(goal = goal, distanceMeters = 500.0))
            }
        }
        compose.setContent { ViewportBox(Viewport(320, 2f)) {
            Box(Modifier.height(520.dp)) { RunmoniApp(state.value, actions) }
        } }
        compose.onNodeWithTag("nav_run").performClick()
        compose.onNodeWithTag("choose_run_goal").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("run_goal_DURATION_30").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(RunGoal(RunGoalKind.DURATION, 30), state.value.plannedRunGoal) }
        compose.onNodeWithTag("choose_run_goal").performScrollTo().performClick()
        compose.onNodeWithTag("run_goal_DISTANCE_1").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(RunGoal(RunGoalKind.DISTANCE, 1), state.value.plannedRunGoal) }
        compose.onNodeWithTag("start_run").assertIsDisplayed().performClick()
        compose.onNodeWithTag("active_run_goal_progress").performScrollTo().assertTextEquals("남은 거리 500 m")
        compose.runOnIdle { state.value = state.value.copy(activeRun = state.value.activeRun.copy(distanceMeters = 1_000.0)) }
        compose.onNodeWithTag("active_run_goal_progress").assertTextEquals("목표 달성")
        compose.onNodeWithTag("pause_run").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(RunPhase.RUNNING, state.value.activeRun.phase)
            assertNull(state.value.plannedRunGoal)
            assertEquals(0, delegate.finishes)
        }
    }
}

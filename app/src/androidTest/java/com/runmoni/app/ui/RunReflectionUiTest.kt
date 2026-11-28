package com.runmoni.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.AppActions
import com.runmoni.app.domain.AppState
import com.runmoni.app.domain.RunEffort
import com.runmoni.app.domain.RunRecord
import com.runmoni.app.domain.RunReflection
import org.junit.Assert.*
import org.junit.Test

class RunReflectionUiTest : RunmoniDesignTestBase() {
    @Test fun optionalReflectionCanBeSavedRetriedEditedAndClearedWithLargeText() {
        val record = mutableStateOf(RunRecord("private-note-fixture", 1_700_000_000_000L, 1_700_001_249_000L,
            distanceMeters = 3_210.0, activeDurationMillis = 1_200_000))
        val delegate = RunmoniUiTest.FakeActions(mutableStateOf(AppState()))
        var rejectFirstSave = true
        var writes = 0
        val actions = object : AppActions by delegate {
            override suspend fun saveRunReflection(id: String, reflection: RunReflection?): Boolean {
                assertEquals(record.value.id, id)
                writes++
                if (rejectFirstSave) {
                    rejectFirstSave = false
                    return false
                }
                record.value = record.value.copy(reflection = reflection)
                return true
            }
        }
        compose.setContent { ViewportBox(Viewport(320, 2f)) {
            RunmoniTheme { Box(Modifier.height(520.dp)) { RunRecordScreen(record.value, actions, onBack = {}) } }
        } }
        val open = compose.onNodeWithTag("record_reflection_open")
        open.performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("run_reflection_save").assertIsNotEnabled()
        compose.onNodeWithTag("confirmation_close").performClick()
        compose.runOnIdle { assertEquals(0, writes) }
        open.performClick()
        compose.onNodeWithTag("run_reflection_effort_EASY").performScrollTo()
            .assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
        val note = compose.onNodeWithTag("run_reflection_note_input")
        note.performScrollTo().performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("가".repeat(121))) }
        compose.onNodeWithTag("run_reflection_save").assertIsNotEnabled()
        note.performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("바람이 강했어요")) }
        capture("run-reflection-editor-320-font2-fixture")
        compose.onNodeWithTag("run_reflection_save").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("run_reflection_save_error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("run_reflection_save").performClick()
        compose.onNodeWithTag("record_reflection_note").performScrollTo().assertTextEquals("바람이 강했어요")
        compose.onNodeWithTag("record_reflection_effort").assertTextEquals("쉬웠어요")
        capture("run-reflection-saved-320-font2-fixture")
        open.performScrollTo().performClick()
        compose.onNodeWithTag("run_reflection_effort_HARD").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("run_reflection_save").performClick()
        compose.onNodeWithTag("record_reflection_effort").assertTextEquals("힘들었어요")
        open.performScrollTo().performClick()
        compose.onNodeWithTag("run_reflection_remove").assertIsDisplayed().performClick()
        compose.onNodeWithTag("record_reflection_note").assertDoesNotExist()
        compose.runOnIdle {
            assertNull(record.value.reflection)
            assertEquals(4, writes)
        }
    }
}

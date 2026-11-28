package com.runmoni.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.AppActions
import com.runmoni.app.domain.RunEffort
import com.runmoni.app.domain.RunRecord
import com.runmoni.app.domain.RunReflection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun RunReflectionSection(record: RunRecord, actions: AppActions, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (record.id.isBlank() || record.finishedAtMillis <= 0) return
    var editing by rememberSaveable(record.id) { mutableStateOf(false) }
    val reflection = record.reflection

    Column(modifier.fillMaxWidth().testTag("record_reflection"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (reflection != null) {
            Text("나의 러닝 메모", style = MaterialTheme.typography.titleMedium)
            reflection.effort?.let {
                Text(it.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("record_reflection_effort"))
            }
            reflection.note?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("record_reflection_note"))
            }
        }
        OutlinedButton(onClick = { editing = true }, shape = RunmoniShapes.Action, enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp).testTag("record_reflection_open")) {
            Text(if (reflection == null) "체감·메모 남기기" else "체감·메모 수정")
        }
    }

    if (editing) RunReflectionEditor(record.id, reflection, actions, onClose = { editing = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunReflectionEditor(id: String, original: RunReflection?, actions: AppActions, onClose: () -> Unit) {
    var effortValue by rememberSaveable(id) { mutableStateOf(original?.effort?.wireValue) }
    var note by rememberSaveable(id) { mutableStateOf(original?.note.orEmpty()) }
    var saving by remember(id) { mutableStateOf(false) }
    var writeFailed by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sizeOk = RunReflection.noteFits(note.trim())
    val draft = if (sizeOk) RunReflection.fromInput(RunEffort.fromStorage(effortValue), note) else null

    fun save(value: RunReflection?) {
        if (saving) return
        saving = true
        writeFailed = false
        scope.launch {
            try {
                if (actions.saveRunReflection(id, value)) onClose() else writeFailed = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                writeFailed = true
            } finally {
                saving = false
            }
        }
    }

    DismissibleAlertDialog(
        onDismissRequest = { if (!saving) onClose() },
        title = { Text("오늘 러닝은 얼마나 힘들었나요?") },
        dismissButton = {
            if (original != null) TextButton(onClick = { save(null) }, enabled = !saving,
                modifier = Modifier.heightIn(min = 48.dp).testTag("run_reflection_remove")) { Text("체감·메모 지우기") }
        },
        confirmButton = {
            TextButton(onClick = { draft?.let(::save) }, enabled = !saving && draft != null && draft != original,
                modifier = Modifier.heightIn(min = 48.dp).testTag("run_reflection_save")) { Text("저장") }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunEffort.entries.forEach { effort ->
                        FilterChip(selected = effortValue == effort.wireValue, onClick = {
                            effortValue = effort.wireValue.takeUnless { it == effortValue }
                        }, enabled = !saving, label = { Text(effort.label) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("run_reflection_effort_${effort.name}"))
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = RunReflection.singleLine(it) },
                    enabled = !saving, singleLine = true, isError = !sizeOk,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().testTag("run_reflection_note_input"),
                    label = { Text("한 줄 메모 (선택)") },
                    supportingText = { Text(if (sizeOk) "이 기기에만 저장돼요" else "${RunReflection.MaxNoteLength}자까지 적을 수 있어요") })
                if (writeFailed) Text("메모를 저장하지 못했어요. 다시 시도해 주세요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("run_reflection_save_error").semantics { liveRegion = LiveRegionMode.Polite })
            }
        },
    )
}

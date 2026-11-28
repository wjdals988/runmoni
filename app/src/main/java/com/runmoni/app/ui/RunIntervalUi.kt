package com.runmoni.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.RunIntervalPlan
import com.runmoni.app.domain.RunIntervalStage

@Composable
internal fun RunIntervalPicker(current: RunIntervalPlan?, onSelect: (RunIntervalPlan?) -> Unit, onDismiss: () -> Unit) {
    val initial = current ?: RunIntervalPlan.Default
    var run by rememberSaveable { mutableStateOf(initial.runSeconds.toString()) }
    var recovery by rememberSaveable { mutableStateOf(initial.recoverySeconds.toString()) }
    var repeats by rememberSaveable { mutableStateOf(initial.repeats.toString()) }
    var vibration by rememberSaveable { mutableStateOf(initial.vibration) }
    val selected = RunIntervalPlan.fromStorage(run.toIntOrNull() ?: 0, recovery.toIntOrNull() ?: 0,
        repeats.toIntOrNull() ?: 0, vibration)
    DismissibleAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("시간 인터벌") },
        dismissButton = {
            if (current != null) TextButton(onClick = { onSelect(null) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("run_interval_clear")) { Text("인터벌 해제") }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onSelect) }, enabled = selected != null,
                modifier = Modifier.heightIn(min = 48.dp).testTag("run_interval_confirm")) { Text("적용") }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallNote("달리기와 회복을 한 세트로 반복해요. 회복 중 멈춰도 시간이 흘러요. 시간을 멈추려면 일시정지를 누르세요.")
                IntervalNumberField("달리기 (초)", run, { run = it }, RunIntervalPlan.PhaseSeconds, "run_interval_run")
                IntervalNumberField("회복 (초)", recovery, { recovery = it }, RunIntervalPlan.PhaseSeconds, "run_interval_recovery")
                IntervalNumberField("반복 횟수", repeats, { repeats = it }, RunIntervalPlan.RepeatCount, "run_interval_repeats")
                ToggleRow("단계 전환 진동", "달리기와 회복이 바뀔 때 알려줘요", vibration, { vibration = it },
                    tag = "run_interval_vibration")
            }
        },
    )
}

@Composable
private fun IntervalNumberField(label: String, value: String, onChange: (String) -> Unit, bounds: IntRange, tag: String) {
    val invalid = value.isNotEmpty() && value.toIntOrNull()?.let { it in bounds } != true
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 3 && it.all { character -> character in '0'..'9' }) onChange(it) },
        modifier = Modifier.fillMaxWidth().testTag(tag),
        label = { Text(label) },
        supportingText = { Text("${bounds.first}–${bounds.last}") },
        isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

internal fun runIntervalDescription(plan: RunIntervalPlan): String =
    "달리기 ${duration(plan.runSeconds * 1_000L)} · 회복 ${duration(plan.recoverySeconds * 1_000L)} · ${plan.repeats}회"

@Composable
internal fun RunIntervalLive(plan: RunIntervalPlan, activeMillis: Long) {
    val progress = plan.progress(activeMillis)
    Column(Modifier.fillMaxWidth().testTag("active_run_interval"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val stage = if (progress.stage == RunIntervalStage.COMPLETE) progress.stage.label
            else "${progress.stage.label} · ${progress.repeat}/${plan.repeats}회"
        Text(stage, style = MaterialTheme.typography.titleSmall, color = RunmoniTrack.Highlight,
            modifier = Modifier.testTag("active_run_interval_stage").semantics { liveRegion = LiveRegionMode.Polite })
        if (progress.stage == RunIntervalStage.COMPLETE) {
            Text("러닝 기록은 계속돼요", style = MaterialTheme.typography.bodySmall, color = RunmoniTrack.Detail)
        } else {
            val leftSeconds = (progress.stageRemainingMillis + 999) / 1_000
            Text("남은 시간 ${duration(leftSeconds * 1_000)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = RunmoniTrack.Detail,
                modifier = Modifier.testTag("active_run_interval_remaining"))
        }
    }
}

@Composable
internal fun RunIntervalResult(plan: RunIntervalPlan?, activeMillis: Long, modifier: Modifier = Modifier) {
    if (plan == null) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val progress = plan.progress(activeMillis)
    Surface(modifier.fillMaxWidth().testTag("run_interval_result"), shape = RunmoniShapes.Action, color = RunmoniColors.Paper) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("시간 인터벌", style = MaterialTheme.typography.titleMedium)
            Text(runIntervalDescription(plan), style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
            Text("완료한 구간 · 달리기 ${progress.completedRuns}/${plan.repeats} · 회복 ${progress.completedRecoveries}/${plan.repeats}",
                style = MaterialTheme.typography.bodyMedium)
            if (progress.stage != RunIntervalStage.COMPLETE && progress.stageElapsedMillis > 0) {
                Text("${progress.repeat}번째 ${progress.stage.label}에서 종료 · ${intervalResultTime(progress.stageElapsedMillis)}",
                    style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
            }
            if (activeMillis > 0) {
                TextButton(onClick = { expanded = !expanded },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("run_interval_result_expand")) {
                    Text(if (expanded) "단계별 기록 접기" else "단계별 기록 보기")
                }
                if (expanded) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    plan.setResults(activeMillis).forEach { set ->
                        Column(Modifier.fillMaxWidth().testTag("run_interval_result_set_${set.repeat}"),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${set.repeat}회차", style = MaterialTheme.typography.titleSmall)
                            Text(resultStageLabel("달리기", set.runMillis, plan.runSeconds), style = MaterialTheme.typography.bodyMedium)
                            Text(resultStageLabel("회복", set.recoveryMillis, plan.recoverySeconds), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun intervalResultTime(millis: Long): String = if (millis < 1_000) "1초 미만" else duration(millis)

private fun resultStageLabel(label: String, millis: Long, targetSeconds: Int): String {
    if (millis == 0L) return "$label · 시작 전"
    val elapsed = intervalResultTime(millis)
    return if (millis < targetSeconds * 1_000L) "$label $elapsed · 미완료" else "$label $elapsed"
}

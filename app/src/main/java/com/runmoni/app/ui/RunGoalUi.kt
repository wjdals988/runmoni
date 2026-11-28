package com.runmoni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.RunGoal
import com.runmoni.app.domain.RunGoalKind

@Composable
internal fun RunGoalPicker(current: RunGoal?, onSelect: (RunGoal?) -> Unit, onDismiss: () -> Unit) {
    DismissibleAlertDialog(onDismissRequest = onDismiss, title = { Text("오늘의 러닝 목표") },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
        }, text = {
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GoalChoices("거리", RunGoalKind.DISTANCE, listOf(1, 3, 5, 10), current, onSelect)
                GoalChoices("시간", RunGoalKind.DURATION, listOf(10, 20, 30, 60), current, onSelect)
                TextButton(onClick = { onSelect(null) }, modifier = Modifier.heightIn(min = 48.dp).testTag("run_goal_none")) {
                    Text("목표 없이 달리기")
                }
            }
        })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalChoices(title: String, kind: RunGoalKind, targets: List<Int>, current: RunGoal?, onSelect: (RunGoal) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            targets.forEach { target ->
                val goal = RunGoal(kind, target)
                FilterChip(selected = current == goal, onClick = { onSelect(goal) }, label = { Text(goal.label) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("run_goal_${kind.name}_$target"))
            }
        }
    }
}

@Composable
internal fun ActiveRunGoal(goal: RunGoal, distanceMeters: Double, activeDurationMillis: Long, compact: Boolean = false) {
    val progress = goal.progress(distanceMeters, activeDurationMillis)
    val label = if (progress.reached) "목표 달성"
        else "남은 ${if (goal.kind == RunGoalKind.DISTANCE) "거리" else "시간"} ${progress.remainingLabel}"
    if (compact) Text(label, style = MaterialTheme.typography.labelSmall, color = RunmoniTrack.Highlight,
        modifier = Modifier.testTag("active_run_goal_progress"))
    else Surface(Modifier.fillMaxWidth().testTag("active_run_goal"), shape = RunmoniShapes.Action, color = RunmoniColors.Paper) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Flag, null, Modifier.size(18.dp), tint = RunmoniColors.Primary)
                Text("목표 ${goal.label}", style = MaterialTheme.typography.titleSmall)
            }
            Text(label,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("active_run_goal_progress"))
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

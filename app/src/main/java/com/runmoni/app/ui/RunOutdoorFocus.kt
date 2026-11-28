@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.runmoni.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.runmoni.app.domain.ActiveRun
import com.runmoni.app.domain.RunIntervalPlan
import com.runmoni.app.domain.RunIntervalProgress
import com.runmoni.app.domain.RunIntervalStage
import com.runmoni.app.domain.RunPhase
import kotlinx.coroutines.flow.collect

@Composable
internal fun OutdoorFocusButton(onOpen: () -> Unit, compact: Boolean) {
    TextButton(onClick = onOpen, shape = RunmoniShapes.Action,
        colors = ButtonDefaults.textButtonColors(contentColor = if (compact) RunmoniTrack.OnBoard else RunmoniColors.Primary),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("open_run_outdoor")
            .semantics { contentDescription = "야외 집중 화면 열기" }) {
        Icon(Icons.Outlined.CenterFocusStrong, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (compact) "집중" else "야외 집중")
    }
}

internal enum class RunGpsTone { Ready, Attention, Neutral }

@Composable
internal fun TrackGpsStatus(label: String, tone: RunGpsTone) {
    val indicator = when (tone) {
        RunGpsTone.Ready -> RunmoniTrack.Recovery
        RunGpsTone.Attention -> RunmoniTrack.Attention
        RunGpsTone.Neutral -> RunmoniTrack.Detail
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(6.dp).background(indicator, CircleShape))
        Text(label, color = if (tone == RunGpsTone.Attention) indicator else RunmoniTrack.Detail,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag("gps_status").semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
internal fun OutdoorRunPanel(
    run: ActiveRun,
    status: String,
    gpsStatus: String,
    currentPace: Int?,
    gpsTone: RunGpsTone,
    onClose: () -> Unit,
) {
    val compact = LocalDensity.current.fontScale >= 1.3f
    val interval = run.interval
    val progress = interval?.progress(run.activeDurationMillis)
    val timingInterval = progress != null && progress.stage != RunIntervalStage.COMPLETE
    RunningMetricPanel(contentPadding = PaddingValues(if (compact) 12.dp else 16.dp), spacing = RunmoniLayout.TightGap,
        modifier = Modifier.testTag("run_outdoor_panel").semantics { paneTitle = "야외 집중 화면" }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(status, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = RunmoniTrack.OnBoard, modifier = Modifier.testTag("run_status").semantics { heading() })
                TrackGpsStatus(gpsStatus, gpsTone)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("close_run_outdoor")) {
                Icon(Icons.Outlined.Close, "야외 집중 화면 닫기", tint = RunmoniTrack.OnBoard)
            }
        }

        if (interval != null && progress != null && timingInterval) {
            OutdoorIntervalTiming(run, interval, progress)
        } else {
            if (progress?.stage == RunIntervalStage.COMPLETE) Text("인터벌 완료 · 기록 계속", color = RunmoniTrack.Detail,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("outdoor_interval_stage"))
            Text("거리", color = RunmoniTrack.Detail, style = MaterialTheme.typography.labelSmall)
            DistanceNumber(distance(run.distanceMeters), RunmoniTrack.Highlight, Modifier.testTag("run_distance"),
                maxSize = if (compact) 46f else 64f, unit = "km")
        }

        HorizontalDivider(color = RunmoniTrack.Detail.copy(alpha = .4f))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
            if (timingInterval) Text("거리 ${distance(run.distanceMeters)} km", color = RunmoniTrack.Detail, style = style)
            Text("시간 ${duration(run.activeDurationMillis)}", color = RunmoniTrack.Detail, style = style,
                modifier = Modifier.testTag("run_duration"))
            Text("페이스 ${if (currentPace == null) "—" else "${pace(currentPace)} /km"}", color = RunmoniTrack.Detail, style = style,
                modifier = Modifier.testTag("run_pace"))
        }
    }
}

@Composable
private fun OutdoorIntervalTiming(run: ActiveRun, plan: RunIntervalPlan, progress: RunIntervalProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val stageInk = if (progress.stage == RunIntervalStage.RECOVERY) RunmoniTrack.Recovery else RunmoniTrack.OnBoard
        Text("${progress.stage.label} ${progress.repeat}/${plan.repeats}회", color = stageInk,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.testTag("outdoor_interval_stage").semantics { liveRegion = LiveRegionMode.Polite })
        val seconds = (progress.stageRemainingMillis + 999) / 1_000
        DistanceNumber(duration(seconds * 1_000), RunmoniTrack.Highlight,
            Modifier.testTag("outdoor_interval_remaining").clearAndSetSemantics {
                contentDescription = "남은 시간 ${seconds / 60}분 ${seconds % 60}초"
            }, maxSize = if (LocalDensity.current.fontScale >= 1.3f) 36f else 76f, unit = "남음")
        val next = when {
            progress.stage == RunIntervalStage.RUN -> "다음 회복 ${duration(plan.recoverySeconds * 1_000L)}"
            progress.repeat < plan.repeats -> "다음 달리기 ${duration(plan.runSeconds * 1_000L)}"
            else -> "이 회복 뒤 인터벌 완료"
        }
        Text(next, color = RunmoniTrack.Detail, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag("outdoor_interval_next"))
        OutdoorIntervalLane(run, progress)
    }
}

private data class OutdoorPhaseSample(val activeMillis: Long, val index: Int, val progressMillis: Long,
    val isRecovery: Boolean, val running: Boolean) {
    val position: Float get() = if (isRecovery) 1f else 0f

    fun follows(previous: OutdoorPhaseSample): Boolean = running && previous.running && index == previous.index + 1 &&
        activeMillis - previous.activeMillis in 1..2_500 && progressMillis <= 2_500
}

/** Only an adjacent transition observed in a live, resumed screen moves the mark. */
@Composable
private fun OutdoorIntervalLane(run: ActiveRun, progress: RunIntervalProgress) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val animate = rememberPageAnimationsEnabled()
    val sample = OutdoorPhaseSample(run.activeDurationMillis, progress.index, progress.stageElapsedMillis,
        progress.stage == RunIntervalStage.RECOVERY, run.phase == RunPhase.RUNNING)
    val latest by rememberUpdatedState(sample)
    val marker = remember(run.id) { Animatable(sample.position) }
    LaunchedEffect(run.id, lifecycle, animate) {
        if (!animate) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous: OutdoorPhaseSample? = null
            snapshotFlow { latest }.collect { current ->
                val slide = previous?.let(current::follows) == true
                previous = current
                if (marker.value != current.position) {
                    if (slide) marker.animateTo(current.position, tween(150)) else marker.snapTo(current.position)
                }
            }
        }
    }
    Canvas(Modifier.fillMaxWidth().height(3.dp).testTag("outdoor_interval_lane").clearAndSetSemantics { }) {
        val center = size.height / 2
        val cap = 2.dp.toPx()
        drawLine(RunmoniTrack.Detail.copy(alpha = .35f), Offset(cap, center), Offset(size.width - cap, center), cap)
        val position = if (animate && sample.running) marker.value else sample.position
        val length = size.width * .28f
        val start = (size.width - length - cap * 2).coerceAtLeast(0f) * position + cap
        drawLine(RunmoniTrack.Highlight, Offset(start, center), Offset(start + length, center), cap, StrokeCap.Round)
    }
}

package com.runmoni.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.RunPaceScale
import com.runmoni.app.domain.RunResultAnalysis
import com.runmoni.app.domain.RunResultSplit
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SplitsPerPage = 6

internal fun formatResultPace(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

@Composable
internal fun RunRecordPaceAnalysis(analysis: RunResultAnalysis, selectedKilometer: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("record_pace_analysis"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RecordSectionTitle("페이스와 1km 구간", Icons.Outlined.Speed)
        if (analysis.splits.isEmpty()) {
            Text("저장된 완료 1km 구간이 없어요.", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                modifier = Modifier.testTag("record_no_splits"))
        } else {
            Text(if (analysis.hasMissingSplits) "페이스가 저장된 완료 1km만 표시해요."
                else "1km를 채운 구간만 비교해요.", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            val selected = analysis.splits.firstOrNull { it.kilometer == selectedKilometer }
            val scale = analysis.paceScale
            if (scale != null) {
                RunPaceChart(analysis.splits, analysis.completedKilometers, scale, analysis.averagePaceSeconds,
                    selected?.kilometer ?: 0, onSelect)
            }
            if (selected != null) {
                val averageComparison = analysis.averagePaceSeconds?.let {
                    paceComparison("전체 평균", selected.paceSeconds - it)
                }
                val previousComparison = analysis.splits.firstOrNull { it.kilometer == selected.kilometer - 1 }?.let {
                    paceComparison("직전 구간", selected.paceSeconds - it.paceSeconds)
                }
                Column(Modifier.fillMaxWidth().background(RunmoniColors.PrimarySoft, RunmoniShapes.Action)
                    .padding(12.dp).testTag("record_selected_split")
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${splitRange(selected)} · ${formatResultPace(selected.paceSeconds)} /km",
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                    listOfNotNull(averageComparison, previousComparison).forEach { comparison ->
                        Text(comparison, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                    }
                }
            }
            RunSplitTable(analysis.splits, selectedKilometer, onSelect)
        }
    }
}

@Composable
private fun RunPaceChart(splits: List<RunResultSplit>, completedKilometers: Int, scale: RunPaceScale,
    average: Long?, selectedKilometer: Int, onSelect: (Int) -> Unit) {
    val axisStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    val muted = RunmoniColors.Muted
    val ink = RunmoniColors.Ink
    val primary = RunmoniColors.Primary
    val line = RunmoniColors.Line
    val paper = RunmoniColors.Paper
    val lastSelection by rememberUpdatedState(onSelect)
    val density = LocalDensity.current
    val measure = rememberTextMeasurer()
    val tickWidth = with(density) {
        scale.ticksSeconds.maxOf { measure.measure(AnnotatedString(formatResultPace(it)), axisStyle).size.width }.toDp()
    }
    val tickHeight = with(density) { axisStyle.lineHeight.toDp() }
    val chartHeight = maxOf(136.dp, tickHeight * scale.ticksSeconds.size)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth - tickWidth >= 120.dp) Column(Modifier.fillMaxWidth().clip(RunmoniShapes.Surface)
            .background(paper).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("페이스 (분:초/km) · 빠를수록 위", style = MaterialTheme.typography.labelSmall, color = muted)
            Row(Modifier.fillMaxWidth().height(chartHeight), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxHeight().width(tickWidth), verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End) {
                    scale.ticksSeconds.forEach { Text(formatResultPace(it), style = axisStyle, color = muted, maxLines = 1) }
                }
                Canvas(Modifier.weight(1f).fillMaxHeight().padding(start = 5.dp, end = 5.dp, top = tickHeight / 2,
                    bottom = tickHeight / 2).testTag("record_pace_plot")
                    .semantics {
                        contentDescription = "1km 구간 페이스 차트. 구간 표에서도 값을 선택할 수 있어요."
                        stateDescription = if (selectedKilometer > 0) "${selectedKilometer}번 구간 선택" else "선택한 구간 없음"
                    }
                    .pointerInput(splits, completedKilometers) {
                        detectTapGestures { touch ->
                            if (size.width > 0) {
                                val step = size.width.toDouble() / (completedKilometers - 1)
                                val kilometer = (touch.x / step).roundToInt() + 1
                                val offset = abs(touch.x - (kilometer - 1) * step)
                                if (offset <= 24.dp.toPx() && splits.binarySearchBy(kilometer) { it.kilometer } >= 0) {
                                    lastSelection(kilometer)
                                }
                            }
                        }
                    }) {
                    val paceRange = (scale.slowestSeconds - scale.fastestSeconds).toDouble()
                    fun y(seconds: Long): Float = (((seconds - scale.fastestSeconds) / paceRange) * size.height).toFloat()
                    fun x(kilometer: Int): Float = ((kilometer - 1).toDouble() /
                        (completedKilometers - 1) * size.width).toFloat()
                    scale.ticksSeconds.forEach { seconds ->
                        val y = y(seconds)
                        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    average?.let {
                        val y = y(it)
                        drawLine(muted, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                    }
                    splits.forEach { split ->
                        val center = Offset(x(split.kilometer), y(split.paceSeconds))
                        drawCircle(primary, radius = 3.dp.toPx(), center = center)
                        if (split.kilometer == selectedKilometer) {
                            drawCircle(ink, radius = 6.dp.toPx(), center = center, style = Stroke(1.5.dp.toPx()))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = tickWidth + 13.dp, end = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1", style = axisStyle, color = muted)
                Text(completedKilometers.toString(), style = axisStyle, color = muted)
            }
            Text("구간 순서 (km)", style = axisStyle, color = muted)
            if (average != null) Text("점선 · 전체 평균 ${formatResultPace(average)} /km", style = axisStyle, color = muted)
        }
    }
}

@Composable
private fun RunSplitTable(splits: List<RunResultSplit>, selectedKilometer: Int, onSelect: (Int) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val lastPage = splits.lastIndex / SplitsPerPage
    val currentPage = page.coerceIn(0, lastPage)
    val firstIndex = currentPage * SplitsPerPage
    val lastIndex = minOf(firstIndex + SplitsPerPage, splits.size)
    LaunchedEffect(splits, selectedKilometer) {
        val selectedIndex = splits.indexOfFirst { it.kilometer == selectedKilometer }
        if (selectedIndex >= 0) page = selectedIndex / SplitsPerPage
    }
    val animate = rememberPageAnimationsEnabled()
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (index in firstIndex until lastIndex) key(splits[index].kilometer) {
            val split = splits[index]
            val selected = split.kilometer == selectedKilometer
            val fill by animateColorAsState(if (selected) RunmoniColors.PrimarySoft else RunmoniColors.Paper,
                animationSpec = if (animate) tween(150) else snap(), label = "선택 구간")
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RunmoniShapes.Action).background(fill)
                .selectable(selected, role = Role.RadioButton) { onSelect(split.kilometer) }
                .testTag("record_split_${split.kilometer}").padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(splitRange(split), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("${formatResultPace(split.paceSeconds)} /km", modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum",
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal))
                if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = RunmoniColors.Primary)
                else Spacer(Modifier.size(16.dp))
            }
        }
    }
    if (lastPage > 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { page = currentPage - 1 }, enabled = currentPage > 0,
            modifier = Modifier.heightIn(min = 48.dp).testTag("record_splits_previous")) { Text("이전") }
        Text("${firstIndex + 1}–$lastIndex / ${splits.size}", color = RunmoniColors.Muted,
            modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { page = currentPage + 1 }, enabled = currentPage < lastPage,
            modifier = Modifier.heightIn(min = 48.dp).testTag("record_splits_next")) { Text("다음") }
    }
}

private fun splitRange(split: RunResultSplit): String = "${split.kilometer - 1}–${split.kilometer} km"

private fun paceComparison(reference: String, delta: Long): String = when {
    delta == 0L -> "${reference}과 같아요."
    delta < 0L -> "${reference}보다 ${readablePaceDifference(-delta)} 빨라요."
    else -> "${reference}보다 ${readablePaceDifference(delta)} 느려요."
}

private fun readablePaceDifference(seconds: Long): String = if (seconds < 60) "${seconds}초"
    else "${seconds / 60}분 ${seconds % 60}초"

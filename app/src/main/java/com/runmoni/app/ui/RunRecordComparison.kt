package com.runmoni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.RunRecord
import com.runmoni.app.domain.RunResultAnalysis
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val comparisonDate = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.KOREA)
private val comparisonDay = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREA)
private val comparisonTime = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RunRecordComparison(current: RunRecord, records: List<RunRecord>, modifier: Modifier = Modifier) {
    val candidates = remember(current.id, current.startedAtMillis, records) {
        records.filter { it.id.isNotBlank() && it.id != current.id && it.finishedAtMillis > 0 &&
            it.startedAtMillis < current.startedAtMillis }.sortedByDescending { it.startedAtMillis }
    }
    if (candidates.isEmpty()) return

    var selecting by rememberSaveable(current.id) { mutableStateOf(false) }
    var comparisonId by rememberSaveable(current.id) { mutableStateOf<String?>(null) }
    val previous = candidates.firstOrNull { it.id == comparisonId }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RecordSectionTitle("지난 러닝과 비교", Icons.AutoMirrored.Outlined.CompareArrows)
        if (previous != null) {
            val currentPace = remember(current) { RunResultAnalysis.from(current).averagePaceSeconds }
            val previousPace = remember(previous) { RunResultAnalysis.from(previous).averagePaceSeconds }
            val currentDistance = comparisonDistance(current)
            val previousDistance = comparisonDistance(previous)
            Column(Modifier.fillMaxWidth().testTag("record_comparison"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ComparisonHeading("이번 러닝", current, Modifier.weight(1f))
                    ComparisonHeading("지난 러닝", previous, Modifier.weight(1f))
                }
                ComparisonMetric("거리", currentDistance?.let { "$it km" }, previousDistance?.let { "$it km" },
                    distanceDifference(currentDistance, previousDistance))
                ComparisonMetric("러닝 시간", current.activeDurationMillis.takeIf { it >= 0 }?.let(::duration),
                    previous.activeDurationMillis.takeIf { it >= 0 }?.let(::duration), timeDifference(current, previous))
                ComparisonMetric("평균 페이스", currentPace?.let { "${formatResultPace(it)} /km" },
                    previousPace?.let { "${formatResultPace(it)} /km" }, paceDifference(currentPace, previousPace))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { selecting = true }, shape = RunmoniShapes.Action,
                modifier = Modifier.heightIn(min = 48.dp).testTag("record_compare_pick")) {
                Text(if (previous == null) "지난 기록 고르기" else "지난 기록 바꾸기")
            }
            if (previous != null) TextButton(onClick = { comparisonId = null },
                modifier = Modifier.heightIn(min = 48.dp)) { Text("비교 닫기") }
        }
    }

    if (selecting) AlertDialog(onDismissRequest = { selecting = false }, title = { Text("지난 기록 고르기") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(candidates, key = { it.id }) { candidate ->
                    TextButton(onClick = { comparisonId = candidate.id; selecting = false },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("record_compare_${candidate.id}")) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                            Text(comparisonDate(candidate), style = MaterialTheme.typography.bodyLarge)
                            Text(listOfNotNull(comparisonDistance(candidate)?.let { "$it km" }, duration(candidate.activeDurationMillis))
                                .joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { selecting = false }) { Text("닫기") } })
}

@Composable
private fun ComparisonHeading(title: String, record: RunRecord, modifier: Modifier) {
    val time = Instant.ofEpochMilli(record.startedAtMillis).atZone(ZoneId.systemDefault())
    val dateStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End)
        Text(comparisonDay.format(time), style = dateStyle, color = RunmoniColors.Muted, textAlign = TextAlign.End)
        Text(comparisonTime.format(time), style = dateStyle, color = RunmoniColors.Muted, textAlign = TextAlign.End)
    }
}

@Composable
private fun ComparisonMetric(title: String, current: String?, previous: String?, difference: String?) {
    HorizontalDivider(color = RunmoniColors.Line)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = RunmoniColors.Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")
            Text(current ?: "기록 없음", Modifier.weight(1f), style = style, textAlign = TextAlign.End)
            Text(previous ?: "기록 없음", Modifier.weight(1f), style = style, textAlign = TextAlign.End)
        }
        difference?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted) }
    }
}

private fun comparisonDate(record: RunRecord) =
    comparisonDate.format(Instant.ofEpochMilli(record.startedAtMillis).atZone(ZoneId.systemDefault()))

private fun comparisonDistance(record: RunRecord): String? =
    record.distanceMeters.takeIf { it.isFinite() && it >= 0 }?.let(::distance)

private fun distanceDifference(current: String?, previous: String?): String? {
    val delta = (current?.toBigDecimalOrNull() ?: return null) - (previous?.toBigDecimalOrNull() ?: return null)
    return when (delta.signum()) {
        0 -> "거리 같음"
        1 -> "${delta.toPlainString()} km 더 달림"
        else -> "${delta.abs().toPlainString()} km 덜 달림"
    }
}

private fun timeDifference(current: RunRecord, previous: RunRecord): String? {
    val latest = current.activeDurationMillis.takeIf { it >= 0 } ?: return null
    val earlier = previous.activeDurationMillis.takeIf { it >= 0 } ?: return null
    val delta = latest / 1_000 - earlier / 1_000
    return when {
        delta == 0L -> "러닝 시간 같음"
        delta > 0 -> "${readableSeconds(delta)} 더 김"
        else -> "${readableSeconds(-delta)} 더 짧음"
    }
}

private fun paceDifference(current: Long?, previous: Long?): String? {
    if (current == null || previous == null) return null
    val delta = current - previous
    return when {
        delta == 0L -> "평균 페이스 같음"
        delta > 0 -> "평균 페이스 ${readableSeconds(delta)} 느림"
        else -> "평균 페이스 ${readableSeconds(abs(delta))} 빠름"
    }
}

private fun readableSeconds(seconds: Long): String = buildList {
    if (seconds >= 3_600) add("${seconds / 3_600}시간")
    if (seconds / 60 % 60 > 0) add("${seconds / 60 % 60}분")
    if (seconds % 60 > 0) add("${seconds % 60}초")
}.joinToString(" ")

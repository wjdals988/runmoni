@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.runmoni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.RecentRunActivity
import com.runmoni.app.domain.RecentRunAvailability
import com.runmoni.app.domain.RecentRunDay
import com.runmoni.app.domain.RunRecord
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun RecentRunActivitySection(activity: RecentRunActivity, onRecord: (RunRecord) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RunmoniLayout.ContentGap)) {
        if (activity.availability == RecentRunAvailability.CACHED) Text("마지막으로 불러온 기록 기준",
            style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
            modifier = Modifier.testTag("recent_run_activity_cached"))
        activity.latestCompleted?.let { latest ->
            DestinationRow("최근 러닝 보기", "${dateTime(latest.startedAtMillis)} · ${distance(latest.distanceMeters)} km",
                Icons.Outlined.History, { onRecord(latest) }, Modifier.testTag("record_${latest.id}"))
        }
        ScreenSection("최근 7일", Modifier.testTag("recent_run_activity")) {
            val totals = activity.totals
            when {
                activity.availability == RecentRunAvailability.LOADING -> Text("저장된 기록을 불러오는 중이에요.",
                    style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                    modifier = Modifier.testTag("recent_run_activity_loading"))
                totals == null -> Text("최근 7일의 합계를 표시할 수 없어요. 기록 목록을 다시 확인해 주세요.",
                    style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                    modifier = Modifier.testTag("recent_run_activity_unavailable"))
                totals.runCount == 0 -> Text("최근 7일에 저장된 러닝이 없어요.", style = MaterialTheme.typography.bodyMedium,
                    color = RunmoniColors.Muted, modifier = Modifier.testTag("recent_run_activity_empty"))
                else -> {
                    val dateFormat = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
                    val days = activity.days
                    Text("${days.first().date.format(dateFormat)}–${days.last().date.format(dateFormat)}",
                        style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                    FlowRow(Modifier.fillMaxWidth().testTag("recent_run_totals"),
                        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RecentRunTotal("거리", "${distance(totals.distanceMeters)} km", "recent_run_total_distance")
                        RecentRunTotal("러닝", "${totals.runCount}회", "recent_run_total_count")
                        RecentRunTotal("활동 시간", duration(totals.activeDurationMillis), "recent_run_total_time")
                    }
                    RecentRunDays(days)
                }
            }
        }
    }
}

@Composable
private fun RecentRunTotal(label: String, value: String, tag: String) {
    Column(Modifier.testTag(tag).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = RunmoniColors.Ink)
    }
}

@Composable
private fun RecentRunDays(days: List<RecentRunDay>) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val dateFormat = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
    val dateStyle = MaterialTheme.typography.bodySmall
    val valueStyle = dateStyle.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelWidth = with(density) { (maxWidth / days.size - 4.dp).toPx() }
        val labelsFit = days.all { day ->
            val value = if (day.runCount == 0) "—" else distance(day.distanceMeters)
            textMeasurer.measure(AnnotatedString(value), style = valueStyle).size.width <= labelWidth &&
                textMeasurer.measure(AnnotatedString(day.date.format(dateFormat)), style = dateStyle).size.width <= labelWidth
        }
        if (density.fontScale >= 1.3f || !labelsFit) {
            Column(Modifier.fillMaxWidth().testTag("recent_run_activity_rows"),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                days.forEach { day ->
                    Column(Modifier.fillMaxWidth().testTag("recent_run_day_${day.date}")
                        .semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(day.date.format(DateTimeFormatter.ofPattern("M월 d일 E요일", Locale.KOREAN)),
                            style = MaterialTheme.typography.titleSmall, color = RunmoniColors.Navy)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${distance(day.distanceMeters)} km", style = MaterialTheme.typography.titleMedium,
                                color = RunmoniColors.Primary)
                            Text("${day.runCount}회", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Navy)
                        }
                        Text("활동 시간 ${duration(day.activeDurationMillis)}", style = MaterialTheme.typography.bodyMedium,
                            color = RunmoniColors.Muted)
                    }
                }
            }
        } else {
            // A seven-day reading strip, not a normalized achievement chart.
            // Equal short markers identify saved runs; the real distance stays in text.
            Row(Modifier.fillMaxWidth().testTag("recent_run_activity_plot")) {
                days.forEach { day ->
                    Column(Modifier.weight(1f).padding(horizontal = 2.dp).testTag("recent_run_day_${day.date}")
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${day.date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 E요일", Locale.KOREAN))}, " +
                                "${distance(day.distanceMeters)}킬로미터, ${day.runCount}회, 활동 시간 ${duration(day.activeDurationMillis)}"
                        }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(day.date.format(dateFormat), style = dateStyle, color = RunmoniColors.Muted)
                        Box(Modifier.width(20.dp).height(4.dp)
                            .background(if (day.runCount > 0) RunmoniColors.Ocean else RunmoniColors.Line))
                        Text(if (day.runCount == 0) "—" else distance(day.distanceMeters), style = valueStyle,
                            color = if (day.runCount > 0) RunmoniColors.Primary else RunmoniColors.Muted)
                    }
                }
            }
        }
    }
}

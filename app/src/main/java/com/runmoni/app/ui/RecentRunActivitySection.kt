@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.runmoni.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.runmoni.app.domain.RecentRunActivity
import com.runmoni.app.domain.RecentRunAvailability
import com.runmoni.app.domain.RecentRunDay
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun RecentRunActivitySection(activity: RecentRunActivity, modifier: Modifier = Modifier) {
    ScreenSection("최근 7일", modifier.testTag("recent_run_activity")) {
        when (activity.availability) {
            RecentRunAvailability.LOADING -> Text("저장된 기록을 불러오는 중이에요.",
                style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                modifier = Modifier.testTag("recent_run_activity_loading"))
            RecentRunAvailability.UNAVAILABLE -> Text("최근 기록을 표시할 수 없어요. 기록 목록을 다시 확인해 주세요.",
                style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                modifier = Modifier.testTag("recent_run_activity_unavailable"))
            RecentRunAvailability.READY, RecentRunAvailability.CACHED -> {
                if (activity.availability == RecentRunAvailability.CACHED) Text("마지막으로 불러온 기록 기준",
                    style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                    modifier = Modifier.testTag("recent_run_activity_cached"))
                val days = activity.days
                if (days.isNotEmpty()) {
                    val dateFormat = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
                    Text("${days.first().date.format(dateFormat)}–${days.last().date.format(dateFormat)} · 거리 (km)",
                        style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                    RecentRunDays(days)
                    if (days.all { it.runCount == 0 }) Text("이 기간에 저장된 러닝이 없어요.",
                        style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun RecentRunDays(days: List<RecentRunDay>) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val textMeasurer = rememberTextMeasurer()
    val dateFormat = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
    val dateStyle = MaterialTheme.typography.bodySmall
    val valueStyle = dateStyle.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelWidth = with(density) { (maxWidth / days.size - 4.dp).toPx() }
        val labelsFit = days.all { day ->
            textMeasurer.measure(AnnotatedString(distance(day.distanceMeters)), style = valueStyle).size.width <= labelWidth &&
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
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val ink = RunmoniColors.Primary
            val baseline = RunmoniColors.Line
            val maximum = days.maxOf { it.distanceMeters }
            Canvas(Modifier.fillMaxWidth().aspectRatio(4f).testTag("recent_run_activity_plot").clearAndSetSemantics {}) {
                val slot = size.width / days.size
                val bottom = (size.height - 1.dp.toPx()).coerceAtLeast(0f)
                drawLine(baseline, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
                if (maximum > 0.0) days.forEachIndexed { index, day ->
                    if (day.distanceMeters > 0.0) {
                        val height = (day.distanceMeters / maximum).toFloat() * bottom
                        val column = if (rtl) days.lastIndex - index else index
                        drawRect(ink, Offset(slot * (column + .24f), bottom - height), Size(slot * .52f, height))
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                days.forEach { day ->
                    Column(Modifier.weight(1f).padding(horizontal = 2.dp).testTag("recent_run_day_${day.date}")
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${day.date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 E요일", Locale.KOREAN))}, " +
                                "${distance(day.distanceMeters)}킬로미터, ${day.runCount}회, 활동 시간 ${duration(day.activeDurationMillis)}"
                        }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(distance(day.distanceMeters), style = valueStyle, color = RunmoniColors.Primary)
                        Text(day.date.format(dateFormat), style = dateStyle, color = RunmoniColors.Muted)
                    }
                }
            }
        }
    }
}

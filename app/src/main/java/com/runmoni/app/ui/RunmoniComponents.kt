package com.runmoni.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class UiMetric(val label: String, val value: String, val unit: String = "", val tag: String = "")

/** Long confirmations must remain readable with the system font enlarged. */
@Composable
internal fun DialogMessage(message: String) {
    Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).testTag("dialog_message")) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Change layout before enlarged labels compete for horizontal space. */
@Composable
internal fun AdaptiveActions(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 300.dp || fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun MetricGroup(metrics: List<UiMetric>, onDark: Boolean = false) {
    val fontScale = LocalDensity.current.fontScale
    val ink = if (onDark) RunmoniColors.OnDark else RunmoniColors.Navy
    val muted = if (onDark) RunmoniColors.OnDarkMuted else RunmoniColors.Muted
    @Composable fun Metric(metric: UiMetric, modifier: Modifier = Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(metric.label, style = MaterialTheme.typography.labelMedium, color = muted)
            Text(metric.value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = ink, modifier = Modifier.testTag(metric.tag))
            if (metric.unit.isNotEmpty()) Text(metric.unit, style = MaterialTheme.typography.labelMedium, color = muted)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minimumWidth = if (metrics.size >= 3) 100.dp * metrics.size else 112.dp * metrics.size
        if (maxWidth < minimumWidth || fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { metrics.forEach { Metric(it) } }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                metrics.forEach { Metric(it, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun MetricPanel(metrics: List<UiMetric>) {
    Surface(color = RunmoniColors.Paper, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, RunmoniColors.Line)) {
        Box(Modifier.fillMaxWidth().padding(20.dp)) { MetricGroup(metrics) }
    }
}

/** Keep the full number readable even for long distances and large font settings. */
@Composable
internal fun DistanceNumber(value: String, color: Color, modifier: Modifier = Modifier, maxSize: Float = 64f) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val size = (maxWidth.value / (value.length.coerceAtLeast(1) * 0.68f * fontScale)).coerceIn(20f, maxSize)
        Text(value, modifier, color = color, fontSize = size.sp, lineHeight = (size * 1.15f).sp,
            fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

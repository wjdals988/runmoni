@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.runmoni.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class UiMetric(val label: String, val value: String, val unit: String = "", val tag: String = "")

@Composable
internal fun pageGutter() = if (LocalConfiguration.current.screenWidthDp >= 600)
    RunmoniLayout.WideGutter else RunmoniLayout.Gutter

/** Full-screen destinations own their system insets; the toolbar owns no insets. */
@Composable
internal fun PageTopBar(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeLabel: String = "닫기",
    closeTag: String = "",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
            .heightIn(min = 64.dp).padding(start = pageGutter(), end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() })
            actions()
            TextButton(onClick = onClose, shape = RunmoniShapes.Action,
                modifier = Modifier.heightIn(min = 48.dp).testTag(closeTag)) { Text(closeLabel) }
        }
    }
}

/** A page contains a few sections; rows inside a section share a tighter rhythm. */
@Composable
internal fun ScreenSection(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
    showDivider: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showDivider) HorizontalDivider(color = RunmoniColors.Line)
        SectionHeading(title, action, onAction)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Long confirmations must remain readable with the system font enlarged. */
@Composable
internal fun DialogMessage(message: String) {
    Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().testTag("dialog_message"))
}

/** Change layout before enlarged labels compete for horizontal space. */
@Composable
internal fun AdaptiveActions(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 300.dp || (fontScale >= 1.3f && maxWidth < 480.dp)) {
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(metric.value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = ink,
                    modifier = Modifier.testTag(metric.tag).alignByBaseline())
                if (metric.unit.isNotEmpty()) Text(metric.unit, style = MaterialTheme.typography.bodyMedium, color = muted,
                    modifier = Modifier.alignByBaseline())
            }
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
    Surface(color = RunmoniColors.Paper, shape = RunmoniShapes.Surface, border = BorderStroke(1.dp, RunmoniColors.Line)) {
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

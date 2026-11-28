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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class UiMetric(val label: String, val value: String, val unit: String = "", val tag: String = "")

@Composable
internal fun pageGutter() = when {
    LocalConfiguration.current.screenWidthDp >= 600 -> RunmoniLayout.WideGutter
    LocalConfiguration.current.screenWidthDp < 360 -> RunmoniLayout.NarrowGutter
    else -> RunmoniLayout.Gutter
}

/** Full-screen destinations own their system insets; the toolbar owns no insets. */
@Composable
internal fun PageTopBar(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeLabel: String = "뒤로",
    closeTag: String = "",
    compact: Boolean = false,
    trailingInset: Dp = RunmoniLayout.TightGap,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val verticalPadding = if (compact) 4.dp else 12.dp
        Row(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
            .heightIn(min = if (compact) 64.dp else 72.dp)
            .padding(start = RunmoniLayout.TightGap, end = trailingInset, top = verticalPadding, bottom = verticalPadding),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RunmoniLayout.TightGap)) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag(closeTag)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, closeLabel, tint = RunmoniColors.Primary)
            }
            Text(title, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() })
            actions()
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RunmoniLayout.ContentGap)) {
        if (showDivider) HorizontalDivider(color = RunmoniColors.Line)
        SectionHeading(title, action, onAction)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RunmoniLayout.ContentGap), content = content)
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun MetricGroup(metrics: List<UiMetric>, onDark: Boolean = false, compact: Boolean = false) {
    if (metrics.isEmpty()) return
    val fontScale = LocalDensity.current.fontScale
    val ink = if (onDark) RunmoniColors.OnDark else RunmoniColors.Navy
    val muted = if (onDark) RunmoniColors.OnDarkMuted else RunmoniColors.Muted
    val labelStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    val valueStyle = (if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall)
        .copy(fontFeatureSettings = "tnum")
    val gap = if (compact) RunmoniLayout.TightGap else 12.dp
    val labels = metrics.map { if (compact && it.unit.isNotEmpty()) "${it.label} ${it.unit}" else it.label }
    @Composable fun Metric(index: Int, modifier: Modifier = Modifier) {
        val metric = metrics[index]
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(labels[index], style = labelStyle, color = muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(metric.value, style = valueStyle, color = ink,
                    modifier = Modifier.testTag(metric.tag).alignByBaseline())
                if (!compact && metric.unit.isNotEmpty()) Text(metric.unit, style = MaterialTheme.typography.bodyMedium, color = muted,
                    modifier = Modifier.alignByBaseline())
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minimumWidth = if (metrics.size >= 3) 100.dp * metrics.size else 112.dp * metrics.size
        val rowFits = if (compact) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val columnWidth = with(density) { ((maxWidth - gap * (metrics.size - 1)) / metrics.size).roundToPx() }
            metrics.indices.all { index ->
                measurer.measure(AnnotatedString(labels[index]), labelStyle, softWrap = false).size.width <= columnWidth &&
                    measurer.measure(AnnotatedString(metrics[index].value), valueStyle, softWrap = false).size.width <= columnWidth
            }
        } else maxWidth >= minimumWidth && fontScale < 1.3f
        if (rowFits) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                metrics.indices.forEach { Metric(it, Modifier.weight(1f)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) gap else RunmoniLayout.ContentGap)) {
                metrics.indices.forEach { Metric(it) }
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
internal fun DistanceNumber(value: String, color: Color, modifier: Modifier = Modifier, maxSize: Float = 64f,
    unit: String? = null) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val unitStyle = MaterialTheme.typography.labelMedium
        val unitWidth = unit?.let {
            val unitPx = rememberTextMeasurer().measure(AnnotatedString(it), unitStyle, softWrap = false).size.width
            with(LocalDensity.current) { unitPx.toDp() } + RunmoniLayout.TightGap
        } ?: 0.dp
        val size = ((maxWidth - unitWidth).value / (value.length.coerceAtLeast(1) * 0.68f * fontScale)).coerceIn(20f, maxSize)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RunmoniLayout.TightGap)) {
            Text(value, modifier.alignByBaseline(), color = color, fontSize = size.sp, lineHeight = (size * 1.15f).sp,
                fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"))
            unit?.let { Text(it, Modifier.alignByBaseline(), style = unitStyle, color = RunmoniTrack.Detail) }
        }
    }
}

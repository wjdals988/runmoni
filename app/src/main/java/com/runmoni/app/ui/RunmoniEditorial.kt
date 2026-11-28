@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.runmoni.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.unit.Dp
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.Crew

@Composable
internal fun EditorialHeading(eyebrow: String, title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (eyebrow.isNotBlank()) Text(eyebrow, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        if (!description.isNullOrBlank()) Text(description, style = MaterialTheme.typography.bodyLarge, color = RunmoniColors.Muted)
    }
}

/** Approved running artwork; decorative and never a map or recorded route. */
@Composable
internal fun RunwayArtwork(avatar: AvatarStyle, modifier: Modifier = Modifier, compact: Boolean = false, running: Boolean = true) {
    StudioAvatarStage(avatar, modifier.fillMaxWidth().height(if (compact) 72.dp else 144.dp), running)
}

/** Decorative track lanes, never a recorded route. The approved sprite is unchanged. */
@Composable
internal fun StudioAvatarStage(avatar: AvatarStyle, modifier: Modifier = Modifier, running: Boolean = false) {
    Box(modifier, contentAlignment = Alignment.Center) {
        RunningTrackBackdrop(Modifier.matchParentSize())
        AvatarMini(avatar, Modifier.fillMaxHeight().aspectRatio(2f / 3f), running = running, decorative = true)
    }
}

/** The runner sits in front of the track; the finish line stays below the clothing. */
@Composable
internal fun RunningTrackBackdrop(modifier: Modifier = Modifier) {
    val footing = RunmoniTrack.Stage
    val marking = RunmoniColors.Primary.copy(alpha = .42f)
    Canvas(modifier.clipToBounds().clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val trackWidth = minOf(w, h * 1.3f)
        val left = (w - trackWidth) / 2
        val stroke = Stroke(1.dp.toPx(), cap = StrokeCap.Round)
        drawOval(footing, Offset(left, h * .75f), Size(trackWidth, h * .23f))
        for (lane in 0..1) {
            val y = h * (.89f + lane * .04f)
            val path = Path().apply {
                moveTo(left + trackWidth * .14f, y)
                quadraticTo(left + trackWidth * .46f, y - h * .09f, left + trackWidth * .87f, y)
            }
            drawPath(path, marking, style = stroke)
        }
    }
}

/** The same telemetry layout frames the current run and its saved result. */
@Composable
internal fun RunningMetricPanel(
    contentPadding: PaddingValues,
    spacing: Dp,
    modifier: Modifier = Modifier,
    accent: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth().clip(RunmoniShapes.Surface).background(RunmoniTrack.Board)) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
            val w = size.width
            val h = size.height
            val rise = minOf(h * .38f, 120.dp.toPx())
            val stroke = Stroke(1.dp.toPx())
            for (lane in 0..2) {
                val gap = lane * 12.dp.toPx()
                val path = Path().apply {
                    moveTo(w * .6f + gap, 0f)
                    cubicTo(w * .69f + gap, rise * .76f, w * .84f + gap, rise, w + gap, rise)
                }
                drawPath(path, RunmoniTrack.Detail.copy(alpha = .2f), style = stroke)
            }
        }
        accent()
        CompositionLocalProvider(LocalContentColor provides RunmoniTrack.OnBoard) {
            Column(Modifier.fillMaxWidth().padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(spacing), content = content)
        }
    }
}

@Composable
internal fun DestinationRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier,
    iconContainer: Color = Color.Transparent, iconTint: Color = RunmoniColors.Primary) {
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(32.dp).background(iconContainer, RunmoniShapes.Action), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = RunmoniColors.Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun DestinationTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, tag: String) {
    ScrollableTabRow(selectedTabIndex = selected, edgePadding = 0.dp, containerColor = Color.Transparent,
        contentColor = RunmoniColors.Primary, divider = { HorizontalDivider(color = RunmoniColors.Line) },
        indicator = { positions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[selected]), color = RunmoniColors.Primary) }) {
        labels.forEachIndexed { index, label ->
            Box(Modifier.heightIn(min = 48.dp)
                .selectable(selected = selected == index, role = Role.Tab, onClick = { onSelect(index) })
                .testTag("${tag}_$index")
                .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Medium),
                    maxLines = 1, softWrap = false,
                    color = if (selected == index) RunmoniColors.Primary else RunmoniColors.Muted)
            }
        }
    }
}

@Composable
internal fun RoomSearch(value: String, onChange: (String) -> Unit, label: String, tag: String,
    placeholder: String = "이름 또는 지역") {
    OutlinedTextField(value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label) }, placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = if (value.isNotEmpty()) { { IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, "검색어 지우기") } } } else null,
        modifier = Modifier.fillMaxWidth().testTag(tag), shape = RunmoniShapes.Input,
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = RunmoniColors.Paper, focusedContainerColor = RunmoniColors.Paper))
}

@Composable
internal fun CrewRoomCard(crew: Crew, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClickLabel = "크루 보기", onClick = onClick).testTag("crew_${crew.id}")) {
        Row(Modifier.padding(vertical = 20.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp).background(RunmoniColors.PrimarySoft, RunmoniShapes.Surface),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Groups, null, tint = RunmoniColors.Primary, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(crew.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null, tint = RunmoniColors.Muted, modifier = Modifier.size(24.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CrewDetail(Icons.Outlined.LocationOn, crew.region.ifBlank { "지역 자유" })
                    CrewDetail(Icons.Outlined.Speed, crew.paceLabel)
                    CrewDetail(Icons.Outlined.PeopleOutline, "${crew.memberCount}명")
                }
                if (crew.description.isNotBlank()) Text(crew.description, style = MaterialTheme.typography.bodyMedium,
                    color = RunmoniColors.Muted, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (crew.joined || crew.isOwner) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(if (crew.isOwner) Icons.Outlined.ManageAccounts else Icons.Outlined.CheckCircleOutline,
                            null, modifier = Modifier.size(16.dp), tint = RunmoniColors.Primary)
                        Text(if (crew.isOwner) "크루장" else "가입한 크루", style = MaterialTheme.typography.labelMedium,
                            color = RunmoniColors.Primary)
                    }
                }
            }
        }
        HorizontalDivider(color = RunmoniColors.Line)
    }
}

/** Text remains explicit: the icons aid scanning rather than replace meaning. */
@Composable
private fun CrewDetail(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = RunmoniColors.Muted)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
    }
}

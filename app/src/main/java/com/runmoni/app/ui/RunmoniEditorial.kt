@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.runmoni.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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

/** Static sports illustration only: no GPS, progress, or achievement is implied. */
@Composable
internal fun RunningTrackBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier.clipToBounds().clearAndSetSemantics { }) {
        // Keep the illustration inside its existing viewport, including tiny previews.
        val w = size.width
        val h = size.height
        drawOval(RunmoniColors.MintWash, Offset(w * .04f, h * .08f), Size(w * .92f, h * .82f))
        drawCircle(RunmoniColors.Lime, radius = minOf(w, h) * .13f, center = Offset(w * .77f, h * .23f))
        drawOval(RunmoniColors.TrackClay, Offset(w * .04f, h * .72f), Size(w * .92f, h * .22f))
        repeat(3) { lane ->
            val inset = w * (.10f + lane * .09f)
            drawOval(RunmoniColors.Paper.copy(alpha = .85f),
                Offset(inset, h * (.75f + lane * .018f)),
                Size(w - inset * 2, h * (.16f - lane * .025f)),
                style = Stroke(width = 1.dp.toPx()))
        }
    }
}

/** A short response to a selection; framework duration scale handles motion disabled. */
@Composable
internal fun selectedTabSurface(selected: Boolean): Color = animateColorAsState(
    if (selected) RunmoniColors.MintWash else RunmoniColors.MintWash.copy(alpha = 0f),
    animationSpec = tween(durationMillis = 150), label = "tab selection",
).value

@Composable
internal fun DestinationRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier,
    iconContainer: Color = RunmoniColors.MintWash, iconTint: Color = RunmoniColors.Primary) {
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(iconContainer, RunmoniShapes.Action), contentAlignment = Alignment.Center) {
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
                .background(selectedTabSurface(selected == index), RunmoniShapes.Action)
                .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false,
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
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(if (crew.joined || crew.isOwner) RunmoniColors.SecondaryMint else RunmoniColors.MintWash,
                RunmoniShapes.Action), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Groups, null, tint = RunmoniColors.Primary, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(crew.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null, tint = RunmoniColors.Primary, modifier = Modifier.size(24.dp))
                }
                Text("${crew.region.ifBlank { "지역 자유" }} · ${crew.paceLabel} · ${crew.memberCount}명", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                if (crew.description.isNotBlank()) Text(crew.description, style = MaterialTheme.typography.bodyMedium,
                    color = RunmoniColors.Muted, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (crew.joined || crew.isOwner) {
                    Text(if (crew.isOwner) "크루장" else "가입한 크루", style = MaterialTheme.typography.labelMedium,
                        color = if (crew.isOwner) RunmoniColors.CoralInk else RunmoniColors.Primary)
                }
            }
        }
        HorizontalDivider(color = RunmoniColors.Line)
    }
}

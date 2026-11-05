@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.runmoni.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.Crew

@Composable
internal fun EditorialHeading(eyebrow: String, title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (eyebrow.any { it.code > 127 }) Text(eyebrow, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
    }
}

/** Approved running artwork; decorative and never a map or recorded route. */
@Composable
internal fun RunwayArtwork(avatar: AvatarStyle, modifier: Modifier = Modifier, compact: Boolean = false, running: Boolean = true) {
    Box(modifier.fillMaxWidth().height(if (compact) 72.dp else 144.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        AvatarMini(avatar, Modifier.width(if (compact) 56.dp else 112.dp).height(if (compact) 70.dp else 144.dp), running = running, decorative = true)
    }
}

@Composable
internal fun DestinationRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = RunmoniColors.Primary, modifier = Modifier.size(24.dp))
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
            Tab(selected = selected == index, onClick = { onSelect(index) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("${tag}_$index"),
                text = { Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1) },
                selectedContentColor = RunmoniColors.Primary, unselectedContentColor = RunmoniColors.Muted)
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
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(crew.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = RunmoniColors.Muted, modifier = Modifier.size(24.dp))
            }
            Text("${crew.region.ifBlank { "지역 자유" }} · ${crew.paceLabel} · ${crew.memberCount}명", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            if (crew.description.isNotBlank()) Text(crew.description, style = MaterialTheme.typography.bodyMedium,
                color = RunmoniColors.Muted, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (crew.joined || crew.isOwner) {
                Text(if (crew.isOwner) "크루장" else "가입한 크루", style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Primary)
            }
        }
        HorizontalDivider(color = RunmoniColors.Line)
    }
}

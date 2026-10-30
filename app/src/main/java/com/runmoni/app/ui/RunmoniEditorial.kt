@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.runmoni.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.Crew

@Composable
internal fun EditorialHeading(eyebrow: String, title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Primary)
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
    }
}

/** Illustration of a track, never a geographic map or recorded route. */
@Composable
internal fun RunwayArtwork(avatar: AvatarStyle, modifier: Modifier = Modifier, compact: Boolean = false, running: Boolean = true) {
    Box(modifier.fillMaxWidth().height(if (compact) 72.dp else 180.dp).clip(RoundedCornerShape(28.dp)).background(RunmoniColors.Mint).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (lane in 0..4) {
                val inset = lane * 22.dp.toPx()
                drawOval(RunmoniColors.Primary.copy(alpha = 0.13f), topLeft = Offset(-size.width * 0.35f + inset, size.height * 0.28f + inset * 0.38f),
                    size = Size(size.width * 1.7f - inset * 2, size.height * 1.3f - inset * 0.5f), style = Stroke(1.dp.toPx()))
            }
        }
        AvatarMini(avatar, Modifier.width(if (compact) 56.dp else 136.dp).height(if (compact) 70.dp else 176.dp), running = running, decorative = true)
    }
}

@Composable
internal fun DestinationRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 80.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(RunmoniColors.Mint), contentAlignment = Alignment.Center) { Icon(icon, null, tint = RunmoniColors.Primary, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted) }
        Icon(Icons.Outlined.ChevronRight, null, tint = RunmoniColors.Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun DestinationTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, tag: String) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            Surface(color = if (selected == index) RunmoniColors.Navy else RunmoniColors.Paper, shape = CircleShape,
                modifier = Modifier.heightIn(min = 48.dp).selectable(selected == index, role = Role.Tab, onClick = { onSelect(index) }).testTag("${tag}_$index")) {
                Box(Modifier.padding(horizontal = if (largeText) 14.dp else 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected == index) RunmoniColors.OnDark else RunmoniColors.Muted) }
            }
        }
    }
}

@Composable
internal fun RoomSearch(value: String, onChange: (String) -> Unit, label: String, tag: String) {
    OutlinedTextField(value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label) }, placeholder = { Text("이름 · 지역 · 페이스") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = if (value.isNotEmpty()) { { IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, "검색어 지우기") } } } else null,
        modifier = Modifier.fillMaxWidth().testTag(tag), shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = RunmoniColors.Paper, focusedContainerColor = RunmoniColors.Paper))
}

@Composable
internal fun CrewRoomCard(crew: Crew, onClick: () -> Unit) {
    Surface(color = RunmoniColors.Paper, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "크루 방 보기", onClick = onClick).testTag("crew_${crew.id}")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(RunmoniColors.Mint), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Groups, null, tint = RunmoniColors.Primary) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(crew.name, style = MaterialTheme.typography.titleLarge); Text("${crew.region.ifBlank { "지역 자유" }} · ${crew.paceLabel}", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted) }
            }
            if (crew.description.isNotBlank()) Text(crew.description, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            HorizontalDivider(color = RunmoniColors.Line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${crew.memberCount}명 · ${if (crew.isOwner) "내가 만든 방" else if (crew.joined) "참여 중" else "입장 가능"}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ArrowForward, null, tint = RunmoniColors.Primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

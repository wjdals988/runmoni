package com.runmoni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.runmoni.app.avatar.RunningKitArtwork
import com.runmoni.app.domain.RunningKitColors
import com.runmoni.app.domain.RunningKitRegion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class KitColorOption(val id: Int, val label: String, val swatch: Color)

// These are display samples of the fabric ramp, independent of inventory IDs.
private val kitColorOptions = listOf(
    KitColorOption(0, "기본색", RunmoniColors.Cream),
    KitColorOption(1, "민트", Color(0xFF21A3A8)),
    KitColorOption(2, "코랄", Color(0xFFF36870)),
    KitColorOption(3, "블루", Color(0xFF3487EC)),
    KitColorOption(4, "퍼플", Color(0xFFA374DF)),
    KitColorOption(5, "골드", Color(0xFFE1AC49)),
)

private fun checkColor(swatch: Color): Color {
    val lightness = swatch.luminance()
    val whiteContrast = 1.05f / (lightness + .05f)
    val inkContrast = (lightness + .05f) / (RunmoniColors.Navy.luminance() + .05f)
    return if (whiteContrast >= inkContrast) Color.White else RunmoniColors.Navy
}

/** A fabric choice changes the selected garment; it never changes the inventory item. */
@Composable
internal fun RunningKitColorControls(
    colors: RunningKitColors,
    region: RunningKitRegion,
    onSelect: (RunningKitRegion, Int) -> Unit,
    modifier: Modifier = Modifier,
    // Deterministic loading/failure fixtures; production verifies the packaged masks.
    availabilityLoader: (suspend () -> Boolean)? = null,
) {
    val context = LocalContext.current.applicationContext
    var attempt by remember(context) { mutableIntStateOf(0) }
    val available by produceState<Boolean?>(null, context, attempt, availabilityLoader) {
        value = null
        value = try {
            withContext(Dispatchers.IO) { availabilityLoader?.invoke() ?: RunningKitArtwork.isAvailable(context) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }
    val selected = colors.normalized().color(region)
    val fontScale = LocalDensity.current.fontScale
    Column(modifier.fillMaxWidth().testTag("kit_colors_${region.wireKey}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("색상", style = MaterialTheme.typography.titleSmall)
        BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
            // Keep labels and 48dp targets, then wrap rather than shrinking text.
            val columns = (maxWidth.value / (60f + 28f * fontScale)).toInt().coerceIn(1, 3)
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                kitColorOptions.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { option ->
                            val isSelected = selected == option.id
                            Row(Modifier.weight(1f).heightIn(min = 48.dp)
                                .selectable(selected = isSelected, enabled = available == true, role = Role.RadioButton,
                                    onClick = { onSelect(region, option.id) })
                                .semantics { contentDescription = "${region.label} 색상" }
                                .testTag("kit_color_${region.wireKey}_${option.id}")
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(24.dp).clearAndSetSemantics { }
                                    .background(option.swatch, CircleShape)
                                    .border(if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) RunmoniColors.Primary else RunmoniColors.Outline, CircleShape),
                                    contentAlignment = Alignment.Center) {
                                    if (isSelected) Icon(Icons.Outlined.Check, null,
                                        tint = checkColor(option.swatch),
                                        modifier = Modifier.size(16.dp))
                                }
                                Text(option.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (available == false) RunmoniColors.Muted else RunmoniColors.Navy)
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        when (available) {
            null -> Text("색상 준비 중", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted,
                modifier = Modifier.testTag("kit_colors_loading"))
            false -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("색상을 불러오지 못했어요. 기본 색상으로 보여드려요.",
                    style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted,
                    modifier = Modifier.testTag("kit_colors_unavailable"))
                TextButton(onClick = { attempt++ }, modifier = Modifier.heightIn(min = 48.dp).testTag("kit_colors_retry")) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("색상 다시 불러오기")
                }
            }
            true -> Text("선택한 ${region.label}에 적용돼요", style = MaterialTheme.typography.bodySmall,
                color = RunmoniColors.Muted, modifier = Modifier.testTag("kit_colors_scope"))
        }
    }
}

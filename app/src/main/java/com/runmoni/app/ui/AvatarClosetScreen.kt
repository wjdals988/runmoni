@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.runmoni.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.runmoni.app.avatar.AvatarArtwork
import com.runmoni.app.domain.*

/** Full-screen route content. The root hides its navigation while this is open. */
@Composable
internal fun ClosetDialog(profile: Profile, actions: AppActions, onDismiss: () -> Unit) {
    var category by rememberSaveable { mutableIntStateOf(3) }
    var accessory by rememberSaveable { mutableIntStateOf(0) }
    var playing by rememberSaveable { mutableStateOf(true) }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val categoryKey = AvatarCatalogue.tabs[category].first
    val accessorySlots = AvatarCatalogue.slots.filter { it.key in AvatarCatalogue.accessoryKeys }
    val slot = if (categoryKey == "accessories") accessorySlots[accessory] else AvatarCatalogue.slots.first { it.key == categoryKey }
    val ready = slot.variants.indices.filter { AvatarArtwork.itemReady(slot.key, it, profile.avatar.gender) }
    val current = profile.avatar.variantFor(slot.key)
    val covered = slot.key in AvatarCatalogue.coveredSlots(profile.avatar.costume)
    val currentReady = AvatarArtwork.itemReady(slot.key, current, profile.avatar.gender)
    val fabricRegion = when (slot.key) {
        "top" -> RunningKitRegion.TOP
        "bottom" -> RunningKitRegion.BOTTOM
        "shoes" -> RunningKitRegion.SHOES
        else -> null
    }
    BackHandler(onBack = onDismiss)
    Surface(Modifier.fillMaxSize().testTag("form_dialog"), color = RunmoniColors.Cream) {
        // This route owns system/IME insets once, outside the activity Scaffold.
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
            .testTag("closet_safe_content")) {
            val previewHeight = if (maxHeight < 420.dp) 88.dp else if (largeText) 104.dp else 156.dp
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("옷장", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { playing = !playing }, modifier = Modifier.size(48.dp).testTag("closet_animation")) {
                        Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "달리기 멈춤" else "달리기 재생")
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("closet_close")) { Text("닫기") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    AvatarMini(profile.avatar, Modifier.width(previewHeight * 2f / 3f).height(previewHeight)
                        .testTag("closet_avatar_preview"), running = playing)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("캐릭터", style = MaterialTheme.typography.titleSmall)
                        GenderSelector(profile, actions, Modifier.fillMaxWidth())
                    }
                }
                ClosetTabs(AvatarCatalogue.tabs, category, { category = it }, "closet_slot")
                if (categoryKey == "accessories") ClosetTabs(accessorySlots.map { it.key to it.label }, accessory, { accessory = it }, "closet_slot")
                key(slot.key) {
                    Column(Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                        .verticalScroll(rememberScrollState()).testTag("closet_item_list")
                        .padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(slot.label, modifier = Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                            Text("모두 무료", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                        }
                        if (!currentReady) Text("이전 선택은 보관 중이에요. 아래 아이템을 착용할 수 있어요.",
                            style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("closet_artwork_status"))
                        else if (covered) Text("아이템을 선택하면 세트가 해제돼요", style = MaterialTheme.typography.bodySmall,
                            color = RunmoniColors.Muted, modifier = Modifier.padding(bottom = 12.dp).testTag("closet_artwork_status"))
                        else Text(if (fabricRegion != null) "바꾸면 기본색으로 착용해요" else "선택하면 바로 착용해요", style = MaterialTheme.typography.bodySmall,
                            color = RunmoniColors.Muted, modifier = Modifier.padding(bottom = 12.dp).testTag("closet_artwork_status"))
                        ready.forEach { variant ->
                            val selected = current == variant && !covered
                            Surface(color = if (selected) RunmoniColors.PrimarySoft else RunmoniColors.Cream,
                                shape = RunmoniShapes.Action,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                    .selectable(selected, role = Role.RadioButton, onClick = { actions.equipItem(slot.key, variant) })
                                    .testTag("closet_${slot.key}_$variant")) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AvatarMini(profile.avatar.withSelectedItem(slot.key, variant), Modifier.width(32.dp).height(48.dp)
                                        .testTag("closet_preview_${slot.key}_$variant"), decorative = true)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(if (variant == 0 && slot.key in setOf("hair", "top", "bottom", "shoes")) "기본 ${slot.label}" else slot.variants[variant],
                                            style = MaterialTheme.typography.titleSmall)
                                        if (selected) Text("착용 중", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Primary)
                                    }
                                    if (selected) Icon(Icons.Outlined.Check, "선택됨", tint = RunmoniColors.Primary, modifier = Modifier.size(20.dp))
                                }
                            }
                            HorizontalDivider(color = RunmoniColors.Line, modifier = Modifier.padding(horizontal = 12.dp))
                        }
                        if (ready.size <= 1 && slot.variants.size > ready.size) Text("추가 아이템을 준비 중이에요",
                            style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted,
                            modifier = Modifier.padding(top = 12.dp).testTag("closet_more_items_pending"))
                        if (fabricRegion != null && currentReady && !covered) {
                            Spacer(Modifier.height(20.dp))
                            RunningKitColorControls(profile.avatar.runningKitColors, fabricRegion, actions::setRunningKitColor)
                        }
                        Spacer(Modifier.height(24.dp).fillMaxWidth().testTag("closet_content_end"))
                    }
                }
            }
        }
    }
}

@Composable
private fun GenderSelector(profile: Profile, actions: AppActions, modifier: Modifier = Modifier) {
    Row(modifier.selectableGroup()) {
        AvatarGender.entries.forEach { gender ->
            val selected = profile.avatar.gender == gender
            Column(Modifier.weight(1f).heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton,
                onClick = { actions.setAvatarGender(gender) }).testTag("closet_gender_${gender.wireValue}"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(gender.label, style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) RunmoniColors.Primary else RunmoniColors.Muted)
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(thickness = if (selected) 2.dp else 1.dp,
                    color = if (selected) RunmoniColors.Primary else RunmoniColors.Line)
            }
        }
    }
}

@Composable
private fun ClosetTabs(items: List<Pair<String, String>>, selected: Int, onSelect: (Int) -> Unit, prefix: String) {
    ScrollableTabRow(selectedTabIndex = selected, edgePadding = 20.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = RunmoniColors.Primary, divider = { HorizontalDivider(color = RunmoniColors.Line) }) {
        items.forEachIndexed { index, (key, label) ->
            Tab(selected = selected == index, onClick = { onSelect(index) }, modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_$key"),
                text = { Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1) })
        }
    }
}

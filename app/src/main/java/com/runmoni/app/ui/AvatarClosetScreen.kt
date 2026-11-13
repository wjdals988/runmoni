@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.runmoni.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    BackHandler(onBack = onDismiss)
    Surface(Modifier.fillMaxSize().testTag("form_dialog"), color = RunmoniColors.Cream) {
        // This route owns system/IME insets once, outside the activity Scaffold.
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
            .testTag("closet_safe_content")) {
            val sidePreview = maxWidth >= 480.dp && maxHeight < 420.dp
            // An unusually short portrait window must scroll its controls rather
            // than reserve all of the available height for fixed decoration.
            val scrollingHeader = !sidePreview && maxHeight < 360.dp
            val previewHeight = if (largeText) 112.dp else 176.dp
            Column(Modifier.fillMaxSize()) {
                PageTopBar("옷장", onDismiss, closeTag = "closet_close", actions = {
                    IconButton(onClick = { playing = !playing }, modifier = Modifier.size(48.dp)
                        .semantics { stateDescription = if (playing) "재생 중" else "멈춤" }
                        .testTag("closet_animation")) {
                        Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            if (playing) "달리기 멈춤" else "달리기 재생")
                    }
                })
                if (sidePreview) {
                    Row(Modifier.weight(1f).widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
                        .align(Alignment.CenterHorizontally).padding(horizontal = pageGutter()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Only the picker scrolls. The fitting preview yields
                        // height to the gender controls in short windows.
                        Column(Modifier.width(if (largeText) 160.dp else 144.dp).fillMaxHeight()
                            .background(RunmoniColors.MintWash, RunmoniShapes.Surface)
                            .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                ClosetAvatarStage(profile.avatar, playing, Modifier.width(96.dp).fillMaxHeight())
                            }
                            GenderSelector(profile, actions, Modifier.fillMaxWidth())
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            ClosetTabs(AvatarCatalogue.tabs, category, { category = it }, "closet_slot", edgePadding = 0.dp)
                            ClosetPicker(profile, actions, slot, categoryKey, accessorySlots, accessory, { accessory = it },
                                Modifier.weight(1f).fillMaxWidth(), horizontalPadding = 0.dp)
                        }
                    }
                } else if (scrollingHeader) {
                    // One vertical region keeps every action reachable even if
                    // the IME or a very short window leaves little room.
                    key(slot.key) {
                        Column(Modifier.weight(1f).widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
                            .align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState())
                            .testTag("closet_item_list")) {
                            ClosetPreview(profile, actions, playing, previewHeight)
                            ClosetTabs(AvatarCatalogue.tabs, category, { category = it }, "closet_slot")
                            ClosetItems(profile, actions, slot, categoryKey, accessorySlots, accessory, { accessory = it },
                                Modifier.background(RunmoniColors.Paper).padding(horizontal = pageGutter()))
                        }
                    }
                } else {
                    ClosetPreview(profile, actions, playing, previewHeight)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()) {
                            ClosetTabs(AvatarCatalogue.tabs, category, { category = it }, "closet_slot")
                        }
                    }
                    ClosetPicker(profile, actions, slot, categoryKey, accessorySlots, accessory, { accessory = it },
                        Modifier.weight(1f).widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
                            .align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
private fun ClosetPreview(profile: Profile, actions: AppActions, playing: Boolean, height: Dp) {
    Box(Modifier.fillMaxWidth().background(RunmoniColors.MintWash), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
            .padding(horizontal = pageGutter(), vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ClosetAvatarStage(profile.avatar, playing, Modifier.width(height * 2f / 3f).height(height))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("현재 착용", style = MaterialTheme.typography.titleMedium)
                GenderSelector(profile, actions, Modifier.fillMaxWidth())
            }
        }
    }
}

/** A static track beneath the approved sprite, never a route or replacement artwork. */
@Composable
private fun ClosetAvatarStage(avatar: AvatarStyle, playing: Boolean, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        RunningTrackBackdrop(Modifier.matchParentSize())
        AvatarMini(avatar, Modifier.fillMaxSize().testTag("closet_avatar_preview"), running = playing)
    }
}

@Composable
private fun ClosetPicker(
    profile: Profile, actions: AppActions, slot: AvatarSlot, categoryKey: String,
    accessorySlots: List<AvatarSlot>, accessory: Int, onAccessory: (Int) -> Unit,
    modifier: Modifier = Modifier, horizontalPadding: Dp = pageGutter(),
) {
    key(slot.key) {
        Column(modifier.background(RunmoniColors.Paper).verticalScroll(rememberScrollState()).testTag("closet_item_list")) {
            ClosetItems(profile, actions, slot, categoryKey, accessorySlots, accessory, onAccessory,
                Modifier.padding(horizontal = horizontalPadding))
        }
    }
}

@Composable
private fun ClosetItems(
    profile: Profile, actions: AppActions, slot: AvatarSlot, categoryKey: String,
    accessorySlots: List<AvatarSlot>, accessory: Int, onAccessory: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
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
    Column(modifier.fillMaxWidth()) {
        // Secondary accessory categories share the item scroll region so they
        // cannot consume the remaining landscape/large-text list height.
        if (categoryKey == "accessories") {
            ClosetTabs(accessorySlots.map { it.key to it.label }, accessory, onAccessory, "closet_slot", edgePadding = 0.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text(slot.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (currentReady && !covered) {
            Spacer(Modifier.height(4.dp))
            Text("현재 선택: ${closetItemLabel(slot, current)}", style = MaterialTheme.typography.bodyMedium,
                color = RunmoniColors.Muted)
        }
        Spacer(Modifier.height(4.dp))
        Text("모두 무료", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
        if (!AvatarArtwork.styleReady(profile.avatar)) {
            Spacer(Modifier.height(8.dp))
            Text("미리보기가 없는 이전 선택은 보관 중이에요", style = MaterialTheme.typography.bodySmall,
                color = RunmoniColors.Muted, modifier = Modifier.testTag("closet_artwork_status"))
        } else if (covered) {
            Spacer(Modifier.height(8.dp))
            Text("아이템을 선택하면 세트가 해제돼요", style = MaterialTheme.typography.bodySmall,
                color = RunmoniColors.Muted, modifier = Modifier.testTag("closet_artwork_status"))
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            ready.forEachIndexed { index, variant ->
                val selected = current == variant && !covered
                Row(Modifier.fillMaxWidth().heightIn(min = if (largeText) 64.dp else 88.dp)
                    .background(if (selected) RunmoniColors.PrimarySoft else Color.Transparent, RunmoniShapes.Action)
                    .selectable(selected, role = Role.RadioButton, onClick = { actions.equipItem(slot.key, variant) })
                    .testTag("closet_${slot.key}_$variant").padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Compare the same approved character wearing each choice.
                    // Large text keeps the full row width for the item's name.
                    if (!largeText) Box(Modifier.width(48.dp).height(72.dp), contentAlignment = Alignment.Center) {
                        AvatarMini(profile.avatar.withSelectedItem(slot.key, variant), Modifier.width(48.dp).height(72.dp)
                            .testTag("closet_preview_${slot.key}_$variant"), decorative = true)
                    }
                    Text(closetItemLabel(slot, variant), style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f))
                    if (selected) Icon(Icons.Outlined.Check, "선택됨", tint = RunmoniColors.Primary,
                        modifier = Modifier.size(20.dp))
                    else Spacer(Modifier.size(20.dp))
                }
                if (index != ready.lastIndex) {
                    HorizontalDivider(color = RunmoniColors.Line,
                        modifier = Modifier.padding(start = if (largeText) 12.dp else 72.dp, end = 12.dp))
                }
            }
        }
        if (fabricRegion != null && currentReady && !covered) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = RunmoniColors.Line)
            Spacer(Modifier.height(20.dp))
            RunningKitColorControls(profile.avatar.runningKitColors, fabricRegion, actions::setRunningKitColor)
        }
        Spacer(Modifier.height(24.dp).fillMaxWidth().testTag("closet_content_end"))
    }
}

private fun closetItemLabel(slot: AvatarSlot, variant: Int): String =
    if (variant == 0 && slot.key in setOf("hair", "top", "bottom", "shoes")) "기본 ${slot.label}" else slot.variants[variant]

@Composable
private fun GenderSelector(profile: Profile, actions: AppActions, modifier: Modifier = Modifier) {
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AvatarGender.entries.forEach { gender ->
            val selected = profile.avatar.gender == gender
            Column(Modifier.weight(1f).heightIn(min = 48.dp).clip(RunmoniShapes.Action)
                .background(if (selected) RunmoniColors.Paper else Color.Transparent)
                .selectable(selected, role = Role.RadioButton,
                onClick = { actions.setAvatarGender(gender) }).testTag("closet_gender_${gender.wireValue}")
                .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(gender.label, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) RunmoniColors.Primary else RunmoniColors.Muted)
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(modifier = Modifier.width(16.dp), thickness = 2.dp,
                    color = if (selected) RunmoniColors.Primary else Color.Transparent)
            }
        }
    }
}

@Composable
private fun ClosetTabs(
    items: List<Pair<String, String>>, selected: Int, onSelect: (Int) -> Unit, prefix: String,
    edgePadding: Dp = pageGutter(),
) {
    ScrollableTabRow(selectedTabIndex = selected, edgePadding = edgePadding,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = RunmoniColors.Primary, divider = { HorizontalDivider(color = RunmoniColors.Line) }) {
        items.forEachIndexed { index, (key, label) ->
            Tab(selected = selected == index, onClick = { onSelect(index) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_$key"),
                text = { Text(label, style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal, maxLines = 1) })
        }
    }
}

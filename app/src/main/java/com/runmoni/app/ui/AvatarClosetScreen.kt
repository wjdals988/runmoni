@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.runmoni.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.runmoni.app.avatar.AvatarArtwork
import com.runmoni.app.domain.*

@Composable
internal fun ClosetDialog(profile: Profile, actions: AppActions, onDismiss: () -> Unit) {
    var reference by remember { mutableStateOf(false) }
    var category by remember { mutableIntStateOf(3) }
    var accessory by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    if (reference) { AvatarReferenceDialog(onDismiss = { reference = false }); return }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("form_dialog"), color = RunmoniColors.Cream) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
                val previewHeight = if (maxHeight < 400.dp) 64.dp else if (largeText) 80.dp else 164.dp
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("옷장", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { playing = !playing }, modifier = Modifier.size(48.dp).testTag("closet_animation")) {
                            Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "달리기 멈춤" else "달리기 재생")
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
                    }
                    Surface(color = RunmoniColors.Mint, modifier = Modifier.fillMaxWidth().height(previewHeight)) {
                        Box(contentAlignment = Alignment.Center) {
                            AvatarMini(profile.avatar, Modifier.width(previewHeight * .68f).height(previewHeight).testTag("closet_avatar_preview"), running = playing)
                        }
                    }
                    Column(Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Spacer(Modifier.height(0.dp))
                        if (largeText) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("기본형", style = MaterialTheme.typography.titleMedium)
                            GenderSelector(profile, actions, Modifier.fillMaxWidth())
                        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("기본형", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            GenderSelector(profile, actions)
                        }
                        Text(if (AvatarArtwork.styleReady(profile.avatar)) "테스트 기간 · 모든 아이템 무료" else "선택은 저장됐어요 · 미리보기는 기본형을 표시해요",
                            style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted, modifier = Modifier.testTag("closet_artwork_status"))
                        ClosetTabs(AvatarCatalogue.tabs, category, { category = it }, "closet_slot")
                        val key = AvatarCatalogue.tabs[category].first
                        val sub = AvatarCatalogue.slots.filter { it.key in AvatarCatalogue.accessoryKeys }
                        val slot = if (key == "accessories") sub[accessory] else AvatarCatalogue.slots.first { it.key == key }
                        if (key == "accessories") ClosetTabs(sub.map { it.key to it.label }, accessory, { accessory = it }, "closet_slot")
                        val covered = slot.key in AvatarCatalogue.coveredSlots(profile.avatar.costume)
                        if (covered) Text("일반 아이템을 선택하면 세트가 해제돼요", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                        val columns = if (largeText) 1 else 2
                        slot.variants.indices.toList().chunked(columns).forEach { variants ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                variants.forEach { variant ->
                                    val ready = AvatarArtwork.itemReady(slot.key, variant)
                                    val selected = profile.avatar.variantFor(slot.key) == variant && !covered
                                    Surface(shape = RoundedCornerShape(16.dp), color = if (selected) RunmoniColors.Mint else RunmoniColors.Paper,
                                        border = if (selected) BorderStroke(1.5.dp, RunmoniColors.Primary) else null,
                                        modifier = Modifier.weight(1f).selectable(selected, role = Role.RadioButton,
                                            onClick = { actions.equipItem(slot.key, variant) }).testTag("closet_${slot.key}_$variant")) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                                    if (ready) AvatarMini(profile.avatar.withItem(slot.key, variant), Modifier.fillMaxSize(), decorative = true)
                                                    else Icon(Icons.Outlined.Checkroom, null, tint = RunmoniColors.Muted, modifier = Modifier.size(32.dp))
                                                }
                                                Spacer(Modifier.weight(1f))
                                                if (selected) Icon(Icons.Outlined.CheckCircle, "선택됨", tint = RunmoniColors.Primary, modifier = Modifier.size(20.dp))
                                            }
                                            Text(if (variant == 0 && slot.key in setOf("hair", "top", "bottom", "shoes")) "기본 ${slot.label}" else slot.variants[variant],
                                                style = MaterialTheme.typography.titleSmall, modifier = Modifier.heightIn(min = if (largeText) 0.dp else 72.dp))
                                            Text("무료 · 0 P", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Primary)
                                            Text(if (ready) if (selected) "착용 중" else "바로 착용" else if (selected) "선택 보관 · 미리보기 준비 중" else "선택 저장 · 미리보기 준비 중", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                                        }
                                    }
                                }
                                if (variants.size < columns) Spacer(Modifier.weight(1f))
                            }
                        }
                        Text("포인트 차감 없이 한 번 눌러 선택해요. 기존 포인트와 보유 내역은 보관돼요.", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                        TextButton(onClick = { reference = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("open_avatar_reference")) {
                            Text("원화 · 수영복 시안 비교"); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GenderSelector(profile: Profile, actions: AppActions, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        AvatarGender.entries.forEachIndexed { index, gender ->
            SegmentedButton(selected = profile.avatar.gender == gender, onClick = { actions.setAvatarGender(gender) },
                shape = SegmentedButtonDefaults.itemShape(index, AvatarGender.entries.size),
                modifier = Modifier.heightIn(min = 48.dp).testTag("closet_gender_${gender.wireValue}")) { Text(gender.label) }
        }
    }
}

@Composable
private fun ClosetTabs(items: List<Pair<String, String>>, selected: Int, onSelect: (Int) -> Unit, prefix: String) {
    ScrollableTabRow(selectedTabIndex = selected, edgePadding = 0.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = RunmoniColors.Primary, divider = { HorizontalDivider(color = RunmoniColors.Line) }) {
        items.forEachIndexed { index, (key, label) ->
            Tab(selected = selected == index, onClick = { onSelect(index) }, modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_$key"),
                text = { Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1) })
        }
    }
}

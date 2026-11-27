@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.runmoni.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.runmoni.app.avatar.AvatarArtwork
import com.runmoni.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Full-screen route content. The root hides its navigation while this is open. */
@Composable
internal fun ClosetDialog(profile: Profile, actions: AppActions, onDismiss: () -> Unit) {
    var category by rememberSaveable { mutableIntStateOf(3) }
    var accessory by rememberSaveable { mutableIntStateOf(0) }
    var playing by rememberSaveable { mutableStateOf(true) }
    // Each request owns its acknowledgement. A draw callback from an old outfit
    // can only mark its old state and cannot unlock a newer outfit's thumbnails.
    val primaryDrawn = remember(profile.avatar, playing) { mutableStateOf(false) }
    val onPrimaryFrame: (Int) -> Unit = remember(primaryDrawn) {
        { if (!primaryDrawn.value) primaryDrawn.value = true }
    }
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val categoryKey = AvatarCatalogue.tabs[category].first
    val accessorySlots = AvatarCatalogue.slots.filter { it.key in AvatarCatalogue.accessoryKeys }
    val slot = if (categoryKey == "accessories") accessorySlots[accessory] else AvatarCatalogue.slots.first { it.key == categoryKey }
    // Keep the input above responsive branches: opening the IME can turn the
    // pinned fitting scene into one scroll region without losing the query.
    var query by rememberSaveable(slot.key) { mutableStateOf("") }
    BackHandler(onBack = onDismiss)
    Surface(Modifier.fillMaxSize().testTag("form_dialog"), color = RunmoniColors.Cream) {
        // This route owns system/IME insets once, outside the activity Scaffold.
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
            .testTag("closet_safe_content")) {
            val sidePreview = maxWidth >= 480.dp && maxHeight < 420.dp
            // An unusually short portrait window must scroll its controls rather
            // than reserve all of the available height for fixed decoration.
            val scrollingHeader = !sidePreview && (maxHeight < 360.dp || (largeText && maxHeight < 420.dp))
            val previewHeight = when {
                largeText -> 96.dp
                maxWidth < 360.dp || maxHeight < 600.dp -> 128.dp
                else -> 160.dp
            }
            val usePrimaryGate = !scrollingHeader && maxHeight >= 240.dp
            val secondaryReady = !usePrimaryGate || primaryDrawn.value
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
                            .background(RunmoniColors.Paper, RunmoniShapes.Surface)
                            .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                ClosetAvatarStage(profile.avatar, playing, Modifier.width(96.dp).fillMaxHeight(),
                                    onFrameRendered = if (usePrimaryGate) onPrimaryFrame else null)
                            }
                            GenderSelector(profile, actions, Modifier.fillMaxWidth())
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            ClosetFittedItem(profile.avatar, slot)
                            ClosetTabs(AvatarCatalogue.tabs, category, { category = it }, "closet_slot", edgePadding = 0.dp)
                            ClosetPicker(profile, actions, slot, categoryKey, accessorySlots, accessory, { accessory = it },
                                Modifier.weight(1f).fillMaxWidth(), horizontalPadding = 0.dp,
                                secondaryReady = secondaryReady, query = query, onQuery = { query = it })
                        }
                    }
                } else if (scrollingHeader) {
                    // One vertical region keeps every action reachable even if
                    // the IME or a very short window leaves little room.
                    key(slot.key) {
                        Column(Modifier.weight(1f).widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
                            .align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState())
                            .testTag("closet_item_list")) {
                            ClosetPreview(profile, actions, playing, previewHeight, slot)
                            ClosetTabs(AvatarCatalogue.tabs, category, { category = it }, "closet_slot")
                            ClosetItems(profile, actions, slot, categoryKey, accessorySlots, accessory, { accessory = it },
                                Modifier.background(RunmoniColors.Paper).padding(horizontal = pageGutter()),
                                secondaryReady = true, query = query, onQuery = { query = it })
                        }
                    }
                } else {
                    ClosetPreview(profile, actions, playing, previewHeight, slot,
                        onFrameRendered = if (usePrimaryGate) onPrimaryFrame else null)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()) {
                            ClosetTabs(AvatarCatalogue.tabs, category, { category = it }, "closet_slot")
                        }
                    }
                    ClosetPicker(profile, actions, slot, categoryKey, accessorySlots, accessory, { accessory = it },
                        Modifier.weight(1f).widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
                            .align(Alignment.CenterHorizontally),
                        secondaryReady = secondaryReady, query = query, onQuery = { query = it })
                }
            }
        }
    }
}

@Composable
private fun ClosetPreview(profile: Profile, actions: AppActions, playing: Boolean, height: Dp, slot: AvatarSlot,
    onFrameRendered: ((Int) -> Unit)? = null,
) {
    Box(Modifier.fillMaxWidth().background(RunmoniColors.Paper), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
            .padding(horizontal = pageGutter(), vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ClosetAvatarStage(profile.avatar, playing, Modifier.width(height * 2f / 3f).height(height), onFrameRendered)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClosetFittedItem(profile.avatar, slot)
                GenderSelector(profile, actions, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ClosetFittedItem(avatar: AvatarStyle, slot: AvatarSlot) {
    val selected = avatar.variantFor(slot.key)
    val visible = slot.key !in AvatarCatalogue.coveredSlots(avatar.costume) &&
        AvatarArtwork.itemReady(slot.key, selected, avatar.gender)
    Text(if (visible) avatarItemDisplayName(slot, selected, avatar.gender) else "현재 착용",
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("closet_fitted_item"))
}

/** A static track beneath the approved sprite, never a route or replacement artwork. */
@Composable
private fun ClosetAvatarStage(avatar: AvatarStyle, playing: Boolean, modifier: Modifier = Modifier,
    onFrameRendered: ((Int) -> Unit)? = null,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        RunningTrackBackdrop(Modifier.matchParentSize())
        AvatarMini(avatar, Modifier.fillMaxSize().testTag("closet_avatar_preview"), running = playing,
            onFrameRendered = onFrameRendered)
    }
}

@Composable
private fun ClosetPicker(
    profile: Profile, actions: AppActions, slot: AvatarSlot, categoryKey: String,
    accessorySlots: List<AvatarSlot>, accessory: Int, onAccessory: (Int) -> Unit,
    modifier: Modifier = Modifier, horizontalPadding: Dp = pageGutter(),
    secondaryReady: Boolean = true,
    query: String, onQuery: (String) -> Unit,
) {
    key(slot.key) {
        Column(modifier.background(RunmoniColors.Paper).verticalScroll(rememberScrollState()).testTag("closet_item_list")) {
            ClosetItems(profile, actions, slot, categoryKey, accessorySlots, accessory, onAccessory,
                Modifier.padding(horizontal = horizontalPadding), secondaryReady, query, onQuery)
        }
    }
}

@Composable
private fun ClosetItems(
    profile: Profile, actions: AppActions, slot: AvatarSlot, categoryKey: String,
    accessorySlots: List<AvatarSlot>, accessory: Int, onAccessory: (Int) -> Unit,
    modifier: Modifier = Modifier,
    secondaryReady: Boolean = true,
    query: String, onQuery: (String) -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val ready = slot.variants.indices.filter { AvatarArtwork.itemReady(slot.key, it, profile.avatar.gender) }
    val current = profile.avatar.variantFor(slot.key)
    val covered = slot.key in AvatarCatalogue.coveredSlots(profile.avatar.costume)
    val currentReady = AvatarArtwork.itemReady(slot.key, current, profile.avatar.gender)
    // Search belongs to this part. A query from shoes must not silently hide
    // shirts when the runner changes category or accessory subcategory.
    val shown = if (query.isBlank()) ready else ready.filter { variant ->
        avatarItemDisplayName(slot, variant, profile.avatar.gender).contains(query.trim(), ignoreCase = true)
    }
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(slot.label, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() })
            Text(if (query.isBlank()) "${ready.size}개" else "검색 ${shown.size}개",
                style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted,
                modifier = Modifier.testTag("closet_item_count"))
        }
        Spacer(Modifier.height(4.dp))
        Text("모든 아이템 무료", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
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
        ClosetSearch(query, onQuery)
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // One fitting scene, then small actual garment details. At narrow
            // large type the full scene stays visible while names get the width.
            val showThumbnail = !largeText || maxWidth >= 360.dp
            Column(Modifier.fillMaxWidth().selectableGroup().testTag("closet_compare_list")) {
                shown.forEachIndexed { index, variant ->
                    ClosetItemOption(profile, actions, slot, variant, current == variant && !covered,
                        secondaryReady, showThumbnail, Modifier.fillMaxWidth())
                    if (index < shown.lastIndex) HorizontalDivider(color = RunmoniColors.Line,
                        modifier = Modifier.padding(start = if (showThumbnail) 103.dp else 27.dp, end = 12.dp))
                }
                if (shown.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag("closet_search_empty"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("일치하는 아이템이 없어요", style = MaterialTheme.typography.titleMedium)
                        Text("다른 이름으로 찾거나 검색어를 지워보세요.", style = MaterialTheme.typography.bodyMedium,
                            color = RunmoniColors.Muted)
                        TextButton(onClick = { onQuery("") }, modifier = Modifier.heightIn(min = 48.dp)
                            .testTag("closet_search_reset")) { Text("검색어 지우기") }
                    }
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

@Composable
private fun ClosetSearch(query: String, onQuery: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(value = query, onValueChange = { onQuery(it.take(40)) },
        modifier = Modifier.fillMaxWidth().testTag("closet_search"),
        label = { Text("이름 검색") }, singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = RunmoniColors.Muted) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQuery("") }, modifier = Modifier.size(48.dp).testTag("closet_search_clear")) {
                    Icon(Icons.Outlined.Close, "검색어 지우기")
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        shape = RunmoniShapes.Input)
}

@Composable
private fun ClosetItemOption(
    profile: Profile, actions: AppActions, slot: AvatarSlot, variant: Int, selected: Boolean,
    secondaryReady: Boolean, showThumbnail: Boolean, modifier: Modifier = Modifier,
) {
    val choice = modifier.heightIn(min = 48.dp).clip(RunmoniShapes.Action)
        .background(if (selected) RunmoniColors.PrimarySoft else Color.Transparent)
        .selectable(selected, role = Role.RadioButton, onClick = { actions.equipItem(slot.key, variant) })
        .testTag("closet_${slot.key}_$variant")
    Row(choice.heightIn(min = if (showThumbnail) 88.dp else 64.dp).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(3.dp).height(32.dp)
            .background(if (selected) RunmoniColors.Primary else Color.Transparent, RunmoniShapes.Status))
        if (showThumbnail) ClosetItemThumbnail(profile.avatar.withSelectedItem(slot.key, variant), slot.key,
            secondaryReady, "closet_preview_${slot.key}_$variant", Modifier.size(64.dp)
                .clip(RunmoniShapes.Action).background(if (selected) RunmoniColors.PrimarySoft else RunmoniColors.Cream))
        Text(avatarItemDisplayName(slot, variant, profile.avatar.gender), style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.Check, "선택됨", tint = RunmoniColors.Primary,
            modifier = Modifier.size(20.dp))
        else Spacer(Modifier.size(20.dp))
    }
}

private data class ClosetThumbnail(val image: ImageBitmap? = null, val failed: Boolean = false)
private data class ClosetCrop(val x: Int, val y: Int, val width: Int, val height: Int)

/** Frame zero regions of the real composite, including its actual occlusion.
 * A set uses the complete frame. No standalone product artwork is invented.
 */
private fun closetCrop(slot: String, gender: AvatarGender): ClosetCrop {
    val female = gender == AvatarGender.FEMALE
    return when (slot) {
        "hair", "headwear", "face" -> ClosetCrop(0, 0, 256, 208)
        "ear", "eyewear" -> ClosetCrop(60, 64, 184, 132)
        "top", "hydration" -> ClosetCrop(40, if (female) 156 else 168, 200, 112)
        "bottom" -> ClosetCrop(64, if (female) 224 else 236, 160, 112)
        "wrist" -> ClosetCrop(32, 184, 208, 104)
        "waist" -> ClosetCrop(64, if (female) 212 else 228, 160, 84)
        "socks" -> ClosetCrop(12, 268, 232, 116)
        "shoes" -> ClosetCrop(140, 304, 116, 80)
        else -> ClosetCrop(0, 0, 256, 384)
    }
}

@Composable
private fun ClosetItemThumbnail(avatar: AvatarStyle, slot: String, ready: Boolean, tag: String, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    var attempt by remember(avatar, slot) { mutableIntStateOf(0) }
    // Never label the previous request's image as a newly selected garment.
    key(context, avatar, slot, ready, attempt) {
        val prepared by produceState(ClosetThumbnail()) {
            if (ready) value = try {
                ClosetThumbnail(withContext(Dispatchers.Default) { avatarBitmap(context, avatar).asImageBitmap() })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ClosetThumbnail(failed = true)
            }
        }
        Box(modifier.testTag(tag), contentAlignment = Alignment.Center) {
            val image = prepared.image
            if (image != null) Canvas(Modifier.fillMaxSize()) {
                val crop = closetCrop(slot, avatar.gender)
                val scale = minOf(size.width / crop.width, size.height / crop.height)
                val width = (crop.width * scale).roundToInt().coerceAtLeast(1)
                val height = (crop.height * scale).roundToInt().coerceAtLeast(1)
                drawImage(image, srcOffset = IntOffset(crop.x, crop.y), srcSize = IntSize(crop.width, crop.height),
                    dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
                    dstSize = IntSize(width, height), filterQuality = FilterQuality.None)
            }
            else if (prepared.failed) IconButton(onClick = { attempt++ }, modifier = Modifier.size(48.dp)
                .testTag("minimi_retry")) {
                Icon(Icons.Outlined.Refresh, "아이템 미리보기 다시 불러오기", tint = RunmoniColors.Muted)
            }
            else Icon(Icons.Outlined.HourglassEmpty, null,
                modifier = Modifier.size(24.dp).testTag("minimi_loading"), tint = RunmoniColors.Muted)
        }
    }
}

/** Display only: the approved male/female originals share stable item zero IDs.
 * Name the actual shape without a fixed hue, since fabric tint is independent.
 */
internal fun avatarItemDisplayName(slot: AvatarSlot, variant: Int, gender: AvatarGender): String {
    if (variant != 0) return slot.variants[variant]
    val female = gender == AvatarGender.FEMALE
    return when (slot.key) {
        "hair" -> if (female) "기본 포니테일" else "기본 쇼트 헤어"
        "top" -> if (female) "기본 러닝 반팔" else "기본 러닝 싱글렛"
        "bottom" -> if (female) "기본 러닝 타이츠" else "기본 러닝 쇼츠"
        "shoes" -> "기본 러닝화"
        else -> slot.variants[variant]
    }
}

@Composable
private fun GenderSelector(profile: Profile, actions: AppActions, modifier: Modifier = Modifier) {
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AvatarGender.entries.forEach { gender ->
            val selected = profile.avatar.gender == gender
            Column(Modifier.weight(1f).heightIn(min = 48.dp).clip(RunmoniShapes.Action)
                .background(if (selected) RunmoniColors.PrimarySoft else Color.Transparent)
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

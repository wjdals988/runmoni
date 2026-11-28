@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.runmoni.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
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
import androidx.compose.ui.platform.LocalFocusManager
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Full-screen route content. The root hides its navigation while this is open. */
@Composable
internal fun ClosetDialog(profile: Profile, actions: AppActions, onDismiss: () -> Unit) {
    var category by rememberSaveable { mutableIntStateOf(3) }
    var accessory by rememberSaveable { mutableIntStateOf(0) }
    var playing by rememberSaveable { mutableStateOf(true) }
    val motionAllowed = rememberPageAnimationsEnabled()
    val isPlaying = playing && motionAllowed
    var showParts by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val openParts = { keyboard?.hide(); focus.clearFocus(); showParts = true }
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
    val insets = legacyEditablePageWindowPolicy() ?: WindowInsets.safeDrawing.union(WindowInsets.ime)
    BackHandler(onBack = onDismiss)
    Surface(Modifier.fillMaxSize().testTag("form_dialog"), color = RunmoniColors.Cream) {
        // This route owns system/IME insets once, outside the activity Scaffold.
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(insets)
            .testTag("closet_safe_content")) {
            val sidePreview = maxWidth >= 480.dp && maxHeight < 420.dp
            val compactList = largeText && maxWidth < 360.dp
            // An unusually short portrait window must scroll its controls rather
            // than reserve all of the available height for fixed decoration.
            val scrollingHeader = !sidePreview && (maxHeight < 360.dp || (largeText && maxHeight < 420.dp))
            val previewHeight = if (largeText) 96.dp else 132.dp
            val previewWidth = if (compactList) 64.dp else 88.dp
            val usePrimaryGate = !scrollingHeader && maxHeight >= 240.dp
            val secondaryReady = !usePrimaryGate || primaryDrawn.value
            Column(Modifier.fillMaxSize()) {
                PageTopBar("옷장", onDismiss, closeTag = "closet_close", compact = compactList,
                    trailingInset = pageGutter(), actions = {
                    IconButton(onClick = { playing = !playing }, enabled = motionAllowed, modifier = Modifier.size(48.dp)
                        .semantics { stateDescription = when {
                            !motionAllowed -> "시스템 설정으로 멈춤"
                            isPlaying -> "재생 중"
                            else -> "멈춤"
                        } }
                        .testTag("closet_animation")) {
                        Icon(if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            if (isPlaying) "달리기 멈춤" else "달리기 재생")
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
                            ClosetPartBar(category, { category = it }, openParts)
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
                            ClosetPreview(profile, actions, playing, previewHeight, previewWidth, slot)
                            ClosetPartBar(category, { category = it }, openParts, Modifier.padding(horizontal = pageGutter()))
                            ClosetItems(profile, actions, slot, categoryKey, accessorySlots, accessory, { accessory = it },
                                Modifier.background(RunmoniColors.Paper).padding(horizontal = pageGutter()),
                                secondaryReady = true, query = query, onQuery = { query = it }, compactList = compactList)
                        }
                    }
                } else {
                    ClosetPreview(profile, actions, playing, previewHeight, previewWidth, slot,
                        onFrameRendered = if (usePrimaryGate) onPrimaryFrame else null)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()) {
                            ClosetPartBar(category, { category = it }, openParts, Modifier.padding(horizontal = pageGutter()))
                        }
                    }
                    ClosetPicker(profile, actions, slot, categoryKey, accessorySlots, accessory, { accessory = it },
                        Modifier.weight(1f).widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
                            .align(Alignment.CenterHorizontally),
                        secondaryReady = secondaryReady, query = query, onQuery = { query = it }, compactList = compactList)
                }
            }
        }
    }
    if (showParts) ClosetPartsDialog(slot.key, accessorySlots, onDismiss = { showParts = false }) { key ->
        val partIndex = AvatarCatalogue.tabs.indexOfFirst { it.first == key }
        if (partIndex >= 0) category = partIndex else {
            category = AvatarCatalogue.tabs.indexOfFirst { it.first == "accessories" }
            accessory = accessorySlots.indexOfFirst { it.key == key }
        }
        showParts = false
    }
}

@Composable
private fun ClosetPreview(profile: Profile, actions: AppActions, playing: Boolean, height: Dp, width: Dp, slot: AvatarSlot,
    onFrameRendered: ((Int) -> Unit)? = null,
) {
    Box(Modifier.fillMaxWidth().background(RunmoniColors.Paper), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
            .padding(horizontal = pageGutter(), vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ClosetAvatarStage(profile.avatar, playing,
                Modifier.width(width).height(height).align(Alignment.Top), onFrameRendered)
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

/** The fitting preview puts the current Minimi in front of a static track. */
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
    compactList: Boolean = false,
) {
    key(slot.key) {
        Column(modifier.background(RunmoniColors.Paper).verticalScroll(rememberScrollState()).testTag("closet_item_list")) {
            ClosetItems(profile, actions, slot, categoryKey, accessorySlots, accessory, onAccessory,
                Modifier.padding(horizontal = horizontalPadding), secondaryReady, query, onQuery, compactList)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ClosetItems(
    profile: Profile, actions: AppActions, slot: AvatarSlot, categoryKey: String,
    accessorySlots: List<AvatarSlot>, accessory: Int, onAccessory: (Int) -> Unit,
    modifier: Modifier = Modifier,
    secondaryReady: Boolean = true,
    query: String, onQuery: (String) -> Unit,
    compactList: Boolean = false,
) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val ready = slot.variants.indices.filter { AvatarArtwork.itemReady(slot.key, it, profile.avatar.gender) }
    val current = profile.avatar.variantFor(slot.key)
    val covered = slot.key in AvatarCatalogue.coveredSlots(profile.avatar.costume)
    val currentReady = AvatarArtwork.itemReady(slot.key, current, profile.avatar.gender)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
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
    }?.takeIf { currentReady && !covered }
    val colorPanel = remember { BringIntoViewRequester() }
    val itemSearch = remember { BringIntoViewRequester() }
    val wornItem = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth()) {
        // Secondary accessory categories share the item scroll region so they
        // cannot consume the remaining landscape/large-text list height.
        if (categoryKey == "accessories") {
            ClosetTabs(accessorySlots.map { it.key to it.label }, accessory, onAccessory, "closet_slot", edgePadding = 0.dp)
        }
        val gap = if (compactList) 8.dp else 16.dp
        Spacer(Modifier.height(gap))
        if (!compactList || query.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(slot.label, style = if (compactList) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() })
                Text(if (query.isBlank()) "${ready.size}개" else "검색 ${shown.size}개",
                    style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted,
                    modifier = Modifier.testTag("closet_item_count"))
            }
            if (!compactList) {
                Spacer(Modifier.height(4.dp))
                Text("모든 아이템 무료", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
            }
            Spacer(Modifier.height(gap))
        }
        if (!AvatarArtwork.styleReady(profile.avatar)) {
            Text("미리보기가 없는 이전 선택은 보관 중이에요", style = MaterialTheme.typography.bodySmall,
                color = RunmoniColors.Muted, modifier = Modifier.testTag("closet_artwork_status"))
            Spacer(Modifier.height(gap))
        } else if (covered) {
            Text("아이템을 선택하면 세트가 해제돼요", style = MaterialTheme.typography.bodySmall,
                color = RunmoniColors.Muted, modifier = Modifier.testTag("closet_artwork_status"))
            Spacer(Modifier.height(gap))
        }
        if (fabricRegion != null) TextButton(onClick = {
            keyboard?.hide()
            focus.clearFocus()
            scope.launch { colorPanel.bringIntoView() }
        }, modifier = Modifier.heightIn(min = 48.dp).testTag("closet_open_colors")) {
            Text("${fabricRegion.label} 색상 바꾸기")
        }
        ClosetSearch(query, onQuery, Modifier.bringIntoViewRequester(itemSearch))
        Spacer(Modifier.height(gap))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // One fitting scene, then small actual garment details. At narrow
            // large type the full scene stays visible while names get the width.
            val showThumbnail = !largeText || maxWidth >= 360.dp
            Column(Modifier.fillMaxWidth().selectableGroup().testTag("closet_compare_list")) {
                shown.forEachIndexed { index, variant ->
                    val isWorn = current == variant && !covered
                    ClosetItemOption(profile, actions, slot, variant, isWorn,
                        secondaryReady, showThumbnail, Modifier.fillMaxWidth().then(
                            if (isWorn) Modifier.bringIntoViewRequester(wornItem) else Modifier))
                    if (index < shown.lastIndex) HorizontalDivider(color = RunmoniColors.Line,
                        modifier = Modifier.padding(start = if (showThumbnail) 104.dp else 28.dp, end = 12.dp))
                }
                if (shown.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag("closet_search_empty"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("일치하는 아이템이 없어요", style = MaterialTheme.typography.titleMedium)
                        Text("다른 이름으로 찾거나 검색어를 지워보세요.", style = MaterialTheme.typography.bodyMedium,
                            color = RunmoniColors.Muted)
                        TextButton(onClick = { keyboard?.hide(); focus.clearFocus(); onQuery("") }, modifier = Modifier.heightIn(min = 48.dp)
                            .testTag("closet_search_reset")) { Text("검색어 지우기") }
                    }
                }
            }
        }
        if (fabricRegion != null) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = RunmoniColors.Line)
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    scope.launch {
                        if (!covered && current in shown) wornItem.bringIntoView() else itemSearch.bringIntoView()
                    }
                },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("closet_back_to_items")) { Text("아이템 목록 보기") }
                RunningKitColorControls(profile.avatar.runningKitColors, fabricRegion, actions::setRunningKitColor,
                    navigationModifier = Modifier.bringIntoViewRequester(colorPanel))
            }
        }
        Spacer(Modifier.height(24.dp).fillMaxWidth().testTag("closet_content_end"))
    }
}

@Composable
private fun ClosetSearch(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    OutlinedTextField(value = query, onValueChange = { onQuery(it.take(40)) },
        modifier = modifier.fillMaxWidth().testTag("closet_search"),
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
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }),
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
        Box(Modifier.width(4.dp).height(32.dp)
            .background(if (selected) RunmoniColors.Primary else Color.Transparent, RunmoniShapes.Status))
        if (showThumbnail) {
            val equipped = profile.avatar.withSelectedItem(slot.key, variant)
            val thumbnail = if (slot.key == "hair") equipped.copy(headwear = 0,
                costume = if ("headwear" in AvatarCatalogue.coveredSlots(equipped.costume)) 0 else equipped.costume) else equipped
            ClosetItemThumbnail(thumbnail, slot.key,
                secondaryReady, "closet_preview_${slot.key}_$variant", Modifier.size(64.dp)
                    .clip(RunmoniShapes.Action)
                    .background(if (selected) RunmoniTrack.Stage else RunmoniColors.Cream)
                    .border(1.dp, if (selected) RunmoniColors.Outline else RunmoniColors.Line, RunmoniShapes.Action))
        }
        Text(avatarItemDisplayName(slot, variant, profile.avatar.gender), style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.Check, null, tint = RunmoniColors.Primary,
            modifier = Modifier.size(20.dp))
        else Spacer(Modifier.size(20.dp))
    }
}

private data class ClosetThumbnail(val image: ImageBitmap? = null, val failed: Boolean = false)
private data class ClosetCrop(val x: Int, val y: Int, val width: Int, val height: Int)

/** Frame zero regions of the real composite, including its actual occlusion.
 * A set uses the complete frame. No standalone product artwork is invented.
 */
private fun closetCrop(slot: String, avatar: AvatarStyle): ClosetCrop {
    val female = avatar.gender == AvatarGender.FEMALE
    // These separately fitted layers occupy a smaller part of their original pose.
    if (StudioCollection.item(slot, avatar.variantFor(slot)) != null) {
        when (slot) {
            "eyewear" -> return ClosetCrop(101, if (female) 99 else 115, 126, 60)
            "hydration" -> return ClosetCrop(86, if (female) 154 else 172, 108, 88)
            "waist" -> return ClosetCrop(95, if (female) 203 else 222, 90, 53)
        }
    }
    return when (slot) {
        "hair" -> ClosetCrop(12, 14, 232, 174)
        "headwear", "face" -> ClosetCrop(0, 0, 256, 208)
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
                val crop = closetCrop(slot, avatar)
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
    modifier: Modifier = Modifier,
) {
    ScrollableTabRow(selectedTabIndex = selected, edgePadding = edgePadding, modifier = modifier,
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

@Composable
private fun ClosetPartBar(selected: Int, onSelect: (Int) -> Unit, openParts: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val showLabel = maxWidth >= 280.dp && LocalDensity.current.fontScale < 1.8f
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ClosetTabs(AvatarCatalogue.tabs, selected, onSelect, "closet_slot", edgePadding = 0.dp,
                modifier = Modifier.weight(1f))
            val target = Modifier.heightIn(min = 48.dp).testTag("closet_open_parts")
            if (showLabel) TextButton(onClick = openParts, modifier = target,
                contentPadding = PaddingValues(horizontal = 8.dp)) { Text("부위 전체", maxLines = 1) }
            else IconButton(onClick = openParts, modifier = target.width(48.dp)) {
                Icon(Icons.Outlined.GridView, "부위 전체", tint = RunmoniColors.Primary)
            }
        }
    }
}

@Composable
private fun ClosetPartsDialog(current: String, accessorySlots: List<AvatarSlot>, onDismiss: () -> Unit,
    onSelect: (String) -> Unit) {
    val primary = AvatarCatalogue.tabs.filterNot { it.first == "accessories" }
    DismissibleAlertDialog(onDismissRequest = onDismiss, title = { Text("부위 전체") }, confirmButton = {
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
    }, text = {
        Column(Modifier.fillMaxWidth().selectableGroup().testTag("closet_all_parts")) {
            primary.forEach { (key, label) -> ClosetPartChoice(key, label, current, onSelect) }
            HorizontalDivider(color = RunmoniColors.Line, modifier = Modifier.padding(vertical = 8.dp))
            Text("액세서리", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            accessorySlots.forEach { ClosetPartChoice(it.key, it.label, current, onSelect) }
        }
    })
}

@Composable
private fun ClosetPartChoice(key: String, label: String, current: String, onSelect: (String) -> Unit) {
    val selected = key == current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RunmoniShapes.Action)
        .background(if (selected) RunmoniColors.PrimarySoft else Color.Transparent)
        .selectable(selected, role = Role.RadioButton, onClick = { onSelect(key) })
        .padding(horizontal = 12.dp, vertical = 6.dp).testTag("closet_part_$key"),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.Check, null, tint = RunmoniColors.Primary, modifier = Modifier.size(20.dp))
    }
}

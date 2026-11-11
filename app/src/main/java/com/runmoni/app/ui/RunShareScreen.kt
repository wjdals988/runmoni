package com.runmoni.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.runmoni.app.domain.*
import com.runmoni.app.sharing.RunShareCardRenderer
import com.runmoni.app.sharing.RunShareImageStore
import com.runmoni.app.sharing.RunSharePhotoLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate

internal val RunShareDraftSaver = listSaver<RunShareDraft, Any>(
    save = { listOf(it.ratio.name, it.theme.name, it.placement.name, it.caption, it.showDate, it.showName,
        it.showAvatar, it.avatarX, it.avatarY, it.avatarScale, it.photoZoom, it.photoX, it.photoY) },
    restore = { values -> runCatching {
        RunShareDraft(RunShareRatio.valueOf(values[0] as String), RunShareTheme.valueOf(values[1] as String),
            RunSharePlacement.valueOf(values[2] as String), values[3] as String, values[4] as Boolean,
            values[5] as Boolean, values[6] as Boolean, values[7] as Float, values[8] as Float,
            values[9] as Float, values[10] as Float, values[11] as Float, values[12] as Float)
    }.getOrNull() },
)

private data class SharePhoto(val uri: String, val bitmap: Bitmap)
private data class ShareRenderInput(val content: RunShareContent, val draft: RunShareDraft,
    val photo: Bitmap?, val avatar: Bitmap?, val name: String)
private data class RenderedShare(val input: ShareRenderInput, val bitmap: Bitmap)

/** An editor owns only its transient photo/draft. It never changes the record, wallet or avatar. */
@Composable
internal fun RunShareScreen(record: RunRecord, avatar: AvatarStyle, runnerName: String,
    recordAvailable: Boolean, authorize: () -> Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val imageStore = remember(context) { RunShareImageStore(context.applicationContext) }
    val loader = remember(context) { RunSharePhotoLoader(context.applicationContext) }
    var draft by rememberSaveable(record.id, stateSaver = RunShareDraftSaver) { mutableStateOf(RunShareDraft()) }
    var tool by rememberSaveable(record.id) { mutableIntStateOf(0) }
    var savedPhotoUri by rememberSaveable(record.id) { mutableStateOf<String?>(null) }
    var requestedPhotoUri by remember(record.id) { mutableStateOf(savedPhotoUri) }
    var photoAttempt by remember { mutableIntStateOf(0) }
    var photo by remember { mutableStateOf<SharePhoto?>(null) }
    var photoLoading by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var avatarImage by remember(avatar) { mutableStateOf<Bitmap?>(null) }
    var avatarError by remember(avatar) { mutableStateOf(false) }
    var rendered by remember { mutableStateOf<RenderedShare?>(null) }
    var renderError by remember { mutableStateOf<String?>(null) }
    var operationError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var editRevision by remember { mutableLongStateOf(0L) }
    val renderedEditRevision = editRevision
    var alive by remember { mutableStateOf(true) }
    var operation by remember { mutableLongStateOf(0L) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    val available by rememberUpdatedState(recordAvailable)
    val content = remember(record) { RunShareContent.from(record) }
    val normalized = draft.normalized()
    LaunchedEffect(recordAvailable, content) {
        if (!recordAvailable || content == null) { operation++; exportJob?.cancel(); exporting = false }
    }
    DisposableEffect(lifecycle) {
        alive = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { operation++; exportJob?.cancel(); exporting = false }
        }
        lifecycle.addObserver(observer)
        onDispose { alive = false; operation++; exportJob?.cancel(); lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(avatar) {
        try {
            // Borrow the shared approved frame. Never recycle or recolor this cache bitmap.
            avatarImage = withContext(Dispatchers.Default) { avatarBitmap(context, avatar, 2, running = true) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { avatarError = true }
    }
    LaunchedEffect(requestedPhotoUri, photoAttempt) {
        val uri = requestedPhotoUri
        if (uri == null) { photo = null; photoLoading = false; return@LaunchedEffect }
        if (photo?.uri == uri) return@LaunchedEffect
        photoLoading = true
        try {
            val bitmap = loader.load(android.net.Uri.parse(uri))
            ensureActive()
            photo = SharePhoto(uri, bitmap); savedPhotoUri = uri; photoError = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            photoError = "사진을 불러오지 못했어요. 다른 사진을 선택하거나 다시 시도해 주세요."
            requestedPhotoUri = savedPhotoUri
        } finally { photoLoading = false }
    }
    val photoReady = !photoLoading && (savedPhotoUri == null || photo?.uri == savedPhotoUri) &&
        (requestedPhotoUri == null || requestedPhotoUri == photo?.uri)
    val input = content?.let { ShareRenderInput(it, normalized, photo?.bitmap,
        if (normalized.showAvatar) avatarImage else null, if (normalized.showName) runnerName else "") }
    val renderInput by rememberUpdatedState(input)
    LaunchedEffect(Unit) {
        // One worker follows the latest draft while a gesture is still moving.
        // Conflation bounds work; a quiet-period debounce would freeze the preview until release.
        snapshotFlow { renderInput }.conflate().collect { candidate ->
            renderError = null
            if (candidate == null || candidate.draft.showAvatar && candidate.avatar == null || rendered?.input == candidate) return@collect
            var produced: Bitmap? = null
            var adopted = false
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    ensureActive()
                    RunShareCardRenderer.render(candidate.content, candidate.draft, candidate.photo, candidate.avatar, candidate.name).also { produced = it }
                }
                ensureActive(); rendered = RenderedShare(candidate, bitmap); adopted = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (candidate == renderInput) renderError = "이미지를 만들지 못했어요. 사진이나 꾸미기 설정을 바꿔 다시 시도해 주세요."
            } finally { if (!adopted) produced?.recycle() }
            delay(32)
        }
    }
    val currentBitmap = rendered?.takeIf { it.input == input }?.bitmap
    val ready = content != null && recordAvailable && photoReady && currentBitmap != null &&
        (!normalized.showAvatar || avatarImage != null)
    val readyNow by rememberUpdatedState(ready)
    val latestInput by rememberUpdatedState(input)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && alive && available && !exporting) {
            editRevision++; requestedPhotoUri = uri.toString(); photoAttempt++; photoError = null; message = null
        }
    }
    fun edit(next: RunShareDraft) {
        if (!exporting && alive && available) { editRevision++; draft = next; operationError = null; message = null }
    }
    fun export(toGallery: Boolean) {
        val snapshot = currentBitmap ?: return
        if (renderedEditRevision != editRevision || !readyNow || latestInput != input || exporting || !alive || !available || !authorize() ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val ticket = ++operation
        fun stillCurrent() = alive && available && ticket == operation && renderedEditRevision == editRevision &&
            authorize() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        exporting = true; operationError = null; message = null
        // These immutable values describe the exact preview, not a later selection.
        val summary = content?.let { "runmoni · ${it.distance} km · ${it.duration} · ${it.pace} /km" }.orEmpty()
        exportJob = scope.launch {
            try {
                if (toGallery) {
                    if (!stillCurrent()) return@launch
                    imageStore.save(snapshot, isCurrent = ::stillCurrent)
                    ensureActive()
                    if (stillCurrent()) message = "사진 앱에 저장했어요."
                } else {
                    val uri = imageStore.cache(snapshot)
                    ensureActive()
                    if (stillCurrent()) {
                        val send = imageStore.shareIntent(uri, summary)
                        val chooser = Intent.createChooser(send, "러닝 기록 공유").apply {
                            clipData = send.clipData; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(chooser)
                        // Opening the system chooser does not establish delivery to another app.
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (alive && ticket == operation) operationError = if (toGallery)
                    "저장하지 못했어요. 저장 공간을 확인한 뒤 다시 시도해 주세요."
                else "공유할 앱을 열지 못했어요. 이미지를 저장하거나 다시 시도해 주세요."
            } finally { if (ticket == operation) exporting = false }
        }
    }
    val problem = when {
        !recordAvailable -> "기록을 확인할 수 없어 공유를 잠시 멈췄어요. 뒤로 돌아가 기록을 확인해 주세요."
        content == null -> "완료된 러닝 기록을 확인할 수 없어요. 뒤로 돌아가 다시 선택해 주세요."
        normalized.showAvatar && avatarError -> "미니미를 불러오지 못했어요. 미니미 표시를 끄거나 편집 화면을 다시 열어 주세요."
        else -> operationError ?: photoError ?: renderError
    }
    RunShareEditorUi(draft, tool, currentBitmap ?: rendered?.bitmap, photo?.bitmap != null,
        ready, exporting, !exporting && recordAvailable, problem, message,
        onDraft = ::edit, onTool = { if (!exporting) tool = it },
        onPickPhoto = { if (!exporting && available) {
            try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            catch (_: Exception) { operationError = "사진 선택 앱을 열지 못했어요. 사진 앱을 확인한 뒤 다시 시도해 주세요." }
        } },
        onRemovePhoto = { if (!exporting) { editRevision++; savedPhotoUri = null; requestedPhotoUri = null; photo = null; photoError = null; message = null } },
        onTransform = { centroid, pan, zoom ->
            if (tool == 0) photo?.bitmap?.let { bitmap ->
                val moved = RunSharePhotoTransform.gesture(draft, bitmap.width, bitmap.height,
                    centroid.x, centroid.y, pan.x, pan.y, zoom)
                edit(draft.copy(photoZoom = moved.photoZoom, photoX = moved.photoX, photoY = moved.photoY))
            } else if (tool == 2 && draft.showAvatar && avatarImage != null && rendered?.input?.avatar === avatarImage && content != null) {
                val placed = RunShareCardRenderer.constrainAvatar(content, draft, avatarImage, runnerName)
                val moved = placed.copy(avatarX = placed.avatarX + pan.x, avatarY = placed.avatarY + pan.y,
                    avatarScale = (placed.avatarScale * zoom).coerceIn(.65f, 1.5f))
                val fitted = RunShareCardRenderer.constrainAvatar(content, moved, avatarImage, runnerName)
                edit(draft.copy(avatarX = fitted.avatarX, avatarY = fitted.avatarY, avatarScale = fitted.avatarScale))
            }
        }, onSave = { export(true) }, onShare = { export(false) }, onBack = onBack,
        previewDescription = content?.let { "공유 이미지 미리보기. 거리 ${it.distance} km, 러닝 시간 ${it.duration}, 평균 페이스 ${it.pace} /km. " +
            (if (draft.showDate) it.date else "") + ". " + normalized.caption }.orEmpty(),
        photoClearable = savedPhotoUri != null || requestedPhotoUri != null || photo != null)
}

/** Controls and preview share one draft; Native layout tests inject only the already-rendered bitmap. */
@Composable
internal fun RunShareEditorUi(draft: RunShareDraft, tool: Int, bitmap: Bitmap?, photoPresent: Boolean,
    ready: Boolean, busy: Boolean, editable: Boolean, error: String?, message: String?,
    onDraft: (RunShareDraft) -> Unit, onTool: (Int) -> Unit, onPickPhoto: () -> Unit, onRemovePhoto: () -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit, onSave: () -> Unit, onShare: () -> Unit, onBack: () -> Unit,
    previewDescription: String = "공유 이미지 미리보기", photoClearable: Boolean = photoPresent) {
    BackHandler(onBack = onBack)
    val scroll = rememberScrollState()
    val latestTransform by rememberUpdatedState(onTransform)
    var photoPositionExpanded by rememberSaveable { mutableStateOf(false) }
    val frameworkFitted = legacyRunShareWindowPolicy()
    // API29 classic fitting owns both bars and IME. Compat may still infer raw IME
    // from root visibleInsets after consumption, so adding it would count it twice.
    val contentInsets = if (frameworkFitted) WindowInsets(0, 0, 0, 0)
        else WindowInsets.safeDrawing.union(WindowInsets.ime)
    Surface(Modifier.fillMaxSize().testTag("run_share_page"), color = RunmoniColors.Cream) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(contentInsets)
            .testTag("run_share_safe_content")) {
          // IME and short windows can leave less room than the two export actions.
          // Keep Back fixed and let those actions join the single body scroll.
          val scrollFooter = maxHeight < 280.dp || LocalDensity.current.fontScale >= 1.3f && maxHeight < 420.dp
          Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("close_run_share")) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "러닝 기록으로 돌아가기")
                }
                Text("기록 공유", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            }
            HorizontalDivider(color = RunmoniColors.Line)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val small = scrollFooter || LocalDensity.current.fontScale >= 1.3f || maxHeight < 500.dp
                val previewHeight = if (small) 220.dp else minOf(maxHeight * .52f, 350.dp)
                val previewWidth = minOf(maxWidth - pageGutter() * 2, previewHeight * draft.ratio.aspectRatio)
                // Keep one scroll owner when IME/viewport changes. The focused field stays
                // inside this same viewport instead of transferring its ScrollState to a parent.
                Column(Modifier.fillMaxSize().verticalScroll(scroll),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(vertical = 16.dp).width(previewWidth).aspectRatio(draft.ratio.aspectRatio)
                        .background(RunmoniColors.Line).border(1.dp, RunmoniColors.Line)
                        .testTag("run_share_preview")
                        .pointerInput(tool, editable, photoPresent, draft.showAvatar) {
                            if (editable && (tool == 0 && photoPresent || tool == 2 && draft.showAvatar)) detectTransformGestures { centroid, pan, zoom, _ ->
                                if (size.width > 0 && size.height > 0) latestTransform(
                                    Offset(centroid.x / size.width, centroid.y / size.height),
                                    Offset(pan.x / size.width, pan.y / size.height), zoom)
                            }
                        }, contentAlignment = Alignment.Center) {
                        if (bitmap != null) Image(bitmap.asImageBitmap(),
                            "$previewDescription ${draft.ratio.label}, ${if (draft.showAvatar) "미니미 포함" else "미니미 숨김"}",
                            Modifier.fillMaxSize())
                        else CircularProgressIndicator(Modifier.size(32.dp))
                        if (!ready && error == null) Surface(color = RunmoniColors.Paper.copy(alpha = .9f),
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                            Text(if (busy) "저장·공유 준비 중…" else "이미지 준비 중…", style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp))
                        }
                    }
                    Column(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
                        .padding(horizontal = pageGutter(), vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (error != null) Text(error, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("run_share_error").semantics { liveRegion = LiveRegionMode.Polite })
                        // Material Tab fixes its text-only height; let large Korean text choose its own height.
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
                            .testTag("run_share_tools"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("배경", "기록", "미니미").forEachIndexed { index, label ->
                                Column(Modifier.widthIn(min = 80.dp).width(IntrinsicSize.Max).heightIn(min = 48.dp)
                                    .selectable(selected = tool == index, enabled = !busy, role = Role.Tab) { onTool(index) }
                                    .testTag("run_share_tool_$index"), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label, Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (tool == index) RunmoniColors.Primary else RunmoniColors.Muted)
                                    Box(Modifier.fillMaxWidth().height(2.dp)
                                        .background(if (tool == index) RunmoniColors.Primary else RunmoniColors.Line))
                                }
                            }
                        }
                        when (tool) {
                            0 -> {
                                OutlinedButton(onClick = onPickPhoto, enabled = editable, shape = RunmoniShapes.Action,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("run_share_photo")) {
                                    Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp))
                                    Text(if (photoPresent) "사진 바꾸기" else "사진 추가")
                                }
                                if (photoPresent || photoClearable) {
                                    if (photoPresent) {
                                        Text("미리보기의 사진을 두 손가락으로 확대하거나 끌어 위치를 맞춰 보세요.", style = MaterialTheme.typography.bodyMedium)
                                        Slider(value = draft.photoZoom.coerceIn(1f, 3f), onValueChange = {
                                            latestTransform(Offset(.5f, .5f), Offset.Zero, it / draft.photoZoom)
                                        },
                                            valueRange = 1f..3f, enabled = editable,
                                            modifier = Modifier.testTag("run_share_photo_zoom").semantics { contentDescription = "사진 확대" })
                                        TextButton(onClick = { photoPositionExpanded = !photoPositionExpanded }, enabled = editable,
                                            modifier = Modifier.heightIn(min = 48.dp).testTag("run_share_photo_position")) {
                                            Text(if (photoPositionExpanded) "위치 버튼 접기" else "버튼으로 위치 맞추기")
                                        }
                                        if (photoPositionExpanded) ShareNudgeControls("사진", "photo", editable && ready) {
                                            latestTransform(Offset(.5f, .5f), it, 1f)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        TextButton(onClick = { onDraft(draft.copy(photoZoom = 1f, photoX = 0f, photoY = 0f)) },
                                            enabled = editable, modifier = Modifier.heightIn(min = 48.dp).testTag("run_share_photo_reset")) { Text("위치 초기화") }
                                        TextButton(onClick = onRemovePhoto, enabled = editable,
                                            modifier = Modifier.heightIn(min = 48.dp).testTag("run_share_photo_remove")) { Text("사진 빼기") }
                                    }
                                }
                                Text("이미지 비율", style = MaterialTheme.typography.titleSmall)
                                ShareChoices(RunShareRatio.entries, draft.ratio, { it.label }, editable, "ratio") { onDraft(draft.copy(ratio = it)) }
                                Text("색상", style = MaterialTheme.typography.titleSmall)
                                ShareChoices(RunShareTheme.entries, draft.theme, { it.label }, editable, "theme") { onDraft(draft.copy(theme = it)) }
                            }
                            1 -> {
                                Text("기록 배치", style = MaterialTheme.typography.titleSmall)
                                ShareChoices(RunSharePlacement.entries, draft.placement, { it.label }, editable, "placement") { onDraft(draft.copy(placement = it)) }
                                OutlinedTextField(draft.caption, { onDraft(draft.copy(caption = RunShareText.takeCodePoints(it, 60))) },
                                    label = { Text("한 줄 기록") }, placeholder = { Text("남기고 싶은 말이 있나요?") },
                                    singleLine = true,
                                    supportingText = { Text("${draft.caption.codePointCount(0, draft.caption.length)} / 60") },
                                    enabled = editable, modifier = Modifier.fillMaxWidth().testTag("run_share_caption"))
                                ToggleRow("날짜", "러닝한 날짜를 표시해요.", draft.showDate,
                                    { onDraft(draft.copy(showDate = it)) }, editable, "run_share_date")
                                ToggleRow("닉네임", "현재 기기 프로필 이름을 표시해요.", draft.showName,
                                    { onDraft(draft.copy(showName = it)) }, editable, "run_share_name")
                                Text("거리·시간·페이스는 저장한 기록을 사용해요. GPS 경로는 공유 이미지에 넣지 않아요.",
                                    style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                            }
                            else -> {
                                ToggleRow("내 미니미", "현재 착용한 옷차림을 넣어요.", draft.showAvatar,
                                    { onDraft(draft.copy(showAvatar = it)) }, editable, "run_share_avatar")
                                if (draft.showAvatar) {
                                    Text("미리보기에서 미니미를 끌어 옮기거나 두 손가락으로 크기를 바꿔 보세요.", style = MaterialTheme.typography.bodyMedium)
                                    Slider(value = draft.avatarScale.coerceIn(.65f, 1.5f), onValueChange = { onDraft(draft.copy(avatarScale = it)) },
                                        valueRange = .65f..1.5f, enabled = editable,
                                        modifier = Modifier.testTag("run_share_avatar_scale").semantics { contentDescription = "미니미 크기" })
                                    ShareChoices(listOf("왼쪽", "오른쪽"), if (draft.avatarX < .5f) "왼쪽" else "오른쪽", { it }, editable, "avatar_side") {
                                        onDraft(draft.copy(avatarX = if (it == "왼쪽") .25f else .76f, avatarY = .5f))
                                    }
                                    ShareNudgeControls("미니미", "avatar", editable && ready) {
                                        latestTransform(Offset(.5f, .5f), it, 1f)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (scrollFooter) RunShareExportActions(ready, busy, message, onSave, onShare)
                }
            }
            if (!scrollFooter) RunShareExportActions(ready, busy, message, onSave, onShare)
          }
        }
    }
}

@Composable
private fun ShareNudgeControls(label: String, tag: String, enabled: Boolean, onMove: (Offset) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val moves = listOf(
            Triple("왼쪽", Icons.Outlined.KeyboardArrowLeft, Offset(-.04f, 0f)),
            Triple("위로", Icons.Outlined.KeyboardArrowUp, Offset(0f, -.04f)),
            Triple("아래로", Icons.Outlined.KeyboardArrowDown, Offset(0f, .04f)),
            Triple("오른쪽", Icons.Outlined.KeyboardArrowRight, Offset(.04f, 0f)),
        )
        moves.forEachIndexed { index, (direction, icon, offset) ->
            IconButton(onClick = { onMove(offset) }, enabled = enabled,
                modifier = Modifier.size(48.dp).testTag("run_share_nudge_${tag}_$index")) {
                Icon(icon, "$label $direction 이동")
            }
        }
    }
}

@Composable
private fun RunShareExportActions(ready: Boolean, busy: Boolean, message: String?,
    onSave: () -> Unit, onShare: () -> Unit) {
    HorizontalDivider(color = RunmoniColors.Line)
    Box(Modifier.fillMaxWidth().padding(horizontal = pageGutter(), vertical = 12.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (message != null) Text(message, color = RunmoniColors.Primary,
                modifier = Modifier.testTag("run_share_message").semantics { liveRegion = LiveRegionMode.Polite })
            AdaptiveActions(first = { m ->
                OutlinedButton(onClick = onSave, enabled = ready && !busy, shape = RunmoniShapes.Action,
                    modifier = m.heightIn(min = 48.dp).testTag("save_run_share").recordActionSnapshot(onSave)) {
                    Text(if (busy) "준비 중…" else "이미지 저장")
                }
            }, second = { m ->
                Button(onClick = onShare, enabled = ready && !busy, shape = RunmoniShapes.Action,
                    modifier = m.heightIn(min = 48.dp).testTag("send_run_share").recordActionSnapshot(onShare)) { Text("공유하기") }
            })
        }
    }
}

@Composable
private fun <T> ShareChoices(options: List<T>, selected: T, label: (T) -> String,
    enabled: Boolean, tag: String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()
        .testTag("run_share_${tag}_choices"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            Row(Modifier.heightIn(min = 48.dp).background(if (selected == option) RunmoniColors.PrimarySoft else RunmoniColors.Paper, RunmoniShapes.Action)
                .selectable(selected == option, enabled, Role.RadioButton) { onSelect(option) }
                .testTag("run_share_${tag}_$index").padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (selected == option) Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                Text(label(option), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** API29 lets the framework fit this editor while the IME is visible instead of panning the
 * complete edge-to-edge root. Other pages and API30+ keep their existing window policy.
 * The framework consumes its fitted insets; do not add raw root IME pixels a second time.
 */
@Composable
private fun legacyRunShareWindowPolicy(): Boolean {
    val view = LocalView.current
    val window = remember(view) {
        if (Build.VERSION.SDK_INT == 29) view.context.runShareHostActivity()?.window else null
    }
    DisposableEffect(window) {
        if (window == null) {
            onDispose { }
        } else {
            val decor = window.decorView
            // WindowCompat API16 implementation owns only these three layout flags on API29.
            val layoutMask = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            val originalLayoutFlags = decor.systemUiVisibility and layoutMask
            val originalSoftInputMode = window.attributes.softInputMode
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.setSoftInputMode((originalSoftInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            ViewCompat.requestApplyInsets(decor)
            decor.requestLayout()
            onDispose {
                // Preserve any independent status/navigation appearance change during this page.
                decor.systemUiVisibility = (decor.systemUiVisibility and layoutMask.inv()) or originalLayoutFlags
                window.setSoftInputMode(originalSoftInputMode)
                ViewCompat.requestApplyInsets(decor)
                decor.requestLayout()
            }
        }
    }
    return window != null
}

private fun Context.runShareHostActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return current as? Activity
}

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.runmoni.app.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.runmoni.app.avatar.AvatarReferenceReview
import com.runmoni.app.avatar.GarmentReviewColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** A review surface, deliberately separate from ownership, profile and remote state. */
@Composable
internal fun AvatarReferenceDialog(onDismiss: () -> Unit, onMotionFrameRendered: ((Int) -> Unit)? = null) {
    val context = LocalContext.current.applicationContext
    val review = remember(context) { AvatarReferenceReview(context) }
    var colors by remember { mutableStateOf(review.load()) }
    var saved by remember { mutableStateOf<GarmentReviewColors?>(if (review.hasSavedColors()) colors else null) }
    var saveFailed by remember { mutableStateOf(false) }
    var animate by remember { mutableStateOf(true) }
    var frame by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val frames by produceState<List<androidx.compose.ui.graphics.ImageBitmap>?>(null, review) {
        value = withContext(Dispatchers.Default) { review.referenceFrames().map { it.asImageBitmap() } }
    }
    val swimsuit by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, review, colors) {
        value = withContext(Dispatchers.Default) { review.swimwear(colors).asImageBitmap() }
    }
    val motion by produceState<List<androidx.compose.ui.graphics.ImageBitmap>?>(null, review, colors) {
        value = withContext(Dispatchers.Default) { review.motionFrames(colors).map { it.asImageBitmap() } }
    }
    LaunchedEffect(animate, lifecycle) {
        frame = 0
        if (animate) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val start = android.os.SystemClock.uptimeMillis()
            while (isActive) {
                frame = if (ValueAnimator.areAnimatorsEnabled())
                    (((android.os.SystemClock.uptimeMillis() - start) / 90) % 8).toInt() else 0
                delay(90)
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding(), color = RunmoniColors.Cream) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("원화 · 수영복 검토", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
                }
                // The two previews scroll with the controls on small screens / large text.
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("승인 기준 원화 · 8프레임", style = MaterialTheme.typography.titleMedium)
                    frames?.let { images ->
                        Image(images[frame], "승인 기준 남녀 원화, 원본 프레임 그대로 재생",
                            Modifier.fillMaxWidth().height(208.dp).background(RunmoniColors.Mint).testTag("reference_running"),
                            filterQuality = FilterQuality.None)
                    } ?: Text("원화를 불러오는 중…")
                    TextButton(onClick = { animate = !animate }, modifier = Modifier.heightIn(min = 48.dp).testTag("reference_playback")) {
                        Text(if (animate) "원화 재생 멈춤" else "원화 재생")
                    }
                    Text("수영복 달리기 검토 · 미승인", style = MaterialTheme.typography.titleMedium)
                    motion?.let { images ->
                        val renderedFrame = frame
                        Image(images[renderedFrame], "원본 얼굴 보호 마스크를 적용한 남녀 수영복 달리기 검토",
                            Modifier.fillMaxWidth().height(208.dp).background(RunmoniColors.Mint).testTag("swimwear_motion")
                                .drawWithContent { drawContent(); onMotionFrameRendered?.invoke(renderedFrame) },
                            filterQuality = FilterQuality.None)
                    } ?: Text("달리기 시안을 불러오는 중…")
                    SmallNote("새 달리기 시안이에요. 얼굴 보호와 색상 검사를 했지만 동작·체형은 검토 중이에요.")
                    Text("이전 수영복 시안 · 정지 그림", style = MaterialTheme.typography.titleMedium)
                    swimsuit?.let { image ->
                        Image(image, "남자 보드숏과 여자 비키니의 미승인 색상 시안",
                            Modifier.fillMaxWidth().height(280.dp).background(RunmoniColors.Mint).testTag("swimwear_proposal"),
                            filterQuality = FilterQuality.None)
                    } ?: Text("시안을 불러오는 중…")
                    SmallNote("이 화면의 선택은 실제 착용·구매에 반영되지 않아요.")
                    ReviewColorPicker("남자 보드숏", "shorts", colors.shorts) { colors = colors.copy(shorts = it); saveFailed = false }
                    ReviewColorPicker("비키니 상의", "top", colors.top) { colors = colors.copy(top = it); saveFailed = false }
                    ReviewColorPicker("비키니 하의", "bottom", colors.bottom) { colors = colors.copy(bottom = it); saveFailed = false }
                    TextButton(onClick = { colors = GarmentReviewColors(); saveFailed = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("시안 원래 색상") }
                    SmallNote("색상은 이 기기에만 저장해요. 얼굴·피부·머리·신발은 색상 선택으로 바뀌지 않아요.")
                    if (saveFailed) Text("저장하지 못했어요. 다시 시도해 주세요.", color = MaterialTheme.colorScheme.error)
                    else Text(if (colors == saved) "시안 색상 저장됨" else "저장하지 않은 시안 색상", style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { if (review.save(colors)) { saved = colors; saveFailed = false } else saveFailed = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_review_colors")) { Text("시안 색상 저장") }
            }
        }
    }
}

@Composable
private fun ReviewColorPicker(label: String, key: String, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("원본", "민트", "코랄", "블루", "퍼플", "골드").forEachIndexed { index, name ->
                FilterChip(selected = selected == index, onClick = { onSelect(index) }, label = { Text(name) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("review_${key}_$index"))
            }
        }
    }
}

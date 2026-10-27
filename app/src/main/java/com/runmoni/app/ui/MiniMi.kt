package com.runmoni.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.runmoni.app.avatar.ApprovedAvatarRenderer
import com.runmoni.app.domain.AvatarStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** Approved whole-frame sprites shared by the app; no reconstructed body parts. */
@Composable
internal fun MiniMi(
    avatar: AvatarStyle,
    modifier: Modifier = Modifier,
    running: Boolean = false,
    description: String? = "러닝 미니미",
    frameOverride: Int? = null,
    // Internal verification observer. Production callers leave this null.
    onFrameRendered: ((Int) -> Unit)? = null,
    // Controlled I/O for loading/retry verification; production uses the approved asset.
    frameLoader: (suspend (Int, Boolean) -> Bitmap)? = null,
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var frame by remember(running) { mutableIntStateOf(0) }
    var attempt by remember(context, avatar, running) { mutableIntStateOf(0) }
    // Asset decoding/cropping must not block the first home screen on the UI thread.
    // Publish the genuine first frame, then the complete clip. A changed selection
    // gets its own state, so an old outfit is never presented as a new one.
    val prepared by key(context, avatar, running, attempt, frameLoader) {
        produceState(initialValue = MiniMiFrames()) {
            try {
                suspend fun load(index: Int) = withContext(Dispatchers.Default) {
                    ensureActive()
                    (frameLoader?.invoke(index, running) ?: avatarBitmap(context, avatar, index, running)).asImageBitmap()
                }
                val first = load(0)
                value = MiniMiFrames(listOf(first))
                if (running) {
                    val rest = (1 until ApprovedAvatarRenderer.FRAMES).map { load(it) }
                    value = MiniMiFrames(listOf(first) + rest)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // An avatar read failure must not take down recording or the rest of the UI.
                value = MiniMiFrames(failed = true)
            }
        }
    }
    val frames = prepared.frames
    val playing = running && frames.size == ApprovedAvatarRenderer.FRAMES
    LaunchedEffect(playing, lifecycle, frameOverride) {
        frame = 0
        if (playing && frameOverride == null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val started = android.os.SystemClock.uptimeMillis()
            while (isActive) {
                frame = if (ValueAnimator.areAnimatorsEnabled()) ApprovedAvatarRenderer.frameAt(android.os.SystemClock.uptimeMillis() - started) else 0
                delay(ApprovedAvatarRenderer.MILLIS.toLong())
            }
        }
    }
    val renderedFrame = if (frames.isEmpty()) 0 else Math.floorMod(frameOverride ?: frame, frames.size)
    val image = frames.getOrNull(renderedFrame)
    val accessible = if (description == null) Modifier.clearAndSetSemantics { } else Modifier.semantics { contentDescription = description }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val showFailureLabel = maxWidth >= 100.dp && maxHeight >= 112.dp
        if (image != null) {
            Canvas(Modifier.fillMaxSize().then(accessible)) {
                val scale = minOf(size.width / ApprovedAvatarRenderer.WIDTH, size.height / ApprovedAvatarRenderer.HEIGHT)
                val width = (ApprovedAvatarRenderer.WIDTH * scale).roundToInt().coerceAtLeast(1)
                val height = (ApprovedAvatarRenderer.HEIGHT * scale).roundToInt().coerceAtLeast(1)
                drawImage(image, dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
                    dstSize = IntSize(width, height), filterQuality = FilterQuality.None)
                onFrameRendered?.invoke(renderedFrame)
            }
        } else if (prepared.failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (showFailureLabel) {
                    Text("불러오기 실패", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { attempt++ }, modifier = Modifier.size(48.dp).testTag("minimi_retry")) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "미니미 다시 불러오기")
                }
            }
        } else {
            Text("준비 중", style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("minimi_loading"))
        }
    }
}

private data class MiniMiFrames(
    val frames: List<androidx.compose.ui.graphics.ImageBitmap> = emptyList(),
    val failed: Boolean = false,
)

/** Same approved base in Compose and map markers. Saved equipment is never rewritten. */
internal fun avatarBitmap(context: Context, @Suppress("UNUSED_PARAMETER") avatar: AvatarStyle, frame: Int = 0, running: Boolean = false): Bitmap =
    AvatarArtRuntime.renderer(context).render(frame, running)

/** One application asset repository/cache shared by Compose and map markers. */
private object AvatarArtRuntime {
    @Volatile private var shared: ApprovedAvatarRenderer? = null
    fun renderer(context: Context): ApprovedAvatarRenderer = shared ?: synchronized(this) {
        shared ?: ApprovedAvatarRenderer(context.applicationContext).also { shared = it }
    }
}

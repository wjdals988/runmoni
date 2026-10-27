package com.runmoni.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var frame by remember(running) { mutableIntStateOf(0) }
    LaunchedEffect(running, lifecycle, frameOverride) {
        frame = 0
        if (running && frameOverride == null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val started = android.os.SystemClock.uptimeMillis()
            while (isActive) {
                frame = if (ValueAnimator.areAnimatorsEnabled()) ApprovedAvatarRenderer.frameAt(android.os.SystemClock.uptimeMillis() - started) else 0
                delay(ApprovedAvatarRenderer.MILLIS.toLong())
            }
        }
    }
    // Asset decoding/cropping must not block the first home screen on the UI thread.
    // Publish the genuine first frame, then the complete clip. A changed selection
    // gets its own state, so an old outfit is never presented as a new one.
    val frames by key(context, avatar, running) {
        produceState(initialValue = emptyList<androidx.compose.ui.graphics.ImageBitmap>()) {
            val first = withContext(Dispatchers.Default) { avatarBitmap(context, avatar, 0, running).asImageBitmap() }
            value = listOf(first)
            if (running) {
                val rest = withContext(Dispatchers.Default) {
                    (1 until ApprovedAvatarRenderer.FRAMES).map {
                        ensureActive()
                        avatarBitmap(context, avatar, it, true).asImageBitmap()
                    }
                }
                value = listOf(first) + rest
            }
        }
    }
    val renderedFrame = if (frames.isEmpty()) 0 else Math.floorMod(frameOverride ?: frame, frames.size)
    val image = frames.getOrNull(renderedFrame)
    val accessible = if (description == null) Modifier.clearAndSetSemantics { } else Modifier.semantics { contentDescription = description }
    Canvas(modifier.then(accessible)) {
        if (image == null) return@Canvas
        val scale = minOf(size.width / ApprovedAvatarRenderer.WIDTH, size.height / ApprovedAvatarRenderer.HEIGHT)
        val width = (ApprovedAvatarRenderer.WIDTH * scale).roundToInt().coerceAtLeast(1)
        val height = (ApprovedAvatarRenderer.HEIGHT * scale).roundToInt().coerceAtLeast(1)
        drawImage(image, dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
            dstSize = IntSize(width, height), filterQuality = FilterQuality.None)
        onFrameRendered?.invoke(renderedFrame)
    }
}

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

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
import com.runmoni.app.avatar.AvatarArtRepository
import com.runmoni.app.avatar.AvatarSpriteRenderer
import com.runmoni.app.domain.AvatarStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** Original PNG parts with a shared face/neck/body rig for independent equipment. */
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
                frame = if (ValueAnimator.areAnimatorsEnabled()) AvatarSpriteRenderer.frameAt(android.os.SystemClock.uptimeMillis() - started) else 0
                delay(AvatarSpriteRenderer.MILLIS.toLong())
            }
        }
    }
    // Eight 256x384 frames per visible animated avatar; no work during each draw.
    val frames = remember(context, avatar, running) {
        List(if (running) AvatarSpriteRenderer.FRAMES else 1) { avatarBitmap(context, avatar, it, running).asImageBitmap() }
    }
    val renderedFrame = Math.floorMod(frameOverride ?: frame, frames.size)
    val image = frames[renderedFrame]
    val accessible = if (description == null) Modifier.clearAndSetSemantics { } else Modifier.semantics { contentDescription = description }
    Canvas(modifier.then(accessible)) {
        val scale = minOf(size.width / AvatarSpriteRenderer.WIDTH, size.height / AvatarSpriteRenderer.HEIGHT)
        val width = (AvatarSpriteRenderer.WIDTH * scale).roundToInt().coerceAtLeast(1)
        val height = (AvatarSpriteRenderer.HEIGHT * scale).roundToInt().coerceAtLeast(1)
        drawImage(image, dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
            dstSize = IntSize(width, height), filterQuality = FilterQuality.None)
        onFrameRendered?.invoke(renderedFrame)
    }
}

/** Shared by Compose and the map marker; equipment remains visible in both. */
internal fun avatarBitmap(context: Context, avatar: AvatarStyle, frame: Int = 0, running: Boolean = false): Bitmap =
    AvatarArtRuntime.renderer(context).render(avatar, frame, running)

/** One application asset repository/cache shared by Compose and map markers. */
private object AvatarArtRuntime {
    @Volatile private var shared: AvatarSpriteRenderer? = null
    fun renderer(context: Context): AvatarSpriteRenderer = shared ?: synchronized(this) {
        shared ?: AvatarSpriteRenderer(AvatarArtRepository(context.applicationContext)).also { shared = it }
    }
}

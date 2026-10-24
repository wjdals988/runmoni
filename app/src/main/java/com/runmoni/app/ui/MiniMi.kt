package com.runmoni.app.ui

import android.animation.ValueAnimator
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.runmoni.app.avatar.AvatarRasterizer
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.variantFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** Native pixel art. The face/neck/body rig is shared by all independent slots. */
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var frame by remember(running) { mutableIntStateOf(0) }
    LaunchedEffect(running, lifecycle, frameOverride) {
        frame = 0
        if (running && frameOverride == null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val started = android.os.SystemClock.uptimeMillis()
            while (isActive) {
                frame = if (ValueAnimator.areAnimatorsEnabled()) AvatarRasterizer.frameAt(android.os.SystemClock.uptimeMillis() - started) else 0
                delay(AvatarRasterizer.FRAME_MILLIS.toLong())
            }
        }
    }
    // Bounded to 8 tiny bitmaps per visible animated avatar; no per-frame rasterization.
    val frames = remember(avatar, running) {
        List(if (running) AvatarRasterizer.FRAME_COUNT else 1) { avatarBitmap(avatar, it, running).asImageBitmap() }
    }
    val renderedFrame = Math.floorMod(frameOverride ?: frame, frames.size)
    val image = frames[renderedFrame]
    val accessible = if (description == null) Modifier.clearAndSetSemantics { } else Modifier.semantics { contentDescription = description }
    Canvas(modifier.then(accessible)) {
        val scale = minOf(size.width / AvatarRasterizer.WIDTH, size.height / AvatarRasterizer.HEIGHT)
        val width = (AvatarRasterizer.WIDTH * scale).roundToInt().coerceAtLeast(1)
        val height = (AvatarRasterizer.HEIGHT * scale).roundToInt().coerceAtLeast(1)
        drawImage(image, dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
            dstSize = IntSize(width, height), filterQuality = FilterQuality.None)
        onFrameRendered?.invoke(renderedFrame)
    }
}

/** Shared by Compose and the map marker; equipment remains visible in both. */
internal fun avatarBitmap(avatar: AvatarStyle, frame: Int = 0, running: Boolean = false): Bitmap = with(avatar) {
    Bitmap.createBitmap(AvatarRasterizer.render(hair, face, top, bottom, socks, shoes, variantFor("headwear"), variantFor("ear"), eyewear, wrist, waist, hydration, costume, frame, running),
        AvatarRasterizer.WIDTH, AvatarRasterizer.HEIGHT, Bitmap.Config.ARGB_8888)
}

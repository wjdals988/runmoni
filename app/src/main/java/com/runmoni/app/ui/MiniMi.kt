package com.runmoni.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
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
    // Optional draw observer: fixed previews can prioritize their first real frame.
    onFrameRendered: ((Int) -> Unit)? = null,
    // Controlled I/O for loading/retry verification; production uses the approved asset.
    frameLoader: (suspend (Int, Boolean) -> Bitmap)? = null,
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val motionAllowed = rememberPageAnimationsEnabled()
    val loopEnabled = running && motionAllowed && frameOverride == null
    val fixedFrame = if (running && frameOverride != null) Math.floorMod(frameOverride, ApprovedAvatarRenderer.FRAMES) else 0
    var attempt by remember(context, avatar, running) { mutableIntStateOf(0) }
    // The first pose has its own request lifetime. A change in motion settings
    // can cancel the additional frames without removing the visible still.
    key(context, avatar, running, attempt, frameLoader) {
        suspend fun loadFrames(indices: List<Int>): MiniMiFrames = try {
            withContext(Dispatchers.Default) {
                MiniMiFrames(indices.associateWith { index ->
                    ensureActive()
                    (frameLoader?.invoke(index, running) ?: avatarBitmap(context, avatar, index, running)).asImageBitmap()
                })
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MiniMiFrames(failed = true)
        }

        val still by produceState(MiniMiFrames()) { value = loadFrames(listOf(0)) }
        val additionalIndices = remember(loopEnabled, fixedFrame) {
            when {
                loopEnabled -> (1 until ApprovedAvatarRenderer.FRAMES).toList()
                fixedFrame != 0 -> listOf(fixedFrame)
                else -> emptyList()
            }
        }
        val additional = if (still.frames.isNotEmpty() && additionalIndices.isNotEmpty()) key(additionalIndices) {
            val prepared by produceState(MiniMiFrames()) { value = loadFrames(additionalIndices) }
            prepared
        } else MiniMiFrames()
        val failed = still.failed || additional.failed
        var frame by remember { mutableIntStateOf(0) }
        val playing = loopEnabled && !failed && additional.frames.size == ApprovedAvatarRenderer.FRAMES - 1
        LaunchedEffect(playing, lifecycle) {
            frame = 0
            if (playing) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val started = android.os.SystemClock.uptimeMillis()
                while (isActive) {
                    frame = ApprovedAvatarRenderer.frameAt(android.os.SystemClock.uptimeMillis() - started)
                    delay(ApprovedAvatarRenderer.MILLIS.toLong())
                }
            }
        }
        val requestedFrame = if (playing) frame else fixedFrame
        val renderedFrame = requestedFrame.takeIf(additional.frames::containsKey) ?: 0
        val image = if (failed) null else additional.frames[renderedFrame] ?: still.frames[0]
        val accessible = if (description == null) Modifier.clearAndSetSemantics { } else Modifier.semantics { contentDescription = description }
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val showStatusText = maxWidth >= 100.dp && maxHeight >= 112.dp
            if (image != null) {
                Canvas(Modifier.fillMaxSize().then(accessible)) {
                    val scale = minOf(size.width / ApprovedAvatarRenderer.WIDTH, size.height / ApprovedAvatarRenderer.HEIGHT)
                    val width = (ApprovedAvatarRenderer.WIDTH * scale).roundToInt().coerceAtLeast(1)
                    val height = (ApprovedAvatarRenderer.HEIGHT * scale).roundToInt().coerceAtLeast(1)
                    drawImage(image, dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
                        dstSize = IntSize(width, height), filterQuality = FilterQuality.None)
                    onFrameRendered?.invoke(renderedFrame)
                }
            } else if (failed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showStatusText) {
                        Text("불러오기 실패", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { attempt++ }, modifier = Modifier.size(48.dp).testTag("minimi_retry")) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "미니미 다시 불러오기")
                    }
                }
            } else {
                if (showStatusText) {
                    Text("준비 중", style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("minimi_loading"))
                } else {
                    Icon(Icons.Outlined.HourglassEmpty, contentDescription = "미니미 준비 중",
                        modifier = Modifier.size(24.dp).testTag("minimi_loading"))
                }
            }
        }
    }
}

private data class MiniMiFrames(
    val frames: Map<Int, ImageBitmap> = emptyMap(),
    val failed: Boolean = false,
)

/** Same approved base in Compose and map markers. Saved equipment is never rewritten. */
internal fun avatarBitmap(context: Context, avatar: AvatarStyle, frame: Int = 0, running: Boolean = false): Bitmap =
    AvatarArtRuntime.renderer(context).renderForDisplay(frame, running, avatar)

/** One application asset repository/cache shared by Compose and map markers. */
private object AvatarArtRuntime {
    @Volatile private var shared: ApprovedAvatarRenderer? = null
    fun renderer(context: Context): ApprovedAvatarRenderer = shared ?: synchronized(this) {
        shared ?: ApprovedAvatarRenderer(context.applicationContext).also { shared = it }
    }
}

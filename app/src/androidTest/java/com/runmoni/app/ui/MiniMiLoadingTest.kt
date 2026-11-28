package com.runmoni.app.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.domain.AvatarStyle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Controlled asset I/O, real Compose drawing; never a new artwork approval. */
class MiniMiLoadingTest : RunmoniDesignTestBase() {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(loader: suspend (Int, Boolean) -> Bitmap, drawn: AtomicInteger,
                        running: Boolean = false, frame: Int? = null,
                        style: MutableState<AvatarStyle> = mutableStateOf(AvatarStyle())) {
        compose.setContent {
            RunmoniTheme {
                Column(Modifier.fillMaxSize().background(RunmoniColors.Cream),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("미니미 로딩 검토")
                    Spacer(Modifier.height(24.dp))
                    val compact = LocalDensity.current.fontScale >= 1.3f
                    MiniMi(style.value, Modifier.size(if (compact) 54.dp else 136.dp, if (compact) 64.dp else 164.dp),
                        running = running, description = null, frameOverride = frame,
                        onFrameRendered = { drawn.set(it) }, frameLoader = loader)
                }
            }
        }
    }

    @Test fun delayedReadShowsStatusUntilAnApprovedFrameIsActuallyDrawn() {
        val gate = CompletableDeferred<Unit>(); val drawn = AtomicInteger(-1)
        fixture({ index, running -> gate.await(); avatarBitmap(context, AvatarStyle(), index, running) }, drawn)
        compose.onNodeWithTag("minimi_loading").assertIsDisplayed()
        if (context.resources.configuration.fontScale >= 1.3f) {
            compose.onNodeWithContentDescription("미니미 준비 중").assertIsDisplayed()
        }
        assertEquals(-1, drawn.get())
        // This fixture intentionally keeps I/O gated while auditing the loading
        // label. Waiting for readiness here would deadlock before gate.complete.
        noTextOverflow("avatar loading", awaitAvatarReady = false)
        capture("avatar-loading-fixture")
        gate.complete(Unit)
        compose.waitUntil(30_000) { drawn.get() == 0 }
        compose.onNodeWithTag("minimi_loading").assertDoesNotExist()
        compose.onNodeWithTag("minimi_retry").assertDoesNotExist()
        capture("avatar-ready-fixture")
    }

    @Test fun failedReadCanRetryWithoutCrashingOrShowingOldArtwork() {
        val gate = CompletableDeferred<Unit>(); val calls = AtomicInteger(); val drawn = AtomicInteger(-1)
        fixture({ index, running ->
            if (calls.incrementAndGet() == 1) throw IOException("controlled asset failure")
            gate.await(); avatarBitmap(context, AvatarStyle(), index, running)
        }, drawn)
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("minimi_retry").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("minimi_retry").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        assertEquals(-1, drawn.get())
        noTextOverflow("avatar retry")
        capture("avatar-retry-fixture")
        compose.onNodeWithTag("minimi_retry").performClick()
        // The new subcomposition can exist before its placement is committed.
        // Keep the I/O gate closed until the loading state is visibly placed.
        compose.waitUntil(30_000) {
            runCatching { compose.onNodeWithTag("minimi_loading").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("minimi_loading").assertIsDisplayed()
        gate.complete(Unit)
        compose.waitUntil(30_000) { drawn.get() == 0 }
        compose.onNodeWithTag("minimi_retry").assertDoesNotExist()
        assertEquals(2, calls.get())
    }

    @Test fun firstFrameRemainsVisibleWhileTheRestOfTheClipLoads() {
        val rest = CompletableDeferred<Unit>(); val drawn = AtomicInteger(-1)
        fixture({ index, running ->
            if (index > 0) rest.await()
            avatarBitmap(context, AvatarStyle(), index, running)
        }, drawn, running = true, frame = 7)
        compose.waitUntil(30_000) { drawn.get() == 0 }
        compose.onNodeWithTag("minimi_loading").assertDoesNotExist()
        rest.complete(Unit)
        compose.waitUntil(30_000) { drawn.get() == 7 }
    }

    @Test fun reducedMotionKeepsTheStillAndCancelsAdditionalFrameRequests() {
        val visible = mutableStateOf(true)
        val animate = mutableStateOf(false)
        val still = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF356F78.toInt()) }
        val firstReads = AtomicInteger()
        val extraReads = AtomicInteger()
        val drawn = AtomicInteger(-1)
        val cancelCount = AtomicInteger()
        val preparedAt = SystemClock.uptimeMillis()
        var readAt: Long? = null
        var drawnAt: Long? = null
        fun readinessRemaining(): Long = (30_000 - (SystemClock.uptimeMillis() - preparedAt)).also {
            assertTrue("Avatar readiness exceeded the separate30s budget", it > 0)
        }
        val loader: suspend (Int, Boolean) -> Bitmap = { index, _ ->
            if (index == 0) {
                firstReads.incrementAndGet()
                still
            } else {
                extraReads.incrementAndGet()
                try { awaitCancellation() }
                finally { cancelCount.incrementAndGet() }
            }
        }
        try {
            compose.setContent {
                if (visible.value) CompositionLocalProvider(LocalRunmoniMotionEnabled provides animate.value) {
                    RunmoniTheme {
                        MiniMi(AvatarStyle(), Modifier.size(96.dp, 144.dp), running = true,
                            onFrameRendered = { drawn.set(it) }, frameLoader = loader)
                    }
                }
            }
            compose.waitUntil(readinessRemaining()) { firstReads.get() == 1 }
            readAt = SystemClock.uptimeMillis() - preparedAt
            compose.waitUntil(readinessRemaining()) { drawn.get() == 0 }
            drawnAt = SystemClock.uptimeMillis() - preparedAt
            assertEquals(0, extraReads.get())
            compose.runOnIdle { animate.value = true }
            compose.waitUntil(5_000) { extraReads.get() == 1 }
            compose.runOnIdle { animate.value = false }
            compose.waitUntil(5_000) { cancelCount.get() == 1 }
            assertEquals(1, firstReads.get())
            assertEquals(0, drawn.get())
        } finally {
            android.util.Log.i("RunmoniAvatarClock", "reduced-motion firstReadMs=$readAt firstDrawMs=$drawnAt " +
                "reads=${firstReads.get()} drawn=${drawn.get()} extraReads=${extraReads.get()} stopped=${cancelCount.get()}")
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            still.recycle()
        }
    }

    @Test fun cancelledSelectionCannotReplaceTheNewSelectionWithAnError() {
        val stale = CompletableDeferred<Unit>(); val calls = AtomicInteger(); val drawn = AtomicInteger(-1)
        val style = mutableStateOf(AvatarStyle())
        fixture({ index, running ->
            if (calls.incrementAndGet() == 1) { stale.await(); throw IOException("stale read") }
            avatarBitmap(context, AvatarStyle(), index, running)
        }, drawn, style = style)
        compose.waitUntil(30_000) { calls.get() == 1 }
        compose.runOnIdle { style.value = style.value.copy(top = 1) }
        compose.waitUntil(30_000) { drawn.get() == 0 }
        stale.complete(Unit)
        compose.onNodeWithTag("minimi_retry").assertDoesNotExist()
        compose.onNodeWithTag("minimi_loading").assertDoesNotExist()
    }
}

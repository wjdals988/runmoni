package com.runmoni.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
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
        noTextOverflow("avatar loading")
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

package com.runmoni.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.avatar.ApprovedAvatarRenderer
import com.runmoni.app.domain.AppState
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.Profile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import kotlin.math.abs

/** Tests the SAME bitmap entry point used by home, profile and map, not the review screen. */
class ApprovedAvatarRenderingTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    @Test fun sharedAppAndMapBitmapMatchesAllEightApprovedSourceCellsExactly() {
        val source = context.assets.open(ApprovedAvatarRenderer.ASSET).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })!!
        }
        val hashes = mutableSetOf<Int>()
        for (frame in 0..7) {
            val expected = IntArray(256 * 384)
            source.getPixels(expected, 0, 256, frame * 256, 0, 256, 384)
            val actual = pixels(avatarBitmap(context, AvatarStyle(), frame, true))
            assertArrayEquals("App still uses a reconstructed body at frame $frame", expected, actual)
            hashes += actual.contentHashCode()
        }
        assertEquals(8, hashes.size)
        assertArrayEquals(pixels(avatarBitmap(context, AvatarStyle(), 0, false)), pixels(avatarBitmap(context, AvatarStyle(), 7, false)))
        source.recycle()
    }

    @Test fun actualMiniMiComposableDrawsTheApprovedWholeBody() {
        val drawn = AtomicBoolean(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                // Compare at source resolution: CPU and GPU downsampling need
                // not choose identical edge samples at a half-pixel boundary.
                MiniMi(AvatarStyle(), Modifier.size(256.dp, 384.dp).background(Color(0xFFF8FAF8)).testTag("approved_main_avatar"),
                    running = false, onFrameRendered = { drawn.set(true) })
            }
        }
        compose.waitUntil(30_000) { drawn.get() }
        val actual = compose.onNodeWithTag("approved_main_avatar").captureToImage().asAndroidBitmap()
        val expected = Bitmap.createBitmap(256, 384, Bitmap.Config.ARGB_8888)
        Canvas(expected).apply {
            drawColor(0xFFF8FAF8.toInt())
            drawBitmap(avatarBitmap(context, AvatarStyle()), null, Rect(0, 0, 256, 384), Paint().apply { isFilterBitmap = false })
        }
        val evidence = context.getExternalFilesDir("approved-avatar-verification")!!
        evidence.mkdirs()
        for ((name, bitmap) in listOf("actual.png" to actual, "expected.png" to expected)) {
            File(evidence, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertEquals(256, actual.width); assertEquals(384, actual.height)
        val a = pixels(actual); val b = pixels(expected)
        for (index in a.indices) for (shift in listOf(0, 8, 16, 24)) {
            assertTrue("Visible body differs from approved source at pixel $index",
                abs(((a[index] ushr shift) and 255) - ((b[index] ushr shift) and 255)) <= 3)
        }
    }

    @Test fun unfinishedArtworkCannotSpendPointsEvenWithEnoughBalance() {
        val state = mutableStateOf(AppState(profile = Profile(mileage = 10_000)))
        compose.setContent { RunmoniTheme { ClosetDialog(state.value.profile, RunmoniUiTest.FakeActions(state)) {} } }
        compose.onNodeWithTag("closet_top_1").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(10_000, state.value.profile.mileage)
            assertEquals(AvatarStyle(), state.value.profile.avatar)
            assertFalse("top:1" in state.value.profile.ownedItems)
        }
    }
}

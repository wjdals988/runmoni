package com.runmoni.app.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.RunningKitColors
import com.runmoni.app.ui.avatarBitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunningKitRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun decode(path: String): Bitmap = context.assets.open(path).use {
        requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888
        }))
    }
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }
    private fun cell(bitmap: Bitmap, gender: AvatarGender, frame: Int) = IntArray(256 * 384).also {
        bitmap.getPixels(it, 0, 256, frame * 256, gender.spriteRow * 384, 256, 384)
    }

    @Test fun originalColorsKeepEveryDecodedFrameAndGenderCropUnchanged() {
        val sheet = decode(ApprovedAvatarRenderer.ASSET)
        try {
            val renderer = ApprovedAvatarRenderer(context)
            for (gender in AvatarGender.entries) for (frame in 0..7) {
                val original = renderer.render(frame, true, gender)
                assertArrayEquals(cell(sheet, gender, frame), pixels(original))
                assertSame(original, renderer.render(frame, true, gender, RunningKitColors(-1, 99, -3)))
            }
            assertEquals(0, renderer.cachedColorBytes())
        } finally { sheet.recycle() }
    }

    @Test fun actualAndroidArgbProtectsOtherRegionsLocksAndAlphaInEveryFrame() {
        assertTrue(RunningKitArtwork.isAvailable(context))
        val mask = decode("${RunningKitArtwork.DIRECTORY}/fabric-mask.png")
        val protected = decode("${RunningKitArtwork.DIRECTORY}/protected-mask.png")
        val renderer = ApprovedAvatarRenderer(context)
        val choices = listOf(RunningKitColors(top = 3), RunningKitColors(bottom = 4), RunningKitColors(shoes = 5), RunningKitColors(3, 4, 5))
        try {
            for (gender in AvatarGender.entries) for (frame in 0..7) {
                val base = renderer.render(frame, true, gender)
                val source = pixels(base)
                val labels = cell(mask, gender, frame)
                val locks = cell(protected, gender, frame)
                for (colors in choices) {
                    val output = pixels(renderer.render(frame, true, gender, colors))
                    val chosen = intArrayOf(0, colors.top, colors.bottom, colors.shoes)
                    val changed = IntArray(4)
                    for (i in source.indices) {
                        if ((source[i] ushr 24) != (output[i] ushr 24)) fail("Alpha ${gender.name}/$frame/$i")
                        val region = (labels[i] ushr 16) and 255
                        if (labels[i] == 0 || locks[i] != 0 || chosen[region] == 0) {
                            if (source[i] != output[i]) fail("Protected/other ${gender.name}/$frame/$i")
                        } else if (source[i] != output[i]) changed[region]++
                    }
                    for (region in 1..3) if (chosen[region] != 0) assertTrue("Region $region changed", changed[region] > 0)
                    assertArrayEquals("Input bitmap unchanged", source, pixels(base))
                }
            }
        } finally { mask.recycle(); protected.recycle() }
    }

    @Test fun sharedDisplayUsesColorsAndCacheStaysBoundedAcrossSelections() {
        val renderer = ApprovedAvatarRenderer(context)
        for (gender in AvatarGender.entries) {
            val avatar = AvatarStyle(gender = gender, runningKitColors = RunningKitColors(3, 4, 5))
            assertArrayEquals(pixels(renderer.renderForDisplay(7, true, gender, avatar.runningKitColors)),
                pixels(avatarBitmap(context, avatar, 7, true)))
            assertArrayEquals(pixels(renderer.renderForDisplay(0, false, gender)),
                pixels(avatarBitmap(context, avatar.copy(runningKitColors = RunningKitColors()), 7, false)))
        }
        for (top in 1..5) for (bottom in 1..5) renderer.render(0, true, AvatarGender.FEMALE, RunningKitColors(top, bottom, 5))
        assertTrue(renderer.cachedColorBytes() > 0)
        assertTrue(renderer.cachedColorBytes() <= ApprovedAvatarRenderer.COLOR_CACHE_BYTES)
    }

    @Test fun displayPaddingLeavesRawFramesAndEveryBodyPixelUnchanged() {
        val renderer = ApprovedAvatarRenderer(context)
        for (frame in 0..7) {
            val colors = RunningKitColors(3, 4, 5)
            val raw = renderer.render(frame, true, AvatarGender.FEMALE, colors)
            val before = pixels(raw)
            val displayed = pixels(renderer.renderForDisplay(frame, true, AvatarGender.FEMALE, colors))
            for (i in before.indices) {
                if (i < 256 * ApprovedAvatarRenderer.FEMALE_DISPLAY_PADDING) {
                    if (displayed[i] != 0) fail("Previous-row padding visible")
                } else if (displayed[i] != before[i]) fail("Body pixel changed by presentation: $frame/$i")
            }
            assertArrayEquals(before, pixels(raw))
        }
    }
}

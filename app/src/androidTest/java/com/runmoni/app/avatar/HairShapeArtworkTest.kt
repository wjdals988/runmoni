package com.runmoni.app.avatar

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.domain.AvatarCatalogue
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.AvatarStyle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HairShapeArtworkTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val variants = listOf(0, 4, 5, 6)
    private val width = ApprovedAvatarRenderer.WIDTH
    private val height = ApprovedAvatarRenderer.HEIGHT

    private fun pixels(bitmap: Bitmap) = IntArray(width * height).also {
        bitmap.getPixels(it, 0, width, 0, 0, width, height)
    }

    @Test fun fourReadyStylesHaveSeparateSilhouettesAcrossAllApprovedPoses() {
        val renderer = ApprovedAvatarRenderer(context)
        val slot = AvatarCatalogue.slots.single { it.key == "hair" }
        for (gender in AvatarGender.entries) {
            assertEquals(variants, slot.variants.indices.filter { AvatarArtwork.itemReady("hair", it, gender) })
            val style = AvatarStyle(gender = gender)
            for (frame in 0 until ApprovedAvatarRenderer.FRAMES) {
                val eye = AvatarEquipmentLayers.anchors(frame, gender).eye
                val original = pixels(renderer.render(frame, true, style))
                val next = variants.drop(1).map { variant ->
                    pixels(renderer.render(frame, true, style.copy(hair = variant))).also { after ->
                        assertTrue("$gender/$frame/$variant has no fitted ink",
                            original.indices.count { original[it] != after[it] } > 500)
                        for (i in original.indices) {
                            val color = original[i]
                            val r = (color ushr 16) and 255
                            val g = (color ushr 8) and 255
                            val b = color and 255
                            val sourceSkin = color ushr 24 > 0 && r >= 147 && g >= 52 && r - b >= 49
                            if (i / width >= eye.y + 26 || sourceSkin)
                                assertEquals("$gender/$frame/$variant altered original anatomy at $i", color, after[i])
                        }
                    }
                }
                val all = listOf(original) + next
                for (i in all.indices) for (j in 0 until i) {
                    assertTrue("$gender/$frame hair $i and $j share an outline",
                        original.indices.count { ((all[i][it] ushr 24) >= 128) != ((all[j][it] ushr 24) >= 128) } >= 340)
                }
                assertArrayEquals(original, pixels(renderer.render(frame, true, style)))
            }
        }
    }

    @Test fun existingCapsCoverTrimmedSourceHairWithoutOpenHoles() {
        val renderer = ApprovedAvatarRenderer(context)
        for (gender in AvatarGender.entries) for (cap in listOf(1, 4, 8)) {
            val frame = 0
            val style = AvatarStyle(gender = gender)
            val plain = pixels(renderer.render(frame, true, style))
            val covered = pixels(renderer.render(frame, true, style.copy(headwear = cap)))
            val capPixels = plain.indices.filter { plain[it] != covered[it] && covered[it] ushr 24 >= 192 }
            assertTrue(capPixels.isNotEmpty())
            for (hair in variants.drop(1)) {
                val after = pixels(renderer.render(frame, true, style.copy(hair = hair, headwear = cap)))
                assertTrue("$gender/$cap/$hair opened a hole in the cap", capPixels.all { after[it] ushr 24 >= 192 })
            }
        }
    }
}

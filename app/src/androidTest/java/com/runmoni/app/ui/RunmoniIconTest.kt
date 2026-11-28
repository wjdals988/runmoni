package com.runmoni.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Android vector rendering; no Activity/auth/network and no visual-approval claim. */
@RunWith(AndroidJUnit4::class)
class RunmoniIconTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun draw(resource: Int, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        requireNotNull(context.getDrawable(resource)).mutate().apply {
            setBounds(0, 0, size, size)
            draw(Canvas(bitmap))
        }
        return bitmap
    }

    @Test fun foregroundAndThemedSilhouetteShareAContourInsideTheAdaptiveSafeCircle() {
        val colored = draw(R.drawable.ic_launcher_foreground, 108)
        val monochrome = draw(R.drawable.ic_launcher_monochrome, 108)
        try {
            var occupied = 0
            for (y in 0 until 108) for (x in 0 until 108) {
                val alpha = colored.getPixel(x, y) ushr 24
                assertEquals("Themed tint must retain the same silhouette", alpha, monochrome.getPixel(x, y) ushr 24)
                if (alpha > 16) {
                    occupied++
                    val dx = x + .5 - 54; val dy = y + .5 - 54
                    assertTrue("Essential shape would be clipped by the adaptive safe circle", dx * dx + dy * dy <= 33 * 33)
                }
            }
            assertTrue("The adaptive mark is empty or too fine to identify", occupied > 500)
        } finally { colored.recycle(); monochrome.recycle() }
    }

    @Test fun notificationRemainsAnUnpaddedAlphaMarkWithAnOpenCounterAt24Pixels() {
        val small = draw(R.drawable.ic_stat_runmoni, 24)
        try {
            val alpha = IntArray(24 * 24) { index -> small.getPixel(index % 24, index / 24) ushr 24 }
            assertEquals(0, alpha[0]); assertEquals(0, alpha[23]); assertEquals(0, alpha[23 * 24]); assertEquals(0, alpha.last())
            val occupied = alpha.indices.filter { alpha[it] > 16 }
            assertTrue("Notification identity must have a substantial stroke", occupied.size > 80)
            val width = occupied.maxOf { it % 24 } - occupied.minOf { it % 24 } + 1
            val height = occupied.maxOf { it / 24 } - occupied.minOf { it / 24 } + 1
            assertTrue("Launcher overscan must not make a tiny status-bar mark", width in 16..20 && height in 16..20)
            // Flood only edge-connected transparency. Any remaining transparent
            // pixels are the internal counter, which must survive at status size.
            val outside = BooleanArray(alpha.size)
            val queue = ArrayDeque<Int>()
            fun seed(index: Int) { if (alpha[index] <= 16 && !outside[index]) { outside[index] = true; queue.addLast(index) } }
            for (i in 0 until 24) { seed(i); seed(23 * 24 + i); seed(i * 24); seed(i * 24 + 23) }
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst(); val x = i % 24; val y = i / 24
                if (x > 0) seed(i - 1); if (x < 23) seed(i + 1)
                if (y > 0) seed(i - 24); if (y < 23) seed(i + 24)
            }
            assertTrue("The R counter closed at notification size", alpha.indices.any { alpha[it] <= 16 && !outside[it] })
        } finally { small.recycle() }
    }

    @Test fun installedNormalRoundAndSmallNotificationResourcesRenderAtTheirConsumerSizes() {
        val directory = File(context.cacheDir, "design-review").apply { mkdirs() }
        for (size in listOf(48, 96)) for ((label, resource) in listOf(
            "launcher" to R.mipmap.ic_launcher, "launcher-round" to R.mipmap.ic_launcher_round,
            "full-tile" to R.drawable.ic_runmoni,
        )) {
            val bitmap = draw(resource, size)
            try {
                assertTrue("The resource must render without an empty tile", (0 until size).any { x -> bitmap.getPixel(x, size / 2) ushr 24 > 16 })
                File(directory, "brand-$label-$size.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
        val notification = draw(R.drawable.ic_stat_runmoni, 24)
        try { File(directory, "brand-notification-24.png").outputStream().use { notification.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { notification.recycle() }
    }

    @Test fun liftedAndShadedPlateKeepsTheMarkReadableAtSmallSize() {
        val plate = draw(R.drawable.ic_launcher_plate, 108)
        val mark = draw(R.drawable.ic_launcher_foreground, 108)
        val tile = draw(R.drawable.ic_runmoni, 24)
        try {
            assertTrue(ColorUtils.calculateLuminance(plate.getPixel(24, 24)) >
                ColorUtils.calculateLuminance(plate.getPixel(84, 84)))
            for (y in 0 until 108) for (x in 0 until 108) {
                val ink = mark.getPixel(x, y)
                if (ink ushr 24 == 255) assertTrue(ColorUtils.calculateContrast(ink, plate.getPixel(x, y)) >= 3)
            }
            assertEquals(0, tile.getPixel(0, 0) ushr 24)
            assertTrue((0 until 24).any { x -> (tile.getPixel(x, 12) ushr 24) == 255 })
        } finally { plate.recycle(); mark.recycle(); tile.recycle() }
    }
}

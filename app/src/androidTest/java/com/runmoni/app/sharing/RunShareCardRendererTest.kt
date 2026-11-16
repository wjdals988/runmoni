package com.runmoni.app.sharing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runmoni.app.avatar.ApprovedAvatarRenderer
import com.runmoni.app.domain.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android bitmap composition with synthetic photos/records. Not Main, Picker, SNS or art approval. */
@RunWith(AndroidJUnit4::class)
class RunShareCardRendererTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val start = Instant.parse("2026-11-11T00:00:00Z").toEpochMilli()
    private fun content(interrupted: Boolean = false) = RunShareContent.from(RunRecord(
        "synthetic-share-run", start, start + 1_800_000, distanceMeters = 5230.0,
        activeDurationMillis = 1_738_000, interrupted = interrupted), ZoneId.of("Asia/Seoul"))!!
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    @Test fun fullBleedPhotoHasNoTintRibbonsOrGradientForEveryStyleToneAndRatio() {
        for (shade in listOf(Color.WHITE, Color.BLACK)) {
            val photo = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(shade) }
            val original = pixels(photo)
            try {
                for (ratio in RunShareRatio.entries) for (style in RunShareStyle.entries) for (tone in RunShareTextTone.entries) {
                    val result = RunShareCardRenderer.render(content(true), RunShareDraft(ratio = ratio,
                        style = style, textTone = tone, showAvatar = false, caption = "내 페이스로 달린 오늘",
                        showName = true, photoZoom = 3f, photoX = -100f, photoY = 100f), photo, null, "검토용 러너")
                    try {
                        assertEquals(ratio.width, result.width); assertEquals(ratio.height, result.height)
                        // Every outer pixel stays photographic, including the old header/footer
                        // locations. A full-width tint/gradient/panel fails this contract.
                        for (x in 0 until result.width) {
                            assertEquals(shade, result.getPixel(x, 0))
                            assertEquals(shade, result.getPixel(x, result.height - 1))
                        }
                        for (y in 0 until result.height) {
                            assertEquals(shade, result.getPixel(0, y))
                            assertEquals(shade, result.getPixel(result.width - 1, y))
                        }
                        for (y in 0 until result.height step 37) for (x in 0 until result.width step 37)
                            assertEquals(255, Color.alpha(result.getPixel(x, y)))
                        assertFalse(photo.isRecycled); assertArrayEquals(original, pixels(photo))
                    } finally { result.recycle() }
                }
            } finally { photo.recycle() }
        }
    }

    @Test fun stylesAndOppositeTextOutlinesRemainVisibleAndCanonicalOnBrightAndDarkPhotos() {
        val signatures = mutableSetOf<Int>()
        for (style in RunShareStyle.entries) for (tone in RunShareTextTone.entries) {
            // Deliberately choose the hardest matching photo for each text fill.
            val shade = if (tone == RunShareTextTone.LIGHT) Color.WHITE else Color.BLACK
            val photo = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(shade) }
            val result = RunShareCardRenderer.render(content(true), RunShareDraft(style = style, textTone = tone,
                showAvatar = false, caption = "가볍게 달린 오늘", showName = true), photo, null, "검토용 러너")
            try {
                val image = pixels(result)
                assertTrue("Actual letters/outline must separate from a matching photo", image.count { it != shade } > 200)
                if (tone == RunShareTextTone.LIGHT)
                    assertTrue("White letters need actual dark local outlines", image.any { Color.red(it) < 80 && Color.green(it) < 80 && Color.blue(it) < 80 })
                else assertTrue("Dark letters need actual light local outlines", image.any { Color.red(it) > 220 && Color.green(it) > 220 && Color.blue(it) > 220 })
                if (tone == RunShareTextTone.LIGHT) signatures += image.contentHashCode()
                assertPngRoundTrip(result, "share-card-fullbleed-${style.name.lowercase()}-${tone.name.lowercase()}-fixture.png")
            } finally { result.recycle(); photo.recycle() }
        }
        assertEquals("Presets change composition, not just their labels", 3, signatures.size)
    }

    @Test fun approvedMaleAndFemalePaletteStaysExactAndBorrowedSourcesStayAlive() {
        val renderer = ApprovedAvatarRenderer(context)
        val photo = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff374b42.toInt()) }
        val originalPhoto = pixels(photo)
        try {
            for (gender in AvatarGender.entries) {
                val avatar = renderer.renderForDisplay(2, true, AvatarStyle(top = 1, bottom = 1, socks = 2, shoes = 2, gender = gender))
                val original = pixels(avatar)
                val flat = Bitmap.createBitmap(avatar.width, avatar.height, Bitmap.Config.ARGB_8888)
                val flattened = try {
                    Canvas(flat).apply { drawColor(photo.getPixel(0, 0)); drawBitmap(avatar, 0f, 0f,
                        Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }) }
                    pixels(flat)
                } finally { flat.recycle() }
                val required = original.indices.filter { Color.alpha(original[it]) >= 250 }.map { flattened[it] }.toMutableSet()
                assertTrue(required.size > 10)
                val draft = RunShareDraft(ratio = if (gender == AvatarGender.MALE) RunShareRatio.POST else RunShareRatio.STORY,
                    avatarX = .25f, avatarY = .25f, avatarScale = 1.5f, recordY = .85f)
                val result = RunShareCardRenderer.render(content(), draft, photo, avatar)
                try {
                    for (pixel in pixels(result)) { required.remove(pixel); if (required.isEmpty()) break }
                    assertTrue("The complete enlarged nearest sprite retains its genuine palette away from the text object", required.isEmpty())
                    assertFalse(avatar.isRecycled); assertArrayEquals(original, pixels(avatar)); assertArrayEquals(originalPhoto, pixels(photo))
                    assertPngRoundTrip(result, "share-card-fullbleed-${gender.name.lowercase()}-approved-fixture.png")
                } finally { result.recycle() }
                // Shared approved renderer owns avatar; do not recycle it.
            }
        } finally { photo.recycle() }
    }

    @Test fun recordAndAvatarMoveIndependentlyAndReactImmediatelyAtTheirCanvasEdges() {
        val avatar = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            for (ratio in RunShareRatio.entries) for (style in RunShareStyle.entries) {
                val draft = RunShareDraft(ratio = ratio, style = style, caption = "가".repeat(60), showName = true,
                    recordX = -9f, recordY = 9f, avatarX = 9f, avatarY = -9f)
                val record = RunShareCardRenderer.constrainRecord(content(true), draft, "검토용 러너")
                assertEquals(draft.avatarX, record.avatarX, 0f); assertEquals(draft.avatarY, record.avatarY, 0f)
                assertEquals(draft.caption, record.caption)
                val bounds = RunShareCardRenderer.recordBounds(content(true), record, "검토용 러너")
                assertTrue(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= ratio.width && bounds.bottom <= ratio.height)
                val moved = RunShareCardRenderer.constrainRecord(content(true), record.copy(recordX = record.recordX + .01f,
                    recordY = record.recordY - .01f), "검토용 러너")
                assertTrue(moved.recordX > record.recordX); assertTrue(moved.recordY < record.recordY)
                val sticker = RunShareCardRenderer.constrainAvatar(content(true), draft, avatar, "검토용 러너")
                assertEquals(draft.recordX, sticker.recordX, 0f); assertEquals(draft.recordY, sticker.recordY, 0f)
                val reverse = RunShareCardRenderer.constrainAvatar(content(true), sticker.copy(
                    avatarX = sticker.avatarX - .01f, avatarY = sticker.avatarY + .01f), avatar)
                assertTrue(reverse.avatarX < sticker.avatarX); assertTrue(reverse.avatarY > sticker.avatarY)
            }
        } finally { avatar.recycle() }
    }

    @Test fun missingAvatarAndLongUnicodeMetricsDoNotSilentlyDisappearOrClipTheCanvas() {
        assertTrue(runCatching { RunShareCardRenderer.render(content(), RunShareDraft(), null, null) }.isFailure)
        val mutable = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        try {
            Canvas(mutable).drawRect(0f, 0f, 20f, 80f, Paint().apply { color = Color.RED })
            val narrow = RunShareCardRenderer.constrainAvatar(content(), RunShareDraft(avatarX = 0f), mutable)
            mutable.eraseColor(Color.TRANSPARENT)
            Canvas(mutable).drawRect(0f, 0f, 80f, 20f, Paint().apply { color = Color.RED })
            val wide = RunShareCardRenderer.constrainAvatar(content(), RunShareDraft(avatarX = 0f), mutable)
            assertTrue("Mutable generation invalidates cached source bounds", wide.avatarX - narrow.avatarX > .2f)
        } finally { mutable.recycle() }
        val long = RunShareContent.from(RunRecord("synthetic-wide-values", start, start + 1,
            distanceMeters = 100_000_000_000.0, activeDurationMillis = 1_000_000_000_000L))!!
        for (ratio in RunShareRatio.entries) for (style in RunShareStyle.entries) {
            val draft = RunShareDraft(ratio = ratio, style = style, caption = "😀".repeat(60), showName = true,
                showAvatar = false, recordX = 1f, recordY = 0f)
            assertEquals(60, draft.normalized().caption.codePointCount(0, draft.normalized().caption.length))
            val bounds = RunShareCardRenderer.recordBounds(long, draft, "가".repeat(32))
            assertTrue(bounds.right <= ratio.width && bounds.bottom <= ratio.height && bounds.top >= 0 && bounds.left >= 0)
            val result = RunShareCardRenderer.render(long, draft, null, null, "가".repeat(32))
            try {
                assertEquals(ratio.height, result.height)
                assertEquals(255, Color.alpha(result.getPixel(0, 0)))
                assertEquals(result.getPixel(0, 0), result.getPixel(result.width - 1, result.height - 1))
            } finally { result.recycle() }
        }
    }

    @Test fun legacyThemesRemainReadableButNeverIntroduceColoredPhotoOrFallbackSurfaces() {
        assertEquals(listOf("FOREST", "INK", "PAPER", "CORAL"), RunShareTheme.entries.map { it.name })
        assertEquals(listOf(0, 1, 2, 3), RunShareTheme.entries.map { it.ordinal })
        assertEquals(RunShareTheme.FOREST, RunShareDraft().theme)
        assertEquals(RunShareRatio.SQUARE, RunShareDraft().ratio)
        var reference: IntArray? = null
        for (theme in RunShareTheme.entries) {
            val result = RunShareCardRenderer.render(content(), RunShareDraft(theme = theme, showAvatar = false), null, null)
            try {
                assertEquals(0xff20272b.toInt(), result.getPixel(0, 0))
                val image = pixels(result)
                if (reference == null) reference = image else assertArrayEquals(reference, image)
            } finally { result.recycle() }
        }
    }

    private fun assertPngRoundTrip(bitmap: Bitmap, filename: String) {
        val target = File(context.cacheDir, "run-share-review/$filename")
        target.parentFile!!.mkdirs()
        target.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val decoded = BitmapFactory.decodeFile(target.absolutePath)
        assertNotNull(decoded)
        try {
            assertEquals(bitmap.width, decoded!!.width); assertEquals(bitmap.height, decoded.height)
            assertArrayEquals("PNG is the exact preview bitmap", pixels(bitmap), pixels(decoded))
        } finally { decoded?.recycle() }
    }
}

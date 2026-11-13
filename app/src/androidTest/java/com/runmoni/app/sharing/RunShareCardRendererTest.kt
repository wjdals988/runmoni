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
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.RunRecord
import com.runmoni.app.domain.RunShareAvatarPlacement
import com.runmoni.app.domain.RunShareContent
import com.runmoni.app.domain.RunShareDraft
import com.runmoni.app.domain.RunSharePlacement
import com.runmoni.app.domain.RunShareRatio
import com.runmoni.app.domain.RunShareTheme
import com.runmoni.app.domain.RunShareText
import com.runmoni.app.domain.TrackPoint
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Native bitmap compositor checks. Exported cards are synthetic fixtures, never MainActivity or user artwork approval. */
@RunWith(AndroidJUnit4::class)
class RunShareCardRendererTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val start = Instant.parse("2026-11-11T00:00:00Z").toEpochMilli()
    private fun content(interrupted: Boolean = false) = RunShareContent.from(RunRecord(
        "synthetic-share-run", start, start + 1_800_000,
        distanceMeters = 5230.0, activeDurationMillis = 1_738_000,
        interrupted = interrupted), ZoneId.of("Asia/Seoul"))!!
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    @Test fun photoCoverAndEveryThemeKeepOpaqueRatiosWithoutChangingTheBorrowedPhoto() {
        val photo = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(0xffc2793e.toInt()) }
        val original = pixels(photo)
        try {
            for (ratio in RunShareRatio.entries) for (theme in RunShareTheme.entries) for (placement in RunSharePlacement.entries) {
                val result = RunShareCardRenderer.render(content(), RunShareDraft(
                    ratio = ratio, theme = theme, placement = placement, showAvatar = false,
                    photoZoom = 3f, photoX = -100f, photoY = 100f), photo, null)
                try {
                    assertEquals(1080, result.width)
                    assertEquals(ratio.height, result.height)
                    val clearPhotoY = if (placement == RunSharePlacement.BOTTOM) ratio.height / 2 else ratio.height - 80
                    assertEquals("No blank crop edge or unrelated tint over the central photo", original[0], result.getPixel(0, clearPhotoY))
                    assertEquals(original[0], result.getPixel(result.width - 1, clearPhotoY))
                    for (y in 0 until result.height step 37) for (x in 0 until result.width step 37)
                        assertEquals(255, Color.alpha(result.getPixel(x, y)))
                } finally { result.recycle() }
            }
            assertFalse(photo.isRecycled)
            assertArrayEquals(original, pixels(photo))
        } finally { photo.recycle() }
    }

    @Test fun coralKeepsReadablePhotoSurfacesAndCanonicalPngAcrossAllRatios() {
        assertEquals(listOf("FOREST", "INK", "PAPER"), RunShareTheme.entries.take(3).map { it.name })
        assertEquals(listOf(0, 1, 2), RunShareTheme.entries.take(3).map { it.ordinal })
        assertEquals(RunShareTheme.CORAL, RunShareTheme.entries.last())
        assertEquals(RunShareTheme.FOREST, RunShareDraft().theme)
        assertEquals(RunShareRatio.SQUARE, RunShareDraft().ratio)
        val privateRecord = RunRecord("synthetic-private-id-a", start, start + 1_800_000,
            distanceMeters = 5230.0, activeDurationMillis = 1_738_000,
            points = listOf(TrackPoint(0, 37.5665, 126.9780, 5f, start)), mileage = 90)
        val publicContent = RunShareContent.from(privateRecord, ZoneId.of("Asia/Seoul"))!!
        assertEquals("Private ID, route and wallet must remain outside share content", publicContent,
            RunShareContent.from(privateRecord.copy(id = "synthetic-private-id-b", mileage = 999,
                points = listOf(TrackPoint(0, 35.1796, 129.0756, 5f, start))), ZoneId.of("Asia/Seoul")))
        val foreground = 0xff192d25.toInt()
        val secondary = 0xff556158.toInt()
        for (ratio in RunShareRatio.entries) for (shade in listOf<Int?>(null, Color.WHITE, Color.BLACK)) {
            val shadeName = when (shade) { Color.WHITE -> "bright"; Color.BLACK -> "dark"; else -> "plain" }
            val label = "${ratio.name}/$shadeName"
            val photo = shade?.let { color -> Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) } }
            val original = photo?.let(::pixels)
            val result = RunShareCardRenderer.render(publicContent, RunShareDraft(ratio = ratio,
                theme = RunShareTheme.CORAL,
                placement = if (shade == Color.BLACK) RunSharePlacement.TOP else RunSharePlacement.BOTTOM,
                caption = "나의 페이스로", showName = true, showAvatar = false), photo, null, "검토용 러너")
            try {
                val image = pixels(result)
                assertEquals(ratio.width, result.width)
                assertEquals(ratio.height, result.height)
                assertTrue("$label actual text must be drawn in the dark foreground", image.any { it == foreground })
                assertTrue("$label actual labels must be drawn in the secondary color", image.any { it == secondary })
                // x=0 contains the real full-width header/stat surfaces, outside all text.
                // Measure their final composed colors rather than assuming the photo alpha.
                val surfaces = (0 until result.height).map { result.getPixel(0, it) }
                    .filter { shade == null || it != shade }.toSet()
                assertTrue("$label must contain a legible theme surface", surfaces.isNotEmpty())
                surfaces.forEach { background ->
                    assertEquals("$label composed surface must be opaque", 255, Color.alpha(background))
                    assertTrue("$label primary contrast=${contrast(foreground, background)}", contrast(foreground, background) >= 4.5)
                    assertTrue("$label secondary contrast=${contrast(secondary, background)}", contrast(secondary, background) >= 4.5)
                }
                assertPngRoundTrip(result, "share-card-coral-${ratio.name.lowercase()}-$shadeName-fixture.png")
                if (photo != null) {
                    assertFalse("$label borrowed photo stays alive", photo.isRecycled)
                    assertArrayEquals("$label borrowed photo must not be tinted", original!!, pixels(photo))
                }
            } finally { result.recycle(); photo?.recycle() }
        }
    }

    @Test fun coralLeavesApprovedMaleAndFemalePaletteAndBorrowedPixelsIntact() {
        val renderer = ApprovedAvatarRenderer(context)
        val photo = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff374b42.toInt()) }
        val originalPhoto = pixels(photo)
        try {
            for (gender in AvatarGender.entries) {
                val avatar = renderer.renderForDisplay(2, true, AvatarStyle(top = 1, bottom = 1,
                    socks = 2, shoes = 2, gender = gender))
                val original = pixels(avatar)
                val flat = Bitmap.createBitmap(avatar.width, avatar.height, Bitmap.Config.ARGB_8888)
                val flattened = try {
                    Canvas(flat).apply {
                        drawColor(photo.getPixel(0, 0))
                        drawBitmap(avatar, 0f, 0f, Paint().apply {
                            isFilterBitmap = false; isAntiAlias = false; isDither = false
                        })
                    }
                    pixels(flat)
                } finally { flat.recycle() }
                val required = original.indices.filter { Color.alpha(original[it]) >= 250 }.map { flattened[it] }.toMutableSet()
                assertTrue("$gender approved palette must be meaningful", required.size > 10)
                val result = RunShareCardRenderer.render(content(true), RunShareDraft(
                    ratio = if (gender == AvatarGender.MALE) RunShareRatio.SQUARE else RunShareRatio.POST,
                    theme = RunShareTheme.CORAL, showName = true, avatarX = .25f, avatarY = -9f, avatarScale = 1.5f),
                    photo, avatar, "검토용 러너")
                try {
                    for (pixel in pixels(result)) {
                        required.remove(pixel)
                        if (required.isEmpty()) break
                    }
                    assertTrue("$gender coral surface must not tint or hide the genuine character palette", required.isEmpty())
                    assertFalse(avatar.isRecycled)
                    assertArrayEquals("$gender borrowed approved source must be unchanged", original, pixels(avatar))
                    assertArrayEquals("$gender borrowed photo must be unchanged", originalPhoto, pixels(photo))
                    assertPngRoundTrip(result, "share-card-coral-${gender.name.lowercase()}-approved-fixture.png")
                } finally { result.recycle() }
                // The genuine renderer owns avatar. Never recycle its shared bitmap.
            }
        } finally { photo.recycle() }
    }

    @Test fun approvedMaleAndFemalePixelsRemainVisibleAndUnchangedUnderExtremeStickerControls() {
        val renderer = ApprovedAvatarRenderer(context)
        val photo = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff374b42.toInt()) }
        try {
            for (gender in AvatarGender.entries) {
                val avatar = renderer.renderForDisplay(2, true, AvatarStyle(
                    top = 1, bottom = 1, socks = 2, shoes = 2, gender = gender))
                val original = pixels(avatar)
                val ratio = if (gender == AvatarGender.MALE) RunShareRatio.POST else RunShareRatio.STORY
                val draft = RunShareDraft(ratio = ratio,
                    theme = if (gender == AvatarGender.MALE) RunShareTheme.PAPER else RunShareTheme.INK,
                    placement = if (gender == AvatarGender.MALE) RunSharePlacement.BOTTOM else RunSharePlacement.TOP,
                    caption = "내 페이스로 꾸준히 달리기 ".repeat(5), showName = true,
                    avatarX = if (gender == AvatarGender.MALE) -9f else 9f,
                    avatarY = if (gender == AvatarGender.MALE) 9f else -9f, avatarScale = 1.5f)
                val smallAtWall = RunShareCardRenderer.constrainAvatar(content(true), draft.copy(avatarScale = .65f), avatar, "FixtureRunner")
                assertEquals("Constraint must not trim text being edited", draft.caption, smallAtWall.caption)
                val reverse = if (gender == AvatarGender.MALE) 1f else -1f
                val firstOppositeGesture = RunShareCardRenderer.constrainAvatar(content(true), smallAtWall.copy(
                    avatarX = smallAtWall.avatarX + reverse * .01f,
                    avatarY = smallAtWall.avatarY - reverse * .01f), avatar, "FixtureRunner")
                assertTrue("Reversing at a horizontal wall reacts on the first gesture",
                    (firstOppositeGesture.avatarX - smallAtWall.avatarX) * reverse > .005f)
                assertTrue("Reversing at the stats boundary reacts on the first gesture",
                    (smallAtWall.avatarY - firstOppositeGesture.avatarY) * reverse > .005f)
                val result = RunShareCardRenderer.render(content(interrupted = true), draft, photo, avatar, "FixtureRunner")
                try {
                    // The approved PNG's strongest original alpha is 254, not 255.
                    // A normal SRC_OVER draw therefore changes its displayed RGB over a photo.
                    // Independently flatten at 1:1 over this fixture's known solid photo color;
                    // require the near-opaque character palette to survive the enlarged nearest draw.
                    val flattened = Bitmap.createBitmap(avatar.width, avatar.height, Bitmap.Config.ARGB_8888)
                    val flatPixels = try {
                        val reference = Canvas(flattened)
                        reference.drawColor(photo.getPixel(0, 0))
                        reference.drawBitmap(avatar, 0f, 0f, Paint().apply {
                            isFilterBitmap = false; isAntiAlias = false; isDither = false
                        })
                        pixels(flattened)
                    } finally { flattened.recycle() }
                    val requiredColors = original.indices.filter { Color.alpha(original[it]) >= 250 }
                        .map { flatPixels[it] }.toMutableSet()
                    val referencePaletteSize = requiredColors.size
                    assertTrue("$gender near-opaque flattened palette has only $referencePaletteSize colors; " +
                        "source maxAlpha=${original.maxOf { Color.alpha(it) }}", referencePaletteSize > 10)
                    val image = pixels(result)
                    for (pixel in image) {
                        requiredColors.remove(pixel)
                        if (requiredColors.isEmpty()) break
                    }
                    assertTrue("$gender nearest SRC_OVER palette lost ${requiredColors.size}/$referencePaletteSize colors; " +
                        "sample=${requiredColors.take(8).map { Integer.toHexString(it) }}", requiredColors.isEmpty())
                    assertFalse(avatar.isRecycled)
                    assertArrayEquals(original, pixels(avatar))
                    val name = if (gender == AvatarGender.MALE) "share-card-post-approved-fixture.png" else "share-card-story-approved-fixture.png"
                    val target = File(context.cacheDir, "run-share-review/$name")
                    target.parentFile!!.mkdirs()
                    target.outputStream().use { assertTrue(result.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    val decoded = BitmapFactory.decodeFile(target.absolutePath)
                    assertNotNull("$gender fixture PNG must decode", decoded)
                    try {
                        assertEquals(result.width, decoded!!.width)
                        assertEquals(result.height, decoded.height)
                        assertArrayEquals("$gender PNG must retain every composed pixel", image, pixels(decoded))
                    } finally { decoded?.recycle() }
                } finally { result.recycle() }
                // The genuine renderer cache owns avatar. Never recycle that borrowed bitmap.
            }
        } finally { photo.recycle() }
    }

    @Test fun missingAvatarCannotSilentlyExportAndLongActualValuesStillRender() {
        assertTrue(runCatching { RunShareCardRenderer.render(content(), RunShareDraft(), null, null) }.isFailure)
        val mutableFixture = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        try {
            Canvas(mutableFixture).drawRect(0f, 0f, 20f, 80f, Paint().apply { color = Color.RED })
            val narrow = RunShareCardRenderer.constrainAvatar(content(), RunShareDraft(avatarX = 0f), mutableFixture)
            mutableFixture.eraseColor(Color.TRANSPARENT)
            Canvas(mutableFixture).drawRect(0f, 0f, 80f, 20f, Paint().apply { color = Color.RED })
            val wide = RunShareCardRenderer.constrainAvatar(content(), RunShareDraft(avatarX = 0f), mutableFixture)
            assertTrue("generationId must invalidate cached nontransparent bounds", wide.avatarX - narrow.avatarX > .2f)
        } finally { mutableFixture.recycle() } // Owned synthetic input only, never an approved/shared avatar.
        val long = RunShareContent.from(RunRecord("synthetic-wide-values", start, start + 1,
            distanceMeters = 100_000_000_000.0, activeDurationMillis = 1_000_000_000_000L))!!
        assertEquals("100000000.00", long.distance)
        assertEquals("277777:46:40", long.duration)
        for (ratio in RunShareRatio.entries) {
            val result = RunShareCardRenderer.render(long, RunShareDraft(ratio = ratio, showAvatar = false,
                caption = "😀".repeat(60), placement = RunSharePlacement.TOP), null, null)
            try {
                assertEquals(ratio.height, result.height)
                assertEquals(255, Color.alpha(result.getPixel(0, 0)))
                assertEquals(255, Color.alpha(result.getPixel(result.width - 1, result.height - 1)))
            } finally { result.recycle() }
        }
    }

    @Test fun squareKeepsSixtyCodePointsAndACompleteLegibleAvatarWithAllMetadata() {
        assertEquals(RunShareRatio.SQUARE, RunShareDraft().ratio)
        val renderer = ApprovedAvatarRenderer(context)
        val photo = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff374b42.toInt()) }
        val originalPhoto = pixels(photo)
        val record = content(interrupted = true)
        try {
            for (gender in AvatarGender.entries) {
                val avatar = renderer.renderForDisplay(2, true, AvatarStyle(top = 1, bottom = 1,
                    socks = 2, shoes = 2, gender = gender))
                val original = pixels(avatar)
                val nonzero = original.indices.filter { Color.alpha(original[it]) > 0 }
                assertTrue("$gender genuine source must contain visible pixels", nonzero.isNotEmpty())
                val sourceLeft = nonzero.minOf { it % avatar.width }
                val sourceRight = nonzero.maxOf { it % avatar.width } + 1
                val sourceTop = nonzero.minOf { it / avatar.width }
                val sourceBottom = nonzero.maxOf { it / avatar.width } + 1
                val sourceWidth = sourceRight - sourceLeft
                val sourceHeight = sourceBottom - sourceTop
                for (placement in RunSharePlacement.entries) for (emoji in listOf(false, true)) {
                    val caption = if (emoji) "😀".repeat(60) else "가".repeat(60)
                    val draft = RunShareDraft(ratio = RunShareRatio.SQUARE,
                        theme = if (gender == AvatarGender.MALE) RunShareTheme.FOREST else RunShareTheme.PAPER,
                        placement = placement, caption = caption, showDate = true, showName = true,
                        avatarX = if (gender == AvatarGender.MALE) .25f else .76f)
                    val label = "$gender/$placement/${if (emoji) "emoji60" else "Korean60"}"
                    assertEquals(60, draft.normalized().caption.codePointCount(0, draft.normalized().caption.length))
                    val constrained = RunShareCardRenderer.constrainAvatar(record, draft, avatar, "FixtureRunner")
                    // Infer the actual fitted height from two bottom-constrained centers and the genuine alpha bounds.
                    // This does not duplicate the renderer's square header/caption/panel constants.
                    val minimum = draft.copy(avatarScale = RunShareDraft.MIN_AVATAR_SCALE, avatarY = 1f)
                    val minimumHeight = RunShareAvatarPlacement.rect(1080, 1080, sourceWidth, sourceHeight, minimum).height
                    val bottomAtMinimum = RunShareCardRenderer.constrainAvatar(record, minimum, avatar, "FixtureRunner").avatarY * 1080
                    val bottomAtActual = RunShareCardRenderer.constrainAvatar(record, draft.copy(avatarY = 1f), avatar, "FixtureRunner").avatarY * 1080
                    val actualHeight = minimumHeight + 2 * (bottomAtMinimum - bottomAtActual)
                    val actualWidth = actualHeight * sourceWidth / sourceHeight
                    val left = constrained.avatarX * 1080 - actualWidth / 2
                    val right = left + actualWidth
                    val top = constrained.avatarY * 1080 - actualHeight / 2
                    val bottom = top + actualHeight
                    assertTrue("$label whole sticker must remain at least220px high; actual=$actualHeight", actualHeight >= 220f)
                    assertTrue("$label complete original must stay inside square", left >= -.1f && top >= -.1f && right <= 1080.1f && bottom <= 1080.1f)
                    val result = RunShareCardRenderer.render(record, draft, photo, avatar, "FixtureRunner")
                    val noAvatar = RunShareCardRenderer.render(record, draft.copy(showAvatar = false), photo, null, "FixtureRunner")
                    try {
                        assertEquals(1080, result.width)
                        assertEquals(1080, result.height)
                        val image = pixels(result)
                        val baseline = pixels(noAvatar)
                        var changed = 0
                        var minY = 1080
                        var maxY = -1
                        var firstTransparent = -1
                        var firstOutside = -1
                        for (index in image.indices) {
                            if (firstTransparent < 0 && Color.alpha(image[index]) != 255) firstTransparent = index
                            if (image[index] == baseline[index]) continue
                            val x = index % 1080
                            val y = index / 1080
                            if (firstOutside < 0 && !(x >= left - 2 && x <= right + 2 && y >= top - 2 && y <= bottom + 2))
                                firstOutside = index
                            changed++
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        }
                        assertEquals("$label entire PNG must be opaque; first bad index=$firstTransparent", -1, firstTransparent)
                        assertEquals("$label avatar must not change pixels outside its fitted rectangle; first index=$firstOutside", -1, firstOutside)
                        assertTrue("$label must actually draw the owned character", changed > 1000)
                        assertTrue("$label visible character must be at least220px high; observed=${maxY - minY + 1}", maxY - minY + 1 >= 220)
                        // Changing only code point60 must alter actual pixels; silently clipping the final line cannot pass.
                        val finalCodePoint = if (emoji) "😎" else "나"
                        val changedCaption = RunShareText.takeCodePoints(caption, 59) + finalCodePoint
                        val finalCharacter = RunShareCardRenderer.render(record,
                            draft.copy(showAvatar = false, caption = changedCaption), photo, null, "FixtureRunner")
                        try {
                            assertFalse("$label last of60 code points must be rendered", baseline.contentEquals(pixels(finalCharacter)))
                        } finally { finalCharacter.recycle() }
                        val capture = gender == AvatarGender.MALE && placement == RunSharePlacement.BOTTOM && !emoji ||
                            gender == AvatarGender.FEMALE && placement == RunSharePlacement.TOP && emoji
                        if (capture) {
                            val name = if (gender == AvatarGender.MALE) "share-card-square-male-fixture.png" else "share-card-square-female-fixture.png"
                            // Synthetic Canvas export stress fixture, not an Android UI screenshot or user visual approval.
                            val target = File(context.cacheDir, "run-share-review/$name")
                            target.parentFile!!.mkdirs()
                            target.outputStream().use { assertTrue(result.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                            val decoded = BitmapFactory.decodeFile(target.absolutePath)
                            assertNotNull("$label square PNG must decode", decoded)
                            try { assertArrayEquals("$label square PNG must preserve every composed pixel", image, pixels(decoded!!)) }
                            finally { decoded?.recycle() }
                        }
                        assertFalse(avatar.isRecycled)
                        assertArrayEquals("$label borrowed approved pixels must remain unchanged", original, pixels(avatar))
                        assertArrayEquals("$label borrowed photo must remain unchanged", originalPhoto, pixels(photo))
                    } finally { result.recycle(); noAvatar.recycle() }
                }
                // The approved renderer owns avatar; the compositor/test never recycles it.
            }
        } finally { photo.recycle() }
    }

    private fun assertPngRoundTrip(bitmap: Bitmap, filename: String) {
        val target = File(context.cacheDir, "run-share-review/$filename")
        target.parentFile!!.mkdirs()
        target.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val decoded = BitmapFactory.decodeFile(target.absolutePath)
        assertNotNull("Canonical PNG must decode: $filename", decoded)
        try {
            assertEquals(bitmap.width, decoded!!.width)
            assertEquals(bitmap.height, decoded.height)
            assertArrayEquals("Canonical PNG must retain every preview-rendered pixel: $filename", pixels(bitmap), pixels(decoded))
        } finally { decoded?.recycle() }
    }

    private fun contrast(foreground: Int, background: Int): Double {
        fun luminance(color: Int): Double {
            fun linear(value: Int): Double {
                val s = value / 255.0
                return if (s <= .04045) s / 12.92 else ((s + .055) / 1.055).pow(2.4)
            }
            return .2126 * linear(Color.red(color)) + .7152 * linear(Color.green(color)) + .0722 * linear(Color.blue(color))
        }
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }
}

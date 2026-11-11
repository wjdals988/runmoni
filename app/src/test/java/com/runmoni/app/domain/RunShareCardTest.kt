package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Modifier
import java.time.Instant
import java.time.ZoneId

class RunShareCardTest {
    private val start = Instant.parse("2026-11-11T00:00:00Z").toEpochMilli()
    private fun record() = RunRecord("private-run-id", start, start + 1_800_000L,
        distanceMeters = 5000.0, activeDurationMillis = 1_501_000L,
        // Existing completed data may not have an elapsed field.
        elapsedDurationMillis = 0,
        points = listOf(TrackPoint(1, 37.5, 127.0, 5f, start)), mileage = 777)

    @Test fun contentUsesRecordedActiveMetricsAndDoesNotExportSensitiveRecordFields() {
        val content = RunShareContent.from(record(), ZoneId.of("Asia/Seoul"))!!
        assertEquals("5.00", content.distance)
        assertEquals("25:01", content.duration)
        assertEquals("5′00″", content.pace)
        assertEquals("2026.11.11 (수)", content.date)
        val fields = RunShareContent::class.java.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
        assertEquals(setOf("date", "distance", "duration", "pace", "interrupted"), fields)
        assertFalse(content.toString().contains("private-run-id"))
        assertFalse(content.toString().contains("127.0"))
        assertFalse(content.toString().contains("777"))
    }

    @Test fun clockChangesDoNotReplaceOrRejectTheRecordedMonotonicDuration() {
        val reversedWallClock = record().copy(finishedAtMillis = start - 3_600_000, interrupted = true)
        val content = RunShareContent.from(reversedWallClock, ZoneId.of("UTC"))!!
        assertEquals("25:01", content.duration)
        assertEquals("5′00″", content.pace)
        assertTrue(content.interrupted)
    }

    @Test fun unfinishedAndInvalidMetricsAreNotTurnedIntoShareableStatistics() {
        for (invalid in listOf(record().copy(finishedAtMillis = 0), record().copy(startedAtMillis = 0),
            record().copy(distanceMeters = Double.NaN), record().copy(distanceMeters = Double.POSITIVE_INFINITY),
            record().copy(distanceMeters = -1.0), record().copy(activeDurationMillis = -1),
            record().copy(distanceMeters = Double.MAX_VALUE))) assertNull(RunShareContent.from(invalid))
        assertEquals("—", RunShareContent.from(record().copy(distanceMeters = 0.0))!!.pace)
        assertEquals("0.01", RunShareContent.from(record().copy(distanceMeters = 9.9))!!.distance)
        assertEquals("—", RunShareContent.from(record().copy(distanceMeters = 9.9))!!.pace)
        assertEquals("—", RunShareContent.from(record().copy(activeDurationMillis = 0))!!.pace)
        assertEquals("—", RunShareContent.from(record().copy(activeDurationMillis = 1))!!.pace)
        assertEquals("1:00:00", RunShareContent.from(record().copy(activeDurationMillis = 3_600_000))!!.duration)
    }

    @Test fun captionsKeepCompleteUnicodeCodePointsAndRemoveControlMarkup() {
        val emoji = "😀".repeat(61)
        val limited = RunShareText.takeCodePoints(emoji, 60)
        assertEquals(60, limited.codePointCount(0, limited.length))
        assertTrue(Character.isLowSurrogate(limited.last()))
        assertEquals("함께 😀 달리기", RunShareText.clean(" 함께\n 😀\t 달리기\u202e ", 60))
        assertEquals("👩‍👩‍👧‍👧", RunShareText.clean("👩‍👩‍👧‍👧", 60))
        assertEquals("ab", RunShareText.clean("a\uD83Db", 60))
        assertEquals(60, RunShareDraft(caption = emoji).normalized().caption.codePointCount(0, limited.length))
    }

    @Test fun normalizedDraftRejectsNonfiniteAndOutOfRangeTransforms() {
        assertEquals(RunShareRatio.SQUARE, RunShareDraft().ratio)
        assertEquals(RunShareRatio.SQUARE, RunShareRatio.entries.first())
        assertEquals(1080, RunShareDraft().ratio.width)
        assertEquals(1080, RunShareDraft().ratio.height)
        // Saved editor state uses enum names, so inserting SQUARE must not reinterpret older POST/STORY drafts.
        assertEquals(RunShareRatio.POST, RunShareDraft(ratio = RunShareRatio.valueOf("POST")).normalized().ratio)
        assertEquals(1350, RunShareRatio.valueOf("POST").height)
        assertEquals(RunShareRatio.STORY, RunShareRatio.valueOf("STORY"))
        val draft = RunShareDraft(avatarX = Float.NaN, avatarY = -8f, avatarScale = Float.POSITIVE_INFINITY,
            photoZoom = -1f, photoX = Float.NEGATIVE_INFINITY, photoY = Float.NaN).normalized()
        assertEquals(.76f, draft.avatarX, 0f)
        assertEquals(0f, draft.avatarY, 0f)
        assertEquals(1f, draft.avatarScale, 0f)
        assertEquals(1f, draft.photoZoom, 0f)
        assertEquals(0f, draft.photoX, 0f)
        assertEquals(0f, draft.photoY, 0f)
    }

    @Test fun coverCropPreservesAspectAndNeverShowsEmptyPhotoBorders() {
        for (ratio in RunShareRatio.entries) for ((photoWidth, photoHeight) in listOf(320 to 240, 240 to 320,
            1080 to 1350, 4096 to 128, 64 to 4096)) for (zoom in listOf(0f, 1f, 1.25f, 3f, 999f, Float.NaN))
            for (offset in listOf(-1000f, 0f, 1000f, Float.NaN)) {
                val r = RunSharePhotoTransform.cover(photoWidth, photoHeight, ratio.width, ratio.height, zoom, offset, -offset)
                assertTrue(r.left <= .05f)
                assertTrue(r.top <= .05f)
                assertTrue(r.right >= ratio.width - .05f)
                assertTrue(r.bottom >= ratio.height - .05f)
                assertEquals(photoWidth.toDouble() / photoHeight, r.width.toDouble() / r.height, .001)
            }
    }

    @Test fun panoramaCanReachBothEdgesAndRatioChangeReclampsIt() {
        val leftEdge = RunSharePhotoTransform.cover(1000, 500, 1080, 1350, x = 1000f)
        assertEquals(0f, leftEdge.left, .001f)
        assertEquals(2700f, leftEdge.right, .001f)
        val rightEdge = RunSharePhotoTransform.cover(1000, 500, 1080, 1350, x = -1000f)
        assertEquals(-1620f, rightEdge.left, .001f)
        assertEquals(1080f, rightEdge.right, .001f)
        val portrait = RunSharePhotoTransform.cover(1000, 500, 1080, 1920, 3f, -1000f, 1000f)
        assertEquals(1080f, portrait.right, .001f)
        assertEquals(0f, portrait.top, .001f)
        assertTrue(portrait.bottom >= 1920)
    }

    @Test fun pinchKeepsTheTouchedPhotoPointAnchoredAndUsesTheClampedZoom() {
        val original = RunShareDraft(ratio = RunShareRatio.POST, photoZoom = 1.5f)
        val old = RunSharePhotoTransform.cover(1600, 1200, 1080, 1350, original.photoZoom)
        val changed = RunSharePhotoTransform.gesture(original, 1600, 1200, .35f, .45f, 0f, 0f, 1.2f)
        val after = RunSharePhotoTransform.cover(1600, 1200, 1080, 1350,
            changed.photoZoom, changed.photoX, changed.photoY)
        assertEquals((.35f * 1080 - old.left) / old.width, (.35f * 1080 - after.left) / after.width, .0001f)
        assertEquals((.45f * 1350 - old.top) / old.height, (.45f * 1350 - after.top) / after.height, .0001f)
        val extreme = RunSharePhotoTransform.gesture(changed, 1600, 1200, .2f, .8f, 900f, -900f, 999f)
        assertEquals(3f, extreme.photoZoom, 0f)
        val bounded = RunSharePhotoTransform.cover(1600, 1200, 1080, 1350,
            extreme.photoZoom, extreme.photoX, extreme.photoY)
        assertEquals(0f, bounded.left, .001f)
        assertEquals(1350f, bounded.bottom, .001f)
    }

    @Test fun completeAvatarFitsCanvasAndTheAvailableRegionForEveryControlExtreme() {
        for (ratio in RunShareRatio.entries) for (x in listOf(-10f, 0f, .5f, 1f, 10f, Float.NaN))
            for (y in listOf(-10f, 0f, .5f, 1f, 10f, Float.NaN)) for (scale in listOf(.01f, .65f, 1f, 1.5f, 20f, Float.NaN)) {
                val rect = RunShareAvatarPlacement.rect(ratio.width, ratio.height, 100, 200,
                    RunShareDraft(avatarX = x, avatarY = y, avatarScale = scale), 200f, 700f)
                assertTrue(rect.left >= 0 && rect.right <= ratio.width)
                assertTrue(rect.top >= 200f - .001f && rect.bottom <= 700f + .001f)
                assertEquals(.5f, rect.width / rect.height, .0001f)
            }
    }
}

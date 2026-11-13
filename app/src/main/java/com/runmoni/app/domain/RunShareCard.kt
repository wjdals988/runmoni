package com.runmoni.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class RunShareRatio(val height: Int, val label: String) {
    SQUARE(1080, "1:1"), POST(1350, "4:5"), STORY(1920, "9:16");
    val width: Int get() = 1080
    val aspectRatio: Float get() = width.toFloat() / height
}

enum class RunShareTheme(val label: String) { FOREST("초록"), INK("검정"), PAPER("밝게"), CORAL("코랄") }
enum class RunSharePlacement(val label: String) { BOTTOM("하단"), TOP("상단") }

/** Editor coordinates are fractions of the complete output canvas, not GPS or source-photo coordinates. */
data class RunShareDraft(
    val ratio: RunShareRatio = RunShareRatio.SQUARE,
    val theme: RunShareTheme = RunShareTheme.FOREST,
    val placement: RunSharePlacement = RunSharePlacement.BOTTOM,
    val caption: String = "",
    val showDate: Boolean = true,
    val showName: Boolean = false,
    val showAvatar: Boolean = true,
    val avatarX: Float = .76f,
    val avatarY: Float = .50f,
    val avatarScale: Float = 1f,
    val photoZoom: Float = 1f,
    val photoX: Float = 0f,
    val photoY: Float = 0f,
) {
    /** Call at the rendering/export boundary; editable text should retain its trailing spaces while typing. */
    fun normalized() = copy(
        caption = RunShareText.clean(caption, MAX_CAPTION_CODE_POINTS),
        avatarX = avatarX.finiteOr(.76f).coerceIn(0f, 1f),
        avatarY = avatarY.finiteOr(.50f).coerceIn(0f, 1f),
        avatarScale = avatarScale.finiteOr(1f).coerceIn(MIN_AVATAR_SCALE, MAX_AVATAR_SCALE),
        photoZoom = photoZoom.finiteOr(1f).coerceIn(MIN_PHOTO_ZOOM, MAX_PHOTO_ZOOM),
        // The geometry boundary clamps offsets against the actual photo overflow.
        // A fixed [-1,1] range would prevent reaching the edge of a panoramic photo.
        photoX = photoX.finiteOr(0f), photoY = photoY.finiteOr(0f),
    )

    companion object {
        const val MAX_CAPTION_CODE_POINTS = 60
        const val MIN_PHOTO_ZOOM = 1f
        const val MAX_PHOTO_ZOOM = 3f
        const val MIN_AVATAR_SCALE = .65f
        const val MAX_AVATAR_SCALE = 1.5f
    }
}

object RunShareText {
    fun takeCodePoints(value: String, maximum: Int): String {
        require(maximum >= 0)
        return if (value.codePointCount(0, value.length) <= maximum) value
        else value.substring(0, value.offsetByCodePoints(0, maximum))
    }

    fun clean(value: String, maximum: Int): String {
        val text = buildString {
            var index = 0
            while (index < value.length) {
                val cp = value.codePointAt(index)
                when {
                    Character.isWhitespace(cp) -> append(' ')
                    Character.isISOControl(cp) || cp in 0xd800..0xdfff || cp in 0x202a..0x202e || cp in 0x2066..0x2069 -> Unit
                    // Keep emoji joiners/variation selectors; never split a UTF-16 surrogate pair.
                    else -> appendCodePoint(cp)
                }
                index += Character.charCount(cp)
            }
        }.trim().replace(Regex(" +"), " ")
        return takeCodePoints(text, maximum)
    }
}

/** Deliberately contains no record ID, route, UID, reward balance, source URI or precise start time. */
data class RunShareContent private constructor(
    val date: String,
    val distance: String,
    val duration: String,
    val pace: String,
    val interrupted: Boolean,
) {
    companion object {
        /** Invalid/in-progress records return null. Older completed records may have elapsedDurationMillis=0. */
        fun from(record: RunRecord, zoneId: ZoneId = ZoneId.systemDefault()): RunShareContent? {
            if (record.startedAtMillis <= 0 || record.finishedAtMillis <= 0 || !record.distanceMeters.isFinite() ||
                record.distanceMeters < 0 || record.activeDurationMillis < 0) return null
            val distance = String.format(Locale.ROOT, "%.2f", record.distanceMeters / 1000)
            val duration = durationText(record.activeDurationMillis)
            val pace = if (record.distanceMeters < 10 || record.activeDurationMillis <= 0) "—" else {
                val seconds = (record.activeDurationMillis / record.distanceMeters).toLong()
                if (seconds <= 0) "—" else String.format(Locale.ROOT, "%d′%02d″", seconds / 60, seconds % 60)
            }
            // Corrupt/astronomical values cannot become clipped or fabricated statistics.
            if (distance.length > 18 || duration.length > 16 || pace.length > 16) return null
            val date = Instant.ofEpochMilli(record.startedAtMillis).atZone(zoneId)
                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREAN))
            return RunShareContent(date, distance, duration, pace, record.interrupted)
        }

        private fun durationText(millis: Long): String {
            val seconds = millis / 1000
            return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
        }
    }
}

data class RunSharePhotoRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2
}

object RunSharePhotoTransform {
    fun cover(photoWidth: Int, photoHeight: Int, canvasWidth: Int, canvasHeight: Int,
              zoom: Float = 1f, x: Float = 0f, y: Float = 0f): RunSharePhotoRect {
        require(photoWidth > 0 && photoHeight > 0 && canvasWidth > 0 && canvasHeight > 0)
        val scale = max(canvasWidth.toDouble() / photoWidth, canvasHeight.toDouble() / photoHeight) *
            zoom.finiteOr(1f).coerceIn(RunShareDraft.MIN_PHOTO_ZOOM, RunShareDraft.MAX_PHOTO_ZOOM)
        val width = (photoWidth * scale).toFloat()
        val height = (photoHeight * scale).toFloat()
        val extraX = max(0f, width - canvasWidth) / 2
        val extraY = max(0f, height - canvasHeight) / 2
        val dx = (x.finiteOr(0f) * canvasWidth).coerceIn(-extraX, extraX)
        val dy = (y.finiteOr(0f) * canvasHeight).coerceIn(-extraY, extraY)
        val left = (canvasWidth - width) / 2 + dx
        val top = (canvasHeight - height) / 2 + dy
        return RunSharePhotoRect(left, top, left + width, top + height)
    }

    /** Centroid/pan are normalized to the visible preview. A bounded pinch keeps the touched photo point stable. */
    fun gesture(draft: RunShareDraft, photoWidth: Int, photoHeight: Int,
                centroidX: Float, centroidY: Float, panX: Float, panY: Float,
                zoomChange: Float): RunShareDraft {
        val current = draft.normalized()
        val width = current.ratio.width
        val height = current.ratio.height
        val old = cover(photoWidth, photoHeight, width, height, current.photoZoom, current.photoX, current.photoY)
        val zoom = (current.photoZoom * zoomChange.finiteOr(1f).coerceAtLeast(.01f))
            .coerceIn(RunShareDraft.MIN_PHOTO_ZOOM, RunShareDraft.MAX_PHOTO_ZOOM)
        val changed = cover(photoWidth, photoHeight, width, height, zoom)
        val factor = zoom / current.photoZoom
        val touchX = centroidX.finiteOr(.5f).coerceIn(0f, 1f) * width
        val touchY = centroidY.finiteOr(.5f).coerceIn(0f, 1f) * height
        val left = touchX + (old.left - touchX) * factor + panX.finiteOr(0f) * width
        val top = touchY + (old.top - touchY) * factor + panY.finiteOr(0f) * height
        val dx = (left - (width - changed.width) / 2).coerceIn(-(changed.width - width) / 2, (changed.width - width) / 2)
        val dy = (top - (height - changed.height) / 2).coerceIn(-(changed.height - height) / 2, (changed.height - height) / 2)
        return current.copy(photoZoom = zoom, photoX = dx / width, photoY = dy / height)
    }
}

object RunShareAvatarPlacement {
    /** The supplied source size is its genuine nontransparent pixel bounds. No limb reconstruction/cell stretching. */
    fun rect(canvasWidth: Int, canvasHeight: Int, sourceWidth: Int, sourceHeight: Int,
             draft: RunShareDraft, topLimit: Float = 0f, bottomLimit: Float = canvasHeight.toFloat()): RunSharePhotoRect {
        require(canvasWidth > 0 && canvasHeight > 0 && sourceWidth > 0 && sourceHeight > 0)
        val config = draft.normalized()
        val padding = canvasWidth * .045f
        val top = topLimit.finiteOr(0f).coerceIn(0f, canvasHeight.toFloat())
        val bottom = bottomLimit.finiteOr(canvasHeight.toFloat()).coerceIn(top, canvasHeight.toFloat())
        val availableHeight = max(1f, bottom - top - 2 * padding)
        val availableWidth = max(1f, canvasWidth - 2 * padding)
        val factor = min(availableWidth / sourceWidth,
            min(availableHeight / sourceHeight, canvasWidth * .31f * config.avatarScale / sourceHeight))
        val width = sourceWidth * factor
        val height = sourceHeight * factor
        // A very small caller-provided region still stays inside the complete canvas.
        val minX = min(canvasWidth - width / 2, padding + width / 2)
        val maxX = max(minX, canvasWidth - padding - width / 2)
        val centerX = (config.avatarX * canvasWidth).coerceIn(minX, maxX)
        val minY = min(canvasHeight - height / 2, top + min(padding, max(0f, bottom - top - height) / 2) + height / 2)
        val maxY = max(minY, bottom - min(padding, max(0f, bottom - top - height) / 2) - height / 2)
        val centerY = (config.avatarY * canvasHeight).coerceIn(minY, maxY)
        return RunSharePhotoRect(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2)
    }
}

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

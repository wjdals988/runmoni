package com.runmoni.app.sharing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.runmoni.app.domain.RunShareAvatarPlacement
import com.runmoni.app.domain.RunShareContent
import com.runmoni.app.domain.RunShareDraft
import com.runmoni.app.domain.RunSharePhotoRect
import com.runmoni.app.domain.RunSharePhotoTransform
import com.runmoni.app.domain.RunShareRatio
import com.runmoni.app.domain.RunSharePlacement
import com.runmoni.app.domain.RunShareText
import com.runmoni.app.domain.RunShareTheme
import kotlin.math.min

/** The same opaque bitmap is used by the editor and PNG export. Inputs are borrowed and never recycled or changed. */
object RunShareCardRenderer {
    private data class Palette(val background: Int, val foreground: Int, val secondary: Int)
    private data class BoundsKey(val bitmap: Bitmap, val generation: Int)
    private val boundsCache = object : LinkedHashMap<BoundsKey, Rect?>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BoundsKey, Rect?>?) = size > 8
    }
    private data class LayoutKey(val width: Int, val height: Int, val theme: RunShareTheme,
        val placement: RunSharePlacement, val caption: String, val showDate: Boolean,
        val hasName: Boolean, val interrupted: Boolean)
    private data class CardLayout(val headerBottom: Float, val caption: StaticLayout?,
        val captionSpace: Float, val panelTop: Float, val panelBottom: Float)
    private val layoutCache = object : LinkedHashMap<LayoutKey, CardLayout>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutKey, CardLayout>?) = size > 8
    }

    /** Start/finish a drag from its real visible center, not a hidden out-of-bounds value.
     * Shared bounded caches are warmed by render; ordinary gestures do not rescan borrowed avatar pixels.
     * Keep the caller's raw caption and every field other than avatarX/Y unchanged.
     */
    fun constrainAvatar(content: RunShareContent, draft: RunShareDraft, avatar: Bitmap?, runnerName: String = ""): RunShareDraft {
        val config = draft.normalized()
        if (!config.showAvatar || avatar == null || avatar.isRecycled || avatar.width.toLong() * avatar.height > 4_194_304L)
            return draft.copy(avatarX = config.avatarX, avatarY = config.avatarY)
        val source = opaqueBounds(avatar) ?: return draft.copy(avatarX = config.avatarX, avatarY = config.avatarY)
        val name = if (config.showName) RunShareText.clean(runnerName, 32) else ""
        val layout = layout(content, config, name.isNotBlank(), palette(config.theme))
        val destination = avatarDestination(config, source, layout)
        return draft.copy(avatarX = destination.centerX / config.ratio.width,
            avatarY = destination.centerY / config.ratio.height)
    }

    fun render(content: RunShareContent, draft: RunShareDraft, photo: Bitmap?, avatar: Bitmap?, runnerName: String = ""): Bitmap {
        val config = draft.normalized()
        require(photo == null || !photo.isRecycled) { "사진을 다시 선택해 주세요." }
        require(!config.showAvatar || avatar != null && !avatar.isRecycled) { "미니미를 다시 불러오거나 표시를 꺼 주세요." }
        val width = config.ratio.width
        val height = config.ratio.height
        val palette = palette(config.theme)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(result)
            canvas.drawColor(palette.background)
            if (photo != null) {
                val crop = RunSharePhotoTransform.cover(photo.width, photo.height, width, height,
                    config.photoZoom, config.photoX, config.photoY)
                canvas.drawBitmap(photo, null, crop.androidRect(), Paint(Paint.FILTER_BITMAP_FLAG))
            }

            val square = config.ratio == RunShareRatio.SQUARE
            val padding = if (square) 52f else 72f
            val textWidth = width - padding * 2
            val name = if (config.showName) RunShareText.clean(runnerName, 32) else ""
            val layout = layout(content, config, name.isNotBlank(), palette)
            val headerBottom = layout.headerBottom
            val caption = layout.caption
            val captionSpace = layout.captionSpace
            val panelTop = layout.panelTop
            val panelBottom = layout.panelBottom

            // Keep the complete genuine character outside the statistics ribbon.
            // If a long caption changes available space, clamp/fit the sticker rather than cover essential numbers.
            if (config.showAvatar && avatar != null) {
                val source = opaqueBounds(avatar) ?: throw IllegalArgumentException("미니미를 다시 불러와 주세요.")
                val destination = avatarDestination(config, source, layout)
                // Photos use interpolation. Approved pixel sprites deliberately do not.
                canvas.drawBitmap(avatar, source, destination.androidRect(), Paint().apply {
                    isFilterBitmap = false; isAntiAlias = false; isDither = false
                })
            }

            // Text is always above image/sticker layers, with a strong opaque-enough theme surface.
            val panelColor = if (photo == null) palette.background else withAlpha(palette.background,
                if (config.theme == RunShareTheme.PAPER) 248 else 236)
            canvas.drawRect(0f, panelTop, width.toFloat(), panelBottom, Paint().apply { color = panelColor })
            var metricsTop = panelTop + if (square) 32f else 40f
            if (caption != null) {
                canvas.save()
                canvas.translate(padding, metricsTop)
                // A cancelled preview may finish while its replacement starts.
                // StaticLayout's internal work paint is not shared concurrently between those draws.
                synchronized(caption) { caption.draw(canvas) }
                canvas.restore()
                metricsTop += captionSpace
            }

            val labelPaint = textPaint(if (square) 32f else 28f, palette.secondary, bold = false)
            canvas.drawText("거리", padding, metricsTop + if (square) 28f else 26f, labelPaint)
            val distancePaint = textPaint(if (square) 128f else 152f, palette.foreground, bold = true)
            fit(distancePaint, content.distance, textWidth - 100f)
            canvas.drawText(content.distance, padding, metricsTop + if (square) 152f else 172f, distancePaint)
            canvas.drawText("km", padding + distancePaint.measureText(content.distance) + 18f,
                metricsTop + if (square) 152f else 172f, textPaint(44f, palette.foreground, bold = false))

            val gap = 56f
            val columnWidth = (textWidth - gap) / 2
            val rightColumn = padding + columnWidth + gap
            canvas.drawText("러닝 시간", padding, metricsTop + if (square) 222f else 236f, labelPaint)
            canvas.drawText("평균 페이스 /km", rightColumn, metricsTop + if (square) 222f else 236f, labelPaint)
            val timePaint = textPaint(if (square) 64f else 76f, palette.foreground, bold = true)
            fit(timePaint, content.duration, columnWidth)
            canvas.drawText(content.duration, padding, metricsTop + if (square) 294f else 318f, timePaint)
            val pacePaint = textPaint(if (square) 64f else 76f, palette.foreground, bold = true)
            fit(pacePaint, content.pace, columnWidth)
            canvas.drawText(content.pace, rightColumn, metricsTop + if (square) 294f else 318f, pacePaint)
            if (content.interrupted) canvas.drawText("중단 구간 포함", padding, metricsTop + if (square) 340f else 364f, labelPaint)

            // Brand/date/name remain legible independently from the chosen photo or stat placement.
            if (photo != null) canvas.drawRect(0f, 0f, width.toFloat(), headerBottom + 24f, Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, headerBottom + 24f,
                    intArrayOf(0xbc000000.toInt(), 0xaa000000.toInt(), Color.TRANSPARENT),
                    floatArrayOf(0f, headerBottom / (headerBottom + 24f), 1f), Shader.TileMode.CLAMP)
            })
            val headerColor = if (photo != null) Color.WHITE else palette.foreground
            val headerSecondary = if (photo != null) 0xffedf2ee.toInt() else palette.secondary
            canvas.drawText("runmoni", padding, if (square) 80f else 96f, textPaint(44f, headerColor, bold = true))
            var nextBaseline = if (square) 124f else 148f
            if (config.showDate) {
                canvas.drawText(content.date, padding, nextBaseline, textPaint(28f, headerSecondary, bold = false))
                nextBaseline += if (square) 40f else 44f
            }
            if (name.isNotBlank()) {
                val namePaint = textPaint(32f, headerColor, bold = false)
                fit(namePaint, name, textWidth)
                canvas.drawText(name, padding, nextBaseline, namePaint)
            }
            return result
        } catch (failure: Throwable) {
            // Only this newly allocated output is owned here; shared avatar/photo inputs stay alive.
            result.recycle()
            throw failure
        }
    }

    private fun palette(theme: RunShareTheme) = when (theme) {
        RunShareTheme.FOREST -> Palette(0xff205641.toInt(), Color.WHITE, 0xffd6e5da.toInt())
        RunShareTheme.INK -> Palette(0xff18201c.toInt(), Color.WHITE, 0xffd6e5da.toInt())
        RunShareTheme.PAPER -> Palette(0xfff5f3eb.toInt(), 0xff192d25.toInt(), 0xff556158.toInt())
    }

    private fun layout(content: RunShareContent, config: RunShareDraft, hasName: Boolean, palette: Palette): CardLayout {
        val key = LayoutKey(config.ratio.width, config.ratio.height, config.theme, config.placement,
            config.caption, config.showDate, hasName, content.interrupted)
        return synchronized(layoutCache) {
            layoutCache[key] ?: run {
                val square = config.ratio == RunShareRatio.SQUARE
                val headerBottom = if (square) when {
                    hasName && config.showDate -> 184f
                    hasName || config.showDate -> 144f
                    else -> 104f
                } else when { hasName -> if (config.showDate) 208f else 164f
                    config.showDate -> 164f
                    else -> 120f }
                val textWidth = config.ratio.width - if (square) 104 else 144
                var caption = caption(config.caption, if (square) 36f else 44f, textWidth, palette.foreground)
                // Two square lines keep the photo and whole avatar useful even with all metadata shown.
                // Keep all 60 code points; only a long square caption uses the smaller fixed export type.
                if (square && (caption?.lineCount ?: 0) > 2)
                    caption = caption(config.caption, 32f, textWidth, palette.foreground)
                val captionSpace = caption?.let { it.height + if (square) 18f else 28f } ?: 0f
                val panelHeight = if (square) 32f + captionSpace + if (content.interrupted) 364f else 328f
                    else 40f + captionSpace + if (content.interrupted) 410f else 370f
                val panelTop = if (config.placement == RunSharePlacement.BOTTOM) config.ratio.height - panelHeight else headerBottom + 24f
                CardLayout(headerBottom, caption, captionSpace, panelTop, panelTop + panelHeight).also { layoutCache[key] = it }
            }
        }
    }

    private fun caption(text: String, size: Float, width: Int, color: Int): StaticLayout? =
        if (text.isEmpty()) null else StaticLayout.Builder.obtain(text, 0, text.length,
            textPaint(size, color, bold = false), width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .setUseLineSpacingFromFallbacks(true)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setLineSpacing(4f, 1f).build()

    private fun avatarDestination(config: RunShareDraft, source: Rect, layout: CardLayout): RunSharePhotoRect {
        val topLimit = if (config.placement == RunSharePlacement.TOP) layout.panelBottom else layout.headerBottom
        val bottomLimit = if (config.placement == RunSharePlacement.BOTTOM) layout.panelTop else config.ratio.height.toFloat()
        return RunShareAvatarPlacement.rect(config.ratio.width, config.ratio.height,
            source.width(), source.height(), config, topLimit, bottomLimit)
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        fontFeatureSettings = "tnum"
    }

    private fun fit(paint: Paint, text: String, available: Float) {
        val measured = paint.measureText(text)
        if (measured > available) paint.textSize *= min(1f, available / measured)
    }

    private fun withAlpha(color: Int, alpha: Int) = color and 0x00ffffff or (alpha shl 24)
    private fun RunSharePhotoRect.androidRect() = RectF(left, top, right, bottom)

    /** Trims transparent cell margins only; every original nontransparent pixel and its aspect ratio are retained. */
    private fun opaqueBounds(bitmap: Bitmap): Rect? {
        require(bitmap.width.toLong() * bitmap.height <= 4_194_304L) { "미니미 이미지를 다시 불러와 주세요." }
        val key = BoundsKey(bitmap, bitmap.generationId)
        return synchronized(boundsCache) {
            if (boundsCache.containsKey(key)) return@synchronized boundsCache[key]
            // A mutable caller's generation change must not use the previous silhouette.
            boundsCache.keys.removeAll { it.bitmap === bitmap && it.generation != key.generation }
            scanOpaqueBounds(bitmap).also { boundsCache[key] = it }
        }
    }

    private fun scanOpaqueBounds(bitmap: Bitmap): Rect? {
        val row = IntArray(bitmap.width)
        var minX = bitmap.width
        var minY = bitmap.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) if (Color.alpha(row[x]) > 0) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                maxY = y
            }
        }
        return if (maxX < 0) null else Rect(minX, minY, maxX + 1, maxY + 1)
    }
}

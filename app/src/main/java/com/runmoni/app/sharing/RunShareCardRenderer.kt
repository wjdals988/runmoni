package com.runmoni.app.sharing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.runmoni.app.domain.*
import kotlin.math.min

/** Preview and PNG export borrow the very same opaque bitmap. Source pixels are never changed/recycled. */
object RunShareCardRenderer {
    private data class BoundsKey(val bitmap: Bitmap, val generation: Int)
    private val boundsCache = object : LinkedHashMap<BoundsKey, Rect?>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BoundsKey, Rect?>?) = size > 8
    }
    private data class Line(val text: String, val x: Float, val baseline: Float, val width: Float,
        val size: Float, val bold: Boolean = false, val centered: Boolean = false, val distance: Boolean = false)
    private data class LayoutKey(val content: RunShareContent, val style: RunShareStyle,
        val tone: RunShareTextTone, val caption: String, val date: Boolean, val name: String)
    private data class CardLayout(val width: Float, val height: Float, val lines: List<Line>,
        val caption: StaticLayout?, val captionY: Float)
    private val layoutCache = object : LinkedHashMap<LayoutKey, CardLayout>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutKey, CardLayout>?) = size > 8
    }

    fun constrainAvatar(content: RunShareContent, draft: RunShareDraft, avatar: Bitmap?, runnerName: String = ""): RunShareDraft {
        val config = draft.normalized()
        if (!config.showAvatar || avatar == null || avatar.isRecycled || avatar.width.toLong() * avatar.height > 4_194_304L)
            return draft.copy(avatarX = config.avatarX, avatarY = config.avatarY)
        val source = opaqueBounds(avatar) ?: return draft.copy(avatarX = config.avatarX, avatarY = config.avatarY)
        val rect = avatarDestination(config, source)
        return draft.copy(avatarX = rect.centerX / config.ratio.width, avatarY = rect.centerY / config.ratio.height)
    }

    fun constrainRecord(content: RunShareContent, draft: RunShareDraft, runnerName: String = ""): RunShareDraft {
        val rect = recordBounds(content, draft, runnerName)
        return draft.copy(recordX = rect.centerX / draft.ratio.width, recordY = rect.centerY / draft.ratio.height)
    }

    fun recordBounds(content: RunShareContent, draft: RunShareDraft, runnerName: String = ""): RunSharePhotoRect {
        val config = draft.normalized()
        val layout = layout(content, config, if (config.showName) RunShareText.clean(runnerName, 32) else "")
        return RunShareRecordPlacement.rect(config.ratio.width, config.ratio.height, layout.width, layout.height, config)
    }

    fun render(content: RunShareContent, draft: RunShareDraft, photo: Bitmap?, avatar: Bitmap?, runnerName: String = ""): Bitmap {
        val config = draft.normalized()
        require(photo == null || !photo.isRecycled) { "사진을 다시 선택해 주세요." }
        require(!config.showAvatar || avatar != null && !avatar.isRecycled) { "미니미를 다시 불러오거나 표시를 꺼 주세요." }
        val result = Bitmap.createBitmap(config.ratio.width, config.ratio.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(result)
            // Neutral fallback only. Legacy theme values remain readable saved
            // state; they never tint the photo or create header/footer ribbons.
            canvas.drawColor(if (config.textTone == RunShareTextTone.LIGHT) 0xff20272b.toInt() else 0xfff4f6f7.toInt())
            if (photo != null) {
                val crop = RunSharePhotoTransform.cover(photo.width, photo.height, result.width, result.height,
                    config.photoZoom, config.photoX, config.photoY)
                canvas.drawBitmap(photo, null, crop.androidRect(), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            if (config.showAvatar && avatar != null) {
                val source = opaqueBounds(avatar) ?: throw IllegalArgumentException("미니미를 다시 불러와 주세요.")
                canvas.drawBitmap(avatar, source, avatarDestination(config, source).androidRect(), Paint().apply {
                    isFilterBitmap = false; isAntiAlias = false; isDither = false
                })
            }
            val name = if (config.showName) RunShareText.clean(runnerName, 32) else ""
            val layout = layout(content, config, name)
            val rect = RunShareRecordPlacement.rect(result.width, result.height, layout.width, layout.height, config)
            canvas.save()
            canvas.translate(rect.left, rect.top)
            for (line in layout.lines) drawLine(canvas, line, config.textTone)
            layout.caption?.let { caption ->
                canvas.save(); canvas.translate(0f, layout.captionY)
                // The cache is shared by cancelled/concurrent preview renders.
                synchronized(caption) {
                    val paint = caption.paint
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.color = outline(config.textTone)
                    paint.clearShadowLayer(); caption.draw(canvas)
                    paint.style = Paint.Style.FILL; paint.strokeWidth = 0f; paint.color = ink(config.textTone)
                    paint.setShadowLayer(2f, 0f, 1f, outline(config.textTone)); caption.draw(canvas)
                }
                canvas.restore()
            }
            canvas.restore()
            return result
        } catch (failure: Throwable) { result.recycle(); throw failure }
    }

    private fun layout(content: RunShareContent, config: RunShareDraft, name: String): CardLayout {
        val key = LayoutKey(content, config.style, config.textTone, config.caption, config.showDate, name)
        return synchronized(layoutCache) {
            layoutCache[key] ?: run {
                val centered = config.style == RunShareStyle.STAMP
                val width = when (config.style) { RunShareStyle.CLEAN -> 800f; RunShareStyle.RECORD -> 880f; RunShareStyle.STAMP -> 620f }
                val lines = mutableListOf<Line>()
                var y = 0f
                fun line(text: String, size: Float, bold: Boolean = false, distance: Boolean = false) {
                    y += size
                    lines += Line(text, 0f, y, width, size, bold, centered, distance)
                    y += if (distance) 14f else 10f
                }
                line("runmoni", if (centered) 26f else 28f, true)
                if (config.showDate) line(content.date, 26f)
                if (name.isNotBlank()) line(name, 28f)
                val captionY = y + 6f
                val caption = if (config.caption.isBlank()) null else StaticLayout.Builder.obtain(config.caption, 0,
                    config.caption.length, textPaint(32f, ink(config.textTone), false), width.toInt())
                    .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false).setUseLineSpacingFromFallbacks(true)
                    .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
                    .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).setLineSpacing(4f, 1f).build()
                if (caption != null) y = captionY + caption.height + 12f
                y += 8f
                line(content.distance, when (config.style) { RunShareStyle.CLEAN -> 124f; RunShareStyle.RECORD -> 180f; RunShareStyle.STAMP -> 104f }, true, true)
                if (config.style == RunShareStyle.RECORD) {
                    val column = (width - 40f) / 2
                    y += 26f
                    lines += Line("러닝 시간", 0f, y, column, 26f)
                    lines += Line("평균 페이스 /km", column + 40f, y, column, 26f)
                    y += 66f
                    lines += Line(content.duration, 0f, y, column, 58f, true)
                    lines += Line(content.pace, column + 40f, y, column, 58f, true)
                    y += 14f
                } else line("${content.duration}  ·  ${content.pace} /km", if (centered) 32f else 38f, true)
                if (content.interrupted) line("중단 구간 포함", 26f)
                CardLayout(width, y + 12f, lines, caption, captionY).also { layoutCache[key] = it }
            }
        }
    }

    private fun drawLine(canvas: Canvas, line: Line, tone: RunShareTextTone) {
        val paint = textPaint(line.size, ink(tone), line.bold)
        val unit = if (line.distance) textPaint(line.size * .32f, ink(tone), false) else null
        val unitWidth = unit?.measureText("km") ?: 0f
        val gap = if (line.distance) 14f else 0f
        fit(paint, line.text, line.width - unitWidth - gap)
        val width = paint.measureText(line.text) + unitWidth + gap
        val x = line.x + if (line.centered) (line.width - width) / 2 else 0f
        outlined(canvas, line.text, x, line.baseline, paint, tone)
        if (unit != null) outlined(canvas, "km", x + paint.measureText(line.text) + gap, line.baseline, unit, tone)
    }

    private fun outlined(canvas: Canvas, text: String, x: Float, baseline: Float, paint: TextPaint, tone: RunShareTextTone) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.color = outline(tone)
        canvas.drawText(text, x, baseline, paint)
        paint.style = Paint.Style.FILL; paint.strokeWidth = 0f; paint.color = ink(tone)
        paint.setShadowLayer(2f, 0f, 1f, outline(tone)); canvas.drawText(text, x, baseline, paint)
    }

    private fun ink(tone: RunShareTextTone) = if (tone == RunShareTextTone.LIGHT) Color.WHITE else 0xff17232a.toInt()
    private fun outline(tone: RunShareTextTone) = if (tone == RunShareTextTone.LIGHT) 0xe0000000.toInt() else 0xf0ffffff.toInt()
    private fun textPaint(size: Float, color: Int, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; textSize = size
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        fontFeatureSettings = "tnum"
    }
    private fun fit(paint: Paint, text: String, available: Float) {
        val measured = paint.measureText(text)
        if (measured > available) paint.textSize *= min(1f, available / measured)
    }
    private fun avatarDestination(config: RunShareDraft, source: Rect) = RunShareAvatarPlacement.rect(
        config.ratio.width, config.ratio.height, source.width(), source.height(), config)
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

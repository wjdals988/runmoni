package com.runmoni.app.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Separate review state: never grants an item, changes the wallet, or syncs a proposal. */
data class GarmentReviewColors(val shorts: Int = 0, val top: Int = 0, val bottom: Int = 0)

class AvatarReferenceReview(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("avatar-reference-review-v1", Context.MODE_PRIVATE)
    private fun decode(name: String): Bitmap = app.assets.open("avatars/reference-review/$name").use {
        val options = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        requireNotNull(BitmapFactory.decodeStream(it, null, options)) { "Missing reference artwork: $name" }
    }
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }
    private val source by lazy { decode("swimwear-proposal.png") }
    private val sourcePixels by lazy { pixels(source) }
    private val mask by lazy {
        decode("swimwear-mask.png").let {
            require(it.width == source.width && it.height == source.height) { "Garment mask dimensions differ" }
            pixels(it).also { _ -> it.recycle() }
        }
    }
    private val sheet by lazy { decode("approved-running.png") }
    private fun pixelAsset(name: String) = decode(name).let { bitmap ->
        pixels(bitmap).also { bitmap.recycle() }
    }
    private val motionBase by lazy {
        GarmentPalette.protectIdentity(pixels(sheet), pixelAsset("swimwear-motion-generated.png"), pixelAsset("motion-identity-mask.png"))
    }
    private val motionFabric by lazy { pixelAsset("motion-fabric-mask.png") }

    fun referenceFrames(): List<Bitmap> {
        val data = pixels(sheet)
        return (0..7).map { frame ->
            Bitmap.createBitmap(GarmentPalette.reviewPair(data, frame),
                512, 384, Bitmap.Config.ARGB_8888)
        }
    }

    fun swimwear(colors: GarmentReviewColors): Bitmap = Bitmap.createBitmap(
        GarmentPalette.recolor(sourcePixels, mask, colors.shorts, colors.top, colors.bottom),
        source.width, source.height, Bitmap.Config.ARGB_8888,
    )

    fun motionFrames(colors: GarmentReviewColors): List<Bitmap> {
        val colored = GarmentPalette.recolor(motionBase, motionFabric, colors.shorts, colors.top, colors.bottom)
        return (0..7).map { frame ->
            Bitmap.createBitmap(GarmentPalette.reviewPair(colored, frame), 512, 384, Bitmap.Config.ARGB_8888)
        }
    }

    private fun savedColor(key: String) = GarmentPalette.checkedColor(runCatching { preferences.getInt(key, 0) }.getOrDefault(0))
    fun load() = GarmentReviewColors(savedColor("shorts"), savedColor("top"), savedColor("bottom"))

    fun hasSavedColors() = preferences.contains("shorts") && preferences.contains("top") && preferences.contains("bottom")

    fun save(colors: GarmentReviewColors): Boolean = preferences.edit()
        .putInt("shorts", GarmentPalette.checkedColor(colors.shorts))
        .putInt("top", GarmentPalette.checkedColor(colors.top))
        .putInt("bottom", GarmentPalette.checkedColor(colors.bottom)).commit()
}

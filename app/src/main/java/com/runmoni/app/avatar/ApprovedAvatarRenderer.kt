package com.runmoni.app.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.runmoni.app.domain.AvatarCatalogue
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.RunningKitColors
import com.runmoni.app.domain.variantFor
import java.security.MessageDigest

/** Artwork readiness is separate from inventory validity/ownership and never migrates a profile. */
object AvatarArtwork {
    fun itemReady(slot: String, variant: Int) = AvatarCatalogue.valid(slot, variant) && variant == 0
    fun styleReady(style: AvatarStyle) = AvatarCatalogue.slots.all { itemReady(it.key, style.variantFor(it.key)) }
}

/** Approved whole-frame artwork. No v0.4 rig, body rotation or old-art fallback. */
class ApprovedAvatarRenderer(context: Context) {
    companion object {
        const val WIDTH = 256
        const val HEIGHT = 384
        const val FRAMES = 8
        const val MILLIS = 90
        const val ASSET = "avatars/v0.3.0/running-motion.png"
        const val SHA256 = "f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65"
        const val COLOR_CACHE_BYTES = 8 * 1024 * 1024
        const val FEMALE_DISPLAY_PADDING = 12
        fun frameAt(elapsedMillis: Long) = ((elapsedMillis.coerceAtLeast(0) / MILLIS) % FRAMES).toInt()
    }

    private val assets = context.applicationContext.assets
    private val applicationContext = context.applicationContext
    private val source by lazy {
        val bytes = assets.open(ASSET).use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(digest == SHA256) { "Approved avatar source integrity check failed" }
        val options = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)).also {
            require(it.width == WIDTH * FRAMES && it.height == HEIGHT * 2) { "Invalid approved avatar sheet" }
        }
    }
    private val frames = Array(AvatarGender.entries.size) { arrayOfNulls<Bitmap>(FRAMES) }
    private val femaleDisplayBases = arrayOfNulls<Bitmap>(FRAMES)
    private val colored = object : LruCache<String, Bitmap>(COLOR_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** Base outfit only until per-item frame layers exist. Readiness is disclosed in the closet. */
    @Synchronized
    fun render(frame: Int, running: Boolean, gender: AvatarGender = AvatarGender.MALE,
        colors: RunningKitColors = RunningKitColors()): Bitmap {
        val selected = if (running) Math.floorMod(frame, FRAMES) else 0
        val row = gender.spriteRow
        val original = frames[row][selected] ?: Bitmap.createBitmap(source, selected * WIDTH, row * HEIGHT, WIDTH, HEIGHT).also {
            frames[row][selected] = it
        }
        val kit = colors.normalized()
        if (kit.isOriginal) return original
        val mask = try { RunningKitArtwork.frameMask(applicationContext, row, selected) } catch (_: Exception) { return original }
        val key = "${RunningKitArtwork.MASK_VERSION}|$row|$selected|${kit.top}|${kit.bottom}|${kit.shoes}"
        colored.get(key)?.let { return it }
        val pixels = IntArray(WIDTH * HEIGHT)
        original.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        val changed = GarmentPalette.recolorRunningKit(pixels, mask, kit.top, kit.bottom, kit.shoes)
        return Bitmap.createBitmap(changed, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { colored.put(key, it) }
    }

    @Synchronized internal fun cachedColorBytes() = colored.size()

    /** Presentation-only clipping of previous-row shoe bleed, above the actual hair.
     * render() and the approved source remain bit-for-bit intact, including padding.
     */
    @Synchronized
    fun renderForDisplay(frame: Int, running: Boolean, gender: AvatarGender = AvatarGender.MALE,
        colors: RunningKitColors = RunningKitColors()): Bitmap {
        val image = render(frame, running, gender, colors)
        if (gender != AvatarGender.FEMALE) return image
        val selected = if (running) Math.floorMod(frame, FRAMES) else 0
        val kit = colors.normalized()
        val original = image === frames[gender.spriteRow][selected]
        val key = "display|${RunningKitArtwork.MASK_VERSION}|$selected|${kit.top}|${kit.bottom}|${kit.shoes}"
        (if (original) femaleDisplayBases[selected] else colored.get(key))?.let { return it }
        val pixels = IntArray(WIDTH * HEIGHT)
        image.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        java.util.Arrays.fill(pixels, 0, WIDTH * FEMALE_DISPLAY_PADDING, 0)
        return Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also {
            if (original) femaleDisplayBases[selected] = it else colored.put(key, it)
        }
    }
}

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
    fun itemReady(slot: String, variant: Int, gender: AvatarGender = AvatarGender.MALE): Boolean {
        if (!AvatarCatalogue.valid(slot, variant)) return false
        if (variant == 0) return true
        return when (slot) {
            "top" -> variant in 1..3
            "bottom" -> variant == 1 || (gender == AvatarGender.MALE && variant in 2..3)
            "socks" -> variant in 1..2
            "shoes" -> variant in 1..3
            "accessory" -> when (variant) {
                1 -> itemReady("headwear", 3, gender)
                2 -> itemReady("headwear", 1, gender)
                3 -> itemReady("ear", 1, gender)
                else -> false
            }
            else -> AvatarEquipmentLayers.supports(slot, variant, gender)
        }
    }
    fun styleReady(style: AvatarStyle) = AvatarCatalogue.slots.all { itemReady(it.key, style.variantFor(it.key), style.gender) }
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

    /** Compatibility entry point for the existing basic-fabric renderer tests. */
    @Synchronized
    fun render(frame: Int, running: Boolean, gender: AvatarGender = AvatarGender.MALE,
        colors: RunningKitColors = RunningKitColors()): Bitmap =
        render(frame, running, AvatarStyle(gender = gender, runningKitColors = colors))

    /** Whole original frames plus explicit clothes/gear; never the rejected body rig. */
    @Synchronized
    fun render(frame: Int, running: Boolean, style: AvatarStyle): Bitmap {
        val selected = if (running) Math.floorMod(frame, FRAMES) else 0
        val row = style.gender.spriteRow
        val original = frames[row][selected] ?: Bitmap.createBitmap(source, selected * WIDTH, row * HEIGHT, WIDTH, HEIGHT).also {
            frames[row][selected] = it
        }
        val kit = style.runningKitColors.normalized()
        // Unsupported saved IDs remain saved. They never select a substitute rig.
        val covered = AvatarCatalogue.coveredSlots(style.costume)
        fun ready(slot: String) = if (slot in covered) 0 else
            style.variantFor(slot).takeIf { AvatarArtwork.itemReady(slot, it, style.gender) } ?: 0
        val effective = AvatarStyle(gender = style.gender, top = ready("top"), bottom = ready("bottom"),
            shoes = ready("shoes"), headwear = ready("headwear"), ear = ready("ear"),
            eyewear = ready("eyewear"), wrist = ready("wrist"), waist = ready("waist"),
            hydration = ready("hydration"), socks = ready("socks"), costume = style.costume, runningKitColors = kit)
        val noGear = listOf("headwear", "ear", "eyewear", "wrist", "waist", "hydration").all { effective.variantFor(it) == 0 }
        if (kit.isOriginal && effective.top == 0 && effective.bottom == 0 && effective.shoes == 0 && effective.socks == 0 && noGear) return original
        val key = "${WardrobeCoverage.VERSION}|${SockArtwork.VERSION}|$selected|$effective"
        colored.get(key)?.let { return it }
        val mask = RunningKitArtwork.frameMask(applicationContext, row, selected)
        val pixels = IntArray(WIDTH * HEIGHT)
        original.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        val needsCoverage = effective.top != 0 || effective.bottom != 0
        val changed = if (needsCoverage) WardrobeClothing.render(pixels, mask,
            WardrobeCoverage.frame(applicationContext, row, selected), effective.top, effective.bottom,
            effective.shoes, kit.top, kit.bottom, kit.shoes, row, selected)
        else WardrobeClothing.render(pixels, mask, IntArray(pixels.size), 0, 0, effective.shoes,
            kit.top, kit.bottom, kit.shoes, row, selected)
        val socked = if (effective.socks == 0) changed else SockClothing.render(changed,
            SockArtwork.frameMask(applicationContext, row, selected), effective.socks)
        val equipped = AvatarEquipmentLayers.render(socked, pixels, mask, selected, style.gender, effective)
        return Bitmap.createBitmap(equipped, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { colored.put(key, it) }
    }

    @Synchronized internal fun cachedColorBytes() = colored.size()

    /** Presentation-only clipping of previous-row shoe bleed, above the actual hair.
     * render() and the approved source remain bit-for-bit intact, including padding.
     */
    @Synchronized
    fun renderForDisplay(frame: Int, running: Boolean, gender: AvatarGender = AvatarGender.MALE,
        colors: RunningKitColors = RunningKitColors()): Bitmap =
        renderForDisplay(frame, running, AvatarStyle(gender = gender, runningKitColors = colors))

    @Synchronized
    fun renderForDisplay(frame: Int, running: Boolean, style: AvatarStyle): Bitmap {
        val image = render(frame, running, style)
        if (style.gender != AvatarGender.FEMALE) return image
        val selected = if (running) Math.floorMod(frame, FRAMES) else 0
        val original = image === frames[style.gender.spriteRow][selected]
        val key = "display|${WardrobeCoverage.VERSION}|${SockArtwork.VERSION}|$selected|$style"
        (if (original) femaleDisplayBases[selected] else colored.get(key))?.let { return it }
        val pixels = IntArray(WIDTH * HEIGHT)
        image.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        java.util.Arrays.fill(pixels, 0, WIDTH * FEMALE_DISPLAY_PADDING, 0)
        return Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also {
            if (original) femaleDisplayBases[selected] = it else colored.put(key, it)
        }
    }
}

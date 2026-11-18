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
        if (variant == 0 || ExpandedAtelierLayers.supports(slot, variant)) return true
        return when (slot) {
            "top" -> variant in 1..3 || variant == AvatarAtelierClothing.MESH_TOP
            "bottom" -> variant == 1 || (gender == AvatarGender.MALE && variant in 2..3)
            "socks" -> variant in 1..2 || variant == AvatarAtelierClothing.RIB_SOCKS
            "shoes" -> variant in 1..3 || variant == AvatarAtelierClothing.PANEL_SHOES
            "costume" -> AvatarCostumeLayers.supports(variant)
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
        val requested = AvatarStyle(gender = style.gender, hair = ready("hair"), face = ready("face"),
            top = ready("top"), bottom = ready("bottom"), shoes = ready("shoes"), headwear = ready("headwear"),
            ear = ready("ear"), eyewear = ready("eyewear"), wrist = ready("wrist"), waist = ready("waist"),
            hydration = ready("hydration"), socks = ready("socks"), costume = style.costume, runningKitColors = kit)
        val expanded = ExpandedAtelierLayers.selected(requested).isNotEmpty()
        val effective = ExpandedAtelierLayers.foundation(requested)
        val drawKit = effective.runningKitColors
        val frameCostume = AvatarCostumeLayers.supports(style.costume)
        val noGear = listOf("headwear", "ear", "eyewear", "wrist", "waist", "hydration").all { effective.variantFor(it) == 0 }
        if (!expanded && !frameCostume && kit.isOriginal && effective.top == 0 && effective.bottom == 0 && effective.shoes == 0 && effective.socks == 0 && noGear) return original
        val key = "${WardrobeCoverage.VERSION}|${WardrobeClothing.VERSION}|${SockArtwork.VERSION}|${SockClothing.VERSION}|${AvatarEquipmentLayers.VERSION}|${AvatarCostumeLayers.VERSION}|${AvatarHeadwearLayers.VERSION}|${AvatarAtelierClothing.VERSION}|${AsepriteMaterialLayers.VERSION}|${ExpandedAtelierLayers.VERSION}|$selected|$requested|$effective"
        colored.get(key)?.let { return it }
        val mask = RunningKitArtwork.frameMask(applicationContext, row, selected)
        val pixels = IntArray(WIDTH * HEIGHT)
        original.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        val needsCoverage = effective.top != 0 || effective.bottom != 0
        val coverage = if (needsCoverage) WardrobeCoverage.frame(applicationContext, row, selected) else null
        val baseTop = AvatarAtelierClothing.baseTop(effective.top)
        val baseShoes = AvatarAtelierClothing.baseShoes(effective.shoes)
        val topColor = AvatarAtelierClothing.topColor(effective.top, drawKit.top)
        val changed = if (frameCostume) {
            val costumeCoverage = WardrobeCoverage.frame(applicationContext, row, selected)
            val costumeHands = WardrobeCoverage.handProtection(applicationContext, row, selected)
            val costumePixels = AvatarCostumeLayers.render(pixels, mask, costumeCoverage,
                costumeHands, selected, style.gender, style.costume)
            // Authored material precedes removable equipment, so fur/stitches
            // cannot be painted over a vest, belt or foreground accessory.
            AsepriteDetailArtwork.costume(applicationContext, costumePixels, pixels, mask,
                costumeCoverage, costumeHands, selected, style.gender, style.costume)
        }
        else if (needsCoverage) WardrobeClothing.render(pixels, mask,
            coverage, baseTop, effective.bottom,
            baseShoes, topColor, drawKit.bottom, drawKit.shoes, row, selected)
        else WardrobeClothing.render(pixels, mask, IntArray(pixels.size), 0, 0, baseShoes,
            drawKit.top, drawKit.bottom, drawKit.shoes, row, selected)
        val sockMask = if (effective.socks == 0) null else SockArtwork.frameMask(applicationContext, row, selected)
        val socked = if (effective.socks == 0) changed else SockClothing.render(changed,
            sockMask, AvatarAtelierClothing.baseSocks(effective.socks))
        val meshHands = if (effective.top == AvatarAtelierClothing.MESH_TOP)
            WardrobeCoverage.handProtection(applicationContext, row, selected) else null
        val atelier = if (effective.top == AvatarAtelierClothing.MESH_TOP ||
            effective.socks == AvatarAtelierClothing.RIB_SOCKS || effective.shoes == AvatarAtelierClothing.PANEL_SHOES)
            AvatarAtelierClothing.render(socked, pixels, mask, coverage, sockMask, meshHands, selected, effective)
        else socked
        // Torso equipment reuses any hand plate already read by the mesh shirt.
        // A head piece/watch/ear device alone must not cold-load unrelated labels.
        val gearHands = if (effective.waist != 0 || effective.hydration != 0)
            meshHands ?: WardrobeCoverage.handProtection(applicationContext, row, selected) else null
        val expandedCoverage = if (expanded) coverage ?: WardrobeCoverage.frame(applicationContext, row, selected) else null
        val expandedSocks = if (expanded) sockMask ?: SockArtwork.frameMask(applicationContext, row, selected) else null
        val expandedHands = if (expanded) gearHands ?: WardrobeCoverage.handProtection(applicationContext, row, selected) else null
        // Independent hand plates override sleeve/torso labels in new constructions.
        val expandedBase = if (expanded) atelier.copyOf().also { out ->
            for (i in out.indices) if (expandedHands!![i] != 0) out[i] = pixels[i]
        } else atelier
        val constructed = if (expanded) ExpandedAtelierArtwork.render(applicationContext, expandedBase, pixels, mask,
            expandedCoverage!!, expandedSocks!!, expandedHands!!, selected, requested, effective, equipment = false) else expandedBase
        val completed = if (expanded) {
            // A newly authored vest must remain behind an older belt/watch,
            // and a new ear device behind older glasses. Interleave generations.
            var out = constructed
            for (slot in listOf("headwear", "hydration", "waist", "wrist", "ear", "eyewear")) {
                val component = ExpandedAtelierLayers.gearOnly(effective, slot)
                if (component.variantFor(slot) != 0)
                    out = AvatarEquipmentLayers.render(out, pixels, mask, selected, style.gender, component, expandedHands)
                val authored = ExpandedAtelierLayers.supports(slot, requested.variantFor(slot))
                if (slot == "headwear" && !authored)
                    out = AsepriteDetailArtwork.headwear(applicationContext, out, pixels, selected, style.gender, effective.headwear ?: 0)
                if (authored) out = ExpandedAtelierArtwork.render(applicationContext, out, pixels, mask,
                    expandedCoverage!!, expandedSocks!!, expandedHands!!, selected, requested, effective,
                    equipment = true, onlySlot = slot)
            }
            out
        } else {
            val equipped = AvatarEquipmentLayers.render(constructed, pixels, mask, selected, style.gender, effective, gearHands)
            AsepriteDetailArtwork.headwear(applicationContext, equipped, pixels, selected, style.gender, effective.headwear ?: 0)
        }
        return Bitmap.createBitmap(completed, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { colored.put(key, it) }
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
        val key = "display|${WardrobeCoverage.VERSION}|${WardrobeClothing.VERSION}|${SockArtwork.VERSION}|${SockClothing.VERSION}|${AvatarEquipmentLayers.VERSION}|${AvatarCostumeLayers.VERSION}|${AvatarHeadwearLayers.VERSION}|${AvatarAtelierClothing.VERSION}|${AsepriteMaterialLayers.VERSION}|${ExpandedAtelierLayers.VERSION}|$selected|$style"
        (if (original) femaleDisplayBases[selected] else colored.get(key))?.let { return it }
        val pixels = IntArray(WIDTH * HEIGHT)
        image.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        java.util.Arrays.fill(pixels, 0, WIDTH * FEMALE_DISPLAY_PADDING, 0)
        return Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also {
            if (original) femaleDisplayBases[selected] = it else colored.put(key, it)
        }
    }
}

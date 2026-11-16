package com.runmoni.app.avatar

import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.AvatarStyle
import kotlin.math.abs

/** Additional garment constructions over the pinned original cloth labels.
 * These are material/panel variants of the approved silhouettes, not new skin,
 * a new shoe outline or a replacement pose. Source/mask arrays are never written.
 */
object AvatarAtelierClothing {
    const val VERSION = "approved-atelier-v1-mesh-rib-panels"
    const val MESH_TOP = 6
    const val RIB_SOCKS = 4
    const val PANEL_SHOES = 4
    private const val WIDTH = 256
    private const val SIZE = WIDTH * 384
    private val GRAPHITE = intArrayOf(0x162735, 0x29434e, 0x45636d, 0x759199, 0xb8cac8)
    private val RIB_LIGHT = intArrayOf(0x334e59, 0x50717a, 0x7f9b9f, 0xadc4c1, 0xdce6d9)

    /** A short sleeve/crew/base shoe supplies the original geometry first. */
    fun baseTop(variant: Int) = if (variant == MESH_TOP) 1 else variant
    fun baseSocks(variant: Int) = if (variant == RIB_SOCKS) 1 else variant
    fun baseShoes(variant: Int) = if (variant == PANEL_SHOES) 2 else variant
    fun topColor(variant: Int, selected: Int) =
        if (variant == MESH_TOP && selected == 0) 3 else selected

    /** Masks are requested only for selected constructions. All body additions
     * remain inside independently pinned cloth/short-sleeve/crew labels.
     * Foreground hands override shirt labels; shoes cannot affect source soles.
     */
    fun render(pixels: IntArray, original: IntArray, fabric: IntArray,
        coverage: IntArray?, sockMask: IntArray?, protectedHands: IntArray?,
        frame: Int, style: AvatarStyle): IntArray {
        require(pixels.size == SIZE && original.size == SIZE && fabric.size == SIZE)
        require(coverage == null || coverage.size == SIZE)
        require(sockMask == null || sockMask.size == SIZE)
        require(protectedHands == null || protectedHands.size == SIZE)
        val mesh = style.top == MESH_TOP
        val rib = style.socks == RIB_SOCKS
        val panels = style.shoes == PANEL_SHOES
        require(!mesh || coverage != null && protectedHands != null)
        require(!rib || sockMask != null)
        if (!mesh && !rib && !panels) return pixels.copyOf()
        val out = pixels.copyOf()
        val pose = Math.floorMod(frame, 8)
        val a = AvatarEquipmentLayers.anchors(pose, style.gender)
        val startY = if (style.gender == AvatarGender.MALE) 190 else 177
        val hemY = a.waist.y - 3
        val colors = style.runningKitColors.normalized()
        val topColor = topColor(style.top, colors.top)
        val shoeColor = if (colors.shoes == 0) 3 else colors.shoes
        val shoes = (if (style.gender == AvatarGender.MALE) maleShoes else femaleShoes)[pose]
        for (i in out.indices) {
            if (original[i] ushr 24 == 0) continue
            // A few source cloth labels continue under a crossing hand. The
            // base tee cannot distinguish that occlusion, so the independent
            // hand plate restores it before this construction is materialized.
            if (mesh && protectedHands!![i] != 0) {
                out[i] = original[i]
                continue
            }
            val region = (fabric[i] ushr 16) and 255
            val x = i % WIDTH
            val y = i / WIDTH
            if (mesh) {
                val clothLabel = coverage!![i]
                val sleeve = ((clothLabel ushr 16) and 1) != 0
                if (region == 1 || sleeve) {
                    val shade = ((if (region == 1) fabric[i] else clothLabel) ushr 8) and 255
                    val axis = a.waist.x + 22 - (y - startY) * 12 / (hemY - startY)
                    // Broad dark side mesh meets the brighter front panel. A
                    // two-pixel raglan seam follows the upper shoulder plane.
                    val side = x < axis - 19
                    var material = if (side) (shade - 48).coerceAtLeast(0) else shade
                    val raglan = axis - 29 + (y - startY) / 2
                    if (y in startY + 3..startY + 24 && abs(x - raglan) <= 1)
                        material = (shade - 66).coerceAtLeast(0)
                    var rgb = GarmentPalette.shade(topColor, material)
                    // Knit pores sit in the side cloth in a sparse 2x2 cluster,
                    // anchored to the body, not a random per-frame noise field.
                    val localX = Math.floorMod(x - a.waist.x, 9)
                    val localY = Math.floorMod(y - a.waist.y, 9)
                    if (side && y >= startY + 20 && y <= hemY - 7 && localX in 2..3 && localY in 2..3)
                        rgb = GarmentPalette.shade(topColor, (material + 32).coerceAtMost(255))
                    if (region == 1 && y in hemY - 3..hemY - 1)
                        rgb = GarmentPalette.shade(topColor, (material - 24).coerceAtLeast(0))
                    out[i] = (pixels[i] and -0x1000000) or rgb
                }
            }
            if (rib) {
                val label = sockMask!![i]
                if (((label ushr 16) and 1) != 0) {
                    val detail = label and 255
                    val shade = (label ushr 8) and 255
                    // The authored shin-axis rib strip survives all moving
                    // shins; no screen-space vertical bars cross a bent leg.
                    val cuff = (detail and 1) != 0
                    val raisedRib = (detail and 4) != 0
                    val rgb = when {
                        cuff -> GarmentPalette.shade(1, (shade - 22).coerceAtLeast(0))
                        raisedRib -> ramp(RIB_LIGHT, (shade - 14).coerceAtLeast(0))
                        else -> ramp(GRAPHITE, shade)
                    }
                    out[i] = (pixels[i] and -0x1000000) or rgb
                }
            }
            if (panels && region == 3) {
                // Bounding rectangles/heel-to-toe axes were authored separately
                // for each real shoe, including the nearly hidden male F5 heel.
                val shoe = shoes.firstOrNull { it.contains(x, y) } ?: continue
                val dx = shoe.toeX - shoe.heelX
                val dy = shoe.toeY - shoe.heelY
                val lengthSquared = dx * dx + dy * dy
                val along = ((x - shoe.heelX) * dx + (y - shoe.heelY) * dy) * 100 / lengthSquared
                val cross = (x - shoe.heelX) * -dy + (y - shoe.heelY) * dx
                val shade = (fabric[i] ushr 8) and 255
                // A darker heel counter and curved-looking stepped quarter
                // panel are sewn onto the existing upper, with a narrow welt.
                val quarterEnd = 37 + if (cross > 0) 5 else 0
                var rgb = if (along <= quarterEnd) ramp(GRAPHITE, (shade + 8).coerceAtMost(255))
                    else GarmentPalette.shade(shoeColor, shade)
                if (along in quarterEnd + 1..quarterEnd + 5)
                    rgb = GarmentPalette.shade(shoeColor, (shade - 58).coerceAtLeast(0))
                // A short light heel stitch and two toe flex clusters replace
                // tiny UI-like boxes. Source white laces/sole stay untouched.
                if (along in 17..23 && abs(cross) < lengthSquared / 5)
                    rgb = ramp(RIB_LIGHT, (shade + 32).coerceAtMost(255))
                if (along in 72..76 || along in 85..88)
                    rgb = GarmentPalette.shade(shoeColor, (shade - 24).coerceAtLeast(0))
                out[i] = (pixels[i] and -0x1000000) or rgb
            }
        }
        return out
    }

    private data class Shoe(val left: Int, val top: Int, val right: Int, val bottom: Int,
        val heelX: Int, val heelY: Int, val toeX: Int, val toeY: Int) {
        fun contains(x: Int, y: Int) = x in left..right && y in top..bottom
    }
    private val maleShoes = arrayOf(
        arrayOf(Shoe(38,293,67,348,61,302,43,340), Shoe(168,335,228,368,181,342,221,354)),
        arrayOf(Shoe(94,319,124,368,116,326,100,358), Shoe(124,355,168,378,129,359,161,373)),
        arrayOf(Shoe(70,289,96,342,91,298,75,333), Shoe(109,352,166,378,114,359,157,371)),
        arrayOf(Shoe(32,289,61,341,55,297,37,333), Shoe(177,338,237,371,183,345,228,359)),
        arrayOf(Shoe(48,293,78,347,72,302,54,339), Shoe(187,334,246,369,192,341,238,355)),
        arrayOf(Shoe(90,313,98,326,97,315,91,324), Shoe(74,360,125,382,81,365,117,376)),
        arrayOf(Shoe(72,297,100,351,95,306,78,342), Shoe(95,354,155,378,101,360,147,371)),
        arrayOf(Shoe(29,293,58,347,52,301,35,339), Shoe(174,326,231,363,180,333,223,349)),
    )
    private val femaleShoes = arrayOf(
        arrayOf(Shoe(46,270,75,325,70,279,51,317), Shoe(176,316,236,348,182,323,228,337)),
        arrayOf(Shoe(110,281,136,329,132,288,115,321), Shoe(112,332,170,354,118,337,162,348)),
        arrayOf(Shoe(90,263,116,313,112,270,95,305), Shoe(111,332,168,354,117,337,160,348)),
        arrayOf(Shoe(39,266,67,319,62,273,44,312), Shoe(176,314,233,348,182,321,225,336)),
        arrayOf(Shoe(59,269,87,323,82,277,64,316), Shoe(184,314,241,348,190,321,233,336)),
        arrayOf(Shoe(121,282,148,329,143,289,126,321), Shoe(106,335,164,354,112,340,156,348)),
        arrayOf(Shoe(77,279,105,331,100,287,82,324), Shoe(101,334,158,355,107,339,150,349)),
        arrayOf(Shoe(38,270,67,323,62,277,43,316), Shoe(178,306,232,340,184,313,224,329)),
    )

    private fun ramp(palette: IntArray, shade: Int): Int {
        val position = shade.coerceIn(0, 255) * 4
        val index = position / 255
        val fraction = position % 255
        var rgb = 0
        for (shift in 16 downTo 0 step 8) {
            val a = (palette[index] ushr shift) and 255
            val b = (palette[(index + 1).coerceAtMost(4)] ushr shift) and 255
            rgb = rgb or (((a * (255 - fraction) + b * fraction + 127) / 255) shl shift)
        }
        return rgb
    }
}

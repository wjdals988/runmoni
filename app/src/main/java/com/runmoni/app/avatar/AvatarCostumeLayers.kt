package com.runmoni.app.avatar

import com.runmoni.app.domain.AvatarGender
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Clothes are drawn on the sixteen approved whole frames, never a body-part rig.
 * Authored fabric/coverage labels retain the moving silhouette and protect hands.
 * Headwear owns its separate hair-occlusion/background-extension allowances.
 * Implemented recipes are not a statement of user visual approval.
 */
object AvatarCostumeLayers {
    const val VERSION = "approved-frame-costumes-v2-clusters"
    private const val W = 256
    private const val H = 384
    private val FUR = intArrayOf(0x728d89, 0xa6bbb0, 0xd1dccb, 0xedf0de, 0xfff8e9)
    private val RED = intArrayOf(0x592336, 0x8d2e40, 0xc94755, 0xec6c71, 0xffaaa0)
    private val BROWN = intArrayOf(0x392b31, 0x654134, 0x956044, 0xbf885b, 0xe5b883)
    private val GOLD = intArrayOf(0x69401f, 0x9d6529, 0xd69935, 0xf0c25f, 0xffe7a3)
    private val BOOT = intArrayOf(0x151d2c, 0x26303b, 0x3d4852, 0x60727a, 0x9bafb0)
    private val BEAR_PANEL = intArrayOf(0x71533b, 0xa37c52, 0xc9a470, 0xe8c894, 0xffe8bd)
    private val GOLD_PANEL = intArrayOf(0x8b622d, 0xbe934b, 0xe9c57f, 0xffdfa0, 0xfff2d0)

    fun supports(costume: Int): Boolean = headKind(costume) != null

    private fun headKind(costume: Int): AvatarHeadwearLayers.Kind? = when (costume) {
        1 -> AvatarHeadwearLayers.Kind.SANTA
        4 -> AvatarHeadwearLayers.Kind.BEAR
        5 -> AvatarHeadwearLayers.Kind.GOLDEN
        else -> null
    }

    /** Independent headwear geometry; never inferred from the rendered result. */
    fun clothingExtensionMask(original: IntArray, frame: Int, gender: AvatarGender, costume: Int): IntArray {
        require(original.size == W * H)
        val kind = headKind(costume) ?: return IntArray(original.size)
        return AvatarHeadwearLayers.extensionMask(original, frame, gender, kind)
    }

    /** Explicit hair-only allowance, separate from face/eye/skin identity. */
    fun hairOcclusionMask(original: IntArray, frame: Int, gender: AvatarGender, costume: Int): IntArray {
        require(original.size == W * H)
        val kind = headKind(costume) ?: return IntArray(original.size)
        return AvatarHeadwearLayers.occlusionMask(original, frame, gender, kind)
    }

    /** Existing IDs1/4/5 only; swimwear still needs its own approved skin underlay. */
    fun render(original: IntArray, fabric: IntArray, coverage: IntArray, protectedHands: IntArray,
        frame: Int, gender: AvatarGender, costume: Int): IntArray {
        require(original.size == W * H && fabric.size == original.size && coverage.size == original.size && protectedHands.size == original.size)
        val kind = headKind(costume) ?: return original.clone()
        val out = original.clone()
        val a = AvatarEquipmentLayers.anchors(frame, gender)
        val material = when (costume) { 1 -> RED; 4 -> BROWN; else -> GOLD }
        val headEnd = if (gender == AvatarGender.MALE) 182 else 176
        for (i in out.indices) {
            val region = (fabric[i] ushr 16) and 255
            val flags = (coverage[i] ushr 16) and 255
            val covered = region in 1..3 || (flags and 6) != 0
            val x = i % W; val y = i / W
            val neck = x in a.eye.x - 14..a.eye.x + 10 && y in a.eye.y + 20..a.eye.y + 45
            if (!covered || ((y < headEnd || neck) && region != 1) || (original[i] ushr 24) == 0 || protectedHands[i] != 0) continue
            val label = if (region in 1..3) fabric[i] else coverage[i]
            var shade = (label ushr 8) and 255
            // Preserve the original broad, rich shading. Only new seams/trim
            // below use discrete clusters; the whole cloth is not quantized.
            if (region == 1 && x < a.waist.x - 19) shade = max(0, shade - 10)
            val cuff = (coverage[i] and 2) != 0 && (flags and 2) != 0
            val palette = when {
                cuff && costume == 1 -> FUR
                cuff && costume == 4 -> BEAR_PANEL
                region == 3 && costume == 1 -> BOOT
                else -> material
            }
            // An ankle contact follows the existing shoe labels in every pose;
            // no guessed leg/boot shape or new body alpha is introduced.
            if ((flags and 4) != 0 && region != 3 && touchesShoes(fabric, x, y)) shade = max(0, shade - 32)
            if (cuff) {
                // A broad light edge and darker fold are readable at phone size,
                // unlike single-pixel cuff stitching or a new ring around skin.
                val above = if (y >= 2) coverage[i - W * 2] else 0
                val upperCuff = (above and 2) == 0 || (((above ushr 16) and 2) == 0)
                if (upperCuff) shade = min(255, shade + 22) else shade = max(0, shade - 8)
            }
            out[i] = (original[i] and -0x1000000) or ramp(palette, shade)
        }
        val torso = BodyRaster(out, original, fabric, coverage, protectedHands, headEnd)
        val x = a.waist.x; val y = a.waist.y
        val top = a.eye.y + if (gender == AvatarGender.MALE) 44 else 41
        // Collar, opening and waistband meet on the same authored front axis.
        // The old opening started to the side of its V collar and looked pasted on.
        fun frontAxis(atY: Int): Int = x + 13 - (atY - top) * 5 / max(1, y - top)
        if (costume == 1) santaCoat(torso, x, y, top, ::frontAxis)
        else animalSuit(torso, x, y, top, costume, material, ::frontAxis)
        return AvatarHeadwearLayers.render(out, original, frame, gender, kind)
    }

    private fun santaCoat(r: BodyRaster, x: Int, y: Int, top: Int, axis: (Int) -> Int) {
        val join = axis(top + 15)
        // Shadow under fur and a few large tufts, rather than a continuous
        // bright vector outline around the entire neckline.
        r.line(RED[0], x - 29, top + 7, join, top + 20, 12)
        r.line(RED[0], join, top + 20, x + 34, top + 8, 12)
        r.line(FUR[1], x - 28, top + 4, join, top + 16, 9)
        r.line(FUR[1], join, top + 16, x + 33, top + 5, 9)
        r.line(FUR[3], x - 27, top + 2, join - 1, top + 13, 6)
        r.line(FUR[4], join, top + 13, x + 31, top + 3, 5)
        r.rect(FUR[4], join - 4, top + 11, 7, 5)
        r.rect(FUR[3], x - 19, top + 5, 6, 5)
        // Connected coat opening. Three dark button clusters replace the old
        // repeated dotted ladder, so the fur is read as one material.
        for (py in top + 16 until y - 11) {
            val center = axis(py)
            r.rect(RED[0], center - 5, py, 11, 1)
            r.rect(FUR[1], center - 4, py, 8, 1)
            r.rect(FUR[3], center - 3, py, 5, 1)
        }
        for (py in top + 24 until y - 15 step 15) r.rect(BOOT[0], axis(py) + 1, py, 4, 4)
        // Tilted waist contact holds the buckle and coat halves together.
        r.line(RED[0], x - 37, y - 4, x + 34, y, 13)
        r.line(BOOT[0], x - 36, y - 8, x + 34, y - 4, 11)
        r.line(BOOT[2], x - 34, y - 10, x + 32, y - 6, 4)
        val buckle = axis(y - 8)
        r.rect(GOLD[0], buckle - 9, y - 16, 23, 18)
        r.rect(GOLD[3], buckle - 7, y - 14, 19, 14)
        r.rect(GOLD[4], buckle - 6, y - 14, 15, 4)
        r.rect(BOOT[0], buckle - 3, y - 10, 10, 6)
        r.rect(GOLD[2], buckle + 3, y - 9, 6, 3)
        // The hem retains the original split/moving edges; no skirt/body alpha.
        r.line(RED[0], x - 25, y + 7, x + 25, y + 11, 8)
        r.line(FUR[2], x - 24, y + 4, x + 24, y + 8, 6)
        r.line(FUR[4], x - 22, y + 2, x + 22, y + 6, 3)
    }

    private fun animalSuit(r: BodyRaster, x: Int, y: Int, top: Int, costume: Int,
        material: IntArray, axis: (Int) -> Int) {
        val center = axis(y - 25)
        val panel = if (costume == 4) BEAR_PANEL else GOLD_PANEL
        // Smaller stepped, source-shaded belly leaves the dark torso side visible.
        // Bear is rounded; golden has a broader angular chest.
        r.panel(panel, center, y - 25, if (costume == 4) 20 else 22, 27, angular = costume == 5)
        val join = axis(top + 13)
        r.line(material[0], x - 28, top + 6, join, top + 17, 9)
        r.line(material[0], join, top + 17, x + 31, top + 7, 9)
        r.line(material[2], x - 27, top + 3, join, top + 13, 6)
        r.line(material[3], join, top + 13, x + 29, top + 4, 5)
        // One short pull and a dark seam convey the zip without a dashed ladder.
        // The nearer arm is excluded by authored sleeve labels.
        for (py in top + 16 until y - 3) {
            val front = axis(py)
            r.rect(material[0], front - 1, py, 3, 1)
            r.rect(panel[3], front + 2, py, 2, 1)
        }
        r.rect(material[0], axis(top + 18) - 2, top + 17, 6, 9)
        r.rect(panel[4], axis(top + 18), top + 19, 2, 4)
        // Pocket folds are compact clusters, not independent fine outlines.
        r.line(material[1], x - 24, y - 16, x - 12, y - 9, 4)
        r.line(material[3], x - 22, y - 18, x - 12, y - 12, 2)
        r.line(material[1], x + 21, y - 11, x + 29, y - 18, 4)
        r.line(material[0], x - 20, y + 2, x + 23, y + 5, 4)
        if (costume == 5) {
            // A stepped chest emblem/shoulder patch makes golden a distinct sport
            // mascot, rather than a color swap of the bear's rounded panel.
            r.line(GOLD[1], x - 27, top + 12, x - 13, top + 18, 7)
            r.line(GOLD[4], x - 26, top + 10, x - 13, top + 16, 3)
            r.glyph(x + 7, top + 20, 2, arrayOf(
                "....KKK....", "....KHK....", "...KHHHK...", "KKKKHHHKKKK",
                "KHHHHHHHHHK", ".KHHHHHHHK.", "..KHHHHHK..", "..KHHHHHK..",
                ".KHHKKHHHK.", ".KHK..KHHK.", ".KK....KKK.",
            ), mapOf('K' to GOLD[0], 'H' to GOLD_PANEL[4]))
        }
    }

    private fun touchesShoes(fabric: IntArray, x: Int, y: Int): Boolean {
        for (dy in -2..2) for (dx in -2..2) {
            val px = x + dx; val py = y + dy
            if (px in 0 until W && py in 0 until H && ((fabric[py * W + px] ushr 16) and 255) == 3) return true
        }
        return false
    }

    private fun ramp(colors: IntArray, value: Int): Int {
        val scaled = value.coerceIn(0, 255) * 4; val stop = scaled / 255; val fraction = scaled % 255
        val a = colors[stop]; val b = colors[min(stop + 1, 4)]
        var rgb = 0
        for (shift in intArrayOf(16, 8, 0)) {
            val lo = (a ushr shift) and 255; val hi = (b ushr shift) and 255
            rgb = rgb or (((lo * (255 - fraction) + hi * fraction + 127) / 255) shl shift)
        }
        return rgb
    }

    /** Details stay on original torso/shorts cloth. Foreground sleeve labels and
     * protected hands occlude them, including when an arm crosses the opening.
     */
    private class BodyRaster(val out: IntArray, val source: IntArray, val mask: IntArray,
        val coverage: IntArray, val hands: IntArray, val minY: Int) {
        private fun put(x: Int, y: Int, rgb: Int) {
            if (x !in 0 until W || y !in 0 until H) return
            val i = y * W + x; val region = (mask[i] ushr 16) and 255
            if (region !in 1..2 || (y < minY && region != 1) || (source[i] ushr 24) == 0 ||
                hands[i] != 0 || (((coverage[i] ushr 16) and 2) != 0)) return
            out[i] = (source[i] and -0x1000000) or rgb
        }
        fun rect(c: Int, x: Int, y: Int, w: Int, h: Int) {
            for (py in y until y + h) for (px in x until x + w) put(px, py, c)
        }
        fun line(c: Int, x0: Int, y0: Int, x1: Int, y1: Int, width: Int) {
            var x = x0; var y = y0; val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
            val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1; var error = dx + dy
            while (true) {
                rect(c, x - width / 2, y - width / 2, width, width)
                if (x == x1 && y == y1) break
                val e = error * 2
                if (e >= dy) { error += dy; x += sx }
                if (e <= dx) { error += dx; y += sy }
            }
        }
        fun panel(palette: IntArray, cx: Int, cy: Int, rx: Int, ry: Int, angular: Boolean) {
            for (y in max(0, cy - ry)..min(H - 1, cy + ry)) {
                val dy = abs(y - cy)
                val inset = when {
                    angular && y < cy -> if (dy > ry * 3 / 4) 6 else 0
                    dy <= ry / 3 -> 0
                    dy <= ry * 2 / 3 -> 3
                    dy <= ry * 8 / 9 -> 7
                    else -> 12
                }
                val left = cx - rx + inset; val right = cx + rx - inset
                for (x in max(0, left)..min(W - 1, right)) {
                    val sourceShade = (mask[y * W + x] ushr 8) and 255
                    val shade = sourceShade + 13 - max(0, cx - x) / 2
                    // Darken side/bottom contact, without inking the complete
                    // perimeter into an independent sticker-like oval.
                    val rgb = when {
                        x < left + 3 || y > cy + ry - 3 -> palette[0]
                        x < left + 5 -> palette[1]
                        else -> ramp(palette, shade)
                    }
                    put(x, y, rgb)
                }
                if ((y - cy + ry) % 12 in 3..5) rect(palette[3], left + 5, y, 2, 1)
            }
        }
        fun glyph(x: Int, y: Int, scale: Int, rows: Array<String>, palette: Map<Char, Int>) {
            for ((row, text) in rows.withIndex()) for ((column, key) in text.withIndex()) {
                val color = palette[key] ?: continue
                rect(color, x + column * scale, y + row * scale, scale, scale)
            }
        }
    }
}

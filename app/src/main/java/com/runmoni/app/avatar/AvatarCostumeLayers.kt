package com.runmoni.app.avatar

import com.runmoni.app.domain.AvatarGender
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Garments authored on the sixteen approved whole frames, never a body-part rig.
 * Fabric/coverage labels decide where sleeves and trousers may replace visible
 * material. Faces, hair, hands, body positions and source files are not rebuilt.
 * The only new alpha is a separately bounded hat/hood/ear layer BEHIND the head.
 * Implemented recipes are not a statement of user visual approval.
 */
object AvatarCostumeLayers {
    const val VERSION = "approved-frame-costumes-v1"
    private const val W = 256
    private const val H = 384
    private const val INK = 0x10172c
    private val FUR = intArrayOf(0x728d89, 0xa6bbb0, 0xd1dccb, 0xedf0de, 0xfff8e9)
    private val RED = intArrayOf(0x592336, 0x8d2e40, 0xc94755, 0xec6c71, 0xffaaa0)
    private val BROWN = intArrayOf(0x392b31, 0x654134, 0x956044, 0xbf885b, 0xe5b883)
    private val GOLD = intArrayOf(0x69401f, 0x9d6529, 0xd69935, 0xf0c25f, 0xffe7a3)
    private val BOOT = intArrayOf(0x151d2c, 0x26303b, 0x3d4852, 0x60727a, 0x9bafb0)
    private val BEAR_PANEL = intArrayOf(0x71533b, 0xa37c52, 0xc9a470, 0xe8c894, 0xffe8bd)
    private val GOLD_PANEL = intArrayOf(0x8b622d, 0xbe934b, 0xe9c57f, 0xffdfa0, 0xfff2d0)

    fun supports(costume: Int): Boolean = costume == 1 || costume == 4 || costume == 5

    /** An independently authored allowance, not a mask inferred from output differences.
     * Only almost-transparent source background inside the fixed hat/hood geometry
     * can become clothing. The outer frame margin, all existing head ink and the
     * entire body below its head boundary remain outside this extension allowance.
     */
    fun clothingExtensionMask(original: IntArray, frame: Int, gender: AvatarGender, costume: Int): IntArray {
        require(original.size == W * H)
        val mask = IntArray(original.size)
        if (!supports(costume)) return mask
        val g = head(frame, gender)
        val headEnd = if (gender == AvatarGender.MALE) 182 else 176
        for (y in 14 until headEnd) for (x in 8 until W - 8) {
            val i = y * W + x
            if ((original[i] ushr 24) > 16) continue
            val selected = if (costume == 1) {
                triangle(x, y, g.cap) || ellipse(x, y, g.pomX, g.pomY, 14, 14) <= 1.0
            } else {
                val ring = ellipse(x, y, g.cx, g.cy, g.rx, g.ry)
                // The hood is folded back: only its crown/ears sit behind the
                // unchanged hair. A full side arc reads as a floating face ring.
                val hood = ring in .70..1.0 && y < g.cy - (g.ry * .42).roundToInt()
                val ears = if (costume == 4) {
                    ellipse(x, y, g.leftEar, g.earY, 19, 20) <= 1.0 ||
                        ellipse(x, y, g.rightEar, g.earY, 19, 20) <= 1.0
                } else {
                    triangle(x, y, earTriangle(g.leftEar, g.earY)) ||
                        triangle(x, y, earTriangle(g.rightEar, g.earY))
                }
                hood || ears
            }
            if (selected) mask[i] = -1
        }
        return mask
    }

    /** Existing costume IDs1/4/5 only; swimwear requires its own approved skin underlay. */
    fun render(original: IntArray, fabric: IntArray, coverage: IntArray, protectedHands: IntArray,
        frame: Int, gender: AvatarGender, costume: Int): IntArray {
        require(original.size == W * H && fabric.size == original.size && coverage.size == original.size && protectedHands.size == original.size)
        val out = original.clone()
        if (!supports(costume)) return out
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
            // Reuse authored shading and the source's moving edges. No skin hue
            // lookup, screen-space noise or frame-dependent sparkle is applied.
            if (region == 1 && x < a.waist.x - 19) shade = max(0, shade - 10)
            val cuff = (coverage[i] and 2) != 0 && (flags and 2) != 0
            val palette = when {
                cuff -> FUR
                region == 3 && costume == 1 -> BOOT
                else -> material
            }
            out[i] = (original[i] and -0x1000000) or ramp(palette, shade)
        }
        val torso = BodyRaster(out, original, fabric, protectedHands, headEnd)
        val x = a.waist.x; val y = a.waist.y
        val top = a.eye.y + if (gender == AvatarGender.MALE) 44 else 41
        fun frontAxis(atY: Int): Int = x + 21 - (atY - top) * 12 / max(1, y - top)
        if (costume == 1) {
            // Cut collar, a fur-lined opening, cuff labels, leather belt and
            // buckle distinguish a Santa coat from a red material substitution.
            torso.line(FUR[1], x - 28, top + 5, x + 5, top + 18, 9)
            torso.line(FUR[3], x - 28, top + 3, x + 5, top + 16, 5)
            torso.line(FUR[1], x + 5, top + 18, x + 35, top + 7, 9)
            torso.line(FUR[4], x + 5, top + 15, x + 34, top + 5, 3)
            for (py in top + 15 until y - 5) {
                val axis = frontAxis(py)
                torso.rect(FUR[1], axis - 3, py, 7, 1)
                torso.rect(FUR[3], axis - 2, py, 3, 1)
                if ((py - top) % 12 in 0..2) torso.rect(INK, axis, py, 3, 1)
            }
            torso.line(INK, x - 38, y - 8, x + 36, y - 4, 11)
            torso.line(BOOT[2], x - 36, y - 9, x + 34, y - 5, 6)
            torso.rect(GOLD[1], x + 2, y - 15, 20, 16)
            torso.rect(GOLD[4], x + 4, y - 13, 16, 12)
            torso.rect(BOOT[0], x + 7, y - 10, 10, 6)
            torso.line(FUR[2], x - 25, y + 5, x + 27, y + 9, 5)
            torso.line(FUR[4], x - 24, y + 4, x + 26, y + 8, 2)
        } else {
            // A sewn belly panel follows each source torso. Near-arm pixels are
            // excluded by fabric labels, including when the arm crosses the zip.
            torso.materialEllipse(if (costume == 4) BEAR_PANEL else GOLD_PANEL, x + 5, y - 26, 25, 35)
            torso.line(material[0], x - 29, top + 5, x + 3, top + 16, 7)
            torso.line(material[3], x - 28, top + 3, x + 3, top + 13, 3)
            torso.line(material[0], x + 3, top + 16, x + 32, top + 5, 7)
            torso.line(material[3], x + 3, top + 13, x + 31, top + 3, 3)
            for (py in top + 7 until y + 4) {
                val axis = frontAxis(py) - 6
                torso.rect(material[0], axis - 1, py, 3, 1)
                if ((py - top) % 5 == 0) torso.rect(FUR[3], axis, py, 2, 2)
            }
            torso.line(material[0], x - 25, y - 18, x - 13, y - 11, 3)
            torso.line(material[3], x - 24, y - 20, x - 12, y - 13, 1)
            torso.line(material[0], x + 18, y - 13, x + 28, y - 20, 3)
            if (costume == 5) {
                // The golden suit has angular hood ears, shoulder tape and an
                // embroidered star, not simply the bear suit recolored yellow.
                torso.line(GOLD[0], x - 30, top + 14, x - 12, top + 20, 5)
                torso.line(GOLD[4], x - 29, top + 12, x - 11, top + 18, 2)
                torso.poly(GOLD[0], x + 27, top + 20, x + 30, top + 26, x + 37, top + 27,
                    x + 32, top + 32, x + 33, top + 39, x + 27, top + 35,
                    x + 21, top + 39, x + 22, top + 32, x + 17, top + 27, x + 24, top + 26)
                torso.poly(FUR[4], x + 27, top + 24, x + 29, top + 29, x + 33, top + 29,
                    x + 30, top + 32, x + 30, top + 35, x + 27, top + 33,
                    x + 24, top + 35, x + 24, top + 31, x + 21, top + 29, x + 25, top + 29)
            }
        }
        val extension = clothingExtensionMask(original, frame, gender, costume)
        val g = head(frame, gender)
        for (i in extension.indices) {
            if (extension[i] == 0) continue
            val px = i % W; val py = i / W
            val rgb = if (costume == 1) {
                val pom = ellipse(px, py, g.pomX, g.pomY, 14, 14)
                val brim = distanceToSegment(px, py, g.cap[0], g.cap[1], g.cap[12], g.cap[13])
                val edge = g.cap.indices.filter { it % 2 == 0 }.minOf { at ->
                    val next = (at + 2) % g.cap.size
                    distanceToSegment(px, py, g.cap[at], g.cap[at + 1], g.cap[next], g.cap[next + 1])
                }
                val fold = distanceToSegment(px, py, g.cap[6], g.cap[7], g.cap[14], g.cap[15])
                when {
                    pom <= 1.0 -> ramp(FUR, (230 - pom * 115 - (px - g.pomX) * 2).roundToInt().coerceIn(0, 255))
                    brim < 5 -> FUR[if (brim < 2) 4 else 1]
                    edge < 1.5 -> RED[0]
                    fold < 2 -> RED[1]
                    else -> ramp(RED, (230 - abs(px - g.cap[6]) / 2 - (py - 14) * 2).coerceIn(20, 240))
                }
            } else {
                val ring = ellipse(px, py, g.cx, g.cy, g.rx, g.ry)
                val nearEar = if (abs(px - g.leftEar) < abs(px - g.rightEar)) g.leftEar else g.rightEar
                val ear = ellipse(px, py, nearEar, g.earY, 19, 20)
                val inEar = if (costume == 4) ear <= 1.0 else triangle(px, py, earTriangle(nearEar, g.earY))
                when {
                    inEar && ear < .30 -> if (costume == 4) FUR[2] else GOLD[4]
                    inEar && ear > .82 -> material[0]
                    ring > .94 -> material[0]
                    ring in .70.. .80 -> if (costume == 4) BROWN[3] else FUR[3]
                    else -> ramp(material, (185 - (px - g.cx) / 2 - (py - g.cy) / 2).coerceIn(30, 240))
                }
            }
            out[i] = (253 shl 24) or rgb
        }
        return out
    }

    private data class Head(val cx: Int, val cy: Int, val rx: Int, val ry: Int,
        val leftEar: Int, val rightEar: Int, val earY: Int, val cap: IntArray, val pomX: Int, val pomY: Int)

    private fun head(frame: Int, gender: AvatarGender): Head {
        val eye = AvatarEquipmentLayers.anchors(frame, gender).eye
        val cx = eye.x - 12; val cy = eye.y - 33
        val rx = if (gender == AvatarGender.MALE) 86 else 79
        val ry = if (gender == AvatarGender.MALE) 90 else 80
        val earY = max(34, cy - (ry * .77).roundToInt())
        val cap = intArrayOf(eye.x - 94, eye.y - 89, eye.x - 85, max(14, eye.y - 112),
            eye.x - 49, max(14, eye.y - 124), eye.x - 17, max(14, eye.y - 127),
            eye.x + 26, max(20, eye.y - 110), eye.x + 44, eye.y - 86,
            eye.x + 37, eye.y - 76, eye.x - 27, eye.y - 87)
        return Head(cx, cy, rx, ry, cx - (rx * .65).roundToInt(), cx + (rx * .65).roundToInt(),
            earY, cap, cap[0] + 4, cap[1] + 2)
    }

    private fun earTriangle(cx: Int, cy: Int) = intArrayOf(cx - 19, cy + 17, cx - 13, cy - 20,
        cx + 7, cy - 17, cx + 19, cy + 17)

    private fun ellipse(x: Int, y: Int, cx: Int, cy: Int, rx: Int, ry: Int): Double =
        (x - cx).toDouble() * (x - cx) / (rx * rx) + (y - cy).toDouble() * (y - cy) / (ry * ry)

    private fun triangle(x: Int, y: Int, xy: IntArray): Boolean {
        var inside = false; var j = xy.size / 2 - 1
        for (i in 0 until xy.size / 2) {
            val xi = xy[i * 2]; val yi = xy[i * 2 + 1]; val xj = xy[j * 2]; val yj = xy[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi).toDouble() * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    private fun distanceToSegment(x: Int, y: Int, x0: Int, y0: Int, x1: Int, y1: Int): Double {
        val dx = x1 - x0; val dy = y1 - y0
        val t = (((x - x0) * dx + (y - y0) * dy).toDouble() / max(1, dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        return kotlin.math.hypot(x - x0 - t * dx, y - y0 - t * dy)
    }

    private fun ramp(colors: IntArray, value: Int): Int {
        val scaled = value.coerceIn(0, 255) * 4; val stop = scaled / 255; val fraction = scaled % 255
        val a = colors[stop]; val b = colors[min(stop + 1, 4)]
        var rgb = 0
        for (shift in intArrayOf(16, 8, 0)) {
            val lo = (a ushr shift) and 255; val hi = (b ushr shift) and 255
            val channel = (lo * (255 - fraction) + hi * fraction + 127) / 255
            rgb = rgb or (channel shl shift)
        }
        return rgb
    }

    /** Decorative coat details only cross original top/bottom cloth, never the
     * nearer arm/sleeve/hand. Sleeve cuffs use their separate authored labels.
     */
    private class BodyRaster(val out: IntArray, val source: IntArray, val mask: IntArray, val hands: IntArray, val minY: Int) {
        private fun put(x: Int, y: Int, rgb: Int) {
            if (x !in 0 until W || y !in 0 until H) return
            val i = y * W + x; val region = (mask[i] ushr 16) and 255
            if (region !in 1..2 || (y < minY && region != 1) || (source[i] ushr 24) == 0 || hands[i] != 0) return
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
        fun materialEllipse(palette: IntArray, cx: Int, cy: Int, rx: Int, ry: Int) {
            for (y in max(0, cy - ry)..min(H - 1, cy + ry)) for (x in max(0, cx - rx)..min(W - 1, cx + rx)) {
                val distance = AvatarCostumeLayers.ellipse(x, y, cx, cy, rx, ry)
                if (distance > 1.0) continue
                val sourceShade = (mask[y * W + x] ushr 8) and 255
                val shade = (sourceShade * .72 + (1.0 - distance) * 35 + 12 - (x - cx) * .35).roundToInt()
                put(x, y, if (distance > .89) palette[0] else AvatarCostumeLayers.ramp(palette, shade))
                if (distance in .77.. .82 && (y - cy + ry) % 5 in 0..1) put(x, y, palette[3])
            }
        }
        fun poly(c: Int, vararg xy: Int) {
            val xs = xy.indices.filter { it % 2 == 0 }.map { xy[it] }
            val ys = xy.indices.filter { it % 2 == 1 }.map { xy[it] }
            for (y in max(0, ys.min())..min(H - 1, ys.max()))
                for (x in max(0, xs.min())..min(W - 1, xs.max())) if (AvatarCostumeLayers.triangle(x, y, xy)) put(x, y, c)
        }
    }
}

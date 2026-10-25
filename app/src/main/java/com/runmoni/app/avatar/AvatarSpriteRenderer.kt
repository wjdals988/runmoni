package com.runmoni.app.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.variantFor
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Original PNG-texture paper doll: face/neck/torso translate together while only
 * articulated limb parts rotate. There is deliberately no procedural-body or
 * legacy low-resolution fallback when production artwork is incomplete.
 */
class AvatarSpriteRenderer(private val art: AvatarArtRepository) {
    private data class Key(val avatar: AvatarStyle, val frame: Int, val running: Boolean)
    private val frames = object : LinkedHashMap<Key, Bitmap>(25, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Bitmap>?) = size > 24
    }
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false; isDither = false }

    companion object {
        const val WIDTH = 256
        const val HEIGHT = 384
        const val FRAMES = 8
        const val MILLIS = 90
        @JvmStatic fun frameAt(elapsedMillis: Long) = ((elapsedMillis.coerceAtLeast(0L) / MILLIS) % FRAMES).toInt()
        private val bob = floatArrayOf(0f, 3f, 0f, -3f, 0f, 3f, 0f, -3f)
        private val ankleOffsets = arrayOf(
            P(46f, 93f), P(10f, 99f), P(-34f, 91f), P(-44f, 54f),
            P(-50f, 30f), P(-8f, 38f), P(28f, 56f), P(47f, 80f),
        )
        private const val INK = -0xebe0c7 // #141f39
        private const val CREAM = -0xa12 // #fff5ee
        private val equipment = intArrayOf(0xff72cbb8.toInt(), 0xffef9281.toInt(), 0xff85abc5.toInt())
    }

    private data class P(val x: Float, val y: Float) {
        operator fun plus(other: P) = P(x + other.x, y + other.y)
        operator fun minus(other: P) = P(x - other.x, y - other.y)
        fun point() = AvatarArtRepository.Point(x, y)
    }
    private data class Limb(val root: P, val middle: P, val tip: P, val footAngle: Float = 0f)
    private data class Pose(val neck: P, val hip: P, val nearArm: Limb, val farArm: Limb, val nearLeg: Limb, val farLeg: Limb)
    private data class Clothes(
        val torso: String?, val pelvis: String?, val topPalette: String?, val bottomPalette: String?,
        val upper: Float, val fore: Float, val thigh: Float, val shin: Float,
        val shoe: AvatarArtRepository.Shoe, val socks: Int,
        val headwear: Int, val hoodFront: String?, val hoodBack: String?,
        val upperArmPart: String? = null, val forearmPart: String? = null,
        val thighPart: String? = null, val shinPart: String? = null, val jointPart: String? = null,
    )

    /** Bitmaps are immutable to callers; cache eviction never recycles a referenced bitmap. */
    @Synchronized
    fun render(avatar: AvatarStyle, frame: Int, running: Boolean): Bitmap {
        val actualFrame = if (running) Math.floorMod(frame, FRAMES) else 0
        val key = Key(avatar, actualFrame, running)
        frames[key]?.let { return it }
        val pose = pose(actualFrame, running)
        val clothes = clothes(avatar)
        val result = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val hair = avatar.hair.checked(4, "hair")

        // Camera-far parts precede torso; camera-near arm is drawn after torso and gear.
        if (art.hasPart("hair_${hair}_back")) draw(canvas, art.part("hair_${hair}_back"), pose.neck)
        clothes.hoodBack?.let { draw(canvas, art.part(it), pose.neck) }
        leg(canvas, pose.farLeg, clothes)
        arm(canvas, pose.farArm, clothes)
        leg(canvas, pose.nearLeg, clothes)
        draw(canvas, art.part(clothes.pelvis ?: "skin_pelvis"), pose.hip)
        draw(canvas, art.part(clothes.torso ?: "skin_torso"), pose.neck)
        hydration(canvas, avatar.hydration.checked(4, "hydration"), pose)
        belt(canvas, avatar.waist.checked(4, "waist"), pose)
        arm(canvas, pose.nearArm, clothes)
        watch(canvas, avatar.wrist.checked(4, "wrist"), pose.nearArm)

        // Whole original face+neck part: no rectangular neck clipping or head swapping with hair.
        draw(canvas, art.part("head_${avatar.face.checked(4, "face")}"), pose.neck)
        draw(canvas, art.part("hair_${hair}_front"), pose.neck)
        clothes.hoodFront?.let { draw(canvas, art.part(it), pose.neck) }
        headwear(canvas, clothes.headwear, pose.neck)
        ear(canvas, avatar.variantFor("ear").checked(4, "ear"), pose.neck)
        glasses(canvas, avatar.eyewear.checked(4, "eyewear"), pose.neck)
        val frozen = requireNotNull(result.copy(Bitmap.Config.ARGB_8888, false)) { "Could not freeze avatar frame" }
        result.recycle()
        frames[key] = frozen
        return frozen
    }

    /** Read-only articulated landmarks for regression tests and arm-motion verification. */
    internal fun debugLandmarks(frame: Int, running: Boolean): Map<String, AvatarArtRepository.Point> {
        val p = pose(Math.floorMod(frame, FRAMES), running)
        return mapOf("neck" to p.neck.point(), "hip" to p.hip.point(),
            "nearShoulder" to p.nearArm.root.point(), "nearElbow" to p.nearArm.middle.point(), "nearWrist" to p.nearArm.tip.point(),
            "farShoulder" to p.farArm.root.point(), "farElbow" to p.farArm.middle.point(), "farWrist" to p.farArm.tip.point(),
            "nearHip" to p.nearLeg.root.point(), "nearKnee" to p.nearLeg.middle.point(), "nearAnkle" to p.nearLeg.tip.point(),
            "farHip" to p.farLeg.root.point(), "farKnee" to p.farLeg.middle.point(), "farAnkle" to p.farLeg.tip.point())
    }

    private fun clothes(a: AvatarStyle): Clothes {
        val top = art.top(a.top.checked(6, "top"))
        val bottom = art.bottom(a.bottom.checked(6, "bottom"))
        val normalShoe = art.shoe(a.shoes.checked(4, "shoes"))
        val suit = art.costume(a.costume.checked(6, "costume"))
        if (suit == null) return Clothes(top.part, bottom.part, top.palette, bottom.palette,
            top.upperArmCoverage, top.forearmCoverage, bottom.thighCoverage, bottom.shinCoverage,
            normalShoe, a.socks.checked(4, "socks"), a.variantFor("headwear").checked(4, "headwear"), null, null)
        val coverTop = "top" in suit.covers
        val coverBottom = "bottom" in suit.covers
        return Clothes(if (coverTop) suit.torso else top.part, if (coverBottom) suit.pelvis else bottom.part,
            if (coverTop) suit.palette else top.palette, if (coverBottom) suit.palette else bottom.palette,
            if (coverTop) suit.upperArmCoverage else top.upperArmCoverage,
            if (coverTop) suit.forearmCoverage else top.forearmCoverage,
            if (coverBottom) suit.thighCoverage else bottom.thighCoverage,
            if (coverBottom) suit.shinCoverage else bottom.shinCoverage,
            if ("shoes" in suit.covers) AvatarArtRepository.Shoe(suit.shoePart ?: "shoe_base", suit.shoePalette) else normalShoe,
            if ("socks" in suit.covers) 3 else a.socks.checked(4, "socks"),
            if ("headwear" in suit.covers) 0 else a.variantFor("headwear").checked(4, "headwear"), suit.hoodFront, suit.hoodBack,
            if (coverTop) suit.upperArmPart else null, if (coverTop) suit.forearmPart else null,
            if (coverBottom) suit.thighPart else null, if (coverBottom) suit.shinPart else null, suit.jointPart)
    }

    private fun arm(canvas: Canvas, limb: Limb, clothes: Clothes) {
        val elbowPalette = if (clothes.upper >= .99f && clothes.fore > .01f) clothes.topPalette else null
        // Fill underneath BOTH open limb ends instead of displaying a round cap on top.
        draw(canvas, clothes.jointPart?.let { art.part(it) } ?: art.coloredPart("joint_skin", elbowPalette), limb.middle)
        val upper = clothes.upperArmPart?.let { art.part(it) }
            ?: art.coloredPart("skin_upper_arm", clothes.topPalette, end = clothes.upper)
        bone(canvas, upper, limb.root, limb.middle, trimEnd = 3f)
        // Fabric forearm parts have no hand. Preserve the original fist under the sleeve.
        val forearm = if (clothes.forearmPart != null) art.part("skin_forearm") else
            art.coloredPart("skin_forearm", clothes.topPalette, end = clothes.fore, preserveBeyondTip = true)
        bone(canvas, forearm, limb.middle, limb.tip, trimStart = 4f)
        clothes.forearmPart?.let { bone(canvas, art.part(it), limb.middle, limb.tip, trimStart = 4f) }
    }

    private fun leg(canvas: Canvas, limb: Limb, clothes: Clothes) {
        val kneePalette = if (clothes.thigh >= .99f && clothes.shin > .01f) clothes.bottomPalette else null
        draw(canvas, clothes.jointPart?.let { art.part(it) } ?: art.coloredPart("joint_skin", kneePalette), limb.middle)
        val thigh = clothes.thighPart?.let { art.part(it) }
            ?: art.coloredPart("skin_thigh", clothes.bottomPalette, end = clothes.thigh)
        val shin = clothes.shinPart?.let { art.part(it) }
            ?: art.coloredPart("skin_shin", clothes.bottomPalette, end = clothes.shin)
        bone(canvas, thigh, limb.root, limb.middle, trimEnd = 3f)
        bone(canvas, shin, limb.middle, limb.tip, trimStart = 4f)
        if (clothes.socks != 3) {
            val start = when (clothes.socks) { 0 -> .83f; 1 -> .54f; else -> .07f }
            bone(canvas, art.coloredPart("skin_shin", "socks", start = start, overlay = true), limb.middle, limb.tip, trimStart = 4f)
        }
        val foot = art.coloredPart(clothes.shoe.part, clothes.shoe.palette)
        val checkpoint = canvas.save()
        canvas.translate(limb.tip.x, limb.tip.y)
        canvas.rotate(limb.footAngle)
        canvas.drawBitmap(foot.bitmap, -foot.pivot.x, -foot.pivot.y, paint)
        canvas.restoreToCount(checkpoint)
    }

    private fun draw(canvas: Canvas, part: AvatarArtRepository.Part, at: P) {
        canvas.drawBitmap(part.bitmap, at.x - part.pivot.x, at.y - part.pivot.y, paint)
    }

    private fun bone(canvas: Canvas, part: AvatarArtRepository.Part, from: P, to: P, trimStart: Float = 0f, trimEnd: Float = 0f) {
        val tip = requireNotNull(part.tip) { "Missing pivot->tip landmarks for avatar bone ${part.id}" }
        val sourceX = tip.x - part.pivot.x
        val sourceY = tip.y - part.pivot.y
        val sourceLength = hypot(sourceX, sourceY)
        val destinationLength = hypot(to.x - from.x, to.y - from.y)
        require(sourceLength > 1f && kotlin.math.abs(sourceLength - destinationLength) <= 2f) {
            "Avatar bone ${part.id} must be normalized to rig length: source=$sourceLength target=$destinationLength"
        }
        val degrees = ((atan2(to.y - from.y, to.x - from.x) - atan2(sourceY, sourceX)) * 180 / PI).toFloat()
        val checkpoint = canvas.save()
        canvas.translate(from.x, from.y)
        canvas.rotate(degrees)
        canvas.translate(-part.pivot.x, -part.pivot.y)
        if (trimStart > 0f || trimEnd > 0f) {
            require(kotlin.math.abs(sourceX) <= .1f && sourceY > 0f) { "Trimmed avatar part ${part.id} must have a vertical pivot->tip axis" }
            val top = if (trimStart > 0f) part.pivot.y + trimStart else -10000f
            val bottom = if (trimEnd > 0f) tip.y - trimEnd else 10000f
            canvas.clipRect(-10000f, top, 10000f, bottom)
        }
        canvas.drawBitmap(part.bitmap, 0f, 0f, paint)
        canvas.restoreToCount(checkpoint)
    }

    private fun pose(frame: Int, running: Boolean): Pose {
        val dy = if (running) bob[frame] else 0f
        val neck = P(139f, 183f + dy)
        val hip = P(139f, 259f + dy)
        fun arm(near: Boolean): Limb {
            val root = if (near) P(112f, 194f + dy) else P(147f, 191f + dy)
            val phase = if (near) frame else (frame + 4) % 8
            val upper = if (running) (100 + 45 * cos(2 * PI * phase / 8)).toFloat() else if (near) 106f else 78f
            val elbowFlex = if (running) (90 + 8 * sin(2 * PI * phase / 8)).toFloat() else 16f
            val fore = upper - elbowFlex
            val middle = root + vector(upper, 40f)
            return Limb(root, middle, middle + vector(fore, 34f))
        }
        fun leg(near: Boolean): Limb {
            val root = if (near) P(132f, 260f + dy) else P(146f, 258f + dy)
            val phase = if (near) frame else (frame + 4) % 8
            val offset = if (running) ankleOffsets[phase] else if (near) P(-7f, 101f) else P(10f, 98f)
            val ankle = root + offset
            val distance = hypot(offset.x, offset.y).coerceIn(7.01f, 106.99f)
            val targetAngle = atan2(offset.y, offset.x)
            val kneeAngle = targetAngle - acos(((50f * 50f + distance * distance - 57f * 57f) / (100f * distance)).coerceIn(-1f, 1f))
            val knee = root + P((cos(kneeAngle) * 50).toFloat(), (sin(kneeAngle) * 50).toFloat())
            val footAngle = if (running) floatArrayOf(-10f,-4f,18f,48f,62f,34f,6f,-15f)[phase] else 0f
            return Limb(root, knee, ankle, footAngle)
        }
        return Pose(neck, hip, arm(true), arm(false), leg(true), leg(false))
    }

    private fun vector(degrees: Float, length: Float): P {
        val radians = degrees * PI / 180
        return P((cos(radians) * length).toFloat(), (sin(radians) * length).toFloat())
    }

    // Only small wearables are code-drawn; all visible human/body/garment art is source PNG.
    private fun poly(canvas: Canvas, color: Int, vararg coordinates: Float) {
        val path = Path(); path.moveTo(coordinates[0], coordinates[1])
        var index = 2
        while (index < coordinates.size) { path.lineTo(coordinates[index], coordinates[index + 1]); index += 2 }
        path.close(); paint.color = color; canvas.drawPath(path, paint)
    }
    private fun rect(canvas: Canvas, color: Int, x: Float, y: Float, width: Float, height: Float) {
        paint.color = color; canvas.drawRect(x, y, x + width, y + height, paint)
    }
    private fun ellipse(canvas: Canvas, color: Int, x: Float, y: Float, width: Float, height: Float) {
        paint.color = color; canvas.drawOval(RectF(x, y, x + width, y + height), paint)
    }
    private fun equipmentColor(variant: Int) = equipment[(variant - 1).coerceIn(0, 2)]

    private fun headwear(canvas: Canvas, kind: Int, head: P) {
        if (kind == 0) return
        val x = head.x; val y = head.y
        if (kind == 1) {
            poly(canvas, INK, x-58,y-83, x-54,y-108, x-35,y-122, x-5,y-126, x+29,y-113, x+48,y-86, x+67,y-78, x+73,y-67, x+57,y-63, x+33,y-75)
            poly(canvas, CREAM, x-53,y-85, x-49,y-105, x-32,y-117, x-5,y-121, x+26,y-108, x+40,y-87)
            poly(canvas, 0xffb5ccd0.toInt(), x-53,y-87, x-20,y-96, x+32,y-95, x+40,y-85, x-52,y-78)
            poly(canvas, 0xff6d919f.toInt(), x-52,y-78, x+39,y-85, x+65,y-76, x+66,y-70, x+40,y-78, x-52,y-73)
        } else if (kind == 2) {
            poly(canvas, INK, x-55,y-83, x+39,y-92, x+45,y-81, x+66,y-76, x+72,y-68, x+57,y-63, x+34,y-75, x-54,y-70)
            poly(canvas, CREAM, x-51,y-80, x+36,y-87, x+38,y-80, x-50,y-74)
            poly(canvas, 0xffa2bec8.toInt(), x+36,y-80, x+64,y-73, x+65,y-69, x+37,y-76)
        } else {
            poly(canvas, INK, x-56,y-82, x+37,y-90, x+42,y-83, x+38,y-71, x-53,y-66)
            poly(canvas, 0xff76cbb7.toInt(), x-52,y-79, x+34,y-86, x+36,y-80, x+34,y-75, x-50,y-70)
            rect(canvas, 0xffbae8d1.toInt(), x-42,y-78, 38f, 3f)
        }
    }

    private fun ear(canvas: Canvas, kind: Int, head: P) {
        if (kind == 0) return
        val x = head.x - 40f; val y = head.y - 48f
        if (kind == 2) {
            ellipse(canvas, INK, x-3,y-5,12f,15f); ellipse(canvas, CREAM,x,y-2,6f,9f)
            rect(canvas,CREAM,x+2,y+5,4f,13f)
        } else {
            poly(canvas,INK,x-7,y-16,x-12,y-8,x-10,y+16,x-2,y+22,x+8,y+17,x+7,y+7,x-1,y+3,x-3,y-8)
            poly(canvas,equipmentColor(1),x-6,y-12,x-8,y-7,x-6,y+13,x,y+17,x+4,y+14,x+3,y+9,x-2,y+7)
            if(kind==3){ellipse(canvas,INK,x-4,y-3,18f,26f);ellipse(canvas,0xff7ba2b2.toInt(),x,y+1,10f,18f)}
        }
    }

    private fun glasses(canvas: Canvas, kind: Int, head: P) {
        if (kind == 0) return
        val x = head.x; val y = head.y - 56f; val c = equipmentColor(kind)
        poly(canvas,INK,x-39,y-3,x+65,y-8,x+65,y+8,x+57,y+20,x+36,y+18,x+24,y+5,x+16,y+5,x+9,y+22,x-14,y+18,x-22,y+1)
        poly(canvas,c,x-17,y,x+12,y-1,x+9,y+12,x+5,y+17,x-11,y+13)
        poly(canvas,c,x+31,y-2,x+60,y-4,x+58,y+8,x+54,y+14,x+38,y+12)
        rect(canvas,CREAM,x-10,y+1,14f,3f);rect(canvas,CREAM,x+39,y-1,13f,3f)
    }

    private fun watch(canvas: Canvas, kind: Int, arm: Limb) {
        if (kind == 0) return
        val dx = arm.tip.x-arm.middle.x; val dy = arm.tip.y-arm.middle.y
        val length = hypot(dx,dy).coerceAtLeast(1f)
        val x=arm.tip.x-dx/length*7; val y=arm.tip.y-dy/length*7
        val checkpoint=canvas.save();canvas.translate(x,y);canvas.rotate((atan2(dy,dx)*180/PI).toFloat())
        rect(canvas,INK,-7f,-11f,14f,22f);rect(canvas,equipmentColor(kind),-4f,-9f,8f,18f)
        rect(canvas,INK,-9f,-8f,18f,16f);rect(canvas,CREAM,-6f,-5f,12f,10f)
        rect(canvas,0xff3f7083.toInt(),-3f,-3f,6f,6f);canvas.restoreToCount(checkpoint)
    }

    private fun belt(canvas: Canvas, kind: Int, pose: Pose) {
        if (kind == 0) return
        val x=pose.hip.x;val y=pose.hip.y-6;val c=equipmentColor(kind)
        poly(canvas,INK,x-36,y-5,x+31,y-3,x+34,y+12,x-31,y+11,x-37,y+4)
        poly(canvas,c,x-32,y-1,x+27,y+1,x+28,y+8,x-30,y+7)
        rect(canvas,INK,x-14,y-3,29f,15f);rect(canvas,0xff3c5664.toInt(),x-11,y,23f,9f)
        rect(canvas,CREAM,x-8,y+1,15f,2f);rect(canvas,0xffa6c3c7.toInt(),x+6,y+3,2f,5f)
    }

    private fun hydration(canvas: Canvas, kind: Int, pose: Pose) {
        if (kind == 0) return
        val c=if(kind==1)0xff39556c.toInt() else equipmentColor(kind)
        val shoulder=pose.nearArm.root;val far=pose.farArm.root
        for(root in arrayOf(shoulder,far)){
            poly(canvas,INK,root.x-8,root.y-7,root.x+9,root.y-6,root.x+15,root.y+48,root.x,root.y+55,root.x-10,root.y+43)
            poly(canvas,c,root.x-4,root.y-3,root.x+6,root.y-2,root.x+10,root.y+44,root.x+1,root.y+50,root.x-6,root.y+40)
            rect(canvas,INK,root.x-5,root.y+15,14f,29f);rect(canvas,0xff8fb8ca.toInt(),root.x-2,root.y+17,9f,24f)
            rect(canvas,CREAM,root.x,root.y+9,5f,13f);rect(canvas,0xffcde6e7.toInt(),root.x-2,root.y+25,9f,9f)
        }
        rect(canvas,INK,shoulder.x+7,shoulder.y+19,far.x-shoulder.x-8,6f)
        rect(canvas,0xffb4c8cb.toInt(),shoulder.x+10,shoulder.y+21,far.x-shoulder.x-13,2f)
    }

    // Persisted/remote integers may come from older or newer catalogues. Normalize
    // artwork input only, matching the previous renderer; do not grant ownership.
    private fun Int.checked(count: Int, @Suppress("UNUSED_PARAMETER") label: String): Int = Math.floorMod(this, count)
}

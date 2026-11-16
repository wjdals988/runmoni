package com.runmoni.app.avatar

import com.runmoni.app.domain.AvatarCatalogue
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.variantFor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Fixed-frame equipment over the approved sheet. No body rig, pose synthesis or source writes.
 * Anchors were authored against f9035f29... cells (256x384), not v0.4 part coordinates.
 * Readiness here is an implemented pixel recipe; visual acceptance remains a separate review.
 */
object AvatarEquipmentLayers {
    const val VERSION = "approved-equipment-v3-perspective"
    private const val WIDTH = 256
    private const val HEIGHT = 384
    private const val INK = 0x10172c
    private const val CREAM = 0xf1f7ee
    data class Point(val x: Int, val y: Int)
    data class Anchors(val eye: Point, val ear: Point, val nearShoulder: Point,
        val nearElbow: Point, val nearWrist: Point, val waist: Point)

    // Each row is authored independently. Eye coordinates refer to the near eye's
    // white source region; wrist/elbow positions follow the actual foreground arm.
    private val male = arrayOf(
        Anchors(Point(150,145),Point(110,154),Point(119,195),Point(84,219),Point(74,236),Point(142,252)),
        Anchors(Point(152,150),Point(112,159),Point(118,200),Point(101,225),Point(132,246),Point(148,258)),
        Anchors(Point(144,145),Point(104,154),Point(112,196),Point(91,219),Point(91,239),Point(140,257)),
        Anchors(Point(148,144),Point(108,153),Point(116,194),Point(81,219),Point(71,236),Point(141,252)),
        Anchors(Point(156,144),Point(116,153),Point(126,194),Point(92,217),Point(82,232),Point(150,252)),
        Anchors(Point(144,150),Point(104,159),Point(108,200),Point(92,226),Point(120,248),Point(138,260)),
        Anchors(Point(146,150),Point(106,159),Point(106,200),Point(79,222),Point(84,241),Point(140,258)),
        Anchors(Point(142,144),Point(102,153),Point(101,192),Point(66,215),Point(58,232),Point(130,251)),
    )
    private val female = arrayOf(
        Anchors(Point(156,129),Point(118,136),Point(106,188),Point(84,207),Point(76,222),Point(142,233)),
        Anchors(Point(171,132),Point(133,139),Point(125,195),Point(115,216),Point(143,232),Point(157,239)),
        Anchors(Point(159,131),Point(121,138),Point(122,188),Point(109,207),Point(119,225),Point(149,235)),
        Anchors(Point(155,131),Point(117,138),Point(105,186),Point(83,206),Point(75,222),Point(141,233)),
        Anchors(Point(156,130),Point(118,137),Point(109,187),Point(88,208),Point(81,223),Point(145,233)),
        Anchors(Point(174,131),Point(136,138),Point(125,188),Point(116,215),Point(137,230),Point(159,239)),
        Anchors(Point(156,130),Point(118,137),Point(113,190),Point(99,211),Point(104,231),Point(147,238)),
        Anchors(Point(155,130),Point(117,137),Point(104,187),Point(83,206),Point(74,222),Point(140,233)),
    )
    fun anchors(frame: Int, gender: AvatarGender): Anchors =
        (if (gender == AvatarGender.MALE) male else female)[Math.floorMod(frame, 8)]

    @Suppress("UNUSED_PARAMETER")
    fun supports(slot: String, variant: Int, gender: AvatarGender): Boolean = when (slot) {
        "headwear" -> variant in 0..3
        "eyewear", "wrist", "ear", "waist", "hydration" -> variant in 0..3
        else -> false
    }

    /** Returns a copy. originalPixels and fabricMask must be the same approved source cell.
     * Body gear is clipped to source fabric labels, preserving foreground hands/arms.
     * Other equipment preserves incoming composed alpha and never enlarges the body silhouette.
     * A null hand mask is for older callers; production supplies the pinned labels.
     */
    @JvmOverloads
    fun render(pixels: IntArray, originalPixels: IntArray, fabricMask: IntArray,
        frame: Int, gender: AvatarGender, style: AvatarStyle, protectedHands: IntArray? = null): IntArray {
        require(pixels.size == WIDTH * HEIGHT && originalPixels.size == pixels.size && fabricMask.size == pixels.size)
        require(protectedHands == null || protectedHands.size == pixels.size)
        val headKind = if ("headwear" in AvatarCatalogue.coveredSlots(style.costume)) null else when (style.variantFor("headwear")) {
            1 -> AvatarHeadwearLayers.Kind.CAP
            2 -> AvatarHeadwearLayers.Kind.VISOR
            3 -> AvatarHeadwearLayers.Kind.HEADBAND
            else -> null
        }
        val out = if (headKind == null) pixels.clone() else
            AvatarHeadwearLayers.render(pixels, originalPixels, frame, gender, headKind)
        val a = anchors(frame, gender)
        // Pinned hand labels override cloth labels when the forward hand crosses
        // a belt or bottle. Wrist equipment remains a separate intentional layer.
        val body = Raster(out, originalPixels, fabricMask, fabricOnly = true, protectedHands = protectedHands)
        if (style.hydration in 1..3) vest(body, a, style.hydration, gender)
        if (style.waist in 1..3) belt(body, a, style.waist)
        val gear = Raster(out, originalPixels, fabricMask, fabricOnly = false)
        if (style.wrist in 1..3) watch(gear, a, style.wrist)
        if (style.variantFor("ear") in 1..3) ear(gear, a, style.variantFor("ear"))
        if (style.eyewear in 1..3) glasses(gear, a, style.eyewear)
        return out
    }

    // The same five material stops as clothes: equipment no longer uses a
    // second, unrelated mint/coral/blue palette. Small clusters describe seams,
    // glass and buckles; the original face/body pixels are not resampled.
    private fun palette(kind: Int): IntArray {
        val color = when (kind) { 1 -> 1; 2 -> 2; else -> 3 }
        return intArrayOf(0, 64, 128, 192, 255).map { GarmentPalette.shade(color, it) }.toIntArray()
    }
    private fun glasses(r: Raster, a: Anchors, kind: Int) {
        val x=a.eye.x; val y=a.eye.y; val c=palette(kind)
        // Keep shared eye/ear anchors fixed. On the original 3/4 face the far
        // lens sits closer to the nose and is narrower than the old +67px frame. Frames
        // have a two-pixel bevel, a darker lens field and one readable reflection.
        r.line(INK,a.ear.x-1,a.ear.y-6,x-7,y-8,3)
        r.line(c[1],a.ear.x,a.ear.y-7,x-7,y-9,1)
        r.poly(INK,x-9,y-12,x+23,y-12,x+27,y-7,x+24,y+10,x+18,y+13,x-3,y+11,x-8,y+5)
        r.poly(c[0],x-6,y-9,x+21,y-9,x+24,y-6,x+21,y+8,x+17,y+10,x-1,y+8,x-5,y+3)
        r.poly(c[1],x-5,y-8,x+19,y-8,x+15,y-2,x+4,y+4,x-4,y+3)
        r.poly(c[2],x-4,y-8,x+8,y-8,x+1,y-1,x-4,y+2)
        r.line(CREAM,x-2,y-7,x+4,y-7,2)
        r.line(c[3],x+2,y+7,x+16,y+8,1)
        r.poly(INK,x+36,y-11,x+54,y-10,x+58,y-5,x+55,y+9,x+50,y+11,x+37,y+8)
        r.poly(c[0],x+39,y-8,x+52,y-7,x+55,y-4,x+52,y+7,x+49,y+8,x+40,y+6)
        r.poly(c[1],x+40,y-7,x+51,y-6,x+48,y,x+40,y+3)
        r.poly(c[2],x+40,y-7,x+46,y-6,x+42,y-1,x+40,y)
        r.rect(CREAM,x+40,y-6,3,2)
        r.line(INK,x+24,y-6,x+37,y-5,3)
        r.line(c[1],x+25,y-7,x+36,y-6,1)
    }
    private fun watch(r: Raster, a: Anchors, kind: Int) {
        val c=palette(kind); val dx=a.nearWrist.x-a.nearElbow.x; val dy=a.nearWrist.y-a.nearElbow.y
        val length=hypot(dx.toDouble(),dy.toDouble()).coerceAtLeast(1.0)
        val ux=dx/length; val uy=dy/length; val vx=-uy; val vy=ux
        val cx=a.nearWrist.x-ux*5; val cy=a.nearWrist.y-uy*5
        fun shape(color: Int, vararg uv: Int) {
            fun px(u:Int,v:Int)=(cx+ux*u+vx*v).roundToInt()
            fun py(u:Int,v:Int)=(cy+uy*u+vy*v).roundToInt()
            val xy=IntArray(uv.size)
            for(i in uv.indices step 2){xy[i]=px(uv[i],uv[i+1]);xy[i+1]=py(uv[i],uv[i+1])}
            r.poly(color,*xy)
        }
        shape(INK,-4,-12,4,-12,4,12,-4,12)
        shape(c[0],-2,-10,2,-10,2,10,-2,10)
        shape(c[2],-2,-10,0,-10,0,9,-2,9)
        shape(INK,-4,-8,4,-8,8,-4,8,4,4,8,-4,8,-8,4,-8,-4)
        shape(c[3],-3,-6,3,-6,6,-3,6,3,3,6,-3,6,-6,3,-6,-3)
        shape(0x172b39,-2,-4,3,-4,4,-2,4,3,2,4,-3,4,-4,2,-4,-2)
        shape(c[4],-4,-4,-1,-5,2,-5,2,-4,-2,-3,-4,-1)
        shape(c[2],-2,0,1,0,1,2,-2,2)
        shape(CREAM,1,-1,3,-1,3,1,1,1)
        shape(c[4],-1,9,1,9,1,11,-1,11)
    }
    private fun ear(r: Raster, a: Anchors, kind: Int) {
        val x=a.ear.x;val y=a.ear.y;val c=palette(1)
        when(kind) {
            1 -> {
                // An open upper/back hook ends in a short pod in front of the
                // ear. Do not close a bright loop below the lobe like an earring.
                r.line(INK,x-3,y-14,x-9,y-10,3)
                r.line(INK,x-9,y-10,x-9,y-3,3)
                r.line(INK,x-9,y-3,x-4,y+3,3)
                r.line(INK,x-4,y+3,x+5,y+3,3)
                r.line(c[1],x-3,y-14,x-8,y-10,1)
                r.line(c[2],x-8,y-9,x-8,y-4,1)
                r.poly(INK,x+3,y-4,x+10,y-5,x+14,y-1,x+13,y+5,x+6,y+6,x+3,y+3)
                r.poly(c[0],x+5,y-2,x+9,y-3,x+12,y,x+11,y+3,x+7,y+4,x+5,y+2)
                r.poly(c[2],x+5,y-2,x+9,y-3,x+11,y-1,x+8,y,x+5,y)
                r.rect(CREAM,x+6,y-2,3,1);r.rect(INK,x+10,y+1,2,2)
            }
            2 -> {
                r.poly(INK,x-4,y-5,x+3,y-6,x+7,y-2,x+6,y+4,x+3,y+6,x-3,y+4)
                r.poly(0x8caaa9,x-2,y-3,x+2,y-4,x+5,y-1,x+3,y+3,x-1,y+2)
                r.poly(CREAM,x-1,y-3,x+2,y-3,x+3,y-1,x+1,y+1,x-1,y)
                r.poly(INK,x+1,y+3,x+6,y+2,x+5,y+12,x+1,y+12)
                r.poly(0xc5d8cf,x+2,y+4,x+4,y+4,x+3,y+10,x+2,y+10)
                r.rect(INK,x+3,y-1,2,2)
            }
            3 -> {
                r.line(INK,x-6,y-6,x-11,y-16,4);r.line(INK,x-11,y-16,x-6,y-26,4)
                r.line(0x66878d,x-7,y-7,x-9,y-16,2);r.line(0x66878d,x-9,y-16,x-5,y-25,2)
                r.poly(INK,x-5,y-10,x+4,y-10,x+9,y-5,x+9,y+8,x+4,y+13,x-5,y+11,x-8,y+6,x-8,y-5)
                r.poly(0x29424b,x-3,y-7,x+3,y-7,x+6,y-3,x+6,y+7,x+2,y+10,x-3,y+8,x-5,y+4,x-5,y-3)
                r.poly(0x759a9b,x-2,y-5,x+1,y-5,x+3,y-2,x+3,y+6,x,y+8,x-2,y+6)
                r.line(CREAM,x-1,y-4,x-1,y+2,2)
                r.line(c[0],x+4,y-2,x+4,y+6,1)
            }
        }
    }
    private fun belt(r: Raster, a: Anchors, kind: Int) {
        val x=a.waist.x;val y=a.waist.y;val c=palette(kind)
        // The near pouch has a curved front and a compressed darker far edge.
        // One zipper and broad folds target the whole runner's small display size.
        r.poly(INK,x-37,y-7,x+31,y-8,x+37,y-3,x+35,y+3,x-35,y+4)
        r.poly(c[0],x-34,y-4,x+30,y-5,x+33,y-2,x+32,y+1,x-33,y+1)
        r.poly(c[2],x-33,y-5,x-12,y-5,x-11,y-1,x-33,y)
        r.poly(INK,x-13,y-11,x+14,y-12,x+22,y-7,x+23,y+2,x+17,y+8,x-7,y+9,x-14,y+4)
        r.poly(c[0],x-10,y-8,x+13,y-9,x+19,y-5,x+20,y+1,x+15,y+5,x-6,y+6,x-11,y+2)
        r.poly(c[1],x-9,y-7,x+10,y-8,x+16,y-4,x+15,y+3,x-6,y+4,x-10,y+1)
        r.poly(c[2],x-8,y-7,x+9,y-7,x+12,y-4,x+4,y-2,x-8,y)
        r.line(c[3],x-6,y-6,x+11,y-6,2)
        r.line(INK,x-6,y-3,x+12,y-3,2)
        r.rect(c[4],x+10,y-5,3,4)
        r.line(c[0],x+14,y+1,x+16,y+2,2)
    }
    private fun vest(r: Raster, a: Anchors, kind: Int, gender: AvatarGender) {
        val x=a.waist.x;val y=a.waist.y;val c=palette(kind)
        // The chest plane follows the original neckline, camera-near shoulder
        // and waist. The far strap is narrower/darker; it is not a mirrored UI
        // packet. Pinned hands and source cloth labels supply foreground depth.
        val topY=a.eye.y+if(gender==AvatarGender.MALE)44 else 41
        val topLeft=a.nearShoulder.x+if(gender==AvatarGender.MALE)8 else 14
        val topRight=x+if(gender==AvatarGender.MALE)33 else 30
        fun point(u:Float,v:Float):Point {
            val left=topLeft+(x-25-topLeft)*v;val right=topRight+(x+30-topRight)*v
            return Point((left+(right-left)*u).roundToInt(),(topY+(y-8-topY)*v+u*3*(1-v)).roundToInt())
        }
        fun panel(color:Int,vararg uv:Float) {
            val xy=IntArray(uv.size)
            for(i in uv.indices step 2){val p=point(uv[i],uv[i+1]);xy[i]=p.x;xy[i+1]=p.y}
            r.poly(color,*xy)
        }
        panel(INK,-.02f,.01f,.34f,0f,.40f,.82f,.30f,1f,.02f,.98f,-.08f,.78f)
        panel(c[0],.04f,.04f,.29f,.03f,.34f,.79f,.26f,.94f,.06f,.91f,-.02f,.75f)
        panel(c[2],.07f,.05f,.22f,.05f,.28f,.7f,.19f,.84f,.07f,.80f)
        panel(c[3],.07f,.08f,.12f,.07f,.15f,.66f,.10f,.77f)
        panel(INK,.77f,.04f,.98f,.12f,1.03f,.86f,.90f,.98f,.75f,.88f)
        panel(c[0],.82f,.09f,.93f,.15f,.97f,.85f,.89f,.91f,.81f,.84f)
        panel(c[1],.83f,.12f,.88f,.15f,.91f,.77f,.86f,.83f)
        // A lower webbing wrap joins the panels; bottle pockets sit on top of
        // it with a small light neck and a single broad water reflection.
        panel(INK,.21f,.79f,.88f,.83f,.91f,.99f,.18f,.94f)
        panel(c[1],.25f,.83f,.85f,.87f,.86f,.94f,.22f,.90f)
        panel(c[2],.25f,.83f,.54f,.85f,.54f,.90f,.23f,.88f)
        panel(INK,.07f,.40f,.26f,.37f,.33f,.47f,.31f,.91f,.10f,.94f,.04f,.83f)
        panel(c[0],.11f,.45f,.23f,.43f,.27f,.49f,.26f,.86f,.12f,.89f,.09f,.81f)
        panel(0x668c91,.12f,.48f,.20f,.45f,.24f,.51f,.23f,.77f,.12f,.81f)
        panel(0xc2d8cf,.12f,.50f,.15f,.48f,.17f,.73f,.13f,.77f)
        panel(INK,.13f,.32f,.21f,.31f,.23f,.43f,.13f,.45f)
        panel(CREAM,.15f,.33f,.19f,.33f,.20f,.41f,.15f,.42f)
        panel(INK,.79f,.47f,.94f,.48f,.98f,.80f,.88f,.89f,.78f,.81f)
        panel(0x38565e,.83f,.51f,.91f,.52f,.93f,.78f,.87f,.83f,.82f,.78f)
        panel(0x87aaa8,.84f,.53f,.87f,.54f,.88f,.76f,.85f,.79f)
        panel(INK,.83f,.40f,.90f,.41f,.92f,.51f,.83f,.51f)
        panel(0xc2d8cf,.85f,.41f,.88f,.42f,.89f,.49f,.85f,.49f)
        val near=point(.29f,.28f);val far=point(.82f,.31f)
        r.line(INK,near.x,near.y,far.x,far.y,3)
        r.line(c[1],near.x,near.y-1,far.x,far.y-1,1)
        val buckle=point(.56f,.30f)
        r.rect(INK,buckle.x-3,buckle.y-2,6,5);r.rect(c[3],buckle.x-1,buckle.y-1,2,2)
    }
    private class Raster(val out:IntArray,val original:IntArray,val mask:IntArray,val fabricOnly:Boolean,
        val protectedHands:IntArray? = null) {
        private fun put(x:Int,y:Int,color:Int) {
            if(x !in 0 until WIDTH || y !in 0 until HEIGHT)return
            val i=y*WIDTH+x
            // Source-background fringe is not an attachment surface. A later
            // hat/hood may occupy that background; gear must neither float on
            // it nor replace its new clothing alpha with the old fringe alpha.
            if((original[i] ushr 24)<=16)return
            if(fabricOnly && ((mask[i] ushr 24)!=255 || ((mask[i] ushr 16) and 255) !in 1..2))return
            if(fabricOnly && protectedHands!=null && protectedHands[i]!=0)return
            val alpha=out[i] ushr 24
            out[i]=(alpha shl 24) or (color and 0xffffff)
        }
        fun rect(c:Int,x:Int,y:Int,w:Int,h:Int) {
            for(py in max(0,y) until min(HEIGHT,y+h))for(px in max(0,x) until min(WIDTH,x+w))put(px,py,c)
        }
        fun line(c:Int,x0:Int,y0:Int,x1:Int,y1:Int,width:Int) {
            var x=x0;var y=y0;val dx=kotlin.math.abs(x1-x0);val dy=-kotlin.math.abs(y1-y0)
            val sx=if(x0<x1)1 else -1;val sy=if(y0<y1)1 else -1;var e=dx+dy
            while(true){rect(c,x-width/2,y-width/2,width,width);if(x==x1 && y==y1)break
                val e2=2*e;if(e2>=dy){e+=dy;x+=sx};if(e2<=dx){e+=dx;y+=sy}}
        }
        fun poly(c:Int,vararg xy:Int) {
            require(xy.size>=6 && xy.size%2==0)
            val xs=xy.indices.filter{it%2==0}.map{xy[it]};val ys=xy.indices.filter{it%2==1}.map{xy[it]}
            for(y in max(0,ys.min())..min(HEIGHT-1,ys.max()))for(x in max(0,xs.min())..min(WIDTH-1,xs.max())) {
                var inside=false;var j=xs.lastIndex
                for(i in xs.indices){if((ys[i]>y)!=(ys[j]>y) && x<(xs[j]-xs[i]).toDouble()*(y-ys[i])/(ys[j]-ys[i])+xs[i])inside=!inside;j=i}
                if(inside)put(x,y,c)
            }
        }
    }
}

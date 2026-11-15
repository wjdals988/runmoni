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
    const val VERSION = "approved-equipment-v2-materials"
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
        "headwear" -> variant == 0 || variant == 3
        "eyewear", "wrist", "ear", "waist", "hydration" -> variant in 0..3
        else -> false
    }

    /** Returns a copy. originalPixels and fabricMask must be the same approved source cell.
     * Body gear is clipped to source fabric labels, preserving foreground hands/arms.
     * Other equipment preserves the source alpha and never enlarges the body silhouette.
     */
    fun render(pixels: IntArray, originalPixels: IntArray, fabricMask: IntArray,
        frame: Int, gender: AvatarGender, style: AvatarStyle): IntArray {
        require(pixels.size == WIDTH * HEIGHT && originalPixels.size == pixels.size && fabricMask.size == pixels.size)
        val out = pixels.clone()
        val a = anchors(frame, gender)
        val body = Raster(out, originalPixels, fabricMask, fabricOnly = true)
        if (style.hydration in 1..3) vest(body, a, style.hydration, gender)
        if (style.waist in 1..3) belt(body, a, style.waist)
        val gear = Raster(out, originalPixels, fabricMask, fabricOnly = false)
        if (style.wrist in 1..3) watch(gear, a, style.wrist)
        if (style.variantFor("headwear") == 3 && "headwear" !in AvatarCatalogue.coveredSlots(style.costume)) band(gear, a)
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
    private fun band(r: Raster, a: Anchors) {
        val x=a.eye.x; val y=a.eye.y+24; val c=palette(1)
        r.poly(INK,x-56,y-40,x+20,y-72,x+48,y-67,x+46,y-52,x+20,y-56,x-49,y-25)
        r.poly(c[1],x-52,y-39,x+21,y-68,x+44,y-64,x+43,y-56,x+20,y-60,x-49,y-29)
        r.poly(c[2],x-51,y-37,x+21,y-66,x+42,y-63,x+41,y-60,x+20,y-63,x-49,y-33)
        r.line(c[3],x-40,y-38,x-13,y-49,1)
        r.line(c[1],x+26,y-65,x+36,y-63,2)
        r.line(c[4],x-37,y-39,x-28,y-43,1)
        r.line(c[0],x+22,y-56,x+42,y-52,1)
    }
    private fun glasses(r: Raster, a: Anchors, kind: Int) {
        val x=a.eye.x; val y=a.eye.y; val c=palette(kind)
        r.line(INK,a.ear.x-2,a.ear.y-7,x-7,y-10,4)
        r.line(c[1],a.ear.x-1,a.ear.y-8,x-7,y-11,2)
        r.poly(INK,x-11,y-14,x+24,y-14,x+27,y-9,x+24,y+11,x+17,y+16,x-4,y+13,x-10,y+5)
        r.poly(c[0],x-7,y-10,x+20,y-10,x+22,y-7,x+19,y+8,x+15,y+11,x-1,y+9,x-6,y+3)
        r.poly(c[2],x-5,y-8,x+18,y-8,x+17,y+1,x+3,y+6,x-3,y+2)
        r.poly(INK,x+42,y-14,x+65,y-15,x+67,y-9,x+64,y+8,x+57,y+13,x+44,y+9)
        r.poly(c[0],x+46,y-10,x+61,y-11,x+63,y-7,x+60,y+5,x+56,y+9,x+47,y+6)
        r.poly(c[2],x+47,y-8,x+60,y-9,x+59,y-1,x+49,y+3)
        r.line(INK,x+23,y-7,x+44,y-8,5)
        r.line(c[1],x+24,y-9,x+44,y-10,2)
        r.line(CREAM,x-2,y-7,x+10,y-7,2)
        r.line(CREAM,x+49,y-7,x+56,y-8,2)
        r.line(c[3],x-2,y+6,x+14,y+8,1)
        r.line(c[3],x+49,y+5,x+57,y+7,1)
        r.rect(c[4],x-3,y-6,3,2);r.rect(c[4],x+48,y-6,2,2)
    }
    private fun watch(r: Raster, a: Anchors, kind: Int) {
        val c=palette(kind); val dx=a.nearWrist.x-a.nearElbow.x; val dy=a.nearWrist.y-a.nearElbow.y
        val length=hypot(dx.toDouble(),dy.toDouble()).coerceAtLeast(1.0)
        val ux=dx/length; val uy=dy/length; val vx=-uy; val vy=ux
        val cx=a.nearWrist.x-ux*3; val cy=a.nearWrist.y-uy*3
        fun box(color: Int, u0: Int, v0: Int, u1: Int, v1: Int) {
            fun px(u:Int,v:Int)=(cx+ux*u+vx*v).roundToInt()
            fun py(u:Int,v:Int)=(cy+uy*u+vy*v).roundToInt()
            r.poly(color,px(u0,v0),py(u0,v0),px(u1,v0),py(u1,v0),px(u1,v1),py(u1,v1),px(u0,v1),py(u0,v1))
        }
        box(INK,-4,-11,4,11);box(c[1],-2,-9,2,9)
        box(INK,-7,-7,7,7);box(c[3],-5,-5,5,5)
        box(c[0],-3,-3,3,3);box(c[2],-2,-2,1,1)
        box(c[4],-4,-4,-1,-3);box(c[4],-4,-3,-3,-1)
        box(c[0],1,7,2,9);box(c[3],-2,-9,-1,-7)
    }
    private fun ear(r: Raster, a: Anchors, kind: Int) {
        val x=a.ear.x;val y=a.ear.y;val c=palette(1)
        when(kind) {
            1 -> {
                r.line(INK,x-6,y-10,x-10,y-4,4);r.line(INK,x-10,y-4,x-7,y+9,4)
                r.line(INK,x-7,y+9,x+1,y+11,4);r.line(c[2],x-6,y-9,x-8,y-3,2)
                r.line(c[1],x-8,y-3,x-5,y+7,2);r.rect(INK,x-1,y+2,7,8);r.rect(c[2],x+1,y+4,3,4)
            }
            2 -> {
                r.rect(INK,x,y-5,7,9);r.rect(CREAM,x+2,y-3,3,5)
                r.rect(INK,x+2,y+1,5,10);r.rect(0xc7dfe4,x+3,y+2,3,8);r.rect(CREAM,x+3,y+2,2,6)
            }
            3 -> {
                r.line(INK,x-7,y-4,x-12,y-15,4);r.line(INK,x-12,y-15,x-8,y-25,4)
                r.line(0x7898b0,x-7,y-5,x-10,y-15,2);r.line(0x7898b0,x-10,y-15,x-7,y-24,2)
                r.rect(INK,x-5,y-7,14,20);r.rect(0x40627b,x-2,y-4,8,14)
                r.rect(0x96bac6,x,y-2,4,9);r.line(CREAM,x+1,y-1,x+1,y+5,1)
            }
        }
    }
    private fun belt(r: Raster, a: Anchors, kind: Int) {
        val x=a.waist.x;val y=a.waist.y;val c=palette(kind)
        r.poly(INK,x-36,y-7,x+36,y-5,x+37,y+4,x-36,y+3)
        r.poly(c[1],x-34,y-4,x+34,y-3,x+34,y+1,x-34,y)
        r.poly(INK,x-10,y-7,x+15,y-6,x+17,y+5,x-9,y+6)
        r.poly(c[0],x-7,y-4,x+13,y-3,x+14,y+3,x-6,y+4)
        r.poly(c[1],x-6,y-3,x+12,y-2,x+12,y+1,x-5,y+2)
        r.line(c[3],x-5,y-4,x+10,y-3,1);r.rect(c[4],x+10,y-3,2,3)
        for(offset in -28..-16 step 6)r.line(c[3],x+offset,y-2,x+offset+2,y-2,1)
        r.line(c[0],x+19,y+1,x+30,y+1,1)
    }
    private fun vest(r: Raster, a: Anchors, kind: Int, gender: AvatarGender) {
        val x=a.waist.x;val y=a.waist.y;val c=palette(kind)
        // A bilinear cloth panel follows this cell's actual shoulder and waist,
        // rather than moving two rectangular bottles with only the hip.
        val topY=a.nearShoulder.y-if(gender==AvatarGender.MALE)5 else 3
        val topLeft=a.nearShoulder.x+4;val topRight=x+28
        fun point(u:Float,v:Float):Point {
            val left=topLeft+(x-25-topLeft)*v;val right=topRight+(x+30-topRight)*v
            return Point((left+(right-left)*u).roundToInt(),(topY+(y-8-topY)*v+u*3*(1-v)).roundToInt())
        }
        fun panel(color:Int,vararg uv:Float) {
            val xy=IntArray(uv.size)
            for(i in uv.indices step 2){val p=point(uv[i],uv[i+1]);xy[i]=p.x;xy[i+1]=p.y}
            r.poly(color,*xy)
        }
        panel(INK,0f,0f,.29f,0f,.29f,.88f,.05f,1f,-.05f,.88f)
        panel(c[1],.06f,.06f,.23f,.06f,.23f,.86f,.07f,.94f)
        panel(c[3],.07f,.08f,.10f,.08f,.10f,.86f,.07f,.9f)
        panel(INK,.69f,.02f,.96f,.08f,1.04f,.94f,.79f,1f,.65f,.86f)
        panel(c[1],.75f,.08f,.90f,.13f,.96f,.91f,.81f,.94f,.72f,.84f)
        panel(c[0],.88f,.14f,.91f,.14f,.97f,.9f,.93f,.91f)
        for(u in floatArrayOf(.1f,.77f)) {
            panel(INK,u,.44f,u+.19f,.44f,u+.2f,.87f,u,.89f)
            panel(c[0],u+.035f,.49f,u+.15f,.49f,u+.15f,.83f,u+.035f,.85f)
            panel(0x83b3c4,u+.055f,.5f,u+.13f,.5f,u+.13f,.75f,u+.055f,.78f)
            panel(CREAM,u+.05f,.38f,u+.12f,.38f,u+.12f,.49f,u+.05f,.49f)
            panel(c[4],u+.055f,.51f,u+.075f,.51f,u+.075f,.68f,u+.055f,.69f)
        }
        val near=point(.25f,.34f);val far=point(.76f,.35f)
        r.line(INK,near.x,near.y,far.x,far.y,4)
        r.line(c[3],near.x,near.y-1,far.x,far.y-1,1)
        val buckle=point(.52f,.35f)
        r.rect(INK,buckle.x-3,buckle.y-3,7,6);r.rect(c[4],buckle.x-1,buckle.y-1,3,2)
    }
    private class Raster(val out:IntArray,val original:IntArray,val mask:IntArray,val fabricOnly:Boolean) {
        private fun put(x:Int,y:Int,color:Int) {
            if(x !in 0 until WIDTH || y !in 0 until HEIGHT)return
            val i=y*WIDTH+x
            // Source-background fringe is not an attachment surface. A later
            // hat/hood may occupy that background; gear must neither float on
            // it nor replace its new clothing alpha with the old fringe alpha.
            if((original[i] ushr 24)<=16)return
            if(fabricOnly && ((mask[i] ushr 24)!=255 || ((mask[i] ushr 16) and 255) !in 1..2))return
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

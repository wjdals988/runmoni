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
    const val VERSION = "approved-equipment-v1"
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

    // Four authored material shades. IDs match catalogue mint/coral/blue gear.
    private fun palette(kind: Int) = when (kind) {
        1 -> intArrayOf(0x235a50,0x358f7c,0x7cd4b8,0xc2ecda)
        2 -> intArrayOf(0x773b4b,0xb55363,0xee8890,0xffd3c4)
        else -> intArrayOf(0x25436c,0x4277b4,0x7cafe1,0xd4e8f5)
    }
    private fun band(r: Raster, a: Anchors) {
        val x=a.eye.x; val y=a.eye.y+24; val c=palette(1)
        r.poly(INK,x-56,y-40,x+20,y-72,x+48,y-67,x+46,y-52,x+20,y-56,x-49,y-25)
        r.poly(c[1],x-52,y-39,x+21,y-68,x+44,y-64,x+43,y-56,x+20,y-60,x-49,y-29)
        r.poly(c[2],x-51,y-37,x+21,y-66,x+42,y-63,x+41,y-60,x+20,y-63,x-49,y-33)
        r.line(c[3],x-40,y-38,x-13,y-49,1)
        r.line(c[1],x+26,y-65,x+36,y-63,2)
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
        r.rect(INK,x-9,y-8,26,15);r.rect(c[0],x-6,y-5,20,9)
        r.line(c[3],x-5,y-4,x+11,y-4,1);r.rect(c[2],x+9,y-1,3,3)
    }
    private fun vest(r: Raster, a: Anchors, kind: Int, gender: AvatarGender) {
        val x=a.waist.x;val y=a.waist.y;val c=palette(kind)
        val top=if(gender==AvatarGender.MALE)y-63 else y-57
        r.poly(INK,x-22,top,x-9,top+1,x-10,y-12,x-22,y-5,x-28,y-12)
        r.poly(c[1],x-20,top+4,x-12,top+4,x-13,y-14,x-21,y-10,x-24,y-14)
        r.poly(INK,x+16,top+2,x+27,top+6,x+29,y-9,x+18,y-6,x+12,y-15)
        r.poly(c[1],x+18,top+6,x+24,top+8,x+25,y-12,x+19,y-10,x+16,y-16)
        r.rect(INK,x-17,y-36,12,24);r.rect(c[0],x-14,y-33,6,18)
        r.rect(0xb4d3de,x-13,y-32,4,14);r.rect(CREAM,x-13,y-38,4,7)
        r.rect(INK,x+14,y-35,12,24);r.rect(c[0],x+17,y-32,6,18)
        r.rect(0xb4d3de,x+18,y-31,4,14);r.rect(CREAM,x+18,y-37,4,7)
        r.line(INK,x-8,top+21,x+17,top+22,4);r.line(c[3],x-7,top+20,x+16,top+21,1)
        r.rect(c[2],x-19,top+10,3,6);r.rect(c[2],x+21,top+12,3,6)
    }
    private class Raster(val out:IntArray,val original:IntArray,val mask:IntArray,val fabricOnly:Boolean) {
        private fun put(x:Int,y:Int,color:Int) {
            if(x !in 0 until WIDTH || y !in 0 until HEIGHT)return
            val i=y*WIDTH+x;val alpha=original[i] ushr 24
            if(alpha==0)return
            if(fabricOnly && ((mask[i] ushr 24)!=255 || ((mask[i] ushr 16) and 255) !in 1..2))return
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

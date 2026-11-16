package com.runmoni.app.avatar

import com.runmoni.app.domain.AvatarGender
import kotlin.math.max
import kotlin.math.min

/** Worn head pieces on the sixteen immutable source poses.
 *
 * The crown may cover the authored HAIR region. That is deliberately different
 * from drawing only on transparent background, which leaves a floating shape
 * behind an unchanged head. An independent face/ear gate keeps the original
 * face, eyes and skin. No source file, face, pose or body part is regenerated.
 */
object AvatarHeadwearLayers {
    const val VERSION = "approved-headwear-v3-bounded-brims"
    enum class Kind { CAP, VISOR, HEADBAND, SANTA, BEAR, GOLDEN }

    private const val W = 256
    private const val H = 384
    private const val INK = 0x10172c
    private const val NEW_ALPHA = 253
    private val FUR = intArrayOf(0x728d89, 0xa6bbb0, 0xd1dccb, 0xedf0de, 0xfff8e9)
    private val MINT = intArrayOf(0x155563, 0x187b85, 0x21a3a8, 0x43c5bf, 0x89e1d2)
    private val RED = intArrayOf(0x592336, 0x8d2e40, 0xc94755, 0xec6c71, 0xffaaa0)
    private val BROWN = intArrayOf(0x392b31, 0x654134, 0x956044, 0xbf885b, 0xe5b883)
    private val GOLD = intArrayOf(0x69401f, 0x9d6529, 0xd69935, 0xf0c25f, 0xffe7a3)
    private val MALE_HAIR = intArrayOf(-83,-36, -87,-62, -71,-88, -55,-102, -17,-120, 22,-117,
        65,-99, 86,-68, 79,-51, 36,-51, -17,-36, -32,-20, -59,-24)
    private val FEMALE_HAIR = intArrayOf(-69,-31, -74,-62, -53,-84, -25,-97, 17,-93, 53,-77,
        74,-53, 72,-44, 32,-47, -7,-27, -22,-14, -45,-22)
    private val MALE_FACE = intArrayOf(-17,-44, 22,-55, 39,-55, 65,-43, 79,-20, 79,27,
        46,49, 4,50, -25,28, -34,12, -33,-12)
    private val FEMALE_FACE = intArrayOf(-16,-33, 18,-45, 38,-48, 64,-35, 74,-16, 74,29,
        39,48, 2,47, -26,26, -34,9, -32,-10)

    /** eyeX/eyeY are the original source landmarks, not inferred from pixels.
     * crownTop and bandY were authored per source cell. The female ponytail is
     * behind the crown and outside the main hair occlusion allowance.
     */
    private data class Fit(val eyeX: Int, val eyeY: Int, val crownTop: Int, val bandY: Int)
    private val male = arrayOf(
        Fit(150,145,17,91), Fit(152,150,22,96), Fit(144,145,17,91), Fit(148,144,16,90),
        Fit(156,144,16,90), Fit(144,150,22,96), Fit(146,150,22,96), Fit(142,144,16,90),
    )
    private val female = arrayOf(
        Fit(156,129,32,82), Fit(171,132,35,85), Fit(159,131,34,84), Fit(155,131,34,84),
        Fit(156,130,33,83), Fit(174,131,34,84), Fit(156,130,33,83), Fit(155,130,33,83),
    )
    private fun fit(frame: Int, gender: AvatarGender) =
        (if (gender == AvatarGender.MALE) male else female)[Math.floorMod(frame, 8)]

    /** -1 marks existing source hair that this particular head piece can cover.
     * Hair-edge alpha17..191 is covered by opaque cloth, not left as a dark crack.
     */
    fun occlusionMask(original: IntArray, frame: Int, gender: AvatarGender, kind: Kind): IntArray {
        require(original.size == W * H) { "Expected one approved 256x384 source cell" }
        val shape = artwork(frame, gender, kind)
        return allowance(original, shape, fit(frame, gender), gender, extension = false)
    }

    /** -1 marks new clothing alpha on nearly transparent source background.
     * Existing face/body alpha is never enlarged by this allowance.
     */
    fun extensionMask(original: IntArray, frame: Int, gender: AvatarGender, kind: Kind): IntArray {
        require(original.size == W * H) { "Expected one approved 256x384 source cell" }
        val shape = artwork(frame, gender, kind)
        return allowance(original, shape, fit(frame, gender), gender, extension = true)
    }

    /** pixels may already contain a costume body. Only allowed head pixels are
     * replaced; all other pixels, including other material layers, are copied.
     */
    fun render(pixels: IntArray, original: IntArray, frame: Int, gender: AvatarGender, kind: Kind): IntArray {
        require(pixels.size == W * H && original.size == pixels.size) { "Headwear dimensions differ" }
        val shape = artwork(frame, gender, kind)
        val f = fit(frame, gender)
        val cover = allowance(original, shape, f, gender, extension = false)
        val extension = allowance(original, shape, f, gender, extension = true)
        return pixels.clone().also { out ->
            for (i in out.indices) {
                if (cover[i] != 0) {
                    val alpha = if ((original[i] ushr 24) < 192) NEW_ALPHA else pixels[i] ushr 24
                    out[i] = (alpha shl 24) or (shape[i] and 0xffffff)
                }
                else if (extension[i] != 0) out[i] = (NEW_ALPHA shl 24) or (shape[i] and 0xffffff)
            }
        }
    }

    private fun allowance(original: IntArray, shape: IntArray, f: Fit, gender: AvatarGender, extension: Boolean): IntArray {
        val result = IntArray(original.size)
        for (y in 14 until min(174, f.eyeY + 24)) for (x in 8 until W - 8) {
            val i = y * W + x
            if (shape[i] == 0 || faceProtected(x, y, f, gender)) continue
            val alpha = original[i] ushr 24
            if (extension) {
                if (alpha <= 16) result[i] = -1
            } else if (alpha > 16 && hairAllowed(x, y, f, gender)) result[i] = -1
        }
        return result
    }

    /** Source-specific main-hair polygon; not a runtime hue/skin detector. */
    private fun hairAllowed(x: Int, y: Int, f: Fit, gender: AvatarGender): Boolean {
        val px = x - f.eyeX; val py = y - f.eyeY
        return inside(px, py, if (gender == AvatarGender.MALE) MALE_HAIR else FEMALE_HAIR)
    }

    /** Forehead, both eyes, cheeks/chin, visible ear and neck have their own
     * allowance-independent lock. Hair below the front seam remains forelock.
     */
    private fun faceProtected(x: Int, y: Int, f: Fit, gender: AvatarGender): Boolean {
        val px = x - f.eyeX; val py = y - f.eyeY
        if (py >= if (gender == AvatarGender.MALE) 30 else 28) return true
        if (px in 23..58 && py in -43..3) return true
        val forehead = if (gender == AvatarGender.MALE) MALE_FACE else FEMALE_FACE
        if (inside(px, py, forehead)) return true
        // Ear rectangle includes the original skin rim and surrounding ink.
        return px in -61..-27 && py in -8..29
    }

    private fun artwork(frame: Int, gender: AvatarGender, kind: Kind): IntArray {
        val f = fit(frame, gender)
        val raster = Raster()
        when (kind) {
            Kind.CAP -> cap(raster, f, gender)
            Kind.VISOR -> visor(raster, f, gender)
            Kind.HEADBAND -> band(raster, f, gender)
            Kind.SANTA -> santa(raster, f, gender)
            Kind.BEAR -> hood(raster, f, gender, golden = false)
            Kind.GOLDEN -> hood(raster, f, gender, golden = true)
        }
        return raster.pixels
    }

    private fun cap(r: Raster, f: Fit, gender: AvatarGender) {
        val x=f.eyeX; val y=f.bandY
        // Lower the male crown without leaving its source spikes above the cap.
        // The female fit and ponytail-root contact are intentionally unchanged.
        val t=f.crownTop+if(gender==AvatarGender.MALE)11 else 0
        val back=if(gender==AvatarGender.MALE)78 else 64
        // Broad stepped arc and a narrowing side plane, rather than straight
        // vertical walls and a flat roof. The crown still covers upper hair.
        r.poly(INK,x-back,y+4,x-back+2,y-17,x-64,t+20,x-49,t+9,x-28,t+2,
            x-5,t,x+24,t+4,x+46,t+14,x+61,t+29,x+68,y-3,x+43,y+7,x-29,y+13)
        r.poly(FUR[1],x-back+3,y+1,x-back+5,y-16,x-61,t+22,x-47,t+12,x-27,t+5,
            x-5,t+3,x+23,t+7,x+44,t+17,x+58,t+30,x+64,y-5,x+42,y+3,x-29,y+9)
        r.poly(FUR[2],x-60,t+24,x-45,t+14,x-26,t+7,x-5,t+5,x+21,t+9,
            x+40,t+19,x+48,y-5,x+8,y+3,x-38,y+8,x-63,y-6)
        r.poly(FUR[3],x-43,t+17,x-24,t+10,x-4,t+8,x+16,t+12,
            x+25,t+21,x+22,y-7,x-16,y-5,x-45,y+1)
        r.line(FUR[4],x-37,t+17,x-20,t+12,2)
        // Short low-contrast panel seam; no dark line crosses the light crown.
        r.line(FUR[1],x+22,t+9,x+31,t+25,2)
        frontBand(r,f,back,MINT)
        brim(r,f,MINT,wide=true)
        r.rect(MINT[0],x+23,y-19,8,5)
        r.rect(FUR[3],x+25,y-18,4,2)
    }

    private fun visor(r: Raster, f: Fit, gender: AvatarGender) {
        val back=if(gender==AvatarGender.MALE)73 else 61
        // Open crown intentionally preserves the upper source hair.
        frontBand(r,f,back,MINT)
        r.line(FUR[2],f.eyeX-back+6,f.bandY+5,f.eyeX+25,f.bandY-4,3)
        brim(r,f,MINT,wide=true)
    }

    private fun band(r: Raster, f: Fit, gender: AvatarGender) {
        val x=f.eyeX;val y=f.bandY;val back=if(gender==AvatarGender.MALE)72 else 59
        r.poly(INK,x-back,y+15,x-back+2,y+3,x-19,y-10,x+31,y-12,x+58,y-5,x+59,y+4,x+29,y-1,x-17,y+2)
        r.poly(MINT[0],x-back+3,y+12,x-back+5,y+5,x-18,y-7,x+30,y-9,x+55,y-3,x+56,y+1,x+29,y-4,x-17,y-1)
        r.poly(MINT[2],x-back+6,y+8,x-17,y-5,x+29,y-7,x+50,y-3,x+29,y-2,x-16,y+1)
        r.line(MINT[3],x-43,y+2,x-21,y-3,2)
        r.line(MINT[1],x+33,y-1,x+51,y+2,2)
    }

    private fun frontBand(r: Raster,f: Fit,back: Int,c: IntArray) {
        val x=f.eyeX;val y=f.bandY
        // Dark contact strip sits ON hair. Near side is thick, far side tapers.
        r.poly(INK,x-back,y+4,x-22,y-10,x+28,y-11,x+63,y-5,x+64,y+4,x+28,y-2,x-19,y-1,x-back+1,y+15)
        r.poly(c[0],x-back+3,y+5,x-20,y-7,x+28,y-8,x+60,y-3,x+61,y+1,x+28,y-4,x-19,y-3,x-back+4,y+11)
        r.line(c[2],x-back+5,y+4,x-20,y-8,3)
        r.line(c[1],x-19,y-8,x+28,y-9,2)
        r.line(c[3],x-back+14,y+1,x-41,y-5,2)
    }

    private fun brim(r: Raster,f: Fit,c: IntArray,wide: Boolean) {
        val x=f.eyeX;val y=f.bandY;val nominal=if(wide)89 else 74
        // Close the entire tip before the raster's x247 crop boundary. The
        // female F1/F5 eye anchors need a shorter three-quarter projection.
        val end=min(nominal,243-x)
        // Foreshorten every curve control with the tip, not only its final x.
        // Fixed x65/x67 controls would otherwise cross the shortened inner tip.
        fun at(offset: Int)=x+22+(offset-22)*(end-22)/(nominal-22)
        r.poly(INK,at(22),y-7,at(45),y-10,at(67),y-7,x+end,y,x+end+3,y+5,
            x+end-4,y+12,at(65),y+13,at(43),y+8,at(28),y+1)
        r.poly(c[0],at(28),y-5,at(46),y-7,at(66),y-4,x+end-2,y+2,x+end,y+5,
            x+end-5,y+9,at(65),y+9,at(44),y+5,at(31),y)
        r.poly(c[2],at(31),y-5,at(46),y-6,at(65),y-3,x+end-5,y+2,
            x+end-6,y+5,at(65),y+6,at(45),y+2)
        r.line(c[3],at(47),y-5,at(66),y-2,2)
        r.line(c[0],at(65),y+10,x+end-5,y+9,2)
    }

    private fun santa(r: Raster,f: Fit,gender: AvatarGender) {
        val x=f.eyeX;val y=f.bandY
        val t=max(16,f.crownTop+if(gender==AvatarGender.MALE)9 else -8)
        val back=if(gender==AvatarGender.MALE)76 else 64
        // Asymmetric soft cone folds toward the front; it is not a red cap crown.
        r.poly(INK,x-back,y-2,x-back+9,y-32,x-43,t+8,x-21,t,x-3,t+2,
            x+24,t+14,x+47,y-38,x+60,y-26,x+57,y-15,x+43,y-20,
            x+42,y-5,x+20,y+2,x-24,y+8)
        r.poly(RED[1],x-back+4,y-4,x-back+12,y-30,x-41,t+11,x-20,t+3,x-4,t+5,
            x+22,t+17,x+45,y-35,x+56,y-25,x+54,y-19,x+44,y-25,
            x+38,y-7,x+19,y-1,x-24,y+4)
        r.poly(RED[2],x-back+16,y-29,x-38,t+13,x-20,t+6,x-5,t+8,x+16,t+19,
            x+27,y-26,x+31,y-6,x+17,y-4,x-24,y+1,x-56,y-6)
        r.poly(RED[3],x-41,t+16,x-20,t+9,x-8,t+11,x-7,t+26,
            x-20,y-7,x-54,y-8,x-62,y-17)
        // One broad folded underside and its low-contrast edge explain drape.
        r.poly(RED[0],x+2,t+7,x+24,t+18,x+48,y-35,x+56,y-24,
            x+45,y-26,x+29,y-34,x+17,t+25)
        r.line(RED[1],x+4,t+8,x+26,t+22,3)
        r.line(RED[4],x-37,t+17,x-22,t+12,2)
        // Solid folded fur brim replaces part of upper hair, with restrained tufts.
        r.poly(INK,x-back-1,y-1,x-24,y-11,x+29,y-12,x+65,y-5,x+66,y+7,x+29,y+2,x-22,y+3,x-back,y+15)
        r.poly(FUR[1],x-back+2,y+1,x-23,y-8,x+28,y-9,x+62,y-3,x+63,y+3,x+29,y-1,x-22,y,x-back+3,y+11)
        r.line(FUR[3],x-back+6,y+3,x-23,y-7,6)
        r.line(FUR[3],x-21,y-7,x+27,y-8,5)
        r.line(FUR[2],x+27,y-8,x+58,y-3,4)
        r.line(FUR[4],x-back+13,y+1,x-back+31,y-2,2)
        // A rounded tuft hangs from the folded tip, not a square side button.
        r.roundCluster(INK,x+56,y-21,14,14)
        r.roundCluster(FUR[1],x+56,y-21,11,11)
        r.roundCluster(FUR[3],x+54,y-23,8,8)
        r.rect(FUR[4],x+51,y-27,5,3)
    }

    private fun hood(r: Raster,f: Fit,gender: AvatarGender,golden: Boolean) {
        val x=f.eyeX;val y=f.bandY;val t=f.crownTop
        val back=if(gender==AvatarGender.MALE)80 else 68
        val c=if(golden)GOLD else BROWN
        // Ears belong to the same crown, not separate circles behind the hair.
        if(golden) {
            r.poly(INK,x-63,t+30,x-66,t-2,x-53,t-8,x-28,t+22)
            r.poly(c[1],x-59,t+25,x-61,t,x-53,t-3,x-34,t+22)
            r.poly(FUR[2],x-56,t+19,x-57,t+4,x-52,t+1,x-41,t+22)
            r.poly(INK,x+25,t+19,x+45,t-8,x+59,t-1,x+64,t+36)
            r.poly(c[1],x+31,t+21,x+46,t-3,x+55,t+2,x+59,t+31)
            r.poly(FUR[2],x+38,t+22,x+47,t+4,x+51,t+6,x+55,t+26)
        } else {
            // Centers keep the complete round silhouette below the 14px frame
            // margin. The old high octagons were clipped into square pillars.
            r.roundCluster(INK,x-55,t+18,18,18);r.roundCluster(c[1],x-55,t+18,15,15)
            r.roundCluster(FUR[1],x-55,t+18,9,9);r.roundCluster(c[3],x-59,t+13,4,3)
            r.roundCluster(INK,x+46,t+23,15,16);r.roundCluster(c[1],x+46,t+23,12,13)
            r.roundCluster(FUR[1],x+46,t+23,7,8)
        }
        r.poly(INK,x-back,y+19,x-back+1,t+34,x-61,t+13,x-32,t+4,x+4,t+5,x+39,t+17,x+66,t+39,x+77,y+23,x+65,y+39,x+54,y+25,x+53,y+3,x+27,y-3,x-20,y+1,x-57,y+16,x-62,y+34)
        r.poly(c[0],x-back+3,y+18,x-back+4,t+35,x-59,t+16,x-31,t+7,x+3,t+8,x+37,t+20,x+63,t+40,x+73,y+22,x+65,y+32,x+58,y+22,x+57,y+1,x+27,y-6,x-20,y-2,x-60,y+13,x-65,y+29)
        r.poly(c[2],x-back+7,y+11,x-back+8,t+35,x-56,t+20,x-30,t+11,x+2,t+12,x+33,t+23,x+57,t+40,x+64,y-1,x+27,y-10,x-21,y-6,x-60,y+10)
        r.poly(c[3],x-57,t+25,x-29,t+15,x-3,t+15,x+14,t+23,x+13,y-12,x-22,y-10,x-52,y+3,x-65,y+8)
        r.line(c[4],x-43,t+24,x-27,t+19,2)
        // Front opening seam is attached to hair, with two-pixel contact shadow.
        r.line(INK,x-back+4,y+18,x-22,y-4,6)
        r.line(INK,x-22,y-4,x+28,y-7,5)
        r.line(INK,x+28,y-7,x+57,y+3,5)
        r.line(if(golden)FUR[2] else c[1],x-back+5,y+13,x-22,y-8,4)
        r.line(if(golden)FUR[3] else c[3],x-22,y-8,x+27,y-11,3)
        r.line(if(golden)FUR[1] else c[1],x+27,y-11,x+57,y,3)
        // Side seam is one cluster, not glitter or a screen-space gradient.
        r.line(c[1],x-back+7,y-6,x-back+8,y+13,2)
        r.rect(c[3],x-back+7,y+12,4,3)
    }

    private fun inside(x: Int,y: Int,xy: IntArray): Boolean {
        var hit=false;var j=xy.size-2
        var i=0
        while(i<xy.size) {
            val yi=xy[i+1];val yj=xy[j+1]
            if((yi>y)!=(yj>y) && x<(xy[j]-xy[i]).toDouble()*(y-yi)/(yj-yi)+xy[i]) hit=!hit
            j=i;i+=2
        }
        return hit
    }

    /** Integer pixel clusters only. No blur, antialiasing, random grain or RGB inference. */
    private class Raster {
        val pixels=IntArray(W*H)
        fun rect(color: Int,x: Int,y: Int,w: Int,h: Int) {
            for(py in max(14,y) until min(174,y+h)) for(px in max(8,x) until min(W-8,x+w))
                pixels[py*W+px]=(NEW_ALPHA shl 24) or color
        }
        fun line(color: Int,x0: Int,y0: Int,x1: Int,y1: Int,width: Int) {
            var x=x0;var y=y0;val dx=kotlin.math.abs(x1-x0);val dy=-kotlin.math.abs(y1-y0)
            val sx=if(x0<x1)1 else -1;val sy=if(y0<y1)1 else -1;var e=dx+dy
            while(true) {
                rect(color,x-width/2,y-width/2,width,width)
                if(x==x1 && y==y1)break
                val e2=e*2;if(e2>=dy){e+=dy;x+=sx};if(e2<=dx){e+=dx;y+=sy}
            }
        }
        fun poly(color: Int,vararg xy: Int) {
            require(xy.size>=6 && xy.size%2==0)
            val left=max(8,xy.filterIndexed { i,_->i%2==0 }.min())
            val right=min(W-9,xy.filterIndexed { i,_->i%2==0 }.max())
            val top=max(14,xy.filterIndexed { i,_->i%2==1 }.min())
            val bottom=min(173,xy.filterIndexed { i,_->i%2==1 }.max())
            for(y in top..bottom)for(x in left..right)if(inside(x,y,xy))
                pixels[y*W+x]=(NEW_ALPHA shl 24) or color
        }
        fun roundCluster(color: Int,cx: Int,cy: Int,rx: Int,ry: Int) {
            val squared=rx*rx*ry*ry
            for(y in cy-ry..cy+ry step 2)for(x in cx-rx..cx+rx step 2) {
                val dx=x-cx;val dy=y-cy
                if(dx*dx*ry*ry+dy*dy*rx*rx<=squared)rect(color,x,y,2,2)
            }
        }
    }
}

package com.runmoni.app.avatar

import com.runmoni.app.domain.AvatarCatalogue
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.RunningKitColors
import com.runmoni.app.domain.variantFor

/** Pose-bound contracts for the separately authored Aseprite constructions.
 * Names distinguish material/face decoration from a regenerated hairstyle or expression.
 * The base recipe supplies coverage; exported pixels supply the new construction.
 */
object ExpandedAtelierLayers {
    const val VERSION = "aseprite-constructions-v1"
    const val SIZE = 256 * 384
    data class Spec(val slot: String, val variant: Int, val base: Int, val ordinal: Int) {
        val key get() = "$slot-$variant"
        val tinted get() = slot in setOf("top", "bottom", "costume", "shoes")
    }
    val specs: List<Spec> = listOf(
        "hair" to listOf(4,0,0,0), "headwear" to listOf(4,1,2,3), "face" to listOf(4,0,0,0),
        "top" to listOf(7,1,2,3), "bottom" to listOf(6,0,1,0), "costume" to listOf(6,0,0,0),
        "socks" to listOf(5,1,2,1), "shoes" to listOf(5,2,3,1),
        "ear" to listOf(4,1,2,3), "eyewear" to listOf(4,1,2,3), "wrist" to listOf(4,1,2,3),
        "waist" to listOf(4,1,2,3), "hydration" to listOf(4,1,2,3),
    ).flatMap { (slot, entries) -> (1..3).map { Spec(slot, entries[0] + it - 1, entries[it], it) } }
    fun spec(slot: String, variant: Int): Spec? = specs.firstOrNull { it.slot == slot && it.variant == variant }
    fun supports(slot: String, variant: Int) = spec(slot, variant) != null
    fun selected(style: AvatarStyle): List<Spec> {
        val covered = AvatarCatalogue.coveredSlots(style.costume)
        return specs.filter { style.variantFor(it.slot) == it.variant && it.slot !in covered &&
            !(it.slot == "hair" && "headwear" in covered) }
    }

    /** Resolve only drawing inputs. Saved IDs, colours, ownership and wallet remain unchanged. */
    fun foundation(style: AvatarStyle): AvatarStyle {
        val covered = AvatarCatalogue.coveredSlots(style.costume)
        fun base(slot: String): Int {
            if (slot in covered) return 0
            val v = style.variantFor(slot)
            val s = spec(slot, v) ?: return v
            return if (slot in setOf("ear", "eyewear", "wrist", "waist", "hydration")) 0 else s.base
        }
        var top = base("top"); var bottom = base("bottom"); var socks = base("socks"); var shoes = base("shoes")
        val saved = style.runningKitColors.normalized()
        var tc = saved.top; var bc = saved.bottom; var sc = saved.shoes
        spec("top", style.top)?.takeIf { "top" !in covered }?.let { if (tc == 0) tc = listOf(1,3,5)[it.ordinal-1] }
        spec("bottom", style.bottom)?.takeIf { "bottom" !in covered }?.let { if (bc == 0) bc = listOf(3,4,1)[it.ordinal-1] }
        spec("shoes", style.shoes)?.takeIf { "shoes" !in covered }?.let { if (sc == 0) sc = listOf(3,5,2)[it.ordinal-1] }
        spec("costume", style.costume)?.let {
            top = listOf(1,3,2)[it.ordinal-1]; bottom = 1
            socks = if (it.ordinal == 1) 1 else 2; shoes = listOf(2,1,3)[it.ordinal-1]
            val default = listOf(3,1,4)[it.ordinal-1]
            if (tc == 0) tc = default; if (bc == 0) bc = default; if (sc == 0) sc = default
        }
        return style.copy(top=top, bottom=bottom, socks=socks, shoes=shoes,
            hair=base("hair"), face=base("face"), headwear=base("headwear"), ear=base("ear"),
            eyewear=base("eyewear"), wrist=base("wrist"), waist=base("waist"), hydration=base("hydration"),
            runningKitColors=RunningKitColors(tc,bc,sc))
    }

    // Male backlight uses an independent inner-crown and rear-hair surface,
    // rather than reusing the cap's worn-coverage shape. Both stay above/behind the face and ear.
    private val maleCrown = intArrayOf(-58,-69,-44,-85,-14,-93,12,-82,13,-66,-3,-53,-31,-49,-51,-55)
    private val maleBack = intArrayOf(-65,-59,-52,-59,-52,-29,-65,-29)
    private val ponytail = intArrayOf(-130,-36,-117,-29,-110,-50,-102,-83,-89,-106,-67,-112,-45,-100,-44,-83,-61,-69,-70,-42,-88,-20,-112,-17)
    private fun inside(x: Int,y: Int,p: IntArray): Boolean {
        var yes=false;var j=p.size-2
        for(i in p.indices step 2) {
            if ((p[i+1]>y)!=(p[j+1]>y) && x<(p[j]-p[i]).toDouble()*(y-p[i+1])/(p[j+1]-p[i+1])+p[i]) yes=!yes
            j=i
        }
        return yes
    }

    /** Interleave legacy and authored equipment in the same physical order. */
    fun gearOnly(style: AvatarStyle, slot: String) = AvatarStyle(gender=style.gender, costume=style.costume,
        headwear=if(slot=="headwear") style.headwear else 0, ear=if(slot=="ear") style.ear else 0,
        hydration=if(slot=="hydration") style.hydration else 0, waist=if(slot=="waist") style.waist else 0,
        wrist=if(slot=="wrist") style.wrist else 0, eyewear=if(slot=="eyewear") style.eyewear else 0)

    /** Independent source labels plus source-specific equipment surfaces. No hue detection. */
    fun allowance(s: Spec, original: IntArray, fabric: IntArray, coverage: IntArray,
        socks: IntArray, hands: IntArray, frame: Int, gender: AvatarGender): IntArray {
        require(listOf(original, fabric, coverage, socks, hands).all { it.size == SIZE })
        val a = AvatarEquipmentLayers.anchors(frame, gender)
        val top = if (s.slot == "costume") listOf(1,3,2)[s.ordinal-1] else if (s.slot == "top") s.base else 0
        val bottom = if (s.slot == "costume") 1 else if (s.slot == "bottom") s.base else 0
        val sock = if (s.slot == "costume") (if (s.ordinal == 1) 1 else 2) else if (s.slot == "socks") s.base else 0
        val reference = when {
            s.slot in setOf("ear", "eyewear", "wrist", "waist", "hydration") -> {
                val worn = when (s.slot) {
                    "ear" -> AvatarStyle(ear=s.base, gender=gender)
                    "eyewear" -> AvatarStyle(eyewear=s.base, gender=gender)
                    "wrist" -> AvatarStyle(wrist=s.base, gender=gender)
                    "waist" -> AvatarStyle(waist=s.base, gender=gender)
                    else -> AvatarStyle(hydration=s.base, gender=gender)
                }
                AvatarEquipmentLayers.render(original, original, fabric, frame, gender, worn, hands)
            }
            else -> null
        }
        val kind = if (s.slot == "headwear") when (s.base) {
            1 -> AvatarHeadwearLayers.Kind.CAP
            2 -> AvatarHeadwearLayers.Kind.VISOR
            else -> AvatarHeadwearLayers.Kind.HEADBAND
        } else null
        val front = if (kind != null || s.slot == "hair")
            AvatarHeadwearLayers.occlusionMask(original, frame, gender, kind ?: AvatarHeadwearLayers.Kind.CAP) else null
        val extension = kind?.let { AvatarHeadwearLayers.extensionMask(original, frame, gender, it) }
        return IntArray(SIZE) { i ->
            val region = (fabric[i] ushr 16) and 255
            val flags = (coverage[i] ushr 16) and 255
            val x = i % 256; val y = i / 256
            val topAllowed = (region == 1 || ((if (top == 1) 1 else if (top in 2..3) 2 else 0) and flags) != 0) && hands[i] == 0
            val bottomAllowed = (region == 2 || bottom == 1 && (flags and 4) != 0) && hands[i] == 0
            val sockAllowed = sock != 0 && (((socks[i] ushr 16) and sock) != 0)
            val yes = when (s.slot) {
                "hair" -> {
                    val px=x-a.eye.x; val py=y-a.eye.y
                    val surface = if (s.ordinal == 3 && gender == AvatarGender.MALE)
                        inside(px,py,maleCrown) || inside(px,py,maleBack)
                    else front!![i] != 0 || s.ordinal == 3 && gender == AvatarGender.FEMALE && inside(px,py,ponytail)
                    surface && original[i] ushr 24 >= 192
                }
                "face" -> x in a.eye.x-5..a.eye.x+11 && y in a.eye.y+12..a.eye.y+20 && original[i] ushr 24 >= 192
                "headwear" -> front!![i] != 0 || extension!![i] != 0
                "top" -> topAllowed
                "bottom" -> bottomAllowed
                "socks" -> sockAllowed
                "shoes" -> region == 3
                "costume" -> topAllowed || bottomAllowed || sockAllowed || region == 3
                else -> reference!![i] != original[i] && original[i] ushr 24 > 16
            }
            if (yes && (s.slot == "headwear" || original[i] ushr 24 > 0)) -1 else 0
        }
    }

    /** Exported material roles cannot select an unrelated palette or change alpha. */
    fun apply(pixels: IntArray, detail: IntArray, tint: IntArray?, allowed: IntArray, colors: RunningKitColors): IntArray {
        require(pixels.size == SIZE && detail.size == SIZE && allowed.size == SIZE && (tint == null || tint.size == SIZE))
        val c = colors.normalized()
        val palette = intArrayOf(0,c.top,c.bottom,c.shoes)
        return pixels.copyOf().also { out ->
            for (i in detail.indices) {
                if (detail[i] ushr 24 == 0) {
                    require(tint == null || tint[i] ushr 24 == 0) { "Tint escaped authored ink" }
                    continue
                }
                require(detail[i] ushr 24 == 255 && allowed[i] == -1 && pixels[i] ushr 24 > 0) {
                    "Aseprite construction escaped its pose-bound allowance"
                }
                var rgb = detail[i] and 0xffffff
                if (tint != null && tint[i] ushr 24 != 0) {
                    val role = (tint[i] ushr 16) and 255
                    require(tint[i] ushr 24 == 255 && role in 1..3 && (tint[i] and 255) == 0)
                    val color = palette[role]
                    if (color > 0) rgb = GarmentPalette.shade(color, (tint[i] ushr 8) and 255)
                }
                out[i] = (pixels[i] and -0x1000000) or rgb
            }
        }
    }
}

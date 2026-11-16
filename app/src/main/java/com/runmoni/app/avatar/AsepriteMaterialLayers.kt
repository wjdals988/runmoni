package com.runmoni.app.avatar

import com.runmoni.app.domain.AvatarGender

/** Explicit, pose-bound cloth allowance for the authored Aseprite material cels.
 * It cannot add body pixels, recolour a face or overwrite a protected hand.
 * The original and the already-rendered garment remain separate inputs.
 */
object AsepriteMaterialLayers {
    const val VERSION = "aseprite-materials-v1"
    const val WIDTH = 256
    const val HEIGHT = 384

    fun costumeMask(pixels: IntArray, original: IntArray, fabric: IntArray,
                    coverage: IntArray, hands: IntArray, frame: Int,
                    gender: AvatarGender, costume: Int): IntArray {
        require(listOf(pixels, original, fabric, coverage, hands).all { it.size == WIDTH * HEIGHT })
        val kind = when (costume) {
            1 -> AvatarHeadwearLayers.Kind.SANTA
            4 -> AvatarHeadwearLayers.Kind.BEAR
            5 -> AvatarHeadwearLayers.Kind.GOLDEN
            else -> return IntArray(pixels.size)
        }
        val result = headMask(pixels, original, frame, gender, kind)
        val eye = AvatarEquipmentLayers.anchors(frame, gender).eye
        val headEnd = if (gender == AvatarGender.MALE) 182 else 176
        for (i in pixels.indices) {
            val x = i % WIDTH; val y = i / WIDTH
            val region = (fabric[i] ushr 16) and 255
            val flags = (coverage[i] ushr 16) and 255
            val neck = x in eye.x - 14..eye.x + 10 && y in eye.y + 20..eye.y + 45
            val cloth = (region in 1..3 || (flags and 6) != 0) &&
                !((y < headEnd || neck) && region != 1)
            if (cloth && hands[i] == 0 && original[i] ushr 24 > 0 &&
                pixels[i] ushr 24 > 0 && pixels[i] != original[i]) result[i] = -1
        }
        return result
    }

    fun headwearMask(pixels: IntArray, original: IntArray, frame: Int,
                     gender: AvatarGender, headwear: Int): IntArray {
        require(pixels.size == WIDTH * HEIGHT && original.size == pixels.size)
        val kind = when (headwear) {
            1 -> AvatarHeadwearLayers.Kind.CAP
            2 -> AvatarHeadwearLayers.Kind.VISOR
            else -> return IntArray(pixels.size)
        }
        return headMask(pixels, original, frame, gender, kind)
    }

    private fun headMask(pixels: IntArray, original: IntArray, frame: Int,
                         gender: AvatarGender, kind: AvatarHeadwearLayers.Kind): IntArray {
        val front = AvatarHeadwearLayers.occlusionMask(original, frame, gender, kind)
        val extension = AvatarHeadwearLayers.extensionMask(original, frame, gender, kind)
        return IntArray(pixels.size) { i ->
            if ((front[i] != 0 || extension[i] != 0) && pixels[i] ushr 24 > 0 &&
                pixels[i] != original[i]) -1 else 0
        }
    }

    /** Opaque authored ink only. Even head-fringe alpha253 is kept unchanged. */
    fun apply(pixels: IntArray, detail: IntArray, mask: IntArray): IntArray {
        require(pixels.size == WIDTH * HEIGHT && detail.size == pixels.size && mask.size == pixels.size)
        val result = pixels.clone()
        for (i in detail.indices) {
            if (detail[i] ushr 24 == 0) continue
            require(detail[i] ushr 24 == 255 && mask[i] == -1 && pixels[i] ushr 24 > 0) {
                "Aseprite material cel escaped its explicit cloth allowance"
            }
            result[i] = (pixels[i] and -0x1000000) or (detail[i] and 0x00ffffff)
        }
        return result
    }
}

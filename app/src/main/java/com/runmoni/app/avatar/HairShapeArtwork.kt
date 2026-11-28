package com.runmoni.app.avatar

import android.content.Context
import android.util.LruCache
import com.runmoni.app.domain.AvatarGender
import org.json.JSONObject

/** The ink and source-hair removal are independent Aseprite layers. */
internal object HairShapeArtwork {
    const val VERSION = "hair-shapes-v1"
    private const val Directory = "avatars/hair-shapes-v1"
    private val variants = 4..6
    private data class Cel(val ink: IntArray, val removal: IntArray)
    private val cache = object : LruCache<String, Cel>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Cel) = (value.ink.size + value.removal.size) * 4
    }
    private var hashes: JSONObject? = null

    fun supports(variant: Int) = variant in variants

    @Synchronized
    private fun cel(context: Context, variant: Int, frame: Int, gender: AvatarGender): Cel {
        require(supports(variant))
        val stem = "hair-$variant/${gender.wireValue}-${Math.floorMod(frame, ApprovedAvatarRenderer.FRAMES)}"
        cache.get(stem)?.let { return it }
        val files = hashes ?: context.assets.open("$Directory/manifest.json").use {
            val manifest = JSONObject(it.bufferedReader().readText())
            require(manifest.getInt("schemaVersion") == 1 &&
                manifest.getInt("width") == ApprovedAvatarRenderer.WIDTH &&
                manifest.getInt("height") == ApprovedAvatarRenderer.HEIGHT &&
                manifest.getInt("frames") == ApprovedAvatarRenderer.FRAMES &&
                manifest.getInt("rows") == AvatarGender.entries.size &&
                manifest.getInt("items") == variants.count() &&
                manifest.getString("sourceSha256") == ApprovedAvatarRenderer.SHA256)
            manifest.getJSONObject("files").also { fileHashes ->
                require(fileHashes.length() == variants.count() * AvatarGender.entries.size * ApprovedAvatarRenderer.FRAMES * 2)
                hashes = fileHashes
            }
        }
        fun read(name: String) = readAtelierCel(context, Directory, name, files.getString(name))
        return Cel(read("$stem.png"), read("$stem-cut.png")).also { cache.put(stem, it) }
    }

    fun render(context: Context, pixels: IntArray, source: IntArray, variant: Int, frame: Int,
               gender: AvatarGender, closedCap: Boolean): IntArray {
        val cel = cel(context, variant, frame, gender)
        val eye = AvatarEquipmentLayers.anchors(frame, gender).eye
        val capBottom = eye.y - if (gender == AvatarGender.MALE) 60 else 49
        return pixels.copyOf().also { result ->
            for (i in result.indices) {
                val ink = cel.ink[i]
                val cut = cel.removal[i] ushr 24
                if (ink ushr 24 == 0 && cut == 0) continue
                val x = i % ApprovedAvatarRenderer.WIDTH
                val y = i / ApprovedAvatarRenderer.WIDTH
                val sourceAlpha = source[i] ushr 24
                require(x in 8 until 255 && y in 14 until eye.y + 26 &&
                    ((cut == 255 && sourceAlpha > 0) || (cut == 0 && sourceAlpha == 0))) {
                    "Hairstyle left the authored head and hair coverage"
                }
                require(ink ushr 24 == 0 || ink ushr 24 == 255) { "Hairstyle ink must be opaque" }
                result[i] = if (closedCap && y < capBottom) 0 else ink
            }
        }
    }
}

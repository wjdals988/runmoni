package com.runmoni.app.avatar

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import com.runmoni.app.domain.AvatarGender
import org.json.JSONObject
import java.security.MessageDigest

/** Real Aseprite-exported transparent cels; read only on the renderer worker.
 * The editor executable and its third-party sources are not packaged in the app.
 */
internal object AsepriteDetailArtwork {
    const val DIRECTORY = "avatars/atelier-v1"
    private const val CACHE_BYTES = 4 * 1024 * 1024
    private val cels = object : LruCache<String, IntArray>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: IntArray) = value.size * 4
    }
    private var fileHashes: JSONObject? = null

    @Synchronized
    private fun cel(context: Context, slot: String, variant: Int, frame: Int, gender: AvatarGender): IntArray {
        val name = "$slot-$variant/${gender.wireValue}-${Math.floorMod(frame, 8)}.png"
        cels.get(name)?.let { return it }
        val files = fileHashes ?: context.assets.open("$DIRECTORY/manifest.json").use {
            val manifest = JSONObject(it.bufferedReader().readText())
            require(manifest.getInt("schemaVersion") == 1 && manifest.getInt("width") == 256 &&
                manifest.getInt("height") == 384 && manifest.getInt("frames") == 8 && manifest.getInt("rows") == 2 &&
                manifest.getString("sourceSha256") == ApprovedAvatarRenderer.SHA256)
            manifest.getJSONObject("files").also { hashes -> require(hashes.length() == 80); fileHashes = hashes }
        }
        val bytes = context.assets.open("$DIRECTORY/$name").use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(digest == files.getString(name)) { "Aseprite material cel integrity check failed" }
        val image = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inScaled = false }))
        try {
            require(image.width == 256 && image.height == 384)
            val pixels = IntArray(256 * 384)
            image.getPixels(pixels, 0, 256, 0, 0, 256, 384)
            cels.put(name, pixels)
            return pixels
        } finally { image.recycle() }
    }

    fun costume(context: Context, pixels: IntArray, original: IntArray, fabric: IntArray,
                coverage: IntArray, hands: IntArray, frame: Int, gender: AvatarGender, costume: Int): IntArray {
        if (costume !in setOf(1, 4, 5)) return pixels
        return AsepriteMaterialLayers.apply(pixels, cel(context, "costume", costume, frame, gender),
            AsepriteMaterialLayers.costumeMask(pixels, original, fabric, coverage, hands, frame, gender, costume))
    }

    fun headwear(context: Context, pixels: IntArray, original: IntArray,
                 frame: Int, gender: AvatarGender, headwear: Int): IntArray {
        if (headwear !in 1..2) return pixels
        return AsepriteMaterialLayers.apply(pixels, cel(context, "headwear", headwear, frame, gender),
            AsepriteMaterialLayers.headwearMask(pixels, original, frame, gender, headwear))
    }
}

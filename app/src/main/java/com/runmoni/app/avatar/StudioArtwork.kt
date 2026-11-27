package com.runmoni.app.avatar

import android.content.Context
import android.util.LruCache
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.RunningKitColors
import com.runmoni.app.domain.StudioCollection
import org.json.JSONObject

/** Fitted layers share the approved poses. Only currently worn cels are decoded. */
internal object StudioArtwork {
    const val DIRECTORY = "avatars/atelier-v3"
    private data class Cel(val detail: IntArray, val tint: IntArray?)
    private val cache = object : LruCache<String, Cel>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Cel) = (value.detail.size + (value.tint?.size ?: 0)) * Int.SIZE_BYTES
    }
    private var hashes: JSONObject? = null

    @Synchronized
    private fun cel(context: Context, item: StudioCollection.Item, frame: Int, gender: AvatarGender): Cel {
        val stem = "${item.assetKey}/${gender.wireValue}-$frame"
        cache.get(stem)?.let { return it }
        val files = hashes ?: readManifest(context).also { hashes = it }
        fun read(name: String) = readAtelierCel(context, DIRECTORY, name, files.getString(name))
        val detail = read("$stem.png")
        val tint = if (item.tintRole == 0) null else read("$stem-tint.png")
        require(detail.all { it ushr 24 == 0 || it ushr 24 == 255 }) { "Invalid artwork alpha for $stem" }
        if (tint != null) for (i in tint.indices) {
            val label = tint[i]
            if (label ushr 24 == 0) continue
            require(detail[i] ushr 24 == 255 && label ushr 24 == 255 &&
                (label ushr 16 and 255) == item.tintRole && (label and 255) == 0) {
                "Invalid material label for $stem at pixel $i"
            }
        }
        return Cel(detail, tint).also { cache.put(stem, it) }
    }

    private fun readManifest(context: Context): JSONObject {
        val manifest = context.assets.open("$DIRECTORY/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        require(manifest.getInt("schemaVersion") == 1 && manifest.getInt("width") == ApprovedAvatarRenderer.WIDTH &&
            manifest.getInt("height") == ApprovedAvatarRenderer.HEIGHT && manifest.getInt("frames") == ApprovedAvatarRenderer.FRAMES &&
            manifest.getInt("rows") == AvatarGender.entries.size && manifest.getInt("items") == StudioCollection.items.size &&
            manifest.getString("sourceSha256") == ApprovedAvatarRenderer.SHA256)
        val files = manifest.getJSONObject("files")
        val channels = StudioCollection.items.size + StudioCollection.items.count { it.tintRole != 0 }
        val expected = channels * AvatarGender.entries.size * ApprovedAvatarRenderer.FRAMES
        val sha256 = Regex("[a-f0-9]{64}")
        require(files.length() == expected)
        for (item in StudioCollection.items) for (gender in AvatarGender.entries) for (frame in 0 until ApprovedAvatarRenderer.FRAMES) {
            val stem = "${item.assetKey}/${gender.wireValue}-$frame"
            require(files.getString("$stem.png").matches(sha256))
            if (item.tintRole != 0) require(files.getString("$stem-tint.png").matches(sha256))
        }
        return files
    }

    fun paint(context: Context, pixels: IntArray, item: StudioCollection.Item, frame: Int, gender: AvatarGender,
        colors: RunningKitColors) {
        val (detail, tint) = cel(context, item, frame, gender)
        val color = when (item.slot) {
            "top" -> colors.top
            "bottom" -> colors.bottom
            "shoes" -> colors.shoes
            else -> 0
        }
        val extendsSilhouette = item.slot == "headwear" || item.slot == "hydration"
        for (i in detail.indices) {
            val pixel = detail[i]
            if (pixel ushr 24 == 0) continue
            val label = tint?.get(i) ?: 0
            val alpha = (if (extendsSilhouette) pixel else pixels[i]) and 0xff000000.toInt()
            val rgb = if (color != 0 && label ushr 24 != 0) GarmentPalette.shade(color, label ushr 8 and 255)
                else pixel and 0x00ffffff
            pixels[i] = alpha or rgb
        }
    }
}

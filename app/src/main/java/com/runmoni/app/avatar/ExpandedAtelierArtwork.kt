package com.runmoni.app.avatar

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.AvatarStyle
import org.json.JSONObject
import java.security.MessageDigest

/** Only the selected Aseprite cel is decoded, on the existing renderer worker.
 * A bounded cache holds transparent authored pixels, never 624 eager full-body bitmaps.
 */
internal object ExpandedAtelierArtwork {
    const val DIRECTORY = "avatars/atelier-v2"
    private const val CACHE_BYTES = 8 * 1024 * 1024
    private data class Cel(val detail: IntArray, val tint: IntArray?)
    private val cache = object : LruCache<String, Cel>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Cel) = (value.detail.size + (value.tint?.size ?: 0)) * 4
    }
    private var hashes: JSONObject? = null

    @Synchronized
    private fun cel(context: Context, spec: ExpandedAtelierLayers.Spec, frame: Int, gender: AvatarGender): Cel {
        val stem = "${spec.key}/${gender.wireValue}-${Math.floorMod(frame, 8)}"
        cache.get(stem)?.let { return it }
        val files = hashes ?: context.assets.open("$DIRECTORY/manifest.json").use {
            val m = JSONObject(it.bufferedReader().readText())
            require(m.getInt("schemaVersion") == 1 && m.getInt("width") == 256 && m.getInt("height") == 384 &&
                m.getInt("frames") == 8 && m.getInt("rows") == 2 && m.getInt("items") == 39 &&
                m.getString("sourceSha256") == ApprovedAvatarRenderer.SHA256)
            m.getJSONObject("files").also { f ->
                val expected = ExpandedAtelierLayers.specs.fold(0) { count, s -> count + if (s.tinted) 32 else 16 }
                require(f.length() == expected)
                for (s in ExpandedAtelierLayers.specs) for (g in AvatarGender.entries) for (n in 0..7) {
                    require(f.getString("${s.key}/${g.wireValue}-$n.png").matches(Regex("[a-f0-9]{64}")))
                    if (s.tinted) require(f.getString("${s.key}/${g.wireValue}-$n-tint.png").matches(Regex("[a-f0-9]{64}")))
                }
                hashes = f
            }
        }
        fun read(name: String) = readAtelierCel(context, DIRECTORY, name, files.getString(name))
        return Cel(read("$stem.png"), if (spec.tinted) read("$stem-tint.png") else null).also { cache.put(stem,it) }
    }

    fun render(context: Context, pixels: IntArray, original: IntArray, fabric: IntArray,
        coverage: IntArray, socks: IntArray, hands: IntArray, frame: Int, requested: AvatarStyle,
        foundation: AvatarStyle, equipment: Boolean, onlySlot: String? = null): IntArray {
        val gearOrder = listOf("headwear", "hydration", "waist", "wrist", "ear", "eyewear")
        val selected = ExpandedAtelierLayers.selected(requested).filter { (it.slot in gearOrder) == equipment && (onlySlot == null || it.slot == onlySlot) }
            .sortedBy { if (equipment) gearOrder.indexOf(it.slot) else listOf("hair","face","costume","top","bottom","socks","shoes").indexOf(it.slot) }
        var out = pixels
        for (spec in selected) {
            val cel = cel(context,spec,frame,requested.gender)
            val allowance = ExpandedAtelierLayers.allowance(spec,original,fabric,coverage,socks,hands,frame,requested.gender)
            out = ExpandedAtelierLayers.apply(out,cel.detail,cel.tint,allowance,foundation.runningKitColors)
        }
        return out
    }
}

internal fun readAtelierCel(context: Context, directory: String, name: String, sha256: String): IntArray {
    val bytes = context.assets.open("$directory/$name").use { it.readBytes() }
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    require(digest == sha256) { "Aseprite cel integrity check failed for $directory/$name" }
    val image = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inScaled = false }))
    try {
        val width = ApprovedAvatarRenderer.WIDTH
        val height = ApprovedAvatarRenderer.HEIGHT
        require(image.width == width && image.height == height)
        return IntArray(width * height).also { image.getPixels(it, 0, width, 0, 0, width, height) }
    } finally {
        image.recycle()
    }
}

package com.runmoni.app.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.security.MessageDigest

/** Immutable, source-specific garment labels. No hue/skin detection runs in the app. */
object RunningKitArtwork {
    const val DIRECTORY = "avatars/running-kit-v1"
    const val MASK_VERSION = "approved-running-kit-v1"
    private const val SHEET_WIDTH = 2048
    private const val SHEET_HEIGHT = 768
    private const val CELL_PIXELS = ApprovedAvatarRenderer.WIDTH * ApprovedAvatarRenderer.HEIGHT
    private val loadedGeneration = MutableStateFlow(0)
    val generation: StateFlow<Int> = loadedGeneration
    private var frames: Array<IntArray>? = null

    /** Call on a worker. A failed read is retryable and never replaces approved artwork. */
    fun isAvailable(context: Context): Boolean = try { load(context); true } catch (_: Exception) { false }

    @Synchronized
    internal fun frameMask(context: Context, row: Int, frame: Int): IntArray =
        load(context)[row * ApprovedAvatarRenderer.FRAMES + frame]

    @Synchronized
    private fun load(context: Context): Array<IntArray> {
        frames?.let { return it }
        val assets = context.applicationContext.assets
        fun bytes(file: String) = assets.open("$DIRECTORY/$file").use { it.readBytes() }
        fun sha(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        val manifest = JSONObject(bytes("manifest.json").toString(Charsets.UTF_8))
        require(manifest.getInt("schemaVersion") == 1 && manifest.getString("maskVersion") == MASK_VERSION)
        require(manifest.getString("sourceAsset") == ApprovedAvatarRenderer.ASSET &&
            manifest.getString("sourceSha256") == ApprovedAvatarRenderer.SHA256)
        require(manifest.getJSONArray("canvas").getInt(0) == SHEET_WIDTH && manifest.getJSONArray("canvas").getInt(1) == SHEET_HEIGHT)
        require(manifest.getJSONArray("cell").getInt(0) == ApprovedAvatarRenderer.WIDTH &&
            manifest.getJSONArray("cell").getInt(1) == ApprovedAvatarRenderer.HEIGHT &&
            manifest.getInt("frames") == 8 && manifest.getInt("rows") == 2)
        val regions = manifest.getJSONObject("regions")
        require(regions.length() == 3 && regions.getString("1") == "top" && regions.getString("2") == "bottom" && regions.getString("3") == "shoes-fabric")
        require(manifest.getString("maskFile") == "fabric-mask.png" && manifest.getString("protectedFile") == "protected-mask.png")
        require(sha(assets.open(ApprovedAvatarRenderer.ASSET).use { it.readBytes() }) == ApprovedAvatarRenderer.SHA256)
        fun decode(file: String, hashKey: String): IntArray {
            val data = bytes(file)
            require(sha(data) == manifest.getString(hashKey)) { "Running kit mask integrity check failed" }
            val options = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(data, 0, data.size, options))
            try {
                require(bitmap.width == SHEET_WIDTH && bitmap.height == SHEET_HEIGHT)
                return IntArray(SHEET_WIDTH * SHEET_HEIGHT).also { bitmap.getPixels(it, 0, SHEET_WIDTH, 0, 0, SHEET_WIDTH, SHEET_HEIGHT) }
            } finally { bitmap.recycle() }
        }
        val labels = decode("fabric-mask.png", "maskSha256")
        val protected = decode("protected-mask.png", "protectedSha256")
        val regionCounts = Array(16) { IntArray(3) }
        labels.indices.forEach { index ->
            val label = labels[index]
            require(protected[index] == 0 || protected[index] == -1) { "Invalid running kit protection label" }
            if (label == 0) return@forEach
            val region = (label ushr 16) and 255
            require((label ushr 24) == 255 && region in 1..3 && (label and 255) == 0) { "Invalid running kit garment label" }
            require(protected[index] == 0) { "Garment label overlaps protected pixels" }
            val x = index % SHEET_WIDTH; val y = index / SHEET_WIDTH
            require(y < 384 || y % 384 >= 12) { "Previous-row padding must remain protected" }
            regionCounts[(y / 384) * 8 + x / 256][region - 1]++
        }
        require(regionCounts.all { counts -> counts.all { it > 0 } }) { "Incomplete running kit masks" }
        val result = Array(16) { cell ->
            val row = cell / 8; val frame = cell % 8
            IntArray(CELL_PIXELS).also { target ->
                for (y in 0 until 384) System.arraycopy(labels, (row * 384 + y) * SHEET_WIDTH + frame * 256, target, y * 256, 256)
            }
        }
        frames = result
        loadedGeneration.value = 1
        return result
    }
}

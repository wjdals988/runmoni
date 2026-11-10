package com.runmoni.app.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.security.MessageDigest

/** Source-bound visible sock labels. Loading does not regenerate or infer a body.
 * A successful data check is distinct from acceptance of the rendered artwork.
 */
internal object SockArtwork {
    const val VERSION = "approved-socks-v1"
    private const val DIRECTORY = "avatars/socks-v1"
    private const val SOURCE_SHA = "f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65"
    private const val FABRIC_SHA = "462433ce98a7a2f52c98823d851bed462418bbab18f4262c75f6113456e74bb5"
    private const val WARDROBE_SHA = "2d0e8375a5644311b1337cd369708d5ebf35e0cef3314d6d7c2145382d650115"
    private const val CELL_PIXELS = 256 * 384
    private val minimumFabricY = intArrayOf(267,267,267,267,267,267,267,267,250,274,274,250,250,274,280,250)
    private data class Data(val labels: Array<IntArray>, val ankles: Array<BooleanArray>,
        val protection: Array<BooleanArray>)
    private var cached: Data? = null

    // Independently read source PNG samples, never derived from new sock output.
    private data class Golden(val frame: Int, val x: Int, val y: Int, val rgba: IntArray,
        val ankle: Boolean, val protected: Boolean)
    private val goldens = arrayOf(
        Golden(0,184,315,intArrayOf(253,171,82,253),true,false),
        Golden(0,211,338,intArrayOf(244,135,121,253),false,true),
        Golden(1,135,290,intArrayOf(226,155,93,253),true,false),
        Golden(1,163,347,intArrayOf(252,84,75,253),false,true),
        Golden(2,137,325,intArrayOf(246,158,83,253),true,false),
        Golden(2,150,345,intArrayOf(254,93,78,253),false,true),
        Golden(3,182,315,intArrayOf(253,223,188,253),true,false),
        Golden(3,206,338,intArrayOf(252,190,181,253),false,true),
        Golden(4,191,315,intArrayOf(254,200,154,253),true,false),
        Golden(4,216,338,intArrayOf(255,255,255,253),false,true),
        Golden(5,148,289,intArrayOf(226,138,88,253),true,false),
        Golden(5,140,345,intArrayOf(249,83,71,253),false,true),
        Golden(5,118,333,intArrayOf(245,201,173,253),false,false),
        Golden(6,124,326,intArrayOf(248,158,63,252),true,false),
        Golden(6,151,346,intArrayOf(251,90,83,252),false,true),
        Golden(7,181,308,intArrayOf(253,173,100,253),true,false),
        Golden(7,206,333,intArrayOf(251,251,252,253),false,true),
    )

    /** Call from MiniMi's worker. Failure is retryable; cache is published last. */
    @Synchronized
    internal fun frameMask(context: Context, row: Int, frame: Int): IntArray {
        require(row in 0..1 && frame in 0..7)
        return load(context).labels[row * 8 + frame]
    }

    /** Independent binary data exposed as copies for regression inspection. */
    @Synchronized
    internal fun visibleAnkleMask(context: Context, row: Int, frame: Int): IntArray {
        require(row in 0..1 && frame in 0..7)
        val mask = load(context).ankles[row * 8 + frame]
        return IntArray(CELL_PIXELS) { if (mask[it]) -1 else 0 }
    }

    @Synchronized
    internal fun shoeProtection(context: Context, row: Int, frame: Int): IntArray {
        require(row in 0..1 && frame in 0..7)
        val mask = load(context).protection[row * 8 + frame]
        return IntArray(CELL_PIXELS) { if (mask[it]) -1 else 0 }
    }

    private fun load(context: Context): Data {
        cached?.let { return it }
        val assets = context.applicationContext.assets
        fun bytes(path: String) = assets.open(path).use { it.readBytes() }
        fun sha(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }
        val manifest = JSONObject(bytes("$DIRECTORY/manifest.json").toString(Charsets.UTF_8))
        require(manifest.getInt("schemaVersion") == 1 && manifest.getString("maskVersion") == VERSION)
        require(manifest.getString("sourceAsset") == ApprovedAvatarRenderer.ASSET &&
            SOURCE_SHA == ApprovedAvatarRenderer.SHA256 && manifest.getString("sourceSha256") == SOURCE_SHA)
        require(manifest.getString("fabricMaskSha256") == FABRIC_SHA &&
            manifest.getString("wardrobeMaskSha256") == WARDROBE_SHA)
        require(manifest.getJSONArray("canvas").getInt(0) == 2048 && manifest.getJSONArray("canvas").getInt(1) == 768 &&
            manifest.getJSONArray("cell").getInt(0) == 256 && manifest.getJSONArray("cell").getInt(1) == 384 &&
            manifest.getInt("frames") == 8 && manifest.getInt("rows") == 2)
        require(manifest.getString("maskFile") == "sock-mask.png" &&
            manifest.getString("visibleAnkleFile") == "ankle-coverage.png" &&
            manifest.getString("shoeProtectionFile") == "shoe-protection.png")
        val fabricBounds = manifest.getJSONArray("minimumFabricY")
        require(fabricBounds.length() == 16 && (0 until 16).all { fabricBounds.getInt(it) == minimumFabricY[it] })
        val sourceBytes = bytes(ApprovedAvatarRenderer.ASSET)
        require(sha(sourceBytes) == SOURCE_SHA)
        require(sha(bytes("avatars/running-kit-v1/fabric-mask.png")) == FABRIC_SHA)
        require(sha(bytes("avatars/wardrobe-v1/coverage.png")) == WARDROBE_SHA)
        fun decode(data: ByteArray): Bitmap = requireNotNull(BitmapFactory.decodeByteArray(data, 0, data.size,
            BitmapFactory.Options().apply {
                inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888
                // Read exact original RGBA instead of testing reversibility of
                // premultiplication for source pixels whose alpha is252/253.
                inPremultiplied = false
            })).also { require(it.width == 2048 && it.height == 768) }
        fun decodeLabels(file: String, hashKey: String): Array<IntArray> {
            val data = bytes("$DIRECTORY/$file")
            require(sha(data) == manifest.getString(hashKey)) { "Sock label integrity check failed" }
            val bitmap = decode(data)
            return try {
                Array(16) { cell -> IntArray(CELL_PIXELS).also {
                    bitmap.getPixels(it, 0, 256, (cell % 8) * 256, (cell / 8) * 384, 256, 384)
                } }
            } finally { bitmap.recycle() }
        }
        fun decodeBinary(file: String, hashKey: String): Array<BooleanArray> {
            val data = bytes("$DIRECTORY/$file")
            require(sha(data) == manifest.getString(hashKey)) { "Sock protection integrity check failed" }
            val bitmap = decode(data)
            val buffer = IntArray(CELL_PIXELS)
            return try {
                Array(16) { cell ->
                    bitmap.getPixels(buffer, 0, 256, (cell % 8) * 256, (cell / 8) * 384, 256, 384)
                    BooleanArray(CELL_PIXELS) { index ->
                        require(buffer[index] == 0 || buffer[index] == -1) { "Invalid binary sock mask" }
                        buffer[index] == -1
                    }
                }
            } finally { bitmap.recycle() }
        }
        val labels = decodeLabels("sock-mask.png", "maskSha256")
        val ankles = decodeBinary("ankle-coverage.png", "visibleAnkleSha256")
        val protection = decodeBinary("shoe-protection.png", "shoeProtectionSha256")
        val declared = manifest.getJSONArray("knownPixels")
        require(declared.length() == goldens.size)
        goldens.forEachIndexed { index, golden ->
            val value = declared.getJSONObject(index)
            require(value.getString("gender") == "female" && value.getInt("frame") == golden.frame &&
                value.getJSONArray("point").getInt(0) == golden.x && value.getJSONArray("point").getInt(1) == golden.y &&
                value.getBoolean("visibleAnkle") == golden.ankle && value.getBoolean("shoeProtected") == golden.protected)
            val rgba = value.getJSONArray("sourceRgba")
            require(rgba.length() == 4 && (0..3).all { rgba.getInt(it) == golden.rgba[it] })
        }
        val original = decode(sourceBytes)
        try {
            val source = IntArray(CELL_PIXELS)
            for (cell in 0 until 16) {
                val cellRow = cell / 8
                val cellFrame = cell % 8
                original.getPixels(source, 0, 256, cellFrame * 256, cellRow * 384, 256, 384)
                val fabric = RunningKitArtwork.frameMask(context, cellRow, cellFrame)
                val coverage = WardrobeCoverage.frame(context, cellRow, cellFrame)
                var crew = 0; var knee = 0; var kneeOnly = 0; var visibleAnkles = 0
                labels[cell].forEachIndexed { index, label ->
                    val ankle = ankles[cell][index]
                    require(!ankle || (cellRow == 1 && (source[index] ushr 24) >= 200 &&
                        !protection[cell][index] && label != 0 &&
                        ((label ushr 16) and 255) == 3 && (label and 255) == 0)) {
                        "Every existing visible ankle must belong to both sock lengths"
                    }
                    if (ankle) visibleAnkles++
                    if (label == 0) return@forEachIndexed
                    val flags = (label ushr 16) and 255
                    val detail = label and 255
                    require((label ushr 24) == 255 && flags in 1..3 && detail in 0..7 &&
                        ((detail and 3) and flags.inv()) == 0) { "Invalid explicit sock label" }
                    require((source[index] ushr 24) > 0 && !protection[cell][index]) {
                        "Sock labels exceed source alpha or intersect footwear/sole/ink protection"
                    }
                    val region = if ((fabric[index] ushr 24) == 255) (fabric[index] ushr 16) and 255 else 0
                    val visibleMaleLeg = (coverage[index] ushr 24) == 255 && (((coverage[index] ushr 16) and 4) != 0)
                    require(if (cellRow == 0) visibleMaleLeg && region == 0 && !ankle else region == 2 || ankle) {
                        "Sock coverage exceeds pinned visible leg/tights/ankle data"
                    }
                    require(index / 256 >= if (ankle) 250 else minimumFabricY[cell]) {
                        "Sock coverage exceeds the authored upper-thigh boundary"
                    }
                    if ((flags and 1) != 0) crew++
                    if ((flags and 2) != 0) knee++
                    if (flags == 2) kneeOnly++
                }
                require(crew > 0 && knee > 0 && kneeOnly > 0) { "Incomplete/distinct sock lengths missing" }
                require(if (cellRow == 0) visibleAnkles == 0 else visibleAnkles >= 40)
                if (cellRow == 1) goldens.filter { it.frame == cellFrame }.forEach { golden ->
                    val index = golden.y * 256 + golden.x
                    val rgba = golden.rgba
                    val expected = (rgba[3] shl 24) or (rgba[0] shl 16) or (rgba[1] shl 8) or rgba[2]
                    require(source[index] == expected && ankles[cell][index] == golden.ankle &&
                        protection[cell][index] == golden.protected) { "Known source-pixel/ankle/protection regression" }
                    require(if (golden.ankle) ((labels[cell][index] ushr 16) and 255) == 3 else labels[cell][index] == 0)
                }
            }
        } finally { original.recycle() }
        return Data(labels, ankles, protection).also { cached = it }
    }
}

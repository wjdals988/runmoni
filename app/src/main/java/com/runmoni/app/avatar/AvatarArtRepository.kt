package com.runmoni.app.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Local, original-resolution PNG parts. Missing artwork is a diagnostic error, never old-art fallback. */
class AvatarArtRepository(
    context: Context,
    private val assetDirectory: String = "avatars/v0.4.0",
) {
    data class Point(val x: Float, val y: Float)
    data class Part(val id: String, val bitmap: Bitmap, val pivot: Point, val tip: Point?)
    data class Top(val part: String?, val palette: String?, val upperArmCoverage: Float, val forearmCoverage: Float)
    data class Bottom(val part: String?, val palette: String?, val thighCoverage: Float, val shinCoverage: Float)
    data class Shoe(val part: String, val palette: String?)
    data class Costume(
        val torso: String?, val pelvis: String?, val hoodFront: String?, val hoodBack: String?,
        val palette: String?, val upperArmCoverage: Float, val forearmCoverage: Float,
        val thighCoverage: Float, val shinCoverage: Float, val shoePalette: String?,
        val shoePart: String?, val covers: Set<String>,
        val upperArmPart: String? = null, val forearmPart: String? = null,
        val thighPart: String? = null, val shinPart: String? = null, val jointPart: String? = null,
    )

    private val assets = context.applicationContext.assets
    private val manifest: JSONObject by lazy {
        assets.open("$assetDirectory/manifest.json").bufferedReader().use { JSONObject(it.readText()) }.also {
            val canvas = it.getJSONArray("canvas")
            require(canvas.getInt(0) == 256 && canvas.getInt(1) == 384) { "Avatar canvas must be 256x384" }
            require(it.optInt("frameMillis", 90) == 90) { "Avatar clip must use 90ms frames" }
        }
    }
    private val sources = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val variants = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val skinPalette: IntArray by lazy { colors(manifest.getJSONArray("skinPalette")) }

    fun hasPart(id: String): Boolean = manifest.getJSONObject("parts").has(id)

    @Synchronized
    fun part(id: String): Part {
        val definition = definition(id)
        val bitmap = sources.get(id) ?: run {
            val file = definition.getString("file")
            require(file.isNotBlank() && !file.contains("..") && !file.startsWith("/") && !file.contains(":")) {
                "Invalid local avatar asset path for $id"
            }
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = assets.open("$assetDirectory/$file").use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("Avatar PNG could not be decoded: $id ($file)")
            sources.put(id, decoded)
            decoded
        }
        return Part(id, bitmap, point(definition.getJSONArray("pivot")), definition.optJSONArray("tip")?.let(::point))
    }

    /**
     * Coverage follows the PNG's pivot->tip axis. Outside a garment's coverage the
     * original skin stays intact. Forearm pixels beyond the wrist tip never change.
     * An overlay has transparent pixels outside its range (e.g. socks over tights).
     */
    @Synchronized
    fun coloredPart(
        id: String,
        palette: String?,
        start: Float = 0f,
        end: Float = 1f,
        overlay: Boolean = false,
        preserveBeyondTip: Boolean = false,
    ): Part {
        val original = part(id)
        if (palette == null || (!overlay && end <= start)) return original
        val key = "$id|$palette|$start|$end|$overlay|$preserveBeyondTip"
        val cached = variants.get(key)
        if (cached != null) return original.copy(bitmap = cached)
        val definition = definition(id)
        val customSource = definition.optJSONArray("sourcePalette")
        val sourceColors = customSource?.let(::colors) ?: skinPalette
        val target = this.palette(palette)
        val width = original.bitmap.width
        val height = original.bitmap.height
        val pixels = IntArray(width * height)
        original.bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val tip = original.tip
        val dx = (tip?.x ?: original.pivot.x) - original.pivot.x
        val dy = (tip?.y ?: original.pivot.y) - original.pivot.y
        val lengthSquared = dx * dx + dy * dy
        val tolerance = definition.optDouble("paletteTolerance", 38.0).toFloat().coerceIn(0f, 255f)
        pixels.indices.forEach { index ->
            val color = pixels[index]
            if (Color.alpha(color) == 0) return@forEach
            val x = index % width + .5f
            val y = index / width + .5f
            val along = if (lengthSquared < .01f) .5f else
                ((x - original.pivot.x) * dx + (y - original.pivot.y) * dy) / lengthSquared
            val inRange = along >= (if (start == 0f) -.35f else start) && along <= end &&
                !(preserveBeyondTip && along >= 1f)
            if (!inRange) {
                if (overlay) pixels[index] = Color.TRANSPARENT
            } else if (shouldRecolor(color, sourceColors, customSource == null, tolerance)) {
                pixels[index] = rampColor(color, sourceColors, target)
            }
        }
        val changed = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        variants.put(key, changed)
        return original.copy(bitmap = changed)
    }

    fun top(variant: Int): Top {
        val json = recipe("tops", variant, 6)
        val part = requireNotNull(json.optionalString("part")) { "Missing original PNG for tops:$variant" }
        return Top(part, json.optionalString("palette"),
            json.coverage("upperArmCoverage"), json.coverage("forearmCoverage"))
    }

    fun bottom(variant: Int): Bottom {
        val json = recipe("bottoms", variant, 6)
        val part = requireNotNull(json.optionalString("part")) { "Missing original PNG for bottoms:$variant" }
        return Bottom(part, json.optionalString("palette"),
            json.coverage("thighCoverage"), json.coverage("shinCoverage"))
    }

    fun shoe(variant: Int): Shoe {
        val json = recipe("shoes", variant, 4)
        return Shoe(json.optString("part", "shoe_base"), json.optionalString("palette"))
    }

    fun costume(variant: Int): Costume? {
        if (variant == 0) return null
        val json = recipe("costumes", variant, 6)
        val covers = json.optJSONArray("covers")?.let { array ->
            (0 until array.length()).map { array.getString(it) }.toSet()
        } ?: when (variant) {
            1, 4, 5 -> setOf("headwear", "top", "bottom", "socks", "shoes")
            else -> setOf("top", "bottom")
        }
        return Costume(json.optionalString("torso"), json.optionalString("pelvis"),
            json.optionalString("hoodFront"), json.optionalString("hoodBack"), json.optionalString("palette"),
            json.coverage("upperArmCoverage"), json.coverage("forearmCoverage"),
            json.coverage("thighCoverage"), json.coverage("shinCoverage"), json.optionalString("shoePalette"),
            json.optionalString("shoePart"), covers,
            json.optionalString("upperArmPart"), json.optionalString("forearmPart"),
            json.optionalString("thighPart"), json.optionalString("shinPart"), json.optionalString("jointPart"))
    }

    fun palette(name: String): IntArray {
        val palettes = manifest.getJSONObject("palettes")
        require(palettes.has(name)) { "Missing avatar palette '$name'" }
        return colors(palettes.getJSONArray(name))
    }

    /** Public preflight can run on a worker before UI publication. No artwork is downloaded. */
    fun validateDefinitions() {
        listOf("head_0", "head_1", "head_2", "head_3", "skin_torso", "skin_pelvis",
            "skin_upper_arm", "skin_forearm", "skin_thigh", "skin_shin", "shoe_base", "joint_skin").forEach { definition(it) }
        for (hair in 0..3) definition("hair_${hair}_front")
        for (variant in 0..5) {
            top(variant).part?.let(::definition)
            bottom(variant).part?.let(::definition)
            if (variant > 0) costume(variant)?.let { suit ->
                listOfNotNull(suit.torso, suit.pelvis, suit.hoodBack, suit.hoodFront, suit.shoePart,
                    suit.upperArmPart, suit.forearmPart, suit.thighPart, suit.shinPart, suit.jointPart).forEach { definition(it) }
            }
        }
        for (variant in 0..3) definition(shoe(variant).part)
    }

    private fun definition(id: String): JSONObject {
        val parts = manifest.getJSONObject("parts")
        require(parts.has(id)) { "Missing high-quality avatar part '$id' in $assetDirectory" }
        return parts.getJSONObject(id)
    }

    private fun recipe(name: String, value: Int, count: Int): JSONObject {
        require(value in 0 until count) { "Invalid $name avatar variant $value" }
        val array = manifest.getJSONArray(name)
        require(array.length() == count && !array.isNull(value)) { "Incomplete avatar recipe $name:$value" }
        return array.getJSONObject(value)
    }

    private fun colors(array: JSONArray): IntArray {
        require(array.length() >= 2) { "Avatar shade ramp must contain at least 2 colors" }
        return IntArray(array.length()) { Color.parseColor(array.getString(it)) }
    }

    private fun point(array: JSONArray) = Point(array.getDouble(0).toFloat(), array.getDouble(1).toFloat())
    private fun JSONObject.optionalString(key: String): String? = if (!has(key) || isNull(key)) null else getString(key).takeIf { it.isNotBlank() }
    private fun JSONObject.coverage(key: String) = optDouble(key, 0.0).toFloat().also {
        require(it.isFinite() && it in 0f..1f) { "Invalid avatar coverage $key=$it" }
    }

    private fun shouldRecolor(color: Int, source: IntArray, skin: Boolean, tolerance: Float): Boolean {
        val red = Color.red(color); val green = Color.green(color); val blue = Color.blue(color)
        // Preserve navy/black outlines and neutral white highlights/soles.
        if (max(red, max(green, blue)) < 68 || (red + green + blue < 205 && blue >= red)) return false
        if (min(red, min(green, blue)) > 175 && max(red, max(green, blue)) - min(red, min(green, blue)) < 19) return false
        val nearest = source.minOf { sample ->
            val dr = red - Color.red(sample); val dg = green - Color.green(sample); val db = blue - Color.blue(sample)
            dr * dr + dg * dg + db * db
        }
        val close = nearest <= tolerance * tolerance
        val warmSkin = red > 78 && green > 33 && red > green * 1.04f && red - blue > 18 && green >= blue * .94f
        return if (skin) warmSkin || close else close
    }

    private fun rampColor(color: Int, source: IntArray, target: IntArray): Int {
        fun light(c: Int) = Color.red(c) * .2126f + Color.green(c) * .7152f + Color.blue(c) * .0722f
        val low = source.minOf(::light); val high = source.maxOf(::light)
        val t = ((light(color) - low) / (high - low).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val position = t * (target.size - 1)
        val index = position.toInt().coerceIn(0, target.lastIndex)
        val next = (index + 1).coerceAtMost(target.lastIndex)
        val fraction = position - index
        fun blend(a: Int, b: Int) = (a + (b - a) * fraction).roundToInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), blend(Color.red(target[index]), Color.red(target[next])),
            blend(Color.green(target[index]), Color.green(target[next])), blend(Color.blue(target[index]), Color.blue(target[next])))
    }
}

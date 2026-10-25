package com.runmoni.app.avatar

import android.content.Context
import android.graphics.Bitmap
import com.runmoni.app.domain.AvatarCatalogue
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.withItem
import com.runmoni.hostpreview.PreviewImages
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Runs the unchanged production repository/renderer; only Android drawing APIs are adapted. */
fun main(arguments: Array<String>) {
    require(arguments.size == 2) { "Usage: HostPreviewKt ASSET_ROOT OUTPUT_DIRECTORY" }
    val output = Path.of(arguments[1]).toAbsolutePath()
    Files.createDirectories(output)
    val art = AvatarArtRepository(Context(Path.of(arguments[0])))
    art.validateDefinitions()
    val renderer = AvatarSpriteRenderer(art)
    val allLandmarks = (0 until 8).map { renderer.debugLandmarks(it, true) }
    check(AvatarSpriteRenderer.frameAt(-1L) == 0) { "Negative elapsed time must select frame 0" }
    check(AvatarSpriteRenderer.frameAt(719L) == 7) { "719ms must select frame 7" }
    check(AvatarSpriteRenderer.frameAt(720L) == 0) { "720ms must wrap to frame 0" }
    val wrapChecks = mutableListOf<String>()
    for (side in listOf("near", "far")) {
        check(allLandmarks.map { it.getValue("${side}Wrist") }.toSet().size == 8) { "$side wrist does not have 8 distinct poses" }
        val wristX = allLandmarks.map { it.getValue("${side}Wrist").x }
        check(wristX.max() - wristX.min() >= 50f) { "$side wrist forward/back excursion is less than 50px" }
        allLandmarks.forEach { points ->
            fun length(first: String, second: String, expected: Float) {
                val a = points.getValue(side + first); val b = points.getValue(side + second)
                check(abs(hypot(b.x - a.x, b.y - a.y) - expected) < .05f) { "$side $first->$second bone changed length" }
            }
            length("Shoulder", "Elbow", 40f); length("Elbow", "Wrist", 34f)
            length("Hip", "Knee", 50f); length("Knee", "Ankle", 57f)
        }
        for (joint in listOf("Wrist", "Ankle")) {
            val name = side + joint
            fun distance(first: Int, second: Int): Float {
                val a = allLandmarks[first].getValue(name); val b = allLandmarks[second].getValue(name)
                return hypot(b.x - a.x, b.y - a.y)
            }
            val largestOrdinaryStep = (0 until 7).maxOf { distance(it, it + 1) }
            val wrapStep = distance(7, 0)
            val epsilon = .01f
            wrapChecks += "{\"joint\":\"$name\",\"wrapDistancePx\":$wrapStep,\"maximumOtherAdjacentDistancePx\":$largestOrdinaryStep,\"epsilonPx\":$epsilon}"
            check(wrapStep <= largestOrdinaryStep + epsilon) {
                "$name frame 7->0 jumps $wrapStep px, exceeding other adjacent steps $largestOrdinaryStep px"
            }
        }
    }
    Files.writeString(output.resolve("wrap-checks.json"), wrapChecks.joinToString(",\n", "[\n", "\n]\n"))
    val landmarkJson = allLandmarks.mapIndexed { frame, points ->
        "{\"frame\":$frame,\"points\":{" + points.entries.joinToString(",") { (name, point) ->
            "\"$name\":[${point.x},${point.y}]"
        } + "}}"
    }.joinToString(",\n", "[\n", "\n]\n")
    Files.writeString(output.resolve("landmarks.json"), landmarkJson)
    val male = AvatarStyle(hair = 0, top = 0, bottom = 0)
    val female = AvatarStyle(hair = 2, top = 1, bottom = 1, shoes = 1)
    val clips = linkedMapOf("male" to male, "female" to female)
    for (costume in 1..5) clips["costume_$costume"] = male.copy(costume = costume)
    var frameCount = 0
    var connectedCount = 0
    val componentChecks = mutableListOf<PreviewFrameMetric>()
    for ((name, avatar) in clips) {
        val frames = (0 until 8).map { frame ->
            renderer.render(avatar, frame, true).also { bitmap ->
                PreviewImages.png(bitmap, output.resolve("$name/frame_$frame.png"))
                componentChecks += PreviewFrameMetric(name, frame, alphaComponents(bitmap))
                check(alphaConnected(bitmap, allLandmarks[frame].getValue("neck"))) {
                    "$name frame $frame has no opaque connection between head/neck and chest anchors"
                }
                frameCount++; connectedCount++
            }
        }
        check(frames.map(::pixelHash).toSet().size == 8) { "$name did not render 8 distinct pixel frames" }
        PreviewImages.sheet(frames, (0 until 8).map { "$name frame $it - HOST Java2D" }, 8, output.resolve("$name-strip.png"))
        PreviewImages.gif(frames, output.resolve("$name.gif"))
        if (name == "male" || name == "female")
            PreviewImages.png(renderer.render(avatar, 0, false), output.resolve("$name/idle.png"))
    }
    // Export every frame before rejecting a detached piece, so diagnostic PNGs
    // remain available. Boundary contact is a metric, not an automatic failure:
    // a hairstyle or garment may intentionally touch a source canvas edge.
    val largestDetached = componentChecks.maxOf { it.metrics.largestDetached }
    val detachedFrames = componentChecks.count { it.metrics.largestDetached > 24 }
    val boundaryContactFrames = componentChecks.count { it.metrics.boundaryPixels > 0 }
    Files.writeString(output.resolve("component-boundary-checks.json"), componentChecks.joinToString(",\n", "[\n", "\n]\n") { record ->
        val m = record.metrics
        "{\"clip\":\"${record.clip}\",\"frame\":${record.frame},\"connectivity\":8,\"alphaMinimum\":16,\"ignoredComponentAreaAtMost\":24,\"componentCount\":${m.count},\"largestComponentPixels\":${m.largest},\"largestDetachedComponentPixels\":${m.largestDetached},\"majorDetachedComponents\":${m.majorDetachedCount},\"foregroundBoundsInclusive\":[${m.left},${m.top},${m.right},${m.bottom}],\"canvasBoundaryPixelCount\":${m.boundaryPixels},\"boundaryContactIsInformational\":true}"
    })
    check(detachedFrames == 0) {
        "$detachedFrames/56 frames have a detached alpha>=16 component larger than 24 pixels; largest=$largestDetached. Inspect component-boundary-checks.json and PNGs"
    }
    val recipeFrames = mutableListOf<Bitmap>()
    val recipeLabels = mutableListOf<String>()
    val baseHash = pixelHash(renderer.render(male, 0, true))
    AvatarCatalogue.slots.forEach { slot ->
        slot.variants.indices.forEach { variant ->
            val bitmap = renderer.render(male.withItem(slot.key, variant), 0, true)
            PreviewImages.png(bitmap, output.resolve("catalogue/${slot.key}_$variant.png"))
            recipeFrames += bitmap; recipeLabels += "${slot.key}:$variant - HOST Java2D"
            if (variant == 1) check(pixelHash(bitmap) != baseHash) { "${slot.key}:1 did not visibly change the base avatar" }
        }
    }
    check(recipeFrames.size == 58) { "Catalogue changed: expected 58 recipes, got ${recipeFrames.size}; update preview coverage explicitly" }
    PreviewImages.sheet(recipeFrames, recipeLabels, 8, output.resolve("catalogue-58.png"))
    var normalizationChecks = 0
    AvatarCatalogue.slots.forEach { slot ->
        for (value in listOf(-1, Int.MIN_VALUE, Int.MAX_VALUE, 10000)) {
            val expected = male.withItem(slot.key, Math.floorMod(value, slot.variants.size))
            val actual = rawVariant(male, slot.key, value)
            check(pixelHash(renderer.render(actual, 0, true)) == pixelHash(renderer.render(expected, 0, true))) {
                "Saved integer normalization differs for ${slot.key}:$value"
            }
            normalizationChecks++
        }
    }
    val faceReference = facePixels(renderer.render(male, 0, true))
    // Compare the original head's opaque foreground, not transparent under-chin
    // pixels whose background legitimately changes with hair. Source alpha 254
    // can introduce a small RGB compositing delta even without any occlusion.
    val faceMask = opaqueFaceMask(art.part("head_0"), allLandmarks[0].getValue("neck"))
    check(faceMask.size >= 100) { "Shared-face ROI has only ${faceMask.size} source pixels with alpha >= 253; at least 100 are required" }
    data class FaceResult(val hair: Int, val costume: Int, val maximumRgbDelta: Int, val outsideTolerance: Int)
    val faceResults = (0..3).flatMap { hair -> (0..5).map { costume ->
        val actual = facePixels(renderer.render(male.copy(hair = hair, costume = costume), 0, true))
        val differences = faceMask.map { index ->
            val baseline = faceReference[index]; val candidate = actual[index]
            maxOf(abs((baseline ushr 16 and 255) - (candidate ushr 16 and 255)),
                abs((baseline ushr 8 and 255) - (candidate ushr 8 and 255)),
                abs((baseline and 255) - (candidate and 255)))
        }
        FaceResult(hair, costume, differences.max(), differences.count { it > 3 })
    } }
    Files.writeString(output.resolve("face-roi-checks.json"), faceResults.joinToString(",\n", "[\n", "\n]\n") {
        "{\"hair\":${it.hair},\"costume\":${it.costume},\"sourceHeadAlphaMinimum\":253,\"maskedPixels\":${faceMask.size},\"allowedRgbChannelDelta\":3,\"maximumRgbChannelDelta\":${it.maximumRgbDelta},\"pixelsOutsideTolerance\":${it.outsideTolerance},\"sameOpaqueCheeksMouthRoi\":${it.outsideTolerance == 0}}"
    })
    check(faceResults.all { it.outsideTolerance == 0 }) { "Hair/hood changes the opaque shared cheeks/mouth ROI [151,149,185,165] beyond RGB channel delta 3; inspect face-roi-checks.json and rendered PNGs" }
    Files.writeString(output.resolve("checks.json"), """
        {
          "surface": "HOST Java2D compatibility preview; NOT Android capture",
          "productionKotlinSourcesCompiledUnchanged": true,
          "distinctNearWristPoses": 8, "distinctFarWristPoses": 8,
          "boneLengthChecks": 64, "distinctFramesPerClip": 8,
          "wrapJointChecks": 4, "wrapDistanceEpsilonPx": 0.01,
          "frameAtBoundaryChecks": {"minus1":0,"at719":7,"at720":0},
          "minimumWristHorizontalExcursionPx": 50, "visiblyChangingSlotVariantOneChecks": 13,
          "savedIntegerNormalizationChecks": $normalizationChecks,
          "faceCheeksMouthRoi": [151,149,185,165], "faceRoiHairCostumeChecks": 24,
          "faceRoiSourceHeadAlphaMinimum": 253, "faceRoiMinimumMaskPixels": 100,
          "faceRoiActualMaskPixels": ${faceMask.size}, "faceRoiAllowedRgbChannelDelta": 3,
          "faceRoiToleranceReason": "Head alpha 254 can composite over different hair backgrounds with minor RGB variation; opaque foreground remains checked",
          "runningClips": ${clips.size}, "runningPngFrames": $frameCount,
          "catalogueRecipes": ${recipeFrames.size}, "alphaConnectivityChecks": $connectedCount,
          "detachedComponentFrameChecks": ${componentChecks.size}, "componentConnectivity": 8,
          "componentAlphaMinimum": 16, "ignoredDetachedComponentAreaAtMost": 24,
          "largestDetachedComponentPixels": $largestDetached,
          "framesWithCanvasBoundaryContact": $boundaryContactFrames,
          "boundaryMetricLimitation": "Contact and foreground bounds are reported, not treated as proof of clipping or an automatic failure",
          "alphaConnectivityLimitation": "Opaque head-to-chest component only, not skin continuity or visual seam quality",
          "unverified": ["Android rasterization", "Compose playback", "lifecycle", "physical device", "visual acceptance"]
        }
    """.trimIndent() + "\n")
    Files.writeString(output.resolve("README.md"), """
        # Host preview of production avatar parts

        This is a **HOST Java2D compatibility preview, not an Android screenshot**.
        The current production AvatarArtRepository.kt and AvatarSpriteRenderer.kt are compiled unchanged.
        AvatarStyle is extracted from current Models.kt; current AvatarCatalogue.kt is compiled unchanged.
        Android bitmap/canvas/assets/cache and JSON calls are supplied by local host adapters.
        Drawing uses nearest-neighbor transforms with antialiasing disabled. Java2D and Android
        may rasterize rotations, alpha edges and path boundaries differently. PNGs are lossless;
        GIFs use a convenience palette and a 90 ms delay, so compare PNGs for exact host output.

        - male.gif / female.gif: 8-frame running previews (presentation names, no gender restrictions).
        - costume_1.gif through costume_5.gif: 5 special sets on the same rig.
        - *-strip.png: 8-frame contact sheets; catalogue-58.png: every visible recipe.
        - landmarks.json: actual renderer pose outputs; checks.json: bounded host assertions.
        - wrap-checks.json: near/far wrist and ankle 7-to-0 movement cannot exceed the
          largest other adjacent step by more than 0.01 px. frameAt checks cover -1/719/720 ms.
        - component-boundary-checks.json: all 56 frames use 8-neighbor alpha >= 16
          components. Beyond the largest body component, any component over 24 pixels
          fails. Smaller fragments are reported but ignored. This catches large floating
          body/clothing pieces, and can also flag an intentionally detached graphic.
          Foreground bounds and canvas-edge pixel counts are informational: boundary
          contact alone does not prove clipping, and safe padding alone cannot prove no
          pixels were lost before these PNG parts reached the renderer.
        - face-roi-checks.json: 24 cheeks/mouth ROI comparisons across 4 hairs and 6 sets.
          Only source head_0 pixels with alpha >= 253 are included; the mask must contain
          at least 100 pixels. Every masked pixel must stay within 3 RGB values per channel.
          This admits minor alpha-254 background compositing differences, while recording
          the largest difference and every pixel exceeding tolerance to detect actual occlusion.
        - source-provenance.json: source and manifest hashes recorded by the wrapper script.
        - run-status.json must say passed for this run; failed/running means partial diagnostic output.

        Alpha connectivity detects an opaque gap between central head/neck and chest regions.
        It does not prove skin-color continuity or attractive layering. Human visual review remains necessary.
        No AVD, Android runtime, network, installation or app-source modification is involved.
    """.trimIndent() + "\n")
    val cards = clips.keys.joinToString("\n") { "<figure><img src=\"$it.gif\" width=\"256\" height=\"384\" alt=\"$it\"><figcaption>$it · HOST Java2D · 8 × 90 ms</figcaption></figure>" }
    Files.writeString(output.resolve("index.html"), """
        <!doctype html><html lang="en"><meta charset="utf-8"><title>Runmoni host preview</title>
        <style>body{font:16px system-ui;background:#f3eee5;color:#172238;margin:24px}main{display:flex;flex-wrap:wrap}figure{margin:12px}img{image-rendering:pixelated}figcaption{max-width:256px}</style>
        <h1>HOST Java2D preview</h1><p>Actual production Kotlin rendering logic. Not an Android capture. PNGs and checks are adjacent.</p>
        <main>$cards</main><p><a href="catalogue-58.png">All 58 recipes</a> · <a href="checks.json">Host checks</a></p></html>
    """.trimIndent())
    println("HOST Java2D export complete: $frameCount running PNGs + 58 catalogue PNGs + 2 idle PNGs; ${clips.size} GIFs. $output")
}

private fun pixelHash(bitmap: Bitmap): Int {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return pixels.contentHashCode()
}

private fun facePixels(bitmap: Bitmap): IntArray = IntArray(34 * 16).also {
    bitmap.getPixels(it, 0, 34, 151, 149, 34, 16)
}

private data class PreviewFrameMetric(val clip: String, val frame: Int, val metrics: AlphaComponents)

private data class AlphaComponents(
    val count: Int, val largest: Int, val largestDetached: Int, val majorDetachedCount: Int,
    val left: Int, val top: Int, val right: Int, val bottom: Int, val boundaryPixels: Int,
)

/** Ignore tiny fringe islands; all larger opaque islands must join the main body. */
private fun alphaComponents(bitmap: Bitmap): AlphaComponents {
    val width = bitmap.width; val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val visited = BooleanArray(pixels.size)
    val queue = IntArray(pixels.size)
    val areas = mutableListOf<Int>()
    var left = width; var top = height; var right = -1; var bottom = -1; var boundaryPixels = 0
    for (index in pixels.indices) {
        if ((pixels[index] ushr 24) < 16) continue
        val x = index % width; val y = index / width
        left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
        if (x == 0 || x == width - 1 || y == 0 || y == height - 1) boundaryPixels++
        if (visited[index]) continue
        var head = 0; var tail = 0
        queue[tail++] = index; visited[index] = true
        while (head < tail) {
            val current = queue[head++]; val cx = current % width; val cy = current / width
            for (ny in maxOf(0, cy - 1)..minOf(height - 1, cy + 1)) {
                for (nx in maxOf(0, cx - 1)..minOf(width - 1, cx + 1)) {
                    val next = ny * width + nx
                    if (!visited[next] && (pixels[next] ushr 24) >= 16) {
                        visited[next] = true; queue[tail++] = next
                    }
                }
            }
        }
        areas += tail
    }
    areas.sortDescending()
    return AlphaComponents(areas.size, areas.firstOrNull() ?: 0, areas.getOrNull(1) ?: 0,
        areas.drop(1).count { it > 24 }, if (right < 0) -1 else left, if (bottom < 0) -1 else top,
        right, bottom, boundaryPixels)
}

private fun opaqueFaceMask(head: AvatarArtRepository.Part, neck: AvatarArtRepository.Point): List<Int> {
    val bitmap = head.bitmap
    val source = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(source, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val left = neck.x - head.pivot.x; val top = neck.y - head.pivot.y
    return (0 until 34 * 16).filter { index ->
        val x = floor(151 + index % 34 + .5f - left).toInt()
        val y = floor(149 + index / 34 + .5f - top).toInt()
        x in 0 until bitmap.width && y in 0 until bitmap.height && (source[y * bitmap.width + x] ushr 24) >= 253
    }
}

private fun rawVariant(base: AvatarStyle, slot: String, value: Int): AvatarStyle = when (slot) {
    "hair" -> base.copy(hair = value); "headwear" -> base.copy(headwear = value)
    "face" -> base.copy(face = value); "top" -> base.copy(top = value)
    "bottom" -> base.copy(bottom = value); "costume" -> base.copy(costume = value)
    "socks" -> base.copy(socks = value); "shoes" -> base.copy(shoes = value)
    "ear" -> base.copy(ear = value); "eyewear" -> base.copy(eyewear = value)
    "wrist" -> base.copy(wrist = value); "waist" -> base.copy(waist = value)
    "hydration" -> base.copy(hydration = value)
    else -> error("Unknown host recipe: $slot")
}

/** Four-neighbor opaque connectivity only; intentionally does not claim a skin-color seam test. */
private fun alphaConnected(bitmap: Bitmap, neck: AvatarArtRepository.Point): Boolean {
    val width = bitmap.width; val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    fun anchor(yOffset: Int): Int? {
        val cx = neck.x.roundToInt(); val cy = neck.y.roundToInt() + yOffset
        for (radius in 0..8) for (y in cy - radius..cy + radius) for (x in cx - radius..cx + radius) {
            if (x in 0 until width && y in 0 until height && pixels[y * width + x] ushr 24 > 0) return y * width + x
        }
        return null
    }
    val start = anchor(-20) ?: return false
    val end = anchor(30) ?: return false
    val visited = BooleanArray(pixels.size)
    val queue = IntArray(pixels.size); var head = 0; var tail = 0
    queue[tail++] = start; visited[start] = true
    while (head < tail) {
        val current = queue[head++]
        if (current == end) return true
        val x = current % width; val y = current / width
        fun add(next: Int) {
            if (!visited[next] && pixels[next] ushr 24 > 0) { visited[next] = true; queue[tail++] = next }
        }
        if (x > 0) add(current - 1)
        if (x + 1 < width) add(current + 1)
        if (y > 0) add(current - width)
        if (y + 1 < height) add(current + width)
    }
    return false
}

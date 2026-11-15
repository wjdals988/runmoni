package com.runmoni.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import android.view.TextureView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.LiveRunner
import com.runmoni.app.domain.RouteGeometry
import com.runmoni.app.domain.TrackPoint
import com.runmoni.app.domain.VectorMapFailure
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** Opt-in online SDK fixture. Uses the unmodified production factory/HTTPS client
 * and public OpenFreeMap style. All four route points and the runner are synthetic;
 * no GPS, Firebase, profile, recording service or MainActivity is involved. */
class NativeVectorMapOnlineUiTest : RunmoniDesignTestBase() {
    private data class Camera(val latitude: Double, val longitude: Double, val zoom: Double) {
        fun json() = JSONObject().put("latitude", latitude).put("longitude", longitude).put("zoom", zoom)
    }

    @Test fun publicNativeMapRendersSyntheticRouteAndDressedRunnerAndKeepsGestureCamera() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val began = SystemClock.uptimeMillis()
        val deadline = began + 120_000L
        val directory = File(context.cacheDir, "design-review").apply { mkdirs() }
        val observations = File(directory, "native-online-observations.json")
        val stages = JSONArray()
        val failure = AtomicReference<VectorMapFailure?>(null)
        val nativeView = AtomicReference<MapView?>(null)
        val nativeMap = AtomicReference<MapLibreMap?>(null)
        val constructions = AtomicInteger()
        val fullyRenderedMaps = AtomicInteger()
        val renderedFrames = AtomicInteger()
        val cameraIdles = AtomicInteger()
        val gestureStarts = AtomicInteger()
        val route = listOf(
            TrackPoint(0, 37.5655, 126.9758, 5f, 1_000L, segment = 0),
            TrackPoint(1, 37.5670, 126.9790, 5f, 2_000L, segment = 0),
            TrackPoint(2, 37.5692, 126.9813, 5f, 3_000L, segment = 1),
            TrackPoint(3, 37.5700, 126.9830, 5f, 4_000L, segment = 1),
        )
        val runner = mutableStateOf(LiveRunner("synthetic-native-runner", "합성 러너", 37.5671, 126.9790,
            0L, avatar = AvatarStyle(gender = AvatarGender.FEMALE, costume = 1, shoes = 2, wrist = 2)))
        var imageId = runnerMapImageId(runner.value)
        assertEquals("Names and positions must reuse the registered image key", imageId,
            runnerMapImageId(runner.value.copy(name = "이름 변경", latitude = 0.0, longitude = 0.0, updatedAtMillis = 123L)))
        val rendered = MapView.OnDidFinishRenderingMapListener { fully -> if (fully) fullyRenderedMaps.incrementAndGet() }
        val frameRendered = MapView.OnDidFinishRenderingFrameListener { fully, _, _ ->
            if (fully) renderedFrames.incrementAndGet()
        }
        val idle = MapLibreMap.OnCameraIdleListener { cameraIdles.incrementAndGet() }
        val moved = MapLibreMap.OnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) gestureStarts.incrementAndGet()
        }
        var completed = false
        var initialIcon: Bitmap? = null
        var failureClass: String? = null

        fun <T> onUi(block: () -> T): T {
            val answer = AtomicReference<T>()
            instrumentation.runOnMainSync { answer.set(block()) }
            return answer.get()
        }
        fun remaining(): Long = (deadline - SystemClock.uptimeMillis()).also {
            assertTrue("The shared120s observation budget expired; this is not a provider SLA", it > 0)
        }
        fun await(label: String, condition: () -> Boolean) {
            try {
                compose.waitUntil(remaining()) {
                    assertNull("Production native pane reported a failure: ${failure.get()}", failure.get())
                    onUi(condition)
                }
            } catch (error: Throwable) {
                stages.put(JSONObject().put("stage", label).put("conditionSatisfied", false)
                    .put("elapsedMillis", SystemClock.uptimeMillis() - began))
                throw error
            }
        }
        fun camera(): Camera = onUi {
            val position = requireNotNull(nativeMap.get()).cameraPosition
            val target = requireNotNull(position.target)
            Camera(target.latitude, target.longitude, position.zoom)
        }
        fun record(stage: String, camera: Camera? = null) {
            stages.put(JSONObject().put("stage", stage).put("elapsedMillis", SystemClock.uptimeMillis() - began)
                .put("fullyRenderedMapCallbacks", fullyRenderedMaps.get())
                .put("fullyRenderedFrameCallbacks", renderedFrames.get()).put("cameraIdleCallbacks", cameraIdles.get())
                .put("gestureStartCallbacks", gestureStarts.get()).apply { camera?.let { put("camera", it.json()) } })
        }
        fun recordGestureGeometry(stage: String, bounds: android.graphics.Rect, fraction: Float) {
            fun rectangle(rect: android.graphics.Rect) = JSONObject().put("left", rect.left).put("top", rect.top)
                .put("right", rect.right).put("bottom", rect.bottom)
            val nativeBounds = onUi {
                val rect = android.graphics.Rect()
                Pair(nativeView.get()?.getGlobalVisibleRect(rect) == true, rect)
            }
            stages.put(JSONObject().put("stage", stage).put("elapsedMillis", SystemClock.uptimeMillis() - began)
                .put("accessibilityViewportBoundsPx", rectangle(bounds)).put("nativeMapViewVisible", nativeBounds.first)
                .put("nativeMapViewGlobalVisibleBoundsPx", rectangle(nativeBounds.second))
                .put("gestureMarginFraction", .12f).put("gestureFraction", fraction).put("pixelsPerSecond", 350))
        }
        fun verifyVisibleAvatar(stage: String, expectedImageId: String) {
            // Independent approved-art template, not SDK readback or a baseline
            // calibrated against the failing frame. Native iconSize=.5 at mdpi.
            val base = requireNotNull(Bitmap.createScaledBitmap(avatarBitmap(context, onUi { runner.value.avatar }),
                84, 126, false).copy(Bitmap.Config.ARGB_8888, false)).apply { density = 160 }
            val template = Bitmap.createScaledBitmap(base, 42, 63, true)
            val expected = IntArray(42 * 63).also { template.getPixels(it, 0, 42, 0, 0, 42, 63) }
            val candidates = mutableListOf<Triple<Int, Int, Int>>()
            for (y in 1 until 62) for (x in 1 until 41) {
                val color = expected[y * 42 + x]
                val channels = listOf((color ushr 16) and 255, (color ushr 8) and 255, color and 255)
                if (channels.max() - channels.min() < 40) continue
                if ((y - 1..y + 1).all { row -> (x - 1..x + 1).all { column -> (expected[row * 42 + column] ushr 24) >= 250 } })
                    candidates += Triple(x, y, color)
            }
            fun sample(values: List<Triple<Int, Int, Int>>, maximum: Int) =
                if (values.size <= maximum) values else (0 until maximum).map { values[it * (values.size - 1) / (maximum - 1)] }
            val global = sample(candidates, 48)
            val body = sample(candidates.filter { it.second >= 63 * .42 && it.second < 63 * .72 }, 24)
            var latest = JSONObject()
            var registeredCopy: Bitmap? = null
            var textureCopy: Bitmap? = null
            var passed = false
            fun export(bitmap: Bitmap, suffix: String): String {
                val filename = "native-online-$stage-$suffix.png"
                File(directory, filename).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                return filename
            }
            fun stats(bitmap: Bitmap): JSONObject {
                val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
                val bytes = java.nio.ByteBuffer.allocate(pixels.size * 4).apply { asIntBuffer().put(pixels) }.array()
                val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                return JSONObject().put("width", bitmap.width).put("height", bitmap.height).put("density", bitmap.density)
                    .put("rowBytes", bitmap.rowBytes).put("byteCount", bitmap.byteCount).put("config", bitmap.config?.name)
                    .put("isPremultiplied", bitmap.isPremultiplied).put("nonTransparentPixels", pixels.count { (it ushr 24) > 0 })
                    .put("distinctArgbColors", pixels.toSet().size).put("argbBigEndianSha256", sha)
            }
            try {
                await("actual-native-avatar-pixels-$stage") {
                    val map = requireNotNull(nativeMap.get()); val style = requireNotNull(map.style)
                    val view = requireNotNull(nativeView.get()); val current = runner.value
                    val registered = style.getImage(expectedImageId)
                    registeredCopy?.recycle(); registeredCopy = registered?.copy(Bitmap.Config.ARGB_8888, false)
                    val texture = (view.renderView as? TextureView)?.bitmap
                    textureCopy?.recycle(); textureCopy = texture
                    val point = map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(current.latitude, current.longitude))
                    val cameraNow = map.cameraPosition
                    val supported = abs(view.pixelRatio - 1f) < .0001f && abs(cameraNow.bearing) < .0001 && abs(cameraNow.tilt) < .0001 &&
                        texture != null && texture.width == view.width && texture.height == view.height && point.x.isFinite() && point.y.isFinite()
                    val viewport = RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())
                    val renderedFeatures = map.queryRenderedFeatures(viewport, "runmoni-runner-icons")
                    val renderedReference = renderedFeatures.any { it.hasProperty("icon") && it.getStringProperty("icon") == expectedImageId }
                    val sourceFeatures = style.getSourceAs<GeoJsonSource>("runmoni-runners")?.querySourceFeatures(Expression.literal(true)).orEmpty()
                    val sourceReference = sourceFeatures.any { it.hasProperty("icon") && it.getStringProperty("icon") == expectedImageId }
                    val basePixels = IntArray(84 * 126).also { base.getPixels(it, 0, 84, 0, 0, 84, 126) }
                    val actualPixels = if (registered != null && registered.width == 84 && registered.height == 126)
                        IntArray(84 * 126).also { registered.getPixels(it, 0, 84, 0, 0, 84, 126) } else null
                    val mismatches = actualPixels?.let { pixels -> basePixels.indices.count { pixels[it] != basePixels[it] } } ?: -1
                    fun match(samples: List<Triple<Int, Int, Int>>): JSONObject {
                        var hits = 0; val colors = mutableSetOf<Int>(); val rows = JSONArray()
                        samples.forEach { (x, y, expectedColor) ->
                            val px = Math.round(point.x - 21f + x); val py = Math.round(point.y - 63f + y)
                            var best = Int.MAX_VALUE; var actual = 0
                            if (supported && texture != null) for (dy in -1..1) for (dx in -1..1) {
                                val tx = px + dx; val ty = py + dy
                                if (tx !in 0 until texture.width || ty !in 0 until texture.height) continue
                                val color = texture.getPixel(tx, ty)
                                val delta = maxOf(abs(((color ushr 16) and 255) - ((expectedColor ushr 16) and 255)),
                                    abs(((color ushr 8) and 255) - ((expectedColor ushr 8) and 255)), abs((color and 255) - (expectedColor and 255)))
                                if ((color ushr 24) >= 250 && delta < best) { best = delta; actual = color }
                            }
                            val hit = best <= 32
                            if (hit) { hits++; colors += expectedColor and 0xffffff }
                            rows.put(JSONObject().put("x", x).put("y", y).put("expectedArgb", expectedColor)
                                .put("actualArgb", actual).put("maxChannelDelta", best).put("matched", hit))
                        }
                        return JSONObject().put("samples", samples.size).put("matched", hits)
                            .put("fraction", if (samples.isEmpty()) 0.0 else hits.toDouble() / samples.size)
                            .put("matchedDistinctChromaticColors", colors.size).put("points", rows)
                    }
                    val overall = match(global); val clothing = match(body)
                    val pixelsPass = global.size >= 12 && overall.getInt("matched") >= 12 && overall.getDouble("fraction") >= .60 &&
                        overall.getInt("matchedDistinctChromaticColors") >= 2 && body.size >= 8 && clothing.getInt("matched") >= 8 && clothing.getDouble("fraction") >= .60
                    latest = JSONObject().put("stage", "visible-avatar-$stage").put("expectedImageId", expectedImageId)
                        .put("projectionSupported", supported).put("nativeViewPixelRatio", view.pixelRatio)
                        .put("projectedAnchorLocalPx", JSONObject().put("x", point.x).put("y", point.y))
                        .put("iconBoundsLocalPx", JSONObject().put("left", point.x - 21).put("top", point.y - 63).put("right", point.x + 21).put("bottom", point.y))
                        .put("cameraBearing", cameraNow.bearing).put("cameraTilt", cameraNow.tilt)
                        .put("sourceRefersToCurrentImage", sourceReference).put("renderedSymbolRefersToCurrentImage", renderedReference)
                        .put("renderedSymbolFeatures", JSONArray(renderedFeatures.take(8).map { JSONObject(it.toJson()) }))
                        .put("bitmapRoundTripPixelMismatches", mismatches).put("global", overall).put("clothingBand42To72Percent", clothing)
                        .put("opaqueAlphaMinimum", 250).put("chromaticChannelSpreadMinimum", 40).put("positionTolerancePx", 1)
                        .put("perChannelTolerance", 32).put("minimumMatchFraction", .60)
                    supported && sourceReference && renderedReference && mismatches == 0 && pixelsPass
                }
                passed = true
            } finally {
                latest.put("passed", passed).put("expectedSourceStats", stats(base))
                    .put("expectedSourceFile", export(base, "app-source-icon"))
                    .put("expectedDisplayFile", export(template, "expected-display"))
                registeredCopy?.let { latest.put("registeredStats", stats(it)).put("registeredFile", export(it, "native-registered-icon")) }
                textureCopy?.let { latest.put("nativeTextureFile", export(it, "native-texture-private")) }
                stages.put(latest)
                registeredCopy?.recycle(); textureCopy?.recycle(); template.recycle(); base.recycle()
            }
        }
        fun save() {
            observations.writeText(JSONObject().put("syntheticFixture", true).put("actualMainActivity", false)
                .put("actualGps", false).put("firebaseUsed", false).put("profileOrRecordingModified", false)
                .put("styleUri", OPEN_FREE_MAP_STYLE).put("productionFactoryAndTransportUsed", true)
                .put("httpCacheClearedOrWarmupForced", false).put("freshHttpFetchForEveryResourceClaimed", false)
                .put("observationBudgetMillis", 120_000L).put("providerSlaClaimed", false)
                .put("routePointCount", route.size).put("routeSegmentCount", RouteGeometry.from(route).segments.size)
                .put("runnerCount", 1).put("mapViewConstructions", constructions.get())
                .put("completed", completed).put("failureClass", failureClass ?: JSONObject.NULL)
                .put("nativeFailure", failure.get()?.name ?: JSONObject.NULL).put("stages", stages).toString(2))
        }
        try {
            assertEquals(2, RouteGeometry.from(route).segments.size)
            compose.setContent {
                // Capture the real default before overriding it. The wrapper only
                // observes SDK references; it never supplies a fake MapView/client.
                val productionFactory = LocalMapLibreViewFactory.current
                val observedFactory: (Context) -> MapView = remember(productionFactory) {
                    { owner -> productionFactory(owner).also { view ->
                        constructions.incrementAndGet()
                        nativeView.set(view)
                        view.addOnDidFinishRenderingMapListener(rendered)
                        view.addOnDidFinishRenderingFrameListener(frameRendered)
                        view.getMapAsync { ready ->
                            nativeMap.set(ready)
                            ready.addOnCameraIdleListener(idle)
                            ready.addOnCameraMoveStartedListener(moved)
                        }
                    } }
                }
                RunmoniTheme {
                    CompositionLocalProvider(LocalMapLibreViewFactory provides observedFactory) {
                        Column(Modifier.fillMaxSize()) {
                            Text("온라인 지도 · 합성 서울 경로", style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                            Text("검증용 장면 · GPS/Firebase/Main 화면 아님", style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                            MapLibreMapPane(route, listOf(runner.value), Modifier.fillMaxWidth().weight(1f),
                                fitRoute = true, interactiveMap = true, onFailure = { failure.set(it) })
                        }
                    }
                }
            }
            record("production-pane-created")
            save()
            var nativeRouteFeatures = 0
            var nativeBaseFeatures = 0
            await("fully-rendered-public-style-and-own-overlays") {
                val map = nativeMap.get()
                val style = map?.style
                if (style == null || !style.isFullyLoaded || fullyRenderedMaps.get() == 0 || renderedFrames.get() == 0) false
                else {
                    assertEquals("Use the production public style", OPEN_FREE_MAP_STYLE, style.uri)
                    val routeSource = style.getSourceAs<GeoJsonSource>("runmoni-route")
                    val runnerSource = style.getSourceAs<GeoJsonSource>("runmoni-runners")
                    val endpoints = style.getSourceAs<GeoJsonSource>("runmoni-endpoints")
                    val layers = listOf("runmoni-route-outline", "runmoni-route-line", "runmoni-start", "runmoni-finish",
                        "runmoni-self", "runmoni-point-label", "runmoni-runner-location", "runmoni-runner-icons")
                    val view = nativeView.get()
                    if (view == null || view.width <= 0 || view.height <= 0 || routeSource == null || runnerSource == null ||
                        endpoints == null || layers.any { style.getLayer(it) == null } || style.getImage(imageId) == null) false
                    else {
                        nativeRouteFeatures = routeSource.querySourceFeatures(Expression.literal(true)).size
                        val providerLayers = style.layers.filterNot { it.id.startsWith("runmoni-") }.map { it.id }.toTypedArray()
                        nativeBaseFeatures = requireNotNull(map).queryRenderedFeatures(RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()),
                            *providerLayers).size
                        nativeRouteFeatures >= 2 && nativeBaseFeatures > 0 &&
                            endpoints.querySourceFeatures(Expression.literal(true)).isNotEmpty() &&
                            runnerSource.querySourceFeatures(Expression.literal(true)).any { it.getStringProperty("label") == "합성 러너" }
                    }
                }
            }
            var nativeTextureColorCount = 0
            await("actual-native-texture-has-drawn-map-detail") {
                val texture = requireNotNull(nativeView.get()).renderView as? TextureView
                assertNotNull("Production texture renderer must be present", texture)
                val pixels = texture?.takeIf { it.isAvailable }?.bitmap
                if (pixels == null) false
                else try {
                    val colors = HashSet<Int>()
                    for (y in 0 until pixels.height step 8) for (x in 0 until pixels.width step 8) colors.add(pixels.getPixel(x, y))
                    nativeTextureColorCount = colors.size
                    // Wait for actual presentation within the same absolute
                    // budget; a native callback alone is not a screenshot.
                    colors.size > 32
                } finally { pixels.recycle() }
            }
            onUi {
                assertEquals("Observe one actual SDK view", 1, constructions.get())
                initialIcon = requireNotNull(requireNotNull(nativeMap.get()).style?.getImage(imageId))
                    .copy(Bitmap.Config.ARGB_8888, false)
                assertEquals(84, requireNotNull(initialIcon).width)
                assertEquals(126, requireNotNull(initialIcon).height)
            }
            val before = camera()
            stages.put(JSONObject().put("stage", "native-feature-observations").put("routeSourceFeatureCount", nativeRouteFeatures)
                .put("renderedProviderFeatureCount", nativeBaseFeatures).put("textureSampledDistinctColors", nativeTextureColorCount)
                .put("sourceFeatureDuplicatesNotDeduplicated", true))
            record("ready-before-gestures", before)
            verifyVisibleAvatar("before", imageId)
            save()
            remaining()
            captureDevice("native-online-seoul-fixture")
            val device = UiDevice.getInstance(instrumentation)
            fun currentMapObject() = requireNotNull(device.findObject(By.descContains("실제 GPS 경로 4개 지점"))) {
                "The real map viewport must be exposed to accessibility for touch injection"
            }.apply { setGestureMarginPercentage(.12f) }
            val panTarget = currentMapObject()
            recordGestureGeometry("one-finger-input-geometry", panTarget.visibleBounds, .20f)
            val idlesBeforePan = cameraIdles.get()
            val gesturesBeforePan = gestureStarts.get()
            record("one-finger-drag-before-input", camera())
            remaining()
            panTarget.swipe(Direction.LEFT, .20f, 350)
            record("one-finger-drag-input-returned", camera())
            await("one-finger-drag-changes-native-camera") {
                val position = nativeMap.get()?.cameraPosition
                val target = position?.target
                cameraIdles.get() > idlesBeforePan && gestureStarts.get() > gesturesBeforePan && target != null &&
                    (abs(target.latitude - before.latitude) > .00001 || abs(target.longitude - before.longitude) > .00001)
            }
            val afterPan = camera()
            record("one-finger-drag-idle", afterPan)
            save()
            val pinchTarget = currentMapObject()
            recordGestureGeometry("two-finger-input-geometry", pinchTarget.visibleBounds, .45f)
            val idlesBeforePinch = cameraIdles.get()
            val gesturesBeforePinch = gestureStarts.get()
            val framesBeforePinch = renderedFrames.get()
            stages.put(JSONObject().put("stage", "two-finger-input-baselines")
                .put("idleBaseline", idlesBeforePinch).put("gestureBaseline", gesturesBeforePinch)
                .put("fullyRenderedFrameBaseline", framesBeforePinch))
            record("two-finger-pinch-before-input", camera())
            remaining()
            pinchTarget.pinchOpen(.45f, 350)
            record("two-finger-pinch-input-returned", camera())
            await("two-finger-pinch-increases-native-zoom-and-renders") {
                cameraIdles.get() > idlesBeforePinch && gestureStarts.get() > gesturesBeforePinch &&
                    renderedFrames.get() > framesBeforePinch && (nativeMap.get()?.cameraPosition?.zoom ?: 0.0) > afterPan.zoom + .10
            }
            val afterPinch = camera()
            record("two-finger-pinch-idle", afterPinch)
            save()
            val originalMap = nativeMap.get()
            val framesBeforeMarker = renderedFrames.get()
            // Keep the synthetic marker inside the newly panned viewport. These
            // coordinates come only from this fixture's gesture camera, never GPS.
            val priorImageId = imageId
            onUi { runner.value = runner.value.copy(name = "합성 러너 · 변경",
                latitude = afterPinch.latitude, longitude = afterPinch.longitude,
                avatar = runner.value.avatar.copy(costume = 4)) }
            imageId = onUi { runnerMapImageId(runner.value) }
            assertNotEquals("A new outfit must receive a distinct native image key", priorImageId, imageId)
            await("marker-recomposition-draws-the-new-costume") {
                val style = nativeMap.get()?.style
                val next = style?.getImage(imageId)
                style != null && next != null && !next.sameAs(requireNotNull(initialIcon)) && renderedFrames.get() > framesBeforeMarker &&
                    style.getSourceAs<GeoJsonSource>("runmoni-runners")?.querySourceFeatures(Expression.literal(true))
                        ?.any { it.getStringProperty("label") == "합성 러너 · 변경" } == true
            }
            val afterMarker = camera()
            assertEquals("A marker update must preserve the manually panned latitude", afterPinch.latitude, afterMarker.latitude, .0000001)
            assertEquals("A marker update must preserve the manually panned longitude", afterPinch.longitude, afterMarker.longitude, .0000001)
            assertEquals("A marker update must preserve the pinched zoom", afterPinch.zoom, afterMarker.zoom, .000001)
            assertSame("Marker changes must reuse the native map", originalMap, nativeMap.get())
            assertEquals(1, constructions.get())
            record("marker-update-keeps-gesture-camera", afterMarker)
            verifyVisibleAvatar("after", imageId)
            remaining()
            captureDevice("native-online-gestures-fixture")
            remaining()
            completed = true
        } catch (error: Throwable) {
            failureClass = error.javaClass.name
            // Failure-only native state disambiguates the conjunction without
            // retrying input, extending the shared deadline, or hiding failure.
            val finalCamera = runCatching { camera() }
            record("failure-final-native-camera-and-counters", finalCamera.getOrNull())
            finalCamera.exceptionOrNull()?.let {
                stages.put(JSONObject().put("stage", "failure-camera-observation-unavailable")
                    .put("failureClass", it.javaClass.name))
            }
            // Failure-only current-window evidence; it never changes the result
            // or starts a second observation/retry budget.
            runCatching { captureDevice("native-online-private-failure") }
            throw error
        } finally {
            save()
            runCatching {
                onUi {
                    nativeView.get()?.removeOnDidFinishRenderingMapListener(rendered)
                    nativeView.get()?.removeOnDidFinishRenderingFrameListener(frameRendered)
                    nativeMap.get()?.removeOnCameraIdleListener(idle)
                    nativeMap.get()?.removeOnCameraMoveStartedListener(moved)
                    initialIcon?.recycle()
                }
            }.getOrElse { cleanupError ->
                // A secondary observer cleanup must preserve the original failure.
                if (failureClass == null) throw cleanupError
            }
        }
    }
}

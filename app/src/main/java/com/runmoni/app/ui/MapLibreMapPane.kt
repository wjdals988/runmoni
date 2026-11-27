package com.runmoni.app.ui

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.runmoni.app.BuildConfig
import com.runmoni.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal const val OPEN_FREE_MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

/** Tests can exercise initialization failure without starting GL or public
 * requests. Production always uses MapView, never a remote JavaScript WebView. */
internal val LocalMapLibreViewFactory = staticCompositionLocalOf<(Context) -> MapView> { ::createOpenFreeMapView }

private object OpenFreeMapTransport {
    private var configured = false
    @Synchronized fun configure(context: Context) {
        if (configured) return
        val dispatcher = Dispatcher().apply { maxRequests = 4; maxRequestsPerHost = 4 }
        HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder().dispatcher(dispatcher)
            .cache(Cache(File(context.applicationContext.cacheDir, "openfreemap-http"), 24L * 1024 * 1024))
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false)
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.scheme != "https" || request.url.host != "tiles.openfreemap.org")
                    throw IOException("Unexpected map resource host")
                chain.proceed(request.newBuilder().header("User-Agent",
                    "Runmoni/${BuildConfig.VERSION_NAME} (Android; https://github.com/wjdals988/runmoni)").build())
            }.build())
        configured = true
    }
}

private fun createOpenFreeMapView(context: Context): MapView {
    MapLibre.getInstance(context.applicationContext)
    OpenFreeMapTransport.configure(context)
    return MapView(context, MapLibreMapOptions.createFromAttributes(context)
        .textureMode(true).setPrefetchZoomDelta(0).localIdeographFontFamily("sans-serif")
        .attributionEnabled(false).logoEnabled(false).compassEnabled(false))
}

private class NativeMapLifetime { var active = true }
private data class RunnerArtworkKey(val id: String, val avatar: AvatarStyle)
private data class RunnerMapImage(val artwork: RunnerArtworkKey, val bitmap: Bitmap)
private data class NativeRouteLayers(val route: GeoJsonSource, val endpoints: GeoJsonSource,
    val runners: GeoJsonSource, val imageIds: MutableSet<String> = mutableSetOf(),
    val imageIdsByRunner: MutableMap<String, String> = mutableMapOf())

/** Position/name changes reuse an image; equipment changes receive a new native
 * image key. The complete avatar value participates, without changing saved IDs. */
internal fun runnerMapImageId(runner: LiveRunner): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(runner.avatar.toString().toByteArray(Charsets.UTF_8))
    val alphabet = "0123456789abcdef"
    val version = buildString(digest.size * 2) {
        digest.forEach { byte ->
            val value = byte.toInt() and 255
            append(alphabet[value ushr 4]); append(alphabet[value and 15])
        }
    }
    return "runmoni-runner-${runner.id}-$version"
}

@Composable
internal fun MapLibreMapPane(
    points: List<TrackPoint>, runners: List<LiveRunner>, modifier: Modifier = Modifier,
    fitRoute: Boolean = false, interactiveMap: Boolean = false, followCenter: RouteMapCenter? = null,
    onBoundsChanged: ((RouteMapBounds) -> Unit)? = null, savedCamera: RouteMapCenter? = null,
    onCameraSaved: (RouteMapCenter) -> Unit = {}, onFailure: (VectorMapFailure) -> Unit,
    styleUri: String = OPEN_FREE_MAP_STYLE,
    recenterRequest: MapRecenterRequest? = null,
    onRecenterApplied: (MapRecenterRequest) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val mapPalette = RunningMapPalette.forDark(RunmoniColors.IsDark)
    val geometry = remember(points) { RouteGeometry.from(points) }
    val validRunners = remember(runners) { runners.filter { it.hasValidCoordinates() } }
    val actualCenter = followCenter?.takeIf { it.valid() }
    val factory = LocalMapLibreViewFactory.current
    val lifetime = remember(context, factory) { NativeMapLifetime() }
    val currentFailure by rememberUpdatedState(onFailure)
    val currentBounds by rememberUpdatedState(onBoundsChanged)
    val currentCameraSaved by rememberUpdatedState(onCameraSaved)
    val beganWithSavedCamera = remember { savedCamera?.valid() == true }
    val view = remember(context, factory) {
        var created: MapView? = null
        try { factory(context).also { created = it; it.onCreate(null) } }
        catch (_: Exception) { runCatching { created?.onDestroy() }; null }
        catch (_: LinkageError) { runCatching { created?.onDestroy() }; null }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    var overlays by remember { mutableStateOf<NativeRouteLayers?>(null) }
    var fullyRendered by remember { mutableStateOf(false) }
    var manualCamera by remember(fitRoute, interactiveMap) { mutableStateOf(beganWithSavedCamera) }
    var positioned by remember { mutableStateOf(false) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var camera by remember { mutableStateOf<RouteMapCenter?>(null) }
    val tileSize = 256.0 * density.density
    fun fail(reason: VectorMapFailure) { if (lifetime.active) currentFailure(reason) }
    fun target(): RouteViewport? = when {
        fitRoute && geometry.points.isNotEmpty() -> RouteViewport.fit(geometry.points,
            mapSize.width.toDouble(), mapSize.height.toDouble(), 24.0 * density.density, tileSize)
        actualCenter != null -> actualCenter.viewport()
        geometry.points.isNotEmpty() -> RouteViewport.follow(geometry.points.last())
        interactiveMap -> VectorMapCoordinates.seoulOverview.viewport()
        validRunners.isNotEmpty() -> validRunners.first().let { RouteMapCenter(it.latitude, it.longitude, 15.0).viewport() }
        else -> null
    }
    fun position(ready: MapLibreMap, restore: Boolean = false, requestedCenter: RouteMapCenter? = null): Boolean {
        if (mapSize.width == 0 || mapSize.height == 0) return false
        val next = when {
            requestedCenter != null -> requestedCenter.viewport()
            restore -> savedCamera?.takeIf { it.valid() }?.viewport() ?: target()
            else -> target()
        }
        next ?: return false
        ready.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(Mercator.latitude(next.centerY),
            Mercator.longitude(next.centerX)), VectorMapCoordinates.nativeZoom(next.zoom)))
        positioned = true
        return true
    }
    fun changeCamera(zoomDelta: Double = 0.0, dx: Double = 0.0, dy: Double = 0.0): Boolean {
        val ready = map ?: return false
        val before = ready.cameraPosition
        val target = before.target ?: return false
        val viewport = VectorMapCoordinates.viewport(target.latitude, target.longitude, before.zoom) ?: return false
        val moved = viewport.pan(dx, dy, tileSize)
        manualCamera = true
        ready.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(Mercator.latitude(moved.centerY),
            Mercator.longitude(moved.centerX)), (before.zoom + zoomDelta).coerceIn(0.0, 17.0)))
        return true
    }
    val recenterLabel = if (fitRoute) "전체 러닝 경로 보기" else if (actualCenter != null) "내 위치로 이동" else "처음 지도 영역 보기"
    val actions = listOf(
        CustomAccessibilityAction("지도 확대") { changeCamera(zoomDelta = 1.0) },
        CustomAccessibilityAction("지도 축소") { changeCamera(zoomDelta = -1.0) },
        CustomAccessibilityAction("서쪽으로 이동") { changeCamera(dx = mapSize.width * .35) },
        CustomAccessibilityAction("동쪽으로 이동") { changeCamera(dx = -mapSize.width * .35) },
        CustomAccessibilityAction("북쪽으로 이동") { changeCamera(dy = mapSize.height * .35) },
        CustomAccessibilityAction("남쪽으로 이동") { changeCamera(dy = -mapSize.height * .35) },
        CustomAccessibilityAction(recenterLabel) { manualCamera = false; map?.let { position(it) } ?: false },
    )
    LaunchedEffect(view) { if (view == null) fail(VectorMapFailure.INITIALIZATION) }
    DisposableEffect(view, lifecycle) {
        if (view == null) onDispose { lifetime.active = false }
        else {
            var started = false
            var resumed = false
            fun start() { if (!started) { view.onStart(); started = true } }
            fun resume() { start(); if (!resumed) { view.onResume(); resumed = true } }
            fun pause() { if (resumed) { view.onPause(); resumed = false } }
            fun stop() { pause(); if (started) { view.onStop(); started = false } }
            val observer = LifecycleEventObserver { _, event ->
                try { when (event) {
                    Lifecycle.Event.ON_START -> start()
                    Lifecycle.Event.ON_RESUME -> resume()
                    Lifecycle.Event.ON_PAUSE -> pause()
                    Lifecycle.Event.ON_STOP -> stop()
                    else -> Unit
                } } catch (_: Exception) { fail(VectorMapFailure.RENDERER) }
            }
            val callbacks = object : ComponentCallbacks {
                override fun onConfigurationChanged(config: Configuration) = Unit
                override fun onLowMemory() { view.onLowMemory() }
            }
            val failed = MapView.OnDidFailLoadingMapListener { view.post { fail(VectorMapFailure.STYLE) } }
            val rendered = MapView.OnDidFinishRenderingMapListener { fully ->
                if (fully) view.post { if (lifetime.active) fullyRendered = true }
            }
            view.addOnDidFailLoadingMapListener(failed)
            view.addOnDidFinishRenderingMapListener(rendered)
            lifecycle.addObserver(observer)
            context.registerComponentCallbacks(callbacks)
            try {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
                else if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
            } catch (_: Exception) { view.post { fail(VectorMapFailure.RENDERER) } }
            onDispose {
                lifetime.active = false
                lifecycle.removeObserver(observer)
                context.unregisterComponentCallbacks(callbacks)
                view.removeOnDidFailLoadingMapListener(failed)
                view.removeOnDidFinishRenderingMapListener(rendered)
                runCatching { stop() }; runCatching { view.onDestroy() }
            }
        }
    }
    DisposableEffect(map) {
        val ready = map
        val idle = MapLibreMap.OnCameraIdleListener {
            if (lifetime.active && ready != null) {
                val position = ready.cameraPosition
                position.target?.let { target -> camera = RouteMapCenter(target.latitude,
                    Mercator.longitude(Mercator.x(target.longitude)), (position.zoom + 1.0).coerceIn(0.0, 18.0)) }
            }
        }
        val move = MapLibreMap.OnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) manualCamera = true
        }
        ready?.addOnCameraIdleListener(idle); ready?.addOnCameraMoveStartedListener(move)
        onDispose { ready?.removeOnCameraIdleListener(idle); ready?.removeOnCameraMoveStartedListener(move) }
    }
    LaunchedEffect(camera, mapSize, tileSize) {
        val latest = camera ?: return@LaunchedEffect
        delay(250)
        if (lifetime.active) {
            currentCameraSaved(latest)
            latest.viewport()?.bounds(mapSize.width.toDouble(), mapSize.height.toDouble(), tileSize)?.let { currentBounds?.invoke(it) }
        }
    }
    LaunchedEffect(view, lifecycle) {
        if (view != null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // A finite readiness budget prevents an endless blank map; this is
            // a UI failure policy, never a measured loading-time guarantee.
            val ready = withTimeoutOrNull(30_000) { snapshotFlow { loadedStyle != null && fullyRendered }.first { it } }
            if (ready == null) fail(VectorMapFailure.RENDERER)
        }
    }
    LaunchedEffect(loadedStyle) {
        val style = loadedStyle ?: return@LaunchedEffect
        try { overlays = installRouteLayers(style, density.fontScale, mapPalette) }
        catch (_: Exception) { fail(VectorMapFailure.STYLE) }
    }
    // Restyle the loaded objects in place: no style URL reload, camera reset,
    // image replacement or extra runner lookup when appearance changes.
    LaunchedEffect(loadedStyle, overlays, mapPalette) {
        val style = loadedStyle ?: return@LaunchedEffect
        if (overlays == null) return@LaunchedEffect
        try {
            applyRunningMapAppearance(style, mapPalette)
            applyRoutePalette(style, mapPalette)
        } catch (_: Exception) { fail(VectorMapFailure.STYLE) }
    }
    LaunchedEffect(loadedStyle, overlays, density.fontScale) {
        val style = loadedStyle ?: return@LaunchedEffect
        style.getLayerAs<SymbolLayer>("runmoni-point-label")?.setProperties(textSize(12f * density.fontScale))
        style.getLayerAs<SymbolLayer>("runmoni-runner-icons")?.setProperties(textSize(12f * density.fontScale))
    }
    val runnerArtwork = remember(validRunners) { validRunners.map { RunnerArtworkKey(it.id, it.avatar) } }
    val runnerImages by produceState<List<RunnerMapImage>>(emptyList(), runnerArtwork, context) {
        value = withContext(Dispatchers.Default) { runnerArtwork.mapNotNull { artwork ->
            try {
                // Copy for the map icon; never recycle/recolor the approved shared frame.
                val scaled = Bitmap.createScaledBitmap(avatarBitmap(context, artwork.avatar), 84, 126, false)
                val icon = requireNotNull(scaled.copy(Bitmap.Config.ARGB_8888, false)).apply {
                    this.density = android.util.DisplayMetrics.DENSITY_DEFAULT
                }
                RunnerMapImage(artwork, icon)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        } }
    }
    LaunchedEffect(overlays, geometry, validRunners, runnerImages, loadedStyle, actualCenter) {
        val layers = overlays ?: return@LaunchedEffect
        val style = loadedStyle ?: return@LaunchedEffect
        try {
            layers.route.setGeoJson(FeatureCollection.fromFeatures(geometry.segments.filter { it.size > 1 }.map { segment ->
                Feature.fromGeometry(LineString.fromLngLats(segment.map { Point.fromLngLat(it.x * 360.0 - 180.0,
                    it.point.latitude.coerceIn(-Mercator.MAX_LATITUDE, Mercator.MAX_LATITUDE)) }))
            }))
            layers.endpoints.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(
                geometry.points.firstOrNull()?.let { endpoint(it, "시작", "start") },
                geometry.points.lastOrNull()?.let { endpoint(it, "도착", "finish") },
                actualCenter?.takeIf { geometry.points.isEmpty() }?.let { center ->
                    Feature.fromGeometry(Point.fromLngLat(center.longitude, center.latitude.coerceIn(-Mercator.MAX_LATITUDE, Mercator.MAX_LATITUDE))).apply {
                        addStringProperty("label", "내 위치"); addStringProperty("kind", "self")
                    }
                })))
            val images = runnerImages.filter { image -> validRunners.any { it.id == image.artwork.id && it.avatar == image.artwork.avatar } }
                .associateBy { it.artwork.id }
            layers.imageIdsByRunner.keys.retainAll(validRunners.map { it.id }.toSet())
            val features = validRunners.map { runner ->
                val imageId = runnerMapImageId(runner)
                images[runner.id]?.let { image ->
                    if (imageId !in layers.imageIds) {
                        style.addImage(imageId, image.bitmap)
                        layers.imageIds += imageId
                    }
                    layers.imageIdsByRunner[runner.id] = imageId
                }
                Feature.fromGeometry(Point.fromLngLat(runner.longitude, runner.latitude.coerceIn(-Mercator.MAX_LATITUDE, Mercator.MAX_LATITUDE))).apply {
                    // Keep the previous ready outfit while the replacement is
                    // prepared. Never publish an image key before registration.
                    layers.imageIdsByRunner[runner.id]?.let { addStringProperty("icon", it) }
                    addStringProperty("label", if (runner.memberCount > 1) "${runner.name} · ${runner.memberCount}명" else runner.name)
                }
            }
            // Apply only this small runner collection synchronously before
            // retiring prior images; route/endpoints keep their async updates.
            layers.runners.setGeoJsonSync(FeatureCollection.fromFeatures(features))
            // Submit the new reference before retiring unused prior images.
            val nextImageIds = layers.imageIdsByRunner.values.toSet()
            (layers.imageIds - nextImageIds).forEach { imageId ->
                style.removeImage(imageId)
                layers.imageIds.remove(imageId)
            }
        } catch (_: Exception) { fail(VectorMapFailure.STYLE) }
    }
    LaunchedEffect(map, overlays, geometry, actualCenter, mapSize, fitRoute, interactiveMap, recenterRequest) {
        val ready = map ?: return@LaunchedEffect
        if (recenterRequest != null && position(ready, requestedCenter = recenterRequest.center)) {
            manualCamera = true
            onRecenterApplied(recenterRequest)
        } else if (!positioned) position(ready, restore = beganWithSavedCamera)
        else if (!manualCamera) position(ready)
    }
    Column(modifier.background(RunmoniColors.Paper).testTag("vector_map")) {
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().onSizeChanged { mapSize = it }.testTag("vector_map_viewport").semantics {
            contentDescription = when {
                geometry.points.isNotEmpty() -> "실제 GPS 경로 ${geometry.points.size}개 지점. 중단 구간은 연결하지 않습니다."
                actualCenter != null -> "확인된 위치 주변 벡터 지도."
                else -> "탐색용 벡터 지도. 내 위치는 확인되지 않았습니다."
            } + " 드래그로 이동하고 두 손가락으로 확대하거나 축소합니다."
            customActions = actions
        }) {
            if (view != null) AndroidView(factory = { view.apply { getMapAsync { ready ->
                if (lifetime.active) {
                    map = ready
                    ready.setMinZoomPreference(0.0); ready.setMaxZoomPreference(17.0)
                    ready.uiSettings.isRotateGesturesEnabled = false; ready.uiSettings.isTiltGesturesEnabled = false
                    ready.uiSettings.isScrollGesturesEnabled = true; ready.uiSettings.isZoomGesturesEnabled = true
                    try { ready.setStyle(Style.Builder().fromUri(styleUri)) { style -> if (lifetime.active) loadedStyle = style } }
                    catch (_: Exception) { view.post { fail(VectorMapFailure.STYLE) } }
                }
            } } }, modifier = Modifier.fillMaxSize())
            if (loadedStyle == null || !fullyRendered) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).testTag("map_vector_loading"))
        }
        if (interactiveMap && actualCenter == null && geometry.points.isEmpty()) Text(
            if (!manualCamera && !beganWithSavedCamera) "서울 중심 지도 · 내 위치 확인 전" else "내 위치 확인 전",
            style = MaterialTheme.typography.labelSmall, color = RunmoniColors.Muted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("map_location_unconfirmed"))
        if (density.fontScale >= 1.3f) {
            // Full copyright text gets the whole width at large font sizes;
            // keep the separate map-controls target outside the viewport.
            VectorMapAttribution(Modifier.fillMaxWidth())
            MapControlBar(actions, enabled = map != null)
        } else {
            MapControlBar(actions, enabled = map != null, attribution = {
                VectorMapAttribution(Modifier.weight(1f))
            })
        }
        if (fitRoute && !interactiveMap) MapPageScrollHint()
    }
}

@Composable
private fun VectorMapAttribution(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    TextButton(onClick = { uri.openUri("https://www.openstreetmap.org/copyright") },
        modifier = modifier.heightIn(min = 48.dp).testTag("vector_attribution"),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Text("© OpenMapTiles · Data from OpenStreetMap", style = MaterialTheme.typography.labelSmall)
    }
}

private fun endpoint(node: RouteMapPoint, label: String, kind: String) = Feature.fromGeometry(
    Point.fromLngLat(node.x * 360.0 - 180.0, node.point.latitude.coerceIn(-Mercator.MAX_LATITUDE, Mercator.MAX_LATITUDE))).apply {
    addStringProperty("label", label); addStringProperty("kind", kind)
}

private fun installRouteLayers(style: Style, fontScale: Float, palette: RunningMapPalette): NativeRouteLayers {
    applyRunningMapAppearance(style, palette)
    // A flat running map keeps routes and runners clear of 3D building clutter.
    style.layers.filterIsInstance<FillExtrusionLayer>().forEach { it.setProperties(visibility(Property.NONE)) }
    // Names only: preserve road refs/shields and the provider's cartography.
    style.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
        val field = layer.textField
        val raw = if (field.isExpression) field.expression?.toString().orEmpty() else field.value?.toString().orEmpty()
        if (raw.contains("\"name") || raw.contains("{name")) layer.setProperties(textField(Expression.coalesce(
            Expression.get("name:ko"), Expression.get("name:nonlatin"), Expression.get("name"), Expression.get("name:latin"), Expression.literal(""))))
    }
    val route = GeoJsonSource("runmoni-route", FeatureCollection.fromFeatures(emptyArray<Feature>()))
    val endpoints = GeoJsonSource("runmoni-endpoints", FeatureCollection.fromFeatures(emptyArray<Feature>()))
    val runners = GeoJsonSource("runmoni-runners", FeatureCollection.fromFeatures(emptyArray<Feature>()))
    style.addSource(route); style.addSource(endpoints); style.addSource(runners)
    style.addLayer(LineLayer("runmoni-route-outline", route.id).withProperties(
        lineColor(palette.halo), lineWidth(9f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
    style.addLayer(LineLayer("runmoni-route-line", route.id).withProperties(
        lineColor(palette.route), lineWidth(5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
    style.addLayer(CircleLayer("runmoni-start", endpoints.id).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("start")))
        .withProperties(circleRadius(6f), circleColor(palette.route), circleStrokeColor(palette.halo), circleStrokeWidth(2f)))
    style.addLayer(CircleLayer("runmoni-finish", endpoints.id).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("finish")))
        .withProperties(circleRadius(7f), circleColor(palette.ink), circleStrokeColor(palette.halo), circleStrokeWidth(2f)))
    style.addLayer(CircleLayer("runmoni-self", endpoints.id).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("self")))
        .withProperties(circleRadius(6f), circleColor(palette.route), circleStrokeColor(palette.halo), circleStrokeWidth(3f)))
    style.addLayer(SymbolLayer("runmoni-point-label", endpoints.id).withProperties(
        textField(Expression.get("label")), textFont(arrayOf("Noto Sans Regular")), textSize(12f * fontScale),
        textColor(palette.ink), textHaloColor(palette.halo), textHaloWidth(2f),
        textOffset(arrayOf(0f, 1.2f)), textAllowOverlap(false)))
    style.addLayer(CircleLayer("runmoni-runner-location", runners.id).withProperties(
        circleRadius(5f), circleColor(palette.route), circleStrokeColor(palette.halo), circleStrokeWidth(2.5f)))
    style.addLayer(SymbolLayer("runmoni-runner-icons", runners.id).withProperties(
        iconImage(Expression.get("icon")), iconSize(.5f), iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        iconAllowOverlap(true), iconIgnorePlacement(true), iconOptional(true), textField(Expression.get("label")),
        textFont(arrayOf("Noto Sans Regular")), textSize(12f * fontScale), textAnchor(Property.TEXT_ANCHOR_TOP),
        textOffset(arrayOf(0f, .3f)), textColor(palette.ink), textHaloColor(palette.halo), textHaloWidth(2.5f), textOptional(true)))
    return NativeRouteLayers(route, endpoints, runners)
}

/** Restyle known cartographic roles, preserving provider geometry, road widths,
 * shields, place names and transport. Unknown layers retain their own styling. */
private fun applyRunningMapAppearance(style: Style, palette: RunningMapPalette) {
    style.layers.forEach { layer ->
        val id = layer.id.lowercase(java.util.Locale.ROOT)
        when (layer) {
            is BackgroundLayer -> layer.setProperties(backgroundColor(palette.ground))
            is FillLayer -> when {
                layer.sourceLayer == "water" -> layer.setProperties(fillColor(palette.water))
                layer.sourceLayer == "building" -> layer.setProperties(fillColor(palette.ink), fillOpacity(.075f))
                layer.sourceLayer in setOf("landcover", "landuse") &&
                    listOf("park", "wood", "grass").any { it in id } -> layer.setProperties(fillColor(palette.park))
                layer.sourceLayer in setOf("landcover", "landuse", "aeroway") -> layer.setProperties(fillColor(palette.ground))
                else -> Unit
            }
            is LineLayer -> when {
                layer.sourceLayer == "waterway" -> layer.setProperties(lineColor(palette.water))
                layer.sourceLayer == "transportation" &&
                    listOf("rail", "ferry", "aerialway").none { it in id } -> {
                    val walking = listOf("path", "pedestrian", "cycleway", "track").any { it in id }
                    layer.setProperties(lineColor(when {
                        "casing" in id -> palette.roadEdge
                        walking -> palette.secondary
                        else -> palette.road
                    }))
                }
                else -> Unit
            }
            is SymbolLayer -> if (!id.startsWith("runmoni-")) {
                val poi = layer.sourceLayer == "poi"
                layer.setProperties(textColor(if (poi) palette.secondary else palette.ink),
                    textHaloColor(palette.halo), textHaloWidth(if (poi) 1.2f else 1.5f))
                if (poi) {
                    val transport = listOf("transit", "station", "bus").any { it in id }
                    layer.setProperties(iconOpacity(if (transport) .8f else .55f))
                }
            }
            else -> Unit
        }
    }
}

/** Only colours change; all GeoJSON, icons, font scaling and positions survive. */
private fun applyRoutePalette(style: Style, palette: RunningMapPalette) {
    style.getLayerAs<LineLayer>("runmoni-route-outline")?.setProperties(lineColor(palette.halo))
    style.getLayerAs<LineLayer>("runmoni-route-line")?.setProperties(lineColor(palette.route))
    listOf("runmoni-start", "runmoni-self", "runmoni-runner-location").forEach { id ->
        style.getLayerAs<CircleLayer>(id)?.setProperties(circleColor(palette.route), circleStrokeColor(palette.halo))
    }
    style.getLayerAs<CircleLayer>("runmoni-finish")?.setProperties(circleColor(palette.ink), circleStrokeColor(palette.halo))
    listOf("runmoni-point-label", "runmoni-runner-icons").forEach { id ->
        style.getLayerAs<SymbolLayer>(id)?.setProperties(textColor(palette.ink), textHaloColor(palette.halo))
    }
}

package com.runmoni.app.ui

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import com.runmoni.app.BuildConfig
import com.runmoni.app.domain.LiveRunner
import com.runmoni.app.domain.TrackPoint
import com.runmoni.app.domain.RouteGeometry
import com.runmoni.app.domain.RouteViewport
import com.runmoni.app.domain.Mercator
import com.runmoni.app.domain.RouteMapCenter
import com.runmoni.app.domain.RouteMapBounds

@Composable
internal fun MapPane(
    points: List<TrackPoint>, runners: List<LiveRunner> = emptyList(), modifier: Modifier = Modifier,
    fitRoute: Boolean = false, interactiveMap: Boolean = false, followCenter: RouteMapCenter? = null,
    onBoundsChanged: ((RouteMapBounds) -> Unit)? = null,
) {
    val geometry = remember(points) { RouteGeometry.from(points) }
    val usablePoints = remember(geometry) { geometry.points.map { it.point } }
    val validRunners = remember(runners) { runners.filter { it.hasValidCoordinates() } }
    var authFailed by remember { mutableStateOf(false) }
    if (LocalMapTileSource.current != null || BuildConfig.NAVER_MAP_CLIENT_ID.isBlank() || authFailed) {
        RouteTileMap(points, validRunners, modifier, fitRoute, interactiveMap, followCenter, onBoundsChanged)
        return
    }
    val context = LocalContext.current
    val sdk = remember(context) { NaverMapSdk.getInstance(context) }
    DisposableEffect(sdk) {
        val previous = sdk.onAuthFailedListener
        val listener = NaverMapSdk.OnAuthFailedListener { authFailed = true }
        sdk.onAuthFailedListener = listener
        onDispose { if (sdk.onAuthFailedListener === listener) sdk.onAuthFailedListener = previous }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<NaverMap?>(null) }
    val paths = remember { mutableListOf<PathOverlay>() }
    val markers = remember { mutableListOf<Marker>() }
    var cameraPositioned by remember { mutableStateOf(false) }
    var manualCamera by remember(fitRoute, interactiveMap) { mutableStateOf(false) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    val tileSize = 256.0 * LocalDensity.current.density
    val actualCenter = followCenter?.takeIf { it.valid() }
    val currentOnBoundsChanged by rememberUpdatedState(onBoundsChanged)
    fun positionCamera(ready: NaverMap): Boolean {
        val fitted = if (fitRoute) RouteViewport.fit(geometry.points, mapSize.width.toDouble(), mapSize.height.toDouble(),
            32.0 * context.resources.displayMetrics.density, tileSize) else null
        val target = fitted ?: actualCenter?.viewport() ?: geometry.points.lastOrNull()?.let { RouteViewport.follow(it) }
            ?: (if (interactiveMap) RouteViewport.world() else null)
            ?: validRunners.firstOrNull()?.let { RouteViewport(Mercator.x(it.longitude), Mercator.y(it.latitude), 14.0) }
        if (target == null) return false
        ready.moveCamera(CameraUpdate.scrollAndZoomTo(LatLng(Mercator.latitude(target.centerY), Mercator.longitude(target.centerX)), target.zoom))
        cameraPositioned = true
        return true
    }
    fun changeCamera(zoomDelta: Double = 0.0, dx: Double = 0.0, dy: Double = 0.0): Boolean {
        val ready = map ?: return false
        val camera = ready.cameraPosition
        val next = RouteViewport(Mercator.x(camera.target.longitude), Mercator.y(camera.target.latitude), camera.zoom).pan(dx, dy, tileSize)
        manualCamera = true
        ready.moveCamera(CameraUpdate.scrollAndZoomTo(LatLng(Mercator.latitude(next.centerY), Mercator.longitude(next.centerX)),
            (camera.zoom + zoomDelta).coerceIn(ready.minZoom, ready.maxZoom)))
        return true
    }
    val recenterLabel = if (fitRoute) "전체 러닝 경로 보기" else if (actualCenter != null) "내 위치로 이동" else "처음 지도 영역 보기"
    val mapActions = listOf(
        CustomAccessibilityAction("지도 확대") { changeCamera(zoomDelta = 1.0) },
        CustomAccessibilityAction("지도 축소") { changeCamera(zoomDelta = -1.0) },
        CustomAccessibilityAction("서쪽으로 이동") { changeCamera(dx = mapSize.width * .35) },
        CustomAccessibilityAction("동쪽으로 이동") { changeCamera(dx = -mapSize.width * .35) },
        CustomAccessibilityAction("북쪽으로 이동") { changeCamera(dy = mapSize.height * .35) },
        CustomAccessibilityAction("남쪽으로 이동") { changeCamera(dy = -mapSize.height * .35) },
        CustomAccessibilityAction(recenterLabel) { manualCamera = false; map?.let(::positionCamera) ?: false },
    )
    DisposableEffect(map, mapSize, tileSize) {
        val ready = map
        val idle = NaverMap.OnCameraIdleListener {
            if (ready != null) {
                val camera = ready.cameraPosition
                RouteViewport(Mercator.x(camera.target.longitude), Mercator.y(camera.target.latitude), camera.zoom)
                    .bounds(mapSize.width.toDouble(), mapSize.height.toDouble(), tileSize)?.let { currentOnBoundsChanged?.invoke(it) }
            }
        }
        val changed = NaverMap.OnCameraChangeListener { reason, _ ->
            if (reason == CameraUpdate.REASON_GESTURE) manualCamera = true
        }
        ready?.addOnCameraIdleListener(idle)
        ready?.addOnCameraChangeListener(changed)
        onDispose {
            ready?.removeOnCameraIdleListener(idle)
            ready?.removeOnCameraChangeListener(changed)
        }
    }
    DisposableEffect(view, lifecycle) {
        var started = false
        var resumed = false
        fun start() { if (!started) { view.onStart(); started = true } }
        fun resume() { start(); if (!resumed) { view.onResume(); resumed = true } }
        fun pause() { if (resumed) { view.onPause(); resumed = false } }
        fun stop() { pause(); if (started) { view.onStop(); started = false } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() { view.onLowMemory() }
        }
        lifecycle.addObserver(observer)
        context.registerComponentCallbacks(callbacks)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        else if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose {
            paths.forEach { it.map = null }
            markers.forEach { it.map = null }
            lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(callbacks)
            stop()
            view.onDestroy()
        }
    }
    Column(modifier.testTag("naver_map")) {
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { mapSize = it }.semantics {
            contentDescription = "지도. 드래그로 이동하고 두 손가락으로 확대하거나 축소합니다."
            customActions = mapActions
        }) {
            AndroidView(factory = { view.apply { getMapAsync { map = it } } }, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { MapOptionsMenu(mapActions, enabled = map != null) }
            if (usablePoints.isNotEmpty() || actualCenter != null || interactiveMap || validRunners.isNotEmpty())
                FilledTonalIconButton(shape = RunmoniShapes.Action, onClick = { manualCamera = false; map?.let(::positionCamera) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(48.dp)) { Icon(Icons.Outlined.MyLocation, recenterLabel) }
        }
        if (fitRoute && !interactiveMap) MapPageScrollHint()
    }
    LaunchedEffect(map, geometry, validRunners, mapSize, fitRoute, interactiveMap, actualCenter) {
        val ready = map ?: return@LaunchedEffect
        ready.uiSettings.isZoomControlEnabled = false
        ready.uiSettings.isScrollGesturesEnabled = true
        ready.uiSettings.isZoomGesturesEnabled = true
        // Keep the run/discovery map north-up and flat so viewport geography
        // and the raster fallback use the same drag/pinch model.
        ready.uiSettings.isRotateGesturesEnabled = false
        ready.uiSettings.isTiltGesturesEnabled = false
        paths.forEach { it.map = null }; paths.clear()
        geometry.segments.forEach { segment ->
            if (segment.size > 1) paths += PathOverlay().apply {
                coords = segment.map { LatLng(it.point.latitude.coerceIn(-Mercator.MAX_LATITUDE, Mercator.MAX_LATITUDE), it.point.longitude) }
                color = RunmoniColors.Primary.toArgb()
                width = 10
                this.map = ready
            }
        }
        markers.forEach { it.map = null }; markers.clear()
        listOfNotNull(usablePoints.firstOrNull()?.let { "시작" to it }, usablePoints.lastOrNull()?.let { "도착" to it })
            .forEach { (label, point) ->
                markers += Marker().apply {
                    position = LatLng(point.latitude.coerceIn(-Mercator.MAX_LATITUDE, Mercator.MAX_LATITUDE), point.longitude)
                    captionText = label
                    this.map = ready
                }
            }
        validRunners.forEach { runner ->
            markers += Marker().apply {
                position = LatLng(runner.latitude, runner.longitude)
                icon = OverlayImage.fromBitmap(Bitmap.createScaledBitmap(avatarBitmap(context, runner.avatar), 84, 126, false))
                width = 42; height = 63
                captionText = if (runner.memberCount > 1) "${runner.name} · ${runner.memberCount}명" else runner.name
                this.map = ready
            }
        }
        val current = usablePoints.lastOrNull()
        if (current != null) {
            ready.locationOverlay.position = LatLng(current.latitude.coerceIn(-Mercator.MAX_LATITUDE, Mercator.MAX_LATITUDE), current.longitude)
            ready.locationOverlay.isVisible = true
        } else {
            ready.locationOverlay.isVisible = false
        }
        if (!manualCamera || !cameraPositioned) positionCamera(ready)
    }
}

internal fun LiveRunner.hasValidCoordinates() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

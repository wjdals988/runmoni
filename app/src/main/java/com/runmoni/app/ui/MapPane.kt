package com.runmoni.app.ui

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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

@Composable
internal fun MapPane(points: List<TrackPoint>, runners: List<LiveRunner> = emptyList(), modifier: Modifier = Modifier, fitRoute: Boolean = false) {
    val geometry = remember(points) { RouteGeometry.from(points) }
    val usablePoints = remember(geometry) { geometry.points.map { it.point } }
    val validRunners = remember(runners) { runners.filter { it.hasValidCoordinates() } }
    var authFailed by remember { mutableStateOf(false) }
    if (LocalMapTileSource.current != null || BuildConfig.NAVER_MAP_CLIENT_ID.isBlank() || authFailed) {
        RouteTileMap(points, validRunners, modifier, fitRoute)
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
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    val tileSize = 256.0 * LocalDensity.current.density
    fun positionCamera(ready: NaverMap) {
        val fitted = if (fitRoute) RouteViewport.fit(geometry.points, mapSize.width.toDouble(), mapSize.height.toDouble(),
            32.0 * context.resources.displayMetrics.density, tileSize) else null
        if (fitted != null) ready.moveCamera(CameraUpdate.scrollAndZoomTo(
            LatLng(Mercator.latitude(fitted.centerY), Mercator.longitude(fitted.centerX)), fitted.zoom))
        else usablePoints.lastOrNull()?.let { ready.moveCamera(CameraUpdate.scrollAndZoomTo(LatLng(it.latitude.coerceIn(-Mercator.MAX_LATITUDE, Mercator.MAX_LATITUDE), it.longitude), 15.5)) }
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
    Box(modifier.testTag("naver_map").onSizeChanged { mapSize = it }) {
        AndroidView(factory = { view.apply { getMapAsync { map = it } } }, modifier = Modifier.fillMaxSize())
        if (usablePoints.isNotEmpty()) FilledTonalIconButton(shape = RunmoniShapes.Action, onClick = { map?.let(::positionCamera) }, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(48.dp)) { Icon(Icons.Outlined.MyLocation, if (fitRoute) "전체 러닝 경로 보기" else "GPS 경로 위치로 이동") }
    }
    LaunchedEffect(map, geometry, validRunners, mapSize, fitRoute) {
        val ready = map ?: return@LaunchedEffect
        ready.uiSettings.isZoomControlEnabled = true
        // Embedded history leaves the page's one-finger vertical scroll usable.
        // SDK pinch zoom and the full-route action remain available.
        ready.uiSettings.isScrollGesturesEnabled = !fitRoute
        ready.uiSettings.isZoomGesturesEnabled = true
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
            if (!cameraPositioned || fitRoute) {
                positionCamera(ready)
                cameraPositioned = true
            }
        } else {
            ready.locationOverlay.isVisible = false
            if (!cameraPositioned && validRunners.isNotEmpty()) {
                val runner = validRunners.first()
                ready.moveCamera(CameraUpdate.scrollAndZoomTo(LatLng(runner.latitude, runner.longitude), 14.0))
                cameraPositioned = true
            }
        }
    }
}

internal fun LiveRunner.hasValidCoordinates() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

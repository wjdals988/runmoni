package com.runmoni.app.ui

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.runmoni.app.domain.AvatarStyle
import kotlin.math.cos

@Composable
internal fun MapPane(points: List<TrackPoint>, runners: List<LiveRunner> = emptyList(), modifier: Modifier = Modifier) {
    val usablePoints = remember(points) { points.filter { it.validForRoute() } }
    val validRunners = remember(runners) { runners.filter { it.hasValidCoordinates() } }
    var authFailed by remember { mutableStateOf(false) }
    if (BuildConfig.NAVER_MAP_CLIENT_ID.isBlank() || authFailed) {
        Box(modifier.background(RunmoniColors.Mint).testTag("route_canvas")) {
            RouteCanvas(usablePoints, Modifier.fillMaxSize())
            Text(if (authFailed) "내 GPS 경로 · 지도 인증 확인 필요" else "내 GPS 경로 · 지도 연결 준비 필요", Modifier.align(Alignment.TopStart).padding(14.dp), style = MaterialTheme.typography.labelMedium)
            if (usablePoints.isEmpty()) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (points.isEmpty()) "아직 달린 경로가 없어요" else "GPS 정확도를 확인하고 있어요", style = MaterialTheme.typography.titleMedium)
                Text(if (points.isEmpty()) "러닝을 시작하면 실제 경로가 쌓여요" else "정확도 30m 이내의 좌표만 경로에 표시해요", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            }
        }
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
    Box(modifier.testTag("naver_map")) {
        AndroidView(factory = { view.apply { getMapAsync { map = it } } }, modifier = Modifier.fillMaxSize())
        if (usablePoints.isNotEmpty()) FilledTonalIconButton(onClick = { usablePoints.lastOrNull()?.let { map?.moveCamera(CameraUpdate.scrollAndZoomTo(LatLng(it.latitude, it.longitude), 15.5)) } }, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) { Icon(Icons.Outlined.MyLocation, "GPS 경로 위치로 이동") }
    }
    LaunchedEffect(map, usablePoints, validRunners) {
        val ready = map ?: return@LaunchedEffect
        ready.uiSettings.isZoomControlEnabled = false
        paths.forEach { it.map = null }; paths.clear()
        usablePoints.groupBy { it.segment }.values.forEach { segment ->
            if (segment.size > 1) paths += PathOverlay().apply {
                coords = segment.map { LatLng(it.latitude, it.longitude) }
                color = android.graphics.Color.rgb(241, 109, 85)
                width = 10
                this.map = ready
            }
        }
        markers.forEach { it.map = null }; markers.clear()
        validRunners.forEach { runner ->
            markers += Marker().apply {
                position = LatLng(runner.latitude, runner.longitude)
                icon = OverlayImage.fromBitmap(runnerBitmap(runner.avatar))
                width = 42; height = 52
                captionText = if (runner.memberCount > 1) "${runner.name} · ${runner.memberCount}명" else runner.name
                this.map = ready
            }
        }
        val current = usablePoints.lastOrNull()
        if (current != null) {
            ready.locationOverlay.position = LatLng(current.latitude, current.longitude)
            ready.locationOverlay.isVisible = true
            if (!cameraPositioned) {
                ready.moveCamera(CameraUpdate.scrollAndZoomTo(LatLng(current.latitude, current.longitude), 15.5))
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

@Composable
private fun RouteCanvas(points: List<TrackPoint>, modifier: Modifier) {
    Canvas(modifier) {
        val line = Color(0xFFD1E1D8)
        for (column in 0..8) drawLine(line, Offset(size.width * column / 8, 0f), Offset(size.width * column / 8, size.height), 1f)
        for (row in 0..6) drawLine(line, Offset(0f, size.height * row / 6), Offset(size.width, size.height * row / 6), 1f)
        val valid = points.filter { it.validForRoute() }
        if (valid.isEmpty()) return@Canvas
        val longitudeScale = cos(Math.toRadians(valid.first().latitude))
        val minX = valid.minOf { it.longitude * longitudeScale }
        val maxX = valid.maxOf { it.longitude * longitudeScale }
        val minY = valid.minOf { it.latitude }
        val maxY = valid.maxOf { it.latitude }
        val pad = 40.dp.toPx()
        val availableWidth = (size.width - 2 * pad).coerceAtLeast(1f)
        val availableHeight = (size.height - 2 * pad).coerceAtLeast(1f)
        val scale = minOf(availableWidth / (maxX - minX).coerceAtLeast(0.00015), availableHeight / (maxY - minY).coerceAtLeast(0.00015))
        fun at(point: TrackPoint) = Offset(
            (size.width / 2 + (point.longitude * longitudeScale - (minX + maxX) / 2) * scale).toFloat(),
            (size.height / 2 - (point.latitude - (minY + maxY) / 2) * scale).toFloat(),
        )
        valid.groupBy { it.segment }.values.forEach { segment ->
            val route = Path()
            segment.forEachIndexed { index, point -> val p = at(point); if (index == 0) route.moveTo(p.x, p.y) else route.lineTo(p.x, p.y) }
            drawPath(route, Color.White, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
            drawPath(route, RunmoniColors.Coral, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        }
        drawCircle(Color.White, 8.dp.toPx(), at(valid.last()))
        drawCircle(RunmoniColors.Coral, 5.dp.toPx(), at(valid.last()))
    }
}

private fun TrackPoint.validForRoute() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0 && accuracyMeters.isFinite() && accuracyMeters in 0f..30f

internal fun LiveRunner.hasValidCoordinates() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

private fun runnerBitmap(avatar: AvatarStyle): Bitmap {
    val bitmap = Bitmap.createBitmap(84, 104, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(42f, 45f, 38f, paint)
    paint.color = when (avatar.bottom % 4) { 1 -> android.graphics.Color.rgb(83, 110, 97); 2 -> android.graphics.Color.rgb(124, 143, 172); 3 -> android.graphics.Color.rgb(197, 126, 104); else -> android.graphics.Color.rgb(25, 51, 64) }
    canvas.drawRoundRect(27f, 66f, 57f, 87f, 8f, 8f, paint)
    paint.color = topColor(avatar.top).toArgb()
    canvas.drawRoundRect(24f, 46f, 60f, 70f, 8f, 8f, paint)
    paint.color = android.graphics.Color.rgb(240, 194, 160)
    canvas.drawCircle(42f, 31f, 19f, paint)
    paint.color = android.graphics.Color.rgb(25, 51, 64)
    canvas.drawCircle(35f, 31f, 2f, paint); canvas.drawCircle(49f, 31f, 2f, paint)
    paint.color = when (avatar.hair % 4) { 1 -> android.graphics.Color.rgb(121, 88, 68); 2 -> android.graphics.Color.rgb(216, 172, 88); 3 -> android.graphics.Color.rgb(117, 99, 139); else -> android.graphics.Color.rgb(25, 51, 64) }
    canvas.drawArc(24f, 9f, 60f, 39f, 180f, 180f, true, paint)
    if (avatar.accessory == 1) { paint.color = android.graphics.Color.rgb(138, 191, 193); canvas.drawRoundRect(23f, 20f, 61f, 26f, 3f, 3f, paint) }
    if (avatar.accessory == 2) { paint.color = topColor(avatar.top).toArgb(); canvas.drawArc(22f, 8f, 62f, 37f, 180f, 180f, true, paint); canvas.drawRoundRect(44f, 19f, 69f, 25f, 3f, 3f, paint) }
    if (avatar.accessory == 3) { paint.color = android.graphics.Color.rgb(145, 184, 205); canvas.drawRoundRect(19f, 25f, 27f, 39f, 3f, 3f, paint); canvas.drawRoundRect(58f, 25f, 66f, 39f, 3f, 3f, paint) }
    return bitmap
}

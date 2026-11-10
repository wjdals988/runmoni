package com.runmoni.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runmoni.app.data.MapTileRepository
import com.runmoni.app.data.MapTileResult
import com.runmoni.app.data.MapTileSource
import com.runmoni.app.domain.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Tests provide an inert unavailable source; it must never be described as a
 * successful online basemap. Null uses the real bounded OSM repository. */
internal val LocalMapTileSource = staticCompositionLocalOf<MapTileSource?> { null }

@Composable
internal fun RouteTileMap(points: List<TrackPoint>, runners: List<LiveRunner>, modifier: Modifier, fitRoute: Boolean) {
    val context = LocalContext.current
    val source = LocalMapTileSource.current ?: remember(context) { MapTileRepository.get(context) }
    val geometry = remember(points) { RouteGeometry.from(points) }
    val route = remember(geometry) { geometry.points }
    val validRunners = remember(runners) { runners.filter { it.hasValidCoordinates() } }
    val avatars = remember(validRunners, context) { validRunners.associate { it.id to avatarBitmap(context, it.avatar).asImageBitmap() } }
    val tileSize = 256.0 * LocalDensity.current.density
    val captionSize = with(LocalDensity.current) { 12.sp.toPx() }
    val captionPaint = remember(captionSize) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = RunmoniColors.Navy.toArgb(); textSize = captionSize
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    } }
    val captionBackground = remember { android.graphics.Paint().apply { color = android.graphics.Color.argb(240, 255, 255, 255) } }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var viewport by remember(fitRoute) { mutableStateOf<RouteViewport?>(null) }
    var manual by remember(fitRoute) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    val images = remember(source) { mutableStateMapOf<MapTileKey, MapTileResult>() }
    val uri = LocalUriHandler.current
    val mapWidth = canvasSize.width.toDouble()
    val mapHeight = canvasSize.height.toDouble()
    val padding = 16.0 * LocalDensity.current.density
    fun reset(): RouteViewport? = if (fitRoute) RouteViewport.fit(route, mapWidth, mapHeight, padding, tileSize)
        else route.lastOrNull()?.let { RouteViewport.follow(it) }
            ?: validRunners.firstOrNull()?.let { RouteViewport(Mercator.x(it.longitude), Mercator.y(it.latitude), 14.0) }
    LaunchedEffect(route, validRunners, canvasSize, fitRoute) {
        if (canvasSize.width > 0 && canvasSize.height > 0 && (!manual || viewport == null)) viewport = reset()
    }
    val placements = remember(viewport, canvasSize, tileSize) { viewport?.tiles(mapWidth, mapHeight, tileSize).orEmpty() }
    val requested = remember(placements) { placements.map { it.tile }.distinct() }
    val requestIdentity = remember(source, requested, attempt) { Any() }
    val latestRequestIdentity by rememberUpdatedState(requestIdentity)
    LaunchedEffect(requestIdentity) {
        images.keys.toList().filter { it !in requested }.forEach(images::remove)
        loading = requested.isNotEmpty()
        try {
            for (tile in requested) {
                val result = source.load(tile)
                currentCoroutineContext().ensureActive()
                if (latestRequestIdentity === requestIdentity) images[tile] = result
            }
        } finally {
            // A canceled old HTTP read may finish after the next viewport has
            // begun loading. Only the current request owns its loading state.
            if (latestRequestIdentity === requestIdentity) loading = false
        }
    }
    val failed = requested.isNotEmpty() && !loading && requested.any { images[it]?.bitmap == null || images[it]?.stale == true }
    Column(modifier.background(RunmoniColors.Paper).testTag("route_canvas")) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            if (failed) {
                Text("지도 연결 실패", style = MaterialTheme.typography.labelSmall, color = RunmoniColors.Muted,
                    modifier = Modifier.padding(horizontal = 8.dp).testTag("map_tile_error"))
                FilledTonalIconButton(shape = RunmoniShapes.Action, onClick = { source.retry(); attempt++ }, modifier = Modifier.size(48.dp).testTag("map_tile_retry")) {
                    Icon(Icons.Outlined.Refresh, "배경 지도 다시 불러오기")
                }
            }
            FilledTonalIconButton(shape = RunmoniShapes.Action, onClick = {
                manual = true
                viewport = viewport?.zoomAt(2.0, mapWidth / 2, mapHeight / 2, mapWidth, mapHeight, tileSize)
            }, enabled = viewport != null && viewport!!.zoom < 18, modifier = Modifier.size(48.dp).testTag("map_zoom_in")) { Icon(Icons.Outlined.Add, "지도 확대") }
            FilledTonalIconButton(shape = RunmoniShapes.Action, onClick = {
                manual = true
                viewport = viewport?.zoomAt(.5, mapWidth / 2, mapHeight / 2, mapWidth, mapHeight, tileSize)
            }, enabled = viewport != null && viewport!!.zoom > 0, modifier = Modifier.size(48.dp).testTag("map_zoom_out")) { Icon(Icons.Outlined.Remove, "지도 축소") }
            FilledTonalIconButton(shape = RunmoniShapes.Action, onClick = { manual = false; viewport = reset() }, enabled = route.isNotEmpty() || validRunners.isNotEmpty(),
                modifier = Modifier.size(48.dp).testTag("map_recenter")) {
                Icon(Icons.Outlined.MyLocation, if (fitRoute) "전체 러닝 경로 보기" else "현재 GPS 위치로 이동")
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { canvasSize = it }
            .pointerInput(tileSize, fitRoute) {
                fun transform(centroid: Offset, pan: Offset, zoom: Float) {
                    viewport?.let { current ->
                        manual = true
                        viewport = current.pan(pan.x.toDouble(), pan.y.toDouble(), tileSize)
                            .zoomAt(zoom.toDouble(), centroid.x.toDouble(), centroid.y.toDouble(),
                                size.width.toDouble(), size.height.toDouble(), tileSize)
                    }
                }
                // Both history and live maps are embedded in scrolling pages.
                // Only two-finger gestures consume movement; one finger keeps
                // the page and its controls below the map reachable.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed && it.previousPressed } >= 2) {
                            transform(event.calculateCentroid(), event.calculatePan(), event.calculateZoom())
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }.testTag("route_map_viewport").semantics {
                contentDescription = if (route.isEmpty() && validRunners.isEmpty()) "표시할 위치가 없습니다" else
                    (if (route.isNotEmpty()) "실제 GPS 경로 ${route.size}개 지점. 시작과 끝을 표시하고 중단 구간은 연결하지 않습니다."
                        else "위치를 공유한 러너 ${validRunners.size}명.") +
                        (if (failed) " 배경 지도 연결 실패. 위치 표시는 유지합니다." else if (loading) " 배경 지도를 불러오는 중입니다." else "") +
                        " 두 손가락으로 지도를 이동하거나 확대합니다."
            }) {
            Canvas(Modifier.fillMaxSize()) {
                val view = viewport ?: return@Canvas
                placements.forEach { placement ->
                    images[placement.tile]?.bitmap?.let { bitmap ->
                        drawImage(bitmap.asImageBitmap(), srcSize = IntSize(bitmap.width, bitmap.height),
                            dstOffset = IntOffset(placement.left.roundToInt(), placement.top.roundToInt()),
                            dstSize = IntSize(placement.size.roundToInt() + 1, placement.size.roundToInt() + 1))
                    }
                }
                fun at(x: Double, y: Double): Offset = view.screen(x, y, size.width.toDouble(), size.height.toDouble(), tileSize)
                    .let { Offset(it.x.toFloat(), it.y.toFloat()) }
                fun caption(text: String, position: Offset, below: Boolean) {
                    if (position.x !in 0f..size.width || position.y !in 0f..size.height) return
                    val pad = 4.dp.toPx()
                    val width = captionPaint.measureText(text) + pad * 2
                    val height = captionPaint.descent() - captionPaint.ascent() + pad * 2
                    val left = (position.x - width / 2).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
                    val top = (if (below) position.y + 10.dp.toPx() else position.y - height - 10.dp.toPx())
                        .coerceIn(0f, (size.height - height).coerceAtLeast(0f))
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRoundRect(left, top, left + width, top + height, pad, pad, captionBackground)
                        canvas.nativeCanvas.drawText(text, left + pad, top + pad - captionPaint.ascent(), captionPaint)
                    }
                }
                val halfWorld = tileSize * 2.0.pow(view.zoom) / 2
                geometry.segments.forEach { segment ->
                    val path = Path()
                    var previous: Offset? = null
                    segment.forEach { node ->
                        val p = at(node.x, node.y)
                        // A world-wrap seam is not a line across the entire map.
                        if (previous == null || abs(p.x - previous!!.x) > halfWorld) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                        previous = p
                    }
                    drawPath(path, Color.White, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(path, RunmoniColors.Primary, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
                route.firstOrNull()?.let { first ->
                    drawCircle(Color.White, 8.dp.toPx(), at(first.x, first.y))
                    drawCircle(RunmoniColors.Primary, 5.dp.toPx(), at(first.x, first.y))
                    caption("시작", at(first.x, first.y), below = true)
                }
                route.lastOrNull()?.let { last ->
                    drawCircle(Color.White, 9.dp.toPx(), at(last.x, last.y))
                    drawCircle(RunmoniColors.Navy, 6.dp.toPx(), at(last.x, last.y))
                    drawCircle(Color.White, 2.dp.toPx(), at(last.x, last.y))
                    caption("도착", at(last.x, last.y), below = false)
                }
                validRunners.forEach { runner ->
                    val image = avatars[runner.id] ?: return@forEach
                    val position = at(Mercator.x(runner.longitude), Mercator.y(runner.latitude))
                    val width = 28.dp.toPx().roundToInt(); val height = 42.dp.toPx().roundToInt()
                    drawImage(image, srcSize = IntSize(image.width, image.height), dstOffset = IntOffset(position.x.roundToInt() - width / 2, position.y.roundToInt() - height), dstSize = IntSize(width, height))
                    caption(if (runner.memberCount > 1) "${runner.name} · ${runner.memberCount}명" else runner.name, position, below = true)
                }
            }
            if (route.isEmpty() && validRunners.isEmpty()) Text(
                if (points.isEmpty() && fitRoute) "위치가 저장되지 않은 기록이에요" else "위치 정보 없음",
                style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp, vertical = if (fitRoute) 12.dp else 4.dp).testTag("map_location_empty"))
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).testTag("map_tiles_loading"))
        }
        TextButton(onClick = { uri.openUri("https://www.openstreetmap.org/copyright") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("osm_attribution"), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
        }
    }
}

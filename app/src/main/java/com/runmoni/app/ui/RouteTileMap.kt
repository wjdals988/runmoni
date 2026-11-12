package com.runmoni.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
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
import kotlinx.coroutines.delay

/** Tests provide an inert unavailable source; it must never be described as a
 * successful online basemap. Null uses the real bounded OSM repository. */
internal val LocalMapTileSource = staticCompositionLocalOf<MapTileSource?> { null }

@Composable
internal fun RouteTileMap(
    points: List<TrackPoint>, runners: List<LiveRunner>, modifier: Modifier, fitRoute: Boolean,
    interactiveMap: Boolean = false, followCenter: RouteMapCenter? = null,
    onBoundsChanged: ((RouteMapBounds) -> Unit)? = null,
) {
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
    var viewport by remember(fitRoute, interactiveMap) { mutableStateOf<RouteViewport?>(null) }
    var manual by remember(fitRoute, interactiveMap) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    val images = remember(source) { mutableStateMapOf<MapTileKey, MapTileResult>() }
    val uri = LocalUriHandler.current
    val mapWidth = canvasSize.width.toDouble()
    val mapHeight = canvasSize.height.toDouble()
    val padding = 16.0 * LocalDensity.current.density
    val actualCenter = followCenter?.takeIf { it.valid() }
    val currentOnBoundsChanged by rememberUpdatedState(onBoundsChanged)
    fun reset(): RouteViewport? = when {
        fitRoute && route.isNotEmpty() -> RouteViewport.fit(route, mapWidth, mapHeight, padding, tileSize)
        actualCenter != null -> actualCenter.viewport()
        route.isNotEmpty() -> RouteViewport.follow(route.last())
        interactiveMap -> RouteViewport.world() // A browse view, never a fake user fix.
        validRunners.isNotEmpty() -> validRunners.first().let { RouteViewport(Mercator.x(it.longitude), Mercator.y(it.latitude), 14.0) }
        else -> null
    }
    fun zoomMap(factor: Double): Boolean {
        val before = viewport ?: return false
        val after = before.zoomAt(factor, mapWidth / 2, mapHeight / 2, mapWidth, mapHeight, tileSize)
        if (after == before) return false
        manual = true; viewport = after
        return true
    }
    fun panMap(x: Double, y: Double): Boolean {
        val before = viewport ?: return false
        manual = true; viewport = before.pan(x, y, tileSize)
        return true
    }
    fun recenter(): Boolean {
        val next = reset() ?: return false
        manual = false; viewport = next
        return true
    }
    val recenterLabel = when {
        fitRoute -> "전체 러닝 경로 보기"
        actualCenter != null -> "내 위치로 이동"
        route.isNotEmpty() -> "경로 위치로 이동"
        else -> "처음 지도 영역 보기"
    }
    val mapActions = listOf(
        CustomAccessibilityAction("지도 확대") { zoomMap(2.0) },
        CustomAccessibilityAction("지도 축소") { zoomMap(.5) },
        CustomAccessibilityAction("서쪽으로 이동") { panMap(mapWidth * .35, 0.0) },
        CustomAccessibilityAction("동쪽으로 이동") { panMap(-mapWidth * .35, 0.0) },
        CustomAccessibilityAction("북쪽으로 이동") { panMap(0.0, mapHeight * .35) },
        CustomAccessibilityAction("남쪽으로 이동") { panMap(0.0, -mapHeight * .35) },
        CustomAccessibilityAction(recenterLabel) { recenter() },
    )
    LaunchedEffect(route, validRunners, canvasSize, fitRoute, interactiveMap, actualCenter) {
        if (canvasSize.width > 0 && canvasSize.height > 0 && (!manual || viewport == null)) viewport = reset()
    }
    LaunchedEffect(viewport, canvasSize, tileSize) {
        val bounds = viewport?.bounds(mapWidth, mapHeight, tileSize) ?: return@LaunchedEffect
        delay(250) // Bounds observers do not poll a backend on every pointer event.
        currentOnBoundsChanged?.invoke(bounds)
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
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { canvasSize = it }
            .pointerInput(tileSize, fitRoute, interactiveMap, viewport != null) {
                if (viewport == null) return@pointerInput
                detectTransformGestures { centroid, pan, zoom, _ ->
                    viewport?.let { current ->
                        manual = true
                        viewport = current.zoomAt(zoom.toDouble(), centroid.x.toDouble(), centroid.y.toDouble(),
                            size.width.toDouble(), size.height.toDouble(), tileSize)
                            .pan(pan.x.toDouble(), pan.y.toDouble(), tileSize)
                    }
                }
            }.testTag("route_map_viewport").semantics {
                contentDescription = if (route.isEmpty() && validRunners.isEmpty() && actualCenter == null && !interactiveMap) "표시할 위치가 없습니다" else
                    (if (route.isNotEmpty()) "실제 GPS 경로 ${route.size}개 지점. 시작과 끝을 표시하고 중단 구간은 연결하지 않습니다."
                        else if (validRunners.isNotEmpty()) "위치를 공유한 러너 ${validRunners.size}명."
                        else if (actualCenter != null) "확인된 위치 주변 지도." else "세계 지도. 내 위치는 확인되지 않았습니다.") +
                        (if (failed) " 배경 지도 연결 실패. 위치 표시는 유지합니다." else if (loading) " 배경 지도를 불러오는 중입니다." else "") +
                        " 드래그로 지도를 이동하고 두 손가락으로 확대하거나 축소합니다."
                customActions = mapActions
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
                    drawPath(path, RunmoniColors.RouteBlue, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
                route.firstOrNull()?.let { first ->
                    drawCircle(Color.White, 8.dp.toPx(), at(first.x, first.y))
                    drawCircle(RunmoniColors.RouteBlue, 5.dp.toPx(), at(first.x, first.y))
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
            if (route.isEmpty() && validRunners.isEmpty() && actualCenter == null && !interactiveMap) Text(
                if (points.isEmpty() && fitRoute) "위치가 저장되지 않은 기록이에요" else "위치 정보 없음",
                style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp, vertical = if (fitRoute) 12.dp else 4.dp).testTag("map_location_empty"))
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).testTag("map_tiles_loading"))
        }
        // Device location and basemap availability are independent. A failed
        // tile request must never hide the absence of a confirmed user fix.
        val status = listOfNotNull(
            "지도 연결 실패".takeIf { failed },
            "내 위치 확인 전".takeIf { interactiveMap && route.isEmpty() && actualCenter == null },
        ).joinToString(" · ").takeIf { it.isNotEmpty() }
        if (status != null) Text(status, style = MaterialTheme.typography.labelSmall, color = RunmoniColors.Muted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                .then(if (failed) Modifier.testTag("map_tile_error") else Modifier))
        MapControlBar(mapActions, enabled = viewport != null,
            retry = if (failed) ({ source.retry(); attempt++ }) else null,
            attribution = {
                TextButton(onClick = { uri.openUri("https://www.openstreetmap.org/copyright") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("osm_attribution"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
                }
            })
        if (fitRoute && !interactiveMap) MapPageScrollHint()
    }
}

@Composable
internal fun MapPageScrollHint() {
    Text("본문은 바깥에서 스크롤", style = MaterialTheme.typography.labelSmall, color = RunmoniColors.Muted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("map_page_scroll_hint"))
}

private class MapMenuLifetime { var active = true }

/** All pointer controls sit outside the geographic viewport; its pixels belong
 * to the route, runners and provider attribution rather than floating buttons. */
@Composable
internal fun MapControlBar(
    actions: List<CustomAccessibilityAction>, enabled: Boolean,
    retry: (() -> Unit)? = null, attribution: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().background(RunmoniColors.Paper)
        .testTag("map_control_bar"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End) {
        attribution?.invoke(this)
        MapOptionsMenu(actions, enabled, retry)
    }
}

/** Pointer alternative to pinch/drag without permanent +/- buttons. A delayed
 * item/dismiss callback cannot act on a reopened menu or a disposed map. */
@Composable
internal fun MapOptionsMenu(actions: List<CustomAccessibilityAction>, enabled: Boolean = true, retry: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    var request by remember { mutableIntStateOf(0) }
    val latestActions by rememberUpdatedState(actions)
    val latestRetry by rememberUpdatedState(retry)
    val latestEnabled by rememberUpdatedState(enabled)
    val lifetime = remember { MapMenuLifetime() }
    DisposableEffect(lifetime) { onDispose { lifetime.active = false } }
    val currentRequest = request
    Box {
        TextButton(enabled = enabled, shape = RunmoniShapes.Action,
            onClick = { request++; expanded = true }, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).testTag("map_options")) {
            Text("지도 조작", style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = {
            if (lifetime.active && request == currentRequest) expanded = false
        }) {
            if (retry != null) DropdownMenuItem(text = { Text("배경 지도 다시 불러오기") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("map_tile_retry"), onClick = {
                    if (lifetime.active && expanded && latestEnabled && request == currentRequest) {
                        expanded = false
                        latestRetry?.invoke()
                    }
                })
            actions.forEach { action ->
                DropdownMenuItem(text = { Text(action.label) }, modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    if (lifetime.active && expanded && latestEnabled && request == currentRequest) {
                        expanded = false
                        latestActions.singleOrNull { it.label == action.label }?.action?.invoke()
                    }
                })
            }
        }
    }
}

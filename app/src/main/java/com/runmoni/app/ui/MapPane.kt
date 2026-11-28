package com.runmoni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*

internal class MapRecenterRequest(val center: RouteMapCenter)

@Composable
internal fun MapPane(
    points: List<TrackPoint>, runners: List<LiveRunner> = emptyList(), modifier: Modifier = Modifier,
    fitRoute: Boolean = false, interactiveMap: Boolean = false, followCenter: RouteMapCenter? = null,
    onBoundsChanged: ((RouteMapBounds) -> Unit)? = null,
    recenterRequest: MapRecenterRequest? = null,
    highlightedRoute: RouteGeometry? = null,
) {
    val validRunners = remember(runners) { runners.filter { it.hasValidCoordinates() } }
    val hasRoute = remember(points) { RouteGeometry.from(points).points.isNotEmpty() }
    var appliedRecenter by remember { mutableStateOf<MapRecenterRequest?>(null) }
    val pendingRecenter = recenterRequest?.takeUnless { it === appliedRecenter }
    val onRecenterApplied: (MapRecenterRequest) -> Unit = { appliedRecenter = it }
    // Existing offline tests must never instantiate a networked native provider.
    // A record without a position also stays empty rather than inventing a map fix.
    if (LocalMapTileSource.current != null ||
        (!interactiveMap && !hasRoute && validRunners.isEmpty() && followCenter?.valid() != true)) {
        RouteTileMap(points, validRunners, modifier, fitRoute, interactiveMap, followCenter,
            onBoundsChanged, pendingRecenter, onRecenterApplied, highlightedRoute)
        return
    }
    var failure by remember { mutableStateOf<VectorMapFailure?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var savedCamera by remember { mutableStateOf<RouteMapCenter?>(null) }
    if (failure == null) {
        key(attempt) {
            MapLibreMapPane(points, validRunners, modifier, fitRoute, interactiveMap, followCenter,
                onBoundsChanged, savedCamera, onCameraSaved = { savedCamera = it }, onFailure = { failure = it },
                recenterRequest = pendingRecenter, onRecenterApplied = onRecenterApplied, highlightedRoute = highlightedRoute)
        }
    } else {
        Column(modifier) {
            TextButton(onClick = { attempt++; failure = null }, shape = RunmoniShapes.Action,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("map_vector_retry")) {
                Text("벡터 지도 연결 실패 · 다시 시도", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("map_vector_error"))
            }
            // This is the actual raster fallback, with its own loading/error and
            // attribution. It is never reported as a rendered vector basemap.
            RouteTileMap(points, validRunners, Modifier.fillMaxWidth().weight(1f), fitRoute, interactiveMap,
                savedCamera ?: followCenter, onBoundsChanged, pendingRecenter, onRecenterApplied, highlightedRoute)
        }
    }
}

internal fun LiveRunner.hasValidCoordinates() = latitude.isFinite() && longitude.isFinite() &&
    latitude in -90.0..90.0 && longitude in -180.0..180.0

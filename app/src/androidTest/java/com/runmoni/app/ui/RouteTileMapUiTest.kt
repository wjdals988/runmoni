package com.runmoni.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.data.MapTileResult
import com.runmoni.app.data.MapTileSource
import com.runmoni.app.domain.MapTileKey
import com.runmoni.app.domain.TrackPoint
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Inert unavailable tiles on Android; these fixtures never download public
 * OSM tiles or prove an online basemap/GPS/physical-device result. */
class RouteTileMapUiTest : RunmoniDesignTestBase() {
    private val points = listOf(
        TrackPoint(0, 37.5665, 126.978, 5f, 1000),
        TrackPoint(1, 37.5670, 126.9785, 5f, 2000),
        TrackPoint(2, 37.5680, 126.979, 5f, 3000, segment = 1),
    )

    @Test fun aCanceledViewportCannotClearTheNextViewportsLoadingState() {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstFinished = AtomicBoolean()
        val nextStarted = CompletableDeferred<Unit>()
        val releaseNext = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val source = MapTileSource {
            if (calls.incrementAndGet() == 1) {
                // Model a blocking HTTP read that returns after cancellation.
                try {
                    withContext(NonCancellable) { firstStarted.complete(Unit); releaseFirst.await() }
                } finally { firstFinished.set(true) }
            } else {
                nextStarted.complete(Unit)
                releaseNext.await()
            }
            MapTileResult()
        }
        try {
            compose.setContent {
                CompositionLocalProvider(LocalMapTileSource provides source) {
                    RunmoniTheme { MapPane(points, modifier = Modifier.fillMaxWidth().height(300.dp), fitRoute = true) }
                }
            }
            compose.waitUntil(30_000) { firstStarted.isCompleted }
            compose.onNodeWithTag("map_zoom_in").performClick()
            compose.waitUntil(30_000) { nextStarted.isCompleted }
            compose.runOnIdle { releaseFirst.complete(Unit) }
            compose.waitUntil(30_000) { firstFinished.get() }
            compose.onNodeWithTag("map_tiles_loading").assertExists()
            compose.onNodeWithTag("map_tile_error").assertDoesNotExist()
            compose.runOnIdle { releaseNext.complete(Unit) }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithTag("map_tile_error").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("map_tiles_loading").assertDoesNotExist()
        } finally {
            releaseFirst.complete(Unit)
            releaseNext.complete(Unit)
        }
    }

    @Test fun unavailableTilesKeepRealRouteControlsAttributionAndRetryAtLargeText() {
        val requests = mutableListOf<MapTileKey>()
        val source = MapTileSource { key -> requests += key; MapTileResult() }
        compose.setContent {
            CompositionLocalProvider(LocalMapTileSource provides source) {
                ViewportBox(Viewport(320, 2f)) {
                    RunmoniTheme { MapPane(points, modifier = Modifier.fillMaxWidth().height(300.dp), fitRoute = true) }
                }
            }
        }
        compose.onNodeWithTag("route_map_viewport").assertContentDescriptionContains("중단 구간은 연결하지 않습니다", substring = true)
        compose.onNodeWithTag("map_tile_error").assertIsDisplayed()
        compose.onNodeWithText("© OpenStreetMap contributors").assertIsDisplayed()
        for (tag in listOf("map_tile_retry", "map_zoom_in", "map_zoom_out", "map_recenter")) {
            compose.onNodeWithTag(tag).performScrollTo().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        val before = requests.size
        compose.onNodeWithTag("map_tile_retry").performScrollTo().performClick()
        compose.waitUntil(30_000) { requests.size > before }
        compose.onNodeWithTag("map_zoom_in").performScrollTo().performClick()
        compose.onNodeWithTag("map_zoom_out").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("map_recenter").performScrollTo().assertIsEnabled().performClick()
        noTextOverflow("inert unavailable basemap/real route controls at320dp-font2")
        capture("record-map-unavailable-font2-fixture")
    }

    @Test fun oneFingerMapSwipeKeepsTheRecordPageAndItsLowerActionReachable() {
        val scroll = ScrollState(0)
        val saves = AtomicInteger()
        compose.setContent {
            CompositionLocalProvider(LocalMapTileSource provides MapTileSource { MapTileResult() }) {
                RunmoniTheme {
                    Column(Modifier.fillMaxWidth().height(440.dp).verticalScroll(scroll)) {
                        MapPane(points, modifier = Modifier.fillMaxWidth().height(300.dp), fitRoute = true)
                        Spacer(Modifier.height(500.dp))
                        Button(onClick = { saves.incrementAndGet() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("기록 하단 행동") }
                    }
                }
            }
        }
        compose.onNodeWithTag("route_map_viewport").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue("One-finger map drag must scroll the enclosing history page", scroll.value > 0) }
        compose.onNodeWithText("기록 하단 행동").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, saves.get()) }
    }

    @Test fun aRecordWithoutLocationsNeverInventsACenterOrFetchesTiles() {
        val requests = AtomicInteger()
        compose.setContent {
            CompositionLocalProvider(LocalMapTileSource provides MapTileSource { requests.incrementAndGet(); MapTileResult() }) {
                RunmoniTheme { MapPane(emptyList(), modifier = Modifier.fillMaxWidth().height(300.dp), fitRoute = true) }
            }
        }
        compose.onNodeWithTag("map_location_empty").assertTextEquals("위치가 저장되지 않은 기록이에요")
        compose.onNodeWithTag("map_recenter").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, requests.get()) }
    }
}

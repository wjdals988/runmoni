package com.runmoni.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.data.MapTileResult
import com.runmoni.app.data.MapTileSource
import com.runmoni.app.domain.MapTileKey
import com.runmoni.app.domain.TrackPoint
import com.runmoni.app.domain.RouteMapBounds
import com.runmoni.app.domain.RouteMapCenter
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
    private fun pinchOut() {
        compose.onNodeWithTag("route_map_viewport").performTouchInput {
            pinch(start0 = center + Offset(-width * .12f, 0f), start1 = center + Offset(width * .12f, 0f),
                end0 = center + Offset(-width * .35f, 0f), end1 = center + Offset(width * .35f, 0f), durationMillis = 400)
        }
    }

    private fun mapAccessibilityAction(label: String) {
        val actions = compose.onNodeWithTag("route_map_viewport").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.single { it.label == label }.action()) }
    }

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
            pinchOut()
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
        compose.onNodeWithTag("map_zoom_in").assertDoesNotExist()
        compose.onNodeWithTag("map_zoom_out").assertDoesNotExist()
        for (tag in listOf("map_tile_retry", "map_options", "map_recenter")) {
            compose.onNodeWithTag(tag).performScrollTo().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        val before = requests.size
        compose.onNodeWithTag("map_tile_retry").performScrollTo().performClick()
        compose.waitUntil(30_000) { requests.size > before }
        compose.onNodeWithTag("map_options").performScrollTo().performClick()
        compose.onNodeWithText("지도 확대").performClick()
        compose.onNodeWithText("지도 확대").assertDoesNotExist()
        mapAccessibilityAction("지도 축소")
        compose.onNodeWithTag("map_recenter").performScrollTo().assertIsEnabled().performClick()
        noTextOverflow("inert unavailable basemap/real route controls at320dp-font2")
        capture("record-map-unavailable-font2-fixture")
    }

    @Test fun oneFingerDragMovesTheMapAndThePageScrollsFromOutsideTheMap() {
        val scroll = ScrollState(0)
        val saves = AtomicInteger()
        val bounds = AtomicReference<RouteMapBounds?>()
        compose.setContent {
            CompositionLocalProvider(LocalMapTileSource provides MapTileSource { MapTileResult() }) {
                RunmoniTheme {
                    Column(Modifier.fillMaxWidth().height(440.dp).verticalScroll(scroll)) {
                        Text("본문 스크롤 영역", modifier = Modifier.fillMaxWidth().height(96.dp).testTag("outside_map_scroll"))
                        MapPane(points, modifier = Modifier.fillMaxWidth().height(300.dp), fitRoute = true,
                            onBoundsChanged = { bounds.set(it) })
                        Spacer(Modifier.height(500.dp))
                        Button(onClick = { saves.incrementAndGet() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("기록 하단 행동") }
                    }
                }
            }
        }
        compose.waitUntil(30_000) { bounds.get() != null }
        val original = bounds.get()!!
        compose.onNodeWithTag("route_map_viewport").performTouchInput { swipeUp() }
        compose.waitUntil(30_000) { bounds.get() != original }
        compose.runOnIdle {
            assertEquals("A map drag takes priority over the enclosing page", 0, scroll.value)
            assertTrue("The geographic region moved with the finger", bounds.get()!!.north < original.north)
        }
        compose.onNodeWithTag("map_page_scroll_hint").assertIsDisplayed()
        compose.onNodeWithTag("outside_map_scroll").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue("The page remains scrollable outside the map", scroll.value > 0) }
        compose.onNodeWithText("기록 하단 행동").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, saves.get()) }
    }

    @Test fun explorationPinchAndDragOverrideFollowingUntilTheUserRecenters() {
        val center = mutableStateOf(RouteMapCenter(37.5665, 126.978))
        val bounds = AtomicReference<RouteMapBounds?>()
        compose.setContent {
            CompositionLocalProvider(LocalMapTileSource provides MapTileSource { MapTileResult() }) {
                RunmoniTheme { MapPane(emptyList(), modifier = Modifier.fillMaxWidth().height(400.dp), interactiveMap = true,
                    followCenter = center.value, onBoundsChanged = { bounds.set(it) }) }
            }
        }
        compose.waitUntil(30_000) { bounds.get() != null }
        val before = bounds.get()!!
        pinchOut()
        compose.waitUntil(30_000) { bounds.get() != before }
        val zoomed = bounds.get()!!
        assertTrue("Pinch shrinks the visible geographic span", zoomed.east - zoomed.west < (before.east - before.west) * .7)
        compose.onNodeWithTag("route_map_viewport").performTouchInput { swipeLeft() }
        compose.waitUntil(30_000) { bounds.get() != zoomed }
        val dragged = bounds.get()!!
        compose.runOnIdle { center.value = RouteMapCenter(35.1796, 129.0756) }
        compose.waitForIdle()
        assertEquals("A host fix does not interrupt manual browsing", dragged, bounds.get())
        compose.onNodeWithTag("map_recenter").performScrollTo().performClick()
        compose.waitUntil(30_000) { bounds.get()?.contains(35.1796, 129.0756) == true }
        compose.onNodeWithTag("map_page_scroll_hint").assertDoesNotExist()
    }

    @Test fun unlocatedExplorationShowsABrowsableWorldWithoutInventingARouteOrUserFix() {
        val bounds = AtomicReference<RouteMapBounds?>()
        compose.setContent {
            CompositionLocalProvider(LocalMapTileSource provides MapTileSource { MapTileResult() }) {
                RunmoniTheme { MapPane(emptyList(), modifier = Modifier.width(320.dp).height(400.dp), interactiveMap = true,
                    onBoundsChanged = { bounds.set(it) }) }
            }
        }
        compose.waitUntil(30_000) { bounds.get() != null }
        compose.onNodeWithTag("route_map_viewport").assertContentDescriptionContains("내 위치는 확인되지 않았습니다", substring = true)
        compose.onNodeWithTag("map_location_empty").assertDoesNotExist()
        compose.onNodeWithText("내 위치 확인 전").assertIsDisplayed()
        mapAccessibilityAction("지도 확대")
        compose.waitUntil(30_000) { bounds.get()!!.east - bounds.get()!!.west < 360 }
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

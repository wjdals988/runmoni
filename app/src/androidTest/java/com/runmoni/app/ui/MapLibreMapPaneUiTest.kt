package com.runmoni.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.data.MapTileResult
import com.runmoni.app.data.MapTileSource
import com.runmoni.app.domain.VectorMapFailure
import com.runmoni.app.domain.RouteMapCenter
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.maps.MapView
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** No public map requests. Missing asset style tests actual native SDK failure;
 * initialization injection and inert raster remain separate fixture evidence. */
class MapLibreMapPaneUiTest : RunmoniDesignTestBase() {
    @Test fun offlineFixtureNeverConstructsTheProductionNativeProvider() {
        var constructed = 0
        val reject: (Context) -> MapView = { constructed++; error("Fixture must not construct a native view") }
        val tiles = MapTileSource { MapTileResult() }
        compose.setContent { RunmoniTheme {
            CompositionLocalProvider(LocalMapTileSource provides tiles, LocalMapLibreViewFactory provides reject) {
                Box(Modifier.fillMaxWidth().height(320.dp)) { MapPane(emptyList(), interactiveMap = true) }
            }
        } }
        compose.onNodeWithTag("route_canvas").assertIsDisplayed()
        compose.onNodeWithTag("vector_map").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, constructed) }
    }

    @Test fun initializationFailureIsReportedInsteadOfCrashingThePage() {
        val failure = mutableStateOf<VectorMapFailure?>(null)
        val reject: (Context) -> MapView = { throw IllegalStateException("Controlled renderer initialization failure") }
        compose.setContent { RunmoniTheme {
            CompositionLocalProvider(LocalMapLibreViewFactory provides reject) {
                MapLibreMapPane(emptyList(), emptyList(), Modifier.fillMaxWidth().height(320.dp),
                    interactiveMap = true, onFailure = { failure.value = it })
            }
        } }
        compose.runOnIdle { assertEquals(VectorMapFailure.INITIALIZATION, failure.value) }
    }

    @Test fun missingAssetStyleReportsFailureFromTheActualNativeSdk() {
        val failure = mutableStateOf<VectorMapFailure?>(null)
        compose.setContent { RunmoniTheme {
            MapLibreMapPane(emptyList(), emptyList(), Modifier.fillMaxWidth().height(320.dp),
                interactiveMap = true, styleUri = "asset://runmoni-intentionally-missing-style.json",
                onFailure = { failure.value = it })
        } }
        // Readiness/failure observation budget on software GL, not a load-time SLA.
        compose.waitUntil(30_000) { failure.value != null }
        compose.runOnIdle { assertEquals(VectorMapFailure.STYLE, failure.value) }
    }

    @Test fun explicitLocationRequestRecentersTheNativeCameraAfterManualBrowsing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val style = File.createTempFile("runmoni-local-style", ".json", context.cacheDir).apply {
            writeText("""{"version":8,"sources":{},"layers":[{"id":"background","type":"background"}]}""")
        }
        val center = RouteMapCenter(37.57, 126.98)
        val request = mutableStateOf<MapRecenterRequest?>(null)
        val open = mutableStateOf(true)
        val failure = AtomicReference<VectorMapFailure?>()
        val camera = AtomicReference<RouteMapCenter?>()
        fun atFix() = camera.get()?.let { abs(it.latitude - center.latitude) < .0001 &&
            abs(it.longitude - center.longitude) < .0001 } == true
        fun awaitCamera(condition: () -> Boolean) {
            compose.waitUntil(30_000) { assertNull(failure.get()); condition() }
        }
        try {
            compose.setContent { if (open.value) RunmoniTheme {
                MapLibreMapPane(emptyList(), emptyList(), Modifier.fillMaxWidth().height(320.dp),
                    interactiveMap = true, followCenter = center, onCameraSaved = { camera.set(it) },
                    onFailure = { failure.set(it) }, styleUri = Uri.fromFile(style).toString(), recenterRequest = request.value,
                    onRecenterApplied = { if (request.value === it) request.value = null })
            } }
            awaitCamera { atFix() }
            repeat(2) {
                val pan = compose.onNodeWithTag("vector_map_viewport").fetchSemanticsNode()
                    .config[SemanticsActions.CustomActions].single { it.label == "동쪽으로 이동" }
                compose.runOnIdle { assertTrue(pan.action()) }
                awaitCamera { !atFix() }
                compose.runOnIdle { request.value = MapRecenterRequest(center) }
                awaitCamera { atFix() }
            }
        } finally {
            compose.runOnIdle { open.value = false }
            compose.waitForIdle()
            style.delete()
        }
    }

    /** Actual Compose measurement only: intentionally no MapView/GL/provider.
     * Direct pane keeps its native footer visible after controlled failure;
     * the MapPane wrapper would swap to raster and invalidate this audit. */
    @Test fun directNativeFooterFitsAt320DpAndDoubleTextWithoutConstructingGl() {
        val failure = mutableStateOf<VectorMapFailure?>(null)
        var constructed = 0
        val reject: (Context) -> MapView = {
            constructed++
            throw IllegalStateException("Controlled native footer layout fixture")
        }
        compose.setContent {
            ViewportBox(Viewport(width = 320, scale = 2f)) {
                RunmoniTheme {
                    CompositionLocalProvider(LocalMapLibreViewFactory provides reject) {
                        Box(Modifier.width(320.dp).height(360.dp).testTag("native_map_small_fixture")) {
                            MapLibreMapPane(emptyList(), emptyList(), Modifier.fillMaxSize(),
                                interactiveMap = true, onFailure = { failure.value = it })
                        }
                    }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(1, constructed)
            assertEquals(VectorMapFailure.INITIALIZATION, failure.value)
        }
        fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode()
        fun full(node: SemanticsNode) = Rect(node.positionInRoot,
            Size(node.size.width.toFloat(), node.size.height.toFloat()))
        fun inside(inner: Rect, outer: Rect, label: String) {
            val tolerance = 0.5f // physical pixel rounding, not a dp allowance
            assertTrue("$label outside fixture: $inner / $outer",
                inner.left >= outer.left - tolerance && inner.top >= outer.top - tolerance &&
                inner.right <= outer.right + tolerance && inner.bottom <= outer.bottom + tolerance)
        }
        val host = node("native_map_small_fixture")
        val viewport = node("vector_map_viewport")
        val credit = node("vector_attribution")
        val bar = node("map_control_bar")
        val controls = node("map_options")
        val location = node("map_location_unconfirmed")
        val copyright = compose.onNodeWithText("© OpenMapTiles · Data from OpenStreetMap", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode()
        val d = compose.density.density
        assertEquals("Fixture width", 320f * d, full(host).width, 0.5f)
        assertEquals("Fixture height", 360f * d, full(host).height, 0.5f)
        assertTrue("Usable native viewport must retain at least96dp", full(viewport).height + 0.5f >= 96f * d)
        assertTrue("Map control bar must retain48dp", full(bar).height + 0.5f >= 48f * d)
        assertTrue("Map menu target height", full(controls).height + 0.5f >= 48f * d)
        assertTrue("Map menu target width", full(controls).width + 0.5f >= 48f * d)
        assertTrue("Copyright target height", full(credit).height + 0.5f >= 48f * d)
        assertEquals("Large-text credit receives the full map width", full(host).width, full(credit).width, 0.5f)
        listOf(viewport, credit, bar, controls, location, copyright).forEach {
            inside(full(it), full(host), "Full semantics bounds")
            assertEquals("No clipped semantics height", full(it).height, it.boundsInRoot.height, 0.5f)
            assertEquals("No clipped semantics width", full(it).width, it.boundsInRoot.width, 0.5f)
        }
        inside(full(copyright), full(credit), "Complete copyright text")
        assertTrue("Credit occupies a separate row above controls", full(credit).bottom <= full(bar).top + 0.5f)
        assertTrue("Viewport stays above location/credit", full(viewport).bottom <= full(location).top + 0.5f)
        noTextOverflow("direct-native-map-320-font2-height360-failure-fixture")
        capture("direct-native-map-320-font2-height360-layout-fixture")
    }
}

package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class VectorMapContractTest {
    @Test fun passiveDiscoveryFailureKeepsTheLastConfirmationWithoutAWarningOrFreshTimestamp() {
        val confirmed = RunnerDiscoveryUi(loading = true, error = "previous manual error", checkedAtMillis = 1234L,
            partial = true, requiresZoom = true)
        val failed = discoveryReadFailed(confirmed, userInitiated = false)
        assertFalse(failed.loading)
        assertNull(failed.error)
        assertEquals(confirmed.checkedAtMillis, failed.checkedAtMillis)
        assertEquals(confirmed.partial, failed.partial)
        assertEquals(confirmed.requiresZoom, failed.requiresZoom)
    }

    @Test fun explicitDiscoveryFailureHasARetryDirectionWithoutServerImplementationDetails() {
        val failed = discoveryReadFailed(RunnerDiscoveryUi(checkedAtMillis = 1234L), userInitiated = true)
        assertTrue(failed.error.orEmpty().contains("다시 시도"))
        assertFalse(failed.error.orEmpty().contains("규칙"))
        assertFalse(failed.error.orEmpty().contains("색인"))
        assertEquals(1234L, failed.checkedAtMillis)
        assertFalse(failed.loading)
    }

    @Test fun native512AndRaster256SeeTheSameGeographyAtDifferentDensities() {
        for (density in listOf(1.0, 2.75)) for (zoom in listOf(4.0, 11.5, 16.0, 18.0)) {
            val nativeZoom = VectorMapCoordinates.nativeZoom(zoom)
            val converted = VectorMapCoordinates.viewport(37.5665, 126.978, nativeZoom)!!
            val native = RouteViewport(Mercator.x(126.978), Mercator.y(37.5665), nativeZoom)
            val expected = native.bounds(360 * density, 280 * density, 512 * density)!!
            val observed = converted.bounds(360 * density, 280 * density, 256 * density)!!
            assertEquals(expected.south, observed.south, 1e-10)
            assertEquals(expected.north, observed.north, 1e-10)
            assertEquals(expected.west, observed.west, 1e-10)
            assertEquals(expected.east, observed.east, 1e-10)
        }
    }

    @Test fun nativeFitKeepsBothSidesOfAnInterruptedDateLineRouteInsideThePadding() {
        val geometry = RouteGeometry.from(listOf(
            TrackPoint(0, 0.0, 179.9, 5f, 0, 0),
            TrackPoint(1, .01, -179.9, 5f, 1_000, 0),
            TrackPoint(3, .02, -179.8, 5f, 3_000, 1)))
        assertEquals(2, geometry.segments.size)
        for ((width, height) in listOf(360.0 to 240.0, 240.0 to 360.0)) {
            val fitted = RouteViewport.fit(geometry.points, width, height, 24.0)!!
            val native = fitted.copy(zoom = VectorMapCoordinates.nativeZoom(fitted.zoom))
            geometry.points.forEach { point ->
                val at = native.screen(point.x, point.y, width, height, 512.0)
                assertTrue(at.x in 23.999..width - 23.999)
                assertTrue(at.y in 23.999..height - 23.999)
            }
        }
    }

    @Test fun wrappedNativeCameraAndInvalidCameraNeverProduceFalseQueryBounds() {
        val wrapped = VectorMapCoordinates.viewport(37.5, 181.0, 14.0)!!.bounds(360.0, 280.0)!!
        assertTrue(wrapped.contains(37.5, -179.0))
        assertFalse(wrapped.contains(37.5, 0.0))
        assertNull(VectorMapCoordinates.viewport(Double.NaN, 126.0, 14.0))
        assertNull(VectorMapCoordinates.viewport(37.5, Double.POSITIVE_INFINITY, 14.0))
        assertNull(VectorMapCoordinates.viewport(91.0, 126.0, 14.0))
        assertNull(VectorMapCoordinates.viewport(37.5, 126.0, Double.NaN))
    }
}

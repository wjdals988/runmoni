package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class AppAppearanceTest {
    @Test fun absentUnknownAndLegacyLookingValuesFollowSystemWithoutWritingAccountState() {
        for (wire in listOf(null, "", "automatic", "DARK", "2", "guest", "unknown"))
            assertEquals(AppAppearance.SYSTEM, AppAppearance.fromWire(wire))
        for (mode in AppAppearance.entries) assertEquals(mode, AppAppearance.fromWire(mode.wireValue))
    }

    @Test fun explicitAppearanceOverridesBothSystemModesWhileSystemTracksIt() {
        for (system in listOf(false, true)) {
            assertEquals(system, AppAppearance.SYSTEM.isDark(system))
            assertFalse(AppAppearance.LIGHT.isDark(system))
            assertTrue(AppAppearance.DARK.isDark(system))
        }
    }

    @Test fun disabledAnimationHasNoEntranceDurationOrDeferredVisibility() {
        assertEquals(0, PageMotionPolicy.durationMillis(false))
        assertEquals(160, PageMotionPolicy.durationMillis(true))
        assertEquals(8f, PageMotionPolicy.TRAVEL_DP, 0f)
    }

    @Test fun mapTextAndRouteAreDistinctFromDayAndNightSurfaces() {
        for (dark in listOf(false, true)) {
            val palette = RunningMapPalette.forDark(dark)
            assertEquals(dark, palette.dark)
            for (background in listOf(palette.ground, palette.road, palette.water, palette.park)) {
                assertTrue("map ink dark=$dark contrast=${contrast(palette.ink, background)}", contrast(palette.ink, background) >= 4.5)
                assertTrue("map route halo dark=$dark", contrast(palette.route, palette.halo) >= 3.0)
            }
            assertTrue("caption ink against halo", contrast(palette.ink, palette.halo) >= 4.5)
        }
    }

    private fun contrast(a: Int, b: Int): Double {
        fun luminance(argb: Int): Double {
            fun channel(shift: Int): Double {
                val value = ((argb ushr shift) and 255) / 255.0
                return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
            }
            return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
        }
        val light = max(luminance(a), luminance(b))
        val dark = min(luminance(a), luminance(b))
        return (light + .05) / (dark + .05)
    }
}

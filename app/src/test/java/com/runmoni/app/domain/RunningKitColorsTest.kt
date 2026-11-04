package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RunningKitColorsTest {
    @Test fun eachRegionCanChangeAndReturnToOriginalWithoutChangingTheOtherRegions() {
        val selected = RunningKitColors(1, 2, 3)
        for (region in RunningKitRegion.entries) for (color in 0 until RunningKitColors.COLOR_COUNT) {
            val updated = selected.withColor(region, color)
            assertEquals(color, updated.color(region))
            for (other in RunningKitRegion.entries.filterNot { it == region }) {
                assertEquals(selected.color(other), updated.color(other))
            }
            assertEquals(0, updated.withColor(region, 0).color(region))
        }
        assertEquals(RunningKitColors(1, 2, 3), selected)
        assertTrue(RunningKitColors().isOriginal)
        assertFalse(selected.isOriginal)
    }

    @Test fun invalidDecodedValuesNormalizeIndividuallyButInvalidSelectionsAreRejected() {
        val invalid = RunningKitColors(-1, 4, Int.MAX_VALUE)
        assertEquals(RunningKitColors(0, 4, 0), invalid.normalized())
        assertEquals(-1, invalid.top)
        assertEquals(Int.MAX_VALUE, invalid.shoes)
        assertTrue(RunningKitColors(-1, 6, Int.MAX_VALUE).isOriginal)
        for (region in RunningKitRegion.entries) for (color in listOf(-1, RunningKitColors.COLOR_COUNT, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { RunningKitColors().withColor(region, color) }
        }
    }

    @Test fun equipmentAndGenderChangesKeepFabricSettingsSeparateFromInventory() {
        val colors = RunningKitColors(5, 2, 3)
        val style = AvatarStyle(gender = AvatarGender.FEMALE, runningKitColors = colors)
        for (slot in AvatarCatalogue.slots) for (variant in slot.variants.indices) {
            assertEquals(colors, style.withItem(slot.key, variant).runningKitColors)
        }
        val costume = style.withItem("costume", 1)
        assertEquals(colors, costume.copy(gender = AvatarGender.MALE).runningKitColors)
        assertEquals(colors, costume.withItem("top", 0).runningKitColors)
        assertEquals(13, AvatarCatalogue.slots.size)
        assertEquals(9, AvatarCatalogue.tabs.size)
        assertEquals(58, AvatarCatalogue.slots.sumOf { it.variants.size })
        assertFalse(AvatarCatalogue.valid("runningKitColors", 0))
    }
}

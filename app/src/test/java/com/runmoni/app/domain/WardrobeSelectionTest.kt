package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class WardrobeSelectionTest {
    private val style = AvatarStyle(gender = AvatarGender.FEMALE, top = 1, bottom = 1,
        shoes = 1, wrist = 2, eyewear = 3, runningKitColors = RunningKitColors(4, 2, 3))

    @Test fun changingAnItemResetsOnlyItsOwnPaletteAndPreservesTheRestOfTheAvatar() {
        for ((slot, variant, colors) in listOf(Triple("top", 2, RunningKitColors(0, 2, 3)),
            Triple("bottom", 0, RunningKitColors(4, 0, 3)), Triple("shoes", 3, RunningKitColors(4, 2, 0)))) {
            assertEquals(style.withItem(slot, variant).copy(runningKitColors = colors), style.withSelectedItem(slot, variant))
        }
        assertEquals(RunningKitColors(4, 2, 3), style.runningKitColors)
    }

    @Test fun tappingTheCurrentItemKeepsItsTintButLeavingACoveringCostumeStartsInTheItemPalette() {
        for (slot in listOf("top", "bottom", "shoes")) assertEquals(style, style.withSelectedItem(slot, 1))
        val costume = style.copy(costume = 1)
        val normal = costume.withSelectedItem("top", 1)
        assertEquals(0, normal.costume)
        assertEquals(style.copy(runningKitColors = RunningKitColors(0, 2, 3)), normal)
        assertEquals(style.runningKitColors, costume.withItem("top", 1).runningKitColors)
    }

    @Test fun equipmentAndGenderKeepAllFabricColorsAndPureSavedItemUpdatesRemainCompatible() {
        for (slot in AvatarCatalogue.accessoryKeys + "headwear")
            assertEquals(style.runningKitColors, style.withSelectedItem(slot, 0).runningKitColors)
        assertEquals(style.runningKitColors, style.copy(gender = AvatarGender.MALE).runningKitColors)
        assertEquals(style.runningKitColors, style.withItem("shoes", 3).runningKitColors)
    }
}

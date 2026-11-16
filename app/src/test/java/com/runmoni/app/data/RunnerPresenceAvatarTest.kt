package com.runmoni.app.data

import com.runmoni.app.domain.AvatarCatalogue
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.RunningKitColors
import com.runmoni.app.domain.variantFor
import org.junit.Assert.*
import org.junit.Test

/** Firestore-shaped maps only. No SDK client, identity, recorder or public writes. */
class RunnerPresenceAvatarTest {
    private fun wireAvatar(): Map<String, Any> = buildMap {
        AvatarCatalogue.slots.forEach { put(it.key, 0L) }
        put("gender", "female")
        put("colors", mapOf("top" to 1L, "bottom" to 2L, "shoes" to 3L))
    }

    @Test fun everyCatalogueIdIncludingTheThreeAppendedItemsDecodesWithoutChangingItsSlot() {
        assertEquals(61, AvatarCatalogue.slots.sumOf { it.variants.size })
        for (slot in AvatarCatalogue.slots) for (id in slot.variants.indices) {
            val decoded = RunnerPresenceRepository.decodeAvatar(wireAvatar() + (slot.key to id.toLong()))
            assertNotNull("${slot.key}:$id", decoded)
            for (other in AvatarCatalogue.slots)
                assertEquals("${slot.key}:$id changed ${other.key}", if (other.key == slot.key) id else 0,
                    decoded!!.variantFor(other.key))
        }
    }

    @Test fun newJerseySocksAndShoesKeepIndependentPaletteAndGender() {
        val decoded = RunnerPresenceRepository.decodeAvatar(wireAvatar() + mapOf("top" to 6L, "socks" to 4L, "shoes" to 4L))!!
        assertEquals(6, decoded.top)
        assertEquals(4, decoded.socks)
        assertEquals(4, decoded.shoes)
        assertEquals(AvatarGender.FEMALE, decoded.gender)
        assertEquals(RunningKitColors(1, 2, 3), decoded.runningKitColors)
    }

    @Test fun overflowingNegativeFractionalOrNonFirestoreIntegersCannotBecomeValidIds() {
        for (slot in AvatarCatalogue.slots) for (value in listOf<Any>(-1L, Long.MIN_VALUE, Long.MAX_VALUE,
            Int.MAX_VALUE.toLong() + 1L, 0x1_0000_0000L, 0x1_0000_0006L,
            slot.variants.size.toLong(), 1.0, 1, "1")) {
            assertNull("${slot.key}:$value", RunnerPresenceRepository.decodeAvatar(wireAvatar() + (slot.key to value)))
        }
    }

    @Test fun strictFieldAndPaletteSchemaStillRejectsUnknownMissingAndInvalidValues() {
        val base = wireAvatar()
        for (key in base.keys) assertNull("missing $key", RunnerPresenceRepository.decodeAvatar(base - key))
        assertNull(RunnerPresenceRepository.decodeAvatar(base + ("points" to 100L)))
        assertNull(RunnerPresenceRepository.decodeAvatar(base + ("gender" to "unknown")))
        for (bad in listOf<Map<String, Any>>(mapOf("top" to 6L, "bottom" to 0L, "shoes" to 0L),
            mapOf("top" to 0x1_0000_0001L, "bottom" to 0L, "shoes" to 0L),
            mapOf("top" to 1.0, "bottom" to 0L, "shoes" to 0L),
            mapOf("top" to 0L, "bottom" to 0L),
            mapOf("top" to 0L, "bottom" to 0L, "shoes" to 0L, "skin" to 1L)))
            assertNull(RunnerPresenceRepository.decodeAvatar(base + ("colors" to bad)))
    }
}

package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class AvatarBasePolicyTest {
    @Test fun oldAndInvalidBaseValuesKeepTheOriginalMaleDefault() {
        assertEquals(AvatarGender.MALE, AvatarStyle(hair = 2, top = 4).gender)
        for (value in listOf(null, "", "unknown", "2", "null")) {
            assertEquals(AvatarGender.MALE, AvatarGender.fromWire(value))
        }
        assertEquals(AvatarGender.FEMALE, AvatarGender.fromWire("female"))
        assertEquals(AvatarGender.FEMALE, AvatarGender.fromWire("FEMALE"))
        assertEquals("female", AvatarGender.FEMALE.wireValue)
    }

    @Test fun changingEquipmentAndCostumeDoesNotChangeTheSelectedBase() {
        val female = AvatarStyle(gender = AvatarGender.FEMALE)
        for (slot in AvatarCatalogue.slots) for (variant in slot.variants.indices) {
            assertEquals(AvatarGender.FEMALE, female.withItem(slot.key, variant).gender)
        }
        val costume = female.withItem("costume", 1).withItem("wrist", 1)
        assertEquals(AvatarGender.FEMALE, costume.withItem("top", 0).gender)
        assertEquals(1, costume.withItem("top", 0).wrist)
        assertFalse(AvatarCatalogue.valid("gender", 0))
    }

    @Test fun everyExistingItemIsFreeWhileSavedIdsAndOwnershipStaySeparate() {
        assertTrue(AvatarCatalogue.TEST_ITEMS_FREE)
        assertEquals(13, AvatarCatalogue.slots.size)
        assertEquals(9, AvatarCatalogue.tabs.size)
        assertEquals(58, AvatarCatalogue.slots.sumOf { it.variants.size })
        AvatarCatalogue.slots.forEach { slot ->
            slot.variants.indices.forEach { assertEquals("${slot.key}:$it", 0, AvatarCatalogue.price(it)) }
        }
        val existing = AvatarCatalogue.migrateOwned(setOf("top:2", "accessory:2"))
        assertTrue(existing.containsAll(listOf("top:2", "accessory:2", "headwear:1")))
        assertFalse("Free access must not erase the distinction between saved and unclaimed items", "hydration:1" in existing)
    }
}

package com.runmoni.app.domain

import com.runmoni.app.avatar.AvatarRasterizer as Art
import org.junit.Assert.*
import org.junit.Test

class AvatarArtTest {
    private fun pixels(a: AvatarStyle, f: Int = 0, running: Boolean = true) = with(a) {
        Art.render(hair, face, top, bottom, socks, shoes, variantFor("headwear"), variantFor("ear"), eyewear, wrist, waist, hydration, costume, f, running)
    }

    @Test fun neckBridgeRemainsSolidAcrossHairTopsCostumesAndAllEightFrames() {
        for (hair in 0..3) for (top in 0..5) for (costume in 0..5) for (frame in 0..7) {
            val p = pixels(AvatarStyle(hair = hair, top = top, costume = costume), frame)
            val bob = Art.bobForFrame(frame, true)
            for (y in 46..58) assertTrue("neck gap hair=$hair top=$top costume=$costume frame=$frame y=$y", p[(y + bob) * Art.WIDTH + 48] ushr 24 != 0)
            // Face and torso must be joined by actual opaque pixels, not just nearby bounds.
            assertConnected(p, 50, 40 + bob, 48, 65 + bob)
        }
    }

    private fun assertConnected(p: IntArray, x0: Int, y0: Int, x1: Int, y1: Int) {
        val seen = BooleanArray(p.size); val queue = IntArray(p.size)
        var read = 0; var end = 1; queue[0] = y0 * Art.WIDTH + x0; seen[queue[0]] = true
        assertTrue(p[queue[0]] ushr 24 != 0)
        val goal = y1 * Art.WIDTH + x1
        while (read < end) {
            val i = queue[read++]; if (i == goal) return
            for (n in intArrayOf(i - Art.WIDTH, i + Art.WIDTH, if (i % Art.WIDTH > 0) i - 1 else -1, if (i % Art.WIDTH < Art.WIDTH - 1) i + 1 else -1)) {
                if (n in p.indices && !seen[n] && p[n] ushr 24 != 0) { seen[n] = true; queue[end++] = n }
            }
        }
        fail("Face is detached from torso")
    }

    @Test fun changingHairDoesNotReplaceTheFace() {
        val base = pixels(AvatarStyle(), running = false)
        for (hair in 1..3) {
            val changed = pixels(AvatarStyle(hair = hair), running = false)
            for (y in 33..44) for (x in 40..60) assertEquals("Face changed with hair=$hair", base[y * Art.WIDTH + x], changed[y * Art.WIDTH + x])
        }
    }

    @Test fun allEightFramesDifferAndTimingWrapsWithoutNegativeIndices() {
        assertEquals(8, (0..7).map { f -> pixels(AvatarStyle(hair = 2, top = 4, bottom = 4, socks = 2), f).contentHashCode() }.toSet().size)
        assertEquals(0, Art.frameAt(-1)); assertEquals(7, Art.frameAt(719)); assertEquals(0, Art.frameAt(720))
        assertEquals(0, Art.frameAt(0)); assertEquals(1, Art.frameAt(90))
    }

    @Test fun independentSlotsAndSpecialCostumeOccupancyRules() {
        val a = AvatarStyle(hair = 2, top = 4, bottom = 4, shoes = 1, headwear = 1, ear = 1, eyewear = 1, wrist = 1, waist = 1, hydration = 1, socks = 2)
        for (slot in AvatarCatalogue.slots) for (v in slot.variants.indices) {
            val b = a.withItem(slot.key, v)
            AvatarCatalogue.slots.filter { it.key != slot.key }.forEach { assertEquals("Changed unrelated ${it.key}", a.variantFor(it.key), b.variantFor(it.key)) }
        }
        val santa = a.withItem("costume", 1)
        assertEquals(0, santa.withItem("top", 1).costume)
        assertEquals(0, santa.withItem("headwear", 2).costume)
        assertEquals(1, santa.withItem("wrist", 2).costume)
        assertEquals(a, santa.withItem("costume", 0))
        assertEquals(3, a.withItem("costume", 3).withItem("shoes", 2).costume)
        assertEquals(0, a.withItem("costume", 3).withItem("bottom", 0).costume)
    }

    @Test fun legacyHeadItemsAndOwnershipArePreserved() {
        assertEquals(3, AvatarStyle(accessory = 1).variantFor("headwear"))
        assertEquals(1, AvatarStyle(accessory = 2).variantFor("headwear"))
        assertEquals(1, AvatarStyle(accessory = 3).variantFor("ear"))
        val migrated = AvatarCatalogue.migrateOwned(setOf("top:2", "accessory:1", "accessory:2", "accessory:3"))
        assertTrue(migrated.containsAll(listOf("top:2", "headwear:3", "headwear:1", "ear:1", "socks:0", "costume:0")))
        assertFalse("Do not grant paid gear", "hydration:1" in migrated)
    }

    @Test fun everyVisibleSlotChangesPixelsAndNineTabsHideInternalEquipmentSlots() {
        val base = AvatarStyle()
        val plain = pixels(base)
        AvatarCatalogue.slots.forEach { slot -> assertFalse("Invisible slot ${slot.key}", plain.contentEquals(pixels(base.withItem(slot.key, 1)))) }
        assertEquals(9, AvatarCatalogue.tabs.size)
        assertEquals(5, AvatarCatalogue.accessoryKeys.size)
        assertFalse(AvatarCatalogue.valid("top", 6)); assertFalse(AvatarCatalogue.valid("unknown", 0))
    }
}

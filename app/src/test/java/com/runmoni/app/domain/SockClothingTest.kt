package com.runmoni.app.domain

import com.runmoni.app.avatar.SockClothing
import org.junit.Assert.*
import org.junit.Test

class SockClothingTest {
    private fun label(flags: Int, shade: Int = 150, detail: Int = 0) =
        (255 shl 24) or (flags shl 16) or (shade shl 8) or detail

    @Test fun clothingChangesOnlyTheChosenMaskAndPreservesSourceAlphaAndInputs() {
        val source = intArrayOf(0xfea37631.toInt(), 0xfd305064.toInt(), 0xfe1a253c.toInt(), 0x00244668)
        val original = source.clone()
        val mask = intArrayOf(label(3), label(2), 0, label(3))
        val beforeMask = mask.clone()
        val crew = SockClothing.render(source, mask, 1)
        val knee = SockClothing.render(source, mask, 2)
        assertNotEquals(source[0], crew[0])
        assertEquals(source[1], crew[1])
        assertNotEquals(source[1], knee[1])
        assertEquals(source[2], knee[2])
        assertEquals(source[3], crew[3])
        for (i in source.indices) {
            assertEquals(source[i] ushr 24, crew[i] ushr 24)
            assertEquals(source[i] ushr 24, knee[i] ushr 24)
        }
        assertArrayEquals(original, source)
        assertArrayEquals(beforeMask, mask)
        assertArrayEquals(original, SockClothing.render(source, mask, 0))
    }

    @Test fun authoredShadeAndCuffDetailRemainVisibleWithoutQuantizedFlatBlocks() {
        val source = IntArray(256) { 0xfe895a32.toInt() }
        val shaded = SockClothing.render(source, IntArray(256) { label(3, it) }, 1)
        assertTrue(shaded.toSet().size > 100)
        val cuff = SockClothing.render(source, IntArray(256) { label(3, it, 1) }, 1)
        val rib = SockClothing.render(source, IntArray(256) { label(3, it, 4) }, 1)
        assertFalse(shaded.contentEquals(cuff))
        assertFalse(shaded.contentEquals(rib))
    }

    @Test fun invalidOrUntrustedLabelsFailClosedInsteadOfRecoloringOtherPixels() {
        for (invalid in intArrayOf(1, label(4), label(1, detail = 8), 0x00030000)) {
            assertThrows(IllegalArgumentException::class.java) {
                SockClothing.render(intArrayOf(-1), intArrayOf(invalid), 1)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { SockClothing.render(intArrayOf(-1), intArrayOf(), 1) }
        assertThrows(IllegalArgumentException::class.java) { SockClothing.render(intArrayOf(-1), intArrayOf(0), 3) }
    }
}

package com.runmoni.app.data

import org.junit.Assert.*
import org.junit.Test

class TileHttpCachePolicyTest {
    @Test fun absentFreshnessUsesAtLeastSevenDays() {
        val p = TileHttpCachePolicy.from(1000, null, null, null)
        assertTrue(p.fresh(1000 + 6L * 24 * 3600 * 1000))
        assertFalse(p.fresh(1000 + 7L * 24 * 3600 * 1000))
    }
    @Test fun serverMaxAgeAndAgeOverrideFallback() {
        val p = TileHttpCachePolicy.from(1000, "public, max-age=3600", null, null, 600)
        assertEquals(3_001_000L, p.validUntil)
        assertFalse(p.fresh(3_001_000))
    }
    @Test fun noCacheMustValidateAndNoStoreCannotPersist() {
        val validate = TileHttpCachePolicy.from(1000, "no-cache, max-age=604800", null, null)
        assertFalse(validate.fresh(1001)); assertFalse(validate.mayUseStaleOnError); assertTrue(validate.store)
        val noStore = TileHttpCachePolicy.from(1000, "no-store", null, null)
        assertFalse(noStore.store); assertFalse(noStore.fresh(1001)); assertFalse(noStore.mayUseStaleOnError)
    }
    @Test fun mustRevalidateDoesNotShowAnUnvalidatedExpiredTileOffline() {
        val p = TileHttpCachePolicy.from(1000, "max-age=1, must-revalidate", null, null)
        assertTrue(p.fresh(1001)); assertFalse(p.fresh(2001)); assertFalse(p.mayUseStaleOnError)
    }
    @Test fun expiresAndDateDetermineFreshness() {
        val p = TileHttpCachePolicy.from(1000, null, "Tue, 10 Nov 2026 02:00:00 GMT", "Tue, 10 Nov 2026 01:00:00 GMT")
        assertEquals(3_601_000L, p.validUntil)
    }
    @Test fun oldDateCannotExtendAnAlreadyAgedServerResponse() {
        val p = TileHttpCachePolicy.from(3_601_000, "max-age=3600", null, "Thu, 01 Jan 1970 00:00:01 GMT")
        assertFalse(p.fresh(3_601_000))
    }
}

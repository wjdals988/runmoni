package com.runmoni.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runmoni.app.domain.CommunityProfileReceipt
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Local UUID preferences only; no Auth, Firestore, user profile or wallet writes. */
@RunWith(AndroidJUnit4::class)
class CommunityProfileReceiptStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "profile-receipt-test-${UUID.randomUUID()}"
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val pendingKey = "pendingAnonymousConnectV1:test-app"
    private val receipt = CommunityProfileReceipt("verified-owner", "기존 러너", "기존 러너")
    private fun store() = CommunityProfileReceiptStore(preferences, "test-app")

    @After fun deleteOnlyThisTestsPrivatePreferences() { context.deleteSharedPreferences(name) }

    @Test fun durableServerAckAndPendingRemovalShareOnePreferencesCommit() {
        assertTrue(preferences.edit().putBoolean(pendingKey, true).commit())
        assertNull(store().read())
        assertTrue(store().write(receipt, pendingKey))
        assertFalse(preferences.contains(pendingKey))
        assertEquals(receipt, store().read())
        assertEquals(receipt, CommunityProfileReceiptStore(preferences, "test-app").read())
        assertNull(CommunityProfileReceiptStore(preferences, "different-app").read())
    }

    @Test fun corruptAndUnknownReceiptsCannotRestoreAConnection() {
        val store = store()
        for (bad in listOf("broken", "{}", "{\"version\":2,\"uid\":\"verified-owner\"}", "{\"version\":1,\"uid\":\"\"}")) {
            assertTrue(preferences.edit().putString(store.key, bad).commit())
            assertNull(store.read())
        }
    }

    @Test fun clearingReceiptLeavesOtherAccountAndLocalSettingsUntouched() {
        assertTrue(preferences.edit().putInt("existingBalanceSentinel", 321).putString("otherAccountSentinel", "unchanged").commit())
        assertTrue(store().write(receipt, pendingKey))
        assertTrue(store().clear())
        assertNull(store().read())
        assertEquals(321, preferences.getInt("existingBalanceSentinel", 0))
        assertEquals("unchanged", preferences.getString("otherAccountSentinel", ""))
    }
}

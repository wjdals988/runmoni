package com.runmoni.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Only nonce-owned local settings. Never activates a recorder or publishes a location. */
@RunWith(AndroidJUnit4::class)
class RunnerSharingPreferenceStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "sharing-choice-test-${UUID.randomUUID()}"
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    @After fun cleanup() { context.deleteSharedPreferences(name) }

    @Test fun remembersOnlyTheConsentingAccountAcrossStoreRecreation() {
        assertTrue(RunnerSharingPreferenceStore(preferences).accept("owner", true))
        val restored = RunnerSharingPreferenceStore(preferences)
        assertTrue(restored.enabledFor("owner"))
        assertFalse(restored.enabledFor("different-account"))
        assertFalse(restored.enabledFor(""))
    }

    @Test fun missingConsentCannotCreateOrReplaceAChoice() {
        val store = RunnerSharingPreferenceStore(preferences)
        for ((uid, adult) in listOf("owner" to false, "" to true)) {
            assertThrows(IllegalArgumentException::class.java) { store.accept(uid, adult) }
            assertEquals("", store.preferredUid())
        }
    }

    @Test fun turningOffRemovesSavedConsentAndPreservesUnrelatedSettings() {
        preferences.edit().putBoolean("voiceGuidance", true).commit()
        val store = RunnerSharingPreferenceStore(preferences)
        assertTrue(store.accept("owner", true))
        assertTrue(store.clear())
        assertFalse(RunnerSharingPreferenceStore(preferences).enabledFor("owner"))
        assertTrue(preferences.getBoolean("voiceGuidance", false))
    }

    /** A controlled failed acknowledgement, not a claim that the device disk failed. */
    private fun failedCommitPreferences(ignoreRemovals: Boolean = false): SharedPreferences = object : SharedPreferences by preferences {
        override fun edit(): SharedPreferences.Editor {
            val target = preferences.edit()
            return object : SharedPreferences.Editor by target {
                override fun putString(key: String, value: String?): SharedPreferences.Editor { target.putString(key, value); return this }
                override fun putInt(key: String, value: Int): SharedPreferences.Editor { target.putInt(key, value); return this }
                override fun remove(key: String): SharedPreferences.Editor { if (!ignoreRemovals) target.remove(key); return this }
                override fun commit(): Boolean { target.commit(); return false }
                override fun apply() { throw AssertionError("Durable consent must await commit acknowledgement") }
            }
        }
    }

    @Test fun failedAcceptanceDoesNotAuthorizeAnObservableInMemoryChoice() {
        val store = RunnerSharingPreferenceStore(failedCommitPreferences())
        assertFalse(store.accept("owner", true))
        assertTrue("The fake exposes the write before denying its acknowledgement",
            RunnerSharingPreferenceStore(preferences).enabledFor("owner"))
        assertEquals("", store.preferredUid())
        assertFalse(store.enabledFor("owner"))
    }

    @Test fun failedClearIsReportedAndSuppressesTheRetainedChoiceInThisProcess() {
        preferences.edit().putBoolean("voiceGuidance", true).commit()
        assertTrue(RunnerSharingPreferenceStore(preferences).accept("owner", true))
        val store = RunnerSharingPreferenceStore(failedCommitPreferences(ignoreRemovals = true))
        assertFalse(store.clear())
        assertTrue("The controlled failure leaves the old backing choice available",
            RunnerSharingPreferenceStore(preferences).enabledFor("owner"))
        assertFalse(store.enabledFor("owner"))
        assertEquals("", store.preferredUid())
        assertTrue(preferences.getBoolean("voiceGuidance", false))
    }

    @Test fun suppressionDoesNotWaitForAnOlderCommitOrReopenAfterItsLateAcknowledgement() {
        preferences.edit().putBoolean("voiceGuidance", true).commit()
        assertTrue(RunnerSharingPreferenceStore(preferences).accept("previous", true))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val heldPreferences = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val target = preferences.edit()
                return object : SharedPreferences.Editor by target {
                    override fun putString(key: String, value: String?): SharedPreferences.Editor { target.putString(key, value); return this }
                    override fun putInt(key: String, value: Int): SharedPreferences.Editor { target.putInt(key, value); return this }
                    override fun commit(): Boolean {
                        entered.countDown()
                        check(release.await(30, TimeUnit.SECONDS)) { "Held test commit was not released" }
                        return target.commit()
                    }
                }
            }
        }
        val store = RunnerSharingPreferenceStore(heldPreferences)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val oldAccept = workers.submit<Boolean> { store.accept("owner", true) }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val suppressedRead = workers.submit<String> {
                store.suppressAutomaticSharing()
                store.preferredUid()
            }
            // The writer is still held. Completion here proves this local gate
            // and read do not need its lock; no millisecond latency is claimed.
            assertEquals("", suppressedRead.get(10, TimeUnit.SECONDS))
            assertEquals(1L, release.count)
            release.countDown()
            assertFalse(oldAccept.get(10, TimeUnit.SECONDS))
            // Suppression alone intentionally does not delete persistent data.
            // The late successful backing write cannot reopen this Store's gate.
            assertTrue(RunnerSharingPreferenceStore(preferences).enabledFor("owner"))
            assertEquals("", store.preferredUid())
            assertTrue(preferences.getBoolean("voiceGuidance", false))
            assertTrue(store.accept("owner", true))
            assertEquals("owner", store.preferredUid())
        } finally {
            release.countDown()
            workers.shutdownNow()
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
}

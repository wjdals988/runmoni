package com.runmoni.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalWalletDurabilityTest {
    @Test fun failedMemoryFirstCommitRemainsPendingAcrossWalletInstancesUntilDiskConfirms() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val delegate = context.getSharedPreferences("wallet-fault-delegate", Context.MODE_PRIVATE)
        val disk = DiskSnapshot()
        val preferences = MemoryFirstPreferences(delegate, disk)
        val runId = "durability-${UUID.randomUUID()}"
        preferences.failCommit = true
        try { LocalWallet(preferences).credit(runId, 123); fail("first commit must fail") } catch (_: IllegalStateException) { }
        assertEquals(123, LocalWallet(preferences).read().mileage)
        assertEquals("failed memory state must not masquerade as disk state", 0, LocalWallet(MemoryFirstPreferences(delegate, disk)).read().mileage)
        try { LocalWallet(preferences).credit(runId, 123); fail("another instance must retry the failed commit") } catch (_: IllegalStateException) { }
        assertEquals(2, preferences.commitAttempts)
        assertEquals(123, LocalWallet(preferences).read().mileage)
        preferences.failCommit = false
        LocalWallet(preferences).credit(runId, 123)
        assertEquals(3, preferences.commitAttempts)
        val reopened = MemoryFirstPreferences(delegate, disk)
        assertEquals(123, LocalWallet(reopened).read().mileage)
        assertEquals(123, LocalWallet(reopened).credit(runId, 123).mileage)
        assertEquals("durable replay does not rewrite every historical id", 0, reopened.commitAttempts)
    }

    private class DiskSnapshot(var values: Map<String, Any?> = emptyMap())

    /** Models Android's memory-before-disk ordering rather than only throwing before credit. */
    private class MemoryFirstPreferences(
        private val delegate: SharedPreferences,
        private val disk: DiskSnapshot,
    ) : SharedPreferences by delegate {
        private val memory = disk.values.toMutableMap()
        var failCommit = false
        var commitAttempts = 0

        override fun getString(key: String?, defValue: String?): String? = memory[key] as? String ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return (memory[key] as? Set<String>)?.toMutableSet() ?: defValues
        }

        override fun edit(): SharedPreferences.Editor {
            val changes = mutableMapOf<String, Any?>()
            return object : SharedPreferences.Editor by delegate.edit() {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                    changes[requireNotNull(key)] = value
                    return this
                }

                override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                    changes[requireNotNull(key)] = values?.toSet()
                    return this
                }

                override fun commit(): Boolean {
                    commitAttempts++
                    memory.putAll(changes)
                    if (failCommit) return false
                    disk.values = memory.toMap()
                    return true
                }
            }
        }
    }
}

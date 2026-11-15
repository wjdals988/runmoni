package com.runmoni.app.data

import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicReference

/** A durable, UID-bound choice; it is never evidence of a live public location. */
class RunnerSharingPreferenceStore(private val preferences: SharedPreferences) {
    private class Gate(val suppressed: Boolean, val acceptedUid: String? = null)
    private val gate = AtomicReference(Gate(suppressed = false))
    private val writeLock = Any()

    fun preferredUid(): String {
        val before = gate.get()
        if (before.suppressed) return ""
        val saved = preferences.getString(KEY_UID, "").orEmpty()
            .takeIf { preferences.getInt(KEY_VERSION, 0) == CONSENT_VERSION }.orEmpty()
        // A read begun before suppression cannot authorize the old choice after
        // it. A newer accepted operation also cannot expose another UID's write.
        return saved.takeIf { gate.get() === before && (before.acceptedUid == null || before.acceptedUid == it) }.orEmpty()
    }

    fun enabledFor(uid: String): Boolean = uid.isNotBlank() && preferredUid() == uid

    /** No disk access or writer lock: callers suppress before stopping sharing. */
    fun suppressAutomaticSharing() { gate.set(Gate(suppressed = true)) }

    fun accept(uid: String, adultConfirmed: Boolean): Boolean {
        require(uid.isNotBlank() && adultConfirmed)
        val operation = Gate(suppressed = true)
        gate.set(operation)
        return synchronized(writeLock) {
            if (gate.get() !== operation) return@synchronized false
            val saved = try { preferences.edit().putString(KEY_UID, uid).putInt(KEY_VERSION, CONSENT_VERSION).commit() }
                catch (_: Exception) { false }
            // A delayed ACK must never undo an off/logout/backend suppression.
            saved && gate.compareAndSet(operation, Gate(suppressed = false, acceptedUid = uid))
        }
    }

    fun clear(): Boolean {
        val operation = Gate(suppressed = true)
        gate.set(operation)
        return synchronized(writeLock) {
            if (gate.get() !== operation) return@synchronized false
            // commit can fail after updating the in-memory map. The memory gate
            // remains suppressed until a new explicit choice is acknowledged.
            try { preferences.edit().remove(KEY_UID).remove(KEY_VERSION).commit() }
                catch (_: Exception) { false }
        }
    }

    companion object {
        const val NEXT_RUN = "next-running-session"
        private const val KEY_UID = "runnerSharingPreferredUidV1"
        private const val KEY_VERSION = "runnerSharingConsentVersion"
        private const val CONSENT_VERSION = 1
    }
}

package com.runmoni.app.data

import kotlinx.coroutines.sync.Mutex

internal class GoogleCredentialPickerBusyException : Exception()

/** A second tap must fail immediately, never queue a new picker after the first one closes. */
internal class GooglePickerGate {
    private val mutex = Mutex()

    suspend fun <T> select(block: suspend () -> T): T {
        if (!mutex.tryLock()) throw GoogleCredentialPickerBusyException()
        return try { block() } finally { mutex.unlock() }
    }
}

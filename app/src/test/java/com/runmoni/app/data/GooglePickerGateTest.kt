package com.runmoni.app.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GooglePickerGateTest {
    @Test fun aSecondPickerIsRejectedImmediatelyAndNeverQueued() = runBlocking {
        val gate = GooglePickerGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var secondOpened = false
        val first = launch { gate.select { entered.complete(Unit); release.await() } }
        entered.await()
        try {
            gate.select { secondOpened = true }
            fail("Concurrent picker must not wait or open")
        } catch (_: GoogleCredentialPickerBusyException) { }
        assertFalse(secondOpened)
        release.complete(Unit)
        first.join()
        assertFalse(secondOpened)
        assertEquals("next explicit tap", gate.select { "next explicit tap" })
    }

    @Test fun cancellationReleasesTheGateForANewExplicitAttempt() = runBlocking {
        val gate = GooglePickerGate()
        try {
            gate.select<Unit> { throw CancellationException("cancelled") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertTrue(gate.select { true })
    }

    @Test fun providerFailureAlsoReleasesTheGate() = runBlocking {
        val gate = GooglePickerGate()
        val failure = IllegalStateException("provider failure")
        try {
            gate.select<Unit> { throw failure }
            fail("Failure must propagate")
        } catch (actual: IllegalStateException) { assertSame(failure, actual) }
        assertTrue(gate.select { true })
    }
}

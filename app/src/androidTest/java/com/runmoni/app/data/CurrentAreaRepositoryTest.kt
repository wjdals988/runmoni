package com.runmoni.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.runmoni.app.domain.WeatherLocation
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Android context + injected lookup only. Never contacts the platform geocoder or requests GPS/network. */
class CurrentAreaRepositoryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val firstCell = WeatherLocation(3760, 12702)
    private val secondCell = WeatherLocation(3761, 12702)
    private val missingCell = WeatherLocation(3762, 12702)

    @Test fun memoryCacheSeparatesLocationsAndHonorsPositiveAndNegativeExpiryBoundaries() = runBlocking {
        val time = AtomicLong(1_000)
        val calls = ConcurrentHashMap<WeatherLocation, AtomicInteger>()
        val repository = CurrentAreaRepository(context, lookup = { cell ->
            val count = calls.computeIfAbsent(cell) { AtomicInteger() }.incrementAndGet()
            when (cell) {
                firstCell -> if (count == 1) "합성 첫 지역" else "합성 첫 지역 갱신"
                secondCell -> if (count == 1) "합성 둘째 지역" else "합성 둘째 지역 갱신"
                else -> if (count == 1) null else "합성 복구 지역"
            }
        }, elapsedMillis = time::get)
        assertEquals("합성 첫 지역", repository.resolve(firstCell))
        time.set(2_000)
        assertEquals("합성 둘째 지역", repository.resolve(secondCell))
        time.set(3_000)
        assertNull(repository.resolve(missingCell))
        assertEquals("합성 첫 지역", repository.resolve(firstCell))
        assertEquals(1, calls.getValue(firstCell).get())
        time.set(3_000 + 60_000L - 1)
        assertNull(repository.resolve(missingCell))
        assertEquals(1, calls.getValue(missingCell).get())
        time.incrementAndGet()
        assertEquals("합성 복구 지역", repository.resolve(missingCell))
        assertEquals(2, calls.getValue(missingCell).get())
        time.set(1_000 + 30 * 60_000L - 1)
        assertEquals("합성 첫 지역", repository.resolve(firstCell))
        assertEquals(1, calls.getValue(firstCell).get())
        time.incrementAndGet()
        assertEquals("합성 첫 지역 갱신", repository.resolve(firstCell))
        assertEquals(2, calls.getValue(firstCell).get())
        assertEquals("합성 둘째 지역", repository.resolve(secondCell))
        assertEquals(1, calls.getValue(secondCell).get())
        time.set(2_000 + 30 * 60_000L)
        assertEquals("합성 둘째 지역 갱신", repository.resolve(secondCell))
        assertEquals(2, calls.getValue(secondCell).get())
    }

    @Test fun unavailableMalformedAndTimedOutLookupsDoNotBecomeInventedNames() = runBlocking {
        val time = AtomicLong(1_000)
        val calls = AtomicInteger()
        val answers = listOf("지역\n오류", "가".repeat(113), "  ", "  합성   복구 지역  ")
        val repository = CurrentAreaRepository(context, lookup = { answers[calls.getAndIncrement()] }, elapsedMillis = time::get)
        repeat(3) {
            assertNull(repository.resolve(firstCell))
            assertNull("Negative cache is reused, not another provider result", repository.resolve(firstCell))
            assertEquals(it + 1, calls.get())
            time.addAndGet(60_000)
        }
        assertEquals("합성 복구 지역", repository.resolve(firstCell))
        assertEquals(4, calls.get())

        val rejectedCalls = AtomicInteger()
        val failedRepository = CurrentAreaRepository(context, lookup = {
            rejectedCalls.incrementAndGet()
            throw java.io.IOException("Synthetic provider unavailable")
        }, elapsedMillis = time::get)
        assertNull(failedRepository.resolve(missingCell))
        assertNull(failedRepository.resolve(missingCell))
        assertEquals("Provider failure is negatively cached too", 1, rejectedCalls.get())

        val attempts = AtomicInteger()
        val interrupted = AtomicBoolean()
        val timeoutRepository = CurrentAreaRepository(context, lookup = {
            if (attempts.incrementAndGet() == 1) {
                try { awaitCancellation() } finally { interrupted.set(true) }
            } else "합성 재확인 지역"
        }, elapsedMillis = time::get, timeoutMillis = 100)
        assertNull(withTimeout(10_000) { timeoutRepository.resolve(secondCell) })
        assertTrue(interrupted.get())
        assertNull(timeoutRepository.resolve(secondCell))
        assertEquals(1, attempts.get())
        time.addAndGet(60_000)
        assertEquals("합성 재확인 지역", timeoutRepository.resolve(secondCell))
        assertEquals(2, attempts.get())
    }

    @Test fun cancelledSlowResultCannotPopulateCacheOrReplaceTheNextResolution() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val repository = CurrentAreaRepository(context, lookup = {
            if (calls.incrementAndGet() == 1) {
                entered.complete(Unit)
                // Model a platform backend which cannot stop an already running lookup.
                withContext(NonCancellable) { release.await() }
                "이전 합성 지역"
            } else "새 합성 지역"
        }, elapsedMillis = { 1_000L }, timeoutMillis = 30_000)
        val old = async { repository.resolve(firstCell) }
        try {
            withTimeout(10_000) { entered.await() }
            old.cancel()
            release.complete(Unit)
            val failure = runCatching { withTimeout(10_000) { old.await() } }.exceptionOrNull()
            assertTrue("Caller cancellation must propagate", failure is CancellationException)
            assertEquals("새 합성 지역", withTimeout(10_000) { repository.resolve(firstCell) })
            assertEquals(2, calls.get())
            assertEquals("새 합성 지역", repository.resolve(firstCell))
            assertEquals(2, calls.get())
        } finally {
            release.complete(Unit)
            old.cancelAndJoin()
        }
    }
}

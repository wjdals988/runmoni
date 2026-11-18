@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.runmoni.app.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.ContinuationInterceptor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Controlled diagnostic of the Compose test effect context.
 * No product callback, interaction, renderer or preference is altered. This
 * probes the test's inherited effect dispatcher, not a production crash rate.
 */
abstract class FixtureDispatcherProbe {
    abstract val compose: ComposeContentTestRule
    abstract val mode: String

    protected fun observeWorkerRoundTrips() {
        val observations = ConcurrentLinkedQueue<JSONObject>()
        val done = AtomicBoolean(false)
        val gate = Any()
        var closed = false
        val pending = mutableMapOf<CancellableContinuation<Unit>, Runnable>()
        val rounds = 16
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.cacheDir, "fixture-dispatcher-probe-$mode.json")
        check(!output.exists()) { "An existing diagnostic report must be preserved by the owner" }
        // Output is owned by this unique diagnostic. No account, database,
        // image, location, settings or SharedPreferences are read or written.
        suspend fun observe(stage: String, round: Int) {
            val interceptor = currentCoroutineContext()[ContinuationInterceptor]?.javaClass?.name ?: "none"
            synchronized(gate) {
                if (!closed) {
                    observations.add(JSONObject().put("stage", stage).put("round", round)
                        .put("thread", Thread.currentThread().name)
                        .put("mainLooper", Looper.myLooper() === Looper.getMainLooper())
                        .put("monotonicNanos", SystemClock.elapsedRealtimeNanos())
                        .put("interceptor", interceptor))
                }
            }
        }
        val producer = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "fixture-dispatcher-producer").apply { isDaemon = true }
        }
        val registration = Handler(Looper.getMainLooper())
        var completed = false
        try {
            compose.setContent {
                Box {}
                LaunchedEffect(Unit) {
                    try {
                        repeat(rounds) { round ->
                            observe("before", round)
                            suspendCancellableCoroutine<Unit> { continuation ->
                                // Post registration through Main, then complete on a plain
                                // executor. The test coroutine interceptor cannot relocate
                                // this physical producer or a suspend observation helper.
                                val callback = Runnable {
                                    var rejected: RejectedExecutionException? = null
                                    val cancelPending = synchronized(gate) {
                                        if (closed || !continuation.isActive) {
                                            pending.remove(continuation)
                                            closed && continuation.isActive
                                        } else {
                                            try {
                                                producer.execute {
                                                    val shouldResume = synchronized(gate) {
                                                        if (closed || !continuation.isActive) {
                                                            pending.remove(continuation)
                                                            false
                                                        } else {
                                                            observations.add(JSONObject().put("stage", "worker").put("round", round)
                                                                .put("thread", Thread.currentThread().name)
                                                                .put("mainLooper", Looper.myLooper() === Looper.getMainLooper())
                                                                .put("monotonicNanos", SystemClock.elapsedRealtimeNanos())
                                                                .put("interceptor", "external-java-executor"))
                                                            true
                                                        }
                                                    }
                                                    if (shouldResume) {
                                                        // Default may resume inline and register the next round.
                                                        // Neither resume nor cancellation may run under gate.
                                                        try { continuation.resume(Unit) }
                                                        finally { synchronized(gate) { pending.remove(continuation) } }
                                                    }
                                                }
                                                false
                                            } catch (failure: RejectedExecutionException) {
                                                pending.remove(continuation)
                                                rejected = failure
                                                true
                                            }
                                        }
                                    }
                                    if (cancelPending) continuation.cancel(rejected)
                                }
                                continuation.invokeOnCancellation {
                                    synchronized(gate) {
                                        pending.remove(continuation)?.let { registration.removeCallbacks(it) }
                                    }
                                }
                                var postFailure: IllegalStateException? = null
                                val registered = synchronized(gate) {
                                    if (closed || !continuation.isActive) {
                                        false
                                    } else {
                                        pending[continuation] = callback
                                        if (registration.post(callback)) {
                                            true
                                        } else {
                                            pending.remove(continuation)
                                            postFailure = IllegalStateException("Diagnostic Main registration was rejected")
                                            false
                                        }
                                    }
                                }
                                if (!registered) continuation.cancel(postFailure)
                            }
                            observe("after", round)
                        }
                    } finally { done.set(true) }
                }
            }
            compose.waitUntil(15_000) { done.get() }
            val after = observations.filter { it.getString("stage") == "after" }
            assertEquals("All rounds completed", rounds, after.size)
            assertEquals("Each round has before, worker, after observations", rounds * 3, observations.size)
            assertTrue("Worker observation stays outside Android main Looper",
                observations.filter { it.getString("stage") == "worker" }.all { !it.getBoolean("mainLooper") })
            if (mode == "standard") {
                assertTrue("Queued effect completion resumes through the UI-driven test clock",
                    after.all { it.getBoolean("mainLooper") })
            }
            // The default mode deliberately has no must-fail or must-escape
            // assertion. Record what happens in this run; don't invent a rate.
            completed = true
        } finally {
            val (toCancel, values) = synchronized(gate) {
                closed = true
                // This Handler and executor belong only to this diagnostic.
                registration.removeCallbacksAndMessages(null)
                producer.shutdownNow()
                val continuations = pending.keys.toList()
                pending.clear()
                continuations to observations.toList()
            }
            // Cancellation may invoke a callback or resume coroutine cleanup inline.
            toCancel.forEach { it.cancel() }
            val after = values.filter { it.getString("stage") == "after" }
            val report = JSONObject().put("schemaVersion", 1).put("mode", mode)
                .put("scope", "Compose test effect context only; not a product threading fix or failure probability")
                .put("roundsRequested", rounds).put("roundsObserved", after.size)
                .put("afterOnMainLooper", after.count { it.getBoolean("mainLooper") })
                .put("afterOffMainLooper", after.count { !it.getBoolean("mainLooper") })
                .put("completed", completed).put("observations", JSONArray(values))
            output.writeText(report.toString(2) + "\n")
        }
    }
}

class FixtureDispatcherDefaultProbeTest : FixtureDispatcherProbe() {
    @get:Rule override val compose = createComposeRule()
    override val mode = "default"
    @Test fun recordsDefaultContinuationLooperWithoutAssumingAnEscape() = observeWorkerRoundTrips()
}

class FixtureDispatcherStandardProbeTest : FixtureDispatcherProbe() {
    @get:Rule override val compose = createComposeRule(effectContext = StandardTestDispatcher())
    override val mode = "standard"
    @Test fun queuedContinuationsResumeOnAndroidMainLooper() = observeWorkerRoundTrips()
}

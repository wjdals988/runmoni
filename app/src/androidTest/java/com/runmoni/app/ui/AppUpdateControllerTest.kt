package com.runmoni.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.runmoni.app.data.AppUpdateRepository
import com.runmoni.app.data.AppUpdateSource
import com.runmoni.app.domain.AppRelease
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Deterministic cache/failure/concurrency checks, no HTTP requests or APK downloads. */
class AppUpdateControllerTest {
    @get:Rule val compose = createComposeRule()
    private var now = 1_000_000_000L
    private fun release(code: Long): AppRelease {
        val version = "99.0.$code-dev"
        return AppRelease(version, code, "runmoni-$version.apk", 200_000_000, "a".repeat(64),
            AppRelease.RELEASE_CERTIFICATE, true, "com.runmoni.app",
            "${AppRelease.REPOSITORY}/releases/download/v$version/runmoni-$version.apk",
            "${AppRelease.REPOSITORY}/releases/tag/v$version")
    }
    private inner class Source(var cache: AppUpdateRepository.Cached) : AppUpdateSource {
        var attempts = 0
        var failure = false
        var gate: CompletableDeferred<Unit>? = null
        var next = release(10)
        override fun cached() = cache
        override suspend fun check(includeDevelopment: Boolean): AppUpdateRepository.Cached {
            attempts++
            gate?.await()
            if (failure) throw IOException("offline fixture")
            return AppUpdateRepository.Cached(next, now).also { cache = it }
        }
    }
    private fun withController(source: Source, block: (AppUpdateController) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        compose.setContent { }
        lateinit var controller: AppUpdateController
        compose.runOnIdle { controller = AppUpdateController(source, scope) { now } }
        try { block(controller) } finally { scope.cancel() }
    }

    @Test fun cachedReleaseIsRecheckedAtSixHoursAndAfterClockRollback() {
        val source = Source(AppUpdateRepository.Cached(release(8), now))
        withController(source) { controller ->
            compose.runOnIdle { controller.check(); assertEquals(0, source.attempts) }
            now += 6 * 60 * 60 * 1000L
            compose.runOnIdle { controller.check() }
            compose.waitUntil(3000) { !controller.state.checking }
            compose.runOnIdle { assertEquals(1, source.attempts); assertEquals(10L, controller.state.release!!.versionCode) }
            now -= 60_000
            compose.runOnIdle { controller.check() }
            compose.waitUntil(3000) { !controller.state.checking }
            compose.runOnIdle { assertEquals(2, source.attempts) }
        }
    }

    @Test fun failedRefreshKeepsOfferAndThirtySecondRetryCanRecover() {
        val source = Source(AppUpdateRepository.Cached(release(8), now)).apply { failure = true }
        withController(source) { controller ->
            compose.runOnIdle { controller.check(force = true) }
            compose.waitUntil(3000) { !controller.state.checking }
            compose.runOnIdle { assertNotNull(controller.state.error); assertEquals(8L, controller.state.release!!.versionCode); controller.check(force = true); assertEquals(1, source.attempts) }
            now += 30_000
            source.failure = false
            compose.runOnIdle { controller.check(force = true) }
            compose.waitUntil(3000) { !controller.state.checking }
            compose.runOnIdle { assertEquals(2, source.attempts); assertNull(controller.state.error); assertEquals(10L, controller.state.release!!.versionCode) }
        }
    }

    @Test fun repeatedChecksCannotStartAnotherInFlightRequest() {
        val source = Source(AppUpdateRepository.Cached(null, 0)).apply { gate = CompletableDeferred() }
        withController(source) { controller ->
            compose.runOnIdle { controller.check(force = true); controller.check(force = true); assertEquals(1, source.attempts); assertTrue(controller.state.checking) }
            compose.runOnIdle { source.gate!!.complete(Unit) }
            compose.waitUntil(3000) { !controller.state.checking }
            compose.runOnIdle { assertEquals(1, source.attempts) }
        }
    }
}

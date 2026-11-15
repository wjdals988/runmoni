package com.runmoni.app.data

import com.runmoni.app.domain.*
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Deterministic coordinator checks. No Firebase, Android clock, recorder or public writes. */
class RunnerSharingControllerTest {
    private class Gateway(private val uid: () -> String) : RunnerSharingGateway {
        var online = true
        var serial = 0L
        var allocations = 0
        var cleanupCalls = 0
        var active: RunnerShareSession? = null
        val retained = linkedMapOf<String, RunnerShareSession>()
        val preparedUids = mutableListOf<String>()
        val publications = mutableListOf<RunnerShareSession>()
        var duringCleanup: (() -> Unit)? = null
        var beforePrepare: (() -> Unit)? = null
        var activationGate: CompletableDeferred<Unit>? = null
        var cleanupGate: CompletableDeferred<Unit>? = null
        override fun activeSession() = active
        override suspend fun cleanupInvalidated(expectedUid: String) {
            check(uid() == expectedUid)
            cleanupCalls++
            cleanupGate?.await()
            duringCleanup?.invoke()
            val pending = retained.values.filter { it !== active && it.uid == expectedUid }
            if (pending.isNotEmpty() && !online) throw IOException("offline cleanup")
            pending.forEach { retained.remove(it.token) }
        }
        override fun prepareEnable(runId: String, adultConfirmed: Boolean, expectedUid: String): RunnerShareSession {
            preparedUids += expectedUid
            beforePrepare?.invoke()
            check(adultConfirmed && uid() == expectedUid)
            check(active == null && retained.values.none { it.uid == expectedUid })
            val session = RunnerShareSession(expectedUid, runId, "session-${++serial}", serial)
            allocations++; active = session; retained[session.token] = session
            return session
        }
        override suspend fun activate(session: RunnerShareSession): RunnerShareSession {
            // Model an SDK callback that can outlive cancellation. The coordinator
            // must not adopt it merely because this suspended operation returned.
            activationGate?.let { withContext(NonCancellable) { it.await() } }
            if (!online) throw IOException("offline activate")
            return session
        }
        override suspend fun publish(session: RunnerShareSession, point: TrackPoint, nickname: String, avatar: AvatarStyle) {
            check(active === session && uid() == session.uid)
            publications += session
        }
        override fun invalidate(session: RunnerShareSession?): RunnerShareSession? {
            val known = session?.takeIf { retained[it.token] === it } ?: return null
            if (active === known) active = null
            return known
        }
        override suspend fun revoke(session: RunnerShareSession?) {
            val known = invalidate(session ?: active) ?: return
            if (!online || uid() != known.uid) throw IOException("cleanup not available for this identity")
            retained.remove(known.token)
        }
    }

    private class Fixture {
        var uid = "U1"
        var preferred = "U1"
        var elapsed = 1_000L
        val wall = 100_000L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gateway = Gateway { uid }
        var profileGate: CompletableDeferred<Unit>? = null
        val controller = RunnerSharingController(gateway, { uid }, {
            profileGate?.let { withContext(NonCancellable) { it.await() } }
            "review runner" to AvatarStyle()
        }, { preferred }, { elapsed }, { wall }, scope)
        val run = ActiveRun(id = "owned-run", phase = RunPhase.RUNNING,
            points = listOf(TrackPoint(0, 37.5, 127.0, 5f, wall)))
        fun observe(value: ActiveRun = run, live: Boolean = true, owner: String? = value.id) = controller.observe(value, live, owner)
        fun close() = scope.cancel()
    }

    @Test fun seventyOfflineRetriesKeepOneFenceThenRecoverOnline() {
        val f = Fixture()
        try {
            f.gateway.online = false
            f.observe()
            repeat(70) { f.elapsed += 30_000; f.observe() }
            assertEquals(1, f.gateway.allocations)
            assertEquals(1, f.gateway.retained.size)
            assertTrue(f.gateway.cleanupCalls > 64)
            assertFalse(f.controller.state.value.enabled)
            assertTrue(f.gateway.publications.isEmpty())
            f.gateway.online = true; f.elapsed += 30_000; f.observe()
            assertEquals(2, f.gateway.allocations)
            assertEquals(1, f.gateway.retained.size)
            assertTrue(f.controller.state.value.enabled)
            assertEquals(listOf("U1"), f.gateway.publications.map { it.uid })
        } finally { f.close() }
    }

    @Test fun uidChangedDuringCleanupCannotInheritThePreviousAccountsChoice() {
        val f = Fixture()
        try {
            f.gateway.duringCleanup = { f.uid = "U2" }
            f.observe()
            assertEquals(0, f.gateway.allocations)
            assertTrue(f.gateway.preparedUids.isEmpty())
            assertTrue(f.gateway.publications.isEmpty())
            assertFalse(f.controller.state.value.enabled)
        } finally { f.close() }
    }

    @Test fun expectedUidIsNotReplacedByACurrentUidInsidePrepare() {
        val f = Fixture()
        try {
            f.gateway.beforePrepare = { f.uid = "U2" }
            f.observe()
            assertEquals(listOf("U1"), f.gateway.preparedUids)
            assertEquals(0, f.gateway.allocations)
            assertTrue(f.gateway.publications.isEmpty())
            assertFalse(f.controller.state.value.enabled)
        } finally { f.close() }
    }

    @Test fun stopFencesALateNonCancellableActivation() = runBlocking {
        val f = Fixture()
        try {
            val gate = CompletableDeferred<Unit>(); f.gateway.activationGate = gate
            f.observe(); assertTrue(f.controller.state.value.busy)
            f.controller.stop(); gate.complete(Unit); yield()
            assertFalse(f.controller.state.value.enabled)
            assertTrue(f.gateway.publications.isEmpty())
            assertNull(f.gateway.active)
        } finally { f.close() }
    }

    @Test fun oldUidCleanupFailureDoesNotAuthorizeOrBlockANewConsentingUid() = runBlocking {
        val f = Fixture()
        try {
            val gate = CompletableDeferred<Unit>(); f.gateway.activationGate = gate
            f.observe(); f.uid = "U2"; gate.complete(Unit); yield()
            assertFalse(f.controller.state.value.enabled)
            assertTrue(f.gateway.publications.isEmpty())
            assertEquals(listOf("U1"), f.gateway.retained.values.map { it.uid })
            f.gateway.activationGate = null
            // A separate explicit U2 choice is represented by this test fixture.
            f.preferred = "U2"; f.elapsed += 30_000; f.observe()
            assertTrue(f.controller.state.value.enabled)
            assertEquals(listOf("U2"), f.gateway.publications.map { it.uid })
            assertTrue("The old account's unacknowledged fence is retained", f.gateway.retained.values.any { it.uid == "U1" })
        } finally { f.close() }
    }

    @Test fun clearingTheChoiceDuringCleanupPreventsANewSession() = runBlocking {
        val f = Fixture()
        try {
            val gate = CompletableDeferred<Unit>(); f.gateway.cleanupGate = gate
            f.observe(); f.preferred = ""; gate.complete(Unit); yield()
            assertEquals(0, f.gateway.allocations)
            assertTrue(f.gateway.publications.isEmpty())
            assertFalse(f.controller.state.value.enabled)
            assertFalse(f.controller.state.value.busy)
        } finally { f.close() }
    }

    @Test fun manualEnableAlsoRequiresTheChoiceAfterLateActivation() = runBlocking {
        val f = Fixture()
        try {
            f.preferred = ""; f.observe() // Establish a live recorder without auto-enabling.
            f.preferred = "U1"
            val gate = CompletableDeferred<Unit>(); f.gateway.activationGate = gate
            f.controller.enable(f.run.id, adultConfirmed = true, expectedUid = "U1")
            assertTrue(f.controller.state.value.busy)
            f.preferred = ""; gate.complete(Unit); yield()
            assertFalse(f.controller.state.value.enabled)
            assertTrue(f.gateway.publications.isEmpty())
            assertNull(f.gateway.active)
        } finally { f.close() }
    }

    @Test fun suppressedChoiceRevokesAnAlreadyEnabledSessionOnTheNextRecorderObservation() {
        val f = Fixture()
        try {
            f.observe(); assertTrue(f.controller.state.value.enabled)
            val priorPublications = f.gateway.publications.size
            f.preferred = ""; f.observe()
            assertFalse(f.controller.state.value.enabled)
            assertNull(f.gateway.active)
            assertEquals(priorPublications, f.gateway.publications.size)
        } finally { f.close() }
    }

    @Test fun stopAfterSuspendedProfileReadPreventsPublish() = runBlocking {
        val f = Fixture()
        try {
            val gate = CompletableDeferred<Unit>(); f.profileGate = gate
            f.observe(); assertTrue(f.controller.state.value.enabled)
            f.controller.stop(); gate.complete(Unit); yield()
            assertTrue(f.gateway.publications.isEmpty())
            assertFalse(f.controller.state.value.enabled)
        } finally { f.close() }
    }

    @Test fun pauseRevokesAndAnOldRecorderCannotStopTheNewRun() {
        val f = Fixture()
        try {
            f.observe(); assertTrue(f.controller.state.value.enabled)
            f.observe(f.run.copy(phase = RunPhase.PAUSED))
            assertFalse(f.controller.state.value.enabled)
            val next = f.run.copy(id = "new-owned-run")
            f.observe(next); assertTrue(f.controller.state.value.enabled)
            f.controller.stopForRun(f.run.id)
            assertTrue(f.controller.state.value.enabled)
            assertEquals(next.id, f.gateway.active?.runId)
        } finally { f.close() }
    }
}

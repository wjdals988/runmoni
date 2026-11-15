package com.runmoni.app.data

import android.os.SystemClock
import com.runmoni.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Application-owned sharing follows a foreground recorder, independently of Activity visibility. */
class RunnerSharingController(
    private val repository: RunnerSharingGateway,
    private val acceptedUid: () -> String,
    private val publicProfile: suspend () -> Pair<String, AvatarStyle>,
    private val preferredUid: () -> String = { "" },
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallTimeMillis: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val lock = Any()
    private val mutableState = MutableStateFlow(RunnerSharingUi())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var armedRunId: String? = null
    private var liveRun = ActiveRun()
    private var liveAt = Long.MIN_VALUE
    private var enabledForUid = ""
    private var enableJob: Job? = null
    private var enablingUid = ""
    private var publishJob: Job? = null
    private var lastAttemptAt = Long.MIN_VALUE
    private var lastEnableAttemptAt = Long.MIN_VALUE

    private fun mayEnable(runId: String, expectedUid: String): Boolean =
        expectedUid.isNotBlank() && acceptedUid() == expectedUid &&
            preferredUid() == expectedUid && runId.isNotBlank() &&
            liveRun.id == runId && liveRun.phase == RunPhase.RUNNING && liveAt != Long.MIN_VALUE &&
            elapsedRealtime() - liveAt in 0..10_000

    fun enable(runId: String, adultConfirmed: Boolean, expectedUid: String) =
        requestEnable(runId, adultConfirmed, expectedUid)

    private fun requestEnable(runId: String, adultConfirmed: Boolean, expectedUid: String) {
        val job = synchronized(lock) {
            if (!adultConfirmed || expectedUid.isBlank() || acceptedUid() != expectedUid) return
            if (!mayEnable(runId, expectedUid)) {
                if (armedRunId == null && enableJob?.isActive != true)
                    mutableState.value = RunnerSharingUi(error = "진행 중인 러닝과 프로필 연결을 확인해 주세요.")
                return
            }
            if ((armedRunId == runId && enabledForUid == expectedUid && mutableState.value.enabled) ||
                (enableJob?.isActive == true && enablingUid == expectedUid)) return
            if (armedRunId != null || enableJob?.isActive == true) stopLocked()
            val ticket = ++generation
            lastEnableAttemptAt = elapsedRealtime()
            enablingUid = expectedUid
            mutableState.value = RunnerSharingUi(busy = true)
            scope.launch(start = CoroutineStart.LAZY) {
                var prepared: RunnerShareSession? = null
                try {
                    // Do not create an additional retained SDK context on each
                    // offline retry. Only this UID's acknowledged server cleanup
                    // permits its next session; unrelated accounts are untouched.
                    repository.cleanupInvalidated(expectedUid)
                    val session = synchronized(lock) {
                        if (ticket != generation || !mayEnable(runId, expectedUid)) return@launch
                        repository.prepareEnable(runId, adultConfirmed, expectedUid).also {
                            prepared = it
                            check(it.uid == expectedUid) { "위치 공개 동의의 계정이 바뀌었어요." }
                            armedRunId = runId; enabledForUid = expectedUid
                        }
                    }
                    repository.activate(session)
                    val accepted = synchronized(lock) {
                        if (ticket != generation || !mayEnable(runId, expectedUid) || session.uid != expectedUid) false
                        else { mutableState.value = RunnerSharingUi(enabled = true); true }
                    }
                    if (!accepted) {
                        repository.invalidate(session)
                        repository.revoke(session)
                    }
                } catch (failure: Exception) {
                    prepared?.let { repository.invalidate(it) }
                    synchronized(lock) { if (ticket == generation) {
                        armedRunId = null; enabledForUid = ""
                        mutableState.value = if (failure is CancellationException && failure !is TimeoutCancellationException) RunnerSharingUi()
                            else RunnerSharingUi(error = "공유를 시작하지 못했어요. 프로필·서버 설정과 연결을 확인해 주세요.")
                    } }
                    if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                } finally {
                    synchronized(lock) { if (ticket == generation) {
                        enableJob = null; enablingUid = ""
                        if (!mutableState.value.enabled) {
                            armedRunId = null; enabledForUid = ""
                            mutableState.update { it.copy(busy = false) }
                        }
                    } }
                }
            }.also { enableJob = it }
        }
        job.start()
    }

    /** Only the actual recorder supplies the owner id; a stale service cannot stop another run. */
    fun observe(run: ActiveRun, live: Boolean, ownerRunId: String?) {
        if (ownerRunId.isNullOrBlank()) return
        synchronized(lock) {
            if (live && run.id == ownerRunId) { liveRun = run; liveAt = elapsedRealtime() }
            else if (liveRun.id == ownerRunId) liveAt = Long.MIN_VALUE
            if (armedRunId == ownerRunId && (!live || run.id != ownerRunId || run.phase != RunPhase.RUNNING ||
                    acceptedUid() != enabledForUid || preferredUid() != enabledForUid)) {
                stopLocked()
                return
            }
            val now = elapsedRealtime()
            val currentUid = acceptedUid()
            if (preparedSharingMayFollow(run, live, ownerRunId, currentUid, preferredUid()) &&
                armedRunId == null && (lastEnableAttemptAt == Long.MIN_VALUE || now - lastEnableAttemptAt >= 30_000)) {
                requestEnable(ownerRunId, adultConfirmed = true, expectedUid = currentUid)
            }
            if (!live || run.id != ownerRunId || !mutableState.value.enabled || armedRunId != ownerRunId || publishJob?.isActive == true ||
                lastAttemptAt != Long.MIN_VALUE && now - lastAttemptAt < RunnerPresencePolicy.PUBLISH_INTERVAL_MILLIS) return
            val session = repository.activeSession() ?: return
            val expectedUid = enabledForUid
            if (session.uid != expectedUid || acceptedUid() != expectedUid) return
            val point = run.points.lastOrNull()?.takeIf { RunnerPresencePolicy.shareable(it, wallTimeMillis()) } ?: return
            val ticket = generation
            lastAttemptAt = now
            publishJob = scope.launch {
                try {
                    val (name, avatar) = publicProfile()
                    synchronized(lock) {
                        check(ticket == generation && mutableState.value.enabled && session.uid == expectedUid &&
                            mayEnable(session.runId, expectedUid))
                    }
                    repository.publish(session, point, name, avatar)
                    synchronized(lock) { if (ticket == generation) mutableState.update { it.copy(error = null) } }
                } catch (failure: Exception) {
                    if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                    synchronized(lock) { if (ticket == generation) mutableState.update {
                        it.copy(error = "공유 갱신을 확인하지 못했어요. 연결이 회복되면 다시 시도해요.")
                    } }
                }
            }
        }
    }

    fun stopForRun(runId: String?) = synchronized(lock) {
        if (runId != null && liveRun.id == runId) liveAt = Long.MIN_VALUE
        if (runId != null && (armedRunId == runId || (enableJob?.isActive == true && liveRun.id == runId))) stopLocked()
    }
    fun stop() = synchronized(lock) { stopLocked() }
    private fun stopLocked() {
        val session = repository.invalidate(repository.activeSession())
        generation++; armedRunId = null; enabledForUid = ""; lastAttemptAt = Long.MIN_VALUE
        lastEnableAttemptAt = Long.MIN_VALUE
        enableJob?.cancel(); enableJob = null; enablingUid = ""
        publishJob?.cancel(); publishJob = null
        mutableState.value = RunnerSharingUi()
        if (session != null) scope.launch {
            try { repository.revoke(session) } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Server public reads expire within75s even when cleanup is offline. */ }
        }
    }
}

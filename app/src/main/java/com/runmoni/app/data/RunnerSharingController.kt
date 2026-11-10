package com.runmoni.app.data

import android.os.SystemClock
import com.runmoni.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Application-owned sharing follows a foreground recorder, independently of Activity visibility. */
class RunnerSharingController(
    private val repository: RunnerPresenceRepository,
    private val acceptedUid: () -> String,
    private val publicProfile: suspend () -> Pair<String, AvatarStyle>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val mutableState = MutableStateFlow(RunnerSharingUi())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var armedRunId: String? = null
    private var liveRun = ActiveRun()
    private var liveAt = Long.MIN_VALUE
    private var enabledForUid = ""
    private var publishJob: Job? = null
    private var lastAttemptAt = Long.MIN_VALUE

    fun enable(runId: String, adultConfirmed: Boolean) {
        synchronized(lock) {
            val uid = acceptedUid()
            if (!adultConfirmed || uid.isBlank() || runId.isBlank() || liveRun.id != runId || liveRun.phase != RunPhase.RUNNING ||
                liveAt == Long.MIN_VALUE || SystemClock.elapsedRealtime() - liveAt !in 0..10_000) {
                mutableState.value = RunnerSharingUi(error = "진행 중인 러닝과 프로필 연결을 확인해 주세요.")
                return
            }
            if (armedRunId == runId && (mutableState.value.enabled || mutableState.value.busy)) return
            val prepared = try { repository.prepareEnable(runId, adultConfirmed) }
                catch (_: Exception) { mutableState.value = RunnerSharingUi(error = "공유 프로필 연결을 확인해 주세요."); return }
            armedRunId = runId; enabledForUid = uid
            val ticket = ++generation
            mutableState.value = RunnerSharingUi(busy = true)
            scope.launch {
                try {
                    repository.activate(prepared)
                    val accepted = synchronized(lock) {
                        if (ticket != generation || acceptedUid() != uid) false
                        else { mutableState.value = RunnerSharingUi(enabled = true); true }
                    }
                    if (!accepted) repository.revoke(prepared)
                } catch (failure: Exception) {
                    synchronized(lock) { if (ticket == generation) {
                        repository.invalidate(prepared)
                        armedRunId = null; enabledForUid = ""
                        mutableState.value = if (failure is CancellationException && failure !is TimeoutCancellationException) RunnerSharingUi()
                            else RunnerSharingUi(error = "공유를 시작하지 못했어요. 프로필·서버 설정과 연결을 확인해 주세요.")
                    } }
                    if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                }
            }
        }
    }

    /** Only the actual recorder supplies the owner id; a stale service cannot stop another run. */
    fun observe(run: ActiveRun, live: Boolean, ownerRunId: String?) {
        if (ownerRunId.isNullOrBlank()) return
        synchronized(lock) {
            if (live && run.id == ownerRunId) { liveRun = run; liveAt = SystemClock.elapsedRealtime() }
            else if (liveRun.id == ownerRunId) liveAt = Long.MIN_VALUE
            if (armedRunId == ownerRunId && (!live || run.id != ownerRunId || run.phase != RunPhase.RUNNING || acceptedUid() != enabledForUid)) {
                stopLocked()
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (!live || run.id != ownerRunId || !mutableState.value.enabled || armedRunId != ownerRunId || publishJob?.isActive == true ||
                lastAttemptAt != Long.MIN_VALUE && now - lastAttemptAt < RunnerPresencePolicy.PUBLISH_INTERVAL_MILLIS) return
            val session = repository.activeSession() ?: return
            val point = run.points.lastOrNull()?.takeIf { RunnerPresencePolicy.shareable(it, System.currentTimeMillis()) } ?: return
            val ticket = generation
            lastAttemptAt = now
            // Dispatch validation and its tracking reference have one atomic owner.
            publishJob = scope.launch {
                try {
                    val (name, avatar) = publicProfile()
                    synchronized(lock) { check(ticket == generation && mutableState.value.enabled) }
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
        if (runId != null && armedRunId == runId) stopLocked()
    }
    fun stop() = synchronized(lock) { stopLocked() }
    private fun stopLocked() {
        val session = repository.invalidate(repository.activeSession())
        generation++; armedRunId = null; enabledForUid = ""; lastAttemptAt = Long.MIN_VALUE
        publishJob?.cancel(); publishJob = null
        mutableState.value = RunnerSharingUi()
        if (session != null) scope.launch {
            try { repository.revoke(session) } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Server public reads expire within75s even when cleanup is offline. */ }
        }
    }
}

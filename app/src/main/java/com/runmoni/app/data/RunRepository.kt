package com.runmoni.app.data

import android.content.Context
import android.location.Location
import android.os.SystemClock
import com.runmoni.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.floor

class RunRepository internal constructor(
    val database: RunDatabase,
    val dao: RunDao = database.runs(),
    private val credit: (String, Int) -> Unit,
) {
    constructor(context: Context) : this(RunDatabase.create(context), credit = localCredit(context))

    private val mutex = Mutex()
    private val checkpoints = RunCheckpointWriter { run, points -> dao.saveCheckpoint(run, points) }
    private var engine = RunEngine()
    private var sequence = 0
    private var lastRecordedMillis = 0L
    private var lastGpsSignalElapsedMillis: Long? = null
    private var gpsSignalStale = true
    private var stored: StoredRun? = null
    private var pendingFinish: StoredRun? = null
    private var lastWriteAttemptMillis = 0L
    private val pendingCredits = linkedMapOf<String, Int>()
    private var creditScanPending = true
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialization = CompletableDeferred<Unit>()
    val active = MutableStateFlow(ActiveRun())
    private val persistenceState = MutableStateFlow(RunPersistenceState())
    private val historyRetry = Channel<Unit>(Channel.CONFLATED)
    val persistence: StateFlow<RunPersistenceState> = persistenceState.asStateFlow()
    val records: StateFlow<List<RunRecord>> = dao.records().distinctUntilChanged().map { rows ->
        rows.map { toRecord(it) }.also { persistenceState.update { state -> state.copy(historyUnavailable = false) } }
    }.retryWhen { failure, _ ->
        if (failure is CancellationException) throw failure
        persistenceState.update { it.copy(historyUnavailable = true) }
        withTimeoutOrNull(WRITE_RETRY_MILLIS) { historyRetry.receive() }
        true
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val lastFinished = MutableStateFlow<RunRecord?>(null)

    init { scope.launch {
        try {
            mutex.withLock {
                if (loadRecovery()) retryCredits()
            }
        } finally { initialization.complete(Unit) }
    } }

    private suspend fun loadRecovery(): Boolean {
        persistenceState.update { it.copy(saving = true) }
        return try {
            val row = dao.unfinished()
            val points = row?.let { dao.points(it.id).map { point -> point.toDomain() } }.orEmpty()
            stored = row?.copy(interrupted = true)
            if (row != null) active.value = ActiveRun(row.id, RunPhase.RECOVERY, row.startedAtMillis, row.distanceMeters, row.activeDurationMillis, row.elapsedDurationMillis, points = points, gpsStatus = "이전 기록 복구 가능 · 공백 구간 제외")
            persistenceState.update { it.copy(initialized = true, saving = false, issue = null) }
            true
        } catch (cancelled: CancellationException) {
            persistenceState.update { it.copy(saving = false, initialized = false, issue = RunPersistenceIssue.RECOVERY) }
            throw cancelled
        }
        catch (_: Exception) {
            persistenceState.update { it.copy(initialized = false, saving = false, issue = RunPersistenceIssue.RECOVERY) }
            false
        }
    }

    suspend fun begin(recover: Boolean = false) {
        initialization.await()
        mutex.withLock {
            check(persistenceState.value.initialized) { "기기 기록을 다시 확인한 뒤 시작해 주세요." }
            check(pendingFinish == null) { "종료 기록을 먼저 저장해 주세요." }
            if (active.value.phase == RunPhase.RUNNING || active.value.phase == RunPhase.PAUSED) return@withLock
            val unfinished = try { dao.unfinished() }
            catch (cancelled: CancellationException) {
                persistenceState.update { it.copy(initialized = false, saving = false, issue = RunPersistenceIssue.RECOVERY) }
                throw cancelled
            }
            catch (failure: Exception) {
                persistenceState.update { it.copy(initialized = false, saving = false, issue = RunPersistenceIssue.RECOVERY) }
                throw IllegalStateException("기기 기록을 다시 확인한 뒤 시작해 주세요.", failure)
            }
            if (!recover && unfinished != null) throw IllegalStateException("이전 기록을 복구하거나 종료한 뒤 시작해 주세요.")
            val previous = unfinished.takeIf { recover }
            engine = RunEngine()
            val row = previous?.copy(interrupted = true) ?: StoredRun(UUID.randomUUID().toString(), System.currentTimeMillis())
            if (previous != null) engine.restore(row.distanceMeters, row.activeDurationMillis, row.segment, parseSplits(row.splitsJson))
            val restored = try { if (previous != null) dao.points(row.id).map { it.toDomain() } else emptyList() }
            catch (cancelled: CancellationException) {
                persistenceState.update { it.copy(initialized = false, saving = false, issue = RunPersistenceIssue.RECOVERY) }
                throw cancelled
            }
            catch (failure: Exception) {
                persistenceState.update { it.copy(initialized = false, saving = false, issue = RunPersistenceIssue.RECOVERY) }
                throw IllegalStateException("기기 기록을 다시 확인한 뒤 시작해 주세요.", failure)
            }
            try { dao.save(row) }
            catch (cancelled: CancellationException) {
                persistenceState.update { it.copy(initialized = false, saving = false, issue = RunPersistenceIssue.RECOVERY) }
                throw cancelled
            }
            catch (failure: Exception) {
                // A commit may have succeeded before an acknowledgement failed. Reload before
                // creating another id so that an existing unfinished row becomes recoverable.
                persistenceState.update { it.copy(initialized = false, issue = RunPersistenceIssue.RECOVERY, saving = false) }
                throw IllegalStateException("러닝을 시작하지 못했어요. 기기 저장 공간을 확인한 뒤 다시 시도해 주세요.", failure)
            }
            stored = row
            sequence = (restored.maxOfOrNull { it.sequence } ?: -1) + 1
            lastRecordedMillis = restored.lastOrNull()?.recordedAtMillis ?: 0
            lastGpsSignalElapsedMillis = null
            gpsSignalStale = true
            savedCheckpoint()
            engine.tick(SystemClock.elapsedRealtime(), true)
            active.value = ActiveRun(row.id, RunPhase.RUNNING, row.startedAtMillis, row.distanceMeters, row.activeDurationMillis, System.currentTimeMillis() - row.startedAtMillis, points = restored, gpsStatus = "GPS 신호 기다리는 중")
        }
    }

    suspend fun tick() = mutex.withLock {
        if (pendingFinish != null) return@withLock
        if (active.value.phase !in setOf(RunPhase.RUNNING, RunPhase.PAUSED)) return@withLock
        engine.tick(SystemClock.elapsedRealtime(), active.value.phase == RunPhase.RUNNING)
        if (active.value.phase == RunPhase.RUNNING && !gpsSignalStale && GpsSignalFreshness.isStale(lastGpsSignalElapsedMillis, SystemClock.elapsedRealtime())) {
            engine.breakSegment()
            gpsStatus("GPS 신호 확인 중 · 기존 경로 유지")
        }
        publish()
        checkpoint()
    }

    suspend fun location(location: Location) = mutex.withLock {
        if (active.value.phase != RunPhase.RUNNING) return@withLock
        val time = location.time
        if (time < (stored?.startedAtMillis ?: Long.MAX_VALUE) || time <= lastRecordedMillis || time > System.currentTimeMillis() + 30_000) return@withLock
        val receivedElapsedMillis = SystemClock.elapsedRealtime()
        val measuredElapsedMillis = GpsSignalFreshness.measurementElapsedMillis(location.elapsedRealtimeNanos, time, System.currentTimeMillis(), receivedElapsedMillis)
        if (measuredElapsedMillis == null || GpsSignalFreshness.isStale(measuredElapsedMillis, receivedElapsedMillis)) {
            if (lastGpsSignalElapsedMillis == null || GpsSignalFreshness.isStale(lastGpsSignalElapsedMillis, receivedElapsedMillis)) {
                engine.breakSegment()
                gpsStatus("GPS 신호 확인 중 · 기존 경로 유지")
            }
            return@withLock
        }
        if (!location.hasAccuracy() || !location.accuracy.isFinite() || location.accuracy < 0 ||
            !location.latitude.isFinite() || location.latitude !in -90.0..90.0 ||
            !location.longitude.isFinite() || location.longitude !in -180.0..180.0) {
            engine.breakSegment()
            gpsStatus("위치 정보를 확인하지 못했어요 · 다음 신호를 기다려요")
            return@withLock
        }
        if (!gpsSignalStale && GpsSignalFreshness.isStale(lastGpsSignalElapsedMillis, SystemClock.elapsedRealtime())) engine.breakSegment()
        engine.tick(SystemClock.elapsedRealtime(), true)
        val point = engine.addPoint(TrackPoint(sequence++, location.latitude, location.longitude, location.accuracy, time))
        lastRecordedMillis = time
        lastGpsSignalElapsedMillis = measuredElapsedMillis
        gpsSignalStale = false
        val old = active.value
        active.value = old.copy(points = old.points + point, accuracyMeters = location.accuracy, gpsStatus = if (location.accuracy <= 30) "GPS 수신 중 · 정확도 ${location.accuracy.toInt()}m" else "GPS 정확도 낮음 · 거리 계산에서 제외")
        publish()
        val row = stored ?: return@withLock
        checkpoints.enqueue(StoredPoint(row.id, point.sequence, point.latitude, point.longitude, point.accuracyMeters, point.recordedAtMillis, point.segment))
        checkpoint()
    }

    suspend fun pause(reason: PauseReason = PauseReason.MANUAL) = mutex.withLock {
        if (active.value.phase == RunPhase.PAUSED && reason == PauseReason.MANUAL) {
            active.update { it.copy(pauseReason = reason) }
        }
        if (active.value.phase != RunPhase.RUNNING) return@withLock
        engine.tick(SystemClock.elapsedRealtime(), true)
        engine.breakSegment()
        active.value = active.value.copy(phase = RunPhase.PAUSED, pauseReason = reason, currentPaceSecondsPerKm = null)
        publish(); checkpoint()
    }
    suspend fun resume() = mutex.withLock {
        if (active.value.phase != RunPhase.PAUSED) return@withLock
        if (pendingFinish != null || !checkpoint(force = true)) return@withLock
        engine.tick(SystemClock.elapsedRealtime(), false)
        engine.breakSegment()
        lastGpsSignalElapsedMillis = null
        gpsSignalStale = true
        active.value = active.value.copy(phase = RunPhase.RUNNING, pauseReason = null, accuracyMeters = null, gpsStatus = "GPS 재수신 중")
        publish()
    }
    suspend fun finish() = mutex.withLock {
        if (!persistenceState.value.initialized) return@withLock
        if (pendingFinish != null) { finishPending(); return@withLock }
        val row = stored ?: return@withLock
        if (active.value.phase != RunPhase.RECOVERY) {
            engine.tick(SystemClock.elapsedRealtime(), active.value.phase == RunPhase.RUNNING)
            publish()
        }
        val final = (stored ?: row).copy(finishedAtMillis = System.currentTimeMillis(), mileage = floor((stored ?: row).distanceMeters / 10).toInt())
        // Freeze time, route and reward on the first finish attempt. A retry must not add time.
        pendingFinish = final
        active.value = active.value.copy(phase = RunPhase.PAUSED, pauseReason = null, currentPaceSecondsPerKm = null)
        persistenceState.update { it.copy(finishing = true) }
        finishPending()
    }

    private suspend fun finishPending() {
        val final = pendingFinish ?: return
        persistenceState.update { it.copy(saving = true, finishing = true) }
        try {
            checkpoints.save(final)
        } catch (cancelled: CancellationException) {
            persistenceState.update { it.copy(issue = RunPersistenceIssue.FINISH, saving = false, unsavedPointCount = checkpoints.unsavedPointCount) }
            throw cancelled
        }
        catch (_: Exception) {
            persistenceState.update { it.copy(issue = RunPersistenceIssue.FINISH, saving = false, unsavedPointCount = checkpoints.unsavedPointCount) }
            return
        }
        val finishedPoints = active.value.points
        pendingCredits[final.id] = final.mileage
        pendingFinish = null
        stored = null
        active.value = ActiveRun()
        savedCheckpoint()
        persistenceState.update { it.copy(finishing = false) }
        try { retryCredits() }
        finally { lastFinished.value = recordWithPoints(final, finishedPoints) }
    }

    /** Retry only the unfinished local operation; cloud backup is a separate action. */
    suspend fun retryPersistence() {
        if (persistenceState.value.historyUnavailable) historyRetry.trySend(Unit)
        initialization.await()
        mutex.withLock {
            if (!persistenceState.value.initialized && !loadRecovery()) return@withLock
            if (pendingFinish != null) { finishPending(); return@withLock }
            if (stored != null && !checkpoint(force = true)) return@withLock
            retryCredits()
        }
    }

    private suspend fun checkpoint(force: Boolean = false): Boolean {
        val row = stored ?: return true
        val now = SystemClock.elapsedRealtime()
        if (!force && persistenceState.value.issue == RunPersistenceIssue.CHECKPOINT && now - lastWriteAttemptMillis < WRITE_RETRY_MILLIS) return false
        lastWriteAttemptMillis = now
        persistenceState.update { it.copy(saving = true, unsavedPointCount = checkpoints.unsavedPointCount) }
        return try {
            checkpoints.save(row)
            savedCheckpoint()
            true
        } catch (cancelled: CancellationException) {
            checkpointFailed()
            throw cancelled
        }
        catch (_: Exception) {
            checkpointFailed()
            false
        }
    }

    private fun checkpointFailed() {
        engine.breakSegment()
        active.value = active.value.copy(phase = RunPhase.PAUSED, pauseReason = null, currentPaceSecondsPerKm = null)
        publish()
        persistenceState.update { it.copy(issue = RunPersistenceIssue.CHECKPOINT, saving = false, unsavedPointCount = checkpoints.unsavedPointCount) }
    }

    /** Last-resort boundary for a recorder operation; never show an exception as GPS status. */
    suspend fun reportOperationFailure(issue: RunPersistenceIssue) = mutex.withLock {
        if (stored != null && pendingFinish == null) checkpointFailed()
        persistenceState.update { it.copy(saving = false, issue = if (pendingFinish != null) RunPersistenceIssue.FINISH else issue,
            finishing = pendingFinish != null, unsavedPointCount = checkpoints.unsavedPointCount) }
    }

    private fun savedCheckpoint() {
        persistenceState.update { it.copy(saving = false, lastSavedAtMillis = System.currentTimeMillis(), unsavedPointCount = checkpoints.unsavedPointCount,
            issue = if (it.creditPending) RunPersistenceIssue.CREDIT else null) }
    }

    private suspend fun retryCredits() {
        persistenceState.update { it.copy(saving = true) }
        try {
            // Replaying the wallet's persistent run-id ledger is safe after process death.
            // A failed credit scan must not stop the unfinished-record recovery above.
            if (creditScanPending) {
                dao.records().first().forEach { pendingCredits[it.id] = it.mileage }
                creditScanPending = false
            }
            val entries = pendingCredits.toMap()
            entries.forEach { (id, points) ->
                withContext(Dispatchers.IO) { credit(id, points) }
                pendingCredits.remove(id)
            }
            persistenceState.update { it.copy(saving = false, creditPending = false, pendingCreditRunIds = emptySet(),
                issue = it.issue.takeUnless { issue -> issue == RunPersistenceIssue.CREDIT }) }
        } catch (cancelled: CancellationException) {
            persistenceState.update { it.copy(saving = false, creditPending = true, pendingCreditRunIds = pendingCredits.keys.toSet(),
                issue = it.issue ?: RunPersistenceIssue.CREDIT) }
            throw cancelled
        }
        catch (_: Exception) {
            persistenceState.update { it.copy(saving = false, creditPending = true, pendingCreditRunIds = pendingCredits.keys.toSet(),
                issue = it.issue ?: RunPersistenceIssue.CREDIT) }
        }
    }

    fun gpsStatus(message: String) {
        gpsSignalStale = true
        active.update { it.copy(gpsStatus = message, accuracyMeters = null, currentPaceSecondsPerKm = null) }
    }
    suspend fun interruptLocation(message: String) = mutex.withLock {
        if (pendingFinish != null) return@withLock
        if (active.value.phase !in setOf(RunPhase.RUNNING, RunPhase.PAUSED)) return@withLock
        engine.breakSegment()
        gpsStatus(message)
        publish()
    }
    suspend fun delete(id: String) = mutex.withLock {
        check(id != active.value.id) { "진행 중인 기록은 먼저 종료해 주세요." }
        if (creditScanPending || id in pendingCredits) retryCredits()
        check(!creditScanPending && id !in pendingCredits) { "포인트 적립을 먼저 다시 확인한 뒤 기록을 삭제해 주세요." }
        dao.delete(id)
    }
    suspend fun record(id: String): RunRecord? = dao.get(id)?.let { toRecord(it) }
    private fun publish() {
        val row = stored ?: return
        val elapsed = (System.currentTimeMillis() - row.startedAtMillis).coerceAtLeast(0)
        stored = row.copy(distanceMeters = engine.distanceMeters, activeDurationMillis = engine.activeDurationMillis, elapsedDurationMillis = elapsed, segment = engine.segment, splitsJson = JSONArray().apply { engine.splits.forEach { put(JSONObject().put("km", it.kilometer).put("duration", it.durationMillis)) } }.toString())
        active.value = active.value.copy(distanceMeters = engine.distanceMeters, activeDurationMillis = engine.activeDurationMillis, elapsedDurationMillis = elapsed,
            currentPaceSecondsPerKm = if (active.value.phase == RunPhase.RUNNING && !gpsSignalStale) engine.currentPaceSecondsPerKm() else null)
    }
    private suspend fun toRecord(row: StoredRun) = recordWithPoints(row, dao.points(row.id).map { it.toDomain() })
    private fun recordWithPoints(row: StoredRun, points: List<TrackPoint>) = RunRecord(row.id, row.startedAtMillis, row.finishedAtMillis, row.distanceMeters, row.activeDurationMillis, row.elapsedDurationMillis, points, parseSplits(row.splitsJson), row.synced, row.interrupted, row.mileage)
    private fun StoredPoint.toDomain() = TrackPoint(sequence, latitude, longitude, accuracyMeters, recordedAtMillis, segment)
    private fun parseSplits(json: String): List<KmSplit> = runCatching { val list = JSONArray(json); (0 until list.length()).map { val item = list.getJSONObject(it); KmSplit(item.getInt("km"), item.getLong("duration")) } }.getOrDefault(emptyList())

    internal fun close() { scope.cancel() }

    companion object {
        private const val WRITE_RETRY_MILLIS = 5_000L
        private fun localCredit(context: Context): (String, Int) -> Unit {
            val wallet = LocalWallet(context)
            return { id, points -> wallet.credit(id, points); Unit }
        }
    }
}

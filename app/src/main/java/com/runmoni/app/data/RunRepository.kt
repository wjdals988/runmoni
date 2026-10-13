package com.runmoni.app.data

import android.content.Context
import android.location.Location
import android.os.SystemClock
import com.runmoni.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.floor

class RunRepository(context: Context) {
    private val wallet = LocalWallet(context)
    val database = RunDatabase.create(context)
    val dao = database.runs()
    private val mutex = Mutex()
    private var engine = RunEngine()
    private var sequence = 0
    private var lastRecordedMillis = 0L
    private var stored: StoredRun? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val active = MutableStateFlow(ActiveRun())
    val records: StateFlow<List<RunRecord>> = dao.records().distinctUntilChanged().map { rows -> rows.map { toRecord(it) } }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val lastFinished = MutableStateFlow<RunRecord?>(null)

    init { scope.launch {
        // Completes a credit if the process died after the DB commit but before wallet commit.
        dao.records().first().forEach { wallet.credit(it.id, it.mileage) }
        loadRecovery()
    } }

    private suspend fun loadRecovery() = mutex.withLock {
        val row = dao.unfinished() ?: return@withLock
        stored = row.copy(interrupted = true)
        active.value = ActiveRun(row.id, RunPhase.RECOVERY, row.startedAtMillis, row.distanceMeters, row.activeDurationMillis, row.elapsedDurationMillis, points = dao.points(row.id).map { it.toDomain() }, gpsStatus = "이전 기록 복구 가능 · 공백 구간 제외")
    }

    suspend fun begin(recover: Boolean = false) = mutex.withLock {
        if (active.value.phase == RunPhase.RUNNING || active.value.phase == RunPhase.PAUSED) return@withLock
        val previous = if (recover) dao.unfinished() else null
        if (previous == null && dao.unfinished() != null) throw IllegalStateException("이전 기록을 복구하거나 종료한 뒤 시작해 주세요.")
        engine = RunEngine()
        val row = previous?.copy(interrupted = true) ?: StoredRun(UUID.randomUUID().toString(), System.currentTimeMillis())
        if (previous != null) engine.restore(row.distanceMeters, row.activeDurationMillis, row.segment, parseSplits(row.splitsJson))
        val restored = if (previous != null) dao.points(row.id).map { it.toDomain() } else emptyList()
        stored = row
        sequence = restored.size
        lastRecordedMillis = restored.lastOrNull()?.recordedAtMillis ?: 0
        dao.save(row)
        engine.tick(SystemClock.elapsedRealtime(), true)
        active.value = ActiveRun(row.id, RunPhase.RUNNING, row.startedAtMillis, row.distanceMeters, row.activeDurationMillis, System.currentTimeMillis() - row.startedAtMillis, points = restored, gpsStatus = "GPS 신호 기다리는 중")
    }

    suspend fun tick() = mutex.withLock {
        if (active.value.phase !in setOf(RunPhase.RUNNING, RunPhase.PAUSED)) return@withLock
        engine.tick(SystemClock.elapsedRealtime(), active.value.phase == RunPhase.RUNNING)
        publish()
        stored?.let { dao.save(it) }
    }

    suspend fun location(location: Location) = mutex.withLock {
        if (active.value.phase != RunPhase.RUNNING) return@withLock
        val time = location.time
        if (time < (stored?.startedAtMillis ?: Long.MAX_VALUE) || time <= lastRecordedMillis || time > System.currentTimeMillis() + 30_000) return@withLock
        engine.tick(SystemClock.elapsedRealtime(), true)
        val point = engine.addPoint(TrackPoint(sequence++, location.latitude, location.longitude, location.accuracy, time))
        lastRecordedMillis = time
        val old = active.value
        active.value = old.copy(points = old.points + point, accuracyMeters = location.accuracy, gpsStatus = if (location.accuracy <= 30) "GPS 수신 중 · 정확도 ${location.accuracy.toInt()}m" else "GPS 정확도 낮음 · 거리 계산에서 제외")
        publish()
        val row = stored ?: return@withLock
        dao.append(row, StoredPoint(row.id, point.sequence, point.latitude, point.longitude, point.accuracyMeters, point.recordedAtMillis, point.segment))
    }

    suspend fun pause() = mutex.withLock {
        if (active.value.phase != RunPhase.RUNNING) return@withLock
        engine.tick(SystemClock.elapsedRealtime(), true)
        engine.breakSegment()
        active.value = active.value.copy(phase = RunPhase.PAUSED, currentPaceSecondsPerKm = null)
        publish(); stored?.let { dao.save(it) }
    }
    suspend fun resume() = mutex.withLock {
        if (active.value.phase != RunPhase.PAUSED) return@withLock
        engine.tick(SystemClock.elapsedRealtime(), false)
        engine.breakSegment()
        active.value = active.value.copy(phase = RunPhase.RUNNING, gpsStatus = "GPS 재수신 중")
        publish()
    }
    suspend fun finish() = mutex.withLock {
        val row = stored ?: dao.unfinished() ?: return@withLock
        if (active.value.phase != RunPhase.RECOVERY) {
            engine.tick(SystemClock.elapsedRealtime(), active.value.phase == RunPhase.RUNNING)
            publish()
        }
        val final = (stored ?: row).copy(finishedAtMillis = System.currentTimeMillis(), mileage = floor((stored ?: row).distanceMeters / 10).toInt())
        dao.save(final)
        wallet.credit(final.id, final.mileage)
        lastFinished.value = toRecord(final)
        stored = null
        active.value = ActiveRun()
    }

    fun gpsStatus(message: String) { active.value = active.value.copy(gpsStatus = message) }
    suspend fun delete(id: String) { check(id != active.value.id) { "진행 중인 기록은 먼저 종료해 주세요." }; dao.delete(id) }
    suspend fun record(id: String): RunRecord? = dao.get(id)?.let { toRecord(it) }
    private fun publish() {
        val row = stored ?: return
        val elapsed = (System.currentTimeMillis() - row.startedAtMillis).coerceAtLeast(0)
        stored = row.copy(distanceMeters = engine.distanceMeters, activeDurationMillis = engine.activeDurationMillis, elapsedDurationMillis = elapsed, segment = engine.segment, splitsJson = JSONArray().apply { engine.splits.forEach { put(JSONObject().put("km", it.kilometer).put("duration", it.durationMillis)) } }.toString())
        active.value = active.value.copy(distanceMeters = engine.distanceMeters, activeDurationMillis = engine.activeDurationMillis, elapsedDurationMillis = elapsed, currentPaceSecondsPerKm = engine.currentPaceSecondsPerKm())
    }
    private suspend fun toRecord(row: StoredRun) = RunRecord(row.id, row.startedAtMillis, row.finishedAtMillis, row.distanceMeters, row.activeDurationMillis, row.elapsedDurationMillis, dao.points(row.id).map { it.toDomain() }, parseSplits(row.splitsJson), row.synced, row.interrupted, row.mileage)
    private fun StoredPoint.toDomain() = TrackPoint(sequence, latitude, longitude, accuracyMeters, recordedAtMillis, segment)
    private fun parseSplits(json: String): List<KmSplit> = runCatching { val list = JSONArray(json); (0 until list.length()).map { val item = list.getJSONObject(it); KmSplit(item.getInt("km"), item.getLong("duration")) } }.getOrDefault(emptyList())
}

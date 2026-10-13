package com.runmoni.app.audio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.AudioOptions
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.RemoteAudioTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class AudioController(context: Context) : Closeable {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableStatus = MutableStateFlow(AudioStatus())
    val status: StateFlow<AudioStatus> = mutableStatus.asStateFlow()
    var onBroadcastStopped: ((leaseId: String) -> Unit)? = null
    private var liveRoom: Room? = null
    private var eventsJob: Job? = null
    private var musicJob: Job? = null
    private var projection: MediaProjection? = null
    private var bridge: MusicBridgeSocket? = null
    private var filePump: MusicFilePump? = null
    private var selfMusicOwner: String? = null
    private var activeLeaseId: String? = null
    @Volatile private var musicGeneration = 0L
    @Volatile private var captureRecord: AudioRecord? = null
    private var connectionGeneration = 0L
    private val receivers = ConcurrentHashMap<String, Receiver>()
    private data class Receiver(val track: RemoteAudioTrack, val music: Boolean, val owner: String?)

    init {
        AudioProjectionService.onUserStop = {
            scope.launch { disconnect() }
        }
    }

    suspend fun connect(url: String, token: String, room: String) {
        disconnect()
        val generation = connectionGeneration
        if (url.isBlank() || token.isBlank() || room.isBlank()) {
            mutableStatus.update { it.copy(connection = AudioConnection.NOT_CONFIGURED,
                message = "음성 서버가 아직 설정되지 않았습니다", error = null) }
            return
        }
        mutableStatus.update { it.copy(connection = AudioConnection.CONNECTING, roomName = room,
            message = "음성방 연결 중", error = null) }
        // Keep the selected music app's audio focus; default VoIP focus can pause/duck that source.
        // AudioSwitch still manages route/mode. Route quality and phone-call interruption need device checks.
        val audioHandler = AudioSwitchHandler(context).apply { manageAudioFocus = false }
        val created = LiveKit.create(context, overrides = LiveKitOverrides(
            audioOptions = AudioOptions(audioHandler = audioHandler, disableAudioPrewarming = true)))
        liveRoom = created
        eventsJob = scope.launch {
            created.events.collect { event ->
                if (liveRoom !== created || generation != connectionGeneration) return@collect
                when (event) {
                    is RoomEvent.Reconnecting -> mutableStatus.update { it.copy(connection = AudioConnection.RECONNECTING,
                        message = "음성 연결 복구 중") }
                    is RoomEvent.Reconnected -> mutableStatus.update { it.copy(connection = AudioConnection.CONNECTED,
                        message = "음성 연결됨") }
                    is RoomEvent.Disconnected -> {
                        mutableStatus.update { it.copy(connection = AudioConnection.DISCONNECTED,
                            microphoneEnabled = false, message = "음성 연결 종료") }
                        receivers.clear()
                        stopMusic()
                    }
                    is RoomEvent.TrackSubscribed -> {
                        val track = event.track as? RemoteAudioTrack
                        if (track != null) {
                            val metadata = runCatching { JSONObject(event.participant.metadata ?: "{}") }.getOrNull()
                            val isMusic = event.publication.name == "runmoni-music"
                            receivers[event.publication.sid] = Receiver(track, isMusic, metadata?.optString("owner"))
                            applyVolumes()
                        }
                    }
                    is RoomEvent.TrackUnsubscribed -> receivers.entries.removeAll { it.value.track == event.track }
                    else -> Unit
                }
            }
        }
        try {
            created.connect(url, token)
            currentCoroutineContext().ensureActive()
            if (liveRoom !== created || generation != connectionGeneration) return
            mutableStatus.update { it.copy(connection = AudioConnection.CONNECTED, message = "음성 연결됨", error = null) }
            updateForeground()
        } catch (failure: Exception) {
            if (liveRoom === created) eventsJob?.cancel()
            runCatching { created.disconnect() }
            created.release()
            if (liveRoom === created) liveRoom = null
            if (failure is CancellationException) throw failure
            if (generation != connectionGeneration) return
            mutableStatus.update { it.copy(connection = AudioConnection.ERROR, message = "음성 연결 실패",
                error = "음성 연결을 확인해 주세요. 서버 주소·권한·네트워크가 필요합니다") }
        }
    }

    suspend fun setMicrophoneEnabled(enabled: Boolean) {
        val room = liveRoom
        if (room == null || status.value.connection !in listOf(AudioConnection.CONNECTED, AudioConnection.RECONNECTING)) {
            mutableStatus.update { it.copy(error = "음성방에 먼저 연결해 주세요") }
            return
        }
        if (enabled) {
            requireMicrophonePermission()
            AudioProjectionService.ensureStarted(context, AudioProjectionService.types(true,
                status.value.musicSource == MusicSource.EXTERNAL_APP, true))
        }
        if (liveRoom !== room) return
        try {
            check(room.localParticipant.setMicrophoneEnabled(enabled)) { "마이크 상태를 변경하지 못했습니다" }
        } catch (failure: Exception) {
            runCatching { updateForeground() }
            throw failure
        }
        if (liveRoom !== room) return
        mutableStatus.update { it.copy(microphoneEnabled = enabled, error = null) }
        updateForeground()
    }

    fun setVoiceVolume(value: Float) {
        mutableStatus.update { it.copy(voiceVolume = value.coerceIn(0f, 1f)) }
        applyVolumes()
    }

    fun setMusicVolume(value: Float) {
        mutableStatus.update { it.copy(musicVolume = value.coerceIn(0f, 1f)) }
        filePump?.setVolume(status.value.musicVolume)
        applyVolumes()
    }

    suspend fun playLocalMusicFile(uri: Uri) = startFile(uri, null, null)

    suspend fun startMusicFile(uri: Uri, bridgeUrl: String, bridgeToken: String) = startFile(uri, bridgeUrl, bridgeToken)

    private suspend fun startFile(uri: Uri, bridgeUrl: String?, bridgeToken: String?) {
        stopMusic()
        val generation = musicGeneration
        try {
            val socket = if (bridgeUrl != null && bridgeToken != null) prepareBridge(bridgeUrl, bridgeToken, generation) else null
            ensureMusicCurrent(generation)
            AudioProjectionService.ensureStarted(context, AudioProjectionService.types(status.value.microphoneEnabled, false, true))
            ensureMusicCurrent(generation)
            val pump = MusicFilePump().also { it.setVolume(status.value.musicVolume) }
            filePump = pump
            mutableStatus.update { it.copy(musicSource = MusicSource.FILE, musicSending = socket != null,
                musicSourceLabel = "선택한 음악 파일", bytesSent = 0, queuedBytes = 0, error = null,
                message = if (socket == null) "이 기기에서 음악 재생 중 · 그룹 송출 안 함" else "파일 음악 송출 중") }
            applyVolumes()
            musicJob = scope.launch(Dispatchers.IO) {
                var encoder: OpusMusicEncoder? = null
                try {
                    if (socket != null) encoder = OpusMusicEncoder(socket::send)
                    pump.run(context, uri, encoder)
                    socket?.finish()
                } catch (failure: Exception) {
                    if (failure !is CancellationException) scope.launch {
                        if (generation == musicGeneration)
                            failMusic("음악 파일을 재생하지 못했습니다. 형식·파일 권한·송출 연결을 확인해 주세요")
                    }
                } finally {
                    runCatching { encoder?.close() }
                    scope.launch { if (generation == musicGeneration) stopMusic() }
                }
            }
        } catch (failure: Exception) {
            if (generation != musicGeneration) {
                if (failure is CancellationException) throw failure
                return
            }
            stopMusic()
            if (failure is CancellationException) throw failure
            failMusic(failure.message ?: "음악 재생을 시작하지 못했습니다")
        }
    }

    fun createExternalCaptureIntent(): Intent = context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent()

    suspend fun startExternalCapture(resultCode: Int, data: Intent, packageName: String,
        bridgeUrl: String, bridgeToken: String) {
        stopMusic()
        val generation = musicGeneration
        try {
            require(resultCode == Activity.RESULT_OK) { "기기 오디오 공유 동의가 필요합니다" }
            requireMicrophonePermission()
            require(packageName != context.packageName) { "앱 자체 오디오를 다시 캡처할 수 없습니다" }
            val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
            val socket = prepareBridge(bridgeUrl, bridgeToken, generation)
            ensureMusicCurrent(generation)
            AudioProjectionService.ensureStarted(context, AudioProjectionService.types(status.value.microphoneEnabled, true, true))
            ensureMusicCurrent(generation)
            val capture = checkNotNull(context.getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)) {
                "기기 오디오 공유를 시작하지 못했습니다. 다시 동의해 주세요"
            }
            projection = capture
            val callback = object : MediaProjection.Callback() {
                override fun onStop() { scope.launch {
                    if (projection === capture) { stopMusic(); failMusic("기기 오디오 공유가 종료되었습니다. 다시 동의해 주세요") }
                } }
            }
            capture.registerCallback(callback, Handler(Looper.getMainLooper()))
            val configuration = AudioPlaybackCaptureConfiguration.Builder(capture)
                .addMatchingUid(uid)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
            mutableStatus.update { it.copy(musicSource = MusicSource.EXTERNAL_APP, musicSending = true,
                musicSourceLabel = packageName, message = "선택한 앱 음악 송출 중 · 캡처 차단 시 무음일 수 있음", error = null) }
            applyVolumes()
            musicJob = scope.launch(Dispatchers.IO) {
                var record: AudioRecord? = null
                var encoder: OpusMusicEncoder? = null
                try {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        throw SecurityException("기기 오디오 권한이 취소되었습니다")
                    }
                    record = AudioRecord.Builder().setAudioPlaybackCaptureConfig(configuration)
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(48_000)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build())
                        .setBufferSizeInBytes(maxOf(19_200, AudioRecord.getMinBufferSize(48_000,
                            AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT))).build()
                    check(record.state == AudioRecord.STATE_INITIALIZED)
                    captureRecord = record
                    ensureMusicCurrent(generation)
                    encoder = OpusMusicEncoder(socket::send)
                    record.startRecording()
                    val pcm = ByteArray(OpusMusicEncoder.FRAME_BYTES)
                    var silentFrames = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = record.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                        check(count > 0) { "기기 오디오 캡처가 중단되었습니다" }
                        if (pcm.take(count).all { it == 0.toByte() }) silentFrames++ else silentFrames = 0
                        if (silentFrames == 250 && generation == musicGeneration)
                            mutableStatus.update { it.copy(message = "5초 이상 무음 · 재생 중인지 또는 앱 캡처 차단인지 확인해 주세요") }
                        encoder.write(pcm.copyOf(count), SystemClock.elapsedRealtimeNanos() / 1000)
                    }
                } catch (failure: Exception) {
                    if (failure !is CancellationException) scope.launch {
                        if (generation == musicGeneration)
                            failMusic("선택한 앱 오디오를 캡처하지 못했습니다. 동의·재생 상태·앱의 캡처 허용을 확인해 주세요")
                    }
                } finally {
                    runCatching { record?.stop() }
                    if (captureRecord === record) captureRecord = null
                    runCatching { record?.release() }
                    runCatching { encoder?.close() }
                    runCatching { capture.unregisterCallback(callback) }
                    scope.launch { if (generation == musicGeneration) stopMusic() }
                }
            }
        } catch (failure: Exception) {
            if (generation != musicGeneration) {
                if (failure is CancellationException) throw failure
                return
            }
            stopMusic()
            if (failure is CancellationException) throw failure
            failMusic(failure.message ?: "외부 음악 캡처를 시작하지 못했습니다")
        }
    }

    private suspend fun prepareBridge(url: String, token: String, generation: Long): MusicBridgeSocket {
        require(url.isNotBlank() && token.isNotBlank()) { "음악 송출 서버가 아직 설정되지 않았습니다" }
        require(status.value.connection == AudioConnection.CONNECTED) { "음성방에 먼저 연결해 주세요" }
        val lease = BridgeLease.readUnverified(token)
        require(lease.room == status.value.roomName) { "현재 음성방의 DJ 송출 권한이 필요합니다" }
        activeLeaseId = lease.leaseId
        val socket = MusicBridgeSocket(
            onMetrics = { bytes, queued -> if (generation == musicGeneration)
                mutableStatus.update { it.copy(bytesSent = bytes, queuedBytes = queued) } },
            onStopped = { problem -> scope.launch {
                if (generation == musicGeneration) { stopMusic(); failMusic(problem) }
            } })
        bridge = socket
        selfMusicOwner = lease.owner
        socket.open(url, token, lease)
        ensureMusicCurrent(generation)
        return socket
    }

    private suspend fun ensureMusicCurrent(generation: Long) {
        currentCoroutineContext().ensureActive()
        if (generation != musicGeneration) throw CancellationException("Music source changed")
    }

    private fun requireMicrophonePermission() {
        require(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "마이크/기기 오디오 권한이 필요합니다"
        }
    }

    private fun failMusic(message: String) { mutableStatus.update { it.copy(error = message, message = message) } }

    private suspend fun updateForeground() {
        val state = status.value
        val connected = state.connection in listOf(AudioConnection.CONNECTED, AudioConnection.RECONNECTING)
        val types = AudioProjectionService.types(state.microphoneEnabled,
            state.musicSource == MusicSource.EXTERNAL_APP, connected || state.musicSource == MusicSource.FILE)
        if (types == 0) AudioProjectionService.stop(context) else AudioProjectionService.ensureStarted(context, types)
    }

    private fun applyVolumes() {
        val state = status.value
        receivers.values.forEach { receiver ->
            val volume = if (!receiver.music) state.voiceVolume else
                if (state.musicSending && receiver.owner == selfMusicOwner) 0f else state.musicVolume
            runCatching { receiver.track.setVolume(volume.toDouble()) }
        }
    }

    fun stopMusic() {
        val stoppedLeaseId = activeLeaseId
        activeLeaseId = null
        musicGeneration++
        musicJob?.cancel()
        musicJob = null
        filePump?.stop()
        runCatching { captureRecord?.stop() }
        projection?.let { projection = null; runCatching { it.stop() } }
        bridge?.close()
        bridge = null
        if (stoppedLeaseId != null) onBroadcastStopped?.invoke(stoppedLeaseId)
        filePump = null
        selfMusicOwner = null
        mutableStatus.update { it.copy(musicSource = MusicSource.NONE, musicSending = false,
            musicSourceLabel = "", queuedBytes = 0) }
        applyVolumes()
        scope.launch { runCatching { updateForeground() } }
    }

    suspend fun disconnect() {
        connectionGeneration++
        stopMusic()
        eventsJob?.cancel()
        eventsJob = null
        liveRoom?.let { room -> runCatching { room.disconnect() }; room.release() }
        liveRoom = null
        receivers.clear()
        mutableStatus.update { it.copy(connection = AudioConnection.DISCONNECTED, roomName = "", microphoneEnabled = false,
            message = "음성 연결 종료") }
        AudioProjectionService.stop(context)
    }

    override fun close() {
        connectionGeneration++
        AudioProjectionService.onUserStop = null
        stopMusic()
        eventsJob?.cancel()
        liveRoom?.disconnect()
        liveRoom?.release()
        liveRoom = null
        AudioProjectionService.stop(context)
        scope.cancel()
    }
}

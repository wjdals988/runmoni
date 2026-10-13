package com.runmoni.app.audio

enum class AudioConnection { DISCONNECTED, NOT_CONFIGURED, CONNECTING, CONNECTED, RECONNECTING, ERROR }
enum class MusicSource { NONE, FILE, EXTERNAL_APP }

data class AudioStatus(
    val connection: AudioConnection = AudioConnection.DISCONNECTED,
    val roomName: String = "",
    val microphoneEnabled: Boolean = false,
    val voiceVolume: Float = 1f,
    val musicVolume: Float = 0.7f,
    val musicSource: MusicSource = MusicSource.NONE,
    val musicSending: Boolean = false,
    val musicSourceLabel: String = "",
    val bytesSent: Long = 0,
    val queuedBytes: Long = 0,
    val message: String = "음성 연결 전",
    val error: String? = null,
)

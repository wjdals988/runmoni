package com.runmoni.app.domain

enum class RunPhase { IDLE, RUNNING, PAUSED, RECOVERY }
enum class PartyKind { LOCAL, REMOTE }

data class TrackPoint(
    val sequence: Int,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val recordedAtMillis: Long,
    val segment: Int = 0,
)
data class KmSplit(val kilometer: Int, val durationMillis: Long)
data class RunRecord(
    val id: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long = 0,
    val distanceMeters: Double = 0.0,
    val activeDurationMillis: Long = 0,
    val elapsedDurationMillis: Long = 0,
    val points: List<TrackPoint> = emptyList(),
    val splits: List<KmSplit> = emptyList(),
    val synced: Boolean = false,
    val interrupted: Boolean = false,
    val mileage: Int = 0,
)
data class ActiveRun(
    val id: String = "",
    val phase: RunPhase = RunPhase.IDLE,
    val startedAtMillis: Long = 0,
    val distanceMeters: Double = 0.0,
    val activeDurationMillis: Long = 0,
    val elapsedDurationMillis: Long = 0,
    val currentPaceSecondsPerKm: Int? = null,
    val points: List<TrackPoint> = emptyList(),
    val gpsStatus: String = "GPS 준비 전",
    val accuracyMeters: Float? = null,
)
data class AvatarStyle(
    val hair: Int = 0, val top: Int = 0, val bottom: Int = 0, val shoes: Int = 0,
    val accessory: Int = 0, val eyewear: Int = 0, val wrist: Int = 0,
    val waist: Int = 0, val hydration: Int = 0,
    val face: Int = 0, val socks: Int = 0, val headwear: Int? = null,
    val ear: Int? = null, val costume: Int = 0,
)
data class Profile(val name: String = "러너", val avatar: AvatarStyle = AvatarStyle(), val mileage: Int = 0, val ownedItems: Set<String> = AvatarCatalogue.defaultOwned)
data class Crew(
    val id: String,
    val name: String,
    val description: String,
    val region: String = "",
    val paceLabel: String = "자유 페이스",
    val memberCount: Int = 0,
    val joined: Boolean = false,
    val isOwner: Boolean = false,
    val isDj: Boolean = false,
    val invitationCode: String? = null,
)
data class CrewMember(val id: String, val name: String, val isOwner: Boolean = false, val isDj: Boolean = false)
data class Party(
    val id: String,
    val crewId: String,
    val title: String,
    val kind: PartyKind,
    val startsAtMillis: Long,
    val meetingLabel: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val distanceKm: Double = 5.0,
    val paceLabel: String = "자유 페이스",
    val capacity: Int = 20,
    val participantCount: Int = 0,
    val joined: Boolean = false,
    val cancelled: Boolean = false,
    val isOrganizer: Boolean = false,
)
data class ChatMessage(val id: String, val roomId: String, val author: String, val body: String, val createdAtMillis: Long, val notice: Boolean = false, val mine: Boolean = false)
data class LiveRunner(val id: String, val name: String, val latitude: Double, val longitude: Double, val updatedAtMillis: Long, val avatar: AvatarStyle = AvatarStyle(), val partyId: String? = null, val memberCount: Int = 1)
data class AudioUiState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val microphoneEnabled: Boolean = false,
    val broadcasting: Boolean = false,
    val sourceLabel: String = "음악을 선택해 주세요",
    val status: String = "음성 서비스 설정 필요",
    val voiceVolume: Float = 1f,
    val musicVolume: Float = 0.7f,
    val canDj: Boolean = false,
    val roomId: String = "",
)
data class AppState(
    val profile: Profile = Profile(),
    val activeRun: ActiveRun = ActiveRun(),
    val records: List<RunRecord> = emptyList(),
    val crews: List<Crew> = emptyList(),
    val crewMembers: Map<String, List<CrewMember>> = emptyMap(),
    val parties: List<Party> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val liveRunners: List<LiveRunner> = emptyList(),
    val audio: AudioUiState = AudioUiState(),
    val cloud: CloudAccountState = CloudAccountState(),
    val apiUrl: String = "http://127.0.0.1:8787",
    val serverConnected: Boolean = false,
    val signedIn: Boolean = false,
    val serverStatus: String = "기기 내 기록 모드",
    val publicLocation: Boolean = false,
    val autoPause: Boolean = false,
    val voiceGuidance: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val openedRoomId: String = "",
    val openedRoomTitle: String = "",
)
data class NewCrew(val name: String, val description: String, val region: String, val paceLabel: String)
data class NewParty(val crewId: String, val title: String, val kind: PartyKind, val startsAtMillis: Long, val meetingLabel: String, val distanceKm: Double, val paceLabel: String, val capacity: Int, val latitude: Double? = null, val longitude: Double? = null)

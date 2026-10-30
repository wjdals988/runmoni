package com.runmoni.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.runmoni.app.audio.AudioConnection
import com.runmoni.app.audio.AudioController
import com.runmoni.app.data.*
import com.runmoni.app.data.PilotClient.Companion.toCrew
import com.runmoni.app.data.PilotClient.Companion.toParty
import com.runmoni.app.data.PilotClient.Companion.toProfile
import com.runmoni.app.domain.*
import com.runmoni.app.tracking.RunTrackingService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed interface UiRequest {
    data class RunPermission(val recover: Boolean) : UiRequest
    data class MicrophonePermission(val roomId: String? = null) : UiRequest
    data object PickMusic : UiRequest
    data object GoogleSignIn : UiRequest
    data object LocationSettings : UiRequest
    data object AppPermissionSettings : UiRequest
    data class ExternalCapture(val packageName: String) : UiRequest
    data class ShareFile(val uri: Uri) : UiRequest
}

class AppViewModel(application: Application) : AndroidViewModel(application), AppActions {
    private val app = application as RunmoniApplication
    private val preferences = app.getSharedPreferences("runmoni", Context.MODE_PRIVATE)
    private val session = SecureSession(app)
    private val wallet = LocalWallet(app)
    private val api = PilotClient(preferences.getString("apiUrl", BuildConfig.PILOT_API_URL)!!, session.read())
    private val audio = AudioController(app)
    private var currentUserId = preferences.getString("userId", "").orEmpty()
    private var selectedVoiceRoom = ""
    private var leaseId: String? = null
    private var pendingExternalLease: JSONObject? = null
    private var pendingOperations = 0
    private val communityRefresh = Mutex()
    private val mutableState = MutableStateFlow(AppState(profile = readLocalProfile(), apiUrl = api.baseUrl, signedIn = api.token.isNotBlank(), publicLocation = preferences.getBoolean("publicLocation", false), autoPause = preferences.getBoolean("autoPause", false), voiceGuidance = preferences.getBoolean("voiceGuidance", true), communityGuest = preferences.getBoolean("communityGuest", false)))
    val state: StateFlow<AppState> = mutableState.asStateFlow()
    val requests = MutableSharedFlow<UiRequest>(extraBufferCapacity = 8)

    init {
        audio.onBroadcastStopped = { oldLeaseId ->
            val oldClient = PilotClient(api.baseUrl, api.token)
            if (leaseId == oldLeaseId) leaseId = null
            viewModelScope.launch { runCatching { oldClient.request("DELETE", "/v1/music/lease/$oldLeaseId") } }
        }
        viewModelScope.launch { app.runs.active.collect { active -> mutableState.update { it.copy(activeRun = active, publicLocation = preferences.getBoolean("publicLocation", false)) } } }
        viewModelScope.launch { app.cloud.state.collect { cloud -> mutableState.update { it.copy(cloud = cloud) } } }
        viewModelScope.launch { app.runs.records.collect { records -> mutableState.update { it.copy(records = records, profile = if (api.token.isBlank() || it.communityGuest) wallet.read() else it.profile) } } }
        viewModelScope.launch { app.runs.lastFinished.filterNotNull().collect { record ->
            if (api.token.isBlank() || state.value.communityGuest) {
                mutableState.update { it.copy(profile = wallet.read()) }
            } else { RunSyncWorker.enqueue(app); refreshCommunity() }
        } }
        viewModelScope.launch { audio.status.collect { status ->
            mutableState.update { previous -> previous.copy(audio = previous.audio.copy(connected = status.connection == AudioConnection.CONNECTED, connecting = status.connection in setOf(AudioConnection.CONNECTING, AudioConnection.RECONNECTING), microphoneEnabled = status.microphoneEnabled, broadcasting = status.musicSending, sourceLabel = status.musicSourceLabel.ifBlank { "음악을 선택해 주세요" }, status = status.error ?: status.message, voiceVolume = status.voiceVolume, musicVolume = status.musicVolume)) }
        } }
        viewModelScope.launch {
            while (isActive) {
                if (api.token.isNotBlank()) runCatching { refreshNow() }.onFailure { mutableState.update { it.copy(serverConnected = false, serverStatus = "서버 연결 안 됨 · 기기 기록 계속 가능") } }
                delay(15_000)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                val room = state.value.openedRoomId
                if (room.isNotBlank() && api.token.isNotBlank()) runCatching { api.chat(room) }.onSuccess { messages -> mutableState.update { if (it.openedRoomId == room) it.copy(messages = messages) else it } }
                delay(3000)
            }
        }
    }
    private fun work(block: suspend () -> Unit) = viewModelScope.launch {
        pendingOperations++
        mutableState.update { it.copy(busy = true, error = null) }
        try { block() } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            mutableState.update { it.copy(error = failure.message ?: "작업을 완료하지 못했습니다. 연결 상태를 확인해 주세요.") }
        } finally { pendingOperations--; mutableState.update { it.copy(busy = pendingOperations > 0) } }
    }
    fun permissionDenied(message: String) { mutableState.update { it.copy(error = message) } }
    fun updateLocationAccess(precisePermissionGranted: Boolean, gpsEnabled: Boolean) {
        mutableState.update { it.copy(locationAccess = LocationAccess(precisePermissionGranted, gpsEnabled)) }
    }
    fun beginWithPermission(recover: Boolean) { RunTrackingService.command(app, if (recover) RunTrackingService.RECOVER else RunTrackingService.START) }
    override fun startRun() { requests.tryEmit(UiRequest.RunPermission(false)) }
    override fun recoverRun() { requests.tryEmit(UiRequest.RunPermission(true)) }
    override fun openLocationSettings() { requests.tryEmit(UiRequest.LocationSettings) }
    override fun openAppPermissionSettings() { requests.tryEmit(UiRequest.AppPermissionSettings) }
    override fun pauseRun() { RunTrackingService.command(app, RunTrackingService.PAUSE) }
    override fun resumeRun() { RunTrackingService.command(app, RunTrackingService.RESUME) }
    override fun finishRun() { RunTrackingService.command(app, RunTrackingService.FINISH) }
    override fun dismissError() { mutableState.update { it.copy(error = null) } }
    override fun setAutoPause(enabled: Boolean) { preferences.edit().putBoolean("autoPause", enabled).apply(); mutableState.update { it.copy(autoPause = enabled) } }
    override fun setVoiceGuidance(enabled: Boolean) { preferences.edit().putBoolean("voiceGuidance", enabled).apply(); mutableState.update { it.copy(voiceGuidance = enabled) } }
    override fun setPublicLocation(enabled: Boolean) {
        if (enabled && state.value.communityGuest) { permissionDenied("현재 방 프로필에서는 실시간 위치 공유를 사용할 수 없어요."); return }
        if (enabled && api.token.isBlank()) { permissionDenied("초대코드로 로그인한 뒤 위치 공유를 켜 주세요."); return }
        preferences.edit().putBoolean("publicLocation", enabled).apply()
        mutableState.update { it.copy(publicLocation = enabled) }
        if (!enabled && api.token.isNotBlank()) work { api.request("DELETE", "/v1/presence"); refreshNow() }
    }
    override fun updateProfile(name: String) = ignoreJob(work {
        require(name.trim().length in 1..30) { "이름은 1~30자로 입력해 주세요." }
        if (api.token.isNotBlank()) { api.request("PATCH", "/v1/me", JSONObject().put("nickname", name.trim())); if (state.value.communityGuest) mutateLocalProfile { it.copy(name = name.trim()) }; refreshNow() }
        else mutateLocalProfile { it.copy(name = name.trim()) }
    })
    override fun equipItem(slot: String, variant: Int) = ignoreJob(work {
        val item = "$slot:$variant"
        require(item in state.value.profile.ownedItems) { "먼저 아이템을 구매해 주세요." }
        val avatar = state.value.profile.avatar.with(slot, variant)
        if (api.token.isNotBlank() && !state.value.communityGuest) { api.request("PATCH", "/v1/me", JSONObject().put("avatar", avatar.toJson())); refreshNow() }
        else mutateLocalProfile { it.copy(avatar = it.avatar.with(slot, variant)) }
    })
    override fun buyItem(slot: String, variant: Int) = ignoreJob(work {
        require(AvatarCatalogue.valid(slot, variant)) { "올바른 아이템을 선택해 주세요." }
        val item = "$slot:$variant"
        if (item in state.value.profile.ownedItems) return@work
        if (api.token.isNotBlank() && !state.value.communityGuest) { api.request("POST", "/v1/shop/purchase", JSONObject().put("item_id", item)); refreshNow() }
        else {
            val cost = AvatarCatalogue.price(variant)
            mutateLocalProfile { current ->
                if (item in current.ownedItems) current else {
                    require(current.mileage >= cost) { "마일리지가 부족합니다. 러닝 기록을 쌓아 주세요." }
                    current.copy(mileage = current.mileage - cost, ownedItems = current.ownedItems + item)
                }
            }
        }
    })
    override fun deleteRun(id: String) = ignoreJob(work {
        val record = app.runs.record(id)
        if (api.token.isNotBlank()) try { api.request("DELETE", "/v1/runs/$id") } catch (failure: PilotException) { if (failure.status != 404 || record?.synced == true) throw failure }
        app.runs.delete(id)
    })
    override fun exportRun(id: String) = ignoreJob(work {
        val record = app.runs.record(id) ?: error("기록이 없습니다.")
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val gpx = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"Runmoni\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>Runmoni run</name>")
            record.points.groupBy { it.segment }.values.forEach { points ->
                append("<trkseg>"); points.forEach { append("<trkpt lat=\"${it.latitude}\" lon=\"${it.longitude}\"><time>${formatter.format(Date(it.recordedAtMillis))}</time></trkpt>") }; append("</trkseg>")
            }; append("</trk></gpx>")
        }
        val file = File(app.cacheDir, "exports/run-${record.startedAtMillis}.gpx")
        withContext(Dispatchers.IO) { file.parentFile!!.mkdirs(); file.writeText(gpx) }
        requests.emit(UiRequest.ShareFile(FileProvider.getUriForFile(app, "${BuildConfig.APPLICATION_ID}.files", file)))
    })
    override fun configureServer(url: String) {
        runCatching {
            val uri = URI(url.trim())
            require(uri.userInfo == null && uri.query == null && uri.fragment == null && uri.host != null && uri.scheme in setOf("http", "https")) { "올바른 서버 주소를 입력해 주세요." }
            require(uri.scheme == "https" || uri.host in setOf("127.0.0.1", "localhost", "10.0.2.2")) { "로컬 주소 외에는 HTTPS 주소를 사용해 주세요." }
            if (api.baseUrl != url.trim().trimEnd('/')) {
                val previousClient = PilotClient(api.baseUrl, api.token)
                val previousLease = leaseId
                viewModelScope.launch {
                    runCatching { previousClient.request("DELETE", "/v1/presence") }
                    previousLease?.let { runCatching { previousClient.request("DELETE", "/v1/music/lease/$it") } }
                }
                leaseId = null; selectedVoiceRoom = ""
                api.token = ""; session.write(""); currentUserId = ""; preferences.edit().remove("userId").putBoolean("communityGuest", false).apply()
                audio.stopMusic(); viewModelScope.launch { audio.disconnect() }
                api.baseUrl = url.trim().trimEnd('/'); preferences.edit().putString("apiUrl", api.baseUrl).apply()
                preferences.edit().putBoolean("publicLocation", false).apply()
                mutableState.update { it.copy(apiUrl = api.baseUrl, signedIn = false, communityGuest = false, serverConnected = false, publicLocation = false, profile = readLocalProfile(), crews = emptyList(), parties = emptyList(), messages = emptyList(), liveRunners = emptyList(), crewMembers = emptyMap(), openedRoomId = "", openedRoomTitle = "", audio = it.audio.copy(roomId = "", canDj = false)) }
            }
        }.onFailure { permissionDenied(it.message ?: "서버 주소를 확인해 주세요.") }.onSuccess { refreshCommunity() }
    }
    override fun connectCommunity(name: String) = ignoreJob(work {
        val result = api.connectCommunity(name.trim())
        RunSyncWorker.cancel(app)
        preferences.edit().putBoolean("communityGuest", true).putBoolean("publicLocation", false).apply()
        api.token = result.getString("access_token"); session.write(api.token)
        currentUserId = result.getJSONObject("user").getString("id")
        preferences.edit().putString("userId", currentUserId).apply()
        mutateLocalProfile { it.copy(name = name.trim()) }
        mutableState.update { it.copy(signedIn = true, communityGuest = true, publicLocation = false) }
        refreshNow()
    })
    override fun signIn(inviteCode: String, name: String) = ignoreJob(work {
        val result = api.login(inviteCode.trim(), name.trim())
        preferences.edit().putBoolean("communityGuest", false).apply()
        api.token = result.getString("access_token"); session.write(api.token)
        currentUserId = result.getJSONObject("user").getString("id"); preferences.edit().putString("userId", currentUserId).apply()
        mutableState.update { it.copy(signedIn = true, communityGuest = false) }; refreshNow(); RunSyncWorker.enqueue(app)
    })
    override fun signOut() = ignoreJob(work {
        runCatching { api.request("DELETE", "/v1/presence") }; runCatching { api.request("DELETE", "/v1/auth/session") }
        audio.disconnect(); api.token = ""; session.write(""); currentUserId = ""; preferences.edit().remove("userId").putBoolean("publicLocation", false).putBoolean("communityGuest", false).apply()
        mutableState.update { it.copy(signedIn = false, communityGuest = false, serverConnected = false, profile = readLocalProfile(), publicLocation = false, crews = emptyList(), parties = emptyList(), messages = emptyList(), crewMembers = emptyMap(), liveRunners = emptyList(), openedRoomId = "", openedRoomTitle = "", serverStatus = "기기 내 기록 모드") }
    })
    override fun signInGoogle() { requests.tryEmit(UiRequest.GoogleSignIn) }
    suspend fun googleSignIn(context: Context) {
        try { app.cloud.signIn(context) } catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { permissionDenied("Google 로그인에 실패했습니다. 클라우드 연결 설정을 확인해 주세요.") }
    }
    override fun signOutGoogle() = ignoreJob(work { app.cloud.signOut(app) })
    override fun backupCloudRun(runId: String, expectedUid: String) = ignoreJob(work {
        val record = app.runs.record(runId) ?: error("기기 기록이 없습니다.")
        app.cloud.backupRun(record, expectedUid)
    })
    override fun refreshCloudSummaries(expectedUid: String) = ignoreJob(work { app.cloud.refreshSummaries(expectedUid) })
    override fun deleteCloudSummary(runId: String, expectedUid: String) = ignoreJob(work { app.cloud.deleteSummary(runId, expectedUid) })
    override fun refreshCommunity() = ignoreJob(work {
        try { refreshNow() } catch (failure: Exception) {
            mutableState.update { it.copy(serverConnected = false, serverStatus = "방 목록을 불러오지 못했어요. 연결 후 다시 시도해 주세요.") }
            if (failure is CancellationException) throw failure
        }
    })
    private suspend fun refreshNow() = communityRefresh.withLock {
        val expectedToken = api.token
        val expectedUrl = api.baseUrl
        val health = api.health()
        check(health.optJSONObject("capabilities")?.optBoolean("public_rooms") == true) { "방 목록을 지원하는 서버가 필요해요. 서버 연결을 확인해 주세요." }
        val identity = if (expectedToken.isBlank()) null else api.request("GET", "/v1/me")
        val guest = identity?.optString("auth_mode") == "guest"
        val profile = if (identity == null || guest) wallet.read() else identity.toProfile()
        val crews = api.crews()
        val parties = api.parties()
        val members = crews.filter { it.joined }.associate { it.id to api.members(it.id) }
        val presence = if (identity != null && !guest) api.presence() else emptyList()
        val voice = health.optJSONObject("capabilities")?.optBoolean("voice") ?: false
        if (expectedToken != api.token || expectedUrl != api.baseUrl) return@withLock
        if (identity != null) preferences.edit().putBoolean("communityGuest", guest).apply()
        val voiceParty = parties.find { "party:${it.id}" == selectedVoiceRoom && it.joined && !it.cancelled }
        val voiceCrew = if (voiceParty != null) crews.find { it.joined && it.id == voiceParty.crewId }
            else crews.find { "crew:${it.id}" == selectedVoiceRoom && it.joined }
        if (selectedVoiceRoom.isNotBlank() && voiceCrew == null && voiceParty == null) {
            stopLease(); audio.disconnect(); selectedVoiceRoom = ""
        }
        val canDj = voiceParty?.isOrganizer == true || voiceCrew?.let { it.isOwner || it.isDj } == true
        if (!canDj && audio.status.value.musicSending) { audio.stopMusic(); stopLease() }
        mutableState.update { it.copy(profile = profile, crews = crews, parties = parties, crewMembers = members, liveRunners = presence, serverConnected = true, signedIn = identity != null, communityGuest = guest, serverStatus = "방 목록을 불러왔어요", audio = it.audio.copy(roomId = selectedVoiceRoom, canDj = canDj, status = if (!voice && !it.audio.connected) "음성·방송 서버 설정 필요" else it.audio.status)) }
    }
    private suspend fun refreshAfterCreation() {
        runCatching { refreshNow() }.onFailure {
            if (it is CancellationException) throw it
            mutableState.update { it.copy(serverConnected = false, serverStatus = "방은 만들었지만 목록 갱신을 못했어요. 새로고침해 주세요.") }
        }
    }
    override fun createCrew(input: NewCrew) = ignoreJob(work {
        val crew = api.createCrew(input).toCrew(true)
        mutableState.update { it.copy(crews = listOf(crew) + it.crews.filterNot { old -> old.id == crew.id }, lastCreatedCrewId = crew.id) }
        refreshAfterCreation()
    })
    override fun joinCrew(id: String) = ignoreJob(work { api.request("POST", "/v1/crews/$id/join"); refreshNow() })
    override fun leaveCrew(id: String) = ignoreJob(work { api.request("DELETE", "/v1/crews/$id/join"); refreshNow() })
    override fun setCrewDj(crewId: String, memberId: String, enabled: Boolean) = ignoreJob(work { api.request("PATCH", "/v1/crews/$crewId/members/$memberId", JSONObject().put("role", if (enabled) "dj" else "member")); refreshNow() })
    override fun createParty(input: NewParty) = ignoreJob(work {
        val party = api.createParty(input).toParty()
        mutableState.update { it.copy(parties = listOf(party) + it.parties.filterNot { old -> old.id == party.id }, lastCreatedPartyId = party.id) }
        refreshAfterCreation()
    })
    override fun joinParty(id: String) = ignoreJob(work { api.request("POST", "/v1/parties/$id/join"); refreshNow() })
    override fun leaveParty(id: String) = ignoreJob(work { api.request("DELETE", "/v1/parties/$id/join"); refreshNow() })
    override fun cancelParty(id: String) = ignoreJob(work { api.request("POST", "/v1/parties/$id/cancel"); refreshNow() })
    override fun openChat(roomId: String, title: String) {
        mutableState.update { it.copy(openedRoomId = roomId, openedRoomTitle = title, messages = emptyList()) }
        work { val messages = api.chat(roomId); mutableState.update { if (it.openedRoomId == roomId) it.copy(messages = messages) else it } }
    }
    override fun closeChat() { mutableState.update { it.copy(openedRoomId = "", openedRoomTitle = "", messages = emptyList()) } }
    override fun sendMessage(body: String, notice: Boolean) = ignoreJob(work {
        val roomId = state.value.openedRoomId
        require(roomId.contains(':')) { "채팅방을 선택해 주세요." }
        val (scope, id) = roomId.split(":", limit = 2)
        api.request("POST", "/v1/${if (scope == "crew") "crews" else "parties"}/$id/messages", JSONObject().put("body", body.trim()).put("kind", if (notice) "announcement" else "chat"))
        val messages = api.chat(roomId); mutableState.update { it.copy(messages = messages) }
    })
    override fun connectVoice(roomId: String) { requests.tryEmit(UiRequest.MicrophonePermission(roomId)) }
    fun connectVoiceWithPermission(roomId: String) = work {
        val token = api.request("POST", "/v1/voice/token", roomScope(roomId))
        selectedVoiceRoom = roomId
        val crew = if (roomId.startsWith("crew:")) state.value.crews.find { it.id == roomId.substringAfter(':') } else state.value.crews.find { c -> c.id == state.value.parties.find { it.id == roomId.substringAfter(':') }?.crewId }
        mutableState.update { it.copy(audio = it.audio.copy(roomId = roomId, canDj = crew?.let { c -> c.isOwner || c.isDj } ?: false)) }
        audio.connect(token.getString("server_url"), token.getString("token"), token.getString("room_name"))
    }
    override fun disconnectVoice() = ignoreJob(work { stopLease(); audio.disconnect(); selectedVoiceRoom = ""; mutableState.update { it.copy(audio = it.audio.copy(roomId = "", canDj = false)) } })
    override fun setMicrophone(enabled: Boolean) { if (enabled) requests.tryEmit(UiRequest.MicrophonePermission()) else work { audio.setMicrophoneEnabled(false) } }
    fun enableMicrophoneWithPermission() = work { audio.setMicrophoneEnabled(true) }
    override fun setVoiceVolume(value: Float) { audio.setVoiceVolume(value) }
    override fun setMusicVolume(value: Float) { audio.setMusicVolume(value) }
    override fun selectMusicFile() { requests.tryEmit(UiRequest.PickMusic) }
    fun musicFileSelected(uri: Uri) = work {
        if (selectedVoiceRoom.isNotBlank() && state.value.audio.canDj) {
            val lease = acquireLease(); audio.startMusicFile(uri, lease.getString("bridge_url"), lease.getString("token"))
        } else audio.playLocalMusicFile(uri)
    }
    override fun shareExternalMusic(packageName: String) = ignoreJob(work {
        require(selectedVoiceRoom.isNotBlank() && state.value.audio.canDj) { "크루장 또는 DJ로 음성방에 연결한 뒤 음악을 공유해 주세요." }
        try { app.packageManager.getApplicationInfo(packageName, 0) } catch (_: Exception) { error("선택한 음악 앱이 설치되어 있지 않습니다.") }
        pendingExternalLease = acquireLease(); requests.emit(UiRequest.ExternalCapture(packageName))
    })
    fun captureIntent(): Intent = audio.createExternalCaptureIntent()
    fun captureResult(resultCode: Int, data: Intent?, packageName: String) = work {
        val lease = pendingExternalLease; pendingExternalLease = null
        if (data == null || resultCode != android.app.Activity.RESULT_OK || lease == null) { stopLease(); return@work }
        audio.startExternalCapture(resultCode, data, packageName, lease.getString("bridge_url"), lease.getString("token"))
    }
    override fun stopMusic() { audio.stopMusic(); work { stopLease() } }
    private suspend fun acquireLease(): JSONObject {
        stopLease()
        val body = roomScope(selectedVoiceRoom).put("pilot_test_acknowledged", true)
        val lease = api.request("POST", "/v1/music/lease", body)
        leaseId = lease.getString("lease_id")
        return lease
    }
    private suspend fun stopLease() { leaseId?.let { id -> runCatching { api.request("DELETE", "/v1/music/lease/$id") } }; leaseId = null }
    private fun roomScope(room: String): JSONObject { val (scope, id) = room.split(":", limit = 2); return JSONObject().put("scope_type", scope).put("scope_id", id) }
    private fun readLocalProfile(): Profile = wallet.read()
    private fun mutateLocalProfile(transform: (Profile) -> Profile) { val profile = wallet.update(transform); mutableState.update { it.copy(profile = profile) } }
    private fun AvatarStyle.toJson() = PilotClient.encodeAvatar(this)
    private fun AvatarStyle.with(slot: String, variant: Int) = withItem(slot, variant)
    private fun ignoreJob(job: Job) = Unit
    override fun onCleared() { audio.close(); super.onCleared() }
}

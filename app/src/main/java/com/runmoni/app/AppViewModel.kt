package com.runmoni.app

import com.runmoni.app.data.PilotClient.Companion.items

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.edit
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
    data class PreviewLocation(val weather: Boolean) : UiRequest
    data object ResumeRunPermission : UiRequest
    data object RunNotificationSettings : UiRequest
    data object PinRunWidget : UiRequest
    data class MicrophonePermission(val roomId: String? = null) : UiRequest
    data object PickMusic : UiRequest
    data object GoogleSignIn : UiRequest
    data class EntryGoogleSignIn(val token: Long) : UiRequest
    data object LinkCommunityGoogle : UiRequest
    data object RestoreCommunityGoogle : UiRequest
    data object LocationSettings : UiRequest
    data object AppPermissionSettings : UiRequest
    data class ExternalCapture(val packageName: String) : UiRequest
    data class ShareFile(val uri: Uri) : UiRequest
    data class ShareText(val text: String) : UiRequest
}

class AppViewModel(application: Application) : AndroidViewModel(application), AppActions {
    private val app = application as RunmoniApplication
    private val preferences = app.getSharedPreferences("runmoni", Context.MODE_PRIVATE)
    // Snapshot the local entry choice before opening Room or creating new local state.
    private val entryStore = AppEntryStore(app)
    private val entry = AppEntryController(entryStore.initialChoice(),
        persistChoice = { choice -> withContext(Dispatchers.IO) { entryStore.save(choice) } },
        onRememberFailure = { permissionDenied("선택을 기기에 저장하지 못했어요. 이번에는 계속 이용할 수 있지만 다음 실행에서 다시 선택할 수 있어요.") })
    private var entryGoogleJob: Job? = null
    private var pendingEntryGoogleToken: Long? = null
    private val session = SecureSession(app)
    private val wallet = LocalWallet(app)
    private val api = PilotClient(preferences.getString("apiUrl", BuildConfig.PILOT_API_URL)!!, session.read())
    private val firebaseRooms = app.community
    private val weatherLocations = WeatherLocationRepository(app)
    private val weatherRepository = RunningWeatherRepository(app)
    private val areaRepository = CurrentAreaRepository(app)
    private var weatherJob: Job? = null
    private var areaJob: Job? = null
    private var areaOperation = 0L
    private var profileOperation = 0L
    private var profileConnectOperation = 0L
    private var profileConnectJob: Job? = null
    private var nicknameCheckJob: Job? = null
    private var nicknameCheckOperation = 0L
    private var discoveryJob: Job? = null
    private var discoveryBounds: RouteMapBounds? = null
    private var discoveryVisible = false
    private var firebaseMode = preferences.getString("communityBackend", BuildConfig.COMMUNITY_BACKEND) != "http"
    private var communityGeneration = 0L
    private val audio = AudioController(app)
    private var currentUserId = preferences.getString("userId", "").orEmpty()
    private var selectedVoiceRoom = ""
    private var leaseId: String? = null
    private var pendingExternalLease: JSONObject? = null
    private var runNavigationSerial = android.os.SystemClock.elapsedRealtimeNanos()
    private var pendingOperations = 0
    private val communityRefresh = Mutex()
    private val wardrobeChanges = Mutex()
    private var focusedCrewForParties: String? = null
    private var connectionCheckGeneration = 0L
    private val mutableState = MutableStateFlow(AppState(entry = entry.state.value, profile = readLocalProfile(), runPersistence = app.runs.persistence.value, apiUrl = api.baseUrl, signedIn = !firebaseMode && api.token.isNotBlank(), publicLocation = !firebaseMode && preferences.getBoolean("publicLocation", false), autoPause = preferences.getBoolean("autoPause", false), voiceGuidance = preferences.getBoolean("voiceGuidance", true), communityGuest = firebaseMode || preferences.getBoolean("communityGuest", false), communityBackend = if (firebaseMode) CommunityBackend.FIREBASE else CommunityBackend.HTTP))
    val state: StateFlow<AppState> = mutableState.asStateFlow()
    val requests = MutableSharedFlow<UiRequest>(extraBufferCapacity = 8)

    init {
        viewModelScope.launch { app.runnerSharing.state.collect { sharing ->
            mutableState.update { it.copy(sharing = sharing, publicLocation = if (firebaseMode) sharing.enabled else it.publicLocation) }
        } }
        viewModelScope.launch { entry.state.collect { selection -> mutableState.update { it.copy(entry = selection) } } }
        if (firebaseMode) RunSyncWorker.cancel(app)
        val reported = preferences.getStringSet("phoneChecks.${BuildConfig.VERSION_CODE}", emptySet()).orEmpty()
        mutableState.update { it.copy(deviceChecks = DeviceChecks(selfReported = PhoneCheck.entries.filter { check -> check.name in reported }.toSet())) }
        audio.onBroadcastStopped = { oldLeaseId ->
            val oldClient = PilotClient(api.baseUrl, api.token)
            if (leaseId == oldLeaseId) leaseId = null
            viewModelScope.launch { runCatching { oldClient.request("DELETE", "/v1/music/lease/$oldLeaseId") } }
        }
        viewModelScope.launch { app.runs.active.collect { active ->
            mutableState.update { it.copy(activeRun = active, sharingConsentRunId = it.sharingConsentRunId?.takeIf { id -> id == active.id && active.phase == RunPhase.RUNNING }, publicLocation = if (firebaseMode) app.runnerSharing.state.value.enabled else preferences.getBoolean("publicLocation", false)) }
            if (active.phase != RunPhase.IDLE) preserveEntryForExistingUse()
        } }
        viewModelScope.launch { app.runs.persistence.collect { persistence ->
            val resolvedCredit = state.value.runPersistence.creditPending && !persistence.creditPending
            val before = state.value.profile
            val local = if (resolvedCredit) withContext(Dispatchers.IO) { wallet.read() } else null
            mutableState.update { previous -> previous.copy(runPersistence = persistence,
                profile = if (local != null && (api.token.isBlank() || previous.communityGuest))
                    previous.profile.mergeWalletMileage(before, local) else previous.profile) }
        } }
        viewModelScope.launch { firebaseRooms.googleLinkState.collect { link -> mutableState.update { it.copy(communityGoogleLink = link) } } }
        viewModelScope.launch { app.cloud.state.collect { cloud ->
            entry.setGoogleConfigured(cloud.configured)
            mutableState.update { it.copy(cloud = cloud) }
        } }
        viewModelScope.launch { app.runs.records.collect { records ->
            if (records.isNotEmpty()) preserveEntryForExistingUse()
            val before = state.value.profile
            val local = withContext(Dispatchers.IO) { wallet.read() }
            // New clothes or a nickname must not discard a completed credit read.
            // Merge only the balance, and never replace a balance updated after this read began.
            mutableState.update { it.copy(records = records, profile = if (api.token.isBlank() || it.communityGuest)
                it.profile.mergeWalletMileage(before, local) else it.profile) }
        } }
        viewModelScope.launch { app.runs.lastFinished.filterNotNull().collect { record ->
            if (firebaseMode || api.token.isBlank() || state.value.communityGuest) {
                val before = state.value.profile
                val local = withContext(Dispatchers.IO) { wallet.read() }
                mutableState.update { if (firebaseMode || api.token.isBlank() || it.communityGuest)
                    it.copy(profile = it.profile.mergeWalletMileage(before, local)) else it }
            } else { RunSyncWorker.enqueue(app); refreshCommunity() }
        } }
        viewModelScope.launch { audio.status.collect { status ->
            mutableState.update { previous -> previous.copy(audio = previous.audio.copy(connected = status.connection == AudioConnection.CONNECTED, connecting = status.connection in setOf(AudioConnection.CONNECTING, AudioConnection.RECONNECTING), microphoneEnabled = status.microphoneEnabled, broadcasting = status.musicSending, sourceLabel = status.musicSourceLabel.ifBlank { "음악을 선택해 주세요" }, status = status.error ?: status.message, voiceVolume = status.voiceVolume, musicVolume = status.musicVolume)) }
        } }
        viewModelScope.launch {
            while (isActive) {
                if (!firebaseMode && api.token.isNotBlank()) runCatching { refreshNow() }.onFailure { mutableState.update { it.copy(serverConnected = false, serverStatus = "서버 연결 안 됨 · 기기 기록 계속 가능") } }
                delay(15_000)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                val room = state.value.openedRoomId
                if (!firebaseMode && room.isNotBlank() && api.token.isNotBlank()) runCatching { api.chat(room) }.onSuccess { messages -> mutableState.update { if (!firebaseMode && it.openedRoomId == room) it.copy(messages = messages) else it } }
                delay(3000)
            }
        }
        viewModelScope.launch {
            state.map { if (it.communityBackend == CommunityBackend.FIREBASE) it.openedRoomId else "" }.distinctUntilChanged().collectLatest { opened ->
                if (opened.isBlank()) return@collectLatest
                val generation = communityGeneration
                try {
                    firebaseRooms.messages(opened.substringAfter(':')).collect { update ->
                        mutableState.update { if (firebaseMode && communityGeneration == generation && it.openedRoomId == opened)
                            it.copy(messages = update.messages.map { message -> message.copy(roomId = opened) }, chatFromCache = update.fromCache) else it }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (firebaseMode && communityGeneration == generation) mutableState.update { it.copy(messages = emptyList(), chatCanNotice = false, chatFromCache = true, error = "크루·파티 채팅 연결과 참여 권한을 확인해 주세요.") } }
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
        // Coarse access can still resolve a district, and disabling GPS alone
        // does not disable an available network location provider.
        val unavailable = when {
            !hasPreviewLocationPermission() -> CurrentAreaPhase.PERMISSION_REQUIRED
            runCatching { app.getSystemService(android.location.LocationManager::class.java)?.isLocationEnabled }.getOrNull() == false -> CurrentAreaPhase.LOCATION_DISABLED
            else -> null
        }
        if (unavailable != null) {
            areaOperation++; areaJob?.cancel(); areaJob = null
            weatherJob?.cancel(); weatherJob = null
        }
        mutableState.update { it.copy(locationAccess = LocationAccess(precisePermissionGranted, gpsEnabled),
            currentArea = unavailable?.let { phase -> CurrentAreaUi(phase) } ?: it.currentArea,
            weather = if (unavailable != null && (it.weather.snapshot != null || it.weather.phase in listOf(WeatherPhase.LOCATING, WeatherPhase.LOADING)))
                it.weather.copy(phase = WeatherPhase.valueOf(unavailable.name)) else it.weather) }
    }
    fun updateRunNotificationAccess(enabled: Boolean) {
        mutableState.update { it.copy(runNotificationAccess = RunNotificationAccess(checked = true, enabled = enabled)) }
    }
    fun openRunFromNotification(runId: String, confirmFinish: Boolean) {
        if (runId.isBlank()) return
        val request = RunNavigationRequest(++runNavigationSerial, runId, confirmFinish)
        mutableState.update { it.copy(runNavigationRequest = request) }
    }
    override fun consumeRunNavigation(token: Long) {
        mutableState.update { if (it.runNavigationRequest?.token == token) it.copy(runNavigationRequest = null) else it }
    }
    override fun openRunNotificationSettings() { requests.tryEmit(UiRequest.RunNotificationSettings) }
    override fun addRunWidget() { requests.tryEmit(UiRequest.PinRunWidget) }
    private fun commandRun(action: String, expectedRunId: String? = null) {
        if (!RunTrackingService.command(app, action, expectedRunId)) permissionDenied("러닝 화면을 열고 위치 권한을 확인한 뒤 다시 시도해 주세요.")
    }
    fun beginWithPermission(recover: Boolean) { commandRun(if (recover) RunTrackingService.RECOVER else RunTrackingService.START) }
    fun resumeWithPermission() { commandRun(RunTrackingService.RESUME) }
    override fun startRun() { if (!state.value.runPersistence.blocksNewRun) requests.tryEmit(UiRequest.RunPermission(false)) }
    override fun recoverRun() { if (!state.value.runPersistence.blocksNewRun) requests.tryEmit(UiRequest.RunPermission(true)) }
    override fun openLocationSettings() { requests.tryEmit(UiRequest.LocationSettings) }
    override fun openAppPermissionSettings() { requests.tryEmit(UiRequest.AppPermissionSettings) }
    override fun pauseRun() { commandRun(RunTrackingService.PAUSE) }
    override fun resumeRun() { requests.tryEmit(UiRequest.ResumeRunPermission) }
    override fun finishRun() { commandRun(RunTrackingService.FINISH) }
    override fun finishRun(expectedRunId: String) { commandRun(RunTrackingService.FINISH, expectedRunId) }
    override fun retryRunPersistence() { commandRun(RunTrackingService.RETRY) }
    override fun linkCommunityGoogle() {
        if (firebaseMode && !state.value.cloud.busy && !state.value.communityGoogleLink.busy) {
            invalidateProfileForCommunityIdentityChange()
            requests.tryEmit(UiRequest.LinkCommunityGoogle)
        }
    }
    override fun restoreCommunityGoogle() {
        if (firebaseMode && !state.value.cloud.busy && !state.value.communityGoogleLink.busy) {
            invalidateProfileForCommunityIdentityChange()
            requests.tryEmit(UiRequest.RestoreCommunityGoogle)
        }
    }
    override fun dismissError() { mutableState.update { it.copy(error = null) } }
    override fun setAutoPause(enabled: Boolean) { preferences.edit().putBoolean("autoPause", enabled).apply(); mutableState.update { it.copy(autoPause = enabled) } }
    override fun setVoiceGuidance(enabled: Boolean) { preferences.edit().putBoolean("voiceGuidance", enabled).apply(); mutableState.update { it.copy(voiceGuidance = enabled) } }
    override fun setPublicLocation(enabled: Boolean) {
        if (!enabled) {
            app.runnerSharing.stop()
            preferences.edit().putBoolean("publicLocation", false).apply()
            mutableState.update { it.copy(publicLocation = false, sharingConsentRunId = null) }
            return
        }
        if (!firebaseMode || !state.value.signedIn || firebaseRooms.uid.isBlank()) {
            permissionDenied("근처 위치 공유는 프로필 연결 후 사용할 수 있어요."); return
        }
        val run = state.value.activeRun
        if (run.phase != RunPhase.RUNNING) { permissionDenied("러닝을 시작한 뒤 위치 공유를 켜 주세요."); return }
        mutableState.update { it.copy(sharingConsentRunId = run.id, sharingConsentNonce = it.sharingConsentNonce + 1) }
    }
    override fun dismissSharingConsent(requestNonce: Long) { mutableState.update { if (it.sharingConsentNonce == requestNonce) it.copy(sharingConsentRunId = null) else it } }
    override fun confirmPublicLocation(runId: String, adultConfirmed: Boolean, requestNonce: Long) {
        if (!adultConfirmed || state.value.communityUid != firebaseRooms.uid || requestNonce != state.value.sharingConsentNonce || !firebaseMode || !state.value.signedIn || state.value.sharingConsentRunId != runId ||
            state.value.activeRun.id != runId || state.value.activeRun.phase != RunPhase.RUNNING) return
        dismissSharingConsent(requestNonce)
        app.runnerSharing.enable(runId, adultConfirmed)
    }
    private fun hasPreviewLocationPermission() = listOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION).any {
        androidx.core.content.ContextCompat.checkSelfPermission(app, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    override fun prepareHomeConditions() {
        if (hasPreviewLocationPermission() && weatherJob?.isActive != true) loadPreviewLocation(weather = true)
    }
    override fun refreshWeather() = requestPreviewLocation(weather = true)
    override fun locateExplore() = requestPreviewLocation(weather = false)
    private fun requestPreviewLocation(weather: Boolean) {
        if (hasPreviewLocationPermission()) loadPreviewLocation(weather)
        else requests.tryEmit(UiRequest.PreviewLocation(weather))
    }
    fun previewPermissionResult(weather: Boolean, allowed: Boolean) {
        if (allowed) loadPreviewLocation(weather)
        else {
            areaOperation++; areaJob?.cancel(); areaJob = null
            mutableState.update { it.copy(currentArea = CurrentAreaUi(CurrentAreaPhase.PERMISSION_REQUIRED),
                weather = if (weather) it.weather.copy(phase = WeatherPhase.PERMISSION_REQUIRED,
                    message = "위치를 허용하면 근처 예보를 확인할 수 있어요.") else it.weather,
                discovery = if (!weather) it.discovery.copy(error = "현재 위치로 이동하려면 위치 권한을 허용해 주세요.") else it.discovery) }
        }
    }
    private fun loadPreviewLocation(weather: Boolean) {
        if (weatherJob?.isActive == true) return
        areaOperation++; areaJob?.cancel(); areaJob = null
        mutableState.update { it.copy(currentArea = CurrentAreaUi(CurrentAreaPhase.LOCATING)) }
        weatherJob = viewModelScope.launch {
            if (weather) mutableState.update { it.copy(weather = it.weather.copy(phase = WeatherPhase.LOCATING, message = null)) }
            try {
                val location = weatherLocations.currentLocation()
                val area = CurrentAreaUi(CurrentAreaPhase.RESOLVING, location, checkedAtMillis = System.currentTimeMillis())
                mutableState.update { it.copy(discoveryCenter = RouteMapCenter(location.latitude, location.longitude), currentArea = area) }
                resolveCurrentArea(area)
                if (weather) {
                    mutableState.update { it.copy(weather = it.weather.copy(phase = WeatherPhase.LOADING)) }
                    val snapshot = weatherRepository.load(location)
                    mutableState.update { it.copy(weather = RunningWeatherState(WeatherPhase.READY, snapshot,
                        area = it.currentArea.takeIf { current -> current.location == snapshot.location })) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (failure is WeatherLocationException) {
                    val areaPhase = when (failure.reason) {
                        WeatherLocationFailure.PERMISSION_REQUIRED -> CurrentAreaPhase.PERMISSION_REQUIRED
                        WeatherLocationFailure.LOCATION_DISABLED -> CurrentAreaPhase.LOCATION_DISABLED
                        WeatherLocationFailure.UNAVAILABLE -> CurrentAreaPhase.UNAVAILABLE
                    }
                    mutableState.update { it.copy(currentArea = CurrentAreaUi(areaPhase)) }
                }
                else mutableState.update { if (it.currentArea.phase == CurrentAreaPhase.LOCATING)
                    it.copy(currentArea = CurrentAreaUi(CurrentAreaPhase.UNAVAILABLE)) else it }
                if (weather) {
                    val phase = (failure as? WeatherLocationException)?.let { WeatherPhase.valueOf(it.reason.name) } ?: WeatherPhase.UNAVAILABLE
                    mutableState.update { it.copy(weather = it.weather.copy(phase = phase, message = failure.message ?: "예보를 확인하지 못했어요.")) }
                } else mutableState.update { it.copy(discovery = it.discovery.copy(error = "현재 위치를 확인하지 못했어요. 위치 서비스와 권한을 확인해 주세요.")) }
            }
        }
    }
    private fun resolveCurrentArea(area: CurrentAreaUi) {
        val location = area.location ?: return
        val ticket = ++areaOperation
        areaJob?.cancel()
        areaJob = viewModelScope.launch {
            val name = areaRepository.resolve(location)
            if (ticket != areaOperation) return@launch
            val resolved = area.copy(phase = if (name == null) CurrentAreaPhase.UNAVAILABLE else CurrentAreaPhase.READY, name = name)
            mutableState.update {
                if (it.currentArea.location != location) it else it.copy(currentArea = resolved,
                    weather = if (it.weather.snapshot?.location == location) it.weather.copy(area = resolved) else it.weather)
            }
        }
    }
    fun onUiStopped() {
        weatherJob?.cancel(); weatherJob = null
        areaOperation++; areaJob?.cancel(); areaJob = null
        mutableState.update { it.copy(
            weather = if (it.weather.phase in listOf(WeatherPhase.LOCATING, WeatherPhase.LOADING)) it.weather.copy(phase = WeatherPhase.IDLE) else it.weather,
            currentArea = if (it.currentArea.phase in listOf(CurrentAreaPhase.LOCATING, CurrentAreaPhase.RESOLVING))
                it.currentArea.copy(phase = CurrentAreaPhase.IDLE) else it.currentArea) }
        setDiscoveryVisible(false)
    }
    override fun setDiscoveryVisible(visible: Boolean) {
        if (discoveryVisible == visible) return
        discoveryVisible = visible
        if (visible) refreshRunners() else { discoveryJob?.cancel(); discoveryJob = null }
    }
    override fun setDiscoveryBounds(bounds: RouteMapBounds) {
        if (discoveryBounds == bounds) return
        discoveryBounds = bounds
        if (discoveryVisible) refreshRunners()
    }
    override fun refreshRunners() {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            while (isActive && discoveryVisible) {
                val bounds = discoveryBounds
                val uid = firebaseRooms.uid
                val generation = communityGeneration
                if (bounds == null) { delay(500); continue }
                if (!firebaseMode || !state.value.signedIn || uid.isBlank()) {
                    mutableState.update { it.copy(discovery = RunnerDiscoveryUi(error = "러너를 보려면 프로필을 연결해 주세요."), liveRunners = emptyList()) }
                    delay(15_000); continue
                }
                mutableState.update { it.copy(discovery = it.discovery.copy(loading = true, error = null)) }
                try {
                    val result = app.runnerPresence.discover(bounds)
                    if (discoveryVisible && generation == communityGeneration && uid == firebaseRooms.uid && discoveryBounds == bounds)
                        mutableState.update { it.copy(liveRunners = result.runners, discovery = RunnerDiscoveryUi(
                            checkedAtMillis = result.checkedAtMillis, partial = result.partial, requiresZoom = result.requiresZoom)) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (discoveryVisible && generation == communityGeneration && uid == firebaseRooms.uid) mutableState.update { it.copy(
                        discovery = it.discovery.copy(loading = false, error = "러너 위치를 확인하지 못했어요. 연결·기기 시각·서버 규칙과 색인을 확인해 주세요.")) }
                }
                delay(15_000)
            }
        }
    }
    override fun checkNickname(name: String) {
        val checkTicket = ++nicknameCheckOperation
        nicknameCheckJob?.cancel()
        val uid = firebaseRooms.uid
        val generation = communityGeneration
        mutableState.update { it.copy(unifiedProfile = it.unifiedProfile.copy(checking = true, check = null, error = null)) }
        nicknameCheckJob = viewModelScope.launch {
            try {
                val result = firebaseRooms.checkNickname(name)
                if (firebaseMode && checkTicket == nicknameCheckOperation && generation == communityGeneration && uid == firebaseRooms.uid)
                    mutableState.update { it.copy(unifiedProfile = it.unifiedProfile.copy(check = result)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (checkTicket == nicknameCheckOperation && generation == communityGeneration && uid == firebaseRooms.uid) mutableState.update {
                    it.copy(unifiedProfile = it.unifiedProfile.copy(error = failure.message ?: "닉네임을 확인하지 못했어요.")) }
            } finally {
                if (checkTicket == nicknameCheckOperation && generation == communityGeneration && uid == firebaseRooms.uid) mutableState.update { it.copy(unifiedProfile = it.unifiedProfile.copy(checking = false)) }
            }
        }
    }
    private fun invalidateProfileConnection() {
        profileConnectOperation++
        profileConnectJob?.cancel(); profileConnectJob = null
        mutableState.update { it.copy(unifiedProfile = it.unifiedProfile.copy(connecting = false)) }
    }
    private fun invalidateProfileForCommunityIdentityChange() {
        invalidateProfileConnection()
        profileOperation++; nicknameCheckOperation++; nicknameCheckJob?.cancel()
        mutableState.update { it.copy(unifiedProfile = it.unifiedProfile.copy(saving = false,
            checking = false, check = null, error = null)) }
    }
    override fun saveUnifiedProfile(name: String, expectedUid: String) =
        saveUnifiedProfile(name, expectedUid, state.value.communityBackend)
    override fun saveUnifiedProfile(name: String, expectedUid: String, expectedBackend: CommunityBackend) {
        val current = state.value
        val actualBackend = if (firebaseMode) CommunityBackend.FIREBASE else CommunityBackend.HTTP
        if (expectedBackend != actualBackend || expectedBackend != current.communityBackend || expectedUid != current.communityUid ||
            firebaseMode && current.signedIn && (expectedUid.isBlank() || expectedUid != firebaseRooms.uid)) {
            mutableState.update { it.copy(unifiedProfile = it.unifiedProfile.copy(error = "연결 상태가 바뀌었어요. 프로필을 다시 열어 주세요.")) }
            return
        }
        // A hidden, unapproved Auth candidate never blocks local device-name saving.
        saveProfile(name, expectedUid)
    }
    override fun updateProfile(name: String) = saveUnifiedProfile(name, state.value.communityUid)
    private fun saveProfile(name: String, expectedUid: String) {
        if (state.value.unifiedProfile.saving || state.value.unifiedProfile.connecting) return
        val ticket = ++profileOperation
        val generation = communityGeneration
        val startedFirebase = firebaseMode
        val onlineFirebase = startedFirebase && state.value.signedIn
        val targetUrl = api.baseUrl
        val targetToken = api.token
        val targetUser = currentUserId
        fun sameContext() = generation == communityGeneration && startedFirebase == firebaseMode &&
            (startedFirebase || targetUrl == api.baseUrl && targetToken == api.token && targetUser == currentUserId)
        mutableState.update { it.copy(unifiedProfile = it.unifiedProfile.copy(saving = true, error = null)) }
        viewModelScope.launch {
            try {
                val normalized = NicknamePolicy.requireValid(name).nickname
                if (ticket != profileOperation || !sameContext() || state.value.communityUid != expectedUid) return@launch
                if (onlineFirebase) firebaseRooms.updateNickname(normalized, expectedUid = expectedUid)
                else if (!startedFirebase && targetToken.isNotBlank())
                    PilotClient(targetUrl, targetToken).request("PATCH", "/v1/me", JSONObject().put("nickname", normalized))
                if (ticket != profileOperation || !sameContext() || state.value.communityUid != expectedUid ||
                    onlineFirebase && expectedUid != firebaseRooms.uid) return@launch
                mutateLocalProfile { it.copy(name = normalized) }
                profileOperation++ // Fence a snapshot which read the previous nickname while this save was pending.
                mutableState.update { it.copy(unifiedProfile = it.unifiedProfile.copy(saving = false,
                    connectedNickname = if (it.signedIn) normalized else null, savedSerial = it.unifiedProfile.savedSerial + 1)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (ticket == profileOperation && sameContext() && state.value.communityUid == expectedUid) mutableState.update {
                    it.copy(unifiedProfile = it.unifiedProfile.copy(error = failure.message ?: "프로필을 저장하지 못했어요.")) }
            } finally {
                if (ticket == profileOperation && sameContext()) mutableState.update { it.copy(unifiedProfile = it.unifiedProfile.copy(saving = false)) }
            }
        }
    }
    override fun setAvatarGender(gender: AvatarGender) = ignoreJob(work { wardrobeChanges.withLock {
        if (!firebaseMode && api.token.isNotBlank() && !state.value.communityGuest) {
            api.request("PATCH", "/v1/me", JSONObject().put("avatar", JSONObject().put("gender", gender.wireValue)))
            refreshNow()
        } else mutateLocalProfile { it.copy(avatar = it.avatar.copy(gender = gender)) }
    } })
    override fun setRunningKitColor(region: RunningKitRegion, color: Int) = ignoreJob(work { wardrobeChanges.withLock {
        require(color in 0 until RunningKitColors.COLOR_COUNT) { "올바른 러닝복 색상을 선택해 주세요." }
        val generation = communityGeneration
        val targetUrl = api.baseUrl
        val targetToken = api.token
        val serverProfile = !firebaseMode && targetToken.isNotBlank() && !state.value.communityGuest
        val savedColor = if (serverProfile) {
            // Snapshot the intended HTTP session before suspension. Send only the
            // selected fabric region; old equipment/gender and other colors stay intact.
            val target = PilotClient(targetUrl, targetToken)
            val result = try {
                target.request("PATCH", "/v1/me", JSONObject().put("avatar", JSONObject().put(
                    "runningKitColors", JSONObject().put(region.wireKey, color))))
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (communityGeneration != generation || firebaseMode || api.baseUrl != targetUrl ||
                    api.token != targetToken || state.value.communityGuest) return@withLock
                if (failure is PilotException && failure.status == 422)
                    throw IllegalStateException("이 서버는 러닝복 색상을 지원하지 않아요. 서버 업데이트 후 다시 시도해 주세요.")
                throw failure
            }
            if (communityGeneration != generation || firebaseMode || api.baseUrl != targetUrl ||
                api.token != targetToken || state.value.communityGuest) return@withLock
            with(PilotClient) { result.confirmedRunningKitColor(region, color) }
        } else withContext(Dispatchers.IO) {
            wallet.update { profile -> profile.copy(avatar = profile.avatar.copy(
                runningKitColors = profile.avatar.runningKitColors.withColor(region, color)))
            }.avatar.runningKitColors.color(region)
        }
        // Publish after any older community/profile snapshot. A refresh that starts
        // after this lock observes the already committed local or HTTP color value.
        communityRefresh.withLock {
            mutableState.update { previous ->
                val stillServerProfile = !firebaseMode && api.token.isNotBlank() && !previous.communityGuest
                if (communityGeneration == generation && api.baseUrl == targetUrl && api.token == targetToken &&
                    stillServerProfile == serverProfile) previous.copy(profile = previous.profile.copy(
                    // Never replace a newer nickname, wallet, gender or item selection
                    // with a profile snapshot received before this operation completed.
                    avatar = previous.profile.avatar.copy(runningKitColors =
                        previous.profile.avatar.runningKitColors.withColor(region, savedColor)))) else previous
            }
        }
    } })
    override fun equipItem(slot: String, variant: Int) = ignoreJob(work { wardrobeChanges.withLock {
        require(AvatarCatalogue.valid(slot, variant)) { "올바른 아이템을 선택해 주세요." }
        require(com.runmoni.app.avatar.AvatarArtwork.itemReady(slot, variant, state.value.profile.avatar.gender)) {
            "아직 착용할 수 없는 아이템이에요. 기존 보유 내역은 그대로 보관돼요."
        }
        val item = "$slot:$variant"
        val current = state.value.profile.avatar
        if (!firebaseMode && api.token.isNotBlank() && !state.value.communityGuest) {
            if (item !in state.value.profile.ownedItems) {
                val entry = api.request("GET", "/v1/shop/items").items().firstOrNull { it.optString("id") == item }
                require(entry?.optInt("cost", -1) == 0) { "이 서버는 무료 테스트 설정이 아니에요. 서버 업데이트 후 다시 시도해 주세요." }
                api.request("POST", "/v1/shop/purchase", JSONObject().put("item_id", item))
            }
            // Send only the selected slot: a slower purchase must not overwrite
            // a newer gender or equipment selection with an old full profile.
            val patch = JSONObject().put(slot, variant)
            RunningKitRegion.entries.firstOrNull { it.wireKey == slot }?.let { region ->
                // Include zero for the new-item intent even when our last local
                // color was already zero; a server snapshot may have changed it.
                if (current.variantFor(slot) != variant || slot in AvatarCatalogue.coveredSlots(current.costume))
                    patch.put("runningKitColors", JSONObject().put(region.wireKey, 0))
            }
            api.request("PATCH", "/v1/me", JSONObject().put("avatar", patch)); refreshNow()
        } else mutateLocalProfile { it.copy(avatar = it.avatar.withSelectedItem(slot, variant), ownedItems = it.ownedItems + item) }
    } })
    override fun buyItem(slot: String, variant: Int) = equipItem(slot, variant)
    override fun deleteRun(id: String) = ignoreJob(work {
        val record = app.runs.record(id)
        if (!firebaseMode && api.token.isNotBlank()) try { api.request("DELETE", "/v1/runs/$id") } catch (failure: PilotException) { if (failure.status != 404 || record?.synced == true) throw failure }
        app.runs.delete(id)
    })
    override fun canShareRun(record: RunRecord): Boolean = RunShareAccess.allowed(state.value, record)
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
            if (firebaseMode || api.baseUrl != url.trim().trimEnd('/')) {
                invalidateProfileConnection()
                app.runnerSharing.stop()
                profileOperation++; nicknameCheckOperation++; nicknameCheckJob?.cancel()
                mutableState.update { it.copy(communityUid = "", sharingConsentRunId = null,
                    unifiedProfile = UnifiedProfileUi(savedSerial = it.unifiedProfile.savedSerial, connectedSerial = it.unifiedProfile.connectedSerial)) }
            }
            if (firebaseMode) {
                firebaseMode = false; communityGeneration++; connectionCheckGeneration++
                preferences.edit { putString("communityBackend", "http") }
                mutableState.update { it.copy(communityBackend = CommunityBackend.HTTP, signedIn = false, serverConnected = false, deviceChecks = it.deviceChecks.copy(room = RoomCheck.NOT_CHECKED, roomCheckedAt = 0), crews = emptyList(), parties = emptyList(), crewMembers = emptyMap(), messages = emptyList(), openedRoomId = "", openedRoomTitle = "") }
            }
            if (api.baseUrl != url.trim().trimEnd('/')) {
                connectionCheckGeneration++
                mutableState.update { it.copy(deviceChecks = it.deviceChecks.copy(room = RoomCheck.NOT_CHECKED, roomCheckedAt = 0)) }
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
    override fun connectUnifiedProfile(name: String, expectedUid: String, expectedBackend: CommunityBackend) {
        val actualBackend = if (firebaseMode) CommunityBackend.FIREBASE else CommunityBackend.HTTP
        if (expectedBackend != actualBackend || expectedBackend != state.value.communityBackend || expectedUid != state.value.communityUid) {
            mutableState.update { it.copy(unifiedProfile = it.unifiedProfile.copy(error = "연결 상태가 바뀌었어요. 프로필을 다시 열어 주세요.")) }
            return
        }
        connectProfile(name, expectedUid, expectedBackend)
    }
    override fun connectCommunity(name: String) = connectUnifiedProfile(name, state.value.communityUid, state.value.communityBackend)
    private fun connectProfile(name: String, expectedUid: String, expectedBackend: CommunityBackend) {
        if (state.value.busy || state.value.unifiedProfile.connecting || state.value.unifiedProfile.saving) return
        val ticket = ++profileConnectOperation
        profileOperation++ // Invalidate a nickname snapshot started before this request.
        val generation = communityGeneration
        val targetUrl = api.baseUrl
        var scopedToken = api.token
        var scopedUser = currentUserId
        var createdHttpGuest = false
        fun sameContext() = ticket == profileConnectOperation && generation == communityGeneration &&
            expectedBackend == state.value.communityBackend &&
            (if (firebaseMode) CommunityBackend.FIREBASE else CommunityBackend.HTTP) == expectedBackend &&
            (expectedBackend == CommunityBackend.FIREBASE || targetUrl == api.baseUrl && scopedToken == api.token && scopedUser == currentUserId)
        nicknameCheckOperation++; nicknameCheckJob?.cancel()
        mutableState.update { it.copy(unifiedProfile = it.unifiedProfile.copy(connecting = true, checking = false, check = null, error = null)) }
        profileConnectJob = viewModelScope.launch {
            try {
                val requested = NicknamePolicy.requireValid(name)
                if (!sameContext() || state.value.communityUid != expectedUid) return@launch
                val acknowledgedUid: String
                if (expectedBackend == CommunityBackend.FIREBASE) {
                    firebaseRooms.connect(requested.nickname)
                    acknowledgedUid = firebaseRooms.uid
                    check(acknowledgedUid.isNotBlank()) { "프로필 연결 결과를 확인하지 못했어요. 같은 닉네임으로 다시 시도해 주세요." }
                } else {
                    val client = PilotClient(targetUrl, scopedToken)
                    if (scopedToken.isNotBlank()) {
                        // Editing an already authenticated HTTP profile must not
                        // create a replacement guest account or discard memberships.
                        check(scopedUser.isNotBlank()) { "현재 서버 프로필을 확인한 뒤 다시 시도해 주세요." }
                        client.request("PATCH", "/v1/me", JSONObject().put("nickname", requested.nickname))
                    } else {
                        val result = client.connectCommunity(requested.nickname)
                        val nextToken = result.getString("access_token")
                        val nextUser = result.getJSONObject("user").getString("id")
                        check(nextToken.isNotBlank() && nextUser.isNotBlank()) { "서버 프로필 연결 결과를 확인하지 못했어요." }
                        if (!sameContext() || state.value.communityUid != expectedUid) return@launch
                        scopedToken = nextToken; scopedUser = nextUser; createdHttpGuest = true
                        api.token = nextToken; currentUserId = nextUser
                        session.write(nextToken)
                        preferences.edit().putString("userId", nextUser).putBoolean("communityGuest", true).putBoolean("publicLocation", false).apply()
                        RunSyncWorker.cancel(app)
                    }
                    acknowledgedUid = state.value.communityUid // HTTP identities remain private in the existing session model.
                }
                if (!sameContext() || state.value.communityUid !in setOf(expectedUid, acknowledgedUid)) return@launch
                profileOperation++ // A read started during the request cannot overwrite its ACKed nickname.
                mutateLocalProfile { it.copy(name = requested.nickname) }
                mutableState.update { it.copy(signedIn = true, communityUid = acknowledgedUid,
                    communityGuest = if (expectedBackend == CommunityBackend.FIREBASE || createdHttpGuest) true else it.communityGuest,
                    publicLocation = if (createdHttpGuest) false else it.publicLocation,
                    unifiedProfile = it.unifiedProfile.copy(connecting = false, error = null,
                        connectedNickname = requested.nickname, connectedNicknameKey = requested.key,
                        connectionUid = acknowledgedUid, connectedSerial = it.unifiedProfile.connectedSerial + 1,
                        savedSerial = it.unifiedProfile.savedSerial + 1)) }
                // The profile has been ACKed. A room-list failure must not undo
                // that receipt or replace it with another modal profile error.
                try { refreshNow() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (sameContext()) mutableState.update { it.copy(serverConnected = false,
                        serverStatus = "프로필은 연결됐어요. 크루 목록은 새로고침해 주세요.") }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (sameContext()) mutableState.update { it.copy(unifiedProfile = it.unifiedProfile.copy(
                    error = failure.message ?: "프로필을 연결하지 못했어요. 같은 닉네임으로 다시 시도해 주세요.")) }
            } finally {
                if (sameContext()) mutableState.update { it.copy(unifiedProfile = it.unifiedProfile.copy(connecting = false)) }
            }
        }
    }
    override fun signIn(inviteCode: String, name: String) {
        invalidateProfileConnection()
        ignoreJob(work {
            val result = api.login(inviteCode.trim(), name.trim())
            preferences.edit().putBoolean("communityGuest", false).apply()
            api.token = result.getString("access_token"); session.write(api.token)
            currentUserId = result.getJSONObject("user").getString("id"); preferences.edit().putString("userId", currentUserId).apply()
            mutableState.update { it.copy(signedIn = true, communityGuest = false) }; refreshNow(); RunSyncWorker.enqueue(app)
        })
    }
    override fun signOut() {
        invalidateProfileConnection()
        ignoreJob(work {
            app.runnerSharing.stop(); profileOperation++; nicknameCheckJob?.cancel()
            if (firebaseMode) {
                firebaseRooms.signOut(); communityGeneration++
                mutableState.update { it.copy(signedIn = false, communityGuest = true, communityUid = "",
                    unifiedProfile = UnifiedProfileUi(savedSerial = it.unifiedProfile.savedSerial, connectedSerial = it.unifiedProfile.connectedSerial), openedRoomId = "", openedRoomTitle = "", messages = emptyList(), crewMembers = emptyMap(), crews = emptyList(), parties = emptyList(), serverConnected = false) }
                refreshNow()
                return@work
            }
            runCatching { api.request("DELETE", "/v1/presence") }; runCatching { api.request("DELETE", "/v1/auth/session") }
            audio.disconnect(); api.token = ""; session.write(""); currentUserId = ""; preferences.edit().remove("userId").putBoolean("publicLocation", false).putBoolean("communityGuest", false).apply()
            mutableState.update { it.copy(signedIn = false, communityGuest = false, communityUid = "",
                unifiedProfile = UnifiedProfileUi(savedSerial = it.unifiedProfile.savedSerial, connectedSerial = it.unifiedProfile.connectedSerial), serverConnected = false, profile = readLocalProfile(), publicLocation = false, crews = emptyList(), parties = emptyList(), messages = emptyList(), crewMembers = emptyMap(), liveRunners = emptyList(), openedRoomId = "", openedRoomTitle = "", serverStatus = "기기 내 기록 모드") }
        })
    }
    override fun signInGoogle() { requests.tryEmit(UiRequest.GoogleSignIn) }
    override fun useFirebaseCommunity() {
        invalidateProfileConnection()
        ignoreJob(work {
            app.runnerSharing.stop()
            profileOperation++; nicknameCheckOperation++; nicknameCheckJob?.cancel()
            mutableState.update { it.copy(sharingConsentRunId = null,
                unifiedProfile = UnifiedProfileUi(savedSerial = it.unifiedProfile.savedSerial, connectedSerial = it.unifiedProfile.connectedSerial)) }
            val previous = PilotClient(api.baseUrl, api.token)
            val previousHttp = !firebaseMode
            firebaseMode = true; communityGeneration++; connectionCheckGeneration++
            preferences.edit { putString("communityBackend", "firebase"); putBoolean("publicLocation", false) }
            RunSyncWorker.cancel(app)
            mutableState.update { it.copy(communityBackend = CommunityBackend.FIREBASE, signedIn = false, communityUid = "", communityGuest = true, publicLocation = false,
                serverConnected = false, deviceChecks = it.deviceChecks.copy(room = RoomCheck.NOT_CHECKED, roomCheckedAt = 0),
                crews = emptyList(), parties = emptyList(), crewMembers = emptyMap(), messages = emptyList(), openedRoomId = "", openedRoomTitle = "") }
            if (previousHttp && previous.token.isNotBlank()) runCatching { previous.request("DELETE", "/v1/presence") }
            audio.stopMusic(); runCatching { stopLease() }; audio.disconnect(); selectedVoiceRoom = ""
            refreshNow()
        })
    }
    override fun loadCrewMembers(id: String) {
        if (!firebaseMode || state.value.crews.none { it.id == id && it.joined }) return
        val generation = communityGeneration
        work {
            val members = firebaseRooms.members(id)
            mutableState.update { if (firebaseMode && generation == communityGeneration) it.copy(
                crewMembers = it.crewMembers + (id to members),
                crews = it.crews.map { crew -> if (crew.id == id) crew.copy(isDj = members.any { member -> member.id == firebaseRooms.uid && member.isDj }) else crew },
            ) else it }
        }
    }
    override fun loadCrewParties(id: String) {
        focusedCrewForParties = id
        if (!firebaseMode) return
        val generation = communityGeneration
        work {
            mutableState.update { it.copy(crewPartiesLoading = true, crewPartiesError = null) }
            try { refreshNow() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { if (generation == communityGeneration) it.copy(crewPartiesError = "파티를 불러오지 못했어요. 연결 후 다시 시도해 주세요.") else it } }
            finally { mutableState.update { if (generation == communityGeneration && focusedCrewForParties == id) it.copy(crewPartiesLoading = false) else it } }
        }
    }
    override fun checkGoogleSession() = ignoreJob(viewModelScope.launch { app.cloud.checkSession() })
    override fun checkRoomConnection() {
        if (state.value.deviceChecks.room == RoomCheck.CHECKING) return
        val expectedUrl = api.baseUrl
        val generation = ++connectionCheckGeneration
        mutableState.update { it.copy(deviceChecks = it.deviceChecks.copy(room = RoomCheck.CHECKING, roomCheckedAt = 0)) }
        viewModelScope.launch {
            val result = if (firebaseMode) {
                if (!firebaseRooms.configured) RoomCheck.UNCONFIGURED
                else try { firebaseRooms.snapshot(); RoomCheck.FIREBASE_READY }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { RoomCheck.UNREACHABLE }
            } else RoomConnectionCheck.check(expectedUrl)
            mutableState.update { if (generation == connectionCheckGeneration && api.baseUrl == expectedUrl) it.copy(deviceChecks = it.deviceChecks.copy(room = result, roomCheckedAt = System.currentTimeMillis())) else it }
        }
    }
    override fun setPhoneCheck(check: PhoneCheck, confirmed: Boolean) {
        mutableState.update {
            val next = if (confirmed) it.deviceChecks.selfReported + check else it.deviceChecks.selfReported - check
            it.copy(deviceChecks = it.deviceChecks.copy(selfReported = next))
        }
        preferences.edit { putStringSet("phoneChecks.${BuildConfig.VERSION_CODE}", state.value.deviceChecks.selfReported.map { it.name }.toSet()) }
    }
    override fun shareDeviceChecks() {
        val installed = InstalledAppIdentity.read(app)
        val current = state.value
        requests.tryEmit(UiRequest.ShareText(deviceCheckReport(
            installed.version, installed.code, installed.certificateSha256, current.deviceChecks,
            current.cloud.configured, current.cloud.uid.isNotBlank(), current.cloud.interactiveSignInAt,
            current.cloud.sessionCheckedAt, current.locationAccess.precisePermissionGranted == true, current.locationAccess.gpsEnabled == true, current.communityBackend,
        )))
    }
    override fun startEntryGoogleSignIn() {
        val token = entry.beginGoogle() ?: return
        pendingEntryGoogleToken = token
        if (requests.subscriptionCount.value == 0 || !requests.tryEmit(UiRequest.EntryGoogleSignIn(token))) {
            pendingEntryGoogleToken = null
            viewModelScope.launch { entry.finishGoogle(token, GoogleSignInOutcome.Failure("로그인 화면을 준비하지 못했어요. 다시 눌러 주세요.")) }
        }
    }
    override fun continueAsGuest() {
        if (!entry.state.value.requiresChoice || entry.state.value.savingChoice) return
        entryGoogleJob?.cancel()
        entryGoogleJob = null
        pendingEntryGoogleToken = null
        viewModelScope.launch { entry.enterGuest() }
    }
    private suspend fun preserveEntryForExistingUse() {
        if (!entry.state.value.requiresChoice || entry.state.value.savingChoice) return
        entryGoogleJob?.cancel()
        entryGoogleJob = null
        pendingEntryGoogleToken = null
        entry.preserveExistingUse()
    }
    fun onEntryHostCreated() {
        // A request queued for an Activity destroyed during recreation must not
        // leave the new screen permanently busy without an account picker.
        val token = pendingEntryGoogleToken ?: return
        if (entryGoogleJob?.isActive != true) {
            entry.cancelGoogle(token)
            pendingEntryGoogleToken = null
        }
    }
    suspend fun googleSignInForEntry(context: Context, token: Long) {
        if (!entry.isCurrentGoogle(token)) return
        val job = currentCoroutineContext().job
        entryGoogleJob = job
        try {
            val result = app.cloud.signIn(context)
            entry.finishGoogle(token, result)
        } catch (cancelled: CancellationException) {
            entry.cancelGoogle(token)
            throw cancelled
        } catch (_: Exception) {
            entry.finishGoogle(token, GoogleSignInOutcome.Failure("Google 로그인을 마치지 못했어요. 다시 시도하거나 게스트로 이용해 주세요."))
        } finally {
            if (entryGoogleJob === job) entryGoogleJob = null
            if (pendingEntryGoogleToken == token) pendingEntryGoogleToken = null
        }
    }
    suspend fun googleSignIn(context: Context) {
        if (state.value.communityGoogleLink.busy) return
        try { app.cloud.signIn(context) } catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { permissionDenied("Google 로그인에 실패했습니다. 클라우드 연결 설정을 확인해 주세요.") }
    }
    suspend fun linkCommunityGoogle(context: Context, restore: Boolean = false) {
        if (!firebaseMode || state.value.cloud.busy || state.value.communityGoogleLink.busy) return
        invalidateProfileForCommunityIdentityChange()
        // An identity operation never carries public-location consent to its result.
        app.runnerSharing.stop()
        mutableState.update { it.copy(sharingConsentRunId = null) }
        try {
            if (restore) firebaseRooms.restoreGoogleAccount(context) else firebaseRooms.linkGoogleAccount(context)
            refreshCommunity()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { permissionDenied("크루 프로필의 Google 연결을 확인하지 못했어요. 기존 프로필은 자동으로 바꾸지 않아요.") }
    }
    override fun signOutGoogle() = ignoreJob(work { app.cloud.signOut(app) })
    override fun backupCloudRun(runId: String, expectedUid: String) = ignoreJob(work {
        val record = app.runs.record(runId) ?: error("기기 기록이 없습니다.")
        app.cloud.backupRun(record, expectedUid)
    })
    override fun refreshCloudSummaries(expectedUid: String) = ignoreJob(work { app.cloud.refreshSummaries(expectedUid) })
    override fun deleteCloudSummary(runId: String, expectedUid: String) = ignoreJob(work { app.cloud.deleteSummary(runId, expectedUid) })
    override fun refreshCommunity() = ignoreJob(work {
        val generation = communityGeneration
        try { refreshNow() } catch (failure: Exception) {
            mutableState.update { if (generation == communityGeneration) it.copy(serverConnected = false, serverStatus = "크루·파티 목록을 불러오지 못했어요. 연결 후 다시 시도해 주세요.") else it }
            if (failure is CancellationException) throw failure
        }
    })
    private suspend fun refreshNow() = communityRefresh.withLock {
        val generation = communityGeneration
        if (firebaseMode) {
            val focusedAtStart = focusedCrewForParties
            val profileVersionAtRead = profileOperation
            // The repository obtains a server profile ACK from its private auth
            // candidate. Do not publish an Auth UID before this read succeeds.
            val snapshot = firebaseRooms.snapshot()
            val accepted = if (snapshot.signedIn) firebaseRooms.uid else ""
            if (snapshot.signedIn && accepted.isBlank()) return@withLock
            var scopedError: String? = null
            val focused = focusedCrewForParties?.takeIf { id -> snapshot.crews.any { it.id == id } }
            val scoped = try { focused?.let { firebaseRooms.partiesForCrew(it) }.orEmpty() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { scopedError = "크루의 파티를 불러오지 못했어요. 다시 시도해 주세요."; emptyList() }
            if (!firebaseMode || generation != communityGeneration || focusedAtStart != focusedCrewForParties ||
                firebaseRooms.uid != accepted) return@withLock
            val visibleParties = (snapshot.parties + scoped).distinctBy { it.id }
            val unchangedProfileOperation = profileVersionAtRead == profileOperation
            val identityChanged = state.value.communityUid != accepted
            if (identityChanged) {
                app.runnerSharing.stop()
                profileOperation++; nicknameCheckOperation++; nicknameCheckJob?.cancel()
            }
            if (snapshot.signedIn && snapshot.nickname != null && unchangedProfileOperation &&
                !state.value.unifiedProfile.saving && !state.value.unifiedProfile.connecting) {
                val before = wallet.read()
                if (before.name != snapshot.nickname) {
                    if (!preferences.contains("legacyLocalNicknameBeforeUnifiedV1")) preferences.edit().putString("legacyLocalNicknameBeforeUnifiedV1", before.name).apply()
                    wallet.update { it.copy(name = snapshot.nickname) }
                }
            }
            mutableState.update {
                val openedId = it.openedRoomId.substringAfter(':')
                val stillMember = it.openedRoomId.isBlank() || snapshot.crews.any { r -> r.id == openedId && r.joined } || visibleParties.any { r -> r.id == openedId && r.joined }
                val profileUi = if (identityChanged) UnifiedProfileUi(savedSerial = it.unifiedProfile.savedSerial,
                    connectedSerial = it.unifiedProfile.connectedSerial, connecting = it.unifiedProfile.connecting) else it.unifiedProfile
                it.copy(profile = wallet.read(), crews = snapshot.crews.map { crew -> crew.copy(isDj = it.crews.any { old -> old.id == crew.id && old.isDj }) }, parties = visibleParties, crewPartiesError = scopedError,
                    signedIn = snapshot.signedIn, communityGuest = true, communityUid = accepted,
                    unifiedProfile = if (unchangedProfileOperation) profileUi.copy(connectedNickname = snapshot.nickname, connectedNicknameKey = snapshot.nicknameKey) else profileUi,
                    sharingConsentRunId = if (identityChanged) null else it.sharingConsentRunId,
                    publicLocation = app.runnerSharing.state.value.enabled,
                    serverConnected = true, serverStatus = "크루·파티 목록을 불러왔어요",
                    openedRoomId = if (stillMember) it.openedRoomId else "", openedRoomTitle = if (stillMember) it.openedRoomTitle else "",
                    messages = if (stillMember) it.messages else emptyList(), audio = it.audio.copy(canDj = false, status = "현재 크루·파티에서는 채팅을 이용해 주세요 · 음성 서버는 별도예요"))
            }
            return@withLock
        }
        val profileVersionAtRead = profileOperation
        val expectedToken = api.token
        val expectedUrl = api.baseUrl
        val health = api.health()
        check(health.optJSONObject("capabilities")?.optBoolean("public_rooms") == true) { "크루·파티 목록을 지원하는 서버가 필요해요. 서버 연결을 확인해 주세요." }
        val identity = if (expectedToken.isBlank()) null else api.request("GET", "/v1/me")
        val guest = identity?.optString("auth_mode") == "guest"
        val profile = if (identity == null || guest) wallet.read() else identity.toProfile()
        val crews = api.crews()
        val parties = api.parties()
        val members = crews.filter { it.joined }.associate { it.id to api.members(it.id) }
        val presence = if (identity != null && !guest) api.presence() else emptyList()
        val voice = health.optJSONObject("capabilities")?.optBoolean("voice") ?: false
        if (firebaseMode || generation != communityGeneration || expectedToken != api.token || expectedUrl != api.baseUrl) return@withLock
        if (identity != null) preferences.edit().putBoolean("communityGuest", guest).apply()
        val voiceParty = parties.find { "party:${it.id}" == selectedVoiceRoom && it.joined && !it.cancelled }
        val voiceCrew = if (voiceParty != null) crews.find { it.joined && it.id == voiceParty.crewId }
            else crews.find { "crew:${it.id}" == selectedVoiceRoom && it.joined }
        if (selectedVoiceRoom.isNotBlank() && voiceCrew == null && voiceParty == null) {
            stopLease(); audio.disconnect(); selectedVoiceRoom = ""
        }
        val canDj = voiceParty?.isOrganizer == true || voiceCrew?.let { it.isOwner || it.isDj } == true
        if (!canDj && audio.status.value.musicSending) { audio.stopMusic(); stopLease() }
        mutableState.update { it.copy(profile = if (profileVersionAtRead == profileOperation && !it.unifiedProfile.saving && !it.unifiedProfile.connecting) profile else it.profile, crews = crews, parties = parties, crewMembers = members, liveRunners = presence, serverConnected = true, signedIn = identity != null, communityGuest = guest, serverStatus = "크루·파티 목록을 불러왔어요", audio = it.audio.copy(roomId = selectedVoiceRoom, canDj = canDj, status = if (!voice && !it.audio.connected) "음성·방송 서버 설정 필요" else it.audio.status)) }
    }
    private suspend fun refreshAfterCreation() {
        runCatching { refreshNow() }.onFailure {
            if (it is CancellationException) throw it
            mutableState.update { it.copy(serverConnected = false, serverStatus = "생성은 완료했지만 목록을 새로 불러오지 못했어요. 새로고침해 주세요.") }
        }
    }
    override fun createCrew(input: NewCrew) = ignoreJob(work {
        val generation = communityGeneration
        val crew = if (firebaseMode) firebaseRooms.createCrew(input) else api.createCrew(input).toCrew(true)
        if (generation != communityGeneration) return@work
        mutableState.update { it.copy(crews = listOf(crew) + it.crews.filterNot { old -> old.id == crew.id }, lastCreatedCrewId = crew.id) }
        refreshAfterCreation()
    })
    override fun joinCrew(id: String) = ignoreJob(work { if (firebaseMode) firebaseRooms.join(id) else api.request("POST", "/v1/crews/$id/join"); refreshNow() })
    override fun leaveCrew(id: String) = ignoreJob(work { if (firebaseMode) firebaseRooms.leave(id) else api.request("DELETE", "/v1/crews/$id/join"); refreshNow() })
    override fun setCrewDj(crewId: String, memberId: String, enabled: Boolean) = ignoreJob(work { if (firebaseMode) firebaseRooms.setDj(crewId, memberId, enabled) else api.request("PATCH", "/v1/crews/$crewId/members/$memberId", JSONObject().put("role", if (enabled) "dj" else "member")); refreshNow(); loadCrewMembers(crewId) })
    override fun createParty(input: NewParty) = ignoreJob(work {
        require(input.crewId != null && state.value.crews.any { it.id == input.crewId && it.joined }) { "가입한 크루에서 파티를 만들어 주세요." }
        val generation = communityGeneration
        val party = if (firebaseMode) firebaseRooms.createParty(input) else api.createParty(input).toParty()
        if (generation != communityGeneration) return@work
        mutableState.update { it.copy(parties = listOf(party) + it.parties.filterNot { old -> old.id == party.id }, lastCreatedPartyId = party.id) }
        refreshAfterCreation()
    })
    override fun joinParty(id: String) = ignoreJob(work {
        val party = state.value.parties.firstOrNull { it.id == id } ?: error("파티 정보를 새로고침해 주세요.")
        val crew = state.value.crews.firstOrNull { it.id == party.crewId } ?: error("먼저 크루에서 파티를 선택해 주세요.")
        val generation = communityGeneration
        try {
            if (!crew.joined) { if (firebaseMode) firebaseRooms.join(crew.id) else api.request("POST", "/v1/crews/${crew.id}/join") }
            check(generation == communityGeneration) { "연결이 바뀌었어요. 다시 시도해 주세요." }
            if (firebaseMode) firebaseRooms.join(id) else api.request("POST", "/v1/parties/$id/join")
        } finally { if (generation == communityGeneration) runCatching { refreshNow() } }
    })
    override fun leaveParty(id: String) = ignoreJob(work { if (firebaseMode) firebaseRooms.leave(id) else api.request("DELETE", "/v1/parties/$id/join"); refreshNow() })
    override fun cancelParty(id: String) = ignoreJob(work { if (firebaseMode) firebaseRooms.cancelParty(id) else api.request("POST", "/v1/parties/$id/cancel"); refreshNow() })
    override fun deleteParty(id: String) = ignoreJob(work {
        if (firebaseMode) firebaseRooms.deleteParty(id) else api.request("DELETE", "/v1/parties/$id")
        mutableState.update { it.copy(parties = it.parties.filterNot { party -> party.id == id }) }
        if (state.value.openedRoomId == "party:$id") closeChat()
        runCatching { refreshNow() }.onFailure {
            if (it is CancellationException) throw it
            mutableState.update { current -> current.copy(serverConnected = false, serverStatus = "파티는 삭제됐어요. 목록을 다시 불러와 주세요.") }
        }
    })
    override fun openChat(roomId: String, title: String) {
        mutableState.update { it.copy(openedRoomId = roomId, openedRoomTitle = title, messages = emptyList(), chatFromCache = true, chatCanNotice = false) }
        if (firebaseMode) {
            val generation = communityGeneration
            work {
                val allowed = firebaseRooms.canAnnounce(roomId.substringAfter(':'))
                mutableState.update { if (firebaseMode && generation == communityGeneration && it.openedRoomId == roomId) it.copy(chatCanNotice = allowed) else it }
            }
        }
        if (!firebaseMode) work { val messages = api.chat(roomId); mutableState.update { if (!firebaseMode && it.openedRoomId == roomId) it.copy(messages = messages) else it } }
    }
    override fun closeChat() { mutableState.update { it.copy(openedRoomId = "", openedRoomTitle = "", messages = emptyList()) } }
    override fun sendMessage(body: String, notice: Boolean) = ignoreJob(work {
        val roomId = state.value.openedRoomId
        require(roomId.contains(':')) { "채팅방을 선택해 주세요." }
        if (firebaseMode) { firebaseRooms.sendMessage(roomId.substringAfter(':'), body, notice); return@work }
        val (scope, id) = roomId.split(":", limit = 2)
        api.request("POST", "/v1/${if (scope == "crew") "crews" else "parties"}/$id/messages", JSONObject().put("body", body.trim()).put("kind", if (notice) "announcement" else "chat"))
        val messages = api.chat(roomId); mutableState.update { it.copy(messages = messages) }
    })
    override fun connectVoice(roomId: String) { if (firebaseMode) permissionDenied("현재 크루·파티는 채팅을 지원해요. 음성 통화 서버는 별도 연결이 필요해요.") else requests.tryEmit(UiRequest.MicrophonePermission(roomId)) }
    fun connectVoiceWithPermission(roomId: String) = work {
        check(!firebaseMode) { "현재 크루·파티에서는 채팅을 이용해 주세요. 음성 통화는 준비 중이에요." }
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
    private fun ignoreJob(job: Job) = Unit
    override fun onCleared() { profileConnectOperation++; profileConnectJob?.cancel(); weatherJob?.cancel(); areaJob?.cancel(); discoveryJob?.cancel(); audio.close(); super.onCleared() }
}

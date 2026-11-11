package com.runmoni.app.domain

interface AppActions {
    fun prepareHomeConditions() {}
    fun refreshWeather() {}
    fun locateExplore() {}
    fun setDiscoveryVisible(visible: Boolean) {}
    fun setDiscoveryBounds(bounds: RouteMapBounds) {}
    fun refreshRunners() {}
    fun checkNickname(name: String) {}
    fun confirmPublicLocation(runId: String, adultConfirmed: Boolean, requestNonce: Long) {}
    fun dismissSharingConsent(requestNonce: Long) {}
    fun startEntryGoogleSignIn() {}
    fun continueAsGuest() {}
    fun openRunNotificationSettings() {}
    fun addRunWidget() {}
    fun consumeRunNavigation(token: Long) {}
    fun startRun()
    fun pauseRun()
    fun resumeRun()
    fun finishRun()
    fun finishRun(expectedRunId: String) { finishRun() }
    fun recoverRun()
    fun retryRunPersistence() {}
    fun openLocationSettings()
    fun openAppPermissionSettings()
    fun dismissError()
    fun setAutoPause(enabled: Boolean)
    fun setVoiceGuidance(enabled: Boolean)
    fun setPublicLocation(enabled: Boolean)
    fun saveUnifiedProfile(name: String, expectedUid: String) { updateProfile(name) }
    fun saveUnifiedProfile(name: String, expectedUid: String, expectedBackend: CommunityBackend) { saveUnifiedProfile(name, expectedUid) }
    fun connectUnifiedProfile(name: String, expectedUid: String, expectedBackend: CommunityBackend) { connectCommunity(name) }
    fun updateProfile(name: String)
    fun setAvatarGender(gender: AvatarGender) {}
    fun setRunningKitColor(region: RunningKitRegion, color: Int) {}
    fun equipItem(slot: String, variant: Int)
    fun buyItem(slot: String, variant: Int)
    fun deleteRun(id: String)
    fun exportRun(id: String)
    fun canShareRun(record: RunRecord): Boolean = false
    fun configureServer(url: String)
    fun signIn(inviteCode: String, name: String)
    fun connectCommunity(name: String)
    fun signOut()
    fun signInGoogle()
    fun linkCommunityGoogle() {}
    fun restoreCommunityGoogle() {}
    fun signOutGoogle()
    fun checkRoomConnection() {}
    fun checkGoogleSession() {}
    fun setPhoneCheck(check: PhoneCheck, confirmed: Boolean) {}
    fun shareDeviceChecks() {}
    fun useFirebaseCommunity() {}
    fun loadCrewMembers(id: String) {}
    fun loadCrewParties(id: String) {}
    fun backupCloudRun(runId: String, expectedUid: String)
    fun refreshCloudSummaries(expectedUid: String)
    fun deleteCloudSummary(runId: String, expectedUid: String)
    fun refreshCommunity()
    fun createCrew(input: NewCrew)
    fun joinCrew(id: String)
    fun leaveCrew(id: String)
    fun setCrewDj(crewId: String, memberId: String, enabled: Boolean)
    fun createParty(input: NewParty)
    fun joinParty(id: String)
    fun leaveParty(id: String)
    fun cancelParty(id: String)
    fun deleteParty(id: String) { cancelParty(id) }
    fun openChat(roomId: String, title: String)
    fun closeChat()
    fun sendMessage(body: String, notice: Boolean)
    fun connectVoice(roomId: String)
    fun disconnectVoice()
    fun setMicrophone(enabled: Boolean)
    fun setVoiceVolume(value: Float)
    fun setMusicVolume(value: Float)
    fun selectMusicFile()
    fun shareExternalMusic(packageName: String)
    fun stopMusic()
}

package com.runmoni.app.domain

interface AppActions {
    fun startEntryGoogleSignIn() {}
    fun continueAsGuest() {}
    fun startRun()
    fun pauseRun()
    fun resumeRun()
    fun finishRun()
    fun recoverRun()
    fun retryRunPersistence() {}
    fun openLocationSettings()
    fun openAppPermissionSettings()
    fun dismissError()
    fun setAutoPause(enabled: Boolean)
    fun setVoiceGuidance(enabled: Boolean)
    fun setPublicLocation(enabled: Boolean)
    fun updateProfile(name: String)
    fun setAvatarGender(gender: AvatarGender) {}
    fun setRunningKitColor(region: RunningKitRegion, color: Int) {}
    fun equipItem(slot: String, variant: Int)
    fun buyItem(slot: String, variant: Int)
    fun deleteRun(id: String)
    fun exportRun(id: String)
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

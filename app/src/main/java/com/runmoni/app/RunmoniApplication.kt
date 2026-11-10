package com.runmoni.app

import android.app.Application
import com.runmoni.app.data.RunRepository
import com.runmoni.app.data.FirebaseCloudAccount
import com.runmoni.app.data.FirebaseCommunityRepository

class RunmoniApplication : Application() {
    val runs by lazy { RunRepository(this) }
    val cloud by lazy { FirebaseCloudAccount(this) }
    val community by lazy { FirebaseCommunityRepository(this) }
    val runnerPresence by lazy { com.runmoni.app.data.RunnerPresenceRepository(
        firestore = { community.presenceFirestore() }, acceptedUid = { if (getSharedPreferences("runmoni", MODE_PRIVATE).getString("communityBackend", BuildConfig.COMMUNITY_BACKEND) == "firebase") community.uid else "" }) }
    val runnerSharing by lazy { com.runmoni.app.data.RunnerSharingController(runnerPresence,
        acceptedUid = { if (getSharedPreferences("runmoni", MODE_PRIVATE).getString("communityBackend", BuildConfig.COMMUNITY_BACKEND) == "firebase") community.uid else "" }, publicProfile = {
            val name = community.currentNickname() ?: error("프로필 연결을 확인해 주세요")
            name to com.runmoni.app.data.LocalWallet(this).read().avatar
        }) }
    override fun onCreate() {
        super.onCreate()
        // Exact sharing always needs fresh consent after process restart.
        getSharedPreferences("runmoni", MODE_PRIVATE).edit().putBoolean("publicLocation", false).apply()
        // A new process has no live recorder. Old widget controls must be stale.
        com.runmoni.app.widget.RunWidgetPublisher.processStarted(this)
    }
}

package com.runmoni.app

import android.app.Application
import com.runmoni.app.data.RunRepository
import com.runmoni.app.data.FirebaseCloudAccount
import com.runmoni.app.data.FirebaseCommunityRepository

class RunmoniApplication : Application() {
    val runs by lazy { RunRepository(this) }
    val cloud by lazy { FirebaseCloudAccount(this) }
    val community by lazy { FirebaseCommunityRepository(this) }
    override fun onCreate() {
        super.onCreate()
        // Exact sharing always needs fresh consent after process restart.
        getSharedPreferences("runmoni", MODE_PRIVATE).edit().putBoolean("publicLocation", false).apply()
    }
}

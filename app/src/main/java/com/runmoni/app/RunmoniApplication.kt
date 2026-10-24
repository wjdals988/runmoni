package com.runmoni.app

import android.app.Application
import com.runmoni.app.data.RunRepository
import com.runmoni.app.data.FirebaseCloudAccount

class RunmoniApplication : Application() {
    val runs by lazy { RunRepository(this) }
    val cloud by lazy { FirebaseCloudAccount(this) }
    override fun onCreate() {
        super.onCreate()
        // Exact sharing always needs fresh consent after process restart.
        getSharedPreferences("runmoni", MODE_PRIVATE).edit().putBoolean("publicLocation", false).apply()
    }
}

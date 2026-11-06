package com.runmoni.app.data

import android.content.Context
import android.content.SharedPreferences
import com.runmoni.app.domain.AppEntryChoice

/** Entry is a local preference, never an authentication or community membership grant. */
internal class AppEntryStore(
    private val preferences: SharedPreferences,
    private val existingInstallation: Boolean,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("runmoni", Context.MODE_PRIVATE),
        isUpdatedInstallation(context),
    )

    fun initialChoice(): AppEntryChoice {
        val stored = preferences.getString(KEY, null)
        AppEntryChoice.entries.firstOrNull { it.wireValue == stored }?.let { return it }
        // App startup already writes publicLocation and creates Room. Neither is
        // evidence that a new user has chosen a way to enter the app.
        val initial = if (stored == null && (existingInstallation || preferences.contains("localProfile")))
            AppEntryChoice.EXISTING else AppEntryChoice.CHOOSE
        preferences.edit().putString(KEY, initial.wireValue).commit()
        return initial
    }

    fun save(choice: AppEntryChoice): Boolean {
        require(choice != AppEntryChoice.CHOOSE)
        return preferences.edit().putString(KEY, choice.wireValue).commit()
    }

    companion object {
        const val KEY = "appEntryChoiceV1"

        @Suppress("DEPRECATION")
        private fun isUpdatedInstallation(context: Context): Boolean = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.firstInstallTime > 0 && info.lastUpdateTime > info.firstInstallTime
        }.getOrDefault(false)
    }
}

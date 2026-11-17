package com.runmoni.app.data

import android.content.Context
import android.content.SharedPreferences
import com.runmoni.app.domain.AppAppearance

/** This file has no UID, location, account credentials, wallet or run IDs. */
internal class AppearancePreferences(context: Context) {
    val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun read(): AppAppearance = AppAppearance.fromWire(preferences.getString(KEY, null))
    fun write(mode: AppAppearance) { preferences.edit().putString(KEY, mode.wireValue).apply() }

    companion object {
        const val FILE = "runmoni_appearance"
        const val KEY = "mode"
    }
}

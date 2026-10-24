package com.runmoni.app.data

import android.content.Context
import com.runmoni.app.domain.Profile
import org.json.JSONArray
import org.json.JSONObject

/** Pilot device wallet. A persistent run-id ledger makes recovery/replayed events harmless. */
class LocalWallet(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("runmoni", Context.MODE_PRIVATE)
    fun read(): Profile = synchronized(lock) { readUnsafe() }
    fun update(transform: (Profile) -> Profile): Profile = synchronized(lock) {
        transform(readUnsafe()).also { profile -> check(preferences.edit().putString("localProfile", encode(profile)).commit()) }
    }
    fun credit(runId: String, points: Int): Profile = synchronized(lock) {
        val credited = preferences.getStringSet("creditedLocalRuns", emptySet()).orEmpty()
        val profile = readUnsafe()
        if (runId in credited) return@synchronized profile
        val updated = profile.copy(mileage = profile.mileage + points.coerceAtLeast(0))
        check(preferences.edit().putString("localProfile", encode(updated)).putStringSet("creditedLocalRuns", credited + runId).commit())
        updated
    }
    private fun readUnsafe(): Profile = runCatching {
        val json = JSONObject(preferences.getString("localProfile", "{}")!!)
        val array = json.optJSONArray("owned")
        Profile(json.optString("name", "러너"), with(PilotClient) { json.optJSONObject("avatar").toAvatar() }, json.optInt("mileage"),
            com.runmoni.app.domain.AvatarCatalogue.migrateOwned(array?.let { (0 until it.length()).map { i -> it.getString(i) }.toSet() } ?: emptySet()))
    }.getOrDefault(Profile())
    private fun encode(profile: Profile) = JSONObject().put("name", profile.name).put("mileage", profile.mileage)
        .put("avatar", PilotClient.encodeAvatar(profile.avatar))
        .put("owned", JSONArray(profile.ownedItems.toList())).toString()
    companion object { private val lock = Any() }
}

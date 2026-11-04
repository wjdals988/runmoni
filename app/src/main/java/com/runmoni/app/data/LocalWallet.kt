package com.runmoni.app.data

import android.content.Context
import android.content.SharedPreferences
import com.runmoni.app.domain.Profile
import org.json.JSONArray
import org.json.JSONObject

/** Pilot device wallet. A persistent run-id ledger makes recovery/replayed events harmless. */
class LocalWallet internal constructor(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("runmoni", Context.MODE_PRIVATE))
    fun read(): Profile = synchronized(lock) { readUnsafe() }
    fun update(transform: (Profile) -> Profile): Profile = synchronized(lock) {
        transform(readUnsafe()).also { profile ->
            check(preferences.edit().putString("localProfile", encode(profile)).commit()) {
                "변경 내용을 기기에 저장하지 못했어요. 다시 시도해 주세요."
            }
        }
    }
    fun credit(runId: String, points: Int): Profile = synchronized(lock) {
        val credited = preferences.getStringSet("creditedLocalRuns", emptySet()).orEmpty()
        val profile = readUnsafe()
        if (runId in credited) {
            // commit(false) may already have updated SharedPreferences' in-process map.
            // An id seen in that map is not durable until its failed commit is retried.
            if (runId in unconfirmedCredits) {
                check(preferences.edit().putString("localProfile", encode(profile)).putStringSet("creditedLocalRuns", credited).commit())
                unconfirmedCredits.remove(runId)
            }
            return@synchronized profile
        }
        val updated = profile.copy(mileage = profile.mileage + points.coerceAtLeast(0))
        unconfirmedCredits.add(runId)
        check(preferences.edit().putString("localProfile", encode(updated)).putStringSet("creditedLocalRuns", credited + runId).commit())
        unconfirmedCredits.remove(runId)
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
    companion object {
        private val lock = Any()
        // Shared across wallet instances, but deliberately not a second on-disk ledger.
        // After process death the actual persisted profile and ledger are read together.
        private val unconfirmedCredits = mutableSetOf<String>()
    }
}

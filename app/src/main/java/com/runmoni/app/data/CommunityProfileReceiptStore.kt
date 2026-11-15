package com.runmoni.app.data

import android.content.SharedPreferences
import com.runmoni.app.domain.CommunityProfileReceipt
import org.json.JSONObject

/** Private profile ACK only. Never stores an ID token, GPS route or wallet. */
internal class CommunityProfileReceiptStore(private val preferences: SharedPreferences, appName: String) {
    internal val key = "verifiedProfileV1:$appName"

    fun read(): CommunityProfileReceipt? = runCatching {
        val json = JSONObject(preferences.getString(key, null) ?: return@runCatching null)
        if (json.getInt("version") != 1) return@runCatching null
        val uid = json.getString("uid").takeIf { it.isNotBlank() && it.length <= 128 } ?: return@runCatching null
        fun optional(name: String): String? = if (json.isNull(name)) null else json.getString(name)
        val nickname = optional("nickname")
        val nicknameKey = optional("nicknameKey")
        if ((nickname?.length ?: 0) > 128 || (nicknameKey?.length ?: 0) > 128) return@runCatching null
        CommunityProfileReceipt(uid, nickname, nicknameKey)
    }.getOrNull()

    /** Receipt and pending marker must reach disk in the same XML commit. */
    fun write(receipt: CommunityProfileReceipt, pendingKey: String): Boolean = preferences.edit()
        .putString(key, JSONObject().put("version", 1).put("uid", receipt.uid)
            .put("nickname", receipt.nickname ?: JSONObject.NULL).put("nicknameKey", receipt.nicknameKey ?: JSONObject.NULL).toString())
        .remove(pendingKey).commit()

    fun clear(): Boolean = preferences.edit().remove(key).commit()
}

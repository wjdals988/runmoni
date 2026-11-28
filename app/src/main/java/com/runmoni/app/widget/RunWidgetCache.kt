package com.runmoni.app.widget

import android.content.Context
import android.content.SharedPreferences
import com.runmoni.app.domain.RunPhase
import com.runmoni.app.domain.RunWidgetSnapshot
import org.json.JSONObject

/** Separate private, replaceable metrics cache; never opens the canonical run DB. */
internal class RunWidgetCache(private val preferences: SharedPreferences) {
    fun read(): RunWidgetSnapshot? = runCatching {
        val json = JSONObject(preferences.getString(KEY, null) ?: return@runCatching null)
        if (json.getInt("version") != 1) return@runCatching null
        val id = json.getString("runId")
        if (id.length > 256) return@runCatching null
        RunWidgetSnapshot(
            runId = id,
            phase = RunPhase.valueOf(json.getString("phase")),
            distanceMeters = json.getDouble("distanceMeters").also { require(it.isFinite() && it >= 0) },
            activeDurationMillis = json.getLong("activeDurationMillis").also { require(it >= 0) },
            speedMetersPerSecond = if (json.isNull("speedMetersPerSecond")) null else
                json.getDouble("speedMetersPerSecond").also { require(it.isFinite() && it >= 0) },
            automaticPause = json.getBoolean("automaticPause"),
            blocked = json.getBoolean("blocked"),
            finishing = json.getBoolean("finishing"),
            updatedAtMillis = json.getLong("updatedAtMillis").also { require(it >= 0) },
        )
    }.getOrNull()

    fun write(snapshot: RunWidgetSnapshot) {
        preferences.edit().putString(KEY, encode(snapshot)).apply()
    }

    fun startToken(): String? = preferences.getString(START_TOKEN, null)
    fun writeStartToken(token: String?) { preferences.edit().putString(START_TOKEN, token).apply() }
    fun consumeStart(token: String): Boolean = startToken() == token && preferences.edit().remove(START_TOKEN).commit()
    fun savedRunId(): String? = preferences.getString(SAVED_RUN_ID, null)
    fun saveReceipt(runId: String?) {
        val edit = preferences.edit().putString(SAVED_RUN_ID, runId)
        if (runId == null) edit.apply() else edit.commit()
    }

    companion object {
        internal const val KEY = "metricsV1"
        private const val START_TOKEN = "idleStart"
        private const val SAVED_RUN_ID = "lastWidgetFinish"
        fun from(context: Context) = RunWidgetCache(context.applicationContext.getSharedPreferences("runmoni_widget", Context.MODE_PRIVATE))

        internal fun encode(snapshot: RunWidgetSnapshot): String = JSONObject()
            .put("version", 1).put("runId", snapshot.runId).put("phase", snapshot.phase.name)
            .put("distanceMeters", snapshot.distanceMeters).put("activeDurationMillis", snapshot.activeDurationMillis)
            .put("speedMetersPerSecond", snapshot.speedMetersPerSecond ?: JSONObject.NULL)
            .put("automaticPause", snapshot.automaticPause).put("blocked", snapshot.blocked).put("finishing", snapshot.finishing)
            .put("updatedAtMillis", snapshot.updatedAtMillis).toString()
    }
}

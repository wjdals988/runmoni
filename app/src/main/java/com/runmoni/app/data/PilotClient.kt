package com.runmoni.app.data

import com.runmoni.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PilotException(val status: Int, message: String) : Exception(message)
class PilotClient(var baseUrl: String, var token: String = "") {
    private val http = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()
    suspend fun request(method: String, path: String, data: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + path)
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        val body = data?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
        builder.method(method, if (method in setOf("POST", "PUT", "PATCH")) body ?: "{}".toRequestBody("application/json".toMediaType()) else null)
        http.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = if (raw.isBlank()) JSONObject() else runCatching { JSONObject(raw) }.getOrElse { throw PilotException(response.code, "서버 응답 형식이 올바르지 않습니다.") }
            if (!response.isSuccessful) {
                val error = json.optJSONObject("error")
                throw PilotException(response.code, error?.optString("message")?.takeIf { it.isNotBlank() } ?: "요청을 처리하지 못했습니다 (${response.code}).")
            }
            json
        }
    }
    suspend fun health() = request("GET", "/health")
    suspend fun login(inviteCode: String, nickname: String): JSONObject = request("POST", "/v1/auth/pilot", JSONObject().put("invite_code", inviteCode).put("nickname", nickname).put("adult_confirmed", true))
    suspend fun profile(): Profile = request("GET", "/v1/me").toProfile()
    suspend fun crews(): List<Crew> {
        val joined = request("GET", "/v1/crews").items().map { it.toCrew(true) }
        val discovered = request("GET", "/v1/crews/discover").items().map { it.toCrew(false) }
        return (joined + discovered.filter { d -> joined.none { it.id == d.id } })
    }
    suspend fun parties(): List<Party> = request("GET", "/v1/parties").items().map { it.toParty() }
    suspend fun members(crewId: String) = request("GET", "/v1/crews/$crewId/members").items().map { CrewMember(it.optString("user_id", it.optString("id")), it.optString("nickname"), it.optString("role") == "owner", it.optString("role") == "dj") }
    suspend fun chat(roomId: String): List<ChatMessage> {
        val (scope, id) = roomId.split(":", limit = 2)
        return request("GET", "/v1/${if (scope == "crew") "crews" else "parties"}/$id/messages").items().map { ChatMessage(it.optString("id"), roomId, it.optString("nickname"), it.optString("body"), it.optLong("created_at_ms"), it.optString("kind") == "announcement") }
    }
    suspend fun presence(): List<LiveRunner> = request("GET", "/v1/presence?scope_type=pilot").items().map {
        LiveRunner(it.optString("user_id"), it.optString("nickname"), it.optDouble("latitude"), it.optDouble("longitude"), it.optLong("updated_at_ms"), it.optJSONObject("avatar").toAvatar())
    }
    suspend fun syncRun(record: RunRecord): Int {
        val existing = request("POST", "/v1/runs", JSONObject().put("id", record.id).put("started_at_ms", record.startedAtMillis))
        // A retry after finalize committed must not re-upload to a closed run.
        if (existing.optString("status") == "finalized") return existing.optInt("reward_points")
        for (batch in record.points.chunked(500)) {
            val array = JSONArray()
            batch.forEach { p -> array.put(JSONObject().put("seq", p.sequence).put("latitude", p.latitude).put("longitude", p.longitude).put("accuracy_m", p.accuracyMeters.toDouble()).put("recorded_at_ms", p.recordedAtMillis).put("segment", p.segment)) }
            request("POST", "/v1/runs/${record.id}/tracks/batch", JSONObject().put("points", array))
        }
        val result = request("POST", "/v1/runs/${record.id}/finalize", JSONObject().put("ended_at_ms", record.finishedAtMillis).put("active_duration_s", record.activeDurationMillis / 1000.0))
        return result.optInt("reward_points")
    }
    suspend fun createCrew(input: NewCrew) = request("POST", "/v1/crews", JSONObject().put("name", input.name).put("description", input.description).put("region", input.region).put("pace_label", input.paceLabel))
    suspend fun createParty(input: NewParty) = request("POST", "/v1/parties", JSONObject().put("crew_id", input.crewId).put("title", input.title).put("kind", input.kind.name.lowercase()).put("starts_at_ms", input.startsAtMillis).put("meeting_point", input.meetingLabel).put("distance_km", input.distanceKm).put("pace_label", input.paceLabel).put("pace_min_per_km", 6.0).put("capacity", input.capacity).put("latitude", input.latitude ?: JSONObject.NULL).put("longitude", input.longitude ?: JSONObject.NULL))
    companion object {
        fun JSONObject.items(): List<JSONObject> = optJSONArray("items")?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } } ?: emptyList()
        fun JSONObject?.toAvatar() = AvatarStyle(this?.optInt("hair") ?: 0, this?.optInt("top") ?: 0, this?.optInt("bottom") ?: 0, this?.optInt("shoes") ?: 0, this?.optInt("accessory") ?: 0)
        fun JSONObject.toProfile(): Profile {
            val owned = optJSONArray("owned_items")?.let { array -> (0 until array.length()).map { array.optString(it) }.toSet() } ?: Profile().ownedItems
            return Profile(optString("nickname", "러너"), optJSONObject("avatar").toAvatar(), optInt("mileage"), owned)
        }
        fun JSONObject.toCrew(joined: Boolean) = Crew(optString("id"), optString("name"), optString("description"), optString("region"), optString("pace_label", "자유 페이스"), optInt("member_count"), joined, optString("role") == "owner", optString("role") == "dj", optString("invitation_code").takeIf { it.isNotBlank() && it != "null" })
        fun JSONObject.toParty() = Party(optString("id"), optString("crew_id"), optString("title"), if (optString("kind") == "remote") PartyKind.REMOTE else PartyKind.LOCAL, optLong("starts_at_ms"), optString("meeting_point"), if (isNull("latitude")) null else optDouble("latitude"), if (isNull("longitude")) null else optDouble("longitude"), optDouble("distance_km", 5.0), optString("pace_label", "자유 페이스"), optInt("capacity", 20), optInt("participant_count"), optBoolean("is_joined"), optString("status") == "cancelled")
    }
}

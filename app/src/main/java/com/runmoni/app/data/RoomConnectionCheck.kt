package com.runmoni.app.data

import com.runmoni.app.domain.RoomCheck
import java.net.URI
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Unauthenticated capability check; never follows redirects or transmits the guest token. */
object RoomConnectionCheck {
    private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    suspend fun check(baseUrl: String): RoomCheck = withContext(Dispatchers.IO) {
        try {
            val uri = URI(baseUrl)
            val loopback = uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2", "::1", "[::1]")
            if (uri.host == null || uri.userInfo != null || uri.query != null || uri.fragment != null ||
                (uri.scheme != "https" && !(uri.scheme == "http" && loopback))) return@withContext RoomCheck.UNSUPPORTED
            val local = loopback || InetAddress.getAllByName(uri.host).any { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }
            client.newCall(Request.Builder().url(baseUrl.trimEnd('/') + "/health").header("Accept", "application/json").build())
                .execute().use { response ->
                    if (!response.isSuccessful) return@withContext RoomCheck.UNREACHABLE
                    val source = response.body?.source() ?: return@withContext RoomCheck.UNSUPPORTED
                    // Read a bounded amount even when Content-Length is absent or incorrect.
                    if (source.request(32_769)) return@withContext RoomCheck.UNSUPPORTED
                    val bytes = source.readByteArray()
                    val body = JSONObject(String(bytes, Charsets.UTF_8))
                    val caps = body.optJSONObject("capabilities")
                    if (body.optString("status") != "ok" || caps?.opt("public_rooms") != true || caps.opt("guest_sessions") != true)
                        RoomCheck.UNSUPPORTED
                    else if (local) RoomCheck.LOCAL_READY else RoomCheck.PUBLIC_READY
                }
        } catch (_: Exception) { RoomCheck.UNREACHABLE }
    }
}

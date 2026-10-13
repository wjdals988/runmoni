package com.runmoni.app.audio

import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal data class BridgeLease(val room: String, val sessionId: String, val owner: String, val leaseId: String) {
    companion object {
        // This is display/routing data only. The bridge must verify the signature and active lease.
        fun readUnverified(token: String): BridgeLease {
            val pieces = token.split('.')
            require(pieces.size == 3) { "DJ 송출 토큰이 올바르지 않습니다" }
            val payload = JSONObject(String(Base64.decode(pieces[1], Base64.URL_SAFE or Base64.NO_WRAP)))
            require(payload.optString("role") == "dj") { "DJ 권한 토큰이 필요합니다" }
            return BridgeLease(payload.getString("room"), payload.getString("session_id"), payload.getString("sub"), payload.getString("lease_id"))
        }
    }
}

internal class MusicBridgeSocket(
    private val onMetrics: (Long, Long) -> Unit,
    private val onStopped: (String) -> Unit,
) : Closeable {
    private val client = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).build()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var closed = false
    @Volatile private var finishing = false
    @Volatile private var eofAcknowledged = false
    private val stoppedReported = AtomicBoolean(false)
    private val drained = CompletableDeferred<Unit>()
    private var sessionId = ""
    private var sent = 0L

    suspend fun open(url: String, token: String, lease: BridgeLease) {
        check(!closed && socket == null)
        sessionId = lease.sessionId
        val endpoint = URI(url)
        require(!endpoint.host.isNullOrBlank() && endpoint.userInfo == null &&
            (endpoint.scheme == "wss" || (endpoint.scheme == "ws" &&
                endpoint.host in setOf("127.0.0.1", "10.0.2.2", "localhost")))) {
            "송출 서버는 wss:// 주소가 필요합니다. USB/에뮬레이터 개발 주소만 ws://를 허용합니다"
        }
        val ready = CompletableDeferred<Unit>()
        fun fail(problem: String) {
            ready.completeExceptionally(IllegalStateException(problem))
            drained.completeExceptionally(IllegalStateException(problem))
            if (!closed && stoppedReported.compareAndSet(false, true)) onStopped(problem)
        }
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (closed) { webSocket.close(1000, "stop"); return }
                val start = JSONObject().put("type", "start").put("version", 1)
                    .put("sampleRate", 48000).put("channels", 2).put("codec", "opus")
                    .put("frameMs", 20).put("room", lease.room).put("sessionId", lease.sessionId)
                if (!webSocket.send(start.toString())) fail("음악 송출 연결이 닫혔습니다")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (closed) return
                if (event.optString("type") == "ready" && event.optString("sessionId") == sessionId) ready.complete(Unit)
                if (event.optString("type") == "finished" && finishing && event.optString("sessionId") == sessionId) {
                    eofAcknowledged = true
                    drained.complete(Unit)
                }
                if (event.optString("type") == "error") {
                    val problem = event.optString("message", "음악 송출 연결 실패")
                    fail(problem)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Do not include request URL, bearer tokens, or remote exception details in logs/UI.
                fail("음악 송출 연결이 끊겼습니다. DJ 송출을 다시 시작해 주세요")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed && !eofAcknowledged)
                    fail("음악 송출이 종료되었습니다 ($code). DJ 권한/유효기간을 확인해 주세요")
                webSocket.close(code, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed && !eofAcknowledged)
                    fail("음악 송출이 종료되었습니다 ($code). DJ 권한/유효기간을 확인해 주세요")
            }
        })
        try {
            withTimeout(15_000) { ready.await() }
        } catch (failure: Exception) {
            close()
            throw failure
        }
    }

    fun send(packet: ByteArray, timestampMicros: Long) {
        check(!closed && !finishing) { "음악 송출이 종료되었습니다" }
        val active = checkNotNull(socket)
        // 16 KiB is approximately one second at 128 kbps. Never accumulate minutes of old music.
        if (active.queueSize() > 16_384) {
            close()
            throw IllegalStateException("음악 송출이 지연되어 중단했습니다. 연결 상태를 확인해 주세요")
        }
        val frame = ByteBuffer.allocate(8 + packet.size).order(ByteOrder.BIG_ENDIAN)
            .putLong(timestampMicros).put(packet).array()
        check(active.send(frame.toByteString())) { "음악 송출 연결이 닫혔습니다" }
        sent += frame.size
        onMetrics(sent, active.queueSize())
    }

    /** EOF is acknowledged only after the bridge has consumed its codec/native source tail. */
    suspend fun finish() {
        check(!closed) { "음악 송출이 종료되었습니다" }
        if (!finishing) {
            finishing = true
            val end = JSONObject().put("type", "end").put("sessionId", sessionId)
            check(checkNotNull(socket).send(end.toString())) { "음악 송출 연결이 닫혔습니다" }
        }
        withTimeout(7_000) { drained.await() }
        close()
    }

    override fun close() {
        if (closed) return
        closed = true
        drained.cancel()
        socket?.close(1000, "stop")
        socket = null
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}

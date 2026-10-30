package com.runmoni.app.data

import com.runmoni.app.domain.RoomCheck
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Local socket fixtures exercise the installed Android HTTP client, not a public deployment. */
class RoomConnectionCheckTest {
    private fun response(body: String, code: String = "200 OK"): Pair<RoomCheck, String> {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 15000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val request = executor.submit<String> {
                    server.accept().use { socket ->
                        socket.soTimeout = 15000
                        val headers = buildString {
                            val input = socket.getInputStream().bufferedReader()
                            while (true) { val line = input.readLine() ?: break; if (line.isEmpty()) break; appendLine(line) }
                        }
                        val bytes = body.toByteArray()
                        val header = "HTTP/1.1 $code\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nLocation: http://127.0.0.1:1/never-follow\r\nConnection: close\r\n\r\n"
                        socket.getOutputStream().apply { write(header.toByteArray()); write(bytes); flush() }
                        headers
                    }
                }
                val result = runBlocking { RoomConnectionCheck.check("http://127.0.0.1:${server.localPort}") }
                return result to request.get(15, TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }

    @Test fun completeCapabilityResponseIsLocalAndUnauthenticated() {
        val (result, request) = response("""{"status":"ok","capabilities":{"public_rooms":true,"guest_sessions":true}}""")
        assertEquals(RoomCheck.LOCAL_READY, result)
        assertTrue(request.startsWith("GET /health "))
        assertFalse(request.lowercase().contains("authorization:"))
        assertFalse(request.lowercase().contains("cookie:"))
    }

    @Test fun genericOkRedirectAndOversizedResponsesAreNotReady() {
        assertEquals(RoomCheck.UNSUPPORTED, response("""{"status":"ok"}""").first)
        assertEquals(RoomCheck.UNREACHABLE, response("", "302 Found").first)
        assertEquals(RoomCheck.UNSUPPORTED, response("x".repeat(32769)).first)
    }
}

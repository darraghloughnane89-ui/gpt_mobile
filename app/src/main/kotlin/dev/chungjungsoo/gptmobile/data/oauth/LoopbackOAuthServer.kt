package dev.chungjungsoo.gptmobile.data.oauth

import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LoopbackOAuthServer {
    private val nonce = UUID.randomUUID().toString().replace("-", "")
    private val path = "/callback/$nonce"
    private var socket: ServerSocket? = null

    val redirectUri: String
        get() {
            val port = socket?.localPort ?: error("Sign-in listener is not open")
            return "http://127.0.0.1:$port$path"
        }

    fun open() {
        if (socket != null) return
        socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    }

    suspend fun awaitCallback(timeoutMs: Int = 180_000): OAuthCallback = withContext(Dispatchers.IO) {
        val server = socket ?: error("Sign-in listener is not open")
        server.soTimeout = timeoutMs
        try {
            server.accept().use { client ->
                client.soTimeout = 10_000
                val requestLine = readRequestLine(client.getInputStream())
                if (requestLine.isEmpty()) error("Sign-in callback was empty")
                val parsed = LoopbackOAuthRequests.parseRequestLine(requestLine, path)
                    ?: error("Sign-in callback did not match this login")
                val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n" +
                    "<html><body><p>You can return to GPT Mobile.</p></body></html>"
                client.getOutputStream().use { output ->
                    output.write(response.toByteArray())
                    output.flush()
                }
                parsed
            }
        } catch (timeout: SocketTimeoutException) {
            error("Sign-in timed out before the browser returned")
        }
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun readRequestLine(input: InputStream): String {
        val line = StringBuilder()
        while (line.length < 4096) {
            val next = input.read()
            if (next < 0 || next == '\n'.code) break
            if (next != '\r'.code) line.append(next.toChar())
        }
        return line.toString()
    }
}

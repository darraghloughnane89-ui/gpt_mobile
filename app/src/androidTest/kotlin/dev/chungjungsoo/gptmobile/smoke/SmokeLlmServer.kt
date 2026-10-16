package dev.chungjungsoo.gptmobile.smoke

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tiny loopback chat-completions server. MockWebServer pulls OkHttp internals that
 * are not in the app APK, so the harness speaks HTTP itself.
 */
class SmokeLlmServer : AutoCloseable {
    private val socket = ServerSocket()
    private val running = AtomicBoolean(true)
    private val paths = mutableListOf<String>()
    private val clients = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "smoke-llm").apply { isDaemon = true }
    }
    val port: Int

    val requestedPaths: List<String>
        get() = synchronized(paths) { paths.toList() }

    init {
        socket.bind(InetSocketAddress("127.0.0.1", 0))
        port = socket.localPort
        Thread({ acceptLoop() }, "smoke-llm-accept").apply { isDaemon = true }.start()
    }

    override fun close() {
        running.set(false)
        runCatching { socket.close() }
        clients.shutdownNow()
    }

    private fun acceptLoop() {
        while (running.get()) {
            val client = try {
                socket.accept()
            } catch (_: Exception) {
                break
            }
            clients.execute { runCatching { handle(client) } }
        }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 10_000
            val input = socket.getInputStream()
            val headerBytes = readHeaders(input)
            val header = headerBytes.toString(StandardCharsets.ISO_8859_1)
            val requestLine = header.lineSequence().firstOrNull().orEmpty()
            val path = requestLine.split(" ").getOrNull(1).orEmpty()
            synchronized(paths) { paths += path }
            val length = header.lineSequence()
                .firstOrNull { line -> line.startsWith("Content-Length:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
                ?.toIntOrNull()
                ?: 0
            if (length > 0) {
                var remaining = length
                val buffer = ByteArray(minOf(remaining, 8192))
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (read < 0) break
                    remaining -= read
                }
            }
            val streaming = path.contains("chat/completions")
            val payload = if (streaming) chatStream("Smoke", "ReplyOk") else "unexpected $path"
            val bytes = payload.toByteArray(StandardCharsets.UTF_8)
            val status = if (streaming) "200 OK" else "404 Not Found"
            val type = if (streaming) "text/event-stream" else "text/plain"
            val response = "HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            val output = socket.getOutputStream()
            output.write(response.toByteArray(StandardCharsets.ISO_8859_1))
            output.write(bytes)
            output.flush()
        }
    }

    private fun readHeaders(input: InputStream): ByteArray {
        val header = ByteArrayOutputStream()
        var matched = 0
        val marker = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        while (header.size() < MAX_HEADER_BYTES) {
            val next = input.read()
            if (next < 0) break
            val byte = next.toByte()
            header.write(next)
            matched = when {
                byte == marker[matched] -> matched + 1
                byte == marker[0] -> 1
                else -> 0
            }
            if (matched == marker.size) break
        }
        return header.toByteArray()
    }

    private fun chatStream(vararg pieces: String): String = buildString {
        pieces.forEach { piece ->
            append("data: {")
            append("\"id\":\"chatcmpl-smoke\",\"object\":\"chat.completion.chunk\",")
            append("\"choices\":[{\"index\":0,\"delta\":{\"content\":\"$piece\"}}]}")
            append("}\n\n")
        }
        append("data: {")
        append("\"id\":\"chatcmpl-smoke\",\"object\":\"chat.completion.chunk\",")
        append("\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]")
        append("}\n\n")
        append("data: [DONE]\n\n")
    }

    private companion object {
        const val MAX_HEADER_BYTES = 64 * 1024
    }
}

package dev.chungjungsoo.gptmobile.data.fetch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.agent.tool.SafePageFetch
import dev.chungjungsoo.gptmobile.data.agent.tool.SafePageFetchException
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SafePageFetchTest {

    private val servers = mutableListOf<HttpServer>()

    @After
    fun stopServers() {
        servers.forEach { it.stop(0) }
    }

    @Test
    fun fetch_privateAddress_isRejected() {
        val error = assertThrows(SafePageFetchException::class.java) {
            runBlocking {
                SafePageFetch.fetch("http://fixture.test/secret", dns("fixture.test", "10.1.2.3"))
            }
        }

        assertEquals("unsafe DNS address rejected", error.message)
    }

    @Test
    fun rejectUnsafe_loopback_matchesWebSearchContract() {
        val error = assertThrows(SafePageFetchException::class.java) {
            SafePageFetch.rejectUnsafe(listOf(InetAddress.getByName("127.0.0.1")))
        }

        assertEquals("unsafe DNS address rejected", error.message)
    }

    @Test
    fun fetch_publicPage_returnsBody() = runBlocking {
        val server = server { exchange ->
            exchange.respond(200, "text/html; charset=utf-8", "<article>Hello</article>")
        }

        val page = SafePageFetch.fetch(
            start = server.url("fixture.test", "/note"),
            dns = dns("fixture.test", "127.0.0.1"),
            method = "GET",
            headers = emptyMap(),
            body = null,
            allowAddress = { it.isLoopbackAddress }
        )

        assertEquals("<article>Hello</article>", page)
    }

    @Test
    fun fetch_redirectToPrivateHost_isRejected() {
        val server = server { exchange ->
            exchange.respond(302, "text/plain", "", "Location" to "http://metadata.test/latest")
        }

        val error = assertThrows(SafePageFetchException::class.java) {
            runBlocking {
                SafePageFetch.fetch(
                    start = server.url("fixture.test", "/go"),
                    dns = dns("fixture.test" to listOf("127.0.0.1"), "metadata.test" to listOf("169.254.169.254")),
                    method = "GET",
                    headers = emptyMap(),
                    body = null,
                    allowAddress = { it.isLoopbackAddress }
                )
            }
        }

        assertTrue(error.message == "unsafe DNS address rejected")
    }

    private fun server(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> handler(exchange) }
        server.start()
        servers += server
        return server
    }

    private fun HttpServer.url(host: String, path: String): String = "http://$host:${address.port}$path"

    private fun HttpExchange.respond(
        status: Int,
        contentType: String,
        body: String,
        vararg headers: Pair<String, String>
    ) {
        responseHeaders.add("Content-Type", contentType)
        headers.forEach { (name, value) -> responseHeaders.add(name, value) }
        val bytes = body.toByteArray(Charsets.UTF_8)
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun dns(host: String, vararg addresses: String): Dns = dns(host to addresses.toList())

    private fun dns(vararg entries: Pair<String, List<String>>): Dns {
        val map = entries.toMap()
        return Dns { host ->
            map.getValue(host).map { InetAddress.getByName(it) }
        }
    }
}

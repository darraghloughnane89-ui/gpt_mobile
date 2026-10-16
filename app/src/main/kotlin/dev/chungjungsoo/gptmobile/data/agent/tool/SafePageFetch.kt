package dev.chungjungsoo.gptmobile.data.agent.tool

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.yield
import okhttp3.Dns

internal object SafePageFetch {
    suspend fun fetch(start: String, dns: Dns = Dns.SYSTEM): String {
        var current = parse(start) ?: throw SafePageFetchException("url must be http or https")
        var redirects = 0
        val seen = mutableSetOf(current.toASCIIString())
        while (true) {
            val pinned = resolveSafe(current.host, dns)
            val response = request(current, pinned)
            try {
                val status = response.status.value
                if (status in REDIRECTS) {
                    if (redirects >= MAX_REDIRECTS) throw SafePageFetchException("too many redirects")
                    val location = response.headers[HttpHeaders.Location]?.trim().orEmpty()
                    val next = resolveRedirect(current, location)
                    if (!seen.add(next.toASCIIString())) throw SafePageFetchException("redirect loop")
                    redirects += 1
                    current = next
                    continue
                }
                if (!response.status.isSuccess()) throw SafePageFetchException("HTTP $status")
                val contentType = response.headers[HttpHeaders.ContentType].orEmpty().lowercase(Locale.US)
                if (contentType.isNotBlank() && !isText(contentType)) throw SafePageFetchException("binary content rejected")
                return readBounded(response).toString(StandardCharsets.UTF_8)
            } finally {
                response.call.client.close()
            }
        }
    }

    internal fun parse(value: String): URI? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawFragment != null) return null
        return uri
    }

    internal fun rejectUnsafe(addresses: List<InetAddress>) {
        if (addresses.isEmpty() || addresses.any(SpecialUseAddress::isSpecialUse)) {
            throw SafePageFetchException("unsafe DNS address rejected")
        }
    }

    private fun resolveSafe(host: String, dns: Dns): List<InetAddress> {
        val addresses = try {
            dns.lookup(host)
        } catch (exception: UnknownHostException) {
            throw SafePageFetchException("DNS lookup failed")
        }
        rejectUnsafe(addresses)
        return addresses
    }

    private suspend fun request(uri: URI, pinned: List<InetAddress>): HttpResponse {
        val pinnedDns = Dns { hostname ->
            if (!hostname.equals(uri.host, ignoreCase = true)) throw UnknownHostException(hostname)
            pinned
        }
        val client = HttpClient(OkHttp) {
            followRedirects = false
            engine {
                dns = pinnedDns
                clientCacheSize = 0
                config {
                    followRedirects(false)
                    followSslRedirects(false)
                    proxy(Proxy.NO_PROXY)
                }
            }
        }
        return try {
            client.get(uri.toASCIIString()) {
                header(HttpHeaders.UserAgent, "GPTMobile/1.0")
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml,text/plain")
            }
        } catch (exception: Exception) {
            client.close()
            throw exception
        }
    }

    private fun resolveRedirect(base: URI, location: String): URI {
        if (location.isBlank()) throw SafePageFetchException("missing Location header")
        val next = try {
            base.resolve(URI(location))
        } catch (ignored: Exception) {
            throw SafePageFetchException("malformed redirect URL")
        }
        return parse(next.toASCIIString()) ?: throw SafePageFetchException("malformed redirect URL")
    }

    private suspend fun readBounded(response: HttpResponse): ByteArray {
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_BODY_BYTES) throw SafePageFetchException("response too large")
        val channel = response.bodyAsChannel()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read == -1) break
            if (read == 0) {
                yield()
                continue
            }
            output.write(buffer, 0, read)
            if (output.size() > MAX_BODY_BYTES) throw SafePageFetchException("response too large")
        }
        return output.toByteArray()
    }

    private fun isText(contentType: String): Boolean = contentType.startsWith("text/") ||
        contentType.startsWith("application/xhtml") ||
        contentType.startsWith("application/xml") ||
        contentType.startsWith("application/json")

    private const val MAX_BODY_BYTES = 1024 * 1024
    private const val MAX_REDIRECTS = 5
    private val REDIRECTS = setOf(301, 302, 303, 307, 308)
}

internal class SafePageFetchException(message: String) : Exception(message)

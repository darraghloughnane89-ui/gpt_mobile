package dev.chungjungsoo.gptmobile.data.gguf

import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.HuggingFaceDownloadAuth
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HuggingFaceGgufMetadataClient @Inject constructor(
    private val tokenStore: HuggingFaceTokenStore
) : GgufRemoteMetadataSource {
    override suspend fun lookup(url: String): GgufPublishedMetadata {
        val link = HuggingFaceGgufUrl.parse(url) ?: return GgufPublishedMetadata(sizeBytes = null, sha256 = null)
        val token = runCatching { tokenStore.readAccessToken() }.getOrNull()
        val pointer = readPrefix(link.pointerUrl, token)
        GgufLfsPointer.parse(pointer)?.let { return it }
        return GgufPublishedMetadata(sizeBytes = contentLength(link.downloadUrl, token), sha256 = null)
    }

    private fun readPrefix(url: String, token: String?): String {
        val connection = HuggingFaceDownloadAuth.openConnection(
            url = url,
            accessToken = token,
            extraHeaders = mapOf("Range" to "bytes=0-511"),
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS
        )
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                return ""
            }
            connection.inputStream.use { input ->
                val buffer = ByteArray(512)
                val read = input.read(buffer)
                if (read <= 0) "" else buffer.copyOf(read).toString(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            ""
        } finally {
            connection.disconnect()
        }
    }

    private fun contentLength(url: String, token: String?): Long? {
        var current = url
        repeat(MAX_REDIRECTS) {
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "HEAD"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                if (!token.isNullOrBlank() && HuggingFaceDownloadAuth.shouldAttachBearerToken(current)) {
                    connection.setRequestProperty("Authorization", HuggingFaceTokenStore.bearerHeader(token))
                }
                connection.connect()
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return null
                    current = URL(URL(current), location).toString()
                } else if (code == HttpURLConnection.HTTP_OK) {
                    return connection.contentLengthLong.takeIf { it > 0L }
                } else {
                    return null
                }
            } catch (_: Exception) {
                return null
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_REDIRECTS = 4
    }
}

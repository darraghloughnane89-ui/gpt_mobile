package dev.chungjungsoo.gptmobile.data.gguf

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import kotlinx.coroutines.yield

class GgufDownloader(
    private val httpClient: HttpClient
) {
    suspend fun download(url: String, destination: File, onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit) {
        val normalized = url.trim()
        require(normalized.startsWith("https://") || normalized.startsWith("http://")) { "Enter an http or https URL" }
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, destination.name + ".partial")
        try {
            httpClient.prepareGet(normalized) {
                timeout {
                    requestTimeoutMillis = Long.MAX_VALUE
                    socketTimeoutMillis = 120_000
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    error("Download failed (${response.status.value})")
                }
                val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                val channel = response.bodyAsChannel()
                partial.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var readTotal = 0L
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break
                        if (read == 0) {
                            yield()
                            continue
                        }
                        output.write(buffer, 0, read)
                        readTotal += read
                        onProgress(readTotal, total)
                    }
                }
                if (destination.exists()) destination.delete()
                if (!partial.renameTo(destination)) error("Could not save the downloaded model")
            }
        } catch (error: Throwable) {
            partial.delete()
            if (!destination.exists()) destination.delete()
            throw error
        }
    }
}

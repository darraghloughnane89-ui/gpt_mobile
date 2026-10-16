package dev.chungjungsoo.gptmobile.data.voice

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

interface MiMoSpeechTransport {
    suspend fun transcribe(apiKey: String, wav: ByteArray, language: String = "auto"): String

    fun synthesize(apiKey: String, text: String, voice: String): Flow<ByteArray>
}

class MiMoSpeechClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = MiMoSpeechModels.BASE_URL
) : MiMoSpeechTransport {
    override suspend fun transcribe(apiKey: String, wav: ByteArray, language: String): String {
        val payload = MiMoSpeechProtocol.transcriptionBody(
            mimeType = "audio/wav",
            audioBase64 = Base64.getEncoder().encodeToString(wav),
            language = language
        )
        val body = post(apiKey, payload, ContentType.Application.Json)
        return MiMoSpeechProtocol.transcript(body)
    }

    override fun synthesize(apiKey: String, text: String, voice: String): Flow<ByteArray> = flow {
        val payload = MiMoSpeechProtocol.synthesisBody(text = text, voice = voice, stream = true)
        httpClient.preparePost(MiMoSpeechProtocol.completionsUrl(baseUrl)) {
            accept(ContentType.Text.EventStream)
            applySpeechTimeout()
            applySpeechAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
                throw MiMoSpeechException(MiMoSpeechProtocol.redact(response.status.value, errorBody, apiKey))
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readLine() ?: break
                val chunk = try {
                    MiMoSpeechProtocol.audioChunks(line)
                } catch (error: MiMoSpeechException) {
                    throw MiMoSpeechException(MiMoSpeechProtocol.redact(response.status.value, error.message.orEmpty(), apiKey))
                } ?: continue
                if (chunk.isNotEmpty()) emit(chunk)
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun post(apiKey: String, payload: String, accept: ContentType): String =
        httpClient.preparePost(MiMoSpeechProtocol.completionsUrl(baseUrl)) {
            accept(accept)
            applySpeechTimeout()
            applySpeechAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.execute { response ->
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw MiMoSpeechException(MiMoSpeechProtocol.redact(response.status.value, body, apiKey))
            }
            body
        }
}

private fun HttpRequestBuilder.applySpeechAuth(apiKey: String) {
    header("api-key", apiKey)
    header(HttpHeaders.Authorization, "Bearer $apiKey")
}

private fun HttpRequestBuilder.applySpeechTimeout() {
    timeout {
        connectTimeoutMillis = 20_000
        requestTimeoutMillis = 120_000
        socketTimeoutMillis = 60_000
    }
}

package dev.chungjungsoo.gptmobile.data.hermes

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface HermesTransport {
    suspend fun probe(endpoint: String, profile: String, token: String): HermesProbeResult

    suspend fun streamChat(
        route: HermesResolvedRoute,
        token: String,
        sessionId: String,
        sessionKey: String,
        idempotencyKey: String,
        body: String,
        onAccepted: suspend () -> Unit,
        onEvent: suspend (HermesStreamEvent) -> Unit
    )
}

@Singleton
class OkHttpHermesTransport @Inject constructor() : HermesTransport {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun probe(endpoint: String, profile: String, token: String): HermesProbeResult = withContext(Dispatchers.IO) {
        val parsed = parseHermesEndpoint(endpoint)
            ?: return@withContext HermesProbeResult.Failed("Invalid endpoint.")
        val health = get("${parsed.base}/health", null)
        val reachable = health is HttpOk || get("${parsed.base}/v1/health", null) is HttpOk
        if (!reachable) {
            val detail = (health as? HttpFailed)?.detail ?: (health as? HttpStatus)?.detail ?: "No response."
            return@withContext if (health is HttpFailed) {
                HermesProbeResult.Unreachable(detail)
            } else {
                HermesProbeResult.Failed(detail)
            }
        }
        if (token.isBlank()) return@withContext HermesProbeResult.NeedsKey

        val direct = get("${directApiRoot(parsed.base)}/models", token)
        val multiplex = if (isValidHermesProfile(profile) && profile != "default") {
            get("${multiplexApiRoot(parsed.base, profile)}/models", token)
        } else {
            null
        }
        when {
            multiplex is HttpOk -> success(HermesRouteMode.MULTIPLEX, multiplex.body, parsed, profile, token)
            multiplex is HttpStatus && multiplex.code == 401 && direct is HttpOk -> HermesProbeResult.ProfileMismatch(profile)
            direct is HttpOk -> success(HermesRouteMode.DIRECT, direct.body, parsed, profile, token)
            direct is HttpStatus && direct.code == 401 -> HermesProbeResult.Unauthorized
            multiplex is HttpStatus && multiplex.code == 401 -> HermesProbeResult.Unauthorized
            else -> HermesProbeResult.Failed(
                (direct as? HttpStatus)?.detail
                    ?: (multiplex as? HttpStatus)?.detail
                    ?: (direct as? HttpFailed)?.detail
                    ?: "Model list failed."
            )
        }
    }

    override suspend fun streamChat(
        route: HermesResolvedRoute,
        token: String,
        sessionId: String,
        sessionKey: String,
        idempotencyKey: String,
        body: String,
        onAccepted: suspend () -> Unit,
        onEvent: suspend (HermesStreamEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(route.apiRoot.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
            .header("X-Hermes-Session-Id", headerValue(sessionId))
            .header("X-Hermes-Session-Key", headerValue(sessionKey))
            .header("Idempotency-Key", headerValue(idempotencyKey))
            .post(body.toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = redact(response.body.string(), token)
                    throw HermesHttpException(response.code, errorBody.ifBlank { "HTTP ${response.code}" })
                }
                onAccepted()
                val mediaType = response.body.contentType()?.toString().orEmpty()
                if (mediaType.contains("json") && !mediaType.contains("event-stream")) {
                    HermesSseParser.parseJsonBody(response.body.string()).forEach { event -> onEvent(event) }
                    return@use
                }
                val parser = HermesSseParser()
                response.body.byteStream().bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        parser.consume(line).forEach { event -> onEvent(event) }
                    }
                }
                parser.finish().forEach { event -> onEvent(event) }
            }
        } catch (error: HermesHttpException) {
            throw error
        } catch (error: IOException) {
            throw HermesNetworkException(error)
        }
    }

    private fun success(
        mode: HermesRouteMode,
        modelsBody: String,
        parsed: HermesEndpoint,
        profile: String,
        token: String
    ): HermesProbeResult {
        val root = if (mode == HermesRouteMode.MULTIPLEX) multiplexApiRoot(parsed.base, profile) else directApiRoot(parsed.base)
        val capabilities = get("$root/capabilities", token)
        val streaming = capabilities !is HttpOk || capabilitiesStreaming(capabilities.body)
        return HermesProbeResult.Success(
            mode = mode,
            modelId = parseModelId(modelsBody) ?: hermesModelId(profile, null),
            streaming = streaming
        )
    }

    private fun get(url: String, token: String?): HttpResult {
        val builder = Request.Builder().url(url).get()
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        return try {
            client.newCall(builder.build()).execute().use { response ->
                val body = redact(response.body.string(), token.orEmpty())
                if (response.isSuccessful) HttpOk(body) else HttpStatus(response.code, body)
            }
        } catch (error: IOException) {
            HttpFailed(error.message ?: "Network error")
        } catch (error: IllegalArgumentException) {
            HttpFailed(error.message ?: "Bad URL")
        }
    }

    private sealed interface HttpResult

    private data class HttpOk(val body: String) : HttpResult

    private data class HttpStatus(val code: Int, val detail: String) : HttpResult

    private data class HttpFailed(val detail: String) : HttpResult

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val json = Json { ignoreUnknownKeys = true }

        fun headerValue(value: String): String = value.replace(Regex("[\\r\\n\\u0000]"), "").take(255)

        fun redact(text: String, token: String): String = if (token.isBlank()) text else text.replace(token, "•••")

        fun parseModelId(body: String): String? = try {
            val data = (json.parseToJsonElement(body) as? JsonObject)?.get("data") as? JsonArray
            data.orEmpty().firstNotNullOfOrNull { item ->
                ((item as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
            }
        } catch (_: Exception) {
            null
        }

        fun capabilitiesStreaming(body: String): Boolean = try {
            val root = json.parseToJsonElement(body) as? JsonObject
            val features = root?.get("features") as? JsonObject
            val flag = (root?.get("chat_completions") as? JsonPrimitive)?.contentOrNull
                ?: (features?.get("chat_completions") as? JsonPrimitive)?.contentOrNull
            flag != "false"
        } catch (_: Exception) {
            true
        }
    }
}

package dev.chungjungsoo.gptmobile.data.hermes

import kotlinx.serialization.Serializable

object HermesDefaults {
    const val PROFILE = "cbre-admin"
    const val PLATFORM_UID = "hermes-direct-chat"
    const val PLATFORM_NAME = "Hermes"
    const val SECRET_REF = "hermes_api_server_key"
    const val SESSION_PREFIX = "robin"
    const val WORK_NAME = "hermes-delivery"
    const val MODEL_FALLBACK = "hermes-agent"
    const val MAX_IMAGE_BYTES = 1_500_000
    const val MAX_TEXT_FILE_CHARS = 32_000
    const val MAX_READ_BYTES = 2_000_000
    const val MAX_HISTORY_CHARS = 16_000
    const val MAX_TRANSCRIPT_CHARS = 100_000
    const val MAX_SUMMARY_CHARS = 6_000
    const val MAX_AUTO_ATTEMPTS = 6
    const val PLATFORM_TIMEOUT_SECONDS = 600
    val PROFILE_PATTERN = Regex("[A-Za-z0-9._-]{1,64}")
}

enum class HermesRouteMode {
    UNKNOWN,
    DIRECT,
    MULTIPLEX
}

enum class HermesHandoffKind {
    MESSAGE,
    TRANSCRIPT,
    SUMMARY,
    FILE,
    TASK
}

enum class HermesRole {
    USER,
    ASSISTANT
}

enum class HermesDeliveryStatus {
    QUEUED,
    SENDING,
    STREAMING,
    DELIVERED,
    FAILED,
    INTERRUPTED
}

@Serializable
data class HermesAttachment(
    val displayName: String,
    val path: String,
    val mimeType: String = ""
)

data class HermesHandoffRequest(
    val kind: HermesHandoffKind,
    val text: String,
    val attachments: List<HermesAttachment> = emptyList()
)

@Serializable
data class HermesTurn(
    val id: String,
    val role: HermesRole,
    val kind: HermesHandoffKind = HermesHandoffKind.MESSAGE,
    val text: String = "",
    val status: HermesDeliveryStatus = HermesDeliveryStatus.DELIVERED,
    val error: String? = null,
    val toolNotes: List<String> = emptyList(),
    val idempotencyKey: String = "",
    val accepted: Boolean = false,
    val attachments: List<HermesAttachment> = emptyList(),
    val createdAtEpochMs: Long = 0L
)

@Serializable
data class HermesThread(
    val id: String,
    val title: String,
    val turns: List<HermesTurn> = emptyList()
)

@Serializable
data class HermesThreadSnapshot(
    val threads: List<HermesThread> = emptyList(),
    val activeThreadId: String = ""
)

data class HermesSettings(
    val endpoint: String = "",
    val profile: String = HermesDefaults.PROFILE,
    val routeMode: HermesRouteMode = HermesRouteMode.UNKNOWN,
    val modelId: String = "",
    val streaming: Boolean = true,
    val chatModelEnabled: Boolean = false
)

data class HermesEndpoint(
    val base: String,
    val profileHint: String? = null
)

data class HermesResolvedRoute(
    val apiRoot: String,
    val mode: HermesRouteMode,
    val modelId: String
)

sealed class HermesProbeResult {
    data class Success(
        val mode: HermesRouteMode,
        val modelId: String,
        val streaming: Boolean
    ) : HermesProbeResult()

    data class Unreachable(val detail: String) : HermesProbeResult()

    data object Unauthorized : HermesProbeResult()

    data class ProfileMismatch(val profile: String) : HermesProbeResult()

    data object NeedsKey : HermesProbeResult()

    data class Failed(val detail: String) : HermesProbeResult()
}

sealed class HermesStreamEvent {
    data class Text(val text: String) : HermesStreamEvent()

    data class Tool(val label: String) : HermesStreamEvent()

    data class Completed(val output: String?) : HermesStreamEvent()

    data class Failed(val message: String) : HermesStreamEvent()
}

class HermesHttpException(
    val code: Int,
    message: String
) : Exception(message) {
    val retryable: Boolean = code == 408 || code == 429 || code in 500..599
}

class HermesNetworkException(cause: Throwable) : Exception(cause.message ?: "Network error", cause)

enum class HermesHandoffResult {
    QUEUED,
    QUEUED_NEEDS_SETUP
}

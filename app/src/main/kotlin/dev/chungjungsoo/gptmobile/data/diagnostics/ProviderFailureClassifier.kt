package dev.chungjungsoo.gptmobile.data.diagnostics

enum class ProviderFailureKind {
    RATE_LIMIT,
    AUTH,
    TIMEOUT,
    NETWORK,
    GENERIC;

    val showsFriendlyState: Boolean
        get() = this != GENERIC
}

object ProviderFailureClassifier {
    fun classify(message: String): ProviderFailureKind? {
        val text = message.lowercase()
        return when {
            STATUS_429.containsMatchIn(message) ||
                "rate limit" in text ||
                "too many requests" in text ||
                "quota" in text -> ProviderFailureKind.RATE_LIMIT

            STATUS_401.containsMatchIn(message) ||
                STATUS_403.containsMatchIn(message) ||
                "unauthorized" in text ||
                "invalid api key" in text ||
                "invalid_api_key" in text ||
                "incorrect api key" in text ||
                "authentication" in text ||
                rejectedApiKey(text) -> ProviderFailureKind.AUTH

            "timed out" in text ||
                "timeout" in text ||
                "time-out" in text -> ProviderFailureKind.TIMEOUT

            "network error" in text ||
                "unable to resolve" in text ||
                "unknownhost" in text ||
                "unknown host" in text ||
                "connection refused" in text ||
                "connection reset" in text ||
                "failed to connect" in text ||
                "ssl/tls" in text -> ProviderFailureKind.NETWORK

            else -> null
        }
    }

    private fun rejectedApiKey(text: String): Boolean {
        if ("api key" !in text) return false
        return "invalid" in text || "incorrect" in text || "rejected" in text || "missing" in text
    }

    private val STATUS_429 = Regex("""(?<!\d)429(?!\d)""")
    private val STATUS_401 = Regex("""(?<!\d)401(?!\d)""")
    private val STATUS_403 = Regex("""(?<!\d)403(?!\d)""")
}

data class ChatFailurePresentation(
    val kind: ProviderFailureKind,
    val visibleAnswer: String
)

private const val ASSISTANT_ERROR_PREFIX = "Error:"
private const val RESPONSE_STOPPED_MARKER = "\n\n[Response stopped: "

fun presentChatFailure(content: String): ChatFailurePresentation? {
    val detail = assistantFailureDetail(content) ?: return null
    val kind = ProviderFailureClassifier.classify(detail) ?: return null
    if (!kind.showsFriendlyState) return null
    return ChatFailurePresentation(kind, visibleAnswerBeforeFailure(content))
}

fun displayChatText(content: String): String {
    presentChatFailure(content)?.let { return it.visibleAnswer }
    if (content.trimStart().startsWith(ASSISTANT_ERROR_PREFIX)) {
        return DiagnosticRedactor.redact(content)
    }
    return content
}

private fun assistantFailureDetail(content: String): String? {
    val trimmed = content.trimStart()
    if (trimmed.startsWith(ASSISTANT_ERROR_PREFIX)) {
        return trimmed.removePrefix(ASSISTANT_ERROR_PREFIX).trim()
    }
    val index = content.lastIndexOf(RESPONSE_STOPPED_MARKER)
    if (index >= 0 && content.trimEnd().endsWith("]")) {
        val end = content.trimEnd()
        return end.substring(index + RESPONSE_STOPPED_MARKER.length, end.length - 1).trim()
    }
    return null
}

private fun visibleAnswerBeforeFailure(content: String): String {
    if (content.trimStart().startsWith(ASSISTANT_ERROR_PREFIX)) return ""
    val index = content.lastIndexOf(RESPONSE_STOPPED_MARKER)
    return if (index >= 0) content.substring(0, index) else ""
}

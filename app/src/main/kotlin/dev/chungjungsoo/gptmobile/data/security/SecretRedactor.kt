package dev.chungjungsoo.gptmobile.data.security

object SecretRedactor {
    private val bearer = Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=\\-]{8,}")
    private val providerKey = Regex("\\bsk-[A-Za-z0-9_\\-]{8,}")
    private val apiKeyHeader = Regex("(?i)(x-api-key|x-goog-api-key)([:=]\\s*)\\S+")

    fun redact(message: String): String = message
        .replace(bearer) { match -> match.groupValues[1] + "[redacted]" }
        .replace(providerKey, "[redacted]")
        .replace(apiKeyHeader) { match -> match.groupValues[1] + match.groupValues[2] + "[redacted]" }
}

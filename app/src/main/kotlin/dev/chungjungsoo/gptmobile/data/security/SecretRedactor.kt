package dev.chungjungsoo.gptmobile.data.security

object SecretRedactor {
    private val bearer = Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=\\-]{8,}")
    private val providerKey = Regex("\\bsk-[A-Za-z0-9_\\-]{8,}")
    private val apiKeyHeader = Regex("(?i)(x-api-key|x-goog-api-key)([:=]\\s*)\\S+")
    private val githubToken = Regex("\\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{8,}|\\bgithub_pat_[A-Za-z0-9_]{8,}")
    private val googleKey = Regex("\\bAIza[A-Za-z0-9_\\-]{20,}")
    private val slackToken = Regex("\\bxox[baprs]-[A-Za-z0-9-]{8,}")
    private val awsAccessKey = Regex("\\bAKIA[A-Z0-9]{16}\\b")

    fun redact(message: String): String = message
        .replace(bearer) { match -> match.groupValues[1] + "[redacted]" }
        .replace(providerKey, "[redacted]")
        .replace(apiKeyHeader) { match -> match.groupValues[1] + match.groupValues[2] + "[redacted]" }
        .replace(githubToken, "[redacted]")
        .replace(googleKey, "[redacted]")
        .replace(slackToken, "[redacted]")
        .replace(awsAccessKey, "[redacted]")
}

package dev.chungjungsoo.gptmobile.data.obsidian

private val bearerSecret = Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=\\-]{8,}")
private val providerKey = Regex("\\bsk-[A-Za-z0-9_\\-]{8,}")
private val githubToken = Regex("\\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{8,}|\\bgithub_pat_[A-Za-z0-9_]{8,}")
private val googleKey = Regex("\\bAIza[A-Za-z0-9_\\-]{20,}")

fun redactVaultOutbound(text: String): String = text
    .replace(bearerSecret) { match -> match.groupValues[1] + "[redacted]" }
    .replace(providerKey, "[redacted]")
    .replace(githubToken, "[redacted]")
    .replace(googleKey, "[redacted]")

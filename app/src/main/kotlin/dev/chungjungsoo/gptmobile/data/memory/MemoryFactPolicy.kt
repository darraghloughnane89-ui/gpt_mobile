package dev.chungjungsoo.gptmobile.data.memory

import dev.chungjungsoo.gptmobile.data.security.SecretRedactor

object MemoryFactPolicy {
    fun sanitize(text: String): String? {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        val withoutMarker = flat.substringBefore(MemoryPromptComposer.MARKER).replace(Regex("\\s+"), " ").trim()
        val redacted = SecretRedactor.redact(withoutMarker).trim()
        if (redacted.isEmpty() || redacted == "[redacted]") return null
        return redacted
    }
}

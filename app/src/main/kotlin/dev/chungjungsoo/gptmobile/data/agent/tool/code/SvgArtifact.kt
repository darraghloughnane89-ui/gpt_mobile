package dev.chungjungsoo.gptmobile.data.agent.tool.code

/**
 * Accepts a small standalone SVG for the chat preview.
 * Rejected markup is not rendered. AndroidSVG draws accepted markup with no
 * script engine and no external file resolver.
 */
internal object SvgArtifact {
    const val MAX_CHARS = 12_000

    private val eventHandler = Regex("""on[a-z]{2,}\s*=""", RegexOption.IGNORE_CASE)
    private val externalReference = Regex(
        """(?:href|xlink:href)\s*=\s*['"]?\s*(?:javascript:|https?:|file:|data:|//)""",
        RegexOption.IGNORE_CASE
    )
    private val externalUrl = Regex(
        """url\s*\(\s*['"]?\s*(?:javascript:|https?:|file:|data:|//)""",
        RegexOption.IGNORE_CASE
    )
    private val blockedTag = Regex(
        """<\s*(?:script|foreignObject|iframe|embed|object|audio|video|link|meta|handler)\b""",
        RegexOption.IGNORE_CASE
    )

    fun sanitize(markup: String): String? {
        val trimmed = markup.trim().removePrefix("\uFEFF")
        if (trimmed.length !in 1..MAX_CHARS) return null
        if (!trimmed.contains("<svg", ignoreCase = true)) return null
        val start = trimmed.indexOf("<svg", ignoreCase = true)
        if (start < 0) return null
        val prefix = trimmed.substring(0, start).trim()
        if (prefix.isNotEmpty() && !prefix.startsWith("<?xml")) return null
        val body = trimmed
        if (body.contains("<!")) return null
        if (blockedTag.containsMatchIn(body)) return null
        if (eventHandler.containsMatchIn(body)) return null
        if (externalReference.containsMatchIn(body)) return null
        if (externalUrl.containsMatchIn(body)) return null
        if (body.contains("javascript:", ignoreCase = true)) return null
        if (body.contains("expression(", ignoreCase = true)) return null
        return trimmed
    }
}

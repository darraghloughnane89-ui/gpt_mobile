package dev.chungjungsoo.gptmobile.data.widget

/**
 * Removes credential-shaped text before it can be drawn on a home or lock widget.
 * Applied even when the user allows lock-screen content.
 */
object WidgetSecrets {
    private val patterns = listOf(
        Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----"""),
        Regex("""sk-(?:proj-|ant-)?[A-Za-z0-9_\-]{8,}"""),
        Regex("""AIza[0-9A-Za-z_\-]{20,}"""),
        Regex("""ghp_[A-Za-z0-9]{20,}"""),
        Regex("""github_pat_[A-Za-z0-9_]{20,}"""),
        Regex("""xox[baprs]-[A-Za-z0-9\-]{10,}"""),
        Regex("""(?i)\bbearer\s+[A-Za-z0-9\-._~+/]+=*"""),
        Regex("""(?i)\b(api[_-]?key|access[_-]?token|token|secret|password)\s*[:=]\s*\S+""")
    )

    fun redact(text: String): String = patterns.fold(text) { current, pattern ->
        pattern.replace(current, REDACTED)
    }

    const val REDACTED = "••••"
}

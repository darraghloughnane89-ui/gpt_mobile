package dev.chungjungsoo.gptmobile.data.diagnostics

/**
 * Strips secrets and, by default, chat content from text that may be written to disk or shared.
 * Sharing stays user-initiated; this only prepares the text.
 */
object DiagnosticRedactor {
    const val REDACTED = "[redacted]"
    const val REDACTED_CHAT = "[redacted chat]"

    fun redact(raw: String, includeChatContent: Boolean = false): String {
        val withoutSecrets = redactSecrets(raw)
        return if (includeChatContent) withoutSecrets else redactChatContent(withoutSecrets)
    }

    private fun redactSecrets(raw: String): String {
        var text = URL_USERINFO.replace(raw) { match ->
            "${match.groupValues[1]}$REDACTED:$REDACTED@"
        }
        text = JSON_SECRET.replace(text) { match ->
            "${match.groupValues[1]}$REDACTED${match.groupValues[3]}"
        }
        text = LABELED_SECRET.replace(text) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}$REDACTED"
        }
        text = QUERY_SECRET.replace(text) { match ->
            "${match.groupValues[1]}$REDACTED"
        }
        text = BEARER.replace(text, "Bearer $REDACTED")
        text = OPENAI_KEY.replace(text, REDACTED)
        text = GOOGLE_KEY.replace(text, REDACTED)
        text = GROQ_KEY.replace(text, REDACTED)
        text = HUGGING_FACE_KEY.replace(text, REDACTED)
        text = JWT.replace(text, REDACTED)
        return text
    }

    private fun redactChatContent(raw: String): String {
        var text = JSON_CHAT.replace(raw) { match ->
            "${match.groupValues[1]}$REDACTED_CHAT${match.groupValues[3]}"
        }
        text = LABELED_CHAT.replace(text) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}$REDACTED_CHAT"
        }
        text = ROLE_TEXT.replace(text) { match ->
            "${match.groupValues[1]}: $REDACTED_CHAT"
        }
        return text
    }

    private val URL_USERINFO = Regex("""([a-zA-Z][a-zA-Z0-9+.-]*://)([^/\s:@]+):([^/\s@]+)@""")
    private val JSON_SECRET = Regex(
        """(?i)("(?:authorization|api[_-]?key|apiKey|access[_-]?token|accessToken|refresh[_-]?token|refreshToken|id[_-]?token|idToken|client[_-]?secret|clientSecret|password|passwd|secret|token|credential)"\s*:\s*")([^"\\]*)(")"""
    )
    private val LABELED_SECRET = Regex(
        """(?i)\b(authorization|api[_-]?key|access[_-]?token|refresh[_-]?token|id[_-]?token|client[_-]?secret|password|passwd|secret|token|credential)(\s*[:=]\s*|\s*["']\s*:\s*["'])([^\s"',;&]+)"""
    )
    private val QUERY_SECRET = Regex(
        """(?i)([?&](?:api[_-]?key|access_token|token|password|secret)=)[^&#\s]+"""
    )
    private val BEARER = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]{8,}""")
    private val OPENAI_KEY = Regex("""\bsk-[A-Za-z0-9_-]{16,}""")
    private val GOOGLE_KEY = Regex("""\bAIza[0-9A-Za-z_-]{20,}""")
    private val GROQ_KEY = Regex("""\bgsk_[A-Za-z0-9]{16,}""")
    private val HUGGING_FACE_KEY = Regex("""\bhf_[A-Za-z0-9]{16,}""")
    private val JWT = Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}""")
    private val JSON_CHAT = Regex(
        """(?i)("(?:content|text|prompt|message|input|query|completion|system_prompt|systemPrompt)"\s*:\s*")((?:\\.|[^"\\])*)(")"""
    )
    private val LABELED_CHAT = Regex(
        """(?i)\b(content|prompt|user_message|assistant_message|system_prompt|systemPrompt)(\s*[:=]\s*)("[^"]*"|'[^']*'|\S+)"""
    )
    private val ROLE_TEXT = Regex("""(?i)\b(user|assistant|system|human|developer)\s*:\s*\S.*""")
}

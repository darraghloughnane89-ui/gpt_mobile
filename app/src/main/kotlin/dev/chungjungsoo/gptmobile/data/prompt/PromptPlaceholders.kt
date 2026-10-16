package dev.chungjungsoo.gptmobile.data.prompt

/**
 * Placeholders are `{name}` tokens. The name starts with a letter and may contain
 * letters, digits, and underscores. The same name is filled once and applied everywhere it appears.
 */
object PromptPlaceholders {
    private val pattern = Regex("""\{([A-Za-z][A-Za-z0-9_]{0,40})\}""")

    fun names(body: String): List<String> {
        val seen = linkedSetOf<String>()
        pattern.findAll(body).forEach { match ->
            seen += match.groupValues[1]
        }
        return seen.toList()
    }

    fun fill(body: String, values: Map<String, String>): String {
        if (values.isEmpty()) return body
        return pattern.replace(body) { match ->
            values[match.groupValues[1]] ?: match.value
        }
    }
}

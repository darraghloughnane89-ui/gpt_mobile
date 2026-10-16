package dev.chungjungsoo.gptmobile.data.skill

/**
 * SKILL.md-style import and export. Front matter carries the routing fields.
 * The body is the procedure. Adapted from the Agent Skills layout Hermes uses.
 */
object SkillMarkdown {
    fun export(skill: Skill): String {
        val tools = SkillCodec.tools(skill)
        val examples = SkillCodec.examples(skill)
        return buildString {
            appendLine("---")
            appendLine("name: ${skill.stableName}")
            appendLine("description: ${yamlScalar(skill.description)}")
            if (tools.isNotEmpty()) {
                appendLine("tools: [${tools.joinToString(", ")}]")
            }
            if (examples.isNotEmpty()) {
                appendLine("examples:")
                examples.forEach { example -> appendLine("  - ${example.replace("\n", " ")}") }
            }
            appendLine("---")
            appendLine()
            appendLine("# ${skill.title}")
            appendLine()
            append(skill.instructions.trim())
            appendLine()
        }
    }

    fun parse(markdown: String): SkillDraft? {
        val text = markdown.trim()
        if (!text.startsWith("---")) return null
        val marker = text.indexOf("\n---", startIndex = 3)
        if (marker < 0) return null
        val header = text.substring(3, marker).trim()
        val afterMarker = text.indexOf('\n', marker + 1)
        val body = if (afterMarker < 0) "" else text.substring(afterMarker + 1).trim()
        val fields = parseHeader(header)
        val name = SkillNames.slug(fields.single("name").orEmpty()) ?: return null
        val description = fields.single("description").orEmpty().trim()
        if (description.isEmpty()) return null
        val lines = body.lines()
        val heading = lines.firstOrNull { it.isNotBlank() }.orEmpty()
        val title = if (heading.startsWith("# ")) heading.removePrefix("# ").trim().ifBlank { name } else name
        val instructions = if (heading.startsWith("# ")) {
            lines.dropWhile { it.isBlank() }.drop(1).joinToString("\n").trim()
        } else {
            body
        }
        if (instructions.isBlank()) return null
        return SkillDraft(
            name = name,
            title = title,
            description = description.take(SkillNames.MAX_DESCRIPTION),
            instructions = instructions.take(SkillNames.MAX_INSTRUCTIONS),
            tools = fields.many("tools"),
            examples = fields.many("examples"),
            origin = SkillOrigin.IMPORTED
        )
    }

    private fun yamlScalar(value: String): String {
        val singleLine = value.replace("\n", " ").trim()
        val escaped = singleLine.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }

    private fun parseHeader(header: String): HeaderFields {
        val singles = linkedMapOf<String, String>()
        val many = linkedMapOf<String, MutableList<String>>()
        var listKey: String? = null
        header.lines().forEach { raw ->
            val line = raw.trimEnd()
            if (line.isBlank()) return@forEach
            val item = line.trim()
            if (item.startsWith("- ")) {
                val key = listKey ?: return@forEach
                many.getOrPut(key) { mutableListOf() }.add(item.removePrefix("- ").trim().trim('"'))
                return@forEach
            }
            val colon = line.indexOf(':')
            if (colon <= 0) return@forEach
            val key = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            listKey = null
            if (value.isEmpty()) {
                listKey = key
                many.getOrPut(key) { mutableListOf() }
            } else if (value.startsWith("[") && value.endsWith("]")) {
                val items = value.removePrefix("[").removeSuffix("]")
                    .split(",")
                    .map { it.trim().trim('"', '\'') }
                    .filter { it.isNotEmpty() }
                many[key] = items.toMutableList()
            } else {
                singles[key] = unquote(value)
            }
        }
        return HeaderFields(singles, many)
    }

    private fun unquote(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
            return trimmed.substring(1, trimmed.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return trimmed
    }

    private class HeaderFields(
        private val singles: Map<String, String>,
        private val many: Map<String, List<String>>
    ) {
        fun single(key: String): String? = singles[key]

        fun many(key: String): List<String> = many[key].orEmpty().ifEmpty {
            singles[key]?.let { listOf(it) }.orEmpty()
        }
    }
}

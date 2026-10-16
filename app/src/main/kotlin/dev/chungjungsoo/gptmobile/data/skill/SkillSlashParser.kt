package dev.chungjungsoo.gptmobile.data.skill

/**
 * Leading slash commands, matching Hermes: up to five skill names, then the request.
 * `/learn` asks the learning loop to review the conversation.
 * Parsing stops at the first token that is not a known skill or `/learn`.
 */
object SkillSlashParser {
    const val LEARN = "learn"
    const val MAX_SKILLS = 5

    data class Result(
        val skillNames: List<String>,
        val saveAsSkill: Boolean,
        val remainder: String
    )

    fun parse(input: String, knownNames: Set<String>): Result {
        val known = knownNames.associateBy { it.lowercase() }
        val trimmed = input.trim()
        if (!trimmed.startsWith("/")) {
            return Result(emptyList(), false, trimmed)
        }
        val skills = mutableListOf<String>()
        var save = false
        var index = 0
        while (index < trimmed.length && skills.size < MAX_SKILLS) {
            if (trimmed[index] != '/') break
            val tokenStart = index + 1
            var tokenEnd = tokenStart
            while (tokenEnd < trimmed.length && !trimmed[tokenEnd].isWhitespace()) tokenEnd++
            val token = trimmed.substring(tokenStart, tokenEnd)
            val canonical = known[token.lowercase()]
            if (token.equals(LEARN, ignoreCase = true)) {
                save = true
            } else if (canonical != null && canonical !in skills) {
                skills += canonical
            } else {
                break
            }
            index = tokenEnd
            while (index < trimmed.length && trimmed[index].isWhitespace()) index++
        }
        return Result(skills, save, trimmed.substring(index).trim())
    }

    fun isBareLearn(input: String): Boolean {
        val parsed = parse(input, emptySet())
        return parsed.saveAsSkill && parsed.skillNames.isEmpty() && parsed.remainder.isEmpty()
    }
}

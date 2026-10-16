package dev.chungjungsoo.gptmobile.data.skill

data class SkillSelectionInput(
    val skills: List<Skill>,
    val userText: String,
    val manualNames: List<String> = emptyList(),
    val botLinkedIds: Set<Long>? = null,
    val maxSkills: Int = SkillSelector.MAX_FULL_SKILLS,
    val charCap: Int = SkillSelector.CHAR_CAP
)

data class SkillSelection(
    val text: String,
    val injectedIds: List<Long>,
    val indexedOnly: Boolean,
    val truncated: Boolean
)

/**
 * Embedding-free skill choice. Manual slash names win, then keyword overlap.
 * When nothing matches, the model receives short descriptions and picks.
 * Hermes truncates each routing description to 60 characters.
 */
object SkillSelector {
    const val MAX_FULL_SKILLS = 4
    const val CHAR_CAP = 3500
    private const val MIN_SCORE = 2

    private val stopWords = setOf(
        "a", "an", "the", "to", "for", "of", "and", "or", "in", "on", "with", "me", "my",
        "please", "how", "what", "when", "use", "using", "this", "that", "it", "is", "are",
        "do", "does", "i", "you", "we", "can", "could", "would", "should", "from", "into",
        "about", "want", "need", "help", "just", "be", "as", "at", "by", "your"
    )

    fun select(input: SkillSelectionInput): SkillSelection {
        val enabled = input.skills.filter { it.enabled }
        val pool = if (input.botLinkedIds == null) {
            enabled
        } else {
            enabled.filter { it.id in input.botLinkedIds }
        }
        val byName = enabled.associateBy { it.stableName.lowercase() }
        val manual = input.manualNames.mapNotNull { byName[it.lowercase()] }.distinctBy { it.id }
        val manualIds = manual.map { it.id }.toSet()
        val scored = pool
            .filter { it.id !in manualIds }
            .map { skill -> skill to score(input.userText, skill) }
            .filter { it.second >= MIN_SCORE }
            .sortedByDescending { it.second }
        val ranked = (manual + scored.map { it.first }).distinctBy { it.id }.take(input.maxSkills.coerceAtLeast(manual.size.coerceAtMost(SkillSlashParser.MAX_SKILLS)))
        val chosen = if (manual.size > input.maxSkills) manual.take(SkillSlashParser.MAX_SKILLS) else ranked
        if (chosen.isNotEmpty()) {
            return renderFull(chosen, input.charCap)
        }
        if (pool.isEmpty()) {
            return SkillSelection("", emptyList(), indexedOnly = false, truncated = false)
        }
        return renderIndex(pool, input.charCap)
    }

    private fun score(query: String, skill: Skill): Int {
        if (query.contains(skill.stableName, ignoreCase = true)) return 5 + tokenScore(query, skill)
        return tokenScore(query, skill)
    }

    private fun tokenScore(query: String, skill: Skill): Int {
        val queryTokens = tokens(query, minimum = 3)
        if (queryTokens.isEmpty()) return 0
        val nameTokens = tokens("${skill.stableName.replace('-', ' ')} ${skill.title}", minimum = 2)
        val descriptionTokens = tokens(skill.description, minimum = 3)
        val exampleTokens = tokens(SkillCodec.examples(skill).joinToString(" "), minimum = 3)
        var score = 0
        queryTokens.forEach { token ->
            score += when {
                token in nameTokens -> 3
                token in descriptionTokens -> 2
                token in exampleTokens -> 1
                else -> 0
            }
        }
        return score
    }

    private fun tokens(text: String, minimum: Int): Set<String> = text.lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= minimum && it !in stopWords }
        .toSet()

    private fun renderFull(skills: List<Skill>, charCap: Int): SkillSelection {
        val kept = mutableListOf<Skill>()
        var truncated = false
        skills.forEach { skill ->
            val candidate = kept + skill
            if (fullText(candidate).length <= charCap || kept.isEmpty()) {
                kept += skill
                if (fullText(kept).length > charCap) truncated = true
            } else {
                truncated = true
            }
        }
        var text = fullText(kept)
        if (text.length > charCap) {
            truncated = true
            text = text.take(charCap.coerceAtLeast(1)).trimEnd() + "\n..."
        }
        val injected = kept.filter { text.contains("### ${it.stableName}") }.map { it.id }
        return SkillSelection(
            text = text,
            injectedIds = injected,
            indexedOnly = false,
            truncated = truncated
        )
    }

    private fun fullText(skills: List<Skill>): String {
        val body = skills.joinToString("\n\n") { skill -> skillBlock(skill) }
        return buildString {
            appendLine("## Skills")
            appendLine("Follow a procedure below when it matches the request. A skill does not grant tools beyond the ones already assigned.")
            append(body)
        }.trimEnd()
    }

    private fun skillBlock(skill: Skill): String = buildString {
        appendLine("### ${skill.stableName}")
        appendLine("When to use: ${skill.description.trim()}")
        val tools = SkillCodec.tools(skill)
        if (tools.isNotEmpty()) appendLine("Tools: ${tools.joinToString(", ")}")
        val examples = SkillCodec.examples(skill)
        if (examples.isNotEmpty()) appendLine("Examples: ${examples.joinToString("; ")}")
        append(skill.instructions.trim())
    }.trimEnd()

    private fun renderIndex(skills: List<Skill>, charCap: Int): SkillSelection {
        val header = buildString {
            appendLine("## Skill catalog")
            append("These are short descriptions. Apply a skill only when the request clearly matches its name. Otherwise answer normally.")
        }
        val lines = skills.map { skill ->
            "- ${skill.stableName}: ${SkillNames.routingDescription(skill.description)}"
        }
        val kept = mutableListOf<String>()
        var truncated = false
        lines.forEach { line ->
            val next = (listOf(header) + kept + line).joinToString("\n")
            if (next.length <= charCap) {
                kept += line
            } else {
                truncated = true
            }
        }
        if (kept.isEmpty()) {
            return SkillSelection("", emptyList(), indexedOnly = true, truncated = true)
        }
        return SkillSelection(
            text = (listOf(header) + kept).joinToString("\n"),
            injectedIds = emptyList(),
            indexedOnly = true,
            truncated = truncated
        )
    }
}

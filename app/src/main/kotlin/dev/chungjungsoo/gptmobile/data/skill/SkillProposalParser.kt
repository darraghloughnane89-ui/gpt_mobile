package dev.chungjungsoo.gptmobile.data.skill

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ParsedSkillProposal(
    val action: String,
    val name: String,
    val description: String,
    val instructions: String,
    val tools: List<String>,
    val examples: List<String>,
    val rationale: String
)

data class ParsedReview(
    val skills: List<ParsedSkillProposal>,
    val memories: List<String>
)

object SkillProposalParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(raw: String): ParsedReview? {
        val payload = extractJson(raw) ?: return null
        val document = try {
            json.decodeFromString(ReviewDocument.serializer(), payload)
        } catch (error: Exception) {
            return null
        }
        val skills = document.skills.mapNotNull { item ->
            val action = normalizeAction(item.action) ?: return@mapNotNull null
            val name = SkillNames.slug(item.name).orEmpty()
            if (action != SkillProposalAction.MEMORY && name.isEmpty()) return@mapNotNull null
            if (action == SkillProposalAction.CREATE || action == SkillProposalAction.UPDATE) {
                if (item.instructions.isBlank() || item.description.isBlank()) return@mapNotNull null
            }
            ParsedSkillProposal(
                action = action,
                name = name,
                description = item.description.trim().take(SkillNames.MAX_DESCRIPTION),
                instructions = item.instructions.trim().take(SkillNames.MAX_INSTRUCTIONS),
                tools = item.tools.map { it.trim() }.filter { it.isNotEmpty() },
                examples = item.examples.map { it.trim() }.filter { it.isNotEmpty() },
                rationale = item.rationale.trim()
            )
        }
        val memories = document.memories.map { it.text.trim() }.filter { it.isNotEmpty() }.map { it.take(280) }
        if (skills.isEmpty() && memories.isEmpty()) return ParsedReview(emptyList(), emptyList())
        return ParsedReview(skills, memories)
    }

    private fun normalizeAction(raw: String): String? = when (raw.trim().lowercase()) {
        "create", "new" -> SkillProposalAction.CREATE
        "update", "edit", "patch", "refine" -> SkillProposalAction.UPDATE
        "retire", "delete", "archive" -> SkillProposalAction.RETIRE
        else -> null
    }

    private fun extractJson(raw: String): String? {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        val source = fenced?.takeIf { it.startsWith("{") } ?: raw.trim()
        val start = source.indexOf('{')
        val end = source.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return source.substring(start, end + 1)
    }
}

@Serializable
private data class ReviewDocument(
    val skills: List<ReviewSkillDocument> = emptyList(),
    val memories: List<ReviewMemoryDocument> = emptyList()
)

@Serializable
private data class ReviewSkillDocument(
    val action: String = "",
    val name: String = "",
    val description: String = "",
    val instructions: String = "",
    val tools: List<String> = emptyList(),
    val examples: List<String> = emptyList(),
    val rationale: String = ""
)

@Serializable
private data class ReviewMemoryDocument(
    val text: String = ""
)

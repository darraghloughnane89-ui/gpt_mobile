package dev.chungjungsoo.gptmobile.data.skill

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object SkillNames {
    const val MAX_NAME = 64
    const val MAX_DESCRIPTION = 1024
    const val MAX_INSTRUCTIONS = 8000
    const val ROUTING_DESCRIPTION = 60

    private val slugPattern = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

    fun slug(raw: String): String? {
        val normalized = raw.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(MAX_NAME)
            .trim('-')
        if (normalized.isEmpty() || !slugPattern.matches(normalized)) return null
        return normalized
    }

    fun routingDescription(description: String): String {
        val flat = description.trim().replace(Regex("\\s+"), " ")
        if (flat.length <= ROUTING_DESCRIPTION) return flat
        return flat.take(ROUTING_DESCRIPTION - 3) + "..."
    }
}

object SkillCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(String.serializer())

    fun encodeList(values: List<String>): String {
        val cleaned = values.map { it.trim() }.filter { it.isNotEmpty() }
        return json.encodeToString(listSerializer, cleaned)
    }

    fun decodeList(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            json.decodeFromString(listSerializer, trimmed).map { it.trim() }.filter { it.isNotEmpty() }
        } catch (error: Exception) {
            trimmed.split('\n', ',').map { it.trim().trim('[', ']', '"') }.filter { it.isNotEmpty() }
        }
    }

    fun tools(skill: Skill): List<String> = decodeList(skill.toolRequirements)

    fun examples(skill: Skill): List<String> = decodeList(skill.exampleInputs)

    fun tools(proposal: SkillProposal): List<String> = decodeList(proposal.proposedTools)

    fun examples(proposal: SkillProposal): List<String> = decodeList(proposal.proposedExamples)

    fun memories(proposal: SkillProposal): List<String> = decodeList(proposal.memoryFactsJson)
}

fun Skill.toDraft(): SkillDraft = SkillDraft(
    name = stableName,
    title = title,
    description = description,
    instructions = instructions,
    tools = SkillCodec.tools(this),
    examples = SkillCodec.examples(this),
    pinned = pinned,
    origin = origin
)

fun SkillProposal.toDraft(): SkillDraft = SkillDraft(
    name = proposedName,
    title = proposedName,
    description = proposedDescription,
    instructions = proposedInstructions,
    tools = SkillCodec.tools(this),
    examples = SkillCodec.examples(this),
    pinned = false,
    origin = SkillOrigin.LEARNED
)

package dev.chungjungsoo.gptmobile.data.skill

import dev.chungjungsoo.gptmobile.data.brand.AppBrand

/**
 * Critic prompt for the learning loop. Descriptions must stay within 60 characters
 * because the skill index truncates there, the same routing limit Hermes uses.
 */
object SkillReviewPrompt {
    const val SYSTEM = "You review a conversation for reusable procedures and short durable facts. Reply with one JSON object and no other text."

    fun user(transcript: String, catalog: List<Skill>): String {
        val index = if (catalog.isEmpty()) {
            "(no skills yet)"
        } else {
            catalog.joinToString("\n") { skill ->
                "- ${skill.stableName}: ${SkillNames.routingDescription(skill.description)}"
            }
        }
        return """
            Brand: ${AppBrand.NAME}
            Decide whether this conversation produced a reusable procedure or a durable fact.

            A skill is a named procedure: when to use it, and the steps that worked.
            A memory is a short fact about the user. Do not put procedures in memory.
            Do not copy long passages. Do not invent credentials, tool grants, or commands that were not used.
            Prefer updating a catalog skill when one already covers the task.
            Keep description at or under 60 characters. Anything longer is cut from the routing index.
            Skip one-off chatter. Return empty arrays when nothing should be saved.

            Existing skills:
            $index

            Conversation:
            $transcript

            JSON shape:
            {"skills":[{"action":"create","name":"kebab-case-name","description":"when to use, <=60 chars","instructions":"steps","tools":[],"examples":[],"rationale":"why"}],"memories":[{"text":"durable fact"}]}
            action is create, update, or retire.
        """.trimIndent()
    }
}

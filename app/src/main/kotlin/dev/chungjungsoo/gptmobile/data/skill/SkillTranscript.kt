package dev.chungjungsoo.gptmobile.data.skill

import dev.chungjungsoo.gptmobile.data.context.groupPersistedConversation
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent

object SkillTranscript {
    const val MAX_CHARS = 12_000

    fun fromPersisted(messages: List<MessageV2>): List<LearningTurn> {
        val (users, assistants) = groupPersistedConversation(messages)
        return fromMessages(users, assistants)
    }

    fun fromMessages(users: List<MessageV2>, assistants: List<List<MessageV2>>): List<LearningTurn> {
        val turns = mutableListOf<LearningTurn>()
        users.forEachIndexed { index, user ->
            val userText = user.effectiveContent().trim()
            if (userText.isNotEmpty()) turns += LearningTurn("user", userText)
            val assistantText = assistants.getOrNull(index).orEmpty()
                .map { it.effectiveContent().trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n\n")
            if (assistantText.isNotEmpty()) turns += LearningTurn("assistant", assistantText)
        }
        return turns
    }

    fun bounded(turns: List<LearningTurn>): String {
        val lines = turns.map { turn -> "${turn.role}: ${turn.text.trim()}" }
        val full = lines.joinToString("\n\n")
        if (full.length <= MAX_CHARS) return full
        return full.takeLast(MAX_CHARS)
    }
}

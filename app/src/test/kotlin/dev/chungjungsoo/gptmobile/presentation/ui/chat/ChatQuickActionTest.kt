package dev.chungjungsoo.gptmobile.presentation.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatQuickActionTest {
    @Test
    fun quickActionPrompt_eachActionAsksAboutThePreviousAnswer() {
        val prompts = ChatQuickAction.entries.map(::quickActionPrompt)

        assertEquals(ChatQuickAction.entries.size, prompts.distinct().size)
        assertTrue(prompts.all { it.contains("previous answer") })
        assertTrue(quickActionPrompt(ChatQuickAction.Irish).contains("Gaeilge"))
        assertTrue(quickActionPrompt(ChatQuickAction.Shorter).contains("shorter"))
    }
}

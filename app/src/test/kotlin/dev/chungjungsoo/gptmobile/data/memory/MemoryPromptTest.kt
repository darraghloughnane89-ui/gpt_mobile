package dev.chungjungsoo.gptmobile.data.memory

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPromptTest {
    @Test
    fun extract_rememberThat_returnsFact() {
        assertEquals("I live in Dublin", ExplicitMemoryParser.extract("Remember that I live in Dublin"))
    }

    @Test
    fun extract_rememberColon_returnsFact() {
        assertEquals("the dog is named Nori", ExplicitMemoryParser.extract("please remember: the dog is named Nori"))
    }

    @Test
    fun extract_ordinaryQuestion_returnsNull() {
        assertNull(ExplicitMemoryParser.extract("What is the capital of France?"))
    }

    @Test
    fun compose_disabledFactsStillStayDataNotInstructions() {
        val block = MemoryPromptComposer.compose(listOf("I prefer tea"), "tea")
        assertTrue(block.contains("not instructions"))
        assertTrue(block.contains("- I prefer tea"))
        assertTrue(block.contains(MemoryPromptComposer.MARKER))
    }

    @Test
    fun selectFacts_query_prefersOverlapAndRespectsCap() {
        val facts = listOf(
            "I commute by train",
            "x".repeat(50),
            "The project code name is harbor"
        )
        val selected = MemoryPromptComposer.selectFacts(facts, "Where is harbor?", 80)
        assertEquals("The project code name is harbor", selected.first())
        assertTrue(selected.sumOf { it.length + 2 } <= 80)
    }

    @Test
    fun selectFacts_overCap_dropsFactsThatDoNotFit() {
        val facts = List(20) { index -> "fact number $index " + "y".repeat(200) }
        val selected = MemoryPromptComposer.selectFacts(facts, "", MemoryPromptComposer.MAX_FACT_CHARS)
        assertTrue(selected.isNotEmpty())
        assertTrue(selected.sumOf { it.length + 2 } <= MemoryPromptComposer.MAX_FACT_CHARS)
        assertTrue(selected.size < facts.size)
    }

    @Test
    fun includingMemory_blankBlock_leavesPromptUntouched() {
        val platform = samplePlatform("Be brief")
        assertEquals(platform, platform.includingMemory(null))
        assertEquals(platform, platform.includingMemory("  "))
    }

    @Test
    fun includingMemory_appendsBlockAfterExistingPrompt() {
        val platform = samplePlatform("Be brief").includingMemory("memories")
        assertEquals("Be brief\n\nmemories", platform.systemPrompt)
    }

    @Test
    fun stripper_plainText_passesThrough() {
        val stripper = MemorySuggestionStripper()
        assertEquals("Answer", stripper.accept("Answer"))
        assertEquals(MemorySuggestionTail("", null), stripper.finish())
    }

    @Test
    fun stripper_markerSplitAcrossChunks_hidesSuggestion() {
        val stripper = MemorySuggestionStripper()
        val visible = stripper.accept("Hello\nMEMORY_SUGG") + stripper.accept("ESTION: I like jazz")
        val tail = stripper.finish()
        assertEquals("Hello", visible.trim())
        assertEquals("I like jazz", tail.suggestion)
        assertFalse((visible + tail.visible).contains(MemoryPromptComposer.MARKER))
    }

    private fun samplePlatform(prompt: String) = PlatformV2(
        name = "OpenAI",
        compatibleType = ClientType.OPENAI,
        apiUrl = "https://api.openai.com",
        model = "gpt-4o",
        systemPrompt = prompt
    )
}

package dev.chungjungsoo.gptmobile.presentation.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelAnswerComparisonTest {
    @Test
    fun compareModelAnswers_reportsOverlapAndSentencesUniqueToEachModel() {
        val comparison = compareModelAnswers(
            listOf(
                ModelAnswer("Claude", "The river is wide today. Ships pass at dawn without delay."),
                ModelAnswer("Gemini", "The river is wide today. Birds nest in the reeds.")
            )
        )

        assertTrue(comparison != null)
        assertTrue(comparison!!.overlapPercent in 1..99)
        assertEquals(listOf("Claude", "Gemini"), comparison.answers.map { it.label })
        assertTrue(comparison.answers[0].uniqueSentences.single().contains("Ships"))
        assertTrue(comparison.answers[1].uniqueSentences.single().contains("Birds"))
        assertTrue(comparison.answers[0].wordCount > 0)
    }

    @Test
    fun compareModelAnswers_needsTwoAnswersAndDisambiguatesLabels() {
        assertNull(compareModelAnswers(listOf(ModelAnswer("Claude", "Only one answer here."))))
        val labeled = labeledModelAnswers(
            listOf(
                "OpenAI" to "First answer is long enough.",
                "OpenAI" to "",
                "OpenAI" to "Second answer is different."
            )
        )

        assertEquals(listOf("OpenAI 1", "OpenAI 2"), labeled.map { it.label })
        assertNull(compareModelAnswers(emptyList()))
    }
}

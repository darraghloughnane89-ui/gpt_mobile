package dev.chungjungsoo.gptmobile.data.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSharePlannerTest {
    @Test
    fun plan_extractedText_becomesComposerSection() {
        val plan = PdfSharePlanner.plan("notes.pdf", "Quarterly results")

        assertEquals(
            PdfSharePlanner.PdfPlan.Text("PDF: notes.pdf\nQuarterly results"),
            plan
        )
    }

    @Test
    fun plan_blankExtract_asksForPageImages() {
        assertEquals(PdfSharePlanner.PdfPlan.NeedsPageImages, PdfSharePlanner.plan("scan.pdf", "  "))
    }

    @Test
    fun compose_sharedTextAndSections_keepsUserTextFirst() {
        val text = PdfSharePlanner.compose(
            sharedText = "What is this?",
            sections = listOf("PDF: notes.pdf\nHello")
        )

        assertEquals("What is this?\n\nPDF: notes.pdf\nHello", text)
    }

    @Test
    fun compose_longText_isCapped() {
        val text = PdfSharePlanner.compose("x".repeat(50), emptyList(), maxChars = 8)

        assertTrue(text.length <= 8)
    }
}

package dev.chungjungsoo.gptmobile.presentation.ui.skill

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chungjungsoo.gptmobile.data.skill.SkillDraft
import dev.chungjungsoo.gptmobile.data.skill.SkillProposal
import dev.chungjungsoo.gptmobile.data.skill.SkillProposalAction
import dev.chungjungsoo.gptmobile.data.skill.SkillProposalSource
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SkillReviewInboxInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun approve_editedProposal_returnsTheDraft() {
        val proposal = SkillProposal(
            id = 7,
            action = SkillProposalAction.CREATE,
            proposedName = "deploy-staging",
            proposedDescription = "Deploy staging",
            proposedInstructions = "Check /health.",
            rationale = "The chat found a repeatable deploy.",
            source = SkillProposalSource.SAVE_AS_SKILL,
            createdAt = 1
        )
        var approved: SkillDraft? = null

        composeRule.setContent {
            GPTMobileTheme {
                SkillReviewInboxScreen(
                    proposals = listOf(proposal),
                    autoAccept = false,
                    message = null,
                    onAutoAcceptChange = {},
                    onApprove = { _, draft -> approved = draft },
                    onReject = {},
                    onBack = {},
                    onConsumeMessage = {}
                )
            }
        }

        composeRule.onNodeWithText("Deploy staging", substring = true).assertExists()
        composeRule.onNodeWithText("Approve").performClick()

        assertEquals("deploy-staging", approved?.name)
        assertEquals("Check /health.", approved?.instructions)
    }
}

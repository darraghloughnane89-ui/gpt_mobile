package dev.chungjungsoo.gptmobile.data.onboarding

import dev.chungjungsoo.gptmobile.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiMoSuggestionTest {
    @Test
    fun `the suggested provider is the OpenAI compatible MiMo endpoint`() {
        val platform = MiMoSuggestion.platform(
            name = "  MiMo  ",
            apiUrl = "  ${MiMoSuggestion.API_URL}  ",
            model = "  ${MiMoSuggestion.MODEL}  ",
            apiKey = "  secret  "
        )

        assertEquals("MiMo", platform.name)
        assertEquals("https://token-plan-ams.xiaomimimo.com/v1", platform.apiUrl)
        assertEquals("mimo-v2.6-pro", platform.model)
        assertEquals("secret", platform.token)
        assertEquals(ClientType.CUSTOM, platform.compatibleType)
        assertTrue(platform.enabled)
        assertTrue(MiMoSuggestion.matches("MiMo", MiMoSuggestion.API_URL + "/", MiMoSuggestion.MODEL))
    }

    @Test
    fun `a blank or oversized key is not ready to save`() {
        assertFalse(MiMoSuggestion.isReady("MiMo", MiMoSuggestion.API_URL, MiMoSuggestion.MODEL, " "))
        assertFalse(
            MiMoSuggestion.isReady(
                "MiMo",
                MiMoSuggestion.API_URL,
                MiMoSuggestion.MODEL,
                "k".repeat(MiMoSuggestion.MAX_API_KEY_LENGTH + 1)
            )
        )
        assertFalse(MiMoSuggestion.isReady("MiMo", "token-plan.example", MiMoSuggestion.MODEL, "secret"))
        assertTrue(MiMoSuggestion.isReady("MiMo", MiMoSuggestion.API_URL, MiMoSuggestion.MODEL, "secret"))
    }
}

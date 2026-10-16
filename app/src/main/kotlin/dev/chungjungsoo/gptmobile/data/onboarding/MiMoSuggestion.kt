package dev.chungjungsoo.gptmobile.data.onboarding

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType

/**
 * Default provider offered on first run.
 * MiMo speaks the OpenAI chat-completions protocol, so it is stored as [ClientType.CUSTOM].
 */
object MiMoSuggestion {
    const val NAME = "MiMo"
    const val API_URL = "https://token-plan-ams.xiaomimimo.com/v1"
    const val MODEL = "mimo-v2.6-pro"
    const val MAX_API_KEY_LENGTH = 4096

    fun matches(name: String, apiUrl: String, model: String): Boolean = name.trim() == NAME &&
        normalizeUrl(apiUrl) == normalizeUrl(API_URL) &&
        model.trim() == MODEL

    fun isReady(name: String, apiUrl: String, model: String, apiKey: String): Boolean {
        val key = apiKey.trim()
        if (name.isBlank() || model.isBlank() || key.isEmpty() || key.length > MAX_API_KEY_LENGTH) {
            return false
        }
        val url = apiUrl.trim()
        return url.startsWith("https://") || url.startsWith("http://")
    }

    fun platform(name: String, apiUrl: String, model: String, apiKey: String): PlatformV2 = PlatformV2(
        name = name.trim(),
        compatibleType = ClientType.CUSTOM,
        enabled = true,
        apiUrl = apiUrl.trim(),
        token = apiKey.trim(),
        model = model.trim(),
        temperature = 1.0f,
        topP = 1.0f,
        systemPrompt = ModelConstants.DEFAULT_PROMPT,
        stream = true,
        reasoning = false,
        timeout = 30
    )

    private fun normalizeUrl(url: String): String = url.trim().trimEnd('/')
}

package dev.chungjungsoo.gptmobile.data.voice

import dev.chungjungsoo.gptmobile.data.repository.SettingRepository

class VoiceCredentialLookupImpl(
    private val settingRepository: SettingRepository
) : VoiceCredentialLookup {
    override suspend fun openAiCredential(): VoiceProviderCredential? {
        val selected = selectOpenAiSpeechPlatform(
            settingRepository.fetchPlatformV2s().map { platform ->
                OpenAiSpeechCandidate(
                    enabled = platform.enabled,
                    apiUrl = platform.apiUrl,
                    token = platform.token,
                    clientType = platform.compatibleType
                )
            }
        ) ?: return null
        val token = selected.token?.trim().orEmpty()
        if (token.isEmpty()) return null
        val baseUrl = selected.apiUrl.trim()
        if (baseUrl.isEmpty()) return null
        return VoiceProviderCredential(apiKey = token, baseUrl = baseUrl)
    }
}

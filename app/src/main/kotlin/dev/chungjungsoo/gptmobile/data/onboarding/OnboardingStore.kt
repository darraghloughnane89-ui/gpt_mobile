package dev.chungjungsoo.gptmobile.data.onboarding

interface OnboardingStore {
    suspend fun isCompleted(): Boolean

    suspend fun setCompleted()

    suspend fun isVoiceEnabled(): Boolean

    suspend fun setVoiceEnabled(enabled: Boolean)

    suspend fun dismissedTips(): Set<String>

    suspend fun dismissTip(id: String)
}

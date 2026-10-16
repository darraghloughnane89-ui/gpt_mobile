package dev.chungjungsoo.gptmobile.data.onboarding

class MemoryOnboardingStore : OnboardingStore {
    var completed: Boolean = false
    var voice: Boolean = false
    val tips = mutableSetOf<String>()

    override suspend fun isCompleted(): Boolean = completed

    override suspend fun setCompleted() {
        completed = true
    }

    override suspend fun isVoiceEnabled(): Boolean = voice

    override suspend fun setVoiceEnabled(enabled: Boolean) {
        voice = enabled
    }

    override suspend fun dismissedTips(): Set<String> = tips.toSet()

    override suspend fun dismissTip(id: String) {
        tips += id
    }
}

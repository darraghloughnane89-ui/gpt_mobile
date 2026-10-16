package dev.chungjungsoo.gptmobile.data.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.onboardingPreferences by preferencesDataStore(name = "onboarding")

@Singleton
class DataStoreOnboardingStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) : OnboardingStore {
    override suspend fun isCompleted(): Boolean = context.onboardingPreferences.data.first()[COMPLETED] == true

    override suspend fun setCompleted() {
        context.onboardingPreferences.edit { preferences ->
            preferences[COMPLETED] = true
        }
    }

    override suspend fun isVoiceEnabled(): Boolean = context.onboardingPreferences.data.first()[VOICE_ENABLED] == true

    override suspend fun setVoiceEnabled(enabled: Boolean) {
        context.onboardingPreferences.edit { preferences ->
            preferences[VOICE_ENABLED] = enabled
        }
    }

    override suspend fun dismissedTips(): Set<String> = context.onboardingPreferences.data.first()[DISMISSED_TIPS].orEmpty()

    override suspend fun dismissTip(id: String) {
        context.onboardingPreferences.edit { preferences ->
            preferences[DISMISSED_TIPS] = preferences[DISMISSED_TIPS].orEmpty() + id
        }
    }

    private companion object {
        val COMPLETED = booleanPreferencesKey("onboarding_completed")
        val VOICE_ENABLED = booleanPreferencesKey("onboarding_voice_enabled")
        val DISMISSED_TIPS = stringSetPreferencesKey("onboarding_dismissed_tips")
    }
}

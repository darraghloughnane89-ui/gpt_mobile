package dev.chungjungsoo.gptmobile.data.datastore

import androidx.datastore.preferences.core.stringPreferencesKey
import dev.chungjungsoo.gptmobile.data.model.ApiType

/**
 * Preference names that used to hold API tokens in plaintext DataStore.
 * Tokens are migrated into the Keystore vault and these keys are then removed.
 */
object LegacyTokenKeys {
    val preferenceNames: Map<ApiType, String> = mapOf(
        ApiType.OPENAI to "openai_token",
        ApiType.ANTHROPIC to "anthropic_token",
        ApiType.GOOGLE to "google_token",
        ApiType.GROQ to "groq_token",
        ApiType.OLLAMA to "ollama_token"
    )

    fun preferenceKey(apiType: ApiType) = stringPreferencesKey(
        preferenceNames.getValue(apiType)
    )
}

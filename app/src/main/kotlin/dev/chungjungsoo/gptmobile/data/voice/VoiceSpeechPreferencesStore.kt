package dev.chungjungsoo.gptmobile.data.voice

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.voiceSpeechDataStore by preferencesDataStore(name = "voice_speech")

@Singleton
class VoiceSpeechPreferencesStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) : VoiceSpeechPreferences {
    override val settings: Flow<VoiceSpeechSettings> = context.voiceSpeechDataStore.data.map { preferences ->
        VoiceSpeechSettings(
            useMiMo = preferences[USE_MIMO] == true,
            voiceId = MiMoSpeechModels.knownVoice(preferences[VOICE_ID])
        )
    }

    override suspend fun setUseMiMo(enabled: Boolean) {
        context.voiceSpeechDataStore.edit { preferences ->
            preferences[USE_MIMO] = enabled
        }
    }

    override suspend fun setVoiceId(voiceId: String) {
        context.voiceSpeechDataStore.edit { preferences ->
            preferences[VOICE_ID] = MiMoSpeechModels.knownVoice(voiceId)
        }
    }

    private companion object {
        val USE_MIMO = booleanPreferencesKey("voice_use_mimo")
        val VOICE_ID = stringPreferencesKey("voice_mimo_voice")
    }
}

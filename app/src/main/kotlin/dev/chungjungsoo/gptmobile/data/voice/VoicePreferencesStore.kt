package dev.chungjungsoo.gptmobile.data.voice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.voicePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "voice_preferences")

class VoicePreferencesStore(
    private val context: Context
) : VoicePreferences {
    override val settings: Flow<VoiceSettings> = context.voicePreferencesDataStore.data.map { preferences ->
        VoiceSettings(
            speechRecognition = preferences[SPEECH_RECOGNITION].toVoiceEngine(),
            speechSynthesis = preferences[SPEECH_SYNTHESIS].toVoiceEngine()
        )
    }

    override suspend fun setSpeechRecognition(engine: VoiceEngine) {
        context.voicePreferencesDataStore.edit { preferences ->
            preferences[SPEECH_RECOGNITION] = engine.name
        }
    }

    override suspend fun setSpeechSynthesis(engine: VoiceEngine) {
        context.voicePreferencesDataStore.edit { preferences ->
            preferences[SPEECH_SYNTHESIS] = engine.name
        }
    }

    private companion object {
        val SPEECH_RECOGNITION = stringPreferencesKey("voice_stt_engine")
        val SPEECH_SYNTHESIS = stringPreferencesKey("voice_tts_engine")
    }
}

private fun String?.toVoiceEngine(): VoiceEngine = when (this) {
    VoiceEngine.OPENAI.name -> VoiceEngine.OPENAI
    else -> VoiceEngine.DEVICE
}

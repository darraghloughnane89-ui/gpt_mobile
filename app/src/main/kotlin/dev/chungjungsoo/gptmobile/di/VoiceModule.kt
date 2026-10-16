package dev.chungjungsoo.gptmobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.voice.AndroidAudioFocusGateway
import dev.chungjungsoo.gptmobile.data.voice.AndroidHeadsetRouter
import dev.chungjungsoo.gptmobile.data.voice.AndroidSpeechRecognizerEngine
import dev.chungjungsoo.gptmobile.data.voice.AndroidTextToSpeechEngine
import dev.chungjungsoo.gptmobile.data.voice.AndroidVoiceActivityDetector
import dev.chungjungsoo.gptmobile.data.voice.AudioFocusGateway
import dev.chungjungsoo.gptmobile.data.voice.HeadsetRouter
import dev.chungjungsoo.gptmobile.data.voice.OpenAiAudioClient
import dev.chungjungsoo.gptmobile.data.voice.OpenAiAudioClientImpl
import dev.chungjungsoo.gptmobile.data.voice.OpenAiSpeechToText
import dev.chungjungsoo.gptmobile.data.voice.OpenAiTextToSpeechEngine
import dev.chungjungsoo.gptmobile.data.voice.SpeechEngineRouter
import dev.chungjungsoo.gptmobile.data.voice.SpeechToText
import dev.chungjungsoo.gptmobile.data.voice.TextToSpeechEngine
import dev.chungjungsoo.gptmobile.data.voice.TtsEngineRouter
import dev.chungjungsoo.gptmobile.data.voice.VoiceActivityDetector
import dev.chungjungsoo.gptmobile.data.voice.VoiceCredentialLookup
import dev.chungjungsoo.gptmobile.data.voice.VoiceCredentialLookupImpl
import dev.chungjungsoo.gptmobile.data.voice.VoicePreferences
import dev.chungjungsoo.gptmobile.data.voice.VoicePreferencesStore
import dev.chungjungsoo.gptmobile.presentation.voice.AndroidAssistantRoleGateway
import dev.chungjungsoo.gptmobile.presentation.voice.AssistantRoleGateway
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {
    @Provides
    @Singleton
    fun provideVoicePreferences(@ApplicationContext context: Context): VoicePreferences = VoicePreferencesStore(context)

    @Provides
    @Singleton
    fun provideVoiceCredentials(settingRepository: SettingRepository): VoiceCredentialLookup = VoiceCredentialLookupImpl(settingRepository)

    @Provides
    @Singleton
    fun provideOpenAiAudioClient(networkClient: NetworkClient): OpenAiAudioClient = OpenAiAudioClientImpl(networkClient)

    @Provides
    @Singleton
    fun provideSpeechToText(
        @ApplicationContext context: Context,
        preferences: VoicePreferences,
        credentials: VoiceCredentialLookup,
        client: OpenAiAudioClient
    ): SpeechToText = SpeechEngineRouter(
        device = AndroidSpeechRecognizerEngine(context),
        openAi = OpenAiSpeechToText(context, credentials, client),
        preferences = preferences,
        credentials = credentials
    )

    @Provides
    @Singleton
    fun provideTextToSpeech(
        @ApplicationContext context: Context,
        preferences: VoicePreferences,
        credentials: VoiceCredentialLookup,
        client: OpenAiAudioClient
    ): TextToSpeechEngine = TtsEngineRouter(
        device = AndroidTextToSpeechEngine(context),
        openAi = OpenAiTextToSpeechEngine(context, credentials, client),
        preferences = preferences,
        credentials = credentials
    )

    @Provides
    @Singleton
    fun provideVoiceActivityDetector(): VoiceActivityDetector = AndroidVoiceActivityDetector()

    @Provides
    @Singleton
    fun provideAudioFocus(@ApplicationContext context: Context): AudioFocusGateway = AndroidAudioFocusGateway(context)

    @Provides
    @Singleton
    fun provideHeadsetRouter(@ApplicationContext context: Context): HeadsetRouter = AndroidHeadsetRouter(context)

    @Provides
    @Singleton
    fun provideAssistantRole(@ApplicationContext context: Context): AssistantRoleGateway = AndroidAssistantRoleGateway(context)
}

package dev.chungjungsoo.gptmobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import dev.chungjungsoo.gptmobile.data.voice.AndroidBargeIn
import dev.chungjungsoo.gptmobile.data.voice.AndroidMicrophoneAccess
import dev.chungjungsoo.gptmobile.data.voice.AndroidSpeechRecognizerEngine
import dev.chungjungsoo.gptmobile.data.voice.AndroidTextToSpeechEngine
import dev.chungjungsoo.gptmobile.data.voice.AudioTrackPcmSink
import dev.chungjungsoo.gptmobile.data.voice.MiMoSpeechClient
import dev.chungjungsoo.gptmobile.data.voice.MiMoSpeechRecognizer
import dev.chungjungsoo.gptmobile.data.voice.MiMoSpeechSpeaker
import dev.chungjungsoo.gptmobile.data.voice.MiMoSpeechTransport
import dev.chungjungsoo.gptmobile.data.voice.MicrophoneAccess
import dev.chungjungsoo.gptmobile.data.voice.RepositoryVoiceSpeechKeyStore
import dev.chungjungsoo.gptmobile.data.voice.VoiceRecognizer
import dev.chungjungsoo.gptmobile.data.voice.VoiceSpeaker
import dev.chungjungsoo.gptmobile.data.voice.VoiceSpeechKeyStore
import dev.chungjungsoo.gptmobile.data.voice.VoiceSpeechPreferences
import dev.chungjungsoo.gptmobile.data.voice.VoiceSpeechPreferencesStore
import dev.chungjungsoo.gptmobile.data.voice.VoiceSpeechRouter
import dev.chungjungsoo.gptmobile.data.voice.VoiceSpeechRouterHost
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceSpeechModule {
    @Provides
    @Singleton
    fun provideVoiceSpeechPreferences(store: VoiceSpeechPreferencesStore): VoiceSpeechPreferences = store

    @Provides
    @Singleton
    fun provideMicrophoneAccess(access: AndroidMicrophoneAccess): MicrophoneAccess = access

    @Provides
    @Singleton
    fun provideVoiceSpeechKeys(
        secretVault: SecretVault,
        settingRepository: SettingRepository
    ): VoiceSpeechKeyStore = RepositoryVoiceSpeechKeyStore(secretVault, settingRepository)

    @Provides
    @Singleton
    fun provideMiMoSpeechTransport(networkClient: NetworkClient): MiMoSpeechTransport = MiMoSpeechClient(networkClient())

    @Provides
    @Singleton
    fun provideVoiceSpeechRouter(
        @ApplicationContext context: Context,
        transport: MiMoSpeechTransport,
        keys: VoiceSpeechKeyStore,
        preferences: VoiceSpeechPreferences,
        microphone: MicrophoneAccess
    ): VoiceSpeechRouter {
        val bargeIn = AndroidBargeIn(microphone)
        return VoiceSpeechRouter(
            deviceRecognizer = AndroidSpeechRecognizerEngine(context),
            mimoRecognizer = MiMoSpeechRecognizer(transport, keys, microphone),
            deviceSpeaker = AndroidTextToSpeechEngine(context),
            mimoSpeaker = MiMoSpeechSpeaker(
                transport = transport,
                keys = keys,
                preferences = preferences,
                sink = AudioTrackPcmSink(),
                bargeIn = bargeIn
            ),
            preferences = preferences,
            keys = keys
        )
    }

    @Provides
    fun provideVoiceSpeechRouterHost(router: VoiceSpeechRouter): VoiceSpeechRouterHost = router

    @Provides
    fun provideVoiceRecognizer(router: VoiceSpeechRouter): VoiceRecognizer = router.recognizer

    @Provides
    fun provideVoiceSpeaker(router: VoiceSpeechRouter): VoiceSpeaker = router.speaker
}

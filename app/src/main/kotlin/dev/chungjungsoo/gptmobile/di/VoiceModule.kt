package dev.chungjungsoo.gptmobile.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.voice.DeviceVoiceSpeechRouter
import dev.chungjungsoo.gptmobile.data.voice.OpenWakeWordEngine
import dev.chungjungsoo.gptmobile.data.voice.VoiceSpeechRouterHost
import dev.chungjungsoo.gptmobile.data.voice.WakePhraseEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {
    @Binds
    @Singleton
    abstract fun bindWakeEngine(engine: OpenWakeWordEngine): WakePhraseEngine

    @Binds
    @Singleton
    abstract fun bindSpeechRouter(router: DeviceVoiceSpeechRouter): VoiceSpeechRouterHost
}

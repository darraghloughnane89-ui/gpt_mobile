package dev.chungjungsoo.gptmobile.data.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

class SpeechEngineRouter(
    private val device: SpeechToText,
    private val openAi: SpeechToText,
    private val preferences: VoicePreferences,
    private val credentials: VoiceCredentialLookup
) : SpeechToText {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val eventsFlow = MutableSharedFlow<SttEvent>(extraBufferCapacity = 32)
    private val levelsFlow = MutableSharedFlow<Float>(extraBufferCapacity = 8)
    override val events = eventsFlow
    override val levels = levelsFlow
    private var delegate: SpeechToText = device
    private var forwarding: Job? = null

    override suspend fun start() {
        val settings = preferences.settings.first()
        val selected = if (settings.speechRecognition == VoiceEngine.OPENAI && credentials.openAiCredential() != null) {
            openAi
        } else {
            device
        }
        forwarding?.cancel()
        if (delegate !== selected) {
            delegate.stop()
        }
        delegate = selected
        forwarding = scope.launch {
            coroutineScope {
                launch { selected.events.collect { eventsFlow.emit(it) } }
                launch { selected.levels.collect { levelsFlow.emit(it) } }
                yield()
                selected.start()
                awaitCancellation()
            }
        }
    }

    override suspend fun stop() {
        forwarding?.cancel()
        forwarding = null
        delegate.stop()
    }
}

class TtsEngineRouter(
    private val device: TextToSpeechEngine,
    private val openAi: TextToSpeechEngine,
    private val preferences: VoicePreferences,
    private val credentials: VoiceCredentialLookup
) : TextToSpeechEngine {
    private var delegate: TextToSpeechEngine = device

    override suspend fun speak(utteranceId: String, text: String) {
        val settings = preferences.settings.first()
        delegate = if (settings.speechSynthesis == VoiceEngine.OPENAI && credentials.openAiCredential() != null) {
            openAi
        } else {
            device
        }
        delegate.speak(utteranceId, text)
    }

    override suspend fun stop() {
        device.stop()
        openAi.stop()
    }
}

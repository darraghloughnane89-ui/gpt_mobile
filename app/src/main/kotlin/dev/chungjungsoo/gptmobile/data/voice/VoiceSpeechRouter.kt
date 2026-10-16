package dev.chungjungsoo.gptmobile.data.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

class VoiceSpeechRouter(
    private val deviceRecognizer: VoiceRecognizer,
    private val mimoRecognizer: VoiceRecognizer,
    private val deviceSpeaker: VoiceSpeaker,
    private val mimoSpeaker: VoiceSpeaker,
    private val preferences: VoiceSpeechPreferences,
    private val keys: VoiceSpeechKeyStore,
    parentScope: CoroutineScope? = null
) : VoiceSpeechRouterHost {
    private val scope = parentScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val eventsFlow = MutableSharedFlow<VoiceTranscript>(extraBufferCapacity = 32)
    private val levelsFlow = MutableSharedFlow<Float>(extraBufferCapacity = 8)
    private val backend = MutableStateFlow(VoiceSpeechBackend.DEVICE)
    override val activeBackend: StateFlow<VoiceSpeechBackend> = backend
    override val recognizer: VoiceRecognizer = RouterRecognizer()
    override val speaker: VoiceSpeaker = RouterSpeaker()
    private var delegate: VoiceRecognizer = deviceRecognizer
    private var forwarding: Job? = null

    override suspend fun refresh() {
        backend.value = resolve()
    }

    private suspend fun resolve(): VoiceSpeechBackend = selectVoiceBackend(
        useMiMo = preferences.settings.first().useMiMo,
        hasKey = keys.hasKey()
    ).also { backend.value = it }

    private inner class RouterRecognizer : VoiceRecognizer {
        override val events = eventsFlow
        override val levels: Flow<Float> = levelsFlow

        override suspend fun start() {
            val preferred = if (resolve() == VoiceSpeechBackend.MIMO) mimoRecognizer else deviceRecognizer
            startDelegate(preferred)
        }

        private fun startDelegate(selected: VoiceRecognizer) {
            forwarding?.cancel()
            delegate = selected
            forwarding = scope.launch {
                try {
                    runEngine(selected)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    if (selected === deviceRecognizer) {
                        eventsFlow.emit(
                            VoiceTranscript.Failure(
                                message = "Speech recognition is unavailable on this phone.",
                                recoverable = true
                            )
                        )
                    } else {
                        runCatching { selected.stop() }
                        delegate = deviceRecognizer
                        backend.value = VoiceSpeechBackend.DEVICE
                        runEngine(deviceRecognizer)
                    }
                }
            }
        }

        private suspend fun runEngine(engine: VoiceRecognizer) {
            coroutineScope {
                launch { engine.events.collect { eventsFlow.emit(it) } }
                launch { engine.levels.collect { levelsFlow.emit(it) } }
                yield()
                engine.start()
                awaitCancellation()
            }
        }

        override suspend fun stop() {
            forwarding?.cancel()
            forwarding = null
            mimoRecognizer.stop()
            deviceRecognizer.stop()
        }
    }

    private inner class RouterSpeaker : VoiceSpeaker {
        override suspend fun speak(utteranceId: String, text: String) {
            if (text.isBlank()) return
            recognizer.stop()
            if (resolve() == VoiceSpeechBackend.MIMO) {
                try {
                    mimoSpeaker.speak(utteranceId, text)
                    return
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    backend.value = VoiceSpeechBackend.DEVICE
                }
            }
            deviceSpeaker.speak(utteranceId, text)
        }

        override suspend fun stop() {
            mimoSpeaker.stop()
            deviceSpeaker.stop()
        }
    }
}

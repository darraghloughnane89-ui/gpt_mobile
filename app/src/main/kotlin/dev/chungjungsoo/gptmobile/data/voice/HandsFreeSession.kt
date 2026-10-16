package dev.chungjungsoo.gptmobile.data.voice

enum class HandsFreePhase {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    PAUSED,
    ENDED
}

data class HandsFreeState(
    val phase: HandsFreePhase = HandsFreePhase.IDLE,
    val muted: Boolean = false,
    val partialTranscript: String = "",
    val lastUserUtterance: String = "",
    val replyText: String = "",
    val recentlySpoken: String = "",
    val statusMessage: String? = null
)

sealed interface HandsFreeEvent {
    data object Start : HandsFreeEvent
    data class PartialTranscript(val text: String) : HandsFreeEvent
    data class FinalUtterance(val text: String) : HandsFreeEvent
    data class ReplyProgress(
        val fullText: String,
        val sentences: List<String>,
        val replyComplete: Boolean
    ) : HandsFreeEvent
    data object PlaybackDrained : HandsFreeEvent
    data object Interrupt : HandsFreeEvent
    data object Mute : HandsFreeEvent
    data object Unmute : HandsFreeEvent
    data object End : HandsFreeEvent
    data object BatteryStop : HandsFreeEvent
    data object CaptureBlocked : HandsFreeEvent
    data class SubmitRejected(val message: String) : HandsFreeEvent
    data class Failure(val message: String, val recoverable: Boolean) : HandsFreeEvent
}

sealed interface HandsFreeEffect {
    data object StartListening : HandsFreeEffect
    data object StopListening : HandsFreeEffect
    data class Speak(val text: String) : HandsFreeEffect
    data object StopSpeaking : HandsFreeEffect
    data class SubmitTurn(val text: String) : HandsFreeEffect
    data object CancelReply : HandsFreeEffect
    data object ReleaseCapture : HandsFreeEffect
}

data class HandsFreeTransition(
    val state: HandsFreeState,
    val effects: List<HandsFreeEffect> = emptyList()
)

class HandsFreeReducer {
    fun reduce(state: HandsFreeState, event: HandsFreeEvent): HandsFreeTransition = when (event) {
        HandsFreeEvent.Start -> start(state)
        is HandsFreeEvent.PartialTranscript -> partial(state, event.text)
        is HandsFreeEvent.FinalUtterance -> finalUtterance(state, event.text)
        is HandsFreeEvent.ReplyProgress -> reply(state, event)
        HandsFreeEvent.PlaybackDrained -> playbackDrained(state)
        HandsFreeEvent.Interrupt -> interrupt(state)
        HandsFreeEvent.Mute -> mute(state)
        HandsFreeEvent.Unmute -> unmute(state)
        HandsFreeEvent.End -> end(state, status = null)
        HandsFreeEvent.BatteryStop -> end(state, status = BATTERY_STATUS)
        HandsFreeEvent.CaptureBlocked -> captureBlocked(state)
        is HandsFreeEvent.SubmitRejected -> rejected(state, event.message)
        is HandsFreeEvent.Failure -> failure(state, event)
    }

    private fun start(state: HandsFreeState): HandsFreeTransition {
        if (state.phase != HandsFreePhase.IDLE &&
            state.phase != HandsFreePhase.ENDED &&
            state.phase != HandsFreePhase.PAUSED
        ) {
            return HandsFreeTransition(state)
        }
        return HandsFreeTransition(
            state.copy(
                phase = HandsFreePhase.LISTENING,
                muted = false,
                partialTranscript = "",
                replyText = "",
                recentlySpoken = "",
                statusMessage = null
            ),
            listOf(HandsFreeEffect.StartListening)
        )
    }

    private fun partial(state: HandsFreeState, text: String): HandsFreeTransition {
        if (state.phase != HandsFreePhase.LISTENING || state.muted) return HandsFreeTransition(state)
        return HandsFreeTransition(state.copy(partialTranscript = text, statusMessage = null))
    }

    private fun finalUtterance(state: HandsFreeState, text: String): HandsFreeTransition {
        val trimmed = text.trim()
        if (state.muted || state.phase != HandsFreePhase.LISTENING) return HandsFreeTransition(state)
        if (trimmed.isEmpty() || isSpokenEcho(trimmed, state.recentlySpoken)) {
            return HandsFreeTransition(state.copy(partialTranscript = ""))
        }
        return HandsFreeTransition(
            state.copy(
                phase = HandsFreePhase.THINKING,
                partialTranscript = "",
                lastUserUtterance = trimmed,
                replyText = "",
                recentlySpoken = "",
                statusMessage = null
            ),
            listOf(HandsFreeEffect.StopListening, HandsFreeEffect.SubmitTurn(trimmed))
        )
    }

    private fun reply(state: HandsFreeState, event: HandsFreeEvent.ReplyProgress): HandsFreeTransition {
        if (state.phase != HandsFreePhase.THINKING && state.phase != HandsFreePhase.SPEAKING) {
            return HandsFreeTransition(state)
        }
        val spoken = (state.recentlySpoken + " " + event.sentences.joinToString(" ")).trim()
        if (event.replyComplete && event.sentences.isEmpty() && state.phase == HandsFreePhase.THINKING) {
            return HandsFreeTransition(
                state.copy(
                    phase = HandsFreePhase.LISTENING,
                    replyText = event.fullText,
                    recentlySpoken = spoken,
                    partialTranscript = ""
                ),
                listOf(HandsFreeEffect.StartListening)
            )
        }
        if (event.sentences.isEmpty()) {
            return HandsFreeTransition(state.copy(replyText = event.fullText, recentlySpoken = spoken))
        }
        return HandsFreeTransition(
            state.copy(
                phase = HandsFreePhase.SPEAKING,
                replyText = event.fullText,
                recentlySpoken = spoken
            ),
            event.sentences.map { HandsFreeEffect.Speak(it) }
        )
    }

    private fun playbackDrained(state: HandsFreeState): HandsFreeTransition {
        if (state.phase != HandsFreePhase.SPEAKING) return HandsFreeTransition(state)
        return HandsFreeTransition(
            state.copy(phase = HandsFreePhase.LISTENING, partialTranscript = ""),
            listOf(HandsFreeEffect.StartListening)
        )
    }

    private fun interrupt(state: HandsFreeState): HandsFreeTransition {
        if (state.phase == HandsFreePhase.SPEAKING || state.phase == HandsFreePhase.THINKING) {
            return HandsFreeTransition(
                state.copy(phase = HandsFreePhase.LISTENING, partialTranscript = "", statusMessage = null),
                listOf(
                    HandsFreeEffect.StopSpeaking,
                    HandsFreeEffect.CancelReply,
                    HandsFreeEffect.StartListening
                )
            )
        }
        if (state.phase == HandsFreePhase.LISTENING && !state.muted) {
            return HandsFreeTransition(
                state.copy(partialTranscript = ""),
                listOf(HandsFreeEffect.StopListening, HandsFreeEffect.StartListening)
            )
        }
        return HandsFreeTransition(state)
    }

    private fun mute(state: HandsFreeState): HandsFreeTransition {
        if (state.muted || state.phase == HandsFreePhase.ENDED) return HandsFreeTransition(state)
        return HandsFreeTransition(
            state.copy(muted = true, partialTranscript = ""),
            listOf(HandsFreeEffect.StopListening)
        )
    }

    private fun unmute(state: HandsFreeState): HandsFreeTransition {
        if (!state.muted) return HandsFreeTransition(state)
        val effects = if (state.phase == HandsFreePhase.LISTENING) {
            listOf(HandsFreeEffect.StartListening)
        } else {
            emptyList()
        }
        return HandsFreeTransition(state.copy(muted = false), effects)
    }

    private fun end(state: HandsFreeState, status: String?): HandsFreeTransition = HandsFreeTransition(
        state.copy(
            phase = HandsFreePhase.ENDED,
            muted = false,
            partialTranscript = "",
            statusMessage = status
        ),
        listOf(
            HandsFreeEffect.StopListening,
            HandsFreeEffect.StopSpeaking,
            HandsFreeEffect.CancelReply,
            HandsFreeEffect.ReleaseCapture
        )
    )

    private fun captureBlocked(state: HandsFreeState): HandsFreeTransition {
        if (state.phase == HandsFreePhase.ENDED || state.phase == HandsFreePhase.IDLE) {
            return HandsFreeTransition(state.copy(statusMessage = CAPTURE_STATUS))
        }
        return HandsFreeTransition(
            state.copy(phase = HandsFreePhase.PAUSED, statusMessage = CAPTURE_STATUS),
            listOf(HandsFreeEffect.StopListening, HandsFreeEffect.StopSpeaking)
        )
    }

    private fun rejected(state: HandsFreeState, message: String): HandsFreeTransition {
        if (state.phase != HandsFreePhase.THINKING && state.phase != HandsFreePhase.SPEAKING) {
            return HandsFreeTransition(state)
        }
        return HandsFreeTransition(
            state.copy(phase = HandsFreePhase.LISTENING, statusMessage = message, partialTranscript = ""),
            listOf(HandsFreeEffect.StopSpeaking, HandsFreeEffect.StartListening)
        )
    }

    private fun failure(state: HandsFreeState, event: HandsFreeEvent.Failure): HandsFreeTransition {
        if (event.recoverable) return HandsFreeTransition(state.copy(statusMessage = event.message))
        if (state.phase == HandsFreePhase.ENDED) return HandsFreeTransition(state.copy(statusMessage = event.message))
        return HandsFreeTransition(
            state.copy(phase = HandsFreePhase.PAUSED, statusMessage = event.message),
            listOf(HandsFreeEffect.StopListening, HandsFreeEffect.StopSpeaking)
        )
    }

    companion object {
        const val BATTERY_STATUS = "Stopped to save battery."
        const val CAPTURE_STATUS = "Microphone stays off until the listening notification is visible."
    }
}

internal fun isSpokenEcho(heard: String, recentlySpoken: String): Boolean {
    val heardTokens = tokens(heard)
    val spokenTokens = tokens(recentlySpoken)
    if (heardTokens.isEmpty() || spokenTokens.isEmpty()) return false
    val heardNorm = heardTokens.joinToString(" ")
    val spokenNorm = spokenTokens.joinToString(" ")
    if (spokenNorm.contains(heardNorm)) return true
    if (heardTokens.size < 3) return false
    val spokenSet = spokenTokens.toSet()
    val overlap = heardTokens.count { it in spokenSet }
    return overlap.toFloat() / heardTokens.size >= 0.72f
}

private fun tokens(value: String): List<String> = value
    .lowercase()
    .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }

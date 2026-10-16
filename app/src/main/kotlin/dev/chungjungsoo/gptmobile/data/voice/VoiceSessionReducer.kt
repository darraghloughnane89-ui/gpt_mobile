package dev.chungjungsoo.gptmobile.data.voice

class VoiceSessionReducer {
    fun reduce(state: VoiceSessionState, event: VoiceEvent): VoiceTransition = when (event) {
        VoiceEvent.Start -> start(state)
        is VoiceEvent.PartialTranscript -> partial(state, event.text)
        is VoiceEvent.FinalUtterance -> finalUtterance(state, event.text)
        is VoiceEvent.ReplyProgress -> reply(state, event)
        VoiceEvent.PlaybackDrained -> playbackDrained(state)
        VoiceEvent.BargeIn -> interruptSpeaking(state, countBargeIn = true)
        VoiceEvent.Interrupt -> manualInterrupt(state)
        VoiceEvent.Mute -> mute(state)
        VoiceEvent.Unmute -> unmute(state)
        VoiceEvent.End -> end(state)
        VoiceEvent.AudioFocusLost -> focusLost(state)
        VoiceEvent.Resume -> resume(state)
        is VoiceEvent.SubmitRejected -> rejected(state, event.message)
        is VoiceEvent.Failure -> failure(state, event)
        is VoiceEvent.HeadsetChanged -> VoiceTransition(state.copy(usingHeadset = event.connected))
        is VoiceEvent.LevelChanged -> VoiceTransition(state.copy(level = event.level.coerceIn(0f, 1f)))
    }

    private fun start(state: VoiceSessionState): VoiceTransition {
        if (state.phase != VoicePhase.IDLE && state.phase != VoicePhase.ENDED && state.phase != VoicePhase.PAUSED) {
            return VoiceTransition(state)
        }
        return VoiceTransition(
            state.copy(
                phase = VoicePhase.LISTENING,
                muted = false,
                partialTranscript = "",
                replyText = "",
                recentlySpoken = "",
                level = 0f,
                statusMessage = null
            ),
            listOf(
                VoiceEffect.EngageHeadset,
                VoiceEffect.RequestAudioFocus,
                VoiceEffect.StartListening
            )
        )
    }

    private fun partial(state: VoiceSessionState, text: String): VoiceTransition {
        if (state.phase != VoicePhase.LISTENING || state.muted) return VoiceTransition(state)
        return VoiceTransition(state.copy(partialTranscript = text, statusMessage = null))
    }

    private fun finalUtterance(state: VoiceSessionState, text: String): VoiceTransition {
        val trimmed = text.trim()
        if (state.muted || state.phase != VoicePhase.LISTENING) return VoiceTransition(state)
        if (trimmed.isEmpty() || EchoGuard.isEcho(trimmed, state.recentlySpoken)) {
            return VoiceTransition(state.copy(partialTranscript = ""))
        }
        return VoiceTransition(
            state.copy(
                phase = VoicePhase.THINKING,
                partialTranscript = "",
                lastUserUtterance = trimmed,
                replyText = "",
                recentlySpoken = "",
                statusMessage = null
            ),
            listOf(
                VoiceEffect.StopListening,
                VoiceEffect.StartVad,
                VoiceEffect.SubmitTurn(trimmed)
            )
        )
    }

    private fun reply(state: VoiceSessionState, event: VoiceEvent.ReplyProgress): VoiceTransition {
        if (state.phase != VoicePhase.THINKING && state.phase != VoicePhase.SPEAKING) {
            return VoiceTransition(state)
        }
        val spoken = (state.recentlySpoken + " " + event.sentences.joinToString(" ")).trim()
        if (event.replyComplete && event.sentences.isEmpty() && state.phase == VoicePhase.THINKING) {
            return VoiceTransition(
                state.copy(
                    phase = VoicePhase.LISTENING,
                    replyText = event.fullText,
                    recentlySpoken = spoken
                ),
                listOf(VoiceEffect.StopVad, VoiceEffect.StartListening)
            )
        }
        return VoiceTransition(
            state.copy(
                phase = if (event.sentences.isNotEmpty()) VoicePhase.SPEAKING else state.phase,
                replyText = event.fullText,
                recentlySpoken = spoken
            ),
            event.sentences.map { VoiceEffect.Speak(it) }
        )
    }

    private fun playbackDrained(state: VoiceSessionState): VoiceTransition {
        if (state.phase != VoicePhase.SPEAKING) return VoiceTransition(state)
        return VoiceTransition(
            state.copy(phase = VoicePhase.LISTENING, partialTranscript = ""),
            listOf(VoiceEffect.StopVad, VoiceEffect.StartListening)
        )
    }

    private fun manualInterrupt(state: VoiceSessionState): VoiceTransition {
        if (state.phase == VoicePhase.SPEAKING || state.phase == VoicePhase.THINKING) {
            return interruptSpeaking(state, countBargeIn = false)
        }
        if (state.phase == VoicePhase.LISTENING && !state.muted) {
            return VoiceTransition(
                state.copy(partialTranscript = ""),
                listOf(VoiceEffect.StopListening, VoiceEffect.StartListening)
            )
        }
        return VoiceTransition(state)
    }

    private fun interruptSpeaking(state: VoiceSessionState, countBargeIn: Boolean): VoiceTransition {
        if (state.phase != VoicePhase.SPEAKING && state.phase != VoicePhase.THINKING) {
            return VoiceTransition(state)
        }
        return VoiceTransition(
            state.copy(
                phase = VoicePhase.LISTENING,
                partialTranscript = "",
                bargeInCount = state.bargeInCount + if (countBargeIn) 1 else 0,
                statusMessage = null
            ),
            listOf(
                VoiceEffect.StopSpeaking,
                VoiceEffect.CancelReply,
                VoiceEffect.StopVad,
                VoiceEffect.StartListening
            )
        )
    }

    private fun mute(state: VoiceSessionState): VoiceTransition {
        if (state.muted || state.phase == VoicePhase.ENDED) return VoiceTransition(state)
        val effects = buildList {
            add(VoiceEffect.StopListening)
            if (state.phase == VoicePhase.SPEAKING || state.phase == VoicePhase.THINKING) {
                add(VoiceEffect.StopVad)
            }
        }
        return VoiceTransition(state.copy(muted = true), effects)
    }

    private fun unmute(state: VoiceSessionState): VoiceTransition {
        if (!state.muted) return VoiceTransition(state)
        val effects = when (state.phase) {
            VoicePhase.SPEAKING, VoicePhase.THINKING -> listOf(VoiceEffect.StartVad)
            VoicePhase.ENDED -> emptyList()
            else -> listOf(VoiceEffect.StartListening)
        }
        return VoiceTransition(state.copy(muted = false), effects)
    }

    private fun end(state: VoiceSessionState): VoiceTransition = VoiceTransition(
        state.copy(
            phase = VoicePhase.ENDED,
            muted = false,
            partialTranscript = "",
            level = 0f
        ),
        listOf(
            VoiceEffect.StopListening,
            VoiceEffect.StopVad,
            VoiceEffect.StopSpeaking,
            VoiceEffect.CancelReply,
            VoiceEffect.AbandonAudioFocus,
            VoiceEffect.RestoreAudioRoute
        )
    )

    private fun focusLost(state: VoiceSessionState): VoiceTransition {
        if (state.phase == VoicePhase.ENDED || state.phase == VoicePhase.PAUSED) return VoiceTransition(state)
        return VoiceTransition(
            state.copy(phase = VoicePhase.PAUSED, statusMessage = null),
            listOf(
                VoiceEffect.StopSpeaking,
                VoiceEffect.StopListening,
                VoiceEffect.StopVad
            )
        )
    }

    private fun resume(state: VoiceSessionState): VoiceTransition {
        if (state.phase != VoicePhase.PAUSED) return VoiceTransition(state)
        return VoiceTransition(
            state.copy(phase = VoicePhase.LISTENING, muted = false, statusMessage = null),
            listOf(VoiceEffect.StartListening)
        )
    }

    private fun rejected(state: VoiceSessionState, message: String): VoiceTransition {
        if (state.phase != VoicePhase.THINKING && state.phase != VoicePhase.SPEAKING) {
            return VoiceTransition(state)
        }
        return VoiceTransition(
            state.copy(phase = VoicePhase.LISTENING, statusMessage = message),
            listOf(VoiceEffect.StopVad, VoiceEffect.StopSpeaking, VoiceEffect.StartListening)
        )
    }

    private fun failure(state: VoiceSessionState, event: VoiceEvent.Failure): VoiceTransition {
        if (event.recoverable) {
            return VoiceTransition(state.copy(statusMessage = event.message))
        }
        if (state.phase == VoicePhase.ENDED) return VoiceTransition(state.copy(statusMessage = event.message))
        return VoiceTransition(
            state.copy(phase = VoicePhase.PAUSED, statusMessage = event.message),
            listOf(VoiceEffect.StopListening, VoiceEffect.StopVad, VoiceEffect.StopSpeaking)
        )
    }
}

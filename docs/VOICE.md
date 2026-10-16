# Hands-free voice

Wake phrase and driving mode stay on the phone. The microphone opens only after a user starts a foreground service and that service has posted its notification.

## Adoption seam

The hands-free session route from the voice-session work is not registered as a reducer on main. The speech router from the MiMo work is not on main either. This module keeps the names those changes can adopt:

- `VoiceSessionAdoption.ROUTE` is `voice_session`.
- `VoiceSpeechRouterHost` is the recognizer and speaker that route injects.
  - `recognizer` (`VoiceRecognizer`) — `start()` while listening, `stop()` when playback or the session ends. `events` emits partial text, final text, and failures.
  - `speaker` (`VoiceSpeaker`) — `speak(utteranceId, text)` for each reply sentence. `stop()` cuts playback immediately.
- `canAcceptVoiceUtterance` is the same busy-check the session uses before sending a turn.

`DeviceVoiceSpeechRouter` is the on-device fallback: Android `SpeechRecognizer` (offline when the phone offers it) and Android `TextToSpeech`. A later `VoiceSpeechRouter` can replace the binding in `VoiceModule` without changing the driving screen.

## Wake phrase

The phrase is **Hey Robin**. Detection is on-device.

The scorer follows the [openWakeWord](https://github.com/dscripka/openWakeWord) audio contract: 16 kHz mono PCM and 80 ms frames. openWakeWord is Apache-2.0. This build does not ship that project's ONNX weights. It scores a hey-robin log-mel template generated in `OpenWakeWordEngine`. `OnnxAdoptedWakeEngine` is the seam for an ONNX Runtime session ([ONNX Runtime](https://github.com/microsoft/onnxruntime) is MIT). A Vosk grammar recognizer ([Vosk](https://github.com/alphacep/vosk-api), Apache-2.0) can implement `WakePhraseEngine` the same way.

Picovoice Porcupine is not used. It is proprietary and needs a paid access key.

## When the microphone is on

`HandsFreeForegroundService` is a microphone foreground service. The user starts it from Voice settings or from driving mode. It is not started from boot.

`ForegroundCapture.begin` grants `NotificationCaptureGate` only after `startForeground` succeeds. `WakeFrameProcessor` and `GatedVoiceRecognizer` refuse audio while the gate is closed. Stopping the notification revokes the gate before the recorder is released.

The notification says the microphone is on and has **Stop listening**. A partial wake lock is held only while the wake loop runs, and it times out after 10 minutes. Listening stops when the battery is at or below 15% and the phone is not charging, or when the system reports a low battery. The service does not ask for a battery-optimization exemption. `START_NOT_STICKY` means the process does not resume listening by itself.

## Driving mode

The hands-free screen uses large Start, Stop speaking, Mute, and End controls. After a final transcript, the turn goes through the existing chat path (`ChatViewModel.submitVoiceUtterance`) and the first reply is read aloud sentence by sentence. When playback finishes, listening starts again. End leaves the microphone running only if Hey Robin is still enabled. Otherwise the service stops.

There is no Room change.

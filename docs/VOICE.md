# Voice

MiMo is an optional speech backend. Speech recognition uses MiMo ASR. Spoken replies use MiMo TTS. When MiMo is off, or the key is missing, or a MiMo request fails, recognition and playback stay on the phone.

The hands-free session route is `voice_session`. That destination is not on main. `VoiceSpeechRouter` is the recognizer and speaker that route should inject.

## What the voice session adopts

`VoiceSessionAdoption.ROUTE` is `voice_session`.

`VoiceSpeechRouter` exposes:

- `recognizer` (`VoiceRecognizer`) — `start()` while listening, `stop()` when playback or the session ends. `events` emits partial text, final text, and failures.
- `speaker` (`VoiceSpeaker`) — `speak(utteranceId, text)` for each reply sentence. `stop()` cuts playback immediately.
- `activeBackend` — `MIMO` or `DEVICE`.

Collect `recognizer.events` before calling `start()`, then call `speaker.speak` for each sentence of the reply. `speaker.stop()` is the barge-in and the stop control. The MiMo speaker also stops itself when the microphone hears speech over the reply. After `speak` returns, start the recognizer again.

`selectVoiceBackend(useMiMo, hasKey)` is the same choice the router makes. There is no Room change. The toggle lives in DataStore (`voice_speech`). The key lives in `SecretVault`.

## Endpoint and models

Checked 16 October 2026 against Xiaomi's docs.

| Use | Value | Source |
| --- | --- | --- |
| Base URL | `https://token-plan-ams.xiaomimimo.com/v1` | [Token Plan quick access](https://mimo.mi.com/docs/en-US/tokenplan/Token%20Plan/quick-access) (European cluster) |
| Method and path | `POST /chat/completions` | [ASR](https://mimo.mi.com/docs/en-US/api/audio/Speech-Recognition), [TTS](https://mimo.mi.com/docs/en-US/api/audio/tts) |
| Full URL | `https://token-plan-ams.xiaomimimo.com/v1/chat/completions` | Base URL plus the chat completions path |
| ASR model | `mimo-v2.5-asr` | [Model summary](https://mimo.mi.com/docs/en-US/quick-start/summary/model), [ASR](https://mimo.mi.com/docs/en-US/api/audio/Speech-Recognition) |
| TTS model | `mimo-v2.5-tts` | [Model summary](https://mimo.mi.com/docs/en-US/quick-start/summary/model), [TTS](https://mimo.mi.com/docs/en-US/api/audio/tts) |
| Other TTS ids | `mimo-v2.5-tts-voiceclone`, `mimo-v2.5-tts-voicedesign` | Same TTS page. This app calls the preset model only. |

The pay-as-you-go host in the audio samples is `https://api.xiaomimimo.com/v1`. Token Plan keys (`tp-` or `ttp-`) are not interchangeable with pay-as-you-go keys (`sk-`). This app calls the Europe token-plan host above.

Auth, from [API integration](https://mimo.mi.com/docs/en-US/quick-start/faq/api-integration) and the [OpenAI-compatible chat](https://mimo.mi.com/docs/api/chat/openai-api) page: send either `api-key: $MIMO_API_KEY` or `Authorization: Bearer $MIMO_API_KEY`. Requests send both. `api-key` is redacted in network logs.

### Speech recognition

[ASR](https://mimo.mi.com/docs/en-US/api/audio/Speech-Recognition) takes one user message whose content is `input_audio`. The data URI is `data:{MIME_TYPE};base64,{audio}`. `asr_options.language` is `auto`, `zh`, or `en`. This app sends `auto` and a WAV container (`audio/wav`) of 16 kHz mono PCM16 captured on the phone. The transcript is `choices[0].message.content`.

### Spoken replies

[TTS](https://mimo.mi.com/docs/en-US/api/audio/tts) puts the words to speak in an assistant message, not a user message. Preset voices go in `audio.voice`. The default is `mimo_default`. Built-in ids from that page and the [synthesis guide](https://mimo.mi.com/docs/en-US/quick-start/usage-guide/audio/speech-synthesis-v2.5): `mimo_default`, `冰糖`, `茉莉`, `苏打`, `白桦`, `Mia`, `Chloe`, `Milo`, `Dean`.

Streaming is `stream: true` with `audio.format` set to `pcm16` (`pcm` means the same format). Chunks are server-sent events. PCM bytes are base64 in `choices[0].delta.audio.data`. The synthesis guide describes that audio as 24 kHz PCM16LE mono, which is what playback uses. A non-streaming response would use `audio.format: wav` and `choices[0].message.audio.data`.

## Playback and barge-in

PCM chunks are written to an `AudioTrack` as they arrive. `VoiceSpeaker.stop()` pauses, flushes, and releases that track, and cancels the HTTP stream.

While MiMo audio plays, a `VOICE_COMMUNICATION` capture with echo cancellation watches the microphone. Three windows above the energy gate count as speech and call the same stop. The stop control in Settings does this too. On-device playback uses `TextToSpeech.stop()`.

## Key

`SecretVault` already stores credentials (`voice-mimo-api-key` for the voice screen). Settings → Voice can save or remove that key. The value is not written to DataStore, logs, or Room.

If that vault entry is empty, a platform whose API URL is `https://token-plan-ams.xiaomimimo.com/v1` supplies its vault-backed token. An enabled platform is preferred. The token is the one `SettingRepository` already reads out of `SecretVault`.

## Fallback

Settings → Voice has **Use MiMo speech**.

- Toggle off: Android `SpeechRecognizer` (on-device when the phone offers it) and Android `TextToSpeech`.
- Toggle on, no key: the same on-device engines. The screen says the key is missing.
- Toggle on, key present: MiMo ASR and streaming MiMo TTS.
- MiMo recognition or synthesis throws: that turn uses the on-device engine. Cancelling playback does not start a second reading.

Play sample on the voice screen speaks one sentence and can be stopped, including by talking over a MiMo reply when the microphone permission is granted.

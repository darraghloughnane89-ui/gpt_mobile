# GPT Mobile audit and plan

Base for this plan: **fork `main` at `d99a41fe`**, which matches upstream `Taewan-P/gpt_mobile` `main` exactly.

Upstream’s latest release tag is **`v0.9.1` (`e3a14c38`)**. `main` is nine commits newer (OpenAI stream error propagation, streamed tool names when a router blanks later deltas, Russian Weblate strings). `versionName` is still `0.9.1` (`versionCode` 25). The tag is an ancestor of `main`, so the plan uses `main`.

This document is an audit and a build plan. It does not implement product features.

Vocabulary follows `CONTEXT.md`: a **Platform** is a user-defined profile; a **ClientType** is the protocol family; a **Local Platform** runs a **Local Model** on device. “Bot” below means a new persona entity, separate from a Platform.

## What already ships

Version `0.9.1` is past the original multi-API chat app. On `main` today:

- Platforms for OpenAI, Anthropic, Google, Groq, OpenRouter, Ollama, Custom (OpenAI-compatible URL), and on-device LiteRT-LM. Several Platforms can answer one question side by side.
- Per-chat model override (`chat_platform_model_v2`). Remote models are free text. Local models come from the project Model Catalog.
- Image attachments, resized and cached, sent as vision input. OpenAI, Anthropic, and Google can upload a provider file reference. The picker accepts `image/*` only.
- System prompt, temperature, top-p, top-k, max tokens, Gemini safety, reasoning, resumable OpenAI replies.
- Optional agent tools per Platform: `web_search` (Firecrawl, Perplexity, or Exa), hardened `read_url`, and MCP Streamable HTTP (public, bearer, OAuth). Parallel runs, traces, cancellation, foreground service.
- Token-budget compaction with Room checkpoints, including native OpenAI and Anthropic compaction.
- Chat list search over titles and message text, markdown export, duplicate chat, revisions and retry, math and code rendering, Material You, per-app language.
- API credentials in an Android Keystore AES-GCM vault under `noBackupFilesDir`. Backup of `chat_v2` nulls the legacy `token` column. Vault files are excluded from backup.

Still absent, and in scope below: projects, bots, two-way voice, share-sheet entry, home widget, default assistant, a first-class Grok preset, GGUF/llama.cpp, camera capture, editable long-term memory, Drive sync, biometric lock, and cost tracking.

## Architecture

Single `:app` module. About 280 Kotlin files, ~39,000 lines under `app/src/main`. Packages: `data/`, `di/`, `presentation/`, `util/`.

- **DI.** Hilt `@Singleton` modules in `di/` for the database, network, repositories, catalog, and local runtime.
- **Persistence.** Room `chat_v2` is `ChatDatabaseV2` **version 11**, schema exported under `app/schemas/`. Migrations `1→11` live in `ChatDatabaseV2Migrations.kt` and are registered in `DatabaseModule`. Legacy `ChatDatabase` (version 2) remains for the v1→v2 migrate screen.
- **Networking.** Ktor. Chat completion goes through `ChatRepositoryImpl.openPreparedSession`: OpenAI uses the Responses adapter; Groq, Ollama, OpenRouter, and Custom share the OpenAI-compatible adapter; Anthropic, Google, and LiteRT-LM have their own. Older `*APIImpl` classes still perform streaming, compaction, and file upload.
- **Streaming.** Adapters emit `Flow<ProviderEvent>` (`TextDelta`, `ThinkingDelta`, `ToolCall`, `Usage`, `Failed`, `Completed`). `AgentRunner` owns the remote tool loop. LiteRT-LM owns its own loop (ADR 0003) and bridges tool calls into the same timeline. A foreground service keeps a run alive off-screen.
- **Local inference.** `ClientType.LITERT_LM` is a normal Platform (ADR 0001). The engine stays warm per chat and rebuilds when history diverges (ADR 0002).

Hot files, measured on this base:

| File | Lines |
| --- | ---: |
| `presentation/ui/chat/ChatViewModel.kt` | 1589 |
| `presentation/ui/setting/ToolConnectionsScreen.kt` | 1241 |
| `presentation/ui/chat/ChatScreen.kt` | 1157 |
| `presentation/ui/setting/PlatformSettingScreen.kt` | 942 |
| `data/repository/ChatRepositoryImpl.kt` | 853 |

## Room today

| Version | Change |
| --- | --- |
| 1→2 | Per-chat model rows |
| 2→3 | Attachment JSON replaces legacy file paths |
| 3→4 | Structured assistant revisions |
| 4→5 | Gemini safety columns |
| 5→6 | Legacy API URL repair |
| 6→7 | `secret_ref`, tool connections, bindings, runs, tool events |
| 7→8 | Assistant timeline |
| 8→9 | `local_models` |
| 9→10 | Local sampling columns (`top_k`, `max_tokens`, accelerator) |
| 10→11 | Context checkpoints, model capacities, resumable-replies flag |

`chats_v2` is a flat list: `title`, `enabled_platform` (comma-separated Platform uids), timestamps. There is no project, bot, memory, or usage table.

## Build on this VM

`./gradlew assembleDebug` **succeeded**.

The VM had OpenJDK 21 and no Android SDK. Installed Android command-line tools `13114758`, accepted licenses, and installed `platforms;android-37.0` plus build-tools `37.0.0`. AGP also downloaded build-tools `36.0.0` during configuration. Added an 8 GB swap file because the VM had none and Gradle’s heap is `-Xmx2048m`. `local.properties` points at `$HOME/Android/Sdk` and is gitignored.

Gradle 9.4.1 ran on **JDK 21**. Source and target compatibility stay Java 17. The debug APK is signed with the Android debug certificate (`CN=Android Debug`, APK Signature Scheme v2). No keystore was committed.

Artifact: `app/build/outputs/apk/debug/app-debug.apk`, 134 MB, `versionName` 0.9.1. SHA-256 `312a9554fe01e14a8ecfd37f759417c0aa4bd62ff6993c5419c4623a4b2a746a`. The agent artifact store rejects a single file around 110 MB, so the stored copy is `gpt-mobile-baseline-debug.apk.xz` (about 30 MB). Decompress with `xz -dk`.

## Tests and CI

- Unit tests: **731** `@Test` methods in `app/src/test` (agents, MCP, compaction, settings, backup).
- Instrumented tests: **51** `@Test` methods in `app/src/androidTest` (migrations, vault, Compose chat and home). They need a device. This VM has **no `/dev/kvm`**, so they were not run here.
- Existing CI: `pr-validation.yml` runs `:app:testDebugUnitTest` and `:app:lintDebug` on every pull request. `ktlint.yml` (ktlint 1.3.1, `android_studio`) and `codeql.yml` run for PRs into `main`. `release-build.yml` signs with repository secrets on tags. `debug-build.yml` builds a debug APK for PRs into `main` via `pull_request_target` on macOS and comments on the PR.
- Added in this change: `.github/workflows/debug-apk.yml` builds a debug-signed APK on every **push and pull request whose base is not `main`**, on `ubuntu-latest`, and uploads it. It uses `pull_request`, not `pull_request_target`, and it does not comment. Those artifacts are for agents. Darragh does not take per-milestone APKs.
- Added `scripts/emulator-smoke.sh` and `.github/workflows/emulator-smoke.yml` (`workflow_dispatch` only). GitHub-hosted `ubuntu-latest` exposes KVM, so `reactivecircus/android-emulator-runner` on API 34 is a workable smoke. Promote that workflow to `pull_request` after one green manual run.

`debug-build.yml` checks out pull-request head code with `pull-requests: write` and `allow-unsafe-pr-checkout`. Leave it in place for the existing “APK link” comment. Do not copy that pattern into new workflows.

## Security

- v2 Platform tokens are stored by `AndroidSecretVault` (Keystore AES-GCM, `noBackupFilesDir`), then the Room `token` column is cleared after a verified read. This meets the “Keystore” half of the approved storage item. Jetpack `EncryptedSharedPreferences` is a weaker and older API; do not add it.
- `SettingViewModel` and `SettingDataSourceImpl` can still write plaintext tokens into DataStore for the old `ApiType` keys. The live settings UI uses `SettingViewModelV2` and the vault. Workstream (f) should stop the plaintext path.
- `android:usesCleartextTraffic="true"` is process-wide so Ollama and approved MCP endpoints can use HTTP. MCP cleartext is additionally opt-in per connection.
- Assigned tools run with no confirmation prompt.
- Sanitized backup nulls `platform_v2.token` and does not include vault files. `secret_ref` values in a restored database point at nothing until the user re-enters keys.
- Hugging Face OAuth client id and redirect in `app/build.gradle.kts` are still the `REPLACE_WITH_YOUR_*` placeholders.

## Code quality

ktlint is enforced on PRs to `main` (`android_studio`, no trailing commas, comment above a value parameter rather than beside it). Application code has few `TODO`s. The debt that will hurt parallel work is concentration: `ChatViewModel`, `ChatScreen`, `NavigationGraph.kt`, `AndroidManifest.xml`, `ChatDatabaseV2.kt`, and `DatabaseModule.kt`. Historical migrations in `ChatDatabaseV2Migrations.kt` should stay untouched.

`CLAUDE.md` still describes target SDK 35 and omits agents. `AGENTS.md` says target SDK 36. The Gradle file is `compileSdk` / `targetSdk` **37**, min SDK 31.

## Comparison

| Capability | ChatGPT / Claude / Grok apps | ChatterUI / Maid | This tree |
| --- | --- | --- | --- |
| Projects (chats, instructions, files) | Yes | Character-scoped chats | Flat chat list |
| Custom bots | Custom GPTs; Claude projects; Grok custom instructions | Character cards, lorebooks | One system prompt and tool set per Platform |
| Two-way voice with interrupt | Yes | System TTS, and STT in Maid | None |
| Images / camera | Yes | Images | Images from the gallery. No camera entry |
| PDF and other files | Yes | Documents in ChatterUI | MIME helpers exist; picker and upload allow images only |
| Web search and tools | Apps, connectors, MCP | Thin or absent | Search, `read_url`, MCP HTTP |
| Editable memory | Yes, including per project | Lorebooks / author notes | Compaction checkpoints only |
| Share sheet, widget, assistant | Share and widgets on the official apps | No | No |
| Grok / xAI | First party in the Grok app | Via a custom OpenAI-compatible URL | Custom or OpenRouter URL, no preset |
| On-device models | Not the point of those apps | GGUF via llama.cpp | LiteRT-LM `.litertlm` catalog. No GGUF |
| Backup | Vendor sync | Optional Supabase in Maid | Sanitized Android backup. No Drive sync |
| App lock | Account login | Local | No biometric gate |
| Usage and cost | Plan meters | Token estimates in ChatterUI | `ProviderEvent.Usage` is not persisted or priced |

## Ranked milestones

Order is value against effort, with the two must-haves and two-way voice at the top. Size is surface area (schema, screens, native code), not a schedule. Streams may build in parallel; merge order is in the swarm section. None of these milestones produces an APK for Darragh.

1. **Two-way voice.** Hands-free loop: live speech-to-text, spoken replies, and interrupt while the bot is talking. First slice is Android `SpeechRecognizer` plus `TextToSpeech`, with barge-in that stops speech when the user talks and then listens again. A later slice of the same stream can add a provider realtime audio socket (OpenAI Realtime, Gemini Live, or xAI voice) behind the same session type. **Large.** Room: none. Risk: audio focus, Bluetooth, and partial-result timing. Areas: new `data/voice/`, `presentation/ui/voice/`, `RECORD_AUDIO`, a small hook in `ChatScreen`.
2. **Custom bots and a quick switcher.** A bot has a name, persona, instructions, and its own enabled tools. The composer switches bots without cloning a Platform or its key. Platform system prompt remains the default when no bot is selected. **Medium.** Room: version 12. Risk: compaction must hash the bot instructions. Areas: new bot tables, settings list, composer switcher, `InstructionStack`.
3. **Projects.** A project is a folder of chats with shared instructions and files. Home groups chats by project. Project files enter the context prefix. **Large.** Room: version 13. Risk: context budget and file lifecycle. Areas: new project tables, `chats_v2.project_id`, home, `ContextBuilder`, `CompactionKeys`.
4. **Grok / xAI preset and model-list fetch.** Default `https://api.x.ai/v1` on the existing OpenAI-compatible adapter, plus `GET /models` cached for Custom, OpenRouter, Groq, Ollama, and xAI. Per-chat model already exists. **Small.** Room: none if the cache is a file; a table only if the cache must survive in Room. Risk: low, endpoint quirks.
5. **Camera.** Take a photo and ask about it, reusing the image attachment path. **Medium.** Room: none. Areas: a camera contract and a chat entry point. Workstream (e).
6. **Share-sheet target.** Accept `text/plain`, `image/*`, and `application/pdf` into a new or existing chat. PDF text is extracted on device for OpenAI-compatible Platforms; OpenAI, Anthropic, and Google can use their file APIs where the document type is supported. **Medium.** Room: none beyond the existing attachment JSON. Areas: a new share activity, PDF extract helper, widen `FileUtils.isSupportedUploadMimeType`.
7. **Long-term memory.** User-visible memories (text, source, timestamp), editable and deletable, injected as a bounded block. Project-scoped rows wait until projects have merged; the table has a nullable `project_id` from the start. This is separate from compaction. **Medium.** Room: version 15. Risk: treating memory text as instructions (keep it data, not a tool grant).
8. **On-device GGUF.** Add llama.cpp as another ClientType beside LiteRT-LM (ADR 0001). User-imported GGUF, arm64-v8a first. Keep the LiteRT catalog. **Large.** Room: version 14 only if `local_models` needs an engine column. Risk: native size on top of a 134 MB debug APK, ABI, and the warm-session rules in ADR 0002. MediaPipe is a worse fit while LiteRT-LM is already the Google on-device path.
9. **Token and cost tracking.** Persist `ProviderEvent.Usage` and an editable price table. Show totals per model and per chat. Use `TokenEstimator` when the provider sends no usage. **Medium.** Room: version 16.
10. **Biometric app lock.** `BiometricPrompt` on resume. **Small.** Room: none.
11. **Home-screen widget.** New chat, and a voice shortcut once voice has merged. Glance or `RemoteViews`. **Small.** Room: none.
12. **Default digital assistant.** `VoiceInteractionService` so a power-button long-press opens voice chat. Depends on the voice session. **Medium.** Room: none. Risk: role APIs and OEM power-button behavior.
13. **Google Drive backup and sync.** Manual sanitized backup/restore first, then sync of chats. Never upload vault secrets. Merge last so the file format includes the final schema. **Large.** Room: version 17 for sync cursors. Risk: conflict handling and OAuth scope.
14. **Keystore cleanup.** v2 keys already use the Keystore vault. Remove the unused plaintext DataStore token path. **Small.** Room: none.

Leave these alone unless a stream is already in the file: another web-search vendor, a vector database, cloud sync of secrets, and a rewrite of LiteRT-LM. Chat search and markdown export already work; a FTS index can follow later and is not one of the six streams.

## Test strategy

Every swarm PR includes tests for the behavior it adds. A PR without them does not merge.

- **Unit tests** in `app/src/test`, named `methodName_condition_expectedResult`. Cover reducers, instruction composition, migration SQL via the existing migration test style, voice barge-in state, price math, PDF extraction, and backup sanitizing. No network.
- **Instrumented and Compose UI tests** in `app/src/androidTest`. One path per new screen: create bot, create project, share text, camera permission denied, memory edit, biometric cancel, widget does not need a Room test. Reuse `ChatDatabaseV2MigrationInstrumentedTest` for any migration.
- **Emulator smoke.** `scripts/emulator-smoke.sh` runs `connectedDebugAndroidTest` and then starts `MainActivity` and checks the process. `.github/workflows/emulator-smoke.yml` runs that on API 34 with `reactivecircus/android-emulator-runner`. It is `workflow_dispatch` until a green run; then enable it for pull requests whose base is not `main`.
- **Feature smoke before Darragh’s APK** is manual on an emulator or device, one pass per shipped feature, written down. See the final gate below.

`./gradlew :app:testDebugUnitTest` stays the fast check on every PR (`pr-validation.yml`).

## Swarm plan

Six streams, one feature branch and PR each (`cursor/<name>-ba42` is this agent’s prefix; later agents use their own). They branch from fork `main` at or after `d99a41fe`.

### Foundation PR

No second foundation PR. The shared pieces are already on `main`: Room migrations through version 11, ClientType adapters, the Keystore vault, navigation, and CI. A stub migration chain would only add empty versions. This audit PR adds the internal debug-APK workflow and the emulator smoke harness.

One code contract is created inside stream (b), not as a standalone PR: `InstructionStack` (Platform prompt, then project instructions, then bot instructions). Stream (a) rebases onto that type before it injects project text. Compaction fingerprints include the stack.

### Ownership and migration rules

Current database version is **11**. Each stream that needs schema puts SQL in a **new** file under `data/database/migration/`. It does not edit `ChatDatabaseV2Migrations.kt`. It does not bump `@Database(version)` on the feature branch. At merge, the coordinator assigns the next version, registers the migration in `DatabaseModule`, adds entities on `ChatDatabaseV2`, and exports the schema.

Reserved versions, in merge order:

| Version | Stream | Tables and columns |
| --- | --- | --- |
| 12 | (b) bots | `bots`, `bot_tool_bindings`, `chats_v2.bot_uid` |
| 13 | (a) projects | `projects`, `project_files`, `chats_v2.project_id` |
| 14 | (d) offline models | engine column on `local_models`, only if GGUF cannot reuse the current row |
| 15 | (f) memory | `memories` (`project_id` nullable) |
| 16 | (f) usage | `usage_events`, `model_prices` |
| 17 | (f) sync | `sync_state` |

(c) voice, (e) phone integration, the Grok preset, and the biometric lock take **no** Room version.

Rules:

- New tables only, except `bot_uid` and `project_id` on `chats_v2`. No other stream alters `chats_v2`, `messages_v2`, or `platform_v2`.
- One migration file per stream. (f) has three, merged as three PRs or three commits rebased into versions 15–17. Do not combine them with another stream’s SQL.
- If two branches both started from version 11, the later one rebases and renumbers. Do not ship a migration whose `from` version is not the current `main` version.
- Navigation: new `fun NavGraphBuilder.<feature>Navigation()` in a new file. Add constants to `Route.kt`. `NavigationGraph.kt` gains a single call. Rebase that call.
- `ChatViewModel.kt` and `ChatScreen.kt`: a new collaborator class and one call site. Feature logic stays in the stream’s package.
- Manifest: (c) may add only `RECORD_AUDIO`. (e) owns share filters, the widget receiver, the assistant service, and the camera entry. Rebase if both touch `AndroidManifest.xml`.
- `FileUtils.isSupportedUploadMimeType` is owned by (e) for the PDF change. Other streams leave it.

| Stream | Owns | Leaves alone |
| --- | --- | --- |
| (a) Projects | `data/project/`, project screens, home grouping, project files in `ContextBuilder` | Bot tables, voice, manifest components |
| (b) Custom bots | `data/bot/`, `InstructionStack`, bot settings, composer switcher, bot tool bindings | Project tables, `agent_tool_bindings` semantics for Platforms |
| (c) Voice | `data/voice/`, `presentation/ui/voice/`, `RECORD_AUDIO` | Assistant service, widget |
| (d) Providers and offline models | xAI preset, model-list client, `ClientType` for llama.cpp, local runtime beside `LiteRtLmAdapter` | LiteRT catalog behavior, ADR 0002 warm-session bookkeeping |
| (e) Phone integration | Share activity, widget, assistant service, camera entry, PDF extract | Voice session internals |
| (f) Memory, sync, security, usage | memories, usage, Drive, biometric gate, DataStore token removal | Vault cipher format, unless a test shows a leak |

### Merge order

Schema merges go 12 → 17 as in the table. Streams with no schema can merge as soon as their tests pass.

Suggested attention order when two PRs conflict: **(c) voice**, **(b) bots** (lands `InstructionStack`), **(a) projects**, **(d) Grok preset** then GGUF, **(e) share and camera**, **(e) widget**, **(f) keystore cleanup and memory**, **(f) usage**, **(e) assistant** after voice, **(f) Drive** last.

### APKs

Agents keep using the debug APK artifact from `debug-apk.yml` on non-`main` branches. Darragh does not get those builds.

He gets **one APK at the end**, after this gate:

1. `./gradlew :app:testDebugUnitTest` and `:app:lintDebug` pass.
2. `connectedDebugAndroidTest` passes on an API 34 emulator.
3. `scripts/emulator-smoke.sh` passes.
4. A manual smoke of every feature that merged: voice interrupt, bot switch, project files, Grok preset, GGUF load or a documented device-only limit, camera, share of text/image/PDF, memory edit, cost row, biometric cancel and success, widget tap, assistant launch, Drive backup of a chat and restore without secrets.
5. A short report at `docs/TEST_REPORT.md` listing commands, emulator image, pass/fail, and anything not run.

That APK is the debug-signed `app-debug.apk` from the same commit as the report, unless a release signing secret is available. Do not hand over an intermediate build.

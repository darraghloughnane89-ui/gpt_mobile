# Performance

Robin ships an arm64-only release. Startup, chat open, long-chat scrolling, and streaming replies are covered by a Baseline Profile and a Macrobenchmark module. Those tests need a physical arm64 device. This environment has no KVM, so the benchmark classes are compiled here and run on a Pixel 9 Pro.

## What the release build does

- R8 full mode is on (`android.enableR8.fullMode=true`), and strict full-mode keep rules are on so a keep rule that full mode would ignore fails the release build.
- `app/proguard-rules.pro` records the keep audit: kotlinx.serialization serializers, MCP SDK companions, and the LiteRT-LM JNI surface. LiteRT-LM is the on-device engine; its native library is the llama.cpp-class runtime. Room, Hilt, WorkManager, Glance, Parcelize, and the Ktor engines stay on their consumer rules because the app references them directly.
- Room (`chat` and `chat_v2`), the shared Ktor client, and LiteRT-LM are created on first use on a background thread. Debug builds install StrictMode (`detectAll` + `penaltyLog`) so a later main-thread disk or network read shows up in logcat.
- The chat list and message list use stable keys, `contentType`, `remember`, and `derivedStateOf`. Message rows are an immutable `ChatTurnRenderState`, so a streaming token recomposes the active turn and skips the others.

## Compile the benchmarks

No device and no emulator:

```bash
./gradlew :benchmark:assembleBenchmarkRelease
```

`:benchmark` is a self-instrumenting test module. It contains:

| Test | Measures |
| --- | --- |
| `StartupBenchmark.startup` | Cold startup to the chat list |
| `ChatBenchmarks.openChat` | Opening a chat from the list |
| `ChatBenchmarks.scrollLongChat` | Flinging a long transcript |
| `ChatBenchmarks.streamReply` | Frames while a reply streams |
| `BaselineProfileGenerator` | Startup profile plus the same chat journey |

`assembleDebug` and `assembleRelease` do not generate or run these tests. Baseline profile generation during the release build is off, so a release APK still builds without a phone attached.

## Pixel 9 Pro

Use a Pixel 9 Pro (tokay, arm64-v8a). An x86_64 emulator cannot load the release native engine, and this project does not run those tests under KVM.

1. Enable Developer options and USB debugging. Plug the phone in and accept the debugging prompt.
2. Confirm the device:

   ```bash
   adb devices -l
   ```

   The line should include `device` and `arm64`.
3. Install a debug or benchmark build once and finish setup. Enable one provider that can stream (an API key, or a downloaded on-device model).
4. Create a chat titled exactly `Benchmark long chat`. Give it enough turns to scroll, about 40 or more. The scroll and stream journeys find that title. If the title is missing they fall back to the first chat row, which is the most recently updated chat only when chats are not grouped under projects.
5. Leave the screen unlocked. Macrobenchmark turns animations off itself.

Generate the profile, then run the benchmarks:

```bash
./gradlew :app:generateBaselineProfile
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
```

`generateBaselineProfile` drives `BaselineProfileGenerator` on the connected phone and writes the profile into the app source set (startup and baseline). Commit the generated `baseline-prof.txt` and `startup-prof.txt` after a clean Pixel 9 Pro run. Until those files exist, release builds still install nothing extra; `CompilationMode.Partial()` uses a profile when one is installed and still runs without one.

Run a single journey while iterating:

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.benchmark.ChatBenchmarks#scrollLongChat
```

Results land in `benchmark/build/outputs/connected_android_test_additional_output`. Compare `StartupTimingMetric` and `FrameTimingMetric` before and after a profile change. A useful startup comparison is `timeToInitialDisplay`. For scroll and streaming, compare `frameDurationCpuMs` P95 and P99.

The benchmark variant of the app is the release-like, non-debuggable build the Baseline Profile plugin adds. It is profileable from the shell (`app/src/benchmark/AndroidManifest.xml`). Do not point the benchmarks at the debuggable debug variant if you want numbers that match the arm64 release.

#!/usr/bin/env bash
# Boots only when an emulator is already running (CI starts it).
# Runs the instrumented suite, then confirms the app process stays up.
set -euo pipefail

adb wait-for-device
adb shell input keyevent 82 || true

./gradlew connectedDebugAndroidTest

adb shell am start -W -n dev.chungjungsoo.gptmobile/.presentation.ui.main.MainActivity
sleep 4
adb shell pidof dev.chungjungsoo.gptmobile >/dev/null
echo "emulator-smoke: MainActivity process is running"

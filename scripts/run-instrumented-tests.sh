#!/usr/bin/env bash
# Runs connectedDebugAndroidTest on the booted emulator and always writes
# docs/TEST_REPORT.md. The Gradle exit code is preserved.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${root}"

artifacts="${root}/app/build/smoke-artifacts"
mkdir -p "${artifacts}/logcat" "${artifacts}/screenshots"

adb wait-for-device
{
  echo "profile=${SMOKE_PROFILE:-unknown}"
  echo "sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "model=$(adb shell getprop ro.product.model | tr -d '\r')"
  echo "fingerprint=$(adb shell getprop ro.build.fingerprint | tr -d '\r')"
} > "${artifacts}/device.txt"

adb logcat -c || true
adb logcat -v threadtime > "${artifacts}/logcat/logcat.txt" &
logcat_pid=$!

set +e
./gradlew :app:connectedDebugAndroidTest --stacktrace
gradle_status=$?
set -e

kill "${logcat_pid}" >/dev/null 2>&1 || true
wait "${logcat_pid}" >/dev/null 2>&1 || true

adb exec-out screencap -p > "${artifacts}/screenshots/emulator.png" || true
adb pull /sdcard/Android/data/dev.chungjungsoo.gptmobile/files/smoke-screenshots "${artifacts}/screenshots/device" || true

bash "${root}/scripts/test-report.sh" || true
exit "${gradle_status}"

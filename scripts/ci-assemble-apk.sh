#!/usr/bin/env bash
# Builds a signed release APK when release signing inputs are present,
# and a debug APK otherwise. Passwords stay in the environment and are not printed.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

if [[ -z "${RELEASE_STORE_FILE:-}" || ! -f "${RELEASE_STORE_FILE}" ]]; then
  if [[ -n "${APP_KEYSTORE:-}" ]]; then
    dest="${RUNNER_TEMP:-/tmp}/release.keystore"
    printf '%s' "$APP_KEYSTORE" | tr -d '[:space:]' | base64 --decode > "$dest"
    export RELEASE_STORE_FILE="$dest"
  fi
fi

if [[ -z "${RELEASE_KEY_ALIAS:-}" && -n "${KEY_ALIAS:-}" ]]; then
  export RELEASE_KEY_ALIAS="$KEY_ALIAS"
fi
if [[ -z "${RELEASE_STORE_PASSWORD:-}" && -n "${KEY_PASSWORD:-}" ]]; then
  export RELEASE_STORE_PASSWORD="$KEY_PASSWORD"
fi
if [[ -z "${RELEASE_KEY_PASSWORD:-}" && -n "${RELEASE_STORE_PASSWORD:-}" ]]; then
  export RELEASE_KEY_PASSWORD="$RELEASE_STORE_PASSWORD"
fi

if [[ "${GITHUB_REF_TYPE:-}" == "tag" && -z "${APP_VERSION_NAME:-}" ]]; then
  export APP_VERSION_NAME="${GITHUB_REF_NAME#v}"
fi

mode="debug"
apk_path="app/build/outputs/apk/debug/app-debug.apk"
aab_path=""

if [[ -n "${RELEASE_STORE_FILE:-}" ]]; then
  if [[ ! -f "$RELEASE_STORE_FILE" || -z "${RELEASE_KEY_ALIAS:-}" || -z "${RELEASE_STORE_PASSWORD:-}" || -z "${RELEASE_KEY_PASSWORD:-}" ]]; then
    echo "Release keystore inputs are incomplete. Set the keystore, alias, and store password." >&2
    exit 1
  fi
  mode="release"
  ./gradlew assembleRelease bundleRelease --parallel
  apk_path="app/build/outputs/apk/release/app-release.apk"
  aab_path="app/build/outputs/bundle/release/app-release.aab"
else
  echo "Release signing secrets are absent. Building the debug APK."
  ./gradlew assembleDebug --parallel
fi

if [[ ! -f "$apk_path" ]]; then
  echo "Expected APK was not produced: $apk_path" >&2
  exit 1
fi

bytes="$(wc -c < "$apk_path" | tr -d ' ')"
echo "APK mode=$mode bytes=$bytes path=$apk_path"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "mode=$mode"
    echo "apk_path=$apk_path"
    echo "aab_path=$aab_path"
  } >> "$GITHUB_OUTPUT"
fi

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### APK"
    echo ""
    echo "- Mode: \`$mode\`"
    echo "- Size: \`$bytes\` bytes"
    echo "- SHA-256: \`$(sha256sum "$apk_path" | awk '{print $1}')\`"
  } >> "$GITHUB_STEP_SUMMARY"
fi

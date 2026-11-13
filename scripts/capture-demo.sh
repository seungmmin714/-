#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
moa_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
moa_adb="$moa_sdk/platform-tools/adb"
moa_device="${1:-emulator-5554}"
case "$moa_device" in
  emulator-*) ;;
  *) echo 'Use a dedicated Android emulator: this script clears demo test cards.' >&2; exit 1 ;;
esac
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
"$moa_adb" -s "$moa_device" install -r app/build/outputs/apk/debug/app-debug.apk
"$moa_adb" -s "$moa_device" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
moa_result=$("$moa_adb" -s "$moa_device" shell am instrument -w ai.moa.app.demo.test/androidx.test.runner.AndroidJUnitRunner)
printf '%s\n' "$moa_result"
if ! printf '%s\n' "$moa_result" | grep -q '^OK ('; then
  echo 'Demo UI tests failed; screenshots were not copied.' >&2
  exit 1
fi
mkdir -p docs/screenshots
"$moa_adb" -s "$moa_device" pull /sdcard/Android/data/ai.moa.app.demo/files/screenshots docs/

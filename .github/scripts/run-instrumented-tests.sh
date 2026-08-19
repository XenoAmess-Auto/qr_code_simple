#!/usr/bin/env bash
set -euo pipefail

diagnostics_dir="app/build/outputs/androidTest-diagnostics"
gradle_timeout_seconds="${GRADLE_TIMEOUT_SECONDS:-2700}"
adb_timeout_seconds="${ADB_TIMEOUT_SECONDS:-15}"
diagnostics_collected=0
gradle_pid=""

mkdir -p "$diagnostics_dir"

run_adb() {
  timeout --signal=KILL "${adb_timeout_seconds}s" adb "$@"
}

collect_diagnostics() {
  if ((diagnostics_collected)); then
    return
  fi
  diagnostics_collected=1
  set +e

  run_adb devices -l > "$diagnostics_dir/adb-devices.txt" 2>&1 || true
  run_adb logcat -d > "$diagnostics_dir/logcat.txt" 2>&1 || true
  run_adb shell dumpsys activity activities > "$diagnostics_dir/activities.txt" 2>&1 || true
  run_adb shell dumpsys window windows > "$diagnostics_dir/windows.txt" 2>&1 || true
  run_adb shell uiautomator dump /sdcard/window.xml > "$diagnostics_dir/uiautomator.txt" 2>&1 || true
  run_adb pull /sdcard/window.xml "$diagnostics_dir/window.xml" > "$diagnostics_dir/adb-pull.txt" 2>&1 || true
  run_adb exec-out screencap -p > "$diagnostics_dir/screenshot.png" 2> "$diagnostics_dir/screencap.txt" || true
}

on_exit() {
  local status=$?
  trap - EXIT
  if ((status != 0)); then
    collect_diagnostics
  fi
  exit "$status"
}

on_term() {
  if [[ -n "$gradle_pid" ]]; then
    kill -TERM "$gradle_pid" 2>/dev/null || true
  fi
  collect_diagnostics
  exit 143
}

on_int() {
  if [[ -n "$gradle_pid" ]]; then
    kill -INT "$gradle_pid" 2>/dev/null || true
  fi
  collect_diagnostics
  exit 130
}

trap on_exit EXIT
trap on_term TERM
trap on_int INT

# Run once: assertion failures are deterministic failures and are not rerun.
timeout --signal=TERM --kill-after=30s "${gradle_timeout_seconds}s" \
  ./gradlew --no-daemon :app:connectedDebugAndroidTest &
gradle_pid=$!
set +e
wait "$gradle_pid"
status=$?
set -e
gradle_pid=""
exit "$status"

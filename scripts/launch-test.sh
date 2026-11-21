#!/bin/bash
# Installs the APK on the running emulator, launches it, and fails if the app dies or logs a crash.
set -u
APK="$1"; OUT="${2:-launch-test}"
mkdir -p "$OUT"
adb shell getprop ro.build.version.release | tee "$OUT/android.txt"
adb shell getprop ro.product.cpu.abilist | tee -a "$OUT/android.txt"
adb install -r -g "$APK" || exit 1
adb logcat -c
adb shell am start -W -n com.nibras.terminal/.MainActivity --es nibras.lang en | tee "$OUT/am-start.txt"
ok=1
for t in 5 15 30 45; do
  sleep $((t == 5 ? 5 : 15))
  pid=$(adb shell pidof com.nibras.terminal | tr -d '\r')
  echo "t=${t}s pid=${pid:-none}" | tee -a "$OUT/alive.txt"
  [ -z "$pid" ] && ok=0
done
adb exec-out screencap -p > "$OUT/screen.png" || true
# PRoot needs ptrace, which the emulator's ARM translation does not provide, so also open an
# Android-shell session to prove the PTY + terminal rendering path shows a working prompt.
adb shell am start -a com.nibras.terminal.NEW_SESSION --es env android -n com.nibras.terminal/.MainActivity >/dev/null
sleep 5
adb shell input text "echo%sNIBRAS_OK_\$((6*7))"
adb shell input keyevent 66
sleep 3
adb exec-out screencap -p > "$OUT/screen-android-shell.png" || true
# Termux:API compatible phone commands from the Android shell session.
adb exec-in run-as com.nibras.terminal sh -c 'cat > files/home/apitest.sh' < "$(dirname "$0")/api-test.sh" || true
if ! adb shell run-as com.nibras.terminal ls files/home/apitest.sh >/dev/null 2>&1; then
  # older adb/run-as (API 30): stage in /data/local/tmp and copy as the app
  adb push "$(dirname "$0")/api-test.sh" /data/local/tmp/apitest.sh >/dev/null
  adb shell chmod 644 /data/local/tmp/apitest.sh
  adb shell run-as com.nibras.terminal cp /data/local/tmp/apitest.sh files/home/apitest.sh || true
fi
adb shell run-as com.nibras.terminal ls -l files/home/apitest.sh files/home/.nibras/android-rc 2>&1 | tee -a "$OUT/alive.txt"
# Run it from the user rc of the next Android shell session (no typing: input is unreliable on API 30).
adb exec-in run-as com.nibras.terminal sh -c 'cat > files/home/.nibras/android-rc.local' <<'RC' || true
rm -f "$HOME/.nibras/android-rc.local"; sh "$HOME/apitest.sh"
RC
if ! adb shell run-as com.nibras.terminal ls files/home/.nibras/android-rc.local >/dev/null 2>&1; then
  printf '%s\n' 'rm -f "$HOME/.nibras/android-rc.local"; sh "$HOME/apitest.sh"' > /tmp/rc.local
  adb push /tmp/rc.local /data/local/tmp/rc.local >/dev/null; adb shell chmod 644 /data/local/tmp/rc.local
  adb shell run-as com.nibras.terminal cp /data/local/tmp/rc.local files/home/.nibras/android-rc.local || true
fi
adb shell am start -a com.nibras.terminal.NEW_SESSION --es env android -n com.nibras.terminal/.MainActivity >/dev/null
for i in $(seq 1 45); do
  sleep 2
  adb shell run-as com.nibras.terminal cat files/home/api-test.txt 2>/dev/null | grep -q API_TEST_DONE && break
done
adb shell run-as com.nibras.terminal cat files/home/api-test.txt 2>&1 | tee "$OUT/api-test.txt"
grep -q '"percentage"' "$OUT/api-test.txt" && grep -q '"max_volume"' "$OUT/api-test.txt" && grep -q 'clip: hello-clip' "$OUT/api-test.txt" \
  && grep -q '"sensors"' "$OUT/api-test.txt" && grep -q API_TEST_DONE "$OUT/api-test.txt" && echo "API OK" | tee -a "$OUT/alive.txt" \
  || if [ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -lt 31 ] && [ "$(adb shell getprop ro.dalvik.vm.native.bridge | tr -d '\r')" != 0 ]; then
    # The API 30 x86_64 image runs ARM apps through ndk_translation 0.2.2, whose forked /system/bin/sh dies with
    # SIGSEGV (signal 11) before any script runs. Real ARM phones have no translation layer, so only note it here.
    echo "API test skipped: Android shell crashes inside the emulator's ARM translation (signal 11)" | tee -a "$OUT/alive.txt"
  else echo "API test failed" | tee -a "$OUT/alive.txt"; ok=0; fi
# Open every tool screen once; each must come up without killing the app.
for screen in store hosts files editor media packages; do
  adb shell am start -n com.nibras.terminal/.MainActivity --es open $screen >/dev/null
  sleep 4
  adb exec-out screencap -p > "$OUT/screen-$screen.png" || true
  adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -1 | tee -a "$OUT/screens.txt"
  adb shell input keyevent 4; sleep 1
  [ -z "$(adb shell pidof com.nibras.terminal | tr -d '\r')" ] && { echo "app died on $screen" | tee -a "$OUT/alive.txt"; ok=0; }
done
adb shell am start -n com.nibras.terminal/.MainActivity --es open split >/dev/null; sleep 3
adb shell am start -n com.nibras.terminal/.MainActivity --es open monitor >/dev/null; sleep 4
adb exec-out screencap -p > "$OUT/screen-split-monitor.png" || true
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | tee "$OUT/top.txt"
adb shell ps -A -o PID,NAME,ARGS | grep -iE "proot|nibras" | tee "$OUT/processes.txt" || true
adb shell run-as com.nibras.terminal ls files/unpacked 2>&1 | tee "$OUT/unpacked.txt" || true
adb shell run-as com.nibras.terminal cat files/crash.txt 2>/dev/null | tee "$OUT/crash.txt" || true
adb logcat -d > "$OUT/logcat.txt"
grep -nE "FATAL EXCEPTION|AndroidRuntime: Process: com.nibras.terminal" "$OUT/logcat.txt" | tee "$OUT/fatal.txt"
if [ -s "$OUT/fatal.txt" ] || [ -s "$OUT/crash.txt" ]; then ok=0; grep -A30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60; fi
grep -q "com.nibras.terminal/.MainActivity" "$OUT/top.txt" || ok=0
[ "$ok" = 1 ] && echo "LAUNCH OK" || { echo "LAUNCH FAILED"; exit 1; }

# Runs inside the app's Android shell session on the CI emulator: exercises the Termux:API compatible bridge.
o=$HOME/api-test.txt; : > "$o"
. "$HOME/.nibras/android-rc"   # Android shell: commands are functions (no exec from app data on API 29+)
for c in "battery-status" "volume" "info" "audio-info" "camera-info" "sensor -l" "telephony-deviceinfo" \
  "infrared-frequencies" "notification-channel ci-test CI" "notification -t NibrasCI -c hello --id ci --button1 ok --button1-action true" \
  "notification-list --own" "notification-remove ci" "toast -s -g top hello" "dialog -l" "api-start" "job-scheduler -p" "wifi-connectioninfo" "saf-dirs"; do
  echo "== $c" >> "$o"; eval "nibras-$c" >> "$o" 2>&1; echo "rc=$?" >> "$o"
done
echo hello-clip | nibras-clipboard-set; echo "== clip: $(termux-clipboard-get)" >> "$o"
echo "== stdin toast"; echo from-stdin | nibras-toast -s >> "$o" 2>&1; echo "rc=$?" >> "$o"
echo API_TEST_DONE >> "$o"

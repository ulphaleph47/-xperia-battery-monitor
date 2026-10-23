#!/usr/bin/env bash
# Rulează în emulator Android 15: instalează aplicația, pune date de test și face capturi de ecran.
PKG=ro.florin.bateriexperia
OUT=shots
mkdir -p "$OUT"
exec > >(tee "$OUT/run.log") 2>&1
set -x

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP
# dimensiuni apropiate de Xperia 1 V (ecran 21:9, ~417 dp lățime)
adb shell wm size 1096x2560
adb shell wm density 420

adb install -r app/build/outputs/apk/debug/app-debug.apk

NOW=$(adb shell date +%s | tr -d '\r')
python3 ci/make_samples.py "$NOW" > "$OUT/samples.csv"
wc -l "$OUT/samples.csv"
adb push "$OUT/samples.csv" /data/local/tmp/samples.csv
adb shell "run-as $PKG sh -c 'mkdir -p files; cat /data/local/tmp/samples.csv > files/samples.csv'"

shot() { # nume tab [derulare] [serie] [interval]
  adb shell am force-stop $PKG
  adb shell am start -n $PKG/.MainActivity --ei tab "$2" --ei series "${4:-0}" --ei range "${5:-0}" > /dev/null
  sleep 6
  if [ "${3:-0}" = "1" ]; then
    adb shell input swipe 548 2100 548 500 400
    sleep 1
  fi
  adb exec-out screencap -p > "$OUT/$1.png"
}

# --- stare: se descarcă, 63%
adb shell dumpsys battery unplug
adb shell dumpsys battery set status 3
adb shell dumpsys battery set level 63
adb shell dumpsys battery set temp 312
adb shell dumpsys battery set counter 3060000

adb shell cmd uimode night yes
shot 01_acasa_intunecat 0
shot 02_acasa_intunecat_derulat 0 1
shot 03_detalii_intunecat 1
shot 04_detalii_intunecat_derulat 1 1
shot 05_istoric_intunecat 2
shot 06_istoric_intunecat_derulat 2 1
shot 06b_istoric_temperatura 2 0 1 1
shot 06c_istoric_capacitate 2 0 3 2
shot 07_setari_intunecat 3
shot 08_setari_intunecat_derulat 3 1

# --- tema deschisă
adb shell cmd uimode night no
sleep 2
shot 09_acasa_deschis 0
shot 10_acasa_deschis_derulat 0 1
shot 11_istoric_deschis 2

# --- se încarcă, 45%
adb shell cmd uimode night yes
adb shell dumpsys battery set ac 1
adb shell dumpsys battery set status 2
adb shell dumpsys battery set level 45
adb shell dumpsys battery set temp 355
shot 12_acasa_incarcare 0

# --- conectat, dar nu încarcă, 80% (Battery Care)
adb shell dumpsys battery set status 4
adb shell dumpsys battery set level 80
adb shell dumpsys battery set temp 330
shot 13_acasa_battery_care 0

# --- complet încărcat
adb shell dumpsys battery set status 5
adb shell dumpsys battery set level 100
shot 14_acasa_complet 0

# --- erori
adb logcat -d -b crash > "$OUT/crash.log" 2>&1
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|ro\.florin" | tail -80 > "$OUT/logcat.txt"
adb shell dumpsys battery reset
echo "gata"

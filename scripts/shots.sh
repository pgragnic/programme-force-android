#!/bin/bash
# Captures d'écran sur l'émulateur (utilisé par .github/workflows/screens.yml)
POSES="$1"
mkdir -p shots
adb install -r app/build/outputs/apk/debug/*.apk
adb shell pm grant fr.pgragnic.force android.permission.POST_NOTIFICATIONS || true
adb shell cmd uimode night yes || true
shot() { n=$1; shift; adb shell am force-stop fr.pgragnic.force; sleep 2; adb shell am start -W -n fr.pgragnic.force/.MainActivity "$@" >> shots/start.txt; sleep 9; adb exec-out screencap -p > shots/$n.png; }
shot setup1 --es gear new
adb shell input swipe 160 500 160 150 500; sleep 3; adb exec-out screencap -p > shots/setup2.png
shot kb0 --es gear kb --ei tab 0
adb shell input swipe 160 500 160 150 500; sleep 3; adb exec-out screencap -p > shots/kb0b.png
shot bw0 --es gear bw --ei tab 0
adb shell input swipe 160 500 160 150 500; sleep 3; adb exec-out screencap -p > shots/bw0b.png
shot kbplay --es gear kb --ei play 5
shot tab0 --ez seed true --ei tab 0
for p in $POSES; do shot pose-$p --es demo $p --ef k 0.5; done
adb logcat -d -b crash > shots/crash.txt || true
exit 0

#!/bin/bash
# Captures d'écran sur l'émulateur (utilisé par .github/workflows/screens.yml)
POSES="$1"
mkdir -p shots
adb install -r app/build/outputs/apk/debug/*.apk
adb shell pm grant fr.pgragnic.force android.permission.POST_NOTIFICATIONS || true
adb shell cmd uimode night yes || true
shot() { n=$1; shift; adb shell am force-stop fr.pgragnic.force; sleep 2; adb shell am start -W -n fr.pgragnic.force/.MainActivity "$@" >> shots/start.txt; sleep 9; adb exec-out screencap -p > shots/$n.png; }
for t in 0 1 2 3 4; do shot tab$t --ez seed true --ei tab $t; done
for t in 0 2 3; do shot light$t --ez seed true --ei tab $t --es theme light; done
shot tab3 --ez seed true --ei tab 3; adb shell input swipe 160 500 160 120 600; sleep 4; adb exec-out screencap -p > shots/tab3b.png
adb shell input swipe 160 500 160 120 600; sleep 4; adb exec-out screencap -p > shots/tab3c.png
shot play5 --ez seed true --ei play 5
adb shell cmd statusbar expand-notifications; sleep 4; adb exec-out screencap -p > shots/notif.png; adb shell cmd statusbar collapse
shot play6 --ez seed true --ei play 6
for p in $POSES; do shot pose-$p --es demo $p --ef k 0.5; done
adb logcat -d -b crash > shots/crash.txt || true
adb logcat -d -b all > shots/full.txt || true
exit 0

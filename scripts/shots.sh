#!/bin/bash
# Captures d'écran sur l'émulateur (utilisé par .github/workflows/screens.yml)
POSES="$1"
mkdir -p shots
adb install -r app/build/outputs/apk/debug/*.apk
adb shell pm grant fr.pgragnic.force android.permission.POST_NOTIFICATIONS || true
adb shell cmd uimode night yes || true
shot() { n=$1; shift; adb shell am force-stop fr.pgragnic.force; sleep 2; adb shell am start -W -n fr.pgragnic.force/.MainActivity "$@" >> shots/start.txt; sleep 9; adb exec-out screencap -p > shots/$n.png; }
sw() { adb shell input swipe 160 500 160 150 500; sleep 3; adb exec-out screencap -p > shots/$1.png; }
QUICK=0; case " $POSES " in *" quick "*) QUICK=1;; esac
if [ $QUICK = 0 ]; then
for t in 0 1 2 3 4; do shot fr-tab$t --es lang fr --ez seed true --ei tab $t; done
shot fr-play5 --es lang fr --ez seed true --ei play 5
shot en-setup --es lang en --es gear new; sw en-setup-b
for t in 0 1 2 3 4; do shot en-tab$t --es lang en --ez seed true --ei tab $t; sw en-tab$t-b; sw en-tab$t-c; done
shot en-kb0 --es lang en --es gear kb --ei tab 0; sw en-kb0-b
shot en-play5 --es lang en --ez seed true --ei play 5
adb shell cmd statusbar expand-notifications; sleep 4; adb exec-out screencap -p > shots/en-notif.png; adb shell cmd statusbar collapse
shot en-play6 --es lang en --ez seed true --ei play 6
fi
case " $POSES " in *" setup4 "*) shot fr-setup4 --es lang fr --es gear new --ei setupstep 3; sw fr-setup4-b; sw fr-setup4-c;; esac
for p in $POSES; do [ "$p" = quick ] || [ "$p" = setup4 ] && continue; id=${p%@*}; k=0.5; [ "$id" != "$p" ] && k=${p#*@}; shot pose-$id-$k --es demo $id --ef k $k; done
adb logcat -d -b crash > shots/crash.txt || true
exit 0

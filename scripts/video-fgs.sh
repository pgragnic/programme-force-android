#!/bin/bash
# Vidéo pour la déclaration Google Play « Foreground service permissions » (special use : minuteur de séance).
# Utilisé par .github/workflows/video-fgs.yml. Trois extraits légendés, assemblés en out/uprep-fgs.mp4.
set -x
P=fr.pgragnic.force
mkdir -p out
adb install -r app/build/outputs/apk/debug/*.apk
adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true
adb shell settings put system screen_off_timeout 600000
adb shell wm size 720x1520; adb shell wm density 320; sleep 3
sz() { adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1; }
ui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml out/ui.xml >/dev/null; }
# touche le premier élément dont le texte vaut $1
tapt() { ui; b=$(python3 -c "import re,sys;x=open('out/ui.xml',encoding='utf-8').read();m=re.search(r'text=\"'+sys.argv[1]+r'\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]',x);print((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2) if m else print('')" "$1"); [ -n "$b" ] && adb shell input tap $b; }
# referme les messages « … isn't responding » de l'émulateur
anr() { for k in 1 2 3; do ui; grep -q "isn&apos;t responding\|isn't responding" out/ui.xml || return 0; tapt "Wait"; sleep 3; done; }
# touche le premier élément dont la description vaut $1 (icônes sans texte)
tapd() { ui; b=$(python3 -c "import re,sys;x=open('out/ui.xml',encoding='utf-8').read();m=re.search(r'content-desc=\"'+sys.argv[1]+r'\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]',x);print((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2) if m else print('')" "$1"); [ -n "$b" ] && adb shell input tap $b; }
rec() { adb shell screenrecord --time-limit $2 --bit-rate 8000000 /sdcard/$1.mp4 & }
pull() { wait; adb pull /sdcard/$1.mp4 out/$1.mp4 >/dev/null; adb shell rm /sdcard/$1.mp4; }

# 1) l'utilisateur lance une séance (« Let's go ») : minuteur, consignes, notification
adb shell settings put global hide_error_dialogs 1 || true
sleep 40; anr
adb shell locksettings set-disabled false || true
adb shell settings put secure lock_screen_show_notifications 1
adb shell settings put secure lock_screen_show_silent_notifications 1
adb shell settings put secure lock_screen_allow_private_notifications 1
adb shell am force-stop $P; sleep 2
adb shell am start -W -n $P/.MainActivity --es lang en --es theme light --ez seed true --ei tab 0 >/dev/null; sleep 9; anr
rec a 16; sleep 2
for k in 1 2 3 4; do anr; tapt "Let's go"; sleep 3; tapt "Start anyway"; sleep 2; ui; grep -q "READY?\|Ends in" out/ui.xml && break; adb shell am start -n $P/.MainActivity >/dev/null; sleep 3; done
sleep 7; pull a
adb exec-out screencap -p > out/s1.png

# 2) écran verrouillé : la séance continue (service au premier plan), boutons sur l'écran de verrouillage
adb shell input keyevent 223; sleep 3
rec b 26; sleep 1
adb shell input keyevent 224; sleep 5
adb exec-out screencap -p > out/s2.png
anr; tapd "Expand"; sleep 3   # déplie la notification de séance (la première)
adb exec-out screencap -p > out/s3.png
tapt "Pause"; sleep 5
tapt "Resume"; sleep 5
tapt "Next"; sleep 5; pull b
adb exec-out screencap -p > out/s4.png

# 3) retour dans l'appli, fin de séance : le service s'arrête, la notification disparaît
adb shell wm dismiss-keyguard; sleep 3; anr
rec c 16; sleep 3
adb shell input keyevent 4; sleep 4
adb shell cmd statusbar expand-notifications; sleep 6; adb shell cmd statusbar collapse; sleep 2; pull c
adb exec-out screencap -p > out/s5.png

adb shell wm size reset; adb shell wm density reset

# montage : légendes en anglais (pour l'équipe de revue de Google), 30 i/s
F=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf
cap() { in=$1; out=$2; t1=$3; t2=$4
  ffmpeg -y -loglevel error -i out/$in.mp4 -vf "fps=30,scale=720:1520,drawbox=y=0:w=iw:h=170:color=black@0.72:t=fill,drawtext=fontfile=$F:text='$t1':x=(w-tw)/2:y=40:fontsize=30:fontcolor=white,drawtext=fontfile=$F:text='$t2':x=(w-tw)/2:y=100:fontsize=24:fontcolor=white" -an -c:v libx264 -pix_fmt yuv420p out/$out.mp4; }
cap a 1 "1. The user starts a workout" "Timer, voice cues and beeps guide each rep"
cap b 2 "2. Screen locked - workout keeps running" "Foreground service keeps timer and voice going"
cap c 3 "3. Workout ended by the user" "The service stops, the notification is removed"
printf "file '1.mp4'\nfile '2.mp4'\nfile '3.mp4'\n" > out/list.txt
(cd out && ffmpeg -y -loglevel error -f concat -safe 0 -i list.txt -c copy uprep-fgs.mp4)
ls -la out

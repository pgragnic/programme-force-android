#!/bin/bash
# Captures d'écran sur l'émulateur (utilisé par .github/workflows/screens.yml)
POSES="$1"
mkdir -p shots
adb install -r app/build/outputs/apk/debug/*.apk
adb shell pm grant fr.pgragnic.force android.permission.POST_NOTIFICATIONS || true
adb shell cmd uimode night yes || true
shot() { n=$1; shift; adb shell am force-stop fr.pgragnic.force; sleep 2; adb shell am start -W -n fr.pgragnic.force/.MainActivity --ez noauth true "$@" >> shots/start.txt; sleep 9; adb exec-out screencap -p > shots/$n.png; }
sw() { adb shell input swipe 160 500 160 150 500; sleep 3; adb exec-out screencap -p > shots/$1.png; }
QUICK=0; case " $POSES " in *" quick "*) QUICK=1;; esac
if [ $QUICK = 0 ]; then
for t in 0 1 2 3; do shot fr-tab$t --es lang fr --ez seed true --ei tab $t; done
shot fr-play5 --es lang fr --ez seed true --ei play 5
shot en-setup --es lang en --es gear new; sw en-setup-b
for t in 0 1 2 3; do shot en-tab$t --es lang en --ez seed true --ei tab $t; sw en-tab$t-b; sw en-tab$t-c; done
shot en-kb0 --es lang en --es gear kb --ei tab 0; sw en-kb0-b
shot en-play5 --es lang en --ez seed true --ei play 5
adb shell cmd statusbar expand-notifications; sleep 4; adb exec-out screencap -p > shots/en-notif.png; adb shell cmd statusbar collapse
shot en-play6 --es lang en --ez seed true --ei play 6
fi
# extraits pour un montage (onglets, mouvements) : mot-clé « clips »
rec() { n=$1; secs=$2; act=$3; shift 3; adb shell am force-stop fr.pgragnic.force; sleep 2; adb shell am start -W -n fr.pgragnic.force/.MainActivity "$@" >> shots/start.txt; sleep 8
  adb shell screenrecord --time-limit $secs --bit-rate 12000000 /sdcard/$n.mp4 & sleep 1
  case $act in scroll) sleep 2; adb shell input swipe 360 1150 360 450 1600; sleep 2; adb shell input swipe 360 1150 360 450 1600;; esac
  wait; adb pull /sdcard/$n.mp4 shots/$n.mp4 >/dev/null; adb shell rm /sdcard/$n.mp4; }
case " $POSES " in *" clips "*)
  adb shell wm size 720x1520; adb shell wm density 320; sleep 3
  for L in fr en; do
    rec v-$L-play 14 none --es lang $L --ez seed true --ei play 6
    rec v-$L-seance 7 scroll --es lang $L --ez seed true --ei tab 0
    rec v-$L-corps 5 none --es lang $L --ez seed true --ei tab 1
    rec v-$L-suivi 7 scroll --es lang $L --ez seed true --ei tab 2
    rec v-$L-profil 5 none --es lang $L --es gear kb --ei setupstep 0 --ez opensetup true
  done
  for d in kbswing pushup lunge press; do rec v-demo-$d 5 none --es lang fr --es demo $d --ez clean true; done
  adb shell wm size reset; adb shell wm density reset;;
esac
case " $POSES " in *" muscle "*) shot fr-muscle --es lang fr --ez seed true --ei tab 1 --es muscle Quadriceps; sw fr-muscle-b; sw fr-muscle-c;; esac
# nouvelle ergonomie : 4 onglets, sous-onglets de Progrès, sous-pages de Réglages, lecteur
case " $POSES " in *" ergo "*)
  for t in 0 1 2 3; do shot ergo-tab$t --es lang fr --ez seed true --ei tab $t; sw ergo-tab$t-b; done
  for s in 1 2; do shot ergo-prog$s --es lang fr --ez seed true --ei tab 2 --es sub $s; done
  for s in prog rappels compte affichage apropos; do shot ergo-reg-$s --es lang fr --ez seed true --ei tab 3 --es sub $s; done
  shot ergo-en-tab0 --es lang en --ez seed true --ei tab 0
  shot ergo-play5 --es lang fr --ez seed true --ei play 5; shot ergo-play7 --es lang fr --ez seed true --ei play 7;;
esac
tapt() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml shots/ui.xml >/dev/null
  b=$(python3 -c "import re,sys;x=open('shots/ui.xml',encoding='utf-8').read();m=re.search(r'text=\"'+sys.argv[1]+r'\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]',x);print((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2) if m else print('')" "$1"); [ -n "$b" ] && adb shell input tap $b; sleep 3; }
cap() { adb exec-out screencap -p > shots/$1.png; }
# touche un élément par sa description (icônes sans texte, ex. « Aide »)
tapd() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml shots/ui.xml >/dev/null
  b=$(python3 -c "import re,sys;x=open('shots/ui.xml',encoding='utf-8').read();m=re.search(r'content-desc=\"'+sys.argv[1]+r'\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]',x);print((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2) if m else print('')" "$1"); [ -n "$b" ] && adb shell input tap $b; sleep 3; }
# coach : point avant séance (sans compte sur le téléphone virtuel : la dernière réponse doit demander un compte)
case " $POSES " in *" coach "*)
  # accueil au premier lancement (données effacées), puis « Continuer sans compte », puis coach réservé aux comptes
  adb shell pm clear fr.pgragnic.force; adb shell pm grant fr.pgragnic.force android.permission.POST_NOTIFICATIONS || true
  adb shell am start -W -n fr.pgragnic.force/.MainActivity --es lang fr --es theme light >> shots/start.txt; sleep 10; tapt "J'ai compris"; sleep 2; cap welcome
  tapt "Continuer sans compte"; sleep 2; cap welcome-sans; adb shell input keyevent 4; sleep 1
  shot welcome-en --es lang en --es theme light --ez noauth false; tapt "I understand"; sleep 2; cap welcome-en
  shot locked-tab --es lang fr --es theme light --ez seed true --ez locked true --ei tab 9
  shot locked-seance --es lang fr --es theme light --ez seed true --ez locked true --ei tab 0
  shot coach-0 --es lang fr --es theme light --ez seed true --ez tired true --ei tab 0
  tapt "Faire le point avec le coach"; cap coach-1; tapt "J'accepte"; cap coach-2
  tapt "Un peu fatigué"; tapt "30 min"; cap coach-3; tapt "Genou"; sleep 4; cap coach-4
  shot coach-libre --es lang fr --es theme light --ez seed true --ei tab 0; tapd "Parler au coach"; cap coach-libre
  sz=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${sz%x*}; H=${sz#*x}
  shot coach-semaine --es lang fr --es theme light --ez seed true --ei tab 2 --es sub 2
  for i in 1 2 3 4; do adb shell input swipe $((W/2)) $((H*80/100)) $((W/2)) $((H*30/100)) 400; sleep 1.5; cap coach-semaine-$i; done
  shot coach-plie --es lang fr --es theme light --ez seed true --ei tab 2 --es sub 2; tapt "Bilan de la semaine"; sleep 1; tapt "Le mot du coach"; sleep 1; cap coach-plie
  shot ctab --es lang fr --es theme light --ez seed true --ei tab 9; sz=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${sz%x*}; H=${sz#*x}
  for i in 1 2; do adb shell input swipe $((W/2)) $((H*80/100)) $((W/2)) $((H*30/100)) 400; sleep 1.5; cap ctab-$i; done
  shot ctab-sait --es lang fr --es theme light --ez seed true --ei tab 3 --es sub coach
  shot reg-main --es lang fr --es theme light --ez seed true --ei tab 3
  shot reg-hc --es lang fr --es theme light --ez seed true --ei tab 3 --es sub hc
  shot reg-apropos --es lang fr --es theme light --ez seed true --ei tab 3 --es sub apropos; adb shell input swipe 540 1900 540 300 400; sleep 1; adb shell input swipe 540 1900 540 300 400; sleep 1.5; cap reg-apropos-bas
  tapt "+ Ajouter"; sleep 1; cap ctab-objectif; adb shell input keyevent 4; sleep 1
  shot ctab-seance --es lang fr --es theme light --ez seed true --ei tab 0;;
esac
# fiche Play Store : écrans en clair, français et anglais (à lancer avec un émulateur haute définition, profil pixel_6)
case " $POSES " in *" store "*)
  for L in fr en; do b="--es lang $L --es theme light --ez seed true --ez tired true"
    shot store-$L-play $b --ei play 5; sleep 6; cap store-$L-play-b
    shot store-$L-seance $b --ei tab 0; shot store-$L-corps $b --ei tab 1; shot store-$L-progres $b --ei tab 2
    for st in 0 1 2 3 4; do shot store-$L-setup$st --es lang $L --es theme light --es gear kb --ei setupstep $st --ez opensetup true; done
  done;;
esac
# écran Corps, échelle continue (option A) : muscles en récupération, toucher une cuisse puis une épaule sur le personnage
case " $POSES " in *" reco "*)
  shot reco-a --es lang fr --ez seed true --ez tired true --ez over true --ei tab 1
  sz=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${sz%x*}; H=${sz#*x}
  y=$((H*30/100)); adb shell input motionevent DOWN $((W*10/100)) $y; for x in 20 30 40 50 60 70 80 90; do adb shell input motionevent MOVE $((W*x/100)) $y; done; adb shell input motionevent UP $((W*90/100)) $y; sleep 1.5; cap reco-tourne1
  adb shell input motionevent DOWN $((W*10/100)) $y; for x in 20 30 40 50 60 70 80 90; do adb shell input motionevent MOVE $((W*x/100)) $y; done; adb shell input motionevent UP $((W*90/100)) $y; sleep 1.5; cap reco-tourne
  adb shell input tap $((W*72/100)) $((H*40/100)); sleep 2
    adb shell input tap $((W*44/100)) $((H*48/100)); sleep 1.2; cap reco-cuisse
  adb shell input tap $((W*36/100)) $((H*24/100)); sleep 1.2; cap reco-epaule
  adb shell input swipe $((W/2)) $((H*80/100)) $((W/2)) $((H*30/100)) 400; sleep 2; cap reco-liste
  shot reco-en --es lang en --ez seed true --ez tired true --ei tab 1
  shot reco-seance --es lang fr --ez seed true --ez tired true --ez over true --ei tab 0;;
esac
# marche lestée : pas faits avec le sac
case " $POSES " in *" m3 "*)
  sz=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${sz%x*}; H=${sz#*x}
  b="--es lang fr --ez seed true --ez tired true --ez over true"
  shot m3-seance $b --ei tab 0; shot m3-seance-clair $b --es theme light --ei tab 0
  shot m3-corps $b --ei tab 1; adb shell input swipe $((W/2)) $((H*80/100)) $((W/2)) $((H*35/100)) 400; sleep 2; cap m3-corps-liste
  shot m3-corps-clair $b --es theme light --ei tab 1; tapt "Dos"; sleep 2; cap m3-corps-clair-dos
  shot m3-progres $b --ei tab 2; shot m3-reglages $b --ei tab 3; shot m3-reglages-clair $b --es theme light --ei tab 3
  shot m3-affichage $b --ei tab 3 --es sub affichage
  shot m3-dyn $b --ez dyn true --ei tab 0; shot m3-dyn-clair $b --ez dyn true --es theme light --ei tab 0
  shot m3-lecteur --es demo squat --ef k 0.5
  shot m3-play $b --ei play 5; sleep 6; cap m3-play-b
  shot m3-play-clair $b --es theme light --ei play 5; sleep 6; cap m3-play-clair-b
  shot m3-corps-aide $b --ei tab 1; tapd "Aide"; cap m3-corps-aide; adb shell input swipe $((W/2)) $((H*80/100)) $((W/2)) $((H*30/100)) 400; sleep 2; cap m3-corps-aide-b;;
esac
case " $POSES " in *" lest "*)
  shot lest-a --es lang fr --ez seed true --ei tab 2 --es sub 0
  sz=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${sz%x*}; H=${sz#*x}
  adb shell input swipe $((W/2)) $((H*85/100)) $((W/2)) $((H*20/100)) 600; sleep 1; cap lest-s1
  tapt "Marche lestée"; cap lest-b; adb shell input swipe $((W/2)) $((H*70/100)) $((W/2)) $((H*45/100)) 400; sleep 1; cap lest-c;;
esac
# lecteur : barre du haut qui avance pendant l'exercice
case " $POSES " in *" barre "*)
  shot barre-a --es lang fr --ez seed true --ei play 5; sleep 8; cap barre-b; sleep 8; cap barre-c;;
esac
# alerte « muscles pas encore récupérés » : touche le bouton Démarrer de l'écran Séance
# 6 séances par semaine : séances E et F
case " $POSES " in *" six "*)
  shot six-tab0 --es lang fr --ez seed true --ei tab 0 --ei perweek 6
  tapt "E"; cap six-e; adb shell input swipe 160 500 160 150 500; sleep 3; cap six-e-b; adb shell input swipe 160 150 160 500 500; sleep 2
  tapt "F"; cap six-f; adb shell input swipe 160 500 160 150 500; sleep 3; cap six-f-b
  shot six-5 --es lang fr --ez seed true --ei tab 0 --ei perweek 5
  shot six-4 --es lang fr --ez seed true --ei tab 0 --ei perweek 4;;
esac
# aide contextuelle : visite guidée, « ? » de l'écran, fiche d'un exercice
case " $POSES " in *" aide "*)
  shot aide-tour1 --es lang fr --ez seed true --ei tab 0 --ez tour true
  for n in 2 3 4; do tapt "Suivant"; cap aide-tour$n; done
  tapt "Terminer"; tapt '[?]'; sleep 2; cap aide-help
  adb shell input keyevent 4; sleep 2
  adb shell input swipe 160 500 160 150 500; sleep 3; tapt "Floor press"; sleep 4; cap aide-fiche
  adb shell input swipe 160 560 160 200 500; sleep 3; cap aide-fiche-b
  tapt "Remplacer"; sleep 2; adb shell input swipe 160 560 160 300 500; sleep 2; cap aide-fiche-c
  adb shell input keyevent 4; sleep 2
  shot aide-corps --es lang fr --ez seed true --ei tab 1; tapt '[?]'; sleep 2; cap aide-corps-help
  shot aide-en --es lang en --ez seed true --ei tab 0 --ez tour true;;
esac
case " $POSES " in *" warn "*)
  shot warn-a --es lang fr --ez seed true --ez tired true --ei tab 0; sw warn-b; tapt "Démarrer"; adb exec-out screencap -p > shots/warn-c.png;;
esac
case " $POSES " in *" tabs "*) shot fr-tabs --es lang fr --es gear kb --ei setupstep 3 --ez opensetup true;; esac
case " $POSES " in *" setup4 "*) shot fr-setup4 --es lang fr --es gear new --ei setupstep 3; sw fr-setup4-b; sw fr-setup4-c;; esac
for p in $POSES; do [ "$p" = quick ] || [ "$p" = setup4 ] || [ "$p" = tabs ] || [ "$p" = video ] || [ "$p" = clips ] || [ "$p" = muscle ] || [ "$p" = ergo ] || [ "$p" = aide ] || [ "$p" = six ] || [ "$p" = warn ] || [ "$p" = reco ] || [ "$p" = lest ] || [ "$p" = barre ] || [ "$p" = m3 ] && continue; id=${p%@*}; k=0.5; [ "$id" != "$p" ] && k=${p#*@}; shot pose-$id-$k --es demo $id --ef k $k; done
# Vidéo de démonstration (fiche Google Play) : 30 s de séance guidée, en français et en anglais, en meilleure définition
case " $POSES " in *" video "*)
  adb shell wm size 720x1520; adb shell wm density 320; sleep 3
  for l in fr en; do
    adb shell am force-stop fr.pgragnic.force; sleep 2
    adb shell am start -W -n fr.pgragnic.force/.MainActivity --es lang $l --ez seed true --ei play 5 >> shots/start.txt; sleep 6
    adb shell screenrecord --time-limit 30 --bit-rate 6000000 /sdcard/demo-$l.mp4
    adb pull /sdcard/demo-$l.mp4 shots/demo-$l.mp4
  done
  adb shell wm size reset; adb shell wm density reset;;
esac
adb logcat -d -b crash > shots/crash.txt || true
exit 0

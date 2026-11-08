# Vidéo de présentation UpRep pour la fiche Google Play (1920 × 1080, 30 i/s), en français et en anglais.
# Entrées : enregistrements du téléphone virtuel (scripts/shots.sh, mots-clés « video » et « clips »).
# Usage : python3 promo.py <dossier des enregistrements> <dossier de sortie> fr|en
import sys, os, subprocess
from PIL import Image, ImageDraw, ImageFont, ImageFilter

SRC, OUT, L = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(OUT, exist_ok=True)
W, H = 1920, 1080
BG = (14, 19, 23); INK = (231, 237, 239); MUTED = (148, 161, 171); GREEN = (46, 192, 137)
F = '/usr/share/fonts/opentype/inter/'
def font(n, s): return ImageFont.truetype(F + n, s)

T = {
 'fr': dict(
  intro=('UpRep', 'Ta remise en forme à la maison,\nguidée de la première à la dernière répétition'),
  play=('Une séance guidée', 'Minuteur, tempo, voix\net personnage 3D qui montre le geste'),
  moves=('Chaque mouvement en 3D', 'Plus de 75 exercices,\nmuscles travaillés en rouge'),
  seance=('Ton programme sur 8 semaines', 'Adapté à ton matériel : haltères,\nkettlebell, élastiques, poids du corps'),
  corps=('La récupération de chaque muscle', 'Pour savoir quoi travailler aujourd\'hui'),
  suivi=('Tes progrès, semaine après semaine', 'Bilan, poids, charges et volume'),
  profil=('Fait pour toi', 'Ton matériel, tes charges,\nta santé, ton profil'),
  outro=('UpRep', 'Gratuit, sans publicité')),
 'en': dict(
  intro=('UpRep', 'Get fit at home,\nguided from the first rep to the last'),
  play=('A fully guided workout', 'Timer, tempo, voice coaching\nand a 3D coach showing every move'),
  moves=('Every move in 3D', '75+ exercises,\nworking muscles highlighted'),
  seance=('Your 8-week program', 'Built around your equipment: dumbbells,\nkettlebell, bands, bodyweight'),
  corps=('Recovery for every muscle', 'Know what to train today'),
  suivi=('Your progress, week by week', 'Summary, weight, loads and volume'),
  profil=('Made for you', 'Your equipment, your loads,\nyour health, your profile'),
  outro=('UpRep', 'Free, no ads'))}[L]

def bg(title, sub, center=False):
    im = Image.new('RGB', (W, H), BG); d = ImageDraw.Draw(im)
    # halo vert discret
    glow = Image.new('RGB', (W, H), BG); gd = ImageDraw.Draw(glow)
    gd.ellipse((1100, 80, 1900, 1000), fill=(20, 48, 40)); glow = glow.filter(ImageFilter.GaussianBlur(160)); im = Image.blend(im, glow, .9); d = ImageDraw.Draw(im)
    if center:
        f1 = font('Inter-Black.otf', 190); f2 = font('Inter-Medium.otf', 46)
        w = d.textlength(title, font=f1); d.text(((W - w) / 2, 330), title, font=f1, fill=INK)
        d.rectangle(((W - 120) / 2, 560, (W + 120) / 2, 568), fill=GREEN)
        y = 610
        for line in sub.split('\n'):
            w = d.textlength(line, font=f2); d.text(((W - w) / 2, y), line, font=f2, fill=MUTED); y += 64
    else:
        f0 = font('Inter-Bold.otf', 34); f1 = font('Inter-ExtraBold.otf', 66); f2 = font('Inter-Medium.otf', 42)
        d.text((150, 300), 'UpRep', font=f0, fill=GREEN)
        y = 360
        for line in wrap(title, f1, 940, d): d.text((150, y), line, font=f1, fill=INK); y += 80
        y += 24
        for line in sub.split('\n'): d.text((150, y), line, font=f2, fill=MUTED); y += 60
    return im

def wrap(t, f, maxw, d):
    out, cur = [], ''
    for w in t.split():
        if d.textlength((cur + ' ' + w).strip(), font=f) > maxw: out.append(cur); cur = w
        else: cur = (cur + ' ' + w).strip()
    return out + [cur]

PH_H = 960; PW = 456; PH_X = 1200
def phone_mask(w, h, r=44):
    m = Image.new('L', (w, h), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), r, fill=255); return m

ENC = ['-c:v', 'libx264', '-crf', '18', '-preset', 'veryfast', '-pix_fmt', 'yuv420p', '-r', '30']
def run(args):
    tmp = args[-1] + '.part.mp4'; a = args[:-1] + [tmp]
    subprocess.run(['ffmpeg', '-v', 'error', '-y'] + a, check=True); os.replace(tmp, args[-1])

def still(name, title, sub, dur):
    p = f'{OUT}/{name}.png'; bg(title, sub, center=True).save(p)
    run(['-framerate', '30', '-i', p, '-vf', f'format=yuv420p,loop=-1:1,trim=duration={dur},fade=in:0:9,fade=out:st={dur-.3:.2f}:d=0.3', *ENC, f'{OUT}/{name}.mp4'])

def scene(name, title, sub, clips, dur, ss=0.0):
    """clips : liste de fichiers enregistrés ; s'il y en a plusieurs, ils se suivent dans le cadre du téléphone."""
    p = f'{OUT}/{name}.png'; bg(title, sub).save(p)
    # recadrage : on retire la barre d'état (en haut) ; le téléphone est mis à 960 px de haut avec coins arrondis
    each = dur / len(clips)
    ins = ['-framerate', '30', '-loop', '1', '-i', p]
    fc = []
    for i, c in enumerate(clips):
        ins += ['-ss', str(ss), '-i', c]
        fc.append(f'[{i+1}:v]setpts=PTS-STARTPTS,tpad=stop_mode=clone:stop_duration={each:.2f},fps=30,trim=duration={each:.3f},crop=iw:ih-ih*0.035:0:ih*0.035,scale={PW}:{PH_H}:force_original_aspect_ratio=increase,crop={PW}:{PH_H},setsar=1,format=rgba[c{i}]')
    if len(clips) > 1: fc.append(''.join(f'[c{i}]' for i in range(len(clips))) + f'concat=n={len(clips)}:v=1:a=0[ph]')
    else: fc.append('[c0]null[ph]')
    m = f'{OUT}/mask.png'; phone_mask(PW, PH_H).save(m); ins += ['-i', m]
    fc.append(f'[ph][{len(clips)+1}:v]alphamerge[phm]')
    fc.append(f'[0:v][phm]overlay={PH_X}:{(H-PH_H)//2}:shortest=1,trim=duration={dur},fade=in:0:9,fade=out:st={dur-.3:.2f}:d=0.3,format=yuv420p[o]')
    run(ins + ['-filter_complex', ';'.join(fc), '-map', '[o]', *ENC, '-t', str(dur), f'{OUT}/{name}.mp4'])

S = lambda n: f'{SRC}/{n}'
still('s0', *T['intro'], 3.2)
scene('s1', *T['play'], [S(f'demo-{L}.mp4')], 9.0, ss=4)
scene('s2', *T['moves'], [S('v-demo-kbswing.mp4'), S('v-demo-pushup.mp4'), S('v-demo-lunge.mp4'), S('v-demo-press.mp4')], 7.2, ss=1)
scene('s3', *T['seance'], [S(f'v-{L}-seance.mp4')], 5.5, ss=.5)
scene('s4', *T['corps'], [S(f'v-{L}-corps.mp4')], 4.0, ss=.5)
scene('s5', *T['suivi'], [S(f'v-{L}-suivi.mp4')], 5.5, ss=.5)
scene('s6', *T['profil'], [S(f'v-{L}-profil.mp4')], 4.0, ss=.5)
still('s7', *T['outro'], 3.5)
with open(f'{OUT}/list.txt', 'w') as f:
    for i in range(8): f.write(f"file 's{i}.mp4'\n")
fin = f'{OUT}/UpRep-presentation-{L}.mp4'
# piste son silencieuse (YouTube et la Play Console acceptent mieux une vidéo avec piste audio)
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', f'{OUT}/list.txt', '-f', 'lavfi', '-i', 'anullsrc=r=48000:cl=stereo',
                '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-shortest', fin + '.part.mp4'], check=True)
os.replace(fin + '.part.mp4', fin); print(fin)

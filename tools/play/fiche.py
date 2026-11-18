"""Captures légendées de la fiche Google Play (1080 × 1920), FR et EN.
Usage : python3 tools/play/fiche.py <dossier des captures brutes (branche captures-<run>, pose « store »)> distribution/play
Mise à jour du 18 novembre 2026 (Philippe, 21 h 32 : « Mise à jour de la fiche playstore ») : 8 captures, avec le coach IA."""
import sys, os
from PIL import Image, ImageDraw, ImageFont

SRC, OUT = sys.argv[1], sys.argv[2]
FONT = os.path.join(os.path.dirname(__file__), '../../app/src/main/res/font/inter_%d.ttf')
BG, INK, BLUE, MUTED, FRAME = (247, 246, 242), (17, 17, 17), (30, 136, 255), (110, 110, 110), (16, 16, 16)
W, H = 1080, 1920

# (capture brute, titre avant le mot bleu, mot bleu, sous-titre) — le mot bleu peut aller à la ligne
SHOTS = {
 'fr': [('play-b', 'Un coach qui te montre ', 'chaque mouvement', 'Le personnage 3D fait le geste, une voix te guide.'),
        ('chat', 'Un coach IA qui ', 'adapte ta séance', 'Une gêne, peu de temps, des vacances sans matériel : il ajuste et explique.'),
        ('ctab', 'Chaque changement ', 'expliqué', 'Ce que le coach a changé et pourquoi, annulable en un geste.'),
        ('setup1', 'Avec le matériel ', 'que tu as', 'Haltères, kettlebell, élastiques ou poids du corps.'),
        ('setup3', 'Adaptée à ', 'tes articulations', 'Genou, dos, épaule : les exercices sont remplacés.'),
        ('seance', 'Ta séance du jour, ', 'prête', 'Un geste pour démarrer, la durée et les charges calculées.'),
        ('corps', 'Sache quels muscles ', 'ménager', 'La récupération de chaque muscle, en un coup d\'œil.'),
        ('semaine', 'Des muscles ', 'équilibrés', 'Le programme rattrape la semaine suivante ce qui a pris du retard.')],
 'en': [('play-b', 'A coach that shows you ', 'every move', 'The 3D figure does the move, a voice guides you.'),
        ('chat', 'An AI coach that ', 'adapts your workout', 'Discomfort, little time, a trip without equipment: it adjusts and explains.'),
        ('ctab', 'Every change ', 'explained', 'What the coach changed and why, undone in one tap.'),
        ('setup1', 'With the equipment ', 'you have', 'Dumbbells, kettlebell, bands or bodyweight.'),
        ('setup3', 'Adapted to ', 'your joints', 'Knee, back, shoulder: exercises are replaced.'),
        ('seance', "Today's workout, ", 'ready', 'One tap to start, duration and loads worked out.'),
        ('corps', 'Know which muscles ', 'to spare', 'Each muscle\'s recovery at a glance.'),
        ('semaine', 'Balanced ', 'muscles', 'The program catches up next week on what fell behind.')],
}

def font(w, s): return ImageFont.truetype(FONT % w, s)

def wrap(d, words, f, maxw):
    """mots (texte, couleur) → lignes de mots, coupées à maxw"""
    lines, cur, cw = [], [], 0
    for txt, col in words:
        for i, w in enumerate(txt.split(' ')):
            if not w: continue
            tok = (w, col); ww = d.textlength(w + ' ', font=f)
            if cur and cw + d.textlength(w, font=f) > maxw: lines.append(cur); cur, cw = [], 0
            cur.append(tok); cw += ww
    if cur: lines.append(cur)
    return lines

def make(lang, i, raw, a, b, sub):
    im = Image.new('RGB', (W, H), BG); d = ImageDraw.Draw(im)
    ft = font(800, 84); y = 110
    words = [(a, INK), (b, BLUE)]
    for line in wrap(d, words, ft, 960):
        txt = ' '.join(w for w, _ in line); x = (W - d.textlength(txt, font=ft)) / 2
        for w, col in line: d.text((x, y), w, font=ft, fill=col); x += d.textlength(w + ' ', font=ft)
        y += 100
    fs = font(400, 38); y += 30
    for line in wrap(d, [(sub, MUTED)], fs, 900):
        txt = ' '.join(w for w, _ in line); d.text(((W - d.textlength(txt, font=fs)) / 2, y), txt, font=fs, fill=MUTED); y += 48
    top = max(y + 50, 466)
    # téléphone : cadre noir arrondi, capture réduite, coupée en bas
    fx, fw, bd = 136, 808, 14
    sw = fw - 2 * bd
    shot = Image.open(raw).convert('RGB'); shot = shot.resize((sw, int(shot.height * sw / shot.width)))
    ph = Image.new('RGB', (fw, H - top + 200), FRAME)
    mask = Image.new('L', ph.size, 0); ImageDraw.Draw(mask).rounded_rectangle((0, 0, fw - 1, ph.height - 1), 110, fill=255)
    scr = Image.new('L', (sw, ph.height - bd), 0); ImageDraw.Draw(scr).rounded_rectangle((0, 0, sw - 1, ph.height - bd + 200), 96, fill=255)
    ph.paste(shot.crop((0, 0, sw, ph.height - bd)), (bd, bd), scr)
    im.paste(ph, (fx, top), mask)
    im.save(os.path.join(OUT, f'uprep-play-{lang}-{i}.png'), optimize=True)

for lang, L in SHOTS.items():
    for i, (k, a, b, sub) in enumerate(L, 1):
        make(lang, i, os.path.join(SRC, f'store-{lang}-{k}.png'), a, b, sub)
print('ok')

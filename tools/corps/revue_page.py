# Assemble la planche de revue : images de revue.py + remarques du contrôle automatique (audit2.py, comparé à audit_ref.json).
# Les poses avec des remarques nouvelles passent en tête, puis celles avec des remarques déjà acceptées, puis les autres.
# Usage : python3 revue_page.py <dossiers d'images…> <audit.json> <sortie>
import sys, json, os, re, shutil
*dirs, audit_fn, out = sys.argv[1:]
os.makedirs(f'{out}/img', exist_ok=True)
here = os.path.dirname(os.path.abspath(__file__))
ref = json.load(open(f'{here}/audit_ref.json')); cur = json.load(open(audit_fn))
poses = []
for d in dirs:
    poses += json.load(open(f'{d}/poses.json'))
    for f in os.listdir(f'{d}/img'): shutil.copy(f'{d}/img/{f}', f'{out}/img/{f}')
def side(s):
    fem = s.split(' ')[0] in ('cheville', 'main')
    return s.replace(' R ', ' droite ' if fem else ' droit ').replace(' L ', ' gauche ')
def fmt(key, vals):
    m = max(v[1] for v in vals); u = re.search(r'\((cm|°|points)\)', key); unit = u.group(1) if u else ''
    k = side(re.sub(r'\s*\((cm|°|points)\)', '', key)); k = k[0].upper() + k[1:]
    if 'de (°)' in key or k.endswith(' de'): k = k[:-3] if k.endswith(' de') else k
    v = f'{m:g}'.replace('.', ',')
    return f'{k} : jusqu’à {v} {unit}'.replace(' °', '°').strip()
for p in poses:
    p['names'] = list(dict.fromkeys(p['names']))
    iss = cur.get(p['id'], {}); r = ref.get(p['id'], {})
    p['auto'] = [{'t': fmt(k, v), 'new': k not in r or max(x[1] for x in v) > max(x[1] for x in r[k]) + 2} for k, v in iss.items()]
    p['score'] = sum(3 if a['new'] else 1 for a in p['auto'])
# revue du 15 novembre (« Peux-tu déjà revoir tout ce que tu as déjà remarqué dans les contrôles automatiques ? »)
FIX = 'Corrigé : '; KEEP = 'Gardé : '
WRIST = 'poignet vers 90 à 100°, normal main à plat'
NOTES = {
 'wallpush': FIX + 'les mains sont posées sur le mur et ne bougent plus (elles en étaient à 6 à 10 cm).',
 'calf1': FIX + 'bras d’appui presque tendu, main fixe à plat sur le mur (elle y entrait). Le poignet reste signalé : c’est la torsion de l’avant-bras que le contrôle compte, la main est à plat.',
 'sldl': FIX + 'le pied d’appui ne glisse plus (6 cm).',
 'fwdlunge': FIX + 'le pied avant fait un vrai pas, levé puis posé (il glissait au sol sur 54 cm).',
 'inclinepress': FIX + 'en bas, haltères au-dessus des épaules (les disques entraient dans les épaules).',
 'crunch': FIX + 'haltère tenu sur la poitrine (il était sous le menton).',
 'kbpress': FIX + 'kettlebell posée sur le dos du poignet (elle recouvrait l’avant-bras et le visage).',
 'kbcurl': FIX + 'kettlebell posée sur le dos du poignet en haut du mouvement.',
 'kbfloor': FIX + 'kettlebell posée sur le dos du poignet : la main ne la traverse plus. Pieds à 2 cm du sol : invisible.',
 'pike': FIX + 'les coudes plient et la tête descend vers les mains (avant, les coudes restaient tendus et les images étaient identiques) ; mains et pieds fixes au sol. Cheville pliée à 33° en bas : à peine au-dessus de la limite.',
 'pushup': FIX + 'corps gainé d’une seule ligne, pointes de pieds au sol à chaque instant (ils flottaient jusqu’à 16 cm). Poignet vers 87° : normal main à plat.',
 'abwheel': FIX + 'la roue reste au sol tout le long (elle montait de 12 cm), genoux au sol, orteils repliés au sol (pieds qui décollaient de 17 cm, cheville tendue). Le contrôle signale les mains « en l’air » : c’est la roue qui touche le sol.',
 'chairdip': FIX + 'paumes posées sur l’assise, doigts accrochés au bord (les mains étaient 5 cm plus bas, contre le bord) ; en haut, bras tendus (il était presque assis) ; pieds fixes.',
 'catcow': FIX + 'quatre pattes refait : bras tendus sous les épaules (ils étaient pliés à 115°), cuisses verticales, dos qui s’arrondit puis se creuse sans excès, chevilles moins tendues. Le poignet reste signalé : torsion de l’avant-bras, main à plat.',
 'birddog': FIX + 'mains à l’aplomb des épaules, cuisses verticales, genou et pointe du pied d’appui posés, pied de la jambe tendue fléchi. Poignet vers 95° : normal main à plat sous un bras vertical.',
 'incline': FIX + 'mains à l’aplomb des épaules en haut (poignet à 102° au lieu de 115°, le reste vient de la main à plat), cheville moins tendue (59° au lieu de 72°), pieds toujours dans le prolongement des jambes.',
 'superman': FIX + 'au repos, mains et pointes de pieds posées (elles flottaient à 6–7 cm), chevilles moins tendues. Le contrôle signale les mains au tout début de la montée : c’est le mouvement.',
 'pronet': FIX + 'pointes de pieds posées (pieds à 7 cm du sol), chevilles moins tendues. Le contrôle signale les mains au début de la montée : c’est le mouvement.',
 'ballfoot': FIX + 'la balle reste fixe au sol et le pied roule dessus (elle suivait le pied et s’y enfonçait).',
 'bridge': KEEP + 'jambe libre 10° au-dessus du sol, choisi le 13 novembre.',
 'bridge2': KEEP + 'pieds à 2 cm du sol au milieu : invisible.',
 'stepup': KEEP + 'cheville pliée en montant sur la chaise : normal.',
 'stquad': KEEP + 'cheville tendue en tenant le pied : normal.',
 'row1': KEEP + 'main libre posée sur le genou, poignet vers 80° : normal.',
 'squat': KEEP + 'fausse alerte : le contrôle comptait deux haltères au lieu d’un tenu à deux mains.',
 'pull': KEEP + 'fausse alerte : le contrôle comptait deux haltères au lieu d’un tenu à deux mains.',
 'ohtri': KEEP + 'fausse alerte : le contrôle comptait deux haltères au lieu d’un tenu à deux mains.',
 'dbugw': KEEP + 'haltère qui frôle la main : négligeable.',
}
import datetime
REV_AT = datetime.datetime.now(datetime.timezone.utc).isoformat()
for p in poses: p['review'] = NOTES.get(p['id'], ''); p['rev_at'] = REV_AT if p['review'].startswith('Corrigé') else ''
poses.sort(key=lambda p: (not p['review'].startswith('Corrigé'), -p['score'], p['names'][0]))
json.dump(poses, open(f'{out}/poses.json', 'w'), ensure_ascii=False)
tpl = open(f'{here}/revue_template.html').read()
open(f'{out}/index.html', 'w').write(tpl.replace('/*POSES*/[]', json.dumps(poses, ensure_ascii=False)))
print(len(poses), 'poses ;', sum(1 for p in poses if p['score']), 'avec remarques')

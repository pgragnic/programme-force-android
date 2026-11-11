# Lot 4 : chaque matériel coché a ses exercices propres dans la semaine (demande de Philippe, 8 novembre 2026)
# « Je viens de rajouter une kettlebell et je ne vois pas de nouveaux exercices » ; « Et je veux que ça devienne vrai pour tous les matériels »
# Usage : python3 tools/lot4.py programme.json
import json, sys, copy
p = sys.argv[1]; d = json.load(open(p))
EX, PO, MAP, EN = d['EX'], d['POSES'], d['MAP'], d['EN']

def ex(id, n, l, tip, cues, eq, lt, tool, mp, stress=(), pose=None, m=None, dr=None, inv=None, back=False, knee=False, f0=None):
    o = {'n': n, 'l': l, 'tip': tip, 'cues': cues, 'eq': eq, 'lt': lt, 'tool': tool, 'stress': list(stress), 'back': back, 'knee': knee}
    if pose: o['pose'] = pose
    if m: o['m'] = m
    if dr is not None: o['dr'] = dr
    if inv: o['inv'] = 1
    if f0: o['f0'] = f0
    EX[id] = o; MAP[id] = mp

ex('kbswing', 'Swing kettlebell', '1 kettlebell à 2 mains', 'Pousse les hanches vers l’avant pour envoyer la kettlebell à hauteur d’épaules, bras tendus',
   ['Dos plat', 'La force vient des hanches', 'Serre les fessiers en haut'], ['kb'], 'kg', 'kb',
   {'Fessiers': 1, 'Ischios': .8, 'Lombaires': .5, 'Abdos': .3, 'Épaules': .2}, ['dos'], pose='kbswing', back=True, dr=2, f0=.2)
ex('kbsumo', 'Squat sumo kettlebell', '1 kettlebell à 2 mains', 'Pieds largement écartés, pointes vers l’extérieur, kettlebell tenue bras tendus entre les jambes',
   ['Genoux dans l’axe des pieds', 'Buste droit', 'Pousse sur les talons'], ['kb'], 'kg', 'kb',
   {'Quadriceps': 1, 'Fessiers': .9, 'Ischios': .3}, ['genou'], pose='kbsumo', knee=True, f0=.2)
ex('kbhalo', 'Halo kettlebell', '1 kettlebell à 2 mains', 'Kettlebell tenue par les cornes, fais-la tourner autour de la tête, un tour dans chaque sens',
   ['Abdos serrés', 'Ne cambre pas', 'Kettlebell près de la tête'], ['kb'], 'kg', 'kb',
   {'Épaules': 1, 'Abdos': .5, 'Triceps': .3, 'Dos': .3}, ['epaule'], pose='kbhalo', m='alt', f0=.05)

# poses (repère 200 × 200, sol à y = 180)
STAND = {'H': [100, 38], 'N': [100, 56], 'P': [100, 104], 'K1': [101, 142], 'A1': [101, 180], 'K2': [99, 142], 'A2': [99, 180]}
sw = copy.deepcopy(PO['kbrdl'])
sw['a'] = dict(STAND, E1=[78, 60], W1=[54, 62], E2=[76, 60], W2=[52, 62])       # en haut : bras tendus devant, hauteur d'épaules
hinge = copy.deepcopy(PO['kbrdl']['b'])
hinge.update({'E1': [92, 106], 'W1': [108, 126], 'E2': [90, 106], 'W2': [106, 126]})  # en bas : kettlebell entre les cuisses
sw['b'] = hinge
sw['kf'] = [{'t': 0.5, 'p': {'H': [76, 56], 'N': [84, 70], 'P': [112, 104], 'K1': [106, 142], 'A1': [102, 180], 'K2': [104, 142], 'A2': [100, 180],
                             'E1': [84, 94], 'W1': [86, 118], 'E2': [82, 94], 'W2': [84, 118]}}]
PO['kbswing'] = sw
su = copy.deepcopy(PO['squat'])
for k in ('a', 'b'): su[k].update({'E1': [su[k]['N'][0] + 2, su[k]['N'][1] + 26], 'W1': [su[k]['N'][0] + 3, su[k]['N'][1] + 50],
                                   'E2': [su[k]['N'][0], su[k]['N'][1] + 26], 'W2': [su[k]['N'][0] + 1, su[k]['N'][1] + 50]})
kp = su['kf'][0]['p']; kp.update({'E1': [kp['N'][0] + 2, kp['N'][1] + 26], 'W1': [kp['N'][0] + 3, kp['N'][1] + 50], 'E2': [kp['N'][0], kp['N'][1] + 26], 'W2': [kp['N'][0] + 1, kp['N'][1] + 50]})
PO['kbsumo'] = su
PO['kbhalo'] = {'cam': 0.9,
    'a': dict(STAND, E1=[116, 50], W1=[112, 30], E2=[114, 50], W2=[110, 30]),
    'b': dict(STAND, E1=[84, 50], W1=[90, 28], E2=[82, 50], W2=[88, 28]),
    'kf': [{'t': 0.5, 'p': {'E1': [112, 40], 'W1': [101, 27], 'E2': [110, 40], 'W2': [99, 27]}}], 'db': ['W1'], '__d': 1}
for k in ('kbswing', 'kbsumo', 'kbhalo'):
    PO[k]['db'] = ['W1']
    if k not in d['TWOHAND']: d['TWOHAND'].append(k)
if 'kbswing' not in d['FLIPX']: d['FLIPX'].append('kbswing')
for k in ('kbswing', 'kbsumo'):
    if k not in d['BIG']: d['BIG'].append(k)
    if k not in d['HEAVY']: d['HEAVY'].append(k)

# SIG : exercices propres à chaque matériel, par cycle (SESS1 / SESS2), séance et emplacement.
# L'appli choisit, sur la semaine, en donnant d'abord un emplacement à chaque matériel coché qui n'en a pas encore.
c1 = {
 'A': {'row1': ['bbrow'], 'floor': ['benchpress'], 'pull': ['towelrow'], 'ohtri': ['dips', 'chairdip'], 'revfly': ['bandpull'],
       'crunch': ['abwheel', 'kneeraise', 'medcrunch']},
 'B': {'squat': ['bosusquat', 'mbsquat'], 'lunge': ['bulgarian', 'stepup'], 'rdl': ['kbswing', 'bbrdl', 'ballcurl'], 'bridge': ['ballcurl', 'mbbridge']},
 'C': {'press': ['bbpress', 'bandpress'], 'row2': ['pullup', 'towelrow', 'bbrow'], 'incline': ['inclinepress', 'benchpress'], 'lateral': ['bandlat'], 'birddog': ['kbhalo', 'abwheel']},
 'D': {'squat': ['kbsumo', 'mbsquat', 'bosusquat'], 'row1': ['bbrow', 'pullup', 'bandrow'], 'floor': ['benchpress'], 'rdl': ['bbrdl', 'kbswing', 'ballcurl'], 'plank': ['mbslam', 'abwheel']},
}
c2 = {
 'A': {'row1': ['bbrow'], 'floor': ['benchpress'], 'pull': ['towelrow'], 'skull': ['dips', 'chairdip'], 'revfly': ['bandpull'],
       'dbugw': ['abwheel', 'kneeraise', 'medcrunch']},
 'B': {'squat': ['bosusquat', 'mbsquat'], 'fwdlunge': ['stepup', 'bulgarian'], 'sldl': ['kbswing', 'bbrdl', 'ballcurl'], 'bridge': ['ballcurl', 'mbbridge']},
 'C': {'press': ['bbpress', 'bandpress'], 'row2': ['pullup', 'towelrow', 'bbrow'], 'pushup': ['inclinepress', 'benchpress'], 'lateral': ['bandlat'], 'birddog': ['kbhalo', 'abwheel']},
 'D': {'squat': ['kbsumo', 'mbsquat', 'bosusquat'], 'row1': ['bbrow', 'pullup', 'bandrow'], 'floor': ['benchpress'], 'sldl': ['bbrdl', 'kbswing', 'ballcurl'], 'plank': ['mbslam', 'abwheel']},
}
d['SIG'] = {'SESS1': c1, 'SESS2': c2}
for s in d['SIG'].values():
    for sl in s.values():
        for v in sl.values():
            for x in v: assert x in EX, x

EN.update({
 'Swing kettlebell': 'Kettlebell swing', 'Squat sumo kettlebell': 'Kettlebell sumo squat', 'Halo kettlebell': 'Kettlebell halo',
 'Pousse les hanches vers l’avant pour envoyer la kettlebell à hauteur d’épaules, bras tendus': 'Drive your hips forward to swing the kettlebell to shoulder height, arms straight',
 'La force vient des hanches': 'The power comes from the hips', 'Serre les fessiers en haut': 'Squeeze your glutes at the top',
 'Pieds largement écartés, pointes vers l’extérieur, kettlebell tenue bras tendus entre les jambes': 'Feet wide, toes out, kettlebell held at arm’s length between your legs',
 'Pousse sur les talons': 'Push through your heels',
 'Kettlebell tenue par les cornes, fais-la tourner autour de la tête, un tour dans chaque sens': 'Hold the kettlebell by the horns and circle it around your head, one lap each way',
 'Kettlebell près de la tête': 'Keep the kettlebell close to your head',
})
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))
print(len(EX), 'exercices')

# --- 8 novembre, 11 h 15 : montée en 1,5 s (Philippe : « Ok pour montée à 1,5 s ») ; swing et slam à leur rythme propre
d = json.load(open(p)); EN = d['EN']
for w in d['WEEKS']: w['tp'][0] = 1.5
old = d['WEEKS'][0]['note']; new = old.replace('montée en 1 s', 'montée en 1,5 s')
d['WEEKS'][0]['note'] = new; EN[new] = EN.get(old, '').replace('1 s up', '1.5 s up')
d['EX']['kbswing']['tp'] = [0.8, 0, 1.2, 0]
d['EX']['mbslam']['tp'] = [0.8, 0.2, 1, 0]
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))
print('tempo', [w['tp'] for w in d['WEEKS']], new, EN[new])

# --- 8 novembre, 17 h 30 : oiseau mal dessiné (Philippe : « L'oiseau est mal représenté ») : drapeau fly à 1 au lieu de true, ignoré par l'appli
d = json.load(open(p)); d['POSES']['revfly']['fly'] = True
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 8 novembre, 17 h 45 : prises et écart des jambes (Philippe : « La position des mains par rapport aux haltères n'est pas correcte »)
d = json.load(open(p))
for k in ('hammer', 'row1', 'row2', 'revfly', 'lateral', 'calf', 'calf1', 'lunge', 'fwdlunge'): d['POSES'][k]['grip'] = 'n'
d['POSES']['kbsumo']['legw'] = [12, 18]
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 8 novembre, 17 h 50 : crunch lesté, haltère posé sur la poitrine (Philippe : « L'haltère n'est pas sur la poitrine »)
d = json.load(open(p)); c = d['POSES']['crunch']
c['a'].update({'E1': [66, 168], 'E2': [64, 168], 'W1': [69, 159], 'W2': [67, 159]})
c['b'].update({'E1': [79, 158], 'E2': [77, 158], 'W1': [85, 150], 'W2': [83, 150]})
c['lat'] = [[12, -12.7], [12, -12.7]]
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 8 novembre, 18 h : étirement dorsal, mains jointes au-dessus de la tête et buste penché sur le côté (revue de tous les mouvements demandée par Philippe)
d = json.load(open(p)); st = d['POSES']['stlat']
st['lat'] = [[-4, -14], [-4, -14]]; st['side'] = 14
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 8 novembre, 18 h 25 : étirement pectoraux, mains jointes dans le dos (Philippe : « La position des bras est bizarre ? »)
d = json.load(open(p)); sp = d['POSES']['stpec']
for k in ('a', 'b'): sp[k].update({'E1': [88, 80], 'E2': [88, 80], 'W1': [80, 100], 'W2': [80, 100]})
sp['b'].update({'E1': [86, 78], 'E2': [86, 78], 'W1': [76, 96], 'W2': [76, 96]})
sp['lat'] = [[-5, -15], [-5, -15]]
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 8 novembre, 18 h 30 : mollets (Philippe : « Rien ne bouge » et « Et il n'y a pas de murs »)
d = json.load(open(p)); P = d['POSES']
for k in ('calf', 'calf1', 'kbcalf'):
    P[k]['heel'] = [0, 8]
    for s_ in ('a', 'b'):   # le corps ne monte plus par la pose : c'est la cheville qui se lève
        q = P[k][s_]
        if s_ == 'b':
            for n in ('H', 'N', 'P', 'E1', 'E2', 'K1', 'K2', 'W1', 'W2'): q[n] = P[k]['a'][n][:]
c1 = P['calf1']
for s_ in ('a', 'b'):
    c1[s_].update({'K2': [104, 140], 'A2': [86, 160], 'E2': [113, 66], 'W2': [126, 64]})
c1['prop'] = {'t': 'wall', 'x': 130}
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 8 novembre, 18 h 35 : pompes (Philippe : « Les bras rentrent dans le corps », « Et les paumes doivent être à plat au sol en pompes classiques ? »)
# et bird dog (« les mains et les pieds sont un peu positionnés bizarrement ») : bas de la pompe refait (mains sous les épaules, coudes à 45°),
# bouts des doigts F1/F2 (paume à plat) et orteils T1/T2 (pied à plat derrière quand on est à genoux)
d = json.load(open(p)); P = d['POSES']
for k, dx in (('pushup', 0), ('incline', 2)):
    P[k]['b'].update({'N': [60 + dx, 168], 'H': [46 + dx, 165], 'E1': [70 + dx, 161], 'E2': [68 + dx, 161]})
    P[k]['lat'] = [[3, 6], [16.5, 9.3]]
def fing(k, off):
    for s_ in ('a', 'b'):
        q = P[k][s_] if s_ == 'a' else {**P[k]['a'], **P[k].get('b', {})}
        for w, f in (('W1', 'F1'), ('W2', 'F2')):
            P[k].setdefault(s_, {})[f] = [q[w][0] + off[0], q[w][1] + off[1]]
for k in ('pushup', 'incline', 'pike', 'catcow', 'birddog'): fing(k, (-9, 0))
fing('wallpush', (0, -9)); fing('chairdip', (8, 1))
P['birddog']['b']['F1'] = [3, 127]          # bras tendu devant : la main prolonge le bras
for k in ('birddog', 'catcow'):
    for s_ in ('a', 'b'): P[k][s_].update({'T1': [160, 180], 'T2': [158, 180]})
P['birddog']['b']['T2'] = [178, 143]        # jambe tendue derrière : orteils vers le sol
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 8 novembre, 18 h 40 : rythme propre aux échauffements (Philippe : « La rotation des épaules est trop rapide »)
d = json.load(open(p)); d['WTP'] = {'circle': [2.0, 0, 2.0, 0], 'pvc': [2.0, 0, 2.0, 0], 'catcow': [2.0, 0.5, 2.0, 0.5]}
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 8 novembre, 18 h 45 : mollets sur une jambe, main à plat sur le mur (elle traversait), talons un peu plus hauts
d = json.load(open(p)); P = d['POSES']
for k in ('calf', 'calf1', 'kbcalf'): P[k]['heel'] = [0, 10]
for s_ in ('a', 'b'): P['calf1'][s_].update({'E2': [119, 62], 'W2': [138, 64], 'F2': [138, 55]})
P['calf1']['prop'] = {'t': 'wall', 'x': 143}
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 8 novembre, 18 h 55 : chat-vache, courbure du dos plus marquée (Philippe : « Le dos ne bouge pas du tout ? »)
d = json.load(open(p)); c = d['POSES']['catcow']; c['a']['S'] = [89, 120]; c['b']['S'] = [89, 148]
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 8 novembre, 19 h : rotations d'épaules, bras sur les côtés et petits cercles (Philippe : « La rotation n'est pas très réaliste »)
d = json.load(open(p)); d['POSES']['circle']['circ'] = True
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 8 novembre, 19 h 15 : poses 3D du nouveau personnage (option 2 choisie par Philippe), générées par tools/corps/convert.py
import os
_p3 = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'corps', 'poses3.json')
if os.path.exists(_p3):
    d = json.load(open(p)); d['POSES3'] = json.load(open(_p3))
    json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 10 novembre, 18 h 45 : pont fessier (capture de Philippe, « Oui et pied vers le bas »), personnage posé au sol au pont fessier et à la planche
# (« Et ça donne l'impression que le bonhomme est en lévitation », « Pour le gainage planche aussi d'ailleurs »), étirements d'un seul côté
# des deux côtés (« Étirement quadriceps n'est que d'un côté ») ; corrections de pose dans corps/fix3.py.
# Pont fessier mini-bande : pose à deux jambes « bridge2 » (il prenait la pose unilatérale). Programme livré version 14 (prime sur la base, version 13).
d = json.load(open(p)); d['POSES']['bridge2'] = json.loads(json.dumps(d['POSES']['bridge'])); d['EX']['mbbridge']['pose'] = 'bridge2'; d['V'] = 14
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 11 novembre : contrôle de tous les mouvements (Philippe : « pourrais-tu vérifier pour tous les exercices que les mouvements sont bien
# cohérents ») ; corrections dans corps/fix4.py (dips sur chaise, squat bulgare, montée sur chaise, bancs, rowing barre). Programme version 15.
d = json.load(open(p)); d['POSES3'] = json.load(open(_p3)); d['V'] = 15
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 11 novembre, 10 h 50 : halo kettlebell en vrai tour autour de la tête (Philippe : « Sens du halo ? », « Oui ») ; pose dans corps/fix4.py (halo),
# tempo propre à l'exercice : un tour par répétition, sans retour en arrière. Programme version 16.
d = json.load(open(p)); d['POSES3'] = json.load(open(_p3)); d['EX']['kbhalo']['tp'] = [4.0, 0.0, 0.0, 0.0]; d['V'] = 16
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 11 novembre, 12 h 15 : aide contextuelle (Philippe : « Est-ce qu'on ne rajouterait pas une aide contextuelle ? ») ; consignes du dead bug
# (il n'en avait pas) ; les « À éviter » de chaque exercice sont ajoutés par tools/avoid.py, leurs traductions par tools/en.py.
d = json.load(open(p)); d['EX']['deadbug']['cues'] = ['Bas du dos au sol', 'Bras et jambe opposés', 'Expire en tendant la jambe']
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

# --- 11 novembre, 14 h 40 : séances E et F pour 5 et 6 séances par semaine (Philippe : « J'ai choisi 5 séances », puis
# « une E et une F avec leurs propres exercices »). E : bas du corps, chaîne arrière ; F : haut du corps, bras et abdos.
# Les exercices diffèrent de ceux de la séance du même thème dans le même cycle. Programme version 17.
d = json.load(open(p))
d['SESS1']['E'] = {'t': 'Bas · fessiers & ischios', 'ex': ['sldl', 'fwdlunge', 'squat', 'bridge', 'calf1', 'dbugw'], 'cool': ['stquad', 'stham', 'stlat'], 'warm': ['bwsquat', 'hinge', 'lungev']}
d['SESS2']['E'] = {'t': 'Bas · fessiers & ischios', 'ex': ['rdl', 'lunge', 'squat', 'bridge', 'calf', 'crunch'], 'cool': ['stquad', 'stham', 'stlat'], 'warm': ['bwsquat', 'hinge', 'lungev']}
d['SESS1']['F'] = {'t': 'Haut · bras & abdos', 'ex': ['pushup', 'pull', 'revfly', 'curl', 'skull', 'plank'], 'cool': ['stpec', 'stlat', 'sttri'], 'warm': ['circle', 'catcow', 'rowv']}
d['SESS2']['F'] = {'t': 'Haut · bras & abdos', 'ex': ['incline', 'row2', 'lateral', 'hammer', 'ohtri', 'crunch'], 'cool': ['stpec', 'stlat', 'sttri'], 'warm': ['circle', 'pressv', 'rowv']}
d['SIG']['SESS1']['E'] = {'sldl': ['kbswing', 'bbrdl', 'ballcurl'], 'fwdlunge': ['stepup', 'bulgarian'], 'squat': ['kbsumo', 'bosusquat', 'mbsquat'], 'bridge': ['mbbridge', 'ballcurl'], 'dbugw': ['abwheel', 'medcrunch']}
d['SIG']['SESS2']['E'] = {'rdl': ['bbrdl', 'kbswing', 'ballcurl'], 'lunge': ['bulgarian', 'stepup'], 'squat': ['kbsumo', 'bosusquat', 'mbsquat'], 'bridge': ['mbbridge', 'ballcurl'], 'crunch': ['abwheel', 'kneeraise', 'medcrunch']}
d['SIG']['SESS1']['F'] = {'pushup': ['benchpress', 'inclinepress'], 'pull': ['towelrow'], 'revfly': ['bandpull'], 'curl': ['bbcurl'], 'skull': ['dips', 'chairdip'], 'plank': ['mbslam']}
d['SIG']['SESS2']['F'] = {'incline': ['inclinepress', 'benchpress'], 'row2': ['pullup', 'bbrow'], 'lateral': ['bandlat'], 'hammer': ['bbcurl'], 'ohtri': ['dips', 'chairdip'], 'crunch': ['abwheel', 'kneeraise', 'medcrunch']}
d['V'] = 17
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))


# --- 11 novembre, 18 h 45 : corde à sauter (Philippe : « Pourquoi le personnage ne saute toujours pas à la corde à sauter ? ») : vrai saut
# (9 cm, pointes tendues en l'air, genoux un peu fléchis à la réception), rythme rapide propre à l'exercice, corde dessinée par l'appli.
d = json.load(open(p)); d['POSES3'] = json.load(open(_p3))
r = d['POSES3']['rope']; r['tool'] = 'rope'; r['db'] = ['W1', 'W2']; k0, k1 = r['k'][0], r['k'][-1]
for s, sx in (('1', 1), ('2', -1)):
    k0['K' + s] = [9.08 * sx, 37.0, 4.5]; k0['P'] = [0.0, 74.5, 0.0]; k0['N'] = [0.0, 122.5, 0.5]
    k1['A' + s] = [9.38 * sx, 11.0, 0.0]; k1['T' + s] = [9.38 * sx, 1.5, 10.5]; k1['K' + s] = [9.08 * sx, 48.0, 2.0]
    for k in (k0, k1): k['c' + s] = 0.85
k1['P'] = [0.0, 86.0, 0.0]; k1['N'] = [0.0, 134.0, 0.0]; k1['H'] = [0.0, k1['N'][1] + (k0['H'][1] - 124.0), k0['H'][2]] if 'H' in k0 else k1.get('H')
for x in ('E1', 'E2', 'W1', 'W2'): k1[x] = [k0[x][0], k0[x][1] + 10.0, k0[x][2]]
d['WTP']['rope'] = [0.24, 0.0, 0.26, 0.06]; d['V'] = 18
json.dump(d['POSES3'], open(_p3, 'w'), ensure_ascii=False, separators=(',', ':'))
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))


# --- 11 novembre, 21 h 30 : tempo réel (Philippe : « Partons sur 2,3,5 et 7 », point 3 : descente lente, pause en bas, montée plus rapide).
# Le lecteur suit le tempo de la semaine (montée 1,5 s, tenue, descente 3 à 4 s) ; « inv » dit que le mouvement commence par la descente.
# Contrôle des 80 exercices (sens du bassin, des charges ou des pieds entre le début et la fin du mouvement) : 11 avaient le mauvais sens,
# le personnage y descendait vite et remontait lentement (ou l'inverse pour les développés couché et incliné).
d = json.load(open(p)); d['POSES3'] = json.load(open(_p3))
for id in ('bbrdl', 'kbrdl', 'bandrdl', 'lungebw', 'kblunge', 'bulgarian', 'kbsumo', 'sldlbw', 'abwheel'): d['EX'][id]['inv'] = 1
for id in ('benchpress', 'inclinepress'): d['EX'][id].pop('inv', None)
d['V'] = 19
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))

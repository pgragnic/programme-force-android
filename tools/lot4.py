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

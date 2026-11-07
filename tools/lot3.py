# Lot 3 du matériel : serviette, medicine ball, ballon de stabilité, Bosu, bâton (PVC), balle de massage
# Usage : python3 tools/lot3.py app/src/main/assets/programme.json
import json, sys, copy
p = sys.argv[1]; d = json.load(open(p))
EX, PO, MAP = d['EX'], d['POSES'], d['MAP']
def ex(id, n, l, tip, cues, eq, lt, tool, mp, stress=(), pose=None, m=None, dr=None, inv=None, band=None, back=False, knee=False):
    o = {'n': n, 'l': l, 'tip': tip, 'cues': cues, 'eq': eq, 'lt': lt, 'tool': tool, 'stress': list(stress), 'back': back, 'knee': knee}
    if pose: o['pose'] = pose
    if m: o['m'] = m
    if dr is not None: o['dr'] = dr
    if inv: o['inv'] = 1
    if band: o['band'] = band
    EX[id] = o; MAP[id] = mp

ex('towelrow', 'Tirage serviette à la porte', 'Serviette et porte', 'Serviette passée autour de la poignée d’une porte bien fermée, penche-toi en arrière et tire-toi vers la porte', ['Porte bien fermée', 'Corps gainé', 'Serre les omoplates'], ['towel'], 'bw', 'towel', {'Dos': 1, 'Biceps': .5}, [], pose='towelrow', band=[['W1', 'W2']])
ex('medsquat', 'Squat medicine ball', 'Medicine ball', 'Ballon contre la poitrine, cuisses parallèles au sol', ['Genoux dans l’axe des pieds', 'Talons au sol', 'Buste droit'], ['medball'], 'bw', 'medball', MAP['squat'], ['genou'], pose='squat', inv=1, dr=2, knee=True)
ex('medcrunch', 'Crunch medicine ball', 'Medicine ball', 'Ballon contre la poitrine, enroule le haut du dos', ['Bas du dos au sol', 'Enroule le haut du dos', 'Menton rentré'], ['medball'], 'bw', 'medball', MAP['crunch'], [], pose='crunch', dr=2)
ex('mbslam', 'Slam medicine ball', 'Medicine ball', 'Ballon au-dessus de la tête, lance-le au sol devant tes pieds en pliant les hanches', ['Abdos serrés', 'Plie les hanches', 'Rattrape le ballon au rebond'], ['medball'], 'bw', 'medball', {'Dos': .6, 'Abdos': .6, 'Épaules': .4, 'Fessiers': .4}, ['dos', 'cardio'], pose='mbslam', dr=2)
ex('ballcurl', 'Leg curl sur ballon', 'Ballon de stabilité', 'Allongé, talons sur le ballon, monte les hanches et ramène le ballon vers toi', ['Hanches hautes', 'Talons qui tirent le ballon', 'Retour lent'], ['swissball'], 'bw', '', {'Ischios': 1, 'Fessiers': .6}, [], pose='ballcurl')
ex('bosusquat', 'Squat sur Bosu', 'Bosu', 'Pieds sur le dôme, descends lentement en gardant l’équilibre', ['Genoux dans l’axe des pieds', 'Regard droit devant', 'Descente lente'], ['bosu'], 'bw', '', {'Quadriceps': 1, 'Fessiers': .7, 'Abdos': .3}, ['genou', 'cheville'], pose='bosusquat', inv=1, knee=True)

UP = d['UP']; ALT = d['ALT']
for k in ('row1', 'row2'): ALT[k] = ['kbrow', 'bbrow', 'bandrow', 'pullup', 'towelrow', 'superman']
ALT['pull'] = ['kbpull', 'bandpull', 'towelrow', 'pronet', 'superman']
for k in ('rdl', 'sldl'): ALT[k] = ['kbrdl', 'bbrdl', 'bandrdl', 'ballcurl', 'sldlbw', 'bridge']
ALT['squat'] = ['kbgoblet', 'bandsquat', 'mbsquat', 'medsquat', 'bosusquat', 'squatpdc', 'wallsit', 'bridge']
ALT['crunch'] = ['medcrunch', 'crunchbw']
UP['plank'] = []
# échauffement et retour au calme
d['WU']['pvc'] = ['pvc', 'Rotations d’épaules au bâton', 'Bras tendus, prise large, passe le bâton devant puis derrière toi']
d['COOLN']['ballfoot'] = 'Balle de massage sous le pied'
d['WARMUP_UP'] = {'circle': [['rope', 'rope'], ['pvc', 'pvc']]}
d['COOL_UP'] = {'stlat': [['rollback', 'roller']], 'stquad': [['ballfoot', 'rollball']]}

STAND = {'H': [100, 38], 'N': [100, 56], 'P': [100, 104], 'K1': [101, 142], 'A1': [101, 180], 'K2': [99, 142], 'A2': [99, 180]}
PO['towelrow'] = {'cam': 1.55, 'prop': {'t': 'door', 'x': 106}, 'lat': [[8, 3], [10, 3]],
    'a': {'H': [42, 56], 'N': [50, 72], 'P': [74, 114], 'K1': [93, 147], 'A1': [112, 180], 'K2': [91, 147], 'A2': [110, 180], 'E1': [73, 76], 'W1': [96, 80], 'E2': [71, 76], 'W2': [94, 80]},
    'b': {'H': [76, 42], 'N': [80, 59], 'P': [92, 106], 'K1': [102, 143], 'A1': [112, 180], 'K2': [100, 143], 'A2': [110, 180], 'E1': [70, 82], 'W1': [96, 76], 'E2': [68, 82], 'W2': [94, 76]}, '__d': 1}
PO['mbslam'] = {'cam': 1.1,
    'a': dict(STAND, E1=[104, 30], W1=[106, 6], E2=[102, 30], W2=[104, 6]),
    'b': {'H': [124, 70], 'N': [116, 84], 'P': [86, 122], 'K1': [112, 142], 'A1': [101, 180], 'K2': [110, 142], 'A2': [99, 180], 'E1': [128, 104], 'W1': [134, 128], 'E2': [126, 104], 'W2': [132, 128]},
    'kf': [{'t': 0.5, 'p': {'H': [110, 46], 'N': [106, 62], 'P': [96, 110], 'K1': [106, 144], 'A1': [101, 180], 'K2': [104, 144], 'A2': [99, 180], 'E1': [124, 60], 'W1': [140, 72], 'E2': [122, 60], 'W2': [138, 72]}}], 'db': ['W1', 'W2'], '__d': 1}
PO['ballcurl'] = {'prop': {'t': 'swissball'},
    'a': {'H': [38, 166], 'N': [54, 168], 'P': [100, 160], 'K1': [136, 150], 'A1': [172, 140], 'K2': [134, 150], 'A2': [170, 140], 'E1': [74, 176], 'W1': [94, 178], 'E2': [72, 176], 'W2': [92, 178]},
    'b': {'H': [38, 166], 'N': [54, 168], 'P': [98, 146], 'K1': [128, 118], 'A1': [146, 146], 'K2': [126, 118], 'A2': [144, 146], 'E1': [74, 176], 'W1': [94, 178], 'E2': [72, 176], 'W2': [92, 178]}, '__d': 1}
sq = copy.deepcopy(PO['bwsquat'])
for kk in ('a', 'b'):
    for v in sq[kk].values(): v[1] -= 12
for v in sq['kf'][0]['p'].values(): v[1] -= 12
sq.update({'free': True, 'prop': {'t': 'bosu'}}); PO['bosusquat'] = sq
PO['pvc'] = {'cam': 1.2, 'lat': [[30, 42], [30, 42]],
    'a': dict(STAND, E1=[112, 78], W1=[124, 98], E2=[110, 78], W2=[122, 98]),
    'b': dict(STAND, E1=[88, 78], W1=[76, 98], E2=[86, 78], W2=[74, 98]),
    'kf': [{'t': 0.5, 'p': {'E1': [100, 30], 'W1': [100, 6], 'E2': [98, 30], 'W2': [98, 6]}}], 'db': ['W1', 'W2'], '__d': 1}
PO['ballfoot'] = {'cam': 1.2, 'free': False, 'prop': {'t': 'rollball'},
    'a': dict(STAND, K1=[110, 142], A1=[114, 176], E1=[101, 80], W1=[101, 104], E2=[99, 80], W2=[99, 104]),
    'b': dict(STAND, K1=[114, 142], A1=[122, 176], E1=[101, 80], W1=[101, 104], E2=[99, 80], W2=[99, 104]), '__d': 1}
PO['pvc']['tool'] = 'pvc'
for k in ('mbslam', 'pvc'):
    if k not in d['TWOHAND'] and k == 'mbslam': d['TWOHAND'].append(k)
for sec in d['EQ']:
    if sec[0] == 'Abdos et récupération':
        sec[1] += [['towel', 'Serviette', 1], ['medball', 'Medicine ball', 1], ['swissball', 'Ballon de stabilité', 1], ['bosu', 'Bosu', 1], ['pvc', 'Bâton (PVC)', 1], ['rollball', 'Balle de massage', 1]]
        sec[0] = 'Accessoires'
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))
print(len(EX), 'exercices,', len(PO), 'poses')

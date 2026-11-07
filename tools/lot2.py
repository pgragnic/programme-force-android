# Lot 2 du matériel : traction, dips, barre et disques, banc inclinable, roue abdominale, corde à sauter, rouleau, step
# Usage : python3 tools/lot2.py app/src/main/assets/programme.json
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

ex('pullup', 'Tractions', 'Barre de traction', 'Prise un peu plus large que les épaules, monte le menton au-dessus de la barre', ['Épaules basses', 'Poitrine vers la barre', 'Descente lente bras tendus'], ['pullup'], 'bw', '', {'Dos': 1, 'Biceps': .6}, ['epaule'], pose='pullup', dr=-5)
ex('kneeraise', 'Relevés de genoux suspendu', 'Barre de traction', 'Suspendu, monte les genoux vers la poitrine sans balancer', ['Pas de balancier', 'Enroule le bassin', 'Descente lente'], ['pullup'], 'bw', '', {'Abdos': 1}, ['epaule'], pose='kneeraise', dr=-2)
ex('dips', 'Dips', 'Barres à dips', 'Buste légèrement penché, descends jusqu’à ce que les bras soient à 90°', ['Épaules basses', 'Coudes vers l’arrière', 'Remonte bras tendus'], ['dips'], 'bw', '', {'Triceps': 1, 'Pectoraux': .6, 'Épaules': .3}, ['epaule', 'coude'], pose='dips', dr=-4, inv=1)
ex('bbrdl', 'Soulevé de terre roumain barre', 'Barre et disques', 'Barre contre les cuisses, hanches en arrière, dos plat', ['Dos plat', 'Barre contre les jambes', 'Serre les fessiers en haut'], ['barbell'], 'kg', 'bb', MAP['rdl'], ['dos'], pose='bbrdl', back=True)
ex('bbrow', 'Rowing barre', 'Barre et disques', 'Buste penché, dos plat, tire la barre vers le nombril', ['Dos plat', 'Serre les omoplates', 'Coudes près du corps'], ['barbell'], 'kg', 'bb', MAP['row2'], ['dos'], pose='bbrow', back=True)
ex('bbpress', 'Développé militaire barre', 'Barre et disques', 'Barre devant les épaules, pousse au-dessus de la tête sans cambrer', ['Abdos et fessiers serrés', 'Ne cambre pas', 'Tête qui passe sous la barre'], ['barbell'], 'kg', 'bb', MAP['press'], ['epaule', 'dos'], pose='bbpress', back=True)
ex('bbcurl', 'Curl barre', 'Barre et disques', 'Paumes vers le haut, coudes collés au corps', ['Coudes collés au corps', 'Pas d’élan', 'Descente contrôlée'], ['barbell'], 'kg', 'bb', MAP['curl'], ['coude'], pose='bbcurl', dr=-2)
ex('bbfloor', 'Développé au sol barre', 'Barre et disques', 'Allongé, mains un peu plus larges que les épaules, coudes à 45°', ['Coudes à 45°', 'Pousse vers le plafond', 'Descente contrôlée'], ['barbell'], 'kg', 'bb', MAP['floor'], [], pose='bbfloor')
ex('inclinepress', 'Développé incliné haltères', '2 haltères', 'Dossier incliné à 30–45°, pousse vers le haut et légèrement vers l’avant', ['Omoplates serrées', 'Coudes à 45°', 'Pousse vers le plafond'], ['db', 'incbench'], 'kg', 'db', {'Pectoraux': 1, 'Épaules': .5, 'Triceps': .4}, ['epaule'], pose='inclinepress', inv=1)
ex('abwheel', 'Roue abdominale à genoux', 'Roue abdominale', 'À genoux, roule vers l’avant en gardant le dos plat, reviens en contractant les abdos', ['Dos plat, pas de creux', 'Abdos serrés', 'Va seulement aussi loin que tu contrôles'], ['abwheel'], 'bw', 'wheel', {'Abdos': 1, 'Dos': .3}, ['dos', 'epaule'], pose='abwheel', dr=-2)
ex('pronet', 'Écartés à plat ventre', 'Poids du corps', 'À plat ventre, bras en croix, décolle les bras en serrant les omoplates', ['Regard vers le sol', 'Serre les omoplates', 'Pause en haut'], [], 'bw', '', {'Épaules': .8, 'Dos': .7}, [], pose='pronet', dr=2)
ex('closepush', 'Pompes serrées', 'Poids du corps', 'Mains sous les épaules, coudes le long du corps (sur les genoux si besoin)', ['Coudes le long du corps', 'Corps gainé', 'Poitrine près du sol'], [], 'bw', '', {'Triceps': 1, 'Pectoraux': .6}, ['coude', 'epaule'], pose='pushup', inv=1, dr=-2)
# la montée se fait aussi sur un step
EX['stepup']['eq'] = ['chair|bench|step']; EX['stepup']['l'] = 'Chaise, banc ou step'

UP = d['UP']; ALT = d['ALT']
UP['crunch'] = ['abwheel', 'kneeraise']; UP['incline'] = ['inclinepress']
ALT['row1'] = ['kbrow', 'bbrow', 'bandrow', 'pullup', 'superman']
ALT['row2'] = ['kbrow', 'bbrow', 'bandrow', 'pullup', 'superman']
ALT['floor'] = ['kbfloor', 'bbfloor', 'pushup', 'incline', 'wallpush']
ALT['benchpress'] = ['floor', 'kbfloor', 'bbfloor', 'pushup', 'incline', 'wallpush']
ALT['pull'] = ['kbpull', 'bandpull', 'pronet', 'superman']
ALT['revfly'] = ['bandpull', 'pronet', 'superman']
for k in ('ohtri', 'skull'): ALT[k] = ['kbohtri', 'dips', 'bandext', 'chairdip', 'closepush', 'incline']
for k in ('hammer', 'curl'): ALT[k] = ['kbcurl', 'bbcurl', 'bandcurl', 'pullup']
ALT['press'] = ['kbpress', 'bbpress', 'bandpress', 'pike', 'wallpush']
for k in ('rdl', 'sldl'): ALT[k] = ['kbrdl', 'bbrdl', 'bandrdl', 'sldlbw', 'bridge']
ALT['crunch'] = ['crunchbw']; ALT['dbugw'] = ['deadbug']
ALT['incline'] = ['wallpush']; ALT['inclinepress'] = ['incline', 'wallpush']
ALT['pushup'] = ['incline', 'wallpush']

# échauffement et retour au calme selon le matériel
d['WU']['rope'] = ['rope', 'Corde à sauter', 'Petits sauts sur l’avant des pieds, poignets qui tournent']
d['COOLN']['rollback'] = 'Rouleau sur le haut du dos'
d['WARMUP_UP'] = {'circle': [['rope', 'rope']]}
d['COOL_UP'] = {'stlat': [['rollback', 'roller']]}

def cp(src, **kw):
    o = copy.deepcopy(PO[src]); o.update(kw); return o
# barre : mêmes mouvements que les haltères, prise plus large
PO['bbrdl'] = cp('rdl', lat=[[6, 8], [6, 8]], db=['W1', 'W2'])
PO['bbrow'] = cp('row2', lat=[[8, 8], [8, 8]], db=['W1', 'W2'])
PO['bbpress'] = cp('press', lat=[[10, 8], [10, 8]], db=['W1', 'W2'])
PO['bbcurl'] = cp('curl', lat=[[3, 5], [3, 5]], db=['W1', 'W2'])
PO['bbfloor'] = cp('floor', lat=[[10, 8], [10, 8]], db=['W1', 'W2'])
PO['pullup'] = {'cam': 0.9, 'free': True, 'prop': {'t': 'pullbar'}, 'lat': [[14, 10], [16, 10]],
    'a': {'H': [103, 30], 'N': [100, 48], 'P': [100, 96], 'K1': [101, 134], 'A1': [100, 172], 'K2': [99, 134], 'A2': [98, 172], 'E1': [100, 22], 'W1': [100, -2], 'E2': [98, 22], 'W2': [98, -2]},
    'b': {'H': [104, -4], 'N': [100, 14], 'P': [99, 62], 'K1': [100, 100], 'A1': [99, 138], 'K2': [98, 100], 'A2': [97, 138], 'E1': [106, 30], 'W1': [100, -2], 'E2': [104, 30], 'W2': [98, -2]}, 'db': ['W1', 'W2'], '__d': 1}
PO['kneeraise'] = {'cam': 1.2, 'free': True, 'prop': {'t': 'pullbar'}, 'lat': [[12, 10], [12, 10]],
    'a': {'H': [103, 30], 'N': [100, 48], 'P': [100, 96], 'K1': [101, 134], 'A1': [100, 172], 'K2': [99, 134], 'A2': [98, 172], 'E1': [100, 22], 'W1': [100, -2], 'E2': [98, 22], 'W2': [98, -2]},
    'b': {'H': [100, 30], 'N': [98, 48], 'P': [102, 96], 'K1': [136, 84], 'A1': [130, 120], 'K2': [134, 84], 'A2': [128, 120], 'E1': [100, 22], 'W1': [100, -2], 'E2': [98, 22], 'W2': [98, -2]}, 'db': ['W1', 'W2'], '__d': 1}
PO['dips'] = {'cam': 1.1, 'free': True, 'prop': {'t': 'dipbars'}, 'lat': [[4, 6], [10, 6]],
    'a': {'H': [102, 20], 'N': [100, 38], 'P': [99, 86], 'K1': [104, 122], 'A1': [90, 154], 'K2': [102, 122], 'A2': [88, 154], 'E1': [100, 62], 'W1': [100, 86], 'E2': [98, 62], 'W2': [98, 86]},
    'b': {'H': [108, 40], 'N': [104, 58], 'P': [96, 104], 'K1': [102, 140], 'A1': [88, 172], 'K2': [100, 140], 'A2': [86, 172], 'E1': [80, 70], 'W1': [100, 86], 'E2': [78, 70], 'W2': [98, 86]}, '__d': 1}
PO['inclinepress'] = {'cam': 1.1, 'prop': {'t': 'incbench', 'x': 88},
    'a': {'H': [58, 86], 'N': [68, 100], 'P': [98, 134], 'K1': [134, 134], 'A1': [140, 178], 'K2': [132, 134], 'A2': [138, 178], 'E1': [64, 122], 'W1': [72, 98], 'E2': [62, 122], 'W2': [70, 98]},
    'b': {'H': [58, 86], 'N': [68, 100], 'P': [98, 134], 'K1': [134, 134], 'A1': [140, 178], 'K2': [132, 134], 'A2': [138, 178], 'E1': [78, 80], 'W1': [88, 58], 'E2': [76, 80], 'W2': [86, 58]}, 'db': ['W1', 'W2'], '__d': 1}
PO['abwheel'] = {'cam': 1.3, 'lat': [[4, 4], [4, 4]],
    'a': {'H': [76, 112], 'N': [86, 124], 'P': [118, 142], 'K1': [124, 176], 'A1': [160, 176], 'K2': [122, 176], 'A2': [158, 176], 'E1': [86, 148], 'W1': [86, 172], 'E2': [84, 148], 'W2': [84, 172]},
    'b': {'H': [50, 146], 'N': [64, 152], 'P': [112, 162], 'K1': [124, 176], 'A1': [160, 176], 'K2': [122, 176], 'A2': [158, 176], 'E1': [42, 162], 'W1': [20, 172], 'E2': [40, 162], 'W2': [18, 172]}, 'db': ['W1', 'W2'], '__d': 1}
PO['pronet'] = {'lat': [[24, 46], [24, 46]],
    'a': {'H': [40, 170], 'N': [54, 172], 'P': [104, 174], 'K1': [140, 175], 'A1': [176, 176], 'K2': [138, 175], 'A2': [174, 176], 'E1': [56, 176], 'W1': [58, 177], 'E2': [54, 176], 'W2': [56, 177]},
    'b': {'H': [39, 165], 'N': [54, 170], 'P': [104, 174], 'K1': [140, 175], 'A1': [176, 176], 'K2': [138, 175], 'A2': [174, 176], 'E1': [55, 164], 'W1': [56, 158], 'E2': [53, 164], 'W2': [54, 158]}, '__d': 1}
PO['rope'] = {'cam': 0.9, 'free': True, 'lat': [[6, 12], [6, 12]],
    'a': {'H': [100, 38], 'N': [100, 56], 'P': [100, 104], 'K1': [102, 142], 'A1': [100, 180], 'K2': [100, 142], 'A2': [98, 180], 'E1': [102, 80], 'W1': [108, 100], 'E2': [100, 80], 'W2': [106, 100]},
    'b': {'H': [100, 32], 'N': [100, 50], 'P': [100, 98], 'K1': [103, 136], 'A1': [100, 173], 'K2': [101, 136], 'A2': [98, 173], 'E1': [102, 74], 'W1': [110, 92], 'E2': [100, 74], 'W2': [108, 92]}, '__d': 1}
PO['rollback'] = {'prop': {'t': 'roller', 'x': 60, 'y': 166},
    'a': {'H': [38, 150], 'N': [54, 152], 'P': [100, 150], 'K1': [128, 136], 'A1': [146, 178], 'K2': [126, 136], 'A2': [144, 178], 'E1': [50, 132], 'W1': [44, 112], 'E2': [48, 132], 'W2': [42, 112]},
    'b': {'H': [52, 150], 'N': [68, 152], 'P': [112, 152], 'K1': [136, 138], 'A1': [146, 178], 'K2': [134, 138], 'A2': [144, 178], 'E1': [64, 132], 'W1': [58, 112], 'E2': [62, 132], 'W2': [56, 112]}, '__d': 1}
for k in ('bbrdl', 'bbrow', 'abwheel', 'pronet'):
    if k not in d['FLIPX']: d['FLIPX'].append(k)
for k in ('abwheel',):
    if k not in d['TWOHAND']: d['TWOHAND'].append(k)
for sec in d['EQ']:
    for it in sec[1]:
        if it[0] in ('barbell', 'incbench', 'pullup', 'dips', 'step', 'abwheel', 'rope', 'roller'): it[2] = 1
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))
print(len(EX), 'exercices,', len(PO), 'poses')

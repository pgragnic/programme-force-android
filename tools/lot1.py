# Ajoute au programme le lot 1 du matériel (kettlebell, élastiques, mini-bande, chaise, mur, banc plat, poids du corps)
# Usage : python3 tools/lot1.py app/src/main/assets/programme.json
import json, sys, copy
p = sys.argv[1]; d = json.load(open(p))
EX, PO, MAP, CUEX = d['EX'], d['POSES'], d['MAP'], d['CUEX']

# --- matériel requis, type de charge, articulations sollicitées pour les exercices existants
OLD = {
 'row1': (['db'], 'kg', []), 'floor': (['db'], 'kg', []), 'pull': (['db'], 'kg', ['epaule']), 'ohtri': (['db'], 'kg', ['coude', 'epaule']),
 'hammer': (['db'], 'kg', ['coude']), 'deadbug': ([], 'bw', []), 'crunch': (['db'], 'kg', []), 'squat': (['db'], 'kg', ['genou']),
 'lunge': (['db'], 'kg', ['genou']), 'rdl': (['db'], 'kg', ['dos']), 'bridge': ([], 'bw', []), 'calf': (['db'], 'kg', ['cheville']),
 'plank': ([], 'bw', []), 'press': (['db'], 'kg', ['epaule', 'dos']), 'row2': (['db'], 'kg', ['dos']), 'incline': ([], 'bw', ['epaule']),
 'lateral': (['db'], 'kg', ['epaule']), 'revfly': (['db'], 'kg', ['dos']), 'curl': (['db'], 'kg', ['coude']), 'birddog': ([], 'bw', []),
 'fwdlunge': (['db'], 'kg', ['genou']), 'sldl': (['db'], 'kg', ['dos', 'hanche']), 'calf1': (['db'], 'kg', ['cheville']),
 'dbugw': (['db'], 'kg', []), 'skull': (['db'], 'kg', ['coude']), 'pushup': ([], 'bw', ['epaule']),
}
for k, (eq, lt, st) in OLD.items():
    EX[k].update({'eq': eq, 'lt': lt, 'stress': st, 'tool': 'db' if 'db' in eq else ''})

def ex(id, n, l, tip, cues, eq, lt, tool, mp, stress=(), pose=None, m=None, dr=None, inv=None, band=None, f0=None, back=False, knee=False):
    o = {'n': n, 'l': l, 'tip': tip, 'cues': cues, 'eq': eq, 'lt': lt, 'tool': tool, 'stress': list(stress), 'back': back, 'knee': knee}
    if pose: o['pose'] = pose
    if m: o['m'] = m
    if dr is not None: o['dr'] = dr
    if inv: o['inv'] = 1
    if band: o['band'] = band
    if f0: o['f0'] = f0
    EX[id] = o; MAP[id] = mp

FEET = [['W1', 'A1'], ['W2', 'A2']]; KNEES = [['K1', 'K2']]
# --- kettlebell (une seule kettlebell)
ex('kbgoblet', 'Goblet squat kettlebell', '1 kettlebell', 'Kettlebell contre la poitrine, cuisses parallèles au sol', ['Genoux dans l’axe des pieds', 'Talons au sol', 'Buste droit'], ['kb'], 'kg', 'kb', MAP['squat'], ['genou'], pose='squat', inv=1, back=True, knee=True)
ex('kbrow', 'Rowing unilatéral kettlebell', '1 kettlebell', 'Main libre sur le genou, dos plat, tire vers la hanche', ['Dos plat', 'Coude qui frôle le corps', 'Tire vers la hanche'], ['kb'], 'kg', 'kb', MAP['row1'], [], pose='row1', m='uni', back=True)
ex('kbrdl', 'Soulevé de terre kettlebell', '1 kettlebell à 2 mains', 'Hanches en arrière, dos plat, kettlebell le long des jambes', ['Dos plat', 'Hanches en arrière', 'Serre les fessiers en haut'], ['kb'], 'kg', 'kb', MAP['rdl'], ['dos'], pose='kbrdl', back=True)
ex('kbpress', 'Développé militaire kettlebell', '1 kettlebell', 'Un bras après l’autre, abdos serrés, sans cambrer', ['Abdos serrés', 'Ne cambre pas', 'Bras tendu au-dessus de la tête'], ['kb'], 'kg', 'kb', MAP['press'], ['epaule', 'dos'], pose='kbpress', m='uni', back=True)
ex('kbfloor', 'Développé au sol kettlebell', '1 kettlebell', 'Allongé, un bras après l’autre, coude à 45°', ['Coude à 45°', 'Pousse vers le plafond', 'Descente contrôlée'], ['kb'], 'kg', 'kb', MAP['floor'], [], pose='kbfloor', m='uni')
ex('kblunge', 'Fente arrière goblet kettlebell', '1 kettlebell à 2 mains', 'Kettlebell contre la poitrine, buste droit', ['Genou avant au-dessus de la cheville', 'Buste droit', 'Repousse sur le talon avant'], ['kb'], 'kg', 'kb', MAP['lunge'], ['genou'], pose='kblunge', m='uni', dr=-2, knee=True)
ex('kbpull', 'Pull-over kettlebell', '1 kettlebell à 2 mains', 'Bras légèrement fléchis, côtes basses', ['Bras légèrement fléchis', 'Bas du dos au sol', 'Descends jusqu’à sentir l’étirement'], ['kb'], 'kg', 'kb', MAP['pull'], ['epaule'], pose='pull', inv=1)
ex('kbohtri', 'Extension triceps nuque kettlebell', '1 kettlebell à 2 mains', 'Coudes serrés et pointés vers le plafond', ['Coudes serrés', 'Coudes vers le plafond', 'Seuls les avant-bras bougent'], ['kb'], 'kg', 'kb', MAP['ohtri'], ['coude', 'epaule'], pose='ohtri')
ex('kbcurl', 'Curl kettlebell', '1 kettlebell', 'Un bras après l’autre, coude collé au corps', ['Coude collé au corps', 'Pas d’élan', 'Descente contrôlée'], ['kb'], 'kg', 'kb', MAP['curl'], ['coude'], pose='kbcurl', m='uni', dr=-2)
ex('kbcalf', 'Mollets debout kettlebell', '1 kettlebell', 'Monte le plus haut possible, tiens-toi à un mur', ['Monte le plus haut possible', 'Pause en haut', 'Descente lente'], ['kb'], 'kg', 'kb', MAP['calf'], ['cheville'], pose='kbcalf', dr=5)
# --- bande à poignées (pieds sur la bande)
ex('bandrow', 'Rowing élastique', 'Bande à poignées', 'Pieds sur la bande, buste penché, dos plat, tire vers le nombril', ['Dos plat', 'Serre les omoplates', 'Retour lent'], ['band'], 'band', 'band', MAP['row2'], ['dos'], pose='row2', band=FEET, back=True)
ex('bandpress', 'Développé militaire élastique', 'Bande à poignées', 'Pieds sur la bande, pousse au-dessus de la tête sans cambrer', ['Abdos serrés', 'Ne cambre pas', 'Bras tendus en haut'], ['band'], 'band', 'band', MAP['press'], ['epaule'], pose='press', band=FEET, back=True)
ex('bandcurl', 'Curl élastique', 'Bande à poignées', 'Pieds sur la bande, coudes collés au corps', ['Coudes collés au corps', 'Pas d’élan', 'Retour lent'], ['band'], 'band', 'band', MAP['curl'], ['coude'], pose='curl', band=FEET, dr=-2)
ex('bandlat', 'Élévations latérales élastique', 'Bande à poignées', 'Pieds sur la bande, bras légèrement fléchis, monte à hauteur d’épaules', ['Coudes légèrement fléchis', 'Pas plus haut que les épaules', 'Retour lent'], ['band'], 'band', 'band', MAP['lateral'], ['epaule'], pose='lateral', band=FEET)
ex('bandext', 'Extension triceps élastique', 'Bande à poignées', 'Bande sous un pied, poignée derrière la nuque à 2 mains, tends les bras', ['Coudes serrés', 'Coudes vers le plafond', 'Seuls les avant-bras bougent'], ['band'], 'band', 'band', MAP['ohtri'], ['coude'], pose='ohtri', band=[['W1', 'A1']])
ex('bandsquat', 'Squat élastique', 'Bande à poignées', 'Pieds sur la bande, poignées aux épaules, cuisses parallèles au sol', ['Genoux dans l’axe des pieds', 'Talons au sol', 'Buste droit'], ['band'], 'band', 'band', MAP['squat'], ['genou'], pose='bandsquat', band=FEET, inv=1, knee=True)
ex('bandrdl', 'Soulevé de terre élastique', 'Bande à poignées', 'Pieds sur la bande, hanches en arrière, dos plat', ['Dos plat', 'Hanches en arrière', 'Serre les fessiers en haut'], ['band'], 'band', 'band', MAP['rdl'], ['dos'], pose='rdl', band=FEET, back=True)
ex('bandpull', 'Écartés élastique', 'Bande à poignées', 'Bras tendus devant toi, écarte la bande jusqu’à la poitrine', ['Bras presque tendus', 'Serre les omoplates', 'Retour lent'], ['band'], 'band', 'band', {'Épaules': .8, 'Dos': .6}, [], pose='pullapart', band=[['W1', 'W2']])
# --- mini-bande (au-dessus des genoux)
ex('mbbridge', 'Pont fessier mini-bande', 'Mini-bande', 'Mini-bande au-dessus des genoux, pousse les genoux vers l’extérieur en montant', ['Genoux vers l’extérieur', 'Serre les fessiers en haut', 'Pas de cambrure'], ['mini'], 'band', 'mini', {'Fessiers': 1, 'Ischios': .5}, [], pose='bridge', band=KNEES)
ex('mbsquat', 'Squat mini-bande', 'Mini-bande', 'Mini-bande au-dessus des genoux, garde les genoux écartés', ['Genoux vers l’extérieur', 'Talons au sol', 'Buste droit'], ['mini'], 'band', 'mini', {'Quadriceps': .8, 'Fessiers': .9}, ['genou'], pose='bwsquat', band=KNEES, inv=1, knee=True)
# --- poids du corps, chaise, mur, banc plat
ex('squatpdc', 'Squat au poids du corps', 'Poids du corps', 'Bras devant toi, cuisses parallèles au sol, talons au sol', ['Genoux dans l’axe des pieds', 'Talons au sol', 'Buste droit'], [], 'bw', '', {'Quadriceps': 1, 'Fessiers': .7}, ['genou'], pose='bwsquat', inv=1, dr=4, knee=True)
ex('lungebw', 'Fente arrière', 'Poids du corps', 'Grand pas en arrière, genou arrière près du sol', ['Genou avant au-dessus de la cheville', 'Buste droit', 'Repousse sur le talon avant'], [], 'bw', '', MAP['lunge'], ['genou'], pose='lunge', m='uni', knee=True)
ex('sldlbw', 'Soulevé de terre sur une jambe', 'Poids du corps', 'Bascule le buste en tendant la jambe arrière, dos plat', ['Dos plat', 'Hanches à niveau', 'Jambe arrière dans l’axe du dos'], [], 'bw', '', MAP['sldl'], ['hanche'], pose='sldl', m='uni', back=True)
ex('calfbw', 'Mollets debout', 'Poids du corps', 'Sur une marche ou au sol, monte le plus haut possible', ['Monte le plus haut possible', 'Pause en haut', 'Descente lente'], [], 'bw', '', MAP['calf'], ['cheville'], pose='calf', dr=8)
ex('crunchbw', 'Crunch', 'Poids du corps', 'Mains sur la poitrine, enroule le haut du dos', ['Bas du dos au sol', 'Enroule le haut du dos', 'Menton rentré'], [], 'bw', '', MAP['crunch'], [], pose='crunch', dr=4)
ex('superman', 'Superman', 'Poids du corps', 'À plat ventre, décolle bras et jambes, regard vers le sol', ['Regard vers le sol', 'Bras et jambes tendus', 'Pause en haut'], [], 'bw', '', {'Lombaires': 1, 'Dos': .6, 'Fessiers': .5}, [], pose='superman')
ex('chairdip', 'Dips sur chaise', 'Chaise ou banc', 'Mains au bord du siège derrière toi, descends en pliant les coudes', ['Épaules basses', 'Coudes vers l’arrière', 'Dos près du siège'], ['chair|bench'], 'bw', '', {'Triceps': 1, 'Pectoraux': .4, 'Épaules': .3}, ['epaule', 'coude'], pose='chairdip', inv=1)
ex('stepup', 'Montée sur chaise', 'Chaise ou banc stable', 'Pose tout le pied sur le siège, monte en poussant sur la jambe avant', ['Pousse sur le talon', 'Buste droit', 'Descente contrôlée'], ['chair|bench'], 'bw', '', {'Quadriceps': 1, 'Fessiers': .8}, ['genou'], pose='stepup', m='uni', knee=True)
ex('bulgarian', 'Fente bulgare', 'Chaise ou banc', 'Pied arrière posé sur le siège, descends le genou arrière vers le sol', ['Genou avant au-dessus de la cheville', 'Buste droit', 'Repousse sur le talon avant'], ['chair|bench'], 'bw', '', {'Quadriceps': 1, 'Fessiers': .9, 'Ischios': .3}, ['genou'], pose='bulgarian', m='uni', dr=-2, knee=True)
ex('wallsit', 'Chaise contre le mur', 'Mur', 'Dos plaqué au mur, cuisses parallèles au sol', ['Dos plaqué au mur', 'Genoux à 90°', 'Respire'], ['wall'], 'bw', '', {'Quadriceps': 1, 'Fessiers': .4}, ['genou'], pose='wallsit', m='time')
ex('wallpush', 'Pompes contre le mur', 'Mur', 'Mains au mur à hauteur d’épaules, corps gainé', ['Corps gainé', 'Coudes à 45°', 'Poitrine vers le mur'], ['wall'], 'bw', '', {'Pectoraux': 1, 'Triceps': .5, 'Épaules': .3}, [], pose='wallpush', inv=1, dr=4)
ex('pike', 'Pompes en pike', 'Poids du corps', 'Hanches hautes, descends la tête vers le sol entre les mains', ['Hanches hautes', 'Coudes vers l’arrière', 'Tête entre les mains'], [], 'bw', '', {'Épaules': 1, 'Triceps': .5}, ['epaule'], pose='pike', inv=1, dr=-3)
ex('benchpress', 'Développé couché haltères', '2 haltères', 'Allongé sur le banc, pieds au sol, coudes à 45°', ['Omoplates serrées', 'Coudes à 45°', 'Pousse vers le plafond'], ['db', 'bench'], 'kg', 'db', MAP['floor'], ['epaule'], pose='benchpress', inv=1)

# --- variantes de séance : UP = meilleure option si le matériel est là ; ALT = remplacements dans l'ordre
d['UP'] = {'floor': ['benchpress'], 'bridge': ['mbbridge']}
d['ALT'] = {
 'row1': ['kbrow', 'bandrow', 'superman'], 'row2': ['kbrow', 'bandrow', 'superman'],
 'floor': ['kbfloor', 'pushup', 'incline', 'wallpush'], 'benchpress': ['floor', 'kbfloor', 'pushup', 'incline', 'wallpush'],
 'pull': ['kbpull', 'bandpull', 'superman'], 'revfly': ['bandpull', 'superman'],
 'ohtri': ['kbohtri', 'bandext', 'chairdip', 'incline'], 'skull': ['kbohtri', 'bandext', 'chairdip', 'incline'],
 'hammer': ['kbcurl', 'bandcurl'], 'curl': ['kbcurl', 'bandcurl'],
 'crunch': ['crunchbw'], 'dbugw': ['deadbug'],
 'squat': ['kbgoblet', 'bandsquat', 'mbsquat', 'squatpdc', 'wallsit', 'bridge'],
 'lunge': ['kblunge', 'bulgarian', 'lungebw', 'bridge'], 'fwdlunge': ['kblunge', 'stepup', 'lungebw', 'bridge'],
 'rdl': ['kbrdl', 'bandrdl', 'sldlbw', 'bridge'], 'sldl': ['kbrdl', 'bandrdl', 'sldlbw', 'bridge'],
 'calf': ['kbcalf', 'calfbw'], 'calf1': ['calfbw'],
 'press': ['kbpress', 'bandpress', 'pike', 'wallpush'], 'lateral': ['bandlat'],
 'pushup': ['incline', 'wallpush'], 'incline': ['wallpush'], 'mbbridge': ['bridge'],
}

# --- nouvelles poses (repère 200 × 200, sol à y = 180, vue de profil tournée vers +x)
def cp(src, **kw):
    o = copy.deepcopy(PO[src]); o.update(kw); return o
PO['kbrdl'] = cp('rdl', db=['W1'])
PO['kbpress'] = cp('press', db=['W1'])
PO['kbfloor'] = cp('floor', db=['W1'])
PO['kbcurl'] = cp('curl', db=['W1'])
PO['kbcalf'] = cp('calf', db=['W1'])
PO['kblunge'] = cp('lunge', db=['W1'])
for k in ('a', 'b'): PO['kblunge'][k].update({'E1': [103, 86], 'W1': [118, 80], 'E2': [101, 86], 'W2': [116, 80]} if k == 'a' else {'E1': [76, 118], 'W1': [91, 112], 'E2': [74, 118], 'W2': [89, 112]})
PO['kblunge']['kf'][0]['p'].update({'E1': [95, 90], 'W1': [110, 84], 'E2': [93, 90], 'W2': [108, 84]})
PO['bandsquat'] = cp('squat')
for k, dy in (('a', 0), ('b', 40)):
    PO['bandsquat'][k].update({'E1': [110, 76 + dy], 'W1': [108, 58 + dy], 'E2': [108, 76 + dy], 'W2': [106, 58 + dy]})
PO['bandsquat']['kf'][0]['p'].update({'E1': [113, 96], 'W1': [111, 78], 'E2': [111, 96], 'W2': [109, 78]})
PO['bandsquat'].pop('db', None)
STAND = {'H': [100, 38], 'N': [100, 56], 'P': [100, 104], 'K1': [101, 142], 'A1': [101, 180], 'K2': [99, 142], 'A2': [99, 180]}
PO['pullapart'] = {'cam': 0.5, 'lat': [[2, 2], [22, 44]], 'a': dict(STAND, E1=[124, 58], W1=[148, 60], E2=[122, 58], W2=[146, 60]),
                   'b': dict(STAND, E1=[100, 58], W1=[100, 60], E2=[98, 58], W2=[98, 60]), '__d': 1}
PO['superman'] = {'a': {'H': [40, 170], 'N': [54, 172], 'P': [104, 174], 'K1': [140, 175], 'A1': [176, 176], 'K2': [138, 175], 'A2': [174, 176], 'E1': [30, 173], 'W1': [7, 174], 'E2': [28, 173], 'W2': [5, 174]},
                  'b': {'H': [38, 160], 'N': [53, 165], 'P': [104, 174], 'K1': [140, 172], 'A1': [175, 164], 'K2': [138, 172], 'A2': [173, 164], 'E1': [30, 160], 'W1': [8, 154], 'E2': [28, 160], 'W2': [6, 154]}, '__d': 1}
PO['chairdip'] = {'cam': 1.2, 'prop': {'t': 'chair', 'x': 58},
                  'a': {'H': [80, 70], 'N': [80, 88], 'P': [84, 136], 'K1': [121, 140], 'A1': [132, 178], 'K2': [119, 140], 'A2': [130, 178], 'E1': [78, 112], 'W1': [76, 136], 'E2': [76, 112], 'W2': [74, 136]},
                  'b': {'H': [81, 90], 'N': [80, 108], 'P': [84, 156], 'K1': [118, 152], 'A1': [132, 178], 'K2': [116, 152], 'A2': [130, 178], 'E1': [58, 120], 'W1': [76, 136], 'E2': [56, 120], 'W2': [74, 136]}, '__d': 1}
PO['stepup'] = {'cam': 1.3, 'free': True, 'prop': {'t': 'chair', 'x': 128},
                'a': {'H': [106, 40], 'N': [104, 58], 'P': [97, 106], 'K1': [131, 100], 'A1': [124, 135], 'K2': [98, 143], 'A2': [98, 180], 'E1': [104, 82], 'W1': [104, 106], 'E2': [102, 82], 'W2': [102, 106]},
                'b': {'H': [124, -7], 'N': [124, 11], 'P': [122, 59], 'K1': [123, 97], 'A1': [124, 135], 'K2': [119, 97], 'A2': [113, 133], 'E1': [124, 35], 'W1': [124, 59], 'E2': [122, 35], 'W2': [122, 59]},
                'kf': [{'t': 0.5, 'p': {'H': [118, 18], 'N': [116, 36], 'P': [110, 84], 'K1': [130, 104], 'A1': [124, 135], 'K2': [108, 120], 'A2': [104, 156], 'E1': [116, 60], 'W1': [116, 84], 'E2': [114, 60], 'W2': [114, 84]}}], '__d': 1}
PO['bulgarian'] = {'cam': 1.35, 'prop': {'t': 'chair', 'x': 52},
                   'a': {'H': [112, 38], 'N': [112, 56], 'P': [110, 104], 'K1': [126, 140], 'A1': [130, 180], 'K2': [90, 138], 'A2': [66, 135], 'E1': [112, 80], 'W1': [112, 104], 'E2': [110, 80], 'W2': [110, 104]},
                   'b': {'H': [109, 68], 'N': [108, 86], 'P': [106, 134], 'K1': [141, 140], 'A1': [131, 178], 'K2': [96, 168], 'A2': [66, 136], 'E1': [108, 110], 'W1': [108, 134], 'E2': [106, 110], 'W2': [106, 134]}, '__d': 1}
PO['wallsit'] = {'cam': 1.25, 'stat': True, 'prop': {'t': 'wall', 'x': 56},
                 'a': {'H': [66, 72], 'N': [66, 90], 'P': [70, 138], 'K1': [108, 138], 'A1': [108, 178], 'K2': [106, 138], 'A2': [106, 178], 'E1': [82, 110], 'W1': [100, 128], 'E2': [80, 110], 'W2': [98, 128]},
                 'b': {'H': [66, 72], 'N': [66, 90], 'P': [70, 138], 'K1': [108, 138], 'A1': [108, 178], 'K2': [106, 138], 'A2': [106, 178], 'E1': [82, 110], 'W1': [100, 128], 'E2': [80, 110], 'W2': [98, 128]}, '__d': 1}
PO['wallpush'] = {'cam': 1.2, 'prop': {'t': 'wall', 'x': 194},
                  'a': {'H': [150, 51], 'N': [142, 67], 'P': [122, 111], 'K1': [106, 145], 'A1': [92, 180], 'K2': [104, 145], 'A2': [90, 180], 'E1': [166, 70], 'W1': [189, 72], 'E2': [164, 70], 'W2': [187, 72]},
                  'b': {'H': [172, 60], 'N': [161, 76], 'P': [133, 117], 'K1': [112, 149], 'A1': [92, 180], 'K2': [110, 149], 'A2': [90, 180], 'E1': [168, 98], 'W1': [189, 74], 'E2': [166, 98], 'W2': [187, 74]}, '__d': 1}
PO['pike'] = {'a': {'H': [62, 158], 'N': [72, 144], 'P': [102, 106], 'K1': [122, 142], 'A1': [142, 178], 'K2': [120, 142], 'A2': [140, 178], 'E1': [66, 162], 'W1': [60, 180], 'E2': [64, 162], 'W2': [58, 180]},
              'b': {'H': [58, 174], 'N': [70, 160], 'P': [104, 110], 'K1': [123, 144], 'A1': [142, 178], 'K2': [121, 144], 'A2': [140, 178], 'E1': [80, 168], 'W1': [60, 180], 'E2': [78, 168], 'W2': [58, 180]}, '__d': 1}
fl = PO['floor']
def lift(pts):  # allongé sur un banc de 43 cm : buste et bras montent, pieds au sol
    o = {}
    for k, v in pts.items():
        o[k] = [v[0], v[1] - 43] if k in ('H', 'N', 'P', 'E1', 'W1', 'E2', 'W2') else v
    o['P'] = [118, 127]; o['K1'] = [150, 132]; o['A1'] = [154, 178]; o['K2'] = [148, 132]; o['A2'] = [152, 178]
    return o
PO['benchpress'] = {'cam': 1.15, 'prop': {'t': 'bench', 'x': 70}, 'a': lift(fl['a']), 'b': lift(fl['b']), 'db': ['W1', 'W2'], '__d': 1}
for k in ('superman', 'pike'): 
    if k not in d['FLIPX']: d['FLIPX'].append(k)
for k in ('kbrdl', 'kblunge'):
    if k not in d['TWOHAND']: d['TWOHAND'].append(k)
for k, c in {'superman': ['Regard vers le sol', 'Bras et jambes tendus', 'Pause en haut'], 'chairdip': ['Épaules basses', 'Coudes vers l’arrière', 'Dos près du siège'],
             'wallsit': ['Dos plaqué au mur', 'Genoux à 90°', 'Respire'], 'bulgarian': ['Genou avant au-dessus de la cheville', 'Buste droit', 'Repousse sur le talon']}.items():
    CUEX[k] = {'back': 0, 'knee': 1 if k in ('bulgarian', 'wallsit') else 0, 't': c}
d['EQ'] = [
 ['Poids libres', [['db', 'Haltères', 1], ['kb', 'Kettlebell', 1], ['barbell', 'Barre et disques', 0]]],
 ['Banc', [['bench', 'Banc de musculation', 1], ['incbench', 'Le banc est inclinable', 0]]],
 ['Barres', [['pullup', 'Barre de traction', 0], ['dips', 'Barres à dips', 0]]],
 ['Élastiques', [['band', 'Bande à poignées', 1], ['mini', 'Mini-bande', 1]]],
 ['Maison', [['chair', 'Chaise', 1], ['wall', 'Mur', 1], ['mat', 'Tapis', 1], ['step', 'Step ou marche', 0]]],
 ['Abdos et récupération', [['abwheel', 'Roue abdominale', 0], ['rope', 'Corde à sauter', 0], ['roller', 'Rouleau de massage', 0]]],
]
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))
print(len(EX), 'exercices,', len(PO), 'poses')

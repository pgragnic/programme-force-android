# Corrections mouvement par mouvement des poses 3D (appliquées après convert.py). Chaque correction cite le retour de Philippe.
import json, sys, numpy as np
def nrm(v): v = np.asarray(v, float); return (v / np.linalg.norm(v)).round(3).tolist()
def fix(P3):
    # bird dog : main du bras tendu paume vers le bas (« Pour le bird dog la main droite être paume vers le bas »)
    for k in P3['birddog']['k']:
        if k['t'] > 0: k['U1' if k['W1'][1] > 20 else 'U2'] = [0, -1, 0]
        for s in '12':
            if k['W' + s][1] < 8: k['U' + s] = [0, -1, 0]
    # étirement triceps : main derrière la tête, coude levé près de la tête (revue de tous les mouvements)
    for k in P3['sttri']['k']: k['W1'] = [4, 134, -14]; k['E1'] = [14, 158, -2]
    return P3
if __name__ == '__main__':
    P3 = json.load(open(sys.argv[1])); json.dump(fix(P3), open(sys.argv[1], 'w'), ensure_ascii=False, separators=(',', ':')); print('ok')

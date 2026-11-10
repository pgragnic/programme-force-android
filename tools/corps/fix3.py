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
    # pont fessier unilatéral : bras à plat paumes au sol, jambe libre tendue dans le prolongement de la cuisse (genoux à la même
    # hauteur), pied tendu vers le bas (Philippe, capture du 10 novembre : « Oui et pied vers le bas »)
    for k in P3['bridge']['k']:
        up = k['t'] >= .5
        for s in '12': k['U' + s] = [0, -1, 0]; k['c' + s] = 0.05; k['W' + s][1] = 2.5; k['E' + s][1] = 4.0
        K1 = np.array(k['K1']); k['K2'] = [-K1[0], K1[1], K1[2]]
        hip = np.array([-9.0, k['P'][1], k['P'][2]]); d = (K1 - np.array([9.0, k['P'][1], k['P'][2]])); d = d / np.linalg.norm(d)
        A = np.array(k['K2']) + d * 31.4; k['A2'] = A.round(2).tolist()
        f = np.array([0, -0.55, 0.83]) if up else np.array([0, -0.2, 0.98])
        k['T2'] = (A + f * 14.5).round(2).tolist()
    # pont fessier à deux jambes (mini-bande) : il prenait la pose unilatérale ; les deux pieds au sol
    import copy
    b2 = copy.deepcopy(P3['bridge'])
    for k in b2['k']:
        for j in 'KAT': v = k[j + '1']; k[j + '2'] = [-v[0], v[1], v[2]]
    P3['bridge2'] = b2
    return P3
if __name__ == '__main__':
    P3 = json.load(open(sys.argv[1])); json.dump(fix(P3), open(sys.argv[1], 'w'), ensure_ascii=False, separators=(',', ':')); print('ok')

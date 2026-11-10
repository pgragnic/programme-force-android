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
    # personnage « en lévitation » (Philippe : « Et ça donne l'impression que le bonhomme est en lévitation », « Pour le gainage
    # planche aussi d'ailleurs ») : le haut du corps et les bras descendus jusqu'au sol, calculés sur le maillage déformé
    # étirements d'un seul côté : l'appli change de côté à mi-temps (Philippe : « Étirement quadriceps n'est que d'un côté »)
    for id in ('stquad', 'sttri', 'stlat'): P3[id]['sides'] = True
    FEET = ['footR', 'footL', 'toesR', 'toesL']
    for id in ('bridge', 'bridge2'):
        for k in P3[id]['k']:
            ref = ['footR', 'toesR']   # pied d'appui
            if k['t'] >= .5:   # en haut : bassin un peu plus bas que les genoux, omoplates et tête au sol
                k['P'] = [0.0, 26.0, k['P'][2]]; k['N'] = [0.0, -12.0, k['N'][2]]; k['H'] = [0.0, -6.0, k['H'][2]]
            else: touch(k, ['P', 'N', 'H', 'S'], ['hips', 'chest'], ref)
            touch(k, ['E1', 'E2', 'W1', 'W2'], ['farmR', 'farmL', 'handR', 'handL'], ref)
            # mains à plat : poignet à la hauteur du coude, doigts vers les pieds, paume au sol
            for s in '12': k['W' + s][1] = k['E' + s][1]; k['F' + s] = [k['W' + s][0], k['W' + s][1], k['W' + s][2] + 9]
            touch(k, ['W1', 'W2', 'F1', 'F2'], ['handR', 'handL', 'finAR', 'finAL', 'finBR', 'finBL'], ref, 0.6)
    # planche : coudes sous les épaules, avant-bras à plat au sol, épaules plus basses (corps aligné des épaules aux chevilles)
    for k in P3['plank']['k']:
        for x in 'NH': k[x] = [k[x][0], k[x][1] - 12, k[x][2]]
        k['P'] = [k['P'][0], k['P'][1] - 6, k['P'][2]]
        for s, sx in (('1', 1), ('2', -1)): k['E' + s] = [19.24 * sx, -4.0, 26.0]; k['W' + s] = [18.24 * sx, -4.0, 50.0]
    return P3

_B = None
def touch(k, keys, regions, ref, target=0.3):
    """Abaisse les points « keys » jusqu'à ce que les « regions » soient au niveau du sol, c'est-à-dire à la hauteur de l'appui « ref »."""
    global _B
    import rt
    if _B is None: _B = rt.Body(__file__.replace('fix3.py', 'corps2.bin'))
    g = _B.G[0]; nm = [b['n'] for b in _B.B]
    def mask(rs): return np.isin(g['bi'][:, 0], [nm.index(r) for r in rs]) & (g['bw'][:, 0] > .5)
    m, mr = mask(regions), mask(ref)
    base = {kk: list(k[kk]) for kk in keys if kk in k}
    def gap(d):
        Q = {kk: v for kk, v in k.items() if kk != 't'}
        for kk, v in base.items(): Q[kk] = [v[0], v[1] - d, v[2]]
        V = _B.skin(Q)[0][0][0]; return float(V[m][:, 1].min() - V[mr][:, 1].min())
    if gap(0) <= target: return
    lo, hi = 0.0, 60.0
    for _ in range(20):
        mid = (lo + hi) / 2
        if gap(mid) > target: lo = mid
        else: hi = mid
    for kk, v in base.items(): k[kk] = [v[0], round(v[1] - hi, 2), v[2]]
if __name__ == '__main__':
    P3 = json.load(open(sys.argv[1])); json.dump(fix(P3), open(sys.argv[1], 'w'), ensure_ascii=False, separators=(',', ':')); print('ok')

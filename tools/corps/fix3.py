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
    # rowings penchés : bras tendus à la verticale sous l'épaule en bas du mouvement (Philippe, capture du rowing penché deux bras :
    # « Le bras n'est pas droit à la fin du mouvement »)
    for id, sides in (('row2', '12'), ('bbrow', '12'), ('row1', '2')):   # rowing un bras : c'est la main gauche (W2) qui tient l'haltère
        for k in P3[id]['k']:
            if k['t'] < .5: hang(k, sides)
    # pompes (classiques, sur les genoux) : bras tendus en haut du mouvement (même retour que pour le rowing, capture des pompes
    # sur les genoux) ; le buste remonte pour que les mains restent au sol, les positions intermédiaires sont recalculées
    for id, ref in (('pushup', ['toesR', 'toesL']), ('incline', ['shinR', 'shinL'])):
        ks = P3[id]['k']; k0 = ks[0]; straighten(k0, '12', ref)
        k1 = ks[-1]
        for s in '12': k1['W' + s] = list(k0['W' + s]); k1['F' + s] = list(k0['F' + s])   # mains fixes au sol
        for k in ks[1:-1]:
            u = k['t']
            for key in list(k0):
                if key in ('t',) or not isinstance(k0[key], list) or key not in k1: continue
                k[key] = [round(a + (b - a) * u, 2) for a, b in zip(k0[key], k1[key])]
        for k in ks[1:]: elbows(k, '12')   # coudes à 45° du corps, vers l'arrière, quand les bras plient
    # haltères qui rentraient dans le corps (Philippe, capture du curl biceps : « Les haltères rentrent dans le corps ») : les mains
    # s'écartent juste assez pour qu'aucun disque ne traverse le corps sur tout le mouvement (+ 1 cm de marge)
    for id in ('curl', 'hammer', 'rdl', 'sldl', 'calf', 'lunge', 'fwdlunge', 'row1', 'row2', 'lateral'):
        if id in P3: clear_db(P3, id)
    # planche : coudes sous les épaules, avant-bras à plat au sol, épaules plus basses (corps aligné des épaules aux chevilles)
    for k in P3['plank']['k']:
        for x in 'NH': k[x] = [k[x][0], k[x][1] - 12, k[x][2]]
        k['P'] = [k['P'][0], k['P'][1] - 6, k['P'][2]]
        for s, sx in (('1', 1), ('2', -1)): k['E' + s] = [19.24 * sx, -4.0, 26.0]; k['W' + s] = [18.24 * sx, -4.0, 50.0]
    return P3

_B = None
def hang(k, sides):
    """Bras pendants tendus : coude et poignet sur la verticale de l'épaule (position réelle de l'épaule calculée par le moteur)."""
    global _B
    import rt
    if _B is None: _B = rt.Body(__file__.replace('fix3.py', 'corps2.bin'))
    nm = [b['n'] for b in _B.B]; R, T = _B.solve({kk: v for kk, v in k.items() if kk != 't'})
    for s in sides:
        sh = T[nm.index('uarm' + ('R' if s == '1' else 'L'))]
        k['E' + s] = [round(float(sh[0]), 2), round(float(sh[1] - 25), 2), round(float(sh[2] + 0.5), 2)]
        k['W' + s] = [round(float(sh[0]), 2), round(float(sh[1] - 50), 2), round(float(sh[2] + 1.0), 2)]

def db_pen(P3, id, dx, ax_i=0):
    """Nombre de points des disques d'haltère qui traversent le corps (mains écartées de dx), sur 5 instants du mouvement."""
    global _B
    import rt
    from scipy.spatial import cKDTree
    if _B is None: _B = rt.Body(__file__.replace('fix3.py', 'corps2.bin'))
    nm = [b['n'] for b in _B.B]; Hh = _B.hdr['hand']; tot = 0
    for t in (0, .25, .5, .75, 1):
        Q = {k: v for k, v in rt.at(P3[id], t).items() if k != 't'}
        for s, sx in [('1', 1) if w == 'W1' else ('2', -1) for w in P3[id].get('db', ['W1', 'W2'])]:
            for kk, f in (('W', 1.0), ('E', .5)):
                if kk + s in Q: v = list(Q[kk + s]); v[ax_i] += (sx if ax_i == 0 else 1) * dx * f; Q[kk + s] = v
        out, R, T = _B.skin(Q); V, N = out[0][0], out[0][1]; tr = cKDTree(V)
        for sd in [('R' if w == 'W1' else 'L') for w in P3[id].get('db', ['W1', 'W2'])]:
            i = nm.index('hand' + sd); g = T[i] + R[i] @ np.array(Hh[sd]['g']); ax = R[i] @ np.array(Hh[sd]['l']); ax /= np.linalg.norm(ax)
            u = np.cross(ax, [0, 1, 0]); u = u / np.linalg.norm(u) if np.linalg.norm(u) > .1 else np.cross(ax, [1, 0, 0]); w = np.cross(ax, u)
            pts = np.array([g + ax * o + (u * np.cos(a) + w * np.sin(a)) * r for o in (-10, -8, -6, 6, 8, 10)
                            for a in np.linspace(0, 2 * np.pi, 12, endpoint=False) for r in (3, 6.5)])
            d, j = tr.query(pts); tot += int(((((pts - V[j]) * N[j]).sum(1) < 0) & (d < 4)).sum())
    return tot

def clear_db(P3, id, ax_i=0):
    dx = 0
    while dx < 16 and db_pen(P3, id, dx, ax_i) > 0: dx += 1
    if dx == 0: return
    dx += 1
    hs = [('1', 1) if w == 'W1' else ('2', -1) for w in P3[id].get('db', ['W1', 'W2'])]
    for k in P3[id]['k']:
        for s, sx in hs:
            for kk, f in (('W', 1.0), ('E', .5), ('F', 1.0)):
                if kk + s in k: v = list(k[kk + s]); v[ax_i] = round(v[ax_i] + (sx if ax_i == 0 else 1) * dx * f, 2); k[kk + s] = v
    print('haltères', 'écartés' if ax_i == 0 else 'levés', id, dx, 'cm')

def elbows(k, sides):
    """Coude placé d'après les longueurs réelles du bras : entre l'épaule et la main, décalé vers l'extérieur, l'arrière et le haut."""
    global _B
    import rt
    if _B is None: _B = rt.Body(__file__.replace('fix3.py', 'corps2.bin'))
    nm = [b['n'] for b in _B.B]; R, T = _B.solve({kk: v for kk, v in k.items() if kk != 't'})
    for s in sides:
        sd = 'R' if s == '1' else 'L'; sx = 1 if s == '1' else -1
        bu, bf = _B.B[nm.index('uarm' + sd)], _B.B[nm.index('farm' + sd)]; bh = _B.B[nm.index('hand' + sd)]
        L1 = np.linalg.norm(bf['a'] - bu['a']); L2 = np.linalg.norm(bh['a'] - bf['a'])
        sh = T[nm.index('uarm' + sd)]; W = np.array(k['W' + s], float); d = W - sh; D = np.linalg.norm(d)
        if D >= L1 + L2 - .5: k['E' + s] = (sh + d * L1 / (L1 + L2)).round(2).tolist(); continue
        a = (L1 * L1 - L2 * L2 + D * D) / (2 * D); h = np.sqrt(max(L1 * L1 - a * a, 0)); u = d / D
        o = np.array([0.45 * sx, 0.35, -0.8]); o -= u * (o @ u); o /= np.linalg.norm(o)
        k['E' + s] = (sh + u * a + o * h).round(2).tolist()

def straighten(k, sides, ref, target=0.4):
    """Bras d'appui tendus : coude sur la droite épaule → main ; le buste (P, N, H) monte ou descend pour que les mains touchent le sol
    à la hauteur de l'appui « ref » (pieds ou genoux)."""
    global _B
    import rt
    if _B is None: _B = rt.Body(__file__.replace('fix3.py', 'corps2.bin'))
    g = _B.G[0]; nm = [b['n'] for b in _B.B]
    def mask(rs): return np.isin(g['bi'][:, 0], [nm.index(r) for r in rs]) & (g['bw'][:, 0] > .5)
    mh, mr = mask(['handR', 'handL', 'finAR', 'finAL', 'finBR', 'finBL']), mask(ref)
    base = {kk: list(k[kk]) for kk in ('P', 'N', 'H', 'S') if kk in k}
    # mains sous les épaules (un peu plus larges), doigts vers l'avant
    R0, T0 = _B.solve({kk: v for kk, v in k.items() if kk != 't'})
    for s in sides:
        sh = T0[nm.index('uarm' + ('R' if s == '1' else 'L'))]; sx = 1 if s == '1' else -1
        k['W' + s] = [round(float(sh[0]) + 3.0 * sx, 2), k['W' + s][1], round(float(sh[2]) + 2.0, 2)]
        k['F' + s] = [k['W' + s][0], k['W' + s][1], k['W' + s][2] + 9]
    def setq(d):
        Q = {kk: v for kk, v in k.items() if kk != 't'}
        for kk, v in base.items(): Q[kk] = [v[0], v[1] + d, v[2]]
        R, T = _B.solve(Q)
        for s in sides:
            sh = T[nm.index('uarm' + ('R' if s == '1' else 'L'))]; W = np.array(Q['W' + s], float)
            Q['E' + s] = (sh + (W - sh) * 0.5).round(2).tolist()
        return Q
    def gap(d):
        V = _B.skin(setq(d))[0][0][0]; return float(V[mh][:, 1].min() - V[mr][:, 1].min())
    lo, hi = -10.0, 40.0
    for _ in range(22):
        mid = (lo + hi) / 2
        if gap(mid) > target: hi = mid
        else: lo = mid
    Q = setq(lo)
    for kk in list(base) + ['E' + s for s in sides]: k[kk] = Q[kk]

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

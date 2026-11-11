# Corrections issues du contrôle de tous les exercices (Philippe, 11 novembre : « pourrais-tu vérifier pour tous les exercices que
# les mouvements sont bien cohérents »). Contrôle : tools/corps/audit.py. À appliquer une seule fois sur poses3.json (après fix3.py).
import json, sys, numpy as np, rt
B = rt.Body(__file__.replace('fix4.py', 'corps2.bin')); NM = [b['n'] for b in B.B]; G = B.G[0]; PB = G['bi'][:, 0]
APP_DY = .7   # l'appli pose le corps 0,7 cm au-dessus du sol (Body.skin)

def Q_(k): return {kk: v for kk, v in k.items() if kk != 't'}
def skin(P3, id, k):
    Q = Q_(k); lift = max(0, min(Q['A1'][1], Q['A2'][1]) - 2) if P3[id].get('free') else 0
    out, R, T = B.skin(Q, lift); return out[0][0] + [0, APP_DY, 0], R, T
def two_bone(root, tgt, L1, L2, pole):
    d = tgt - root; D = np.linalg.norm(d); u = d / D
    if D >= L1 + L2 - .3: return root + u * L1 * D / (L1 + L2)
    a = (L1 * L1 - L2 * L2 + D * D) / (2 * D); h = np.sqrt(max(L1 * L1 - a * a, 0))
    o = pole - u * (pole @ u); o /= np.linalg.norm(o); return root + u * a + o * h
def knee(k, s):
    """Genou placé d'après les longueurs réelles de la jambe, dans le plan du genou d'origine : la cheville arrive exactement sur A."""
    sd = 'R' if s == '1' else 'L'; R, T = B.solve(Q_(k))
    th, sh, ft = (B.B[NM.index(n + sd)] for n in ('thgh', 'shin', 'foot'))
    L1 = np.linalg.norm(sh['a'] - th['a']); L2 = np.linalg.norm(ft['a'] - sh['a'])
    hip = T[NM.index('thgh' + sd)]; A = np.array(k['A' + s], float); K0 = np.array(k['K' + s], float)
    k['K' + s] = two_bone(hip, A, L1, L2, K0 - (hip + A) / 2).round(2).tolist()
def elbow(k, s):
    sd = 'R' if s == '1' else 'L'; R, T = B.solve(Q_(k))
    ua, fa, hd = (B.B[NM.index(n + sd)] for n in ('uarm', 'farm', 'hand'))
    L1 = np.linalg.norm(fa['a'] - ua['a']); L2 = np.linalg.norm(hd['a'] - fa['a'])
    sh = T[NM.index('uarm' + sd)]; W = np.array(k['W' + s], float); E0 = np.array(k['E' + s], float)
    k['E' + s] = two_bone(sh, W, L1, L2, E0 - (sh + W) / 2).round(2).tolist()
def lowest(V, bones): return V[np.isin(PB, [NM.index(b) for b in bones]), 1].min()
def shift(k, keys, dy):
    for x in keys:
        if x in k: k[x] = [k[x][0], round(k[x][1] + dy, 2), k[x][2]]

def fix(P3):
    # dips sur chaise : mains fixes sur le bord de l'assise (elles flottaient au-dessus en bas du mouvement)
    p = P3['chairdip']; top = 45.5
    for k in p['k']:
        for _ in range(4):
            for s in '12': elbow(k, s)
            V, _, _ = skin(P3, 'chairdip', k); dy = top + .2 - lowest(V, ['handR', 'handL'])
            if abs(dy) < .2: break
            for s in '12': shift(k, ['W' + s, 'F' + s], dy)
    # squat bulgare : pied arrière posé sur l'assise et immobile (il s'enfonçait dans la chaise)
    p = P3['bulgarian']; A0 = list(p['k'][0]['A2']); T0 = list(p['k'][0]['T2'])
    for k in p['k']:
        k['A2'] = list(A0); k['T2'] = list(T0)
    for _ in range(4):
        for k in p['k']: knee(k, '2')
        V, _, _ = skin(P3, 'bulgarian', p['k'][0]); dy = top + .3 - lowest(V, ['footL', 'toesL'])
        if abs(dy) < .2: break
        for k in p['k']: shift(k, ['A2', 'T2'], dy)
    # montée sur chaise : pied posé sur l'assise sans s'y enfoncer
    p = P3['stepup']
    for k in p['k']: knee(k, '1')
    for _ in range(4):
        dys = []
        for k in p['k']:
            V, _, _ = skin(P3, 'stepup', k); dys.append(top + .3 - lowest(V, ['footR', 'toesR']))
        dy = max(dys)
        if dy < .2: break
        for k in p['k']: shift(k, ['A1', 'T1'], dy); knee(k, '1')
    # développé couché et incliné : banc à la hauteur du dos (le corps s'y enfonçait)
    for id in ('benchpress', 'inclinepress'):
        V, _, _ = skin(P3, id, P3[id]['k'][0])
        if id == 'benchpress':
            P3[id]['prop']['y'] = round(float(lowest(V, ['hips', 'spine', 'chest'])) - .3, 1)
        else:
            # dossier incliné : on descend le banc jusqu'à ce que le dos et les fesses ne le traversent plus
            d = np.array([0, .7, -.71]); d /= np.linalg.norm(d); n = np.cross([1., 0, 0], d); z = P3[id]['prop'].get('x', 100) - 100
            T_ = V[np.isin(PB, [NM.index(b) for b in ('hips', 'spine', 'chest')]) & (np.abs(V[:, 0]) < 14)]
            best = 0
            for dy in np.arange(0, -20, -.5):
                c = np.array([0, 66 + dy, z - 22]) + n * 3; sd = (T_ - c) @ n; al = (T_ - c) @ d
                seat = (np.abs(T_[:, 2] - (z + 20)) < 22.5) & (T_[:, 1] < 43 + dy)
                if ((sd < -.5) & (np.abs(al) < 35)).sum() == 0 and seat.sum() == 0: best = dy; break
            P3[id]['prop']['y'] = round(43 + best, 1)
        print(id, 'banc : dessus à', P3[id]['prop']['y'], 'cm')
    # rowing barre : écartement des mains constant (la barre ne s'allonge pas)
    for k in P3['bbrow']['k']:
        for s, sx in (('1', 1), ('2', -1)): k['W' + s] = [21 * sx, k['W' + s][1], k['W' + s][2]]; elbow(k, s)
    # rouleau sur le haut du dos : mains derrière la tête, coudes ouverts (au lieu des bras tendus vers le plafond)
    for k in P3['rollback']['k']:
        H = np.array(k['H'], float); N = np.array(k['N'], float)
        for s, sx in (('1', 1), ('2', -1)):
            k['W' + s] = [round(7 * sx, 2), round(H[1] + 4, 2), round(H[2] + 3, 2)]; k['E' + s] = [round(34 * sx, 2), round(N[1] + 10, 2), round(N[2] - 4, 2)]
            k['U' + s] = [-sx * .3, .2, -.93]; k['c' + s] = .5
            elbow(k, s)
    return P3

if __name__ == '__main__':
    P3 = json.load(open(sys.argv[1])); json.dump(fix(P3), open(sys.argv[1], 'w'), ensure_ascii=False, separators=(',', ':')); print('ok')

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

HANDV = np.isin(PB, [NM.index(n + s) for n in ('hand', 'finA', 'finB', 'thmb', 'twst', 'farm') for s in 'RL'])
DX = json.load(open(__file__.replace('tools/corps/fix4.py', 'app/src/main/assets/programme.json')))['EX']
def tool_pen(P3, id, k, tool):
    """Points de l'objet tenu (barre, kettlebell, médecine-ball) qui traversent le corps (mains et avant-bras exclus) et direction pour l'en sortir."""
    from scipy.spatial import cKDTree
    Q = Q_(k); out, R, T = B.skin(Q); V, N = out[0][0][~HANDV], out[0][1][~HANDV]; tr = cKDTree(V)
    Hh = B.hdr['hand']
    def grip(sd): i = NM.index('hand' + sd); return T[i] + R[i] @ np.array(Hh[sd]['g']) + [0, B.dy, 0]
    def bell(g, s_):
        fore = np.array(Q['W' + s_], float) - Q['E' + s_]; fore /= np.linalg.norm(fore)
        off = np.array([0, -1., 0]) if fore[1] < -.3 else (-fore + [0, -1, 0]); off /= np.linalg.norm(off); return g + off * 9
    sph = np.random.default_rng(0).normal(size=(200, 3)); sph /= np.linalg.norm(sph, axis=1, keepdims=True)
    if tool == 'bb':
        a, c = grip('R'), grip('L'); ax = (a - c) / np.linalg.norm(a - c); pts = np.array([(a + c) / 2 + ax * t for t in np.linspace(-60, 60, 121)])
    elif P3[id].get('two'):
        c = (grip('R') + grip('L')) / 2
        if tool == 'kb':
            f = sum((np.array(Q['W' + s_], float) - Q['E' + s_]) / np.linalg.norm(np.array(Q['W' + s_], float) - Q['E' + s_]) for s_ in '12') / 2
            off = np.array([0, -1., 0]) if f[1] < -.3 else (-f + [0, -1, 0]); off /= np.linalg.norm(off); pts = c + off * 9 + sph * 8
        else: pts = c + sph * 11
    else:
        pts = np.concatenate([bell(grip('R' if w == 'W1' else 'L'), w[1]) + sph * 8 for w in P3[id].get('db', [])])
    d, j = tr.query(pts); ins = (((pts - V[j]) * N[j]).sum(1) < 0) & (d < 6)
    return int(ins.sum()), (N[j[ins]].mean(0) if ins.any() else np.zeros(3))

def clear_tools(P3):
    """Barre, kettlebell et médecine-ball hors du corps : les mains s'écartent du corps (direction des normales touchées), coudes recalculés."""
    for id, p in P3.items():
        tools = {DX[e].get('tool') for e in DX if DX[e].get('pose', e) == id} & {'bb', 'kb', 'medball'}
        if not tools or not p.get('db') and 'bb' not in tools or id == 'kbhalo': continue   # halo : la cloche tourne au ras de la tête
        tool = sorted(tools)[0]
        for k in p['k']:
            n0, _ = tool_pen(P3, id, k, tool); moved = 0.
            while moved < 14:
                n, dr = tool_pen(P3, id, k, tool)
                if n <= 1: break
                dr = dr / (np.linalg.norm(dr) + 1e-9)
                if id == 'kbsumo': dr = np.array([0, 0, 1.])   # kettlebell devant le bassin, pas plus bas
                hs = ['1', '2'] if tool in ('bb',) or p.get('two') else [w[1] for w in p.get('db', [])]
                for s_ in hs:
                    for x in ('W', 'F'):
                        if x + s_ in k: k[x + s_] = (np.array(k[x + s_], float) + dr).round(2).tolist()
                    elbow(k, s_)
                moved += 1
            if moved: print(f'{id} t={k["t"]} : {tool} sorti du corps ({n0} → {tool_pen(P3, id, k, tool)[0]} points, mains déplacées de {moved:.0f} cm)')

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
    # traction : à la montée, le corps passe derrière la barre (la barre traversait le cou) ; mains fixes sur la barre
    ARM = np.isin(PB, [NM.index(n + s_) for n in ('clav', 'uarm', 'farm', 'twst', 'hand', 'finA', 'finB', 'thmb') for s_ in 'RL'])
    BODYK = ('P', 'N', 'H', 'S', 'K1', 'K2', 'A1', 'A2', 'T1', 'T2', 'TT1', 'TT2')
    for k in P3['pullup']['k']:
        V, R, T = skin(P3, 'pullup', k); Hh = B.hdr['hand']
        g = [T[NM.index('hand' + sd)] + R[NM.index('hand' + sd)] @ np.array(Hh[sd]['g']) for sd in 'RL']
        by = (g[0][1] + g[1][1]) / 2 + B.dy + APP_DY + 1.5; bz = (g[0][2] + g[1][2]) / 2
        m = ~ARM & (np.abs(V[:, 1] - by) < 4) & (np.abs(V[:, 0]) < 16)
        if m.any():
            dz = min(0., bz - 3.5 - V[m, 2].max())
            if dz < 0:
                for x in BODYK:
                    if x in k: k[x] = [k[x][0], k[x][1], round(k[x][2] + dz, 2)]
                for s_ in '12': elbow(k, s_)
                print('traction t=%.3f : corps reculé de %.1f cm' % (k['t'], -dz))
    # début de la montée : recul progressif (pas de saut entre deux images clés)
    for k in P3['pullup']['k']:
        if 0 < k['t'] < .375:
            dz = -16.5 * k['t'] / .375
            for x in BODYK:
                if x in k: k[x] = [k[x][0], k[x][1], round(k[x][2] + dz, 2)]
            for s_ in '12': elbow(k, s_)
    # dips sur chaise : bassin devant le bord de l'assise (il entrait dans la chaise), mains toujours posées sur le bord
    z0 = P3['chairdip']['prop']['x'] - 100; front = z0 + 20
    for k in P3['chairdip']['k']:
        V, _, _ = skin(P3, 'chairdip', k); m = ~ARM & (V[:, 1] > 42) & (V[:, 1] < 46) & (np.abs(V[:, 0]) < 21)
        if m.any() and V[m, 2].min() < front + .5:
            dz = front + .5 - V[m, 2].min()
            for x in BODYK:
                if x in k: k[x] = [k[x][0], k[x][1], round(k[x][2] + dz, 2)]
            for s_ in '12': elbow(k, s_)
            print('dips sur chaise t=%.3f : corps avancé de %.1f cm' % (k['t'], dz))
    clear_tools(P3)
    return P3

if __name__ == '__main__':
    P3 = json.load(open(sys.argv[1])); json.dump(fix(P3), open(sys.argv[1], 'w'), ensure_ascii=False, separators=(',', ':')); print('ok')

def stepup_path(P3):
    """Montée sur chaise : pied d'appui posé sur l'assise tout le long, pied arrière qui passe au-dessus du bord (il traversait l'assise)."""
    p = P3['stepup']; top = 45.5; z0 = p['prop']['x'] - 100
    have = {round(k['t'], 3) for k in p['k']}
    for t in (.125, .25, .375, .625, .75, .875):
        if t not in have:
            k = {'t': t, **{kk: (np.asarray(v).round(3).tolist() if not np.isscalar(v) else v) for kk, v in rt.at(p, t).items()}}
            p['k'].append(k)
    p['k'].sort(key=lambda k: k['t'])
    footL = ['footL', 'toesL']
    for k in p['k']:
        if k['t'] in (.5, .625):   # pied arrière encore devant le bord de l'assise tant qu'il est plus bas qu'elle
            V, _, _ = skin(P3, 'stepup', k); P = V[np.isin(PB, [NM.index(b) for b in footL])]
            dz = (z0 - 20 - 1) - P[:, 2].max()
            if dz < 0:
                for x in ('A2', 'T2', 'K2'): k[x] = [k[x][0], k[x][1], round(k[x][2] + dz, 2)]
                knee(k, '2')
    for k in p['k']:
        if k['t'] == .625:   # puis il monte au-dessus de l'assise avant d'avancer
            V, _, _ = skin(P3, 'stepup', k); P = V[np.isin(PB, [NM.index(b) for b in footL])]
            dy = top + 3 - P[:, 1].min()
            if dy > 0: shift(k, ['A2', 'T2', 'K2'], dy); knee(k, '2')
        if k['t'] in (.75, .875):
            V, _, _ = skin(P3, 'stepup', k); m = np.isin(PB, [NM.index(b) for b in footL]); P = V[m]
            over = (P[:, 2] > z0 - 20) & (P[:, 2] < z0 + 20)
            if over.any() and P[over, 1].min() < top + 1:
                dy = top + 3 - P[over, 1].min()
                shift(k, ['A2', 'T2', 'K2'], dy)
            knee(k, '2')
        for _ in range(4):
            V, _, _ = skin(P3, 'stepup', k); dy = top + .3 - lowest(V, ['footR', 'toesR'])
            if abs(dy) < .2: break
            shift(k, ['A1', 'T1'], dy); knee(k, '1')
    return P3

def bbpress_start(P3):
    """Développé militaire à la barre (Philippe : « Ok pour Développé militaire à la barre : au départ, la barre est un peu haute, vers le
    front plutôt qu'au menton ») : départ barre sous le menton, coudes sous la barre ; à la montée la barre passe devant le visage."""
    p = P3['bbpress']; k0 = p['k'][0]
    for s, sx in (('1', 1), ('2', -1)):
        for x in ('W', 'F'):
            if x + s in k0: k0[x + s] = [k0[x + s][0], round(k0[x + s][1] - 14, 2), k0[x + s][2]]
        k0['E' + s] = [27 * sx, k0['W' + s][1] - 24, 12.0]; elbow(k0, s)
    def clear(k):
        for _ in range(14):
            n, dr = tool_pen(P3, 'bbpress', k, 'bb')
            if n == 0: break
            for s in '12':
                for x in ('W', 'F'):
                    if x + s in k: k[x + s] = [k[x + s][0], k[x + s][1], round(k[x + s][2] + 1, 2)]
                elbow(k, s)
        for s in '12':   # 2 cm de marge devant le visage
            for x in ('W', 'F'):
                if x + s in k: k[x + s] = [k[x + s][0], k[x + s][1], round(k[x + s][2] + 2, 2)]
            elbow(k, s)
    clear(k0)
    have = {round(k['t'], 3) for k in p['k']}
    for t in (.25, .5, .75):
        if t not in have:
            k = {'t': t, **{kk: (np.asarray(v).round(3).tolist() if not np.isscalar(v) else v) for kk, v in rt.at(p, t).items()}}
            p['k'].append(k)
    p['k'].sort(key=lambda k: k['t'])
    for k in p['k']:
        if 0 < k['t'] < 1: clear(k)
    return P3

def kneepush_feet(P3):
    """Pompes sur les genoux (Philippe, captures du 11 novembre : « Les pieds ont un positionnement bizarre ») : pieds détendus dans le
    prolongement des jambes, pointes vers l'arrière (ils étaient repliés vers les tibias)."""
    for k in P3['incline']['k']:
        for s in '12':
            K = np.array(k['K' + s], float); A = np.array(k['A' + s], float); d = (A - K) / np.linalg.norm(A - K)
            f = d + np.array([0, .25, 0]); f /= np.linalg.norm(f)
            k['T' + s] = (A + f * 14.5).round(2).tolist()
    return P3

def halo(P3):
    """Halo kettlebell (Philippe : « Sens du halo ? » puis « Oui ») : la kettlebell fait un vrai tour autour de la tête (devant, côté droit,
    derrière, côté gauche, devant) ; avant, elle passait seulement de l'avant à l'arrière par-dessus la tête. Une répétition = un tour
    (tempo de l'exercice : montée seule), une répétition sur deux en miroir = tour dans l'autre sens."""
    base = dict(P3['kbhalo']['k'][0]); Hd = B.G[0]['bi'][:, 0] == NM.index('head')
    V0 = B.skin(Q_(base))[0][0][0]; hc = V0[Hd].mean(0); dy = B.dy
    for R_ in np.arange(19, 30, 1.):
        ks = []; worst = 0
        for i in range(9):
            th = 2 * np.pi * i / 8; k = json.loads(json.dumps(base)); k['t'] = i / 8
            c = np.array([R_ * np.sin(th), hc[1] + 9 - dy - 3, hc[2] + R_ * np.cos(th)])   # la prise est un peu au-dessus du poignet
            for s, sx in (('1', 1), ('2', -1)):
                k['W' + s] = (c + [4.5 * sx, 0, 0]).round(2).tolist(); k.pop('F' + s, None)
                k['U' + s] = [-sx, 0, 0]; k['c' + s] = .85
                k['E' + s] = [26 * sx, c[1] - 14, c[2] * .5 + 12]; elbow(k, s)
            ks.append(k); P3['kbhalo']['k'] = ks + [dict(x) for x in ks[len(ks):]]
        P3['kbhalo']['k'] = ks
        worst = max(tool_pen(P3, 'kbhalo', k, 'kb')[0] for k in ks)
        if worst <= 1: break
    print('halo : rayon', R_, 'cm, points dans le corps', worst)
    return P3

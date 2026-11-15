# Planche de revue des positions (Philippe, 15 novembre 2026 : « Quel serait la meilleure méthode […] pour que je puisse juger
# des positions des personnages pour chaque exercice ? », puis « Les captures doivent être assez grandes pour être bien visibles sur un téléphone »).
# Rend chaque pose comme dans l'appli (même corps, même angle de caméra, mêmes accessoires) au départ, au milieu et à la fin,
# plus une vue à 90° de la position extrême, en grand (600 × 800). Écrit revue/img/<pose>_<n>.webp et revue/data.json.
# Lancer : cd tools/corps && python3 revue.py [dossier] [poses séparées par des virgules]
import sys, os, json, math, numpy as np, matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
from matplotlib.collections import PolyCollection
os.chdir(os.path.dirname(os.path.abspath(__file__)))
import rt
OUT = sys.argv[1] if len(sys.argv) > 1 else '../../revue'
os.makedirs(f'{OUT}/img', exist_ok=True)
D = json.load(open('../../app/src/main/assets/programme.json')); EX = D['EX']; P3 = json.load(open('poses3.json')); OLD = D['POSES']
b = rt.Body('corps2.bin'); IDX = b.G[0]['idx'].reshape(-1, 3); IDXE = b.G[1]['idx'].reshape(-1, 3)
W, H = 600, 800

def nrm(v): v = np.asarray(v, float); n = np.linalg.norm(v); return v / n if n > 1e-9 else v

# ---------- formes simples (comme Mesh.box / cylinder / sphere de l'appli) ----------
def box_tris(c, s, Rm=None, n=4):
    """boîte centrée c, demi-côtés s (box de l'appli : -1..1 mis à l'échelle), faces subdivisées pour le tri en profondeur"""
    T = []; g = np.linspace(-1, 1, n + 1)
    for ax in range(3):
        for sg in (-1, 1):
            u, v = [a for a in range(3) if a != ax]
            for i in range(n):
                for j in range(n):
                    q = []
                    for a, bb in ((g[i], g[j]), (g[i + 1], g[j]), (g[i + 1], g[j + 1]), (g[i], g[j + 1])):
                        p = np.zeros(3); p[ax] = sg; p[u] = a; p[v] = bb; q.append(p)
                    q = np.array(q) * s
                    if Rm is not None: q = q @ Rm.T
                    nn = np.zeros(3); nn[ax] = sg
                    if Rm is not None: nn = Rm @ nn
                    T.append((q + c, nn))
    return T
def rot_x_to(d):
    return rt.rot_between(np.array([1., 0, 0]), nrm(d))
def cyl_tris(a, c, r, seg=14):
    d = c - a; L = np.linalg.norm(d)
    if L < .5: return []
    Rm = rot_x_to(d); T = []
    for i in range(seg):
        a0, a1 = 2 * math.pi * i / seg, 2 * math.pi * (i + 1) / seg
        p = [np.array([x, r * math.cos(t), r * math.sin(t)]) for x, t in ((0, a0), (L, a0), (L, a1), (0, a1))]
        nn = np.array([0, math.cos((a0 + a1) / 2), math.sin((a0 + a1) / 2)])
        T.append((np.array(p) @ Rm.T + a, Rm @ nn))
    for x, sg in ((0, -1), (L, 1)):   # bouchons
        for i in range(seg):
            a0, a1 = 2 * math.pi * i / seg, 2 * math.pi * (i + 1) / seg
            p = [np.array([x, 0, 0]), np.array([x, r * math.cos(a0), r * math.sin(a0)]), np.array([x, r * math.cos(a1), r * math.sin(a1)])]
            T.append((np.array(p) @ Rm.T + a, Rm @ np.array([sg, 0, 0])))
    return T
def ball_tris(c, s, seg=12):
    s = np.broadcast_to(np.asarray(s, float), (3,)); T = []
    for i in range(seg):
        for j in range(2 * seg):
            pts = []
            for ii, jj in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                t = math.pi * ii / seg; p = 2 * math.pi * jj / (2 * seg)
                pts.append(np.array([math.sin(t) * math.cos(p), math.cos(t), math.sin(t) * math.sin(p)]))
            pts = np.array(pts); T.append((pts * s + c, nrm(pts.mean(0))))
    return T

HANDLE = (.165, .184, .208); DBC = (.36, .40, .46); PROP = (.62, .58, .52); SKIN = np.array([.86, .72, .62])

def objects(id, ex, Q, R, T, dy, nodb=False):
    """accessoires et charges, repris de BodyView.kt"""
    O = []   # (liste de (quad, normale), couleur)
    tool = EX[ex].get('tool') if ex and EX[ex].get('tool') is not None else P3[id].get('tool')   # comme BodyView.kt : exo?.tool ?: poseTool ?: "db"
    if tool is None: tool = '' if nodb else 'db'
    def end(n): i = b.idx[n]; bb = b.B[i]; return T[i] + R[i] @ (bb['b'] - bb['a']) + [0, dy, 0]
    def head(n): return T[b.idx[n]] + [0, dy, 0]
    J = {'W1': head('handR'), 'W2': head('handL'), 'E1': head('farmR'), 'E2': head('farmL'), 'A1': head('footR'), 'A2': head('footL'),
         'K1': head('shinR'), 'K2': head('shinL'), 'Pc': head('hips'), 'Nc': (head('uarmR') + head('uarmL')) / 2}
    def grip(sd): i = b.idx['hand' + sd]; return T[i] + R[i] @ b.grip0[sd] + [0, dy, 0]
    def hax(sd): return nrm(R[b.idx['hand' + sd]] @ b.__dict__['hl' + sd])
    def palmw(sd): return nrm(R[b.idx['hand' + sd]] @ np.array(b.hdr['hand'][sd]['n']))
    def seg(a, c, r, col): O.append((cyl_tris(a, c, r), col))
    dbs = P3[id].get('db', [])
    if tool in ('db', 'kb') and not nodb:
        def dumbbell(p, Rm):
            for ox, L, r in ((0, 12, 1.6), (8, 4.5, 6.5), (-8, 4.5, 6.5)):
                d = Rm @ np.array([1., 0, 0]); c = p + d * ox
                O.append((cyl_tris(c - d * L / 2, c + d * L / 2, r), HANDLE if ox == 0 else DBC))
        def kettlebell(p, fore, hx, palm=None):
            # cloche sous la main quand le bras pend ; bras plié ou levé (curl, rack, développé) : posée sur le dos du poignet et de l'avant-bras
            down = np.array([0, -1., 0])
            if fore[1] < -.3: c = p + down * 9
            elif palm is not None: c = p - palm * 10.5 - fore * 2
            else: c = p + nrm(-fore + down) * 9
            O.append((ball_tris(c, 8), DBC))
            seg(p - hx * 4.5, p + hx * 4.5, 1.1, HANDLE)
            seg(p - hx * 4.5, c - hx * 4 + [0, 3, 0], 1.1, HANDLE); seg(p + hx * 4.5, c + hx * 4 + [0, 3, 0], 1.1, HANDLE)
        def fore(w): return nrm(J[w] - J['E1' if w == 'W1' else 'E2'])
        if P3[id].get('two') and dbs:
            p = (grip('R') + grip('L')) / 2
            if tool == 'kb': kettlebell(p, nrm(fore('W1') + fore('W2')), hax('R'))
            else: dumbbell(p, rt.axis_angle([0, 0, 1], math.pi / 2) if id in ('squat', 'ohtri') else np.eye(3))
        else:
            for w in dbs:
                sd = 'R' if w == 'W1' else 'L'
                if tool == 'kb': kettlebell(grip(sd), fore(w), hax(sd), palmw(sd))
                else: dumbbell(grip(sd), rot_x_to(hax(sd)))
    if tool in ('bb', 'wheel'):
        gr, gl = grip('R'), grip('L'); c = (gr + gl) / 2; ax = gr - gl; ax = nrm(ax) if np.linalg.norm(ax) >= 2 else np.array([1., 0, 0])
        if tool == 'bb':
            seg(c - ax * 65, c + ax * 65, 1.4, HANDLE)
            for sg in (1, -1): p = c + ax * 48 * sg; O.append((cyl_tris(p - ax * 2.25, p + ax * 2.25, 14), DBC))
        else:
            O.append((cyl_tris(c - ax * 3, c + ax * 3, 9), DBC)); seg(c - ax * 14, c + ax * 14, 1.3, HANDLE)
    if tool in ('medball', 'pvc'):
        gr, gl = grip('R'), grip('L'); c = (gr + gl) / 2
        if tool == 'medball': O.append((ball_tris(c, 11), (.54, .35, .24)))
        else:
            ax = gr - gl; ax = nrm(ax) if np.linalg.norm(ax) >= 2 else np.array([1., 0, 0]); seg(c - ax * 75, c + ax * 75, 1.2, (.79, .71, .54))
    exo = EX.get(ex, {}) if ex else {}
    for a, c in exo.get('band', []):
        def pt(n):
            if n in ('W1', 'W2'): return grip('R' if n == 'W1' else 'L')
            if n in ('A1', 'A2'): v = J[n].copy(); v[1] = 1.2; return v
            if n in ('K1', 'K2'): return J[n] + (J['Pc'] - J[n]) * .18
            return J[n]
        mini = exo.get('tool') in ('mini', 'towel')
        seg(pt(a), pt(c), 2.4 if mini else .8, (.95, .94, .91) if exo.get('tool') == 'towel' else (.24, .64, .43))
    pr = P3[id].get('prop')
    if pr:
        t = pr['t']; x = pr.get('x', 100); z = x - 100; top = pr.get('y')
        if t == 'chair':
            dy_ = (top or 45.5) - 45.5
            O.append((box_tris(np.array([0, 44 + dy_, z]), np.array([42, 3, 40]) / 2 * 2 / 2), PROP))
            O[-1] = (box_tris(np.array([0, 44 + dy_, z]), np.array([21, 1.5, 20])), PROP)
            for lx in (-18, 18):
                for lz in (-17, 17): O.append((box_tris(np.array([lx, (43 + dy_) / 2, z + lz]), np.array([1.25, (43 + dy_) / 2, 1.25]), n=1), PROP))
            back = -19 if x < 100 else 19
            O.append((box_tris(np.array([0, 66 + dy_, z + back]), np.array([21, 20, 1.25])), PROP))
        elif t == 'bench':
            dy_ = (top or 43) - 43
            O.append((box_tris(np.array([0, 40 + dy_, z]), np.array([14, 3, 57.5]), n=6), PROP))
            for lz in (-45, 45): O.append((box_tris(np.array([0, (37 + dy_) / 2, z + lz]), np.array([11, (37 + dy_) / 2, 2]), n=2), HANDLE))
        elif t == 'incbench':
            dy_ = (top or 43) - 43
            O.append((box_tris(np.array([0, 40 + dy_, z + 20]), np.array([14, 3, 22.5]), n=4), PROP))
            Rb = rt.rot_between(np.array([0, 0, 1.]), nrm([0, .7, -.71]))
            O.append((box_tris(np.array([0, 66 + dy_, z - 22]), np.array([14, 3, 35]), Rb, n=6), PROP))
            for lz in (-10, 40): O.append((box_tris(np.array([0, (37 + dy_) / 2, z + lz]), np.array([11, (37 + dy_) / 2, 2]), n=2), HANDLE))
        elif t == 'pullbar':
            gr, gl = grip('R'), grip('L'); y = (gr[1] + gl[1]) / 2 + 1.5; zz = (gr[2] + gl[2]) / 2
            seg(np.array([-60, y, zz]), np.array([60, y, zz]), 1.6, HANDLE)
            for xx in (-60, 60): seg(np.array([xx, 0, zz]), np.array([xx, y, zz]), 2.5, PROP)
        elif t == 'dipbars':
            gr, gl = grip('R'), grip('L'); y = (gr[1] + gl[1]) / 2 - 2; zz = (gr[2] + gl[2]) / 2
            for xx in (gr[0], gl[0]):
                seg(np.array([xx, y, zz - 30]), np.array([xx, y, zz + 30]), 2, HANDLE)
                for dz in (-26, 26): seg(np.array([xx, 0, zz + dz]), np.array([xx, y, zz + dz]), 2.5, PROP)
        elif t == 'door':
            O.append((box_tris(np.array([0, 100, z + 3]), np.array([41, 100, 2]), n=6), PROP))
            gr, gl = grip('R'), grip('L'); h = (gr[1] + gl[1]) / 2
            seg((gr + gl) / 2, np.array([-30, h, z - 1]), 1.8, (.95, .94, .91))
        elif t == 'swissball':
            a = (J['A1'] + J['A2']) / 2; O.append((ball_tris(np.array([0, max(30, a[1] - 26), a[2] + 6]), 30), (.37, .58, .95)))
        elif t == 'bosu':
            a = (J['A1'] + J['A2']) / 2; O.append(([q for q in ball_tris(np.array([0, 0, a[2]]), [32, 13, 32]) if q[0][:, 1].mean() >= 0], (.29, .33, .39)))
        elif t == 'rollball':
            a = J['A1']; O.append((ball_tris(np.array([a[0], 3.5, pr['z'] if 'z' in pr else a[2] + 4]), 3.5), (.9, .42, .68)))
        elif t == 'roller':
            n = J['Nc']; O.append((cyl_tris(np.array([-22.5, 7.5, n[2]]), np.array([22.5, 7.5, n[2]]), 7.5), (.23, .51, .77)))
        elif t == 'wall':
            O.append((box_tris(np.array([0, 85, z + (-2 if x < 100 else 2)]), np.array([55, 85, 1.5]), n=8), PROP))
    return O

L = nrm([.4, .8, .6])
def render(id, ex, t, dA=0., fn=None, nodb=False):
    Q = rt.at(P3[id], t); Qn = {k: np.asarray(v) for k, v in Q.items()}
    lift = max(0, min(Qn['A1'][1], Qn['A2'][1]) - 2) if P3[id].get('free') else 0
    out, R, T = b.skin(Qn, lift)
    dy = b.dy + .7
    V = out[0][0] + [0, .7, 0]; N = out[0][1]; Ve = out[1][0] + [0, .7, 0]; Ne = out[1][1]
    # caméra : comme BodyView.kt (angle propre à la pose, sinon de face pour « front »/« fly », profil si allongé)
    Q0 = rt.at(P3[id], 0); Q0 = {k: np.asarray(v) for k, v in Q0.items()}
    _, R0, T0 = b.skin(Q0, 0)
    def lyingOf(R_, T_):
        Pc = T_[b.idx['hips']]; Nc = (T_[b.idx['uarmR']] + T_[b.idx['uarmL']]) / 2; tl = Nc - Pc
        return abs(tl[1]) < .55 * np.linalg.norm(tl)
    lying0 = lyingOf(R0, T0); lying = lyingOf(R, T)
    old = OLD.get(id, {})
    A = P3[id].get('cam')
    if A is None: A = .35 if (old.get('front') or old.get('fly')) else (1.4 if lying0 else 1.05)
    A += dA
    el = P3[id].get('el')
    E = .32 + ((el - .32) if el is not None else (.3 if lying else 0))
    E = min(1.35, E)
    dv = np.array([math.sin(A) * math.cos(E), math.sin(E), math.cos(A) * math.cos(E)])
    rgt = nrm(np.cross([0, 1., 0], dv)); up = nrm(np.cross(dv, rgt))
    objs = objects(id, ex, Q, R, T, dy, nodb)
    # cadrage sur le corps et les objets
    allp = [V] + [np.concatenate([q[0] for q in o[0]]) for o in objs if o[0]]
    allp = np.concatenate(allp)
    if P3[id].get('prop', {}).get('t') in ('wall', 'door'): allp = np.concatenate([V] + [np.concatenate([q[0] for q in o[0]]) for o in objs if o[0] and o[1] != PROP])
    mn, mx = allp.min(0), allp.max(0); tgt = (mn + mx) / 2
    tf = math.tan(math.radians(16)); asp = W / H
    hx = np.abs((allp - tgt) @ rgt).max(); hy = np.abs((allp - tgt) @ up).max()
    dist = max(80, (hy + 12) / tf, (hx + 12) / (tf * asp)) * 1.04
    eye = tgt + dv * dist
    def proj(P):
        d = P - eye; z = -(d @ dv)
        return np.stack([(d @ rgt) / (z * tf * asp), (d @ up) / (z * tf)], -1), z
    polys, cols, depth = [], [], []
    def add(tris3, nn, col, back_cull=True):
        p2, z = proj(tris3.reshape(-1, 3)); p2 = p2.reshape(tris3.shape[0], -1, 2); z = z.reshape(tris3.shape[0], -1)
        view = eye - tris3.mean(1)
        vis = np.einsum('ij,ij->i', nn, view) > -1e-3 if back_cull else np.ones(len(tris3), bool)
        sh = np.clip(nn @ L, 0, 1) * .62 + .38
        sh = sh + .08 * np.clip(np.einsum('ij,ij->i', nn, view / np.linalg.norm(view, axis=1, keepdims=True)), 0, 1)
        c = np.clip(np.asarray(col)[None, :] * sh[:, None] if np.ndim(col) == 1 else col * sh[:, None], 0, 1)
        polys.extend(p2[vis]); cols.append(c[vis]); depth.append(z[vis].mean(1))
    tri = V[IDX]; nn = nrm_rows(N[IDX].mean(1)); add(tri, nn, SKIN)
    tri = Ve[IDXE]; add(tri, nrm_rows(Ne[IDXE].mean(1)), np.array([.15, .15, .17]))
    for T4, col in objs:
        for q, n_ in T4:
            if len(q) == 4: tr = np.array([q[[0, 1, 2]], q[[0, 2, 3]]]); add(tr, np.array([n_, n_]), np.array(col), back_cull=True)
            else: add(np.array([q]), np.array([n_]), np.array(col))
    cols = np.concatenate(cols); depth = np.concatenate(depth); o = np.argsort(-depth)
    fig = plt.figure(figsize=(W / 100, H / 100), dpi=100); ax = fig.add_axes([0, 0, 1, 1])
    fig.patch.set_facecolor('#E9ECF1'); ax.set_facecolor('#E9ECF1')
    # sol (disque) et tapis, dessinés d'abord
    g = np.linspace(-400, 400, 33); fq = []
    for i in range(32):
        for j in range(32):
            q = np.array([[g[i], 0, g[j]], [g[i + 1], 0, g[j]], [g[i + 1], 0, g[j + 1]], [g[i], 0, g[j + 1]]]); q2, qz = proj(q)
            if (qz > 5).all(): fq.append(q2)
    ax.add_collection(PolyCollection(fq, facecolors='#D5DAE2', edgecolors='#D5DAE2', linewidths=.4, zorder=0))
    mat = np.array([[-32, 1.2, -85], [32, 1.2, -85], [32, 1.2, 105], [-32, 1.2, 105]]); m2, mz = proj(mat)
    if (mz > 0).all(): ax.fill(m2[:, 0], m2[:, 1], color='#5B6B8C', alpha=.55, zorder=1)
    P = [polys[i] for i in o]
    ax.add_collection(PolyCollection(P, facecolors=cols[o], edgecolors=cols[o], linewidths=.25, antialiased=True, zorder=3))
    ax.set_xlim(-1, 1); ax.set_ylim(-1, 1); ax.axis('off')
    fig.savefig(fn, dpi=100, facecolor=fig.get_facecolor(), pil_kwargs={'quality': 82}); plt.close(fig)

def nrm_rows(a): return a / (np.linalg.norm(a, axis=1, keepdims=True) + 1e-9)

# ---------- liste des poses et des exercices qui les utilisent ----------
def frames(id):
    ks = P3[id]['k']; k0, k1 = ks[0], ks[-1]
    cyc = len(ks) > 1 and all(np.allclose(np.asarray(k0[n]), np.asarray(k1.get(n, k0[n]))) for n in k0 if n != 't')
    if len(ks) == 1: return [(0, 'Position')], 0
    if cyc: return [(0, 'Départ'), (.25, 'Quart'), (.5, 'Milieu'), (.75, 'Trois quarts')], .5
    return [(0, 'Départ'), (.5, 'Milieu'), (1, 'Fin')], 1

if __name__ == '__main__':
    only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
    byp = {}
    for k, e in EX.items(): byp.setdefault(e.get('pose', k), []).append(k)
    wu = {}
    for k, v in D['WU'].items(): wu.setdefault(v[0], []).append((k, v[1], len(v) > 3 and v[3]))
    cool = D.get('COOLN', {})
    data = []
    for id in sorted(P3):
        if only and id not in only: continue
        exs = byp.get(id, []); ex = exs[0] if exs else None
        names = [EX[x]['n'] for x in exs] + [n for _, n, _ in wu.get(id, [])] + ([cool[id]] if id in cool and id not in exs else [])
        nodb = not exs and any(nd for _, _, nd in wu.get(id, []))
        fr, ext = frames(id); imgs = []
        for i, (t, lab) in enumerate(fr):
            fn = f'img/{id}_{i}.webp'; render(id, ex, t, 0, f'{OUT}/{fn}', nodb); imgs.append({'src': fn, 'lab': lab})
        fn = f'img/{id}_v.webp'; render(id, ex, ext, math.pi / 2, f'{OUT}/{fn}', nodb)
        imgs.append({'src': fn, 'lab': ('Fin' if ext == 1 else 'Milieu') + ', vue à 90°'})
        e = EX.get(ex, {}) if ex else {}
        data.append({'id': id, 'names': names or [id], 'cues': e.get('cues', []), 'avoid': e.get('avoid', []), 'tip': e.get('tip', ''), 'imgs': imgs})
        print(id, flush=True)
    json.dump(data, open(f'{OUT}/poses.json', 'w'), ensure_ascii=False, indent=0)

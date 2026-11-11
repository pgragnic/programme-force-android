# Deuxième contrôle de tous les mouvements, plus strict (Philippe, 11 novembre : « Peux tu refaire une vérification de tous les exercices ? »).
# En plus de audit.py : appuis réellement posés (pieds, mains), appuis qui glissent, angles des chevilles, poignets, cou et dos,
# consignes « dos plat » et « buste droit » respectées, charges dans le corps sur tout le mouvement.
import sys, json, numpy as np, rt, fix3, fix4
from fix4 import B, NM, PB, skin, lowest, tool_pen, Q_
D = json.load(open('../../app/src/main/assets/programme.json')); E = D['EX']; P3 = json.load(open('poses3.json'))
byp = {}
for k, e in E.items(): byp.setdefault(e.get('pose', k), []).append(k)
def ang(a, b): return np.degrees(np.arccos(np.clip(a @ b / (np.linalg.norm(a) * np.linalg.norm(b)), -1, 1)))
def rel(R, a, b): M = R[NM.index(a)].T @ R[NM.index(b)]; return np.degrees(np.arccos(np.clip((np.trace(M) - 1) / 2, -1, 1)))
def bdir(R, n): b = B.B[NM.index(n)]; return R[NM.index(n)] @ (b['b'] - b['a'])
A0 = {sd: ang(B.B[NM.index('shin' + sd)]['b'] - B.B[NM.index('shin' + sd)]['a'], B.B[NM.index('foot' + sd)]['b'] - B.B[NM.index('foot' + sd)]['a']) for sd in 'RL'}
out = {}
for id, p in P3.items():
    exs = byp.get(id, []); cues = ' ; '.join(c for x in exs for c in E[x].get('cues', [])); tools = {E[x].get('tool', '') for x in exs}
    iss = {}; ks = np.linspace(0, 1, 13); feetpos = {'R': [], 'L': []}; handpos = {'R': [], 'L': []}
    def add(key, k, val): iss.setdefault(key, []).append((round(float(k), 2), round(float(val), 1)))
    lunge = 'lunge' in id or id in ('stepup', 'sldl', 'calf1')
    for t in ks:
        kf = {'t': t, **{kk: (v.tolist() if hasattr(v, 'tolist') else v) for kk, v in rt.at(p, t).items()}}
        V, R, T = skin(P3, id, kf); dy = B.dy + fix4.APP_DY
        free = p.get('free')
        for s, sd in (('1', 'R'), ('2', 'L')):
            lo = lowest(V, ['foot' + sd, 'toes' + sd])
            if not free and kf['A' + s][1] <= 4.5:
                if lo > 2.2 and not (lunge and 0 < t < 1): add(f'pied {sd} censé posé mais en l\'air (cm)', t, lo)
                feetpos[sd].append(T[NM.index('foot' + sd)][[0, 2]])
            else: feetpos[sd].append(None)
            hl = lowest(V, ['hand' + sd, 'finA' + sd, 'finB' + sd])
            if not free and kf['W' + s][1] <= 6:
                if hl > 3.2: add(f'main {sd} censée posée mais en l\'air (cm)', t, hl)
                handpos[sd].append(T[NM.index('hand' + sd)][[0, 2]])
            else: handpos[sd].append(None)
            # cheville : flexion par rapport au repos (positif = pointe tendue)
            fx = A0[sd] - ang(bdir(R, 'shin' + sd), bdir(R, 'foot' + sd))
            if fx > 58: add(f'cheville {sd} trop tendue (°)', t, fx)
            if fx < -32: add(f'cheville {sd} trop repliée (°)', t, -fx)
            w = rel(R, 'twst' + sd, 'hand' + sd)
            if w > 75: add(f'poignet {sd} cassé (°)', t, w)
        nk = rel(R, 'chest', 'head')
        if nk > 55: add('cou trop plié (°)', t, nk)
        sp = rel(R, 'hips', 'chest')
        if sp > 42: add('dos trop plié (°)', t, sp)
        if 'Dos plat' in cues and sp > 18: add('consigne « dos plat » non respectée, dos plié de (°)', t, sp)
        if 'Buste droit' in cues:
            tv = ang(bdir(R, 'spine'), np.array([0, 1., 0]))
            if tv > 32: add('consigne « buste droit » non respectée, buste penché de (°)', t, tv)
        tl = sorted(tools & {'bb', 'kb', 'medball'})
        if tl and (p.get('db') or 'bb' in tl) and id != 'kbhalo':
            n, _ = tool_pen(P3, id, kf, tl[0])
            if n > 3: add(f'{tl[0]} dans le corps (points)', t, n)
    for nm_, pos in (('pied', feetpos), ('main', handpos)):
        for sd in 'RL':
            pp = pos[sd]
            if all(x is not None for x in pp):
                a = np.array(pp); rg = np.linalg.norm(a.max(0) - a.min(0))
                if rg > 4: iss[f'{nm_} {sd} posé qui glisse (cm)'] = [(0, round(float(rg), 1))]
    if p.get('db') and tools & {'db'}:
        n = fix3.db_pen(P3, id, 0)
        if n > 0: iss['haltère dans le corps (points)'] = [(0, n)]
    out[id] = iss
    if iss:
        print(id, exs)
        for k_, v in iss.items(): print('   ', k_, ': k', v[0][0], '–', v[-1][0], ', max', max(x[1] for x in v))
json.dump({k: {a: b for a, b in v.items()} for k, v in out.items()}, open(sys.argv[1] if len(sys.argv) > 1 else '/tmp/audit2.json', 'w'), ensure_ascii=False, indent=1)
print(sum(1 for v in out.values() if not v), 'poses sans remarque sur', len(out))

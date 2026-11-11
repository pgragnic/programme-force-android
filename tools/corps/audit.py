# Contrôle automatique de cohérence des mouvements (demande de Philippe : « pourrais-tu vérifier pour tous les exercices que les mouvements sont bien cohérents »)
# Pour chaque pose de POSES3, sur 13 instants : appuis au sol, membre qui traverse le corps, haltère dans le corps, accessoire traversé,
# prise à deux mains qui se déforme, symétrie, à-coups, genou en hyperextension.
import sys, json, numpy as np, rt, fix3
from scipy.spatial import cKDTree
D = json.load(open('../../app/src/main/assets/programme.json')); E = D['EX']; P3 = json.load(open('poses3.json'))
b = rt.Body('corps2.bin'); nm = [x['n'] for x in b.B]; g = b.G[0]; pb = g['bi'][:, 0]; strong = g['bw'][:, 0] > .5
def part(n):
    for k, v in (('pied', ('foot', 'toes')), ('main', ('hand', 'fin', 'thmb')), ('avbras', ('farm', 'twst')), ('bras', ('uarm',)),
                 ('jambe', ('shin',)), ('cuisse', ('thgh',)), ('tete', ('head', 'neck')), ('tronc', ('spine', 'chest', 'hips', 'pelvis', 'clav'))):
        if n.startswith(v): return k + (n[-1] if n[-1] in 'RL' else '')
    return n
PART = np.array([part(n) for n in nm])[pb]
SIDE = np.array([p[-1] if p[-1] in 'RL' else 'C' for p in PART])
CHAIN = np.array([p.rstrip('RL') for p in PART])
dist_parts = np.isin(CHAIN, ['main', 'avbras', 'pied', 'jambe']) & strong
byp = {}
for k, e in E.items(): byp.setdefault(e.get('pose', k), []).append(k)
def pose_q(id, k):
    Q = {kk: v for kk, v in rt.at(P3[id], k).items() if kk != 't'}
    lift = max(0, min(Q['A1'][1], Q['A2'][1]) - 2) if P3[id].get('free') else 0
    return Q, lift
def props(id):
    p = P3[id].get('prop');  boxes = []
    if not p: return boxes
    z = p.get('x', 100) - 100; t = p['t']
    if t == 'chair': boxes += [((0, 44, z), (21, 1.5, 20)), ((0, 66, z + (-19 if p.get('x', 100) < 100 else 19)), (21, 20, 1.2))]
    if t == 'bench': boxes += [((0, 40, z), (14, 3, 57.5))]
    if t == 'wall': boxes += [((0, 85, z + (-2 if p.get('x', 100) < 100 else 2)), (55, 85, 1.5))]
    return boxes
rep = {}
for id in P3:
    issues = []; ks = np.linspace(0, 1, 13); prevV = None; contacts = []
    stat = P3[id].get('stat'); exs = byp.get(id, [])
    ms = {E[x].get('m') for x in exs}; tools = {E[x].get('tool', '') for x in exs}
    for k in ks:
        Q, lift = pose_q(id, k); out, R, T = b.skin(Q, lift); V, N = out[0]
        # appuis
        if not P3[id].get('free'):
            c = sorted(set(PART[(V[:, 1] < 1.2) & strong])); contacts.append(c)
        # membre qui traverse le corps : sommet d'une extrémité sous la surface d'une autre partie (autre chaîne ou autre côté)
        for side in 'RL':
            m = dist_parts & (SIDE == side)
            oth = strong & ~((SIDE == side) & np.isin(CHAIN, ['main', 'avbras', 'bras', 'pied', 'jambe', 'cuisse']))
            tr = cKDTree(V[oth]); d, j = tr.query(V[m]); Vo, No = V[oth][j], N[oth][j]
            depth = -((V[m] - Vo) * No).sum(1); bad = (d < 3) & (depth > 1.2)
            if bad.sum() > 6: issues.append(f'k={k:.2f} {side}: {sorted(set(PART[m][bad]))} rentre dans {sorted(set(PART[oth][j[bad]]))} ({bad.sum()} pts, {depth[bad].max():.1f} cm)')
        # accessoire traversé
        for (cx, cy, cz), (hx, hy, hz) in props(id):
            ins = (np.abs(V[:, 0] - cx) < hx - .8) & (np.abs(V[:, 1] - cy) < hy - .8) & (np.abs(V[:, 2] - cz) < hz - .8)
            if ins.sum() > 10: issues.append(f'k={k:.2f} traverse l\'accessoire {P3[id]["prop"]["t"]} : {sorted(set(PART[ins]))} ({ins.sum()} pts)')
        # genou en hyperextension : la jambe doit se plier vers l'arrière (axe latéral du bassin)
        Lx = R[nm.index('hips')] @ np.array([1., 0, 0])
        for s_, sd in (('1', 'R'), ('2', 'L')):
            H_ = T[nm.index('thgh' + sd)]; K_, A_ = np.array(Q['K' + s_]), np.array(Q['A' + s_])
            u1 = (K_ - H_) / np.linalg.norm(K_ - H_); u2 = (A_ - K_) / np.linalg.norm(A_ - K_)
            sg = np.degrees(np.arcsin(np.clip(np.cross(u1, u2) @ Lx, -1, 1)))
            if sg < -8: issues.append(f'k={k:.2f} genou {sd} plié à l\'envers ({sg:.0f}°)')
        # à-coups
        if prevV is not None:
            jump = np.linalg.norm(V - prevV, axis=1).max()
            if jump > 22: issues.append(f'k={k:.2f} à-coup : un point bouge de {jump:.0f} cm entre deux instants')
        prevV = V
        # prise à deux mains
        if P3[id].get('two') or tools & {'bb', 'medball', 'wheel', 'towel'}:
            pass
    # prise à deux mains : écart des poignets constant
    if tools & {'bb', 'medball', 'wheel'} or P3[id].get('two'):
        dw = [np.linalg.norm(np.array(pose_q(id, k)[0]['W1']) - np.array(pose_q(id, k)[0]['W2'])) for k in ks]
        if max(dw) - min(dw) > 4: issues.append(f'prise à deux mains : écart des poignets de {min(dw):.0f} à {max(dw):.0f} cm')
    # haltères dans le corps
    if P3[id].get('db') and tools & {'db', 'kb'}:
        try:
            n = fix3.db_pen(P3, id, 0)
            if n > 0: issues.append(f'haltère dans le corps ({n} pts)')
        except Exception as ex: issues.append(f'db_pen erreur {ex}')
    # appuis qui changent pendant le mouvement
    if contacts:
        feet = [set(x for x in c if x.startswith(('pied', 'main', 'jambe', 'tronc', 'tete', 'cuisse', 'avbras'))) for c in contacts]
        if len({frozenset(f) for f in feet}) > 1 and not ms & {'alt', 'uni'}:
            issues.append('appuis variables : ' + ' → '.join(['+'.join(sorted(f)) or '∅' for f in (feet[0], feet[6], feet[-1])]))
    # symétrie (exercices non alternés, non unilatéraux)
    if not ms & {'alt', 'uni'} and not P3[id].get('sides'):
        asym = 0
        for k in (0, .5, 1):
            Q, _ = pose_q(id, k)
            for j in 'EWKAT':
                if j + '1' in Q and j + '2' in Q:
                    a, c = np.array(Q[j + '1']), np.array(Q[j + '2']) * [-1, 1, 1]; asym = max(asym, np.linalg.norm(a - c))
        if asym > 8: issues.append(f'asymétrique ({asym:.0f} cm) alors que l\'exercice ne l\'est pas ({",".join(exs)})')
    import re
    grp = {}
    for x in issues:
        mk = re.match(r'k=([0-9.]+) (.*)', x)
        key = re.sub(r'\(.*\)', '', mk.group(2)).replace("np.str_('", '').replace("')", '') if mk else x
        grp.setdefault(key, []).append((float(mk.group(1)) if mk else None, x))
    issues = [(f'{k_} [k {v[0][0]:.2f}–{v[-1][0]:.2f}, {len(v)}/13] ' + re.search(r'\(.*\)', v[-1][1]).group(0) if v[0][0] is not None and re.search(r'\(.*\)', v[-1][1]) else k_) for k_, v in grp.items()]
    rep[id] = dict(ex=exs, m=sorted(x for x in ms if x), contacts=['+'.join(c) for c in (contacts[0], contacts[6], contacts[-1])] if contacts else [], issues=issues)
    print(id, exs, rep[id]['contacts'], *issues, sep='\n   ' if issues else ' ', flush=True)
json.dump(rep, open(sys.argv[1] if len(sys.argv) > 1 else '/tmp/audit.json', 'w'), ensure_ascii=False, indent=1)

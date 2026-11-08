# Convertit les poses 2D (POSES) en poses 3D (POSES3) pour le nouveau personnage.
# Chaque image clé : P bassin, S milieu du dos, N ligne des épaules, H tête, puis par côté (1 = droite, 2 = gauche) :
# E coude, W poignet, F bout des doigts, U normale de la paume, c fermeture de la main (0 ouverte, 1 poing), K genou, A cheville, T orteils.
import sys, json, math, numpy as np
sys.path.insert(0, '/home/claude/programme-force-android/tools/corps')
import oldpose as op

D = op.D; P = D['POSES']; EX = D['EX']
X = np.array([1., 0, 0]); UP = np.array([0, 1., 0])
def nrm(v): n = np.linalg.norm(v); return v / n if n > 1e-9 else v
SUPIN = {'curl', 'kbcurl', 'bbcurl'}
PROC = ('fly', 'circ', 'heel', 'side', 'legw')

def tools_of(pose):
    t = set()
    for k, e in EX.items():
        if e.get('pose', k) == pose: t.add(e.get('tool', ''))
    return t

def keytimes(id):
    p = P[id]; ts = {0.0, 1.0} | {round(m['t'], 3) for m in p.get('kf', [])}
    lat = p.get('lat')
    if any(p.get(f) for f in PROC) or (lat and lat[0] != lat[1]): ts |= {i / 8 for i in range(9)}
    return sorted(ts)

def conv(id):
    p = P[id]; flip = id in op.FLIP; tl = tools_of(id); held = {'db', 'kb', 'bb', 'pvc', 'wheel', 'towel', 'medball', 'band'} & tl
    bar = (p.get('prop') or {}).get('t') in ('pullbar', 'dipbars')
    dbs = p.get('db', []); two = id in op.TWO; grip = p.get('grip')
    floor = 'F1' in p.get('a', {})
    keys = []
    for t in keytimes(id):
        J = op.build(id, t)
        g = lambda n: [round(float(x), 2) for x in J[n]]
        Q = {'t': round(t, 3), 'P': g('Pc'), 'N': g('Nc'), 'H': g('H')}
        if 'S' in J: Q['S'] = g('S')
        for s_old, s_new in (('1', '2'), ('2', '1')) if flip else (('1', '1'), ('2', '2')):
            sd = 1 if s_new == '1' else -1
            for a, b in (('E', 'E'), ('W', 'W'), ('K', 'K'), ('A', 'A'), ('T', 'T'), ('F', 'F')):
                if a + s_old in J: Q[b + s_new] = g(a + s_old)
            E = J['E' + s_old]; W = J['W' + s_old]; fore = nrm(W - E)
            hand_has = (('W' + s_old) in dbs) or two or ('bb' in tl) or bool(held - {'db', 'kb'})
            if 'F' + s_old in J:
                Fv = J['F' + s_old]
                if id == 'wallpush' or (id == 'calf1' and s_new == ('1' if flip else '2')): U = np.array([0, 0, 1.])
                elif id == 'chairdip': U = np.array([0, -1., 0])
                elif Fv[1] > 2.5 and abs(Fv[1] - W[1]) < 3 or Fv[1] <= 2.5: U = np.array([0, -1., 0])
                else: U = None
                c = .5 if id == 'chairdip' else .05
            elif bar:
                # barre de traction : mains fermées, paumes vers l'avant ; barres à dips : mains fermées, paumes vers l'intérieur
                c = .85
                U = np.array([0, 0, 1.]) if p['prop']['t'] == 'pullbar' else nrm(-sd * X - fore * np.dot(-sd * X, fore))
            elif held and hand_has and (dbs or held - {'db', 'kb'}):
                c = .85
                if grip == 'n' or two: U = nrm(-sd * X - fore * np.dot(-sd * X, fore))
                else:
                    h = X - fore * np.dot(X, fore)
                    if np.linalg.norm(h) < .3: h = np.array([0, 0, 1.])
                    U = -np.cross(fore, nrm(h)) * (-1 if id in SUPIN else 1)
            else:
                c = .3; U = nrm(-sd * X - fore * np.dot(-sd * X, fore))
            if U is not None and np.linalg.norm(U) > .1: Q['U' + s_new] = [round(float(x), 3) for x in nrm(U)]
            Q['c' + s_new] = c
        keys.append(Q)
    out = {'k': keys}
    for f in ('cam', 'stat', 'free', 'prop', 'tool', 'db', 'band', '__d'):
        if f in p: out[f] = p[f]
    if flip and 'db' in out: out['db'] = ['W2' if x == 'W1' else 'W1' if x == 'W2' else x for x in out['db']]
    if two or id in D.get('TWOHAND', []): out['two'] = True
    return out

if __name__ == '__main__':
    P3 = {id: conv(id) for id in P}
    json.dump(P3, open(sys.argv[1], 'w'), ensure_ascii=False, separators=(',', ':'))
    print(len(P3), 'poses', sum(len(v['k']) for v in P3.values()), 'images clés')

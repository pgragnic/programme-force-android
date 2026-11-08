# Construit corps2.bin : maillage MakeHuman (CC0) morphologie homme jeune athlétique, squelette réduit à 29 os
# (mains ouvertes, doigts, pouce, avant-bras en deux pour la torsion, colonne en 3), poids fusionnés (4 os max par sommet),
# zones musculaires reprises de l'ancien corps.bin (même ordre de sommets). Repère : comme l'ancien (côté droit en +x, face en +z).
import json, struct, numpy as np, sys
sys.path.insert(0, '/home/claude/programme-force-android/tools/corps')
import mh, old

V, F = mh.base(); V = mh.apply(V, mh.MORPH); MB = mh.skel(V)
hdr0, G0 = old.load(); P0 = G0[0]['pos']
# même échelle/position que l'ancien personnage (ajustement sur le buste et les jambes)
B = V[:13380] * 10; m = np.abs(P0[:, 0]) < 28
X, Y = B[m], P0[m]
s = ((X - X.mean(0)) * (Y - Y.mean(0))).sum() / ((X - X.mean(0)) ** 2).sum(); t = Y.mean(0) - s * X.mean(0)
def tr(p): q = np.asarray(p) * 10 * s + t; q = q.copy(); q[..., 0] *= -1; return q   # miroir : droite en +x
Vb = tr(V[:13380])

S = ['R', 'L']
def sd(n, side): return n.replace('.R', '.' + side)
BONES = []   # (nom, tête, queue, parent, os MakeHuman fusionnés)
def h(n): return tr(MB[n]['head'])
def tl(n): return tr(MB[n]['tail'])
spine_mid = h('spine01')
BONES += [
    ('hips', h('spine05'), h('spine04'), -1, ['root', 'spine05', 'pelvis.L', 'pelvis.R']),
    ('spine', h('spine04'), spine_mid, 0, ['spine04', 'spine03', 'spine02']),
    ('chest', spine_mid, tl('spine01'), 1, ['spine01', 'breast.L', 'breast.R']),
    ('neck', h('neck01'), tl('neck03'), 2, ['neck01', 'neck02', 'neck03']),
    ('head', h('head'), tl('head'), 3, ['head']),
]
face = [n for n in MB if any(n.startswith(p) for p in ('jaw', 'tongue', 'eye', 'oculi', 'levator', 'oris', 'orbicularis', 'risorius', 'special', 'temporalis', 'mandible', 'lolid', 'uplid', 'eyebrow', 'nose', 'cheek', 'ear', 'chin', 'lip'))]
BONES[4][4].extend(face)
for side in S:
    k = 'R' if side == 'R' else 'L'
    c = len(BONES)
    fing1 = [f'finger{i}-1.{k}' for i in (2, 3, 4, 5)]; fing23 = [f'finger{i}-{j}.{k}' for i in (2, 3, 4, 5) for j in (2, 3)]
    knuck = np.mean([h(f) for f in fing1], 0); pip = np.mean([h(f'finger{i}-2.{k}') for i in (2, 3, 4, 5)], 0); tip = np.mean([tl(f'finger{i}-3.{k}') for i in (2, 3, 4, 5)], 0)
    BONES += [
        (f'clav{k}', h(f'clavicle.{k}'), h(f'upperarm01.{k}'), 2, [f'clavicle.{k}', f'shoulder01.{k}']),
        (f'uarm{k}', h(f'upperarm01.{k}'), h(f'lowerarm01.{k}'), c, [f'upperarm01.{k}', f'upperarm02.{k}']),
        (f'farm{k}', h(f'lowerarm01.{k}'), h(f'lowerarm02.{k}'), c + 1, [f'lowerarm01.{k}']),
        (f'twst{k}', h(f'lowerarm02.{k}'), h(f'wrist.{k}'), c + 2, [f'lowerarm02.{k}']),
        (f'hand{k}', h(f'wrist.{k}'), knuck, c + 3, [f'wrist.{k}'] + [f'metacarpal{i}.{k}' for i in (1, 2, 3, 4)]),
        (f'finA{k}', knuck, pip, c + 4, fing1),
        (f'finB{k}', pip, tip, c + 5, fing23),
        (f'thmb{k}', h(f'finger1-1.{k}'), tl(f'finger1-3.{k}'), c + 4, [f'finger1-{j}.{k}' for j in (1, 2, 3)]),
    ]
for side in S:
    k = side; c = len(BONES)
    toes = [n for n in MB if n.startswith('toe') and n.endswith('.' + k)]
    tb = np.mean([h(f'toe{i}-1.{k}') for i in (1, 2, 3, 4, 5)], 0); tt = np.mean([tl(f'toe{i}-{3 if i>1 else 2}.{k}') for i in (1, 2, 3, 4, 5)], 0)
    BONES += [
        (f'thgh{k}', h(f'upperleg01.{k}'), h(f'lowerleg01.{k}'), 0, [f'upperleg01.{k}', f'upperleg02.{k}']),
        (f'shin{k}', h(f'lowerleg01.{k}'), h(f'foot.{k}'), c, [f'lowerleg01.{k}', f'lowerleg02.{k}']),
        (f'foot{k}', h(f'foot.{k}'), tb, c + 1, [f'foot.{k}']),
        (f'toes{k}', tb, tt, c + 2, toes),
    ]
names = [b[0] for b in BONES]
owner = {}
for i, b in enumerate(BONES):
    for n in b[4]: owner[n] = i
# os MakeHuman non listés : rattachés au plus proche ancêtre listé
def own(n):
    while n is not None and n not in owner: n = MB[n]['parent']
    return owner.get(n, 0)
W = np.zeros((13380, len(BONES)))
for n, lst in mh.weights().items():
    bi = own(n)
    for v, w in lst:
        if v < 13380: W[v, bi] += w
sw = W.sum(1); W[sw == 0, 0] = 1; W /= W.sum(1, keepdims=True)
top = np.argsort(-W, 1)[:, :4]; tw = np.take_along_axis(W, top, 1); tw /= tw.sum(1, keepdims=True)
# faces (quads → 2 triangles), ordre inversé à cause du miroir
tri = []
for f in F:
    a, b, c, d = f; tri += [a, c, b, a, d, c]
tri = np.array(tri, dtype='<u2')
# normales au repos
nr = np.zeros_like(Vb)
for i in range(0, len(tri), 3):
    a, b, c = tri[i:i+3]; n = np.cross(Vb[b] - Vb[a], Vb[c] - Vb[a]); nr[[a, b, c]] += n
nr /= np.linalg.norm(nr, axis=1, keepdims=True) + 1e-9
lab = G0[0]['lab']
# yeux : ceux de l'ancien fichier, rattachés à la tête
eye = G0[1]

out = dict(v=2, labels=hdr0['labels'],
           bones=[dict(n=b[0], a=[round(float(x), 3) for x in b[1]], b=[round(float(x), 3) for x in b[2]], p=b[3]) for b in BONES],
           groups=[])
blob = bytearray()
def pad():
    while len(blob) % 4: blob.append(0)
def grp(name, pos, idx, top, tw, nrm, lab):
    mn = pos.min(0); mx = pos.max(0); q = np.round((pos - mn) / (mx - mn) * 65535).astype('<u2')
    out['groups'].append(dict(g=name, nv=len(pos), ni=len(idx), mn=[float(x) for x in mn], mx=[float(x) for x in mx], nrm=1, lab=1 if lab is not None else 0))
    blob.extend(q.tobytes()); pad(); blob.extend(np.asarray(idx, '<u2').tobytes()); pad()
    blob.extend(top.astype('u1').tobytes()); pad(); blob.extend(np.round(tw * 255).astype('u1').tobytes()); pad()
    blob.extend(np.round(nrm * 127).astype('i1').tobytes()); pad()
    if lab is not None: blob.extend(lab.astype('u1').tobytes()); pad()
grp('Corps', Vb, tri, top, tw, nr, lab)
ne = len(eye['pos']); hi = names.index('head')
grp('Yeux', eye['pos'], eye['idx'], np.tile([hi, 0, 0, 0], (ne, 1)), np.tile([1., 0, 0, 0], (ne, 1)), eye['nrm'] if eye['nrm'] is not None else np.zeros((ne, 3)), None)
# repère de la main au repos (pour le moteur) : normale de paume, axe des jointures, point de prise
hand = {}
for k in 'RL':
    hi_ = names.index('hand' + k); hb = BONES[hi_]; u = (hb[2] - hb[1]) / np.linalg.norm(hb[2] - hb[1])
    msk = (top[:, 0] == hi_) & (tw[:, 0] > .6); Pm = Vb[msk]; c = Pm.mean(0); Qm = Pm - c; Qm = Qm - np.outer(Qm @ u, u)
    w_, v_ = np.linalg.eigh(Qm.T @ Qm); lat = v_[:, 2]; n = np.cross(u, lat); n /= np.linalg.norm(n)
    tip_ = BONES[names.index('finB' + k)][2]
    if np.dot(tip_ - hb[1], n) < 0: n = -n
    hl = np.cross(n, u)
    g = (hb[1] + hb[2]) / 2 + n * 2.2   # au milieu de la paume, un peu devant
    hand[k] = dict(n=[round(float(x), 4) for x in n], l=[round(float(x), 4) for x in hl], g=[round(float(x), 3) for x in g - hb[1]])
out['hand'] = hand
hj = json.dumps(out, separators=(',', ':')).encode()
hj += b' ' * ((4 - (4 + len(hj)) % 4) % 4)
open(sys.argv[1] if len(sys.argv) > 1 else 'corps2.bin', 'wb').write(struct.pack('<i', len(hj)) + hj + bytes(blob))
print(len(BONES), 'os', len(hj) + len(blob), 'octets')
for b in BONES: print(f'{b[0]:6s}', np.round(b[1], 1), np.round(b[2], 1), b[3])

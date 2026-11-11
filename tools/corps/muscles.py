# Zones musculaires anatomiques et sens des fibres pour le rendu « écorché » (demande de Philippe : « Comment on pourrait faire pour
# avoir un bonhomme ressemblant à ça ? », choix « Voie 1 »).
# Source : atlas BodyParts3D (© The Database Center for Life Science, CC BY-SA 2.1 JP), muscles superficiels.
# 1) le personnage est mis en position anatomique et calé sur la peau de l'atlas (échelle puis déformation lisse) ;
# 2) chaque sommet prend le muscle le plus proche (tête, mains et pieds restent « peau ») ;
# 3) sens des fibres : axe principal du muscle, ou éventail vers l'insertion (pectoraux, deltoïdes, trapèzes, dorsaux, fessiers) ;
# 4) lignes de séparation entre muscles.
# Usage : python3 muscles.py <corps2.bin subdivisé> <pts.npz> → muscles.npz (puis relancer build.py)
# pts.npz : nuages de points des STL de l'atlas (github.com/Kevin-Mattheus-Moerman/BodyParts3D, assets/BodyParts3D_data/stl).
import sys, numpy as np, rt
from scipy.spatial import cKDTree
import scipy.sparse as sp
BIN, PTS = sys.argv[1], sys.argv[2]
P = np.load(PTS)
def tomh(v): v = np.asarray(v, float); return np.stack([-v[:, 0], v[:, 2], -v[:, 1]], 1) * 0.1
b = rt.Body(BIN); g = b.G[0]; tri = g['idx'].reshape(-1, 3); n = len(g['pos'])
# 1) position anatomique, calage
Q = {'P': [0, 76, 0], 'N': [0, 124, 0], 'H': [0, 142, 0], 'E1': [20.5, 100, -1], 'W1': [22.5, 76, 1], 'F1': [23.5, 66, 2.5], 'U1': [0, 0, 1], 'c1': 0.1,
     'E2': [-20.5, 100, -1], 'W2': [-22.5, 76, 1], 'F2': [-23.5, 66, 2.5], 'U2': [0, 0, 1], 'c2': 0.1,
     'K1': [9.5, 38, 1], 'A1': [9.8, 0, 1], 'T1': [9.8, -4, 15], 'K2': [-9.5, 38, 1], 'A2': [-9.8, 0, 1], 'T2': [-9.8, -4, 15]}
out, R, T = b.skin(Q); M = out[0][0].copy(); Np = out[0][1]
A = tomh(P['FMA7163']); tA = cKDTree(A)
s = (A[:, 1].max() - A[:, 1].min()) / (M[:, 1].max() - M[:, 1].min()); M = (M - M.mean(0)) * s + A.mean(0)
e = np.concatenate([tri[:, [0, 1]], tri[:, [1, 2]], tri[:, [2, 0]]]); e = np.concatenate([e, e[:, ::-1]])
Adj = sp.coo_matrix((np.ones(len(e)), (e[:, 0], e[:, 1])), shape=(n, n)).tocsr(); Adj.data[:] = 1
deg = np.asarray(Adj.sum(1)).ravel(); deg[deg == 0] = 1; Lm = sp.diags(1 / deg) @ Adj
for it in range(25):
    d, i = tA.query(M); d2, j = cKDTree(M).query(A)
    src = np.concatenate([M, M[j]]); dst = np.concatenate([A[i], A])
    ms, md = src.mean(0), dst.mean(0); sc = ((src - ms) * (dst - md)).sum(0) / ((src - ms) ** 2).sum(0); M = (M - ms) * sc + md
for it in range(30):
    d, i = tA.query(M); D = A[i] - M
    d2, j = cKDTree(M).query(A); D2 = np.zeros_like(M); c = np.zeros(n); np.add.at(D2, j, A - M[j]); np.add.at(c, j, 1); m = c > 0; D2[m] /= c[m][:, None]
    D = np.where(m[:, None], (D + D2) / 2, D)
    for k in range(60 if it < 15 else 25): D = Lm @ D
    M += 0.6 * D
print('calage : écart médian', np.median(tA.query(M)[0]).round(2), 'cm')
# 2) muscles (nom, groupe de l'appli, éventail, FMA côté droit du personnage, côté gauche)
MUS = [
 ('pec', 'Pectoraux', 'pec', ['FMA79979', 'FMA34690', 'FMA45874'], ['FMA79980', 'FMA34691', 'FMA45875']),
 ('deltA', 'Épaules', 'low', ['FMA34680'], ['FMA34681']), ('deltM', 'Épaules', 'low', ['FMA34682'], ['FMA34683']), ('deltP', 'Épaules', 'low', ['FMA34684'], ['FMA34685']),
 ('trapD', 'Dos', 'lat', ['FMA33586'], ['FMA33587']), ('trapT', 'Dos', 'lat', ['FMA33584'], ['FMA33585']), ('trapA', 'Dos', 'lat', ['FMA33581'], ['FMA33583']),
 ('lat', 'Dos', 'latup', ['FMA13358'], ['FMA13359']), ('teres', 'Dos', None, ['FMA32551'], ['FMA32552']), ('infra', 'Dos', 'lat', ['FMA32547'], ['FMA32548']),
 ('serr', 'Abdos', None, ['FMA13398'], ['FMA13399']), ('obl', 'Abdos', None, ['FMA13336'], ['FMA13337']), ('rect', 'Abdos', None, ['FMA13377'], ['FMA13378']),
 ('erec', 'Lombaires', None, ['FMA22740', 'FMA22742', 'FMA22751'], ['FMA22741', 'FMA22743', 'FMA22753']),
 ('gmax', 'Fessiers', 'latlow', ['FMA22328'], ['FMA22329']), ('gmed', 'Fessiers', None, ['FMA22330'], ['FMA22331']), ('tfl', 'Fessiers', None, ['FMA22425'], ['FMA22426']),
 ('rf', 'Quadriceps', None, ['FMA38928'], ['FMA38929']), ('vl', 'Quadriceps', None, ['FMA38930'], ['FMA38931']), ('vm', 'Quadriceps', None, ['FMA38932'], ['FMA38933']),
 ('sart', 'Quadriceps', None, ['FMA22354'], ['FMA22355']), ('add', 'Quadriceps', None, ['FMA22456', 'FMA22459'], ['FMA22457', 'FMA22460']), ('grac', 'Quadriceps', None, ['FMA43883'], ['FMA43884']),
 ('bf', 'Ischios', None, ['FMA45888', 'FMA45891'], ['FMA45889', 'FMA45892']), ('st', 'Ischios', None, ['FMA22358'], ['FMA22359']), ('sm', 'Ischios', None, ['FMA22448'], ['FMA22449']),
 ('gasM', 'Mollets', None, ['FMA45957'], ['FMA45958']), ('gasL', 'Mollets', None, ['FMA45960'], ['FMA45961']), ('sol', 'Mollets', None, ['FMA22558'], ['FMA22559']),
 ('tib', 'Jambe', None, ['FMA22544'], ['FMA22545']), ('edl', 'Jambe', None, ['FMA22548'], ['FMA22549']), ('fib', 'Jambe', None, ['FMA22552'], ['FMA22553']),
 ('bic', 'Biceps', None, ['FMA37686', 'FMA37684'], ['FMA37687', 'FMA37685']), ('brach', 'Biceps', None, ['FMA37668'], ['FMA37669']),
 ('tri', 'Triceps', None, ['FMA37699', 'FMA37697', 'FMA37695'], ['FMA37700', 'FMA37698', 'FMA37696']),
 ('brr', 'Avant-bras', None, ['FMA38486'], ['FMA38487']),
 ('flex', 'Avant-bras', None, ['FMA38460', 'FMA38463', 'FMA38617', 'FMA38619', 'FMA38560', 'FMA38562'], ['FMA38461', 'FMA38464', 'FMA38618', 'FMA38620', 'FMA38561', 'FMA38563']),
 ('ext', 'Avant-bras', None, ['FMA38495', 'FMA38498', 'FMA38501', 'BP45', 'BP47'], ['FMA38496', 'FMA38499', 'FMA38502', 'BP44', 'BP46']),
 ('scm', 'Cou', None, ['FMA13408'], ['FMA13409']),
]
fine = [('Peau', 'Peau', None, None)]
for nm, grp, fan, Rr, Ll in MUS:
    for sd, ids in (('R', Rr), ('L', Ll)): fine.append((nm + sd, grp, fan, np.concatenate([tomh(P[i]) for i in ids])))
K = len(fine)
D = np.full((n, K), 1e9)
for k, f in enumerate(fine[1:], 1): D[:, k] = cKDTree(f[3]).query(M)[0]
bones = [x['n'] for x in b.B]
skin = np.isin(g['bi'][:, 0], [i for i, x in enumerate(bones) if x.startswith(('head', 'hand', 'fin', 'thmb', 'foot', 'toes'))])
# muscles profonds (sous un muscle superficiel) : légère pénalité pour laisser la place au muscle visible
DEEP = {'erec': 1.4, 'brach': 0.8, 'add': 0.6, 'sol': 0.4, 'gmed': 0.6, 'infra': 0.4, 'teres': 0.3, 'serr': 0.3, 'vm': 0.2}
for k, f in enumerate(fine[1:], 1): D[:, k] += DEEP.get(f[0][:-1], 0)
# muscles permis selon la partie du corps (os principal) et le côté
SEG = {'thgh': 'gmax gmed tfl rf vl vm sart add grac bf st sm gasM gasL', 'shin': 'gasM gasL sol tib edl fib vl vm bf st sm sart grac',
       'uarm': 'deltA deltM deltP bic brach tri brr pec lat teres', 'farm': 'brr flex ext bic brach tri', 'twst': 'brr flex ext',
       'clav': 'deltA deltM deltP trapD trapT pec scm infra teres lat', 'hips': 'rect obl erec gmax gmed tfl lat add sart rf grac',
       'spine': 'rect obl erec lat serr trapA gmax gmed pec', 'chest': 'pec rect obl serr lat trapD trapT trapA infra teres deltA deltM deltP scm erec',
       'neck': 'scm trapD', 'head': ''}
x0 = g['pos'][:, 0]
for v_seg, allowed in SEG.items():
    rows = np.array([bones[i].startswith(v_seg) for i in g['bi'][:, 0]])
    for k, f in enumerate(fine[1:], 1):
        if f[0][:-1] not in allowed.split(): D[rows, k] = 1e9
for k, f in enumerate(fine[1:], 1):
    side = 1 if f[0].endswith('R') else -1
    D[(x0 * side < -0.8), k] = 1e9
# distances lissées sur la peau (frontières nettes et régulières), muscle = le plus proche, marge = écart avec le 2e
D[:, 0] = 8.0; D = np.minimum(D, 20.0)
for _ in range(25): D = Lm @ D
order = np.argsort(D, 1); lab = order[:, 0]
margin = np.take_along_axis(D, order[:, 1:2], 1)[:, 0] - np.take_along_axis(D, order[:, :1], 1)[:, 0]
lab[skin] = 0
# 3) fibres (espace posé), puis retour au repos par la rotation de peau de chaque sommet
fibp = np.zeros((n, 3))
for k, f in enumerate(fine[1:], 1):
    m = lab == k
    if not m.any(): continue
    pts = f[3]; ax = np.linalg.svd(pts - pts.mean(0), full_matrices=False)[2][0]
    if f[0][:-1] == 'obl': fibp[m] = [-0.55 if f[0].endswith('R') else 0.55, -0.83, 0.0]   # oblique externe : vers le bas et vers le milieu
    elif f[2] is None: fibp[m] = ax
    else:
        X = np.abs(pts[:, 0]); Y = pts[:, 1]
        scr = {'pec': X + 0.4 * Y, 'low': -Y, 'lat': X, 'latup': X + 0.7 * Y, 'latlow': X - 0.5 * Y}[f[2]]
        foc = pts[np.argsort(-scr)[:30]].mean(0); fibp[m] = M[m] - foc
fibp -= Np * (fibp * Np).sum(1, keepdims=True)                      # tangent à la peau
Rs = np.stack(R); Rv = np.einsum('nk,nkij->nij', g['bw'], Rs[g['bi']])
fr = np.einsum('nji,nj->ni', Rv, fibp)                                 # Rᵀ · d
rn = g['nrm']; fr -= rn * (fr * rn).sum(1, keepdims=True)
fr /= np.linalg.norm(fr, axis=1, keepdims=True) + 1e-9; fr[lab == 0] = 0
# 4) lignes de séparation : sommets dont un voisin appartient à un autre muscle
# grand droit de l'abdomen : 3 intersections tendineuses (les « tablettes »), entre le pubis et les côtes
y = g['pos'][:, 1]; names_f = [f[0] for f in fine]
for sd in 'RL':
    m = lab == names_f.index('rect' + sd)
    if m.any():
        y0, y1 = np.percentile(y[m], 3), np.percentile(y[m], 97)
        for t in (0.47, 0.66, 0.83): margin[m] = np.minimum(margin[m], np.abs(y[m] - (y0 + (y1 - y0) * t)) * 1.6)
line = np.clip(margin / 3.0, 0, 1)   # marge (0 à 3 cm) : 0 sur la frontière entre deux muscles → sillon, grand au centre → relief
line[lab == 0] = 1
line[skin] = 1
# sortie : groupes de l'appli (comme avant) + fibres
groups = b.hdr['labels']; gi = np.array([groups.index(f[1]) for f in fine])
shade = g['lab'][:, 3] if g['lab'] is not None else np.full(n, 255)
# relief : muscle bombé au centre, creusé le long des sillons (déplacement le long de la normale au repos, en cm)
bulge = np.clip(margin / 2.2, 0, 1); bulge = bulge * bulge * (3 - 2 * bulge)
disp = np.where(lab > 0, 0.55 * (bulge - 0.35), 0.0)
for _ in range(3): disp = Lm @ disp
disp[skin] = 0
# tendons : extrémités des muscles longs (bras, avant-bras, cuisses, jambes, cou) plus claires, comme sur une planche d'anatomie
LONG = 'bic tri brr flex ext rf vl vm sart bf st sm gasM gasL sol tib edl fib scm grac add'.split()
ten = np.zeros(n)
for k, f in enumerate(fine[1:], 1):
    if f[0][:-1] not in LONG: continue
    m = lab == k
    if m.sum() < 10: continue
    pts = f[3]; ax = np.linalg.svd(pts - pts.mean(0), full_matrices=False)[2][0]
    sv = M[m] @ ax; lo, hi = np.percentile(sv, 2), np.percentile(sv, 98); u = np.clip((sv - lo) / max(hi - lo, 1e-6), 0, 1)
    e = np.minimum(u, 1 - u); ten[m] = np.clip(1 - e / 0.14, 0, 1) ** 1.5
for _ in range(2): ten = Lm @ ten
ten[lab == 0] = 0
# couleur : groupe du muscle mélangé à la peau (gris clair) aux tendons, via le mélange déjà prévu dans l'appli (étiquette B = peau)
# contours : près de la frontière, mélange avec le muscle voisin (50/50 sur la frontière) → bord régulier au lieu de dents de scie
lab2 = order[:, 1]; lab2[skin] = 0
wb = np.clip(margin / 1.0, 0, 1); wb = 0.5 + 0.5 * wb * wb * (3 - 2 * wb)
wt = 1 - 0.85 * ten; useT = ten > 0.02
L4 = np.stack([gi[lab], np.where(useT, 0, gi[lab2]), np.round(255 * np.where(useT, wt, wb)), shade], 1)
L4[skin, 1] = L4[skin, 0]; L4[skin, 2] = 255
L4 = L4.astype('u1')
# stries des fibres : coordonnée φ (cm) qui croît perpendiculairement aux fibres, calculée muscle par muscle (moindres carrés sur les arêtes)
from scipy.sparse.linalg import spsolve
P0 = g['pos']; Gd = np.cross(fr, rn); Gd /= np.linalg.norm(Gd, axis=1, keepdims=True) + 1e-9
E = np.unique(np.sort(np.concatenate([tri[:, [0, 1]], tri[:, [1, 2]], tri[:, [2, 0]]]), 1), axis=0); i, j = E.T
k_ = (lab[i] == lab[j]) & (lab[i] > 0); i, j = i[k_], j[k_]
Gm = Gd[i] + Gd[j]; Gm /= np.linalg.norm(Gm, axis=1, keepdims=True) + 1e-9
rhs = np.einsum('ij,ij->i', P0[j] - P0[i], Gm); r_ = np.arange(len(i))
A = sp.csr_matrix((np.r_[-np.ones(len(i)), np.ones(len(i))], (np.r_[r_, r_], np.r_[i, j])), shape=(len(i), n))
phi = spsolve((A.T @ A + sp.identity(n) * 1e-4).tocsc(), A.T @ rhs); phi[lab == 0] = 0
print('stries : écart moyen', round(float(np.abs(A @ phi - rhs).mean()), 3), 'cm par arête')
F4 = np.concatenate([np.round(fr * 127 + 128).clip(0, 255), (line * 255)[:, None]], 1).astype('u1')
np.savez_compressed(__file__.replace('muscles.py', 'muscles.npz'), lab=L4, fib=F4, disp=disp.astype(np.float32), fine=lab.astype('u1'), ph=phi.astype(np.float32), names=np.array([f[0] for f in fine]))
import collections
print(n, 'sommets ;', (lab > 0).sum(), 'sur un muscle ;', int((line < .1).sum()), 'près d une séparation')

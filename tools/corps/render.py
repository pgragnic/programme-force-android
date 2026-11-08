import numpy as np, matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
from matplotlib.collections import PolyCollection
def view(out, az=-35, el=12, ax=None, title=None, extra=None):
    a, e = np.radians(az), np.radians(el)
    Ry = np.array([[np.cos(a), 0, np.sin(a)], [0, 1, 0], [-np.sin(a), 0, np.cos(a)]])
    Rx = np.array([[1, 0, 0], [0, np.cos(e), -np.sin(e)], [0, np.sin(e), np.cos(e)]])
    M = Rx @ Ry; L = np.array([.4, .8, .6]); L /= np.linalg.norm(L)
    polys = []; cols = []; depth = []
    for gi, (P, N) in enumerate(out):
        idx = G_idx[gi].reshape(-1, 3); Q = P @ M.T
        tri = Q[idx]; nn = N[idx].mean(1) @ M.T
        vis = nn[:, 2] > -0.2
        sh = np.clip((N[idx].mean(1) @ L), 0, 1) * .75 + .25
        c = np.ones((len(idx), 3)) * (.9 if gi == 0 else .25); c = c * sh[:, None]
        if gi == 0 and COL is not None: c = COL[idx].mean(1) * sh[:, None]
        polys.append(tri[vis][:, :, :2]); cols.append(c[vis]); depth.append(tri[vis][:, :, 2].mean(1))
    polys = np.concatenate(polys); cols = np.concatenate(cols); depth = np.concatenate(depth)
    o = np.argsort(depth)
    if ax is None: fig, ax = plt.subplots(figsize=(3, 4))
    ax.add_collection(PolyCollection(polys[o], facecolors=np.clip(cols[o], 0, 1), edgecolors='none'))
    if extra: extra(ax, M)
    ax.set_xlim(-90, 90); ax.set_ylim(-5, 175); ax.set_aspect('equal'); ax.axis('off')
    if title: ax.set_title(title, fontsize=7)
    return ax
G_idx = []; COL = None

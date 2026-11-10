# Subdivision du maillage du corps (hors tête, mains et pieds) : chaque arête des triangles du corps est coupée en deux,
# les triangles voisins sont redécoupés sans trou. Sert aux contours nets des muscles (rendu « écorché »).
import numpy as np

def subdivide(tri, vmask, nv):
    T = tri.reshape(-1, 3)
    body = vmask[T].all(1)
    E = np.concatenate([T[body][:, [0, 1]], T[body][:, [1, 2]], T[body][:, [2, 0]]])
    E = np.unique(np.sort(E, 1), axis=0)
    eid = {(int(a), int(b)): nv + k for k, (a, b) in enumerate(E)}
    def mid(a, b): return eid.get((min(a, b), max(a, b)))
    out = []
    for a, b, c in T.tolist():
        m = [mid(a, b), mid(b, c), mid(c, a)]; v = [a, b, c]; k = sum(x is not None for x in m)
        if k == 0: out.append((a, b, c)); continue
        if k == 3:
            ab, bc, ca = m; out += [(a, ab, ca), (ab, b, bc), (ca, bc, c), (ab, bc, ca)]; continue
        # tourner pour que l'arête non coupée (k = 2) ou coupée (k = 1) soit en position fixe
        for r in range(3):
            vv = v[r:] + v[:r]; mm = m[r:] + m[:r]
            if k == 1 and mm[0] is not None:
                x, y, z = vv; out += [(x, mm[0], z), (mm[0], y, z)]; break
            if k == 2 and mm[2] is None:
                x, y, z = vv; ab, bc = mm[0], mm[1]; out += [(ab, y, bc), (x, ab, bc), (x, bc, z)]; break
    return np.array(out, dtype=np.int64).ravel(), E

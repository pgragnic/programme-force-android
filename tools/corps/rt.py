# Moteur de poses de référence (sera porté à l'identique en Kotlin) : lit corps2.bin, résout une pose 3D, déforme le maillage.
import json, struct, numpy as np, math

def load(p):
    buf = open(p, 'rb').read(); hl = struct.unpack('<i', buf[:4])[0]; hdr = json.loads(buf[4:4+hl]); o = 4 + hl
    def take(dt, n):
        nonlocal o
        a = np.frombuffer(buf, dt, n, o); o += a.nbytes; o += (4 - o % 4) % 4; return a
    G = []
    for g in hdr['groups']:
        nv, ni = g['nv'], g['ni']; mn, mx = np.array(g['mn']), np.array(g['mx'])
        pos = mn + take('<u2', nv*3).reshape(-1, 3) / 65535 * (mx - mn)
        idx = take('<u2', ni); bi = take('u1', nv*4).reshape(-1, 4).astype(int); bw = take('u1', nv*4).reshape(-1, 4) / 255
        nr = take('i1', nv*3).reshape(-1, 3) / 127
        lab = take('u1', nv*4).reshape(-1, 4) if g['lab'] else None
        fib = take('u1', nv*4).reshape(-1, 4) if g.get('fib') else None
        ph = (take('<u2', nv).astype(float) - 32768) / 100 if g.get('ph') else None
        G.append(dict(g=g['g'], pos=pos, idx=idx.astype(int), bi=bi, bw=bw / bw.sum(1, keepdims=True), nrm=nr, lab=lab, fib=fib, ph=ph))
    B = [dict(n=b['n'], a=np.array(b['a']), b=np.array(b['b']), p=b['p']) for b in hdr['bones']]
    return hdr, B, G

def nrm(v): n = np.linalg.norm(v); return v / n if n > 1e-9 else v
def rot_between(a, b):
    a = nrm(a); b = nrm(b); v = np.cross(a, b); c = float(np.dot(a, b))
    if c < -0.999999:
        ax = nrm(np.cross(a, [1, 0, 0] if abs(a[0]) < .9 else [0, 1, 0])); return axis_angle(ax, math.pi)
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + vx + vx @ vx / (1 + c)
def axis_angle(ax, th):
    ax = nrm(np.asarray(ax, float)); x, y, z = ax; c, s = math.cos(th), math.sin(th); C = 1 - c
    return np.array([[c + x*x*C, x*y*C - z*s, x*z*C + y*s], [y*x*C + z*s, c + y*y*C, y*z*C - x*s], [z*x*C - y*s, z*y*C + x*s, c + z*z*C]])
def frame(u, v):   # repère orthonormé : u principal, v secondaire
    u = nrm(u); v = nrm(v - u * np.dot(u, v)); w = np.cross(u, v); return np.stack([u, v, w], 1)
UPV = np.array([0, 1., 0]); XV = np.array([1., 0, 0])
def frame_rot(u0, v0, u1, v1): return frame(u1, v1) @ frame(u0, v0).T

class Body:
    def __init__(self, p):
        self.hdr, self.B, self.G = load(p)
        self.idx = {b['n']: i for i, b in enumerate(self.B)}
        # repère de la main au repos : direction (poignet → jointures), axe des jointures (index → auriculaire), normale de la paume
        for k in 'RL':
            hb = self.B[self.idx['hand' + k]]
            self.__dict__['hu' + k] = nrm(hb['b'] - hb['a'])
        self.palm0 = {}
        for k in 'RL':
            # normale de paume au repos : moyenne des normales des sommets de la main côté paume ≈ direction « vers le bas » du maillage au repos
            g = self.G[0]; i = self.idx['hand' + k]
            m = (g['bi'][:, 0] == i) & (g['bw'][:, 0] > .6)
            P = g['pos'][m]; c = P.mean(0)
            # axe des jointures : de l'index vers l'auriculaire ≈ composante principale ⟂ direction de la main
            u = self.__dict__['hu' + k]; Q = P - c; Q = Q - np.outer(Q @ u, u)
            w, v = np.linalg.eigh(Q.T @ Q); lat = v[:, 2]; n = nrm(np.cross(u, lat))
            # orientation : la paume regarde vers le pouce-côté intérieur… on choisit le sens qui pointe vers le bas au repos (bras le long du corps, paume vers la cuisse/arrière)
            thumb = self.B[self.idx['thmb' + k]]['b'] - c
            if np.dot(n, thumb) < 0: n = -n
            self.palm0[k] = n; self.__dict__['hl' + k] = nrm(np.cross(n, u))

    def solve(self, Q):
        """Q : pose 3D (points en unités du maillage, repère : droite +x, haut +y, face +z).
        Renvoie rotations R[i] (3×3) et positions des têtes d'os T[i]."""
        B = self.B; I = self.idx; n = len(B)
        R = [np.eye(3)] * n; T = [None] * n
        P, N, H = (np.asarray(Q[k], float) for k in ('P', 'N', 'H'))
        S = np.asarray(Q['S'], float) if 'S' in Q else P + (N - P) * .74
        # axe latéral du buste : ligne des épaules si fournie, sinon x
        lat = np.asarray(Q.get('lat', [1, 0, 0]), float)
        def setb(i, Rw):
            b = B[i]; R[i] = Rw
            if b['p'] < 0: T[i] = P.copy()
            else:
                pb = B[b['p']]; T[i] = T[b['p']] + R[b['p']] @ (b['a'] - pb['a'])
        def hp(i):
            b = B[i]; pb = B[b['p']]; return T[b['p']] + R[b['p']] @ (b['a'] - pb['a'])
        def swing(i, tgt):
            b = B[i]; Rp = R[b['p']] if b['p'] >= 0 else np.eye(3)
            u = Rp @ nrm(b['b'] - b['a']); setb(i, rot_between(u, tgt) @ Rp)
        def dirframe(i, tgt, ref):
            b = B[i]; setb(i, frame_rot(nrm(b['b'] - b['a']), np.array([1., 0, 0]), tgt, ref))
        # buste, cou, tête : rotation depuis la posture debout de référence (verticale, épaules selon x)
        Rt = frame(S - P, lat) @ frame(UPV, XV).T
        latc = np.asarray(Q.get('latc', lat), float)
        Rc = frame(N - S, latc) @ frame(UPV, XV).T
        Rn = frame(H - N, latc) @ frame(UPV, XV).T
        setb(I['hips'], Rt); setb(I['spine'], Rt); setb(I['chest'], Rc); setb(I['neck'], Rn); setb(I['head'], Rn)
        def end(i): b = B[i]; return T[i] + R[i] @ (b['b'] - b['a'])
        for k, s in (('R', '1'), ('L', '2')):
            # clavicule : épaule demandée (haussement) sinon inchangée
            setb(I['clav' + k], R[I['chest']])
            if 'Sh' + s in Q: swing(I['clav' + k], np.asarray(Q['Sh' + s], float) - hp(I['clav' + k]))
            E = np.asarray(Q['E' + s], float); Wt = np.asarray(Q['W' + s], float)
            swing(I['uarm' + k], E - hp(I['uarm' + k]))
            fdir = Wt - end(I['uarm' + k])
            swing(I['farm' + k], fdir); swing(I['twst' + k], fdir)
            # main : direction (doigts) et normale de paume demandées
            hb = B[I['hand' + k]]; hu0 = nrm(hb['b'] - hb['a'])
            hd = nrm(np.asarray(Q['F' + s], float) - Wt) if 'F' + s in Q else nrm(fdir)
            Rt = R[I['twst' + k]]
            cur_n = Rt @ self.palm0[k]
            U = np.asarray(Q['U' + s], float) if 'U' + s in Q else cur_n
            Rh = frame_rot(hu0, self.palm0[k], hd, U)
            # torsion de l'avant-bras : part de la rotation de la main autour de l'axe de l'avant-bras
            ax = nrm(fdir); rel = Rh @ np.linalg.inv(Rt)
            # angle de torsion : rotation autour de ax qui amène la normale actuelle vers celle de la main, en projection
            a1 = cur_n - ax * np.dot(cur_n, ax); a2 = (Rh @ self.palm0[k]); a2 = a2 - ax * np.dot(a2, ax)
            if np.linalg.norm(a1) > 1e-3 and np.linalg.norm(a2) > 1e-3:
                a1, a2 = nrm(a1), nrm(a2); th = math.atan2(np.dot(np.cross(a1, a2), ax), np.dot(a1, a2))
                setb(I['farm' + k], axis_angle(ax, th * .45) @ R[I['farm' + k]])
                setb(I['twst' + k], axis_angle(ax, th * .9) @ R[I['twst' + k]])
            setb(I['hand' + k], Rh)
            c = float(Q.get('c' + s, .3))
            # doigts : rotation qui amène la direction de la main vers la paume (fermeture), pouce vers la paume aussi
            pn = Rh @ self.palm0[k]; ax_f = nrm(np.cross(Rh @ hu0, pn))
            setb(I['finA' + k], axis_angle(ax_f, c * 1.35) @ Rh)
            setb(I['finB' + k], axis_angle(ax_f, c * 1.6) @ R[I['finA' + k]])
            tb = B[I['thmb' + k]]; td = Rh @ nrm(tb['b'] - tb['a']); ax_t = nrm(np.cross(td, pn))
            setb(I['thmb' + k], axis_angle(ax_t, c * .8) @ Rh)
            # jambe
            K = np.asarray(Q['K' + s], float); A = np.asarray(Q['A' + s], float)
            swing(I['thgh' + k], K - hp(I['thgh' + k]))
            swing(I['shin' + k], A - end(I['thgh' + k]))
            fb = B[I['foot' + k]]
            Tt = np.asarray(Q['T' + s], float) if 'T' + s in Q else None
            if Tt is not None: swing(I['foot' + k], Tt - A)
            else:
                # pied par défaut : à plat vers l'avant du bassin
                fwd = R[I['hips']] @ np.array([0, 0, 1.]); fwd = nrm(fwd - np.array([0, 1., 0]) * fwd[1] * .0)
                swing(I['foot' + k], nrm(R[I['shin' + k]] @ nrm(fb['b'] - fb['a'])))
            tt = Q.get('TT' + s)
            if tt is not None: swing(I['toes' + k], np.asarray(tt, float) - end(I['foot' + k]))
            else: setb(I['toes' + k], R[I['foot' + k]])
        return R, T

    def skin(self, Q, lift=0.):
        R, T = self.solve(Q); out = []
        Rs = np.stack(R); Ts = np.stack(T); A0 = np.stack([b['a'] for b in self.B])
        for g in self.G:
            p = g['pos']; acc = np.zeros_like(p); nacc = np.zeros_like(p)
            for j in range(4):
                bi = g['bi'][:, j]; w = g['bw'][:, j][:, None]
                loc = p - A0[bi]
                acc += w * (np.einsum('nij,nj->ni', Rs[bi], loc) + Ts[bi])
                nacc += w * np.einsum('nij,nj->ni', Rs[bi], g['nrm'])
            out.append((acc, nacc / (np.linalg.norm(nacc, axis=1, keepdims=True) + 1e-9)))
        my = min(o[0][:, 1].min() for o in out)
        self.dy = lift - my
        for o in out: o[0][:, 1] += lift - my
        return out, R, T

def cr(p0, p1, p2, p3, t):
    return .5 * ((2*p1) + (-p0+p2)*t + (2*p0-5*p1+4*p2-p3)*t*t + (-p0+3*p1-3*p2+p3)*t**3)
def at(P3, k):
    ks = P3['k']
    if len(ks) == 1: return dict(ks[0])
    i = 0
    while i < len(ks) - 2 and k > ks[i+1]['t']: i += 1
    a, b = ks[i], ks[i+1]; z = ks[max(0, i-1)]; c = ks[min(len(ks)-1, i+2)]
    u = min(1, max(0, (k - a['t']) / ((b['t'] - a['t']) or 1)))
    out = {}
    for n, v in a.items():
        if n == 't': continue
        g = lambda q: np.asarray(q.get(n, v), float)
        if len(ks) == 2: r = g(a) + (g(b) - g(a)) * u
        else: r = cr(g(z), g(a), g(b), g(c), u)
        if n[0] == 'U': r = nrm(r)
        out[n] = r
    return out

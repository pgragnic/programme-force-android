import json, math, numpy as np
D = json.load(open('/home/claude/programme-force-android/app/src/main/assets/programme.json'))
P = D['POSES']; FLIP = set(D['FLIPX']); TWO = {"pull", "ohtri", "squat", "crunch", "dbugw"} | set(D["TWOHAND"])
shX, hipX = 16.741, 8.781
def pts(o): return {k: np.array(v, float) for k, v in (o or {}).items()}
def keys(id):
    p = P[id]; a = pts(p['a']); b = {**a, **pts(p.get('b'))}
    lerp = lambda t: {n: v + (b.get(n, v) - v) * t for n, v in a.items()}
    ks = [(0.0, a)] + [(m['t'], {**lerp(m['t']), **pts(m.get('p'))}) for m in p.get('kf', [])] + [(1.0, b)]
    return ks
def cr(p0, p1, p2, p3, t): return .5 * ((2*p1) + (-p0+p2)*t + (2*p0-5*p1+4*p2-p3)*t*t + (-p0+3*p1-3*p2+p3)*t**3)
def raw(id, k):
    ks = keys(id); i = 0
    while i < len(ks)-2 and k > ks[i+1][0]: i += 1
    a, b = ks[i], ks[i+1]; z = ks[max(0, i-1)]; c = ks[min(len(ks)-1, i+2)]
    if len(ks) == 2: return {n: v + (b[1].get(n, v) - v) * k for n, v in a[1].items()}
    u = min(1, max(0, (k - a[0]) / ((b[0] - a[0]) or 1)))
    g = lambda q, n, v: q[1].get(n, v)
    return {n: cr(g(z, n, v), g(a, n, v), g(b, n, v), g(c, n, v), u) for n, v in a[1].items()}
def at(id, k):
    o = raw(id, k)
    return {n: np.array([200 - v[0], v[1]]) for n, v in o.items()} if id in FLIP else o
def flag(id, f): v = P[id].get(f); return bool(v)
def build(id, k, fly_override=None):
    p = at(id, k); front = flag(id, 'front'); two = id in TWO
    def sv(q, lat=0.): return np.array([q[0]-100, 180-q[1], 0.]) if front else np.array([lat, 180-q[1], q[0]-100])
    pt = lambda n: p.get(n, p['N'])
    J = {'H': sv(pt('H')), 'Nc': sv(pt('N')), 'Pc': sv(pt('P'))}
    J['SR'] = J['Nc'] + [shX, -1.4, 0]; J['SL'] = J['Nc'] + [-shX, -1.4, 0]
    J['HR'] = J['Pc'] + [hipX, 0, 0]; J['HL'] = J['Pc'] + [-hipX, 0, 0]
    lat = P[id].get('lat'); LT = None
    if lat: LT = [lat[0][0] + (lat[1][0]-lat[0][0])*k, lat[0][1] + (lat[1][1]-lat[0][1])*k]
    aw = shX + LT[1] if LT else (5 if two else shX + 1.5); ae = shX + LT[0] if LT else (10 if two else shX + 2.5)
    if front:
        e1, w1, e2, w2 = (sv(pt(n)) for n in ('E1', 'W1', 'E2', 'W2')); ds = shX - 11
        for v in (e1, w1, e2, w2): v[0] += np.sign(v[0]) * ds
        if e1[0] > 0: e1, e2, w1, w2 = e2, e1, w2, w1
        J['E1'], J['W1'], J['E2'], J['W2'] = e2, w2, e1, w1
        k1, a1, k2, a2 = sv(pt('K2')), sv(pt('A2')), sv(pt('K1')), sv(pt('A1'))
        if k1[0] < 0: k1, k2, a1, a2 = k2, k1, a2, a1
        for v in (k1, a1, k2, a2): v[0] += np.sign(v[0]) * (hipX - 6)
        J['K1'], J['A1'], J['K2'], J['A2'] = k1, a1, k2, a2
    else:
        J['E1'] = sv(pt('E1'), ae); J['W1'] = sv(pt('W1'), aw); J['E2'] = sv(pt('E2'), -ae); J['W2'] = sv(pt('W2'), -aw)
        lw = P[id].get('legw') or [0, 0]
        LG, LA = hipX + .3 + lw[0], hipX + .6 + lw[1]
        J['K1'] = sv(pt('K1'), LG); J['A1'] = sv(pt('A1'), LA); J['K2'] = sv(pt('K2'), -LG); J['A2'] = sv(pt('A2'), -LA)
        A1, A2 = pt('A1'), pt('A2')
        J['T1'] = sv(np.array([A1[0] + 14.5, A1[1] + 4]), LA); J['T2'] = sv(np.array([A2[0] + 14.5, A2[1] + 4]), -LA)
        if 'T1' in p: J['T1'] = sv(p['T1'], LA)
        if 'T2' in p: J['T2'] = sv(p['T2'], -LA)
        if 'F1' in p: J['F1'] = sv(p['F1'], aw)
        if 'F2' in p: J['F2'] = sv(p['F2'], -aw)
        if 'S' in p: J['S'] = sv(p['S'])
        h = P[id].get('heel')
        if h:
            lift = h[0] + (h[1] - h[0]) * k
            for n in ('A1', 'A2'):
                if pt(n)[1] >= 176: J[n][1] += lift
        if flag(id, 'circ'):
            th = k * 2 * math.pi
            for sd, S_, e, w in ((1, 'SR', 'E1', 'W1'), (-1, 'SL', 'E2', 'W2')):
                Wp = J[S_] + np.array([sd * 42., 7 * math.cos(th), 7 * math.sin(th)]); J[e] = J[S_] + (Wp - J[S_]) * .5 + [0, .5, 0]; J[w] = Wp
        fl = fly_override(id, k, J) if fly_override else (flag(id, 'fly'))
        if fl is True:
            th = k * 1.35
            for sd, S, e, w in ((1, 'SR', 'E1', 'W1'), (-1, 'SL', 'E2', 'W2')):
                d = np.array([math.sin(th)*sd, -math.cos(th), 0]); J[e] = J[S] + d*24 + [0, 0, 2]; J[w] = J[S] + d*45
    sdeg = P[id].get('side')
    if sdeg:
        r = math.radians(sdeg); c, sn = math.cos(r), math.sin(r); o = J['Pc'].copy()
        for n in ('H', 'Nc', 'SR', 'SL', 'E1', 'W1', 'E2', 'W2'):
            if n in J: v = J[n]; dx, dy = v[0] - o[0], v[1] - o[1]; v[0] = o[0] + dx * c - dy * sn; v[1] = o[1] + dx * sn + dy * c
    if id in FLIP:
        for v in J.values(): v[0] = -v[0]
    return J

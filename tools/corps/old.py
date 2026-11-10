import json, struct, numpy as np
def load(p=__file__.replace('old.py', 'corps_v1.bin')):   # ancien corps (v1), retiré des assets le 8 novembre
    buf = open(p, 'rb').read(); hl = struct.unpack('<i', buf[:4])[0]; hdr = json.loads(buf[4:4+hl]); o = 4 + hl
    def al(n):
        nonlocal o; o += n; o += (4 - o % 4) % 4
    G = []
    for g in hdr['groups']:
        nv, ni = g['nv'], g['ni']; mn, mx = np.array(g['mn']), np.array(g['mx'])
        q = np.frombuffer(buf, '<u2', nv*3, o).reshape(-1, 3); pos = mn + q/65535*(mx-mn); al(nv*6)
        idx = np.frombuffer(buf, '<u2', ni, o); al(ni*2)
        bi = np.frombuffer(buf, 'u1', nv*2, o).reshape(-1, 2); al(nv*2)
        w = np.frombuffer(buf, 'u1', nv, o); al(nv)
        nr = None
        if g.get('nrm'): nr = np.frombuffer(buf, 'i1', nv*3, o).reshape(-1, 3)/127; al(nv*3)
        lab = None
        if g.get('lab'): lab = np.frombuffer(buf, 'u1', nv*4, o).reshape(-1, 4); al(nv*4)
        G.append(dict(g=g['g'], pos=pos, idx=idx, bi=bi, w=w, nrm=nr, lab=lab))
    return hdr, G

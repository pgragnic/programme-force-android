import sys, json, numpy as np, matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
sys.path.insert(0, '.'); import rt, render
b = rt.Body('corps2.bin'); render.G_idx = [g['idx'] for g in b.G]
H = b.hdr['hand']
P3 = json.load(open('poses3.json')); D = json.load(open('/home/claude/programme-force-android/app/src/main/assets/programme.json'))
ids = sys.argv[2].split(',') if len(sys.argv) > 2 else list(P3)
byp = {}
for k, e in D['EX'].items(): byp.setdefault(e.get('pose', k), k)
def draw(id, k, ax, az=-50, el=15):
    Q = rt.at(P3[id], k); out, R, T = b.skin(Q)
    def extra(axx, M):
        tool = D['EX'].get(byp.get(id, ''), {}).get('tool', '')
        dbs = P3[id].get('db', [])
        if tool not in ('db', 'kb') or not dbs: return
        for w in (['W1'] if P3[id].get('two') else dbs):
            k_ = 'R' if w == 'W1' else 'L'; i = b.idx['hand' + k_]
            g = T[i] + R[i] @ np.array(H[k_]['g']) + np.array([0, b.dy, 0]); a = R[i] @ np.array(H[k_]['l'])
            p1, p2 = (g - a * 8) @ M.T, (g + a * 8) @ M.T
            axx.plot([p1[0], p2[0]], [p1[1], p2[1]], color='#1a9', lw=4, solid_capstyle='butt')
    render.view(out, az=az, el=el, ax=ax, title=f'{id} {k}', extra=extra)
SH = [0]
if __name__ == '__main__':
    n = int(sys.argv[3]) if len(sys.argv) > 3 else 12
    for s in range(0, len(ids), n):
        sub = ids[s:s+n]
        fig, axs = plt.subplots(len(sub), 3, figsize=(6, 2.1 * len(sub)))
        axs = np.atleast_2d(axs)
        for r, id in enumerate(sub):
            for c, k in enumerate((0, .5, 1)): draw(id, k, axs[r][c])
        plt.tight_layout(pad=.2); plt.savefig(f'{sys.argv[1]}{s//n}.png', dpi=55); plt.close()
    print('ok')

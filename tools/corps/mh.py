import numpy as np, json, os
D = '/home/claude/makehumancommunity/makehuman/makehuman/data'
def base():
    V = []; F = []
    cur = None
    for l in open(D + '/3dobjs/base.obj'):
        if l.startswith('v '): V.append([float(x) for x in l.split()[1:4]])
        elif l.startswith('g '): cur = l.split()[1]
        elif l.startswith('f ') and cur == 'body': F.append([int(t.split('/')[0]) - 1 for t in l.split()[1:]])
    return np.array(V), np.array(F)
def target(name):
    d = {}
    for l in open(D + '/targets/' + name):
        if l[0].isdigit():
            p = l.split(); d[int(p[0])] = [float(x) for x in p[1:4]]
    idx = np.array(list(d.keys()), dtype=int); off = np.array(list(d.values()))
    return idx, off
def apply(V, items):
    V = V.copy()
    for name, w in items:
        i, o = target(name); V[i] += o * w
    return V
MORPH = [('macrodetails/universal-male-young-maxmuscle-averageweight.target', 1.0), ('macrodetails/caucasian-male-young.target', 1.0)]
def skel(V):
    s = json.load(open(D + '/rigs/default.mhskel'))
    J = {k: V[v].mean(0) for k, v in s['joints'].items()}
    B = {}
    for n, b in s['bones'].items():
        B[n] = dict(head=J[b['head']], tail=J[b['tail']], parent=b['parent'])
    return B
def weights():
    return json.load(open(D + '/rigs/default_weights.mhw'))['weights']

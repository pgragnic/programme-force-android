# Pont fessier (Philippe, 24 novembre 2026 18 h 22, capture de la pause entre deux séries : « La tête est relevée alors qu'elle devrait être posée »)
# La tête et le cou restaient au-dessus du sol (~9 unités au départ) : on les descend image par image jusqu'à ce que l'arrière de la tête
# soit à la même hauteur (seule la tête descend, le cou garde son angle) que le haut du dos (point le plus bas du thorax), donc posée au sol.
# Lancer : cd tools/corps && python3 fix8.py
import json, numpy as np
from fix6 import P3, PJ, skin, NM, PB, lowest, shift

def head_down(id, it=10):
    for k in P3[id]['k']:
        for _ in range(it):
            V, _, _ = skin(P3, id, k)
            d = lowest(V, ['head']) - lowest(V, ['chest'])
            if abs(d) <= .3: break
            shift(k, ['H'], -d)
if __name__ == '__main__':
    for id in ('bridge', 'bridge2'):
        head_down(id)
        for k in P3[id]['k']:
            V, _, _ = skin(P3, id, k); print(id, k['t'], 'tête', round(float(lowest(V, ['head'])), 2), 'thorax', round(float(lowest(V, ['chest'])), 2), 'N', k['N'][1], 'H', k['H'][1])
    json.dump(P3, open('poses3.json', 'w'), ensure_ascii=False, separators=(',', ':'))
    D = json.load(open(PJ)); D['POSES3'] = P3; json.dump(D, open(PJ, 'w'), ensure_ascii=False, separators=(',', ':'))
    print('ok')

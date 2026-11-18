# Pont fessier (Philippe, 18 novembre 2026 12 h 31, capture du lecteur : « Ce qui m'intéresse c'est Pont fessier, le bras devrait rester au sol je pense ? »)
# En montant, les épaules descendent vers le sol mais les coudes restaient vers leur position de départ : les bras se soulevaient.
# Coudes recalculés à chaque image clé, pliés vers le sol et un peu vers l'extérieur : bras allongés au sol, paumes au sol.
# Lancer : cd tools/corps && python3 fix7.py
import json, numpy as np
from fix6 import P3, PJ, joints, elbow_pole, real_hands, contact, SIDES

from fix6 import ARM
def arms_down(id):
    """bras presque tendus le long du corps, posés au sol (hauteur des poignets de départ), un peu écartés"""
    fy = P3[id]['k'][0]['W1'][1]
    for k in P3[id]['k']:
        _, J = joints(k)
        for s, sd in SIDES:
            sh = J['uarm' + sd]; L = (ARM[0] + ARM[1]) * .99; x = 1 if sd == 'R' else -1
            dy = fy - sh[1]; hz = np.sqrt(max(L * L - dy * dy, 1)); d = np.array([.28 * hz * x, dy, .96 * hz])
            W = sh + d / np.linalg.norm(d) * L; old = np.array(k['W' + s], float)
            k['W' + s] = [round(float(v), 2) for v in W]
            if 'F' + s in k: k['F' + s] = [round(float(v), 2) for v in np.array(k['F' + s]) + W - old]
            elbow_pole(k, s, (1, .15, 0))
        contact(id, k, hands='RL')

if __name__ == '__main__':
    for id in ('bridge', 'bridge2'): arms_down(id)
    for k in P3['bridge']['k'][::4]:
        _, J = joints(k); print(k['t'], 'épaule', np.round(J['uarmR'][1], 1), 'coude', np.round(J['farmR'][1], 1), 'poignet', np.round(J['handR'][1], 1))
    json.dump(P3, open('poses3.json', 'w'), ensure_ascii=False, separators=(',', ':'))
    D = json.load(open(PJ)); D['POSES3'] = P3; json.dump(D, open(PJ, 'w'), ensure_ascii=False, separators=(',', ':'))
    print('ok')

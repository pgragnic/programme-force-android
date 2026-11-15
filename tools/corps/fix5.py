# Corrections de la revue des positions (Philippe, 15 novembre 2026 : « Peux-tu déjà revoir tout ce que tu as déjà remarqué
# dans les contrôles automatiques ? »). Seuls les vrais défauts vus sur la planche de revue sont corrigés ; les remarques
# voulues (jambe libre du pont fessier 10° au-dessus du sol, dos rond du chat, chevilles tendues à plat ventre…) restent.
#  - pompes contre le mur : les mains ne touchaient pas le mur (6 à 10 cm d'écart) et avançaient pendant le mouvement ;
#  - mollets sur une jambe : la main d'appui entrait de 3 cm dans le mur ;
#  - fente avant : le pied avant glissait au sol sur 54 cm au lieu de faire un pas ;
#  - soulevé de terre sur une jambe : le pied d'appui glissait de 6 cm ;
#  - développé incliné : en bas, les disques intérieurs entraient dans les épaules ;
#  - crunch lesté : l'haltère était tenu sous le menton au lieu de sur la poitrine.
# Lancer : cd tools/corps && python3 fix5.py  (réécrit poses3.json et POSES3 de programme.json)
import json, numpy as np, rt
import fix4
from fix4 import B, NM, PB, skin, lowest, knee, elbow, densify, Q_

P3 = json.load(open('poses3.json'))
PJ = '../../app/src/main/assets/programme.json'
D = json.load(open(PJ))
G = B.G[0]
def verts(sd, names): return np.isin(G['bi'][:, 0], [NM.index(n + sd) for n in names]) & (G['bw'][:, 0] > .5)
HANDV = {sd: verts(sd, ('hand', 'finA', 'finB', 'thmb')) for sd in 'RL'}
VEC = lambda k: [n for n, v in k.items() if n != 't' and isinstance(v, list) and n[0] not in 'U' and n not in ('lat', 'latc')]
r2 = lambda v: np.asarray(v, float).round(2).tolist()

def hand_front(k, sd):
    """avant de la paume (z le plus grand) de la main, en cm, dans le repère de l'appli (posé au sol)"""
    V, _, _ = skin(P3, cur, k); return V[HANDV[sd], 2].max()

# ---------- pompes contre le mur ----------
cur = 'wallpush'; p = P3[cur]
target = min(hand_front(k, sd) for k in p['k'] for sd in 'RL') + .5   # bras tendus au départ : la portée la plus courte fixe le mur
for k in p['k']:
    for s, sd in (('1', 'R'), ('2', 'L')):
        for _ in range(12):   # le bras avance (coude, poignet, doigts) jusqu'à ce que la paume touche le mur
            d = target - hand_front(k, sd)
            if abs(d) < .2: break
            for n in ('E' + s, 'W' + s, 'F' + s): k[n] = r2(np.array(k[n]) + [0, 0, d * .9])
# mur posé contre les paumes (prop x = surface + 99,5 : centre = x − 100 + 2, demi-épaisseur 1,5)
p['prop']['x'] = round(target + 99.5 + .3, 1)
print('pompes au mur : paumes à', round(target, 1), 'cm, mur x =', p['prop']['x'],
      '; écarts restants', [round(target - hand_front(k, sd), 1) for k in p['k'] for sd in 'RL'])

# ---------- mollets sur une jambe : mur contre la main ----------
cur = 'calf1'; p = P3[cur]
f = max(hand_front(k, 'L') for k in p['k'])
p['prop']['x'] = round(f + 99.5 + .3, 1)
print('mollets sur une jambe : main à', round(f, 1), 'cm, mur x =', p['prop']['x'])

# ---------- soulevé de terre sur une jambe : pied d'appui fixe ----------
def pin_foot(id, s, ks=None):
    """déplace toute la pose à chaque image clé pour que le pied d'appui reste où il est au départ"""
    sd = 'R' if s == '1' else 'L'; p = P3[id]
    def foot(k): _, R, T = B.skin({kk: np.asarray(v, float) for kk, v in Q_(k).items()}); return T[NM.index('foot' + sd)][[0, 2]]
    ref = foot(p['k'][0])
    for k in p['k'][1:] if ks is None else ks:
        for _ in range(3):
            d = ref - foot(k)
            if np.linalg.norm(d) < .2: break
            for n in VEC(k): k[n] = r2(np.array(k[n]) + [d[0], 0, d[1]])
cur = 'sldl'; densify(P3['sldl']); pin_foot('sldl', '2')

# ---------- fente avant : un vrai pas ----------
cur = 'fwdlunge'; p = P3[cur]
k0, kl, k1 = p['k']   # départ, pose du pied (t = 0,35), fin
# le pied avant se pose directement à sa place finale, puis ne bouge plus
for n in ('A1', 'T1'): kl[n] = list(k1[n])
kl['A1'] = r2(np.array(k1['A1']) + [0, 1.0, 0]); kl['T1'] = r2(np.array(k1['T1']) + [0, 3.0, 0])   # talon d'abord, pointe encore levée
knee(kl, '1')
# pas : pied levé à mi-chemin
km = json.loads(json.dumps({'t': .17, **{kk: (np.asarray(v).round(3).tolist() if not np.isscalar(v) else v) for kk, v in rt.at(p, .17).items()}}))
A0, A1 = np.array(k0['A1'], float), np.array(k1['A1'], float)
km['A1'] = r2((A0 + A1) / 2 + [0, 12, 0]); km['T1'] = r2(np.array(km['A1']) + [0, -5, 13.5])
km['K1'] = r2(np.array(km['K1']) + [0, 8, 6]); knee(km, '1')
p['k'] = sorted([k0, km, kl, k1], key=lambda k: k['t'])

# ---------- développé incliné : en bas, haltères au-dessus des épaules ----------
cur = 'inclinepress'; p = P3[cur]
k = p['k'][0]
up = np.array(k['N'], float) - k['P']; up /= np.linalg.norm(up)
for s, sx in (('1', 1), ('2', -1)):
    for n in ('W' + s, 'F' + s):
        if n in k: k[n] = r2(np.array(k[n]) + up * 5 + [3 * sx, 0, 0])
    elbow(k, s)

# ---------- crunch lesté : haltère sur la poitrine ----------
cur = 'crunch'; p = P3[cur]
for k in p['k']:
    dn = np.array(k['P'], float) - k['N']; dn /= np.linalg.norm(dn)   # du cou vers le bassin
    nu = np.array([0., -dn[2], dn[1]]); nu = nu if nu[1] > 0 else -nu   # perpendiculaire au buste, vers le plafond
    for s in '12':
        for n in ('W' + s, 'F' + s):
            if n in k: k[n] = r2(np.array(k[n]) + dn * 7)
        elbow(k, s)
    # la même pose sert au crunch au médecine-ball : mains écartées de la poitrine jusqu'à ce que ballon et haltère n'y entrent plus
    for _ in range(10):
        if fix4.tool_pen(P3, 'crunch', k, 'medball')[0] <= 2: break
        for s in '12':
            for n in ('W' + s, 'F' + s):
                if n in k: k[n] = r2(np.array(k[n]) + nu * 1)
            elbow(k, s)

json.dump(P3, open('poses3.json', 'w'), ensure_ascii=False, separators=(',', ':'))
D['POSES3'] = P3
json.dump(D, open(PJ, 'w'), ensure_ascii=False, separators=(',', ':'))
print('ok')

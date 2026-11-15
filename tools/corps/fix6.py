# Revue des positions, verdicts de Philippe sur la planche (15 novembre 2026, « À revoir ») : « Ce que tu as déjà remarqué »
# plus ses propres remarques. Chaque fonction corrige une pose ; lancer : cd tools/corps && python3 fix6.py [poses]
import sys, json, numpy as np, rt
import fix4
from fix4 import B, NM, PB, skin, lowest, knee, elbow, shift, densify, two_bone, Q_

P3 = json.load(open('poses3.json'))
PJ = '../../app/src/main/assets/programme.json'
r2 = lambda v: np.asarray(v, float).round(2).tolist()
nrm = lambda v: np.asarray(v, float) / np.linalg.norm(v)
def L(sd, a, b, c):
    A_, B_, C_ = (B.B[NM.index(n + sd)] for n in (a, b, c))
    return np.linalg.norm(B_['a'] - A_['a']), np.linalg.norm(C_['a'] - B_['a'])
ARM = L('R', 'uarm', 'farm', 'hand'); LEG = L('R', 'thgh', 'shin', 'foot')
def joints(k):
    R, T = B.solve(Q_(k)); return R, {n: T[NM.index(n)] for n in NM}
HANDS = {sd: ['hand' + sd, 'finA' + sd, 'finB' + sd] for sd in 'RL'}
FEET = {sd: ['foot' + sd, 'toes' + sd] for sd in 'RL'}
SIDES = (('1', 'R'), ('2', 'L'))
TORSO = ('P', 'S', 'N', 'H')

def real_hands(k):
    """cibles des poignets (et des doigts) ramenées sur la position réelle des poignets : la cible n'était qu'une direction"""
    _, J = joints(k)
    for s, sd in SIDES:
        w = J['hand' + sd]; d = w - np.array(k['W' + s], float)
        k['W' + s] = r2(w)
        if 'F' + s in k: k['F' + s] = r2(np.array(k['F' + s]) + d)
def real_feet(k):
    _, J = joints(k)
    for s, sd in SIDES:
        a = J['foot' + sd]; d = a - np.array(k['A' + s], float)
        for x in ('A', 'T', 'TT'):
            if x + s in k: k[x + s] = r2(np.array(k[x + s]) + d)

def elbow_pole(k, s, pole):
    """coude recalculé à la bonne longueur, plié dans une direction imposée (pole, côté droit ; miroir à gauche) : il ne s'inverse plus
    entre deux images clés quand le bras est presque tendu"""
    sd = 'R' if s == '1' else 'L'; _, J = joints(k); sh = J['uarm' + sd]; W = np.array(k['W' + s], float)
    pv = np.array(pole, float) * ([1, 1, 1] if sd == 'R' else [-1, 1, 1])
    k['E' + s] = r2(two_bone(sh, W, ARM[0], ARM[1], pv))

def contact(id, k, hands=(), feet=(), it=8):
    """appuis au sol : mains (poignet et doigts) et pieds (cheville et orteils) descendus ou remontés jusqu'au contact,
    coude ou genou recalculé à la bonne longueur ; le sol est le point le plus bas du corps, comme dans l'appli"""
    lim = [('W', 'F', s, HANDS[sd], 2.1, elbow) for s, sd in SIDES if sd in hands] + \
          [('A', 'T', s, FEET[sd], .8, knee) for s, sd in SIDES if sd in feet]
    for _ in range(it):
        V, _, _ = skin(P3, id, k); worst = 0
        for a, b_, s, bones, tg, ik in lim:
            d = lowest(V, bones) - tg
            if abs(d) > .3: shift(k, [a + s, b_ + s] + (['TT' + s] if a == 'A' else []), -d); ik(k, s); worst = max(worst, abs(d))
        if worst <= .3: break
    return k

def pike(P3):
    """Pompes en V (Philippe : « Toutes les images sont les mêmes ») : les coudes restaient tendus, la tête ne descendait que de 2 cm.
    Mains et pieds fixes ; le buste bascule vers le sol entre les mains (épaules au-dessus des mains, tête à une main du sol),
    coudes pliés, bassin qui reste haut."""
    p = P3['pike']; k0 = json.loads(json.dumps(p['k'][0]))
    real_hands(k0); real_feet(k0)
    _, J = joints(k0)
    P0 = np.array(k0['P'], float); sh0 = (J['uarmR'] + J['uarmL']) / 2
    hw = (np.array(k0['W1']) + np.array(k0['W2'])) / 2
    sh1 = hw + np.array([0, 24, 2])            # en bas : épaules 24 cm au-dessus des poignets, à l'aplomb des mains
    lt = {n: np.linalg.norm(np.array(k0[n], float) - P0) for n in ('S', 'N') if n in k0}
    hn = np.array(k0['H'], float) - np.array(k0['N'], float)
    ks = []
    for i in range(9):
        t = i / 8; k = json.loads(json.dumps(k0)); k['t'] = t
        P = P0 + np.array([0, -16, 14]) * t; k['P'] = r2(P)   # le bassin avance et descend un peu (jambes tendues, pieds fixes)
        sh = sh0 + (sh1 - sh0) * t; d = nrm(sh - P)
        for n, l in lt.items(): k[n] = r2(P + d * l)
        # tête dans le prolongement du buste, regard vers les mains
        k['H'] = r2(np.array(k['N']) + nrm(nrm(hn) * (1 - t) + d * t) * np.linalg.norm(hn))
        for s, sd in SIDES: elbow(k, s)
        ks.append(k)
    p['k'] = ks
    for k in ks:
        contact('pike', k, hands='RL', feet='RL')
    return P3

def plank_line(id, k, foot_len=14.5, tuck=True):
    """corps gainé : bassin et jambes tendues dans le prolongement épaules → bassin, incliné juste assez pour que les pointes
    des pieds touchent le sol en même temps que les mains (recherche par dichotomie de l'angle)"""
    _, J = joints(k); Sm = (J['uarmR'] + J['uarmL']) / 2; P0 = np.array(k['P'], float)
    lt = np.linalg.norm(P0 - Sm); back = nrm(np.array([0, 0, (P0 - Sm)[2]]))
    def build(th):
        d = back * np.cos(th) + np.array([0, -1., 0]) * np.sin(th)
        k['P'] = r2(Sm + d * lt); _, J2 = joints(k)
        for s, sd in SIDES:
            hip = J2['thgh' + sd]; K = hip + d * LEG[0]; A = K + d * LEG[1] * .995
            n = np.array([0, -d[2], d[1]]); n = n if n[1] < 0 else -n
            f = nrm(n * .9 - d * .15) if tuck else nrm(d)   # orteils repliés, pointe au sol
            k['K' + s] = r2(K); k['A' + s] = r2(A); k['T' + s] = r2(A + f * foot_len); k.pop('TT' + s, None)
        V, _, _ = skin(P3, id, k)
        return min(lowest(V, FEET['R']), lowest(V, FEET['L'])) - min(lowest(V, HANDS['R']), lowest(V, HANDS['L']))
    lo, hi = np.radians(-10), np.radians(40)
    for _ in range(18):
        mid = (lo + hi) / 2
        if build(mid) > 0: lo = mid      # pieds trop hauts : on incline davantage
        else: hi = mid
    return build((lo + hi) / 2)

def pushup(P3):
    """Pompes (Philippe : « les pieds du personnage ne touchent pas le sol ») : en haut, les jambes partaient plus haut que la ligne
    du buste et les pieds flottaient à 16 cm. Corps gainé d'une seule ligne des épaules aux talons, pointes de pieds au sol."""
    for id in ('pushup',):
        for k in P3[id]['k']:
            g = plank_line(id, k)
            contact(id, k, hands='RL')
    return P3

def grip_y(id, k):
    """hauteur réelle (au sol de l'appli) du milieu des deux prises"""
    _, R, T = B.skin({kk: np.asarray(v, float) for kk, v in Q_(k).items()})
    g = [T[NM.index('hand' + sd)] + R[NM.index('hand' + sd)] @ B.grip0[sd] for sd in 'RL']
    V, _, _ = skin(P3, id, k)
    off = (B.dy + fix4.APP_DY)
    return (g[0][1] + g[1][1]) / 2 + off

def tucked_feet(id, k, sides=SIDES):
    """à genoux, orteils repliés : tibia qui remonte du genou vers la cheville, pied vers le sol, avant-pied posé au niveau des genoux"""
    _, J = joints(k)
    for s, sd in sides:
        Kp = J['shin' + sd]; back = nrm(np.array([0, 0, (J['foot' + sd] - Kp)[2]]))
        k['K' + s] = r2(Kp)
        def build(a):
            d = back * np.cos(a) + np.array([0, 1., 0]) * np.sin(a)
            A = Kp + d * LEG[1] * .995; f = nrm(np.array([0, -1., 0]) * .93 + back * .37)
            k['A' + s] = r2(A); k['T' + s] = r2(A + f * 11.7); k.pop('TT' + s, None)
            V, _, _ = skin(P3, id, k)
            return lowest(V, FEET[sd]) - lowest(V, ['shin' + sd])
        lo, hi = 0., np.radians(45)
        for _ in range(16):
            m = (lo + hi) / 2
            if build(m) > 0: hi = m       # orteils trop hauts : tibia moins relevé
            else: lo = m
        build((lo + hi) / 2)

def abwheel(P3):
    """Roue abdominale à genoux (Philippe : « Ce que tu as déjà remarqué + la roue n'est pas par terre à l'étape de fin ») : les mains
    montaient en fin de mouvement (roue 12 cm au-dessus du sol), les pieds décollaient de 17 cm et glissaient. Roue posée au sol tout
    le long (milieu des prises à 9 cm, rayon de la roue), genoux au sol, orteils repliés au sol (cheville moins tendue)."""
    id = 'abwheel'; densify(P3[id])
    for k in P3[id]['k']:
        tucked_feet(id, k)
        for _ in range(10):
            d = 9.0 - grip_y(id, k)
            if abs(d) < .2: break
            for s in '12':
                shift(k, ['W' + s, 'F' + s], d); elbow(k, s)
    return P3

def chairdip(P3):
    """Dips sur chaise (Philippe : « les mains ne touchent pas la chaise ») : les mains étaient 5 cm sous le dessus de l'assise, contre
    le bord. Paumes posées sur l'assise près du bord avant, doigts qui accrochent le bord ; mains fixes, coudes qui plient."""
    id = 'chairdip'; p = P3[id]; densify(p)
    pr = p['prop']; z0 = pr.get('x', 100) - 100; top = 44 + ((pr.get('y') or 45.5) - 45.5) + 1.5; edge = z0 + 20
    palm = {sd: ['hand' + sd] for sd in 'RL'}
    for k in p['k']:
        # en haut, bras presque tendus : le corps monte (il était presque assis sur la chaise, coudes à 110°)
        up = 5 * (1 - k['t']) + 7; bk = -5 * (1 - k['t'])   # et se rapproche de la chaise
        for n in TORSO + ('E1', 'E2', 'W1', 'W2', 'F1', 'F2'):
            if n in k: k[n] = r2(np.array(k[n]) + [0, up, bk])
        if k['t'] == 0:   # pieds fixes au sol (ils glissaient de 4 cm) : là où la jambe les pose en haut du mouvement
            knee(k, '1'); knee(k, '2'); real_feet(k); feet0 = {x: list(k[x]) for x in ('A1', 'T1', 'TT1', 'A2', 'T2', 'TT2') if x in k}
        k.update(json.loads(json.dumps(feet0)))
        knee(k, '1'); knee(k, '2')
        for s, sd in SIDES:
            w = np.array(k['W' + s], float); w[2] = edge - 5; k['W' + s] = r2(w)
            k['F' + s] = r2(w + [0, -3.5, 9]); k['U' + s] = [0, -1, 0]; k['c' + s] = .5
            elbow(k, s)
        for _ in range(10):
            V, _, _ = skin(P3, id, k); worst = 0
            for s, sd in SIDES:
                d = lowest(V, palm[sd]) - (top + .3)
                if abs(d) > .2: shift(k, ['W' + s, 'F' + s], -d); elbow(k, s); worst = max(worst, abs(d))
            if worst <= .2: break
    return P3

def calf1(P3):
    """Mollets sur une jambe (Philippe : « le bras qui touche le mur n'est pas droit ») : bras d'appui tendu devant, à hauteur d'épaule,
    paume à plat sur le mur, doigts vers le haut ; le mur est placé contre la paume."""
    id = 'calf1'; p = P3[id]; G = B.G[0]
    hv = np.isin(G['bi'][:, 0], [NM.index(n + 'L') for n in ('hand', 'finA', 'finB', 'thmb')]) & (G['bw'][:, 0] > .5)
    front = 0
    km = min(p['k'], key=lambda k: abs(k['t'] - .5)); _, J = joints(km); d = nrm(np.array([.06, -.12, 1.]))   # un peu vers l'intérieur et vers le bas
    def off(k): skin(P3, id, k); return B.dy + fix4.APP_DY   # décalage pose → sol de l'appli (le corps est posé par son point le plus bas)
    Ww = J['uarmL'] + d * (ARM[0] + ARM[1]) * .985 + [0, off(km), 0]   # main fixe sur le mur (au sol de l'appli), bras presque tendu
    for k in p['k']:
        _, J = joints(k); sh = J['uarmL']; W = Ww - [0, off(k), 0]
        k['W2'] = r2(W); elbow_pole(k, '2', (.25, -1, 0))   # bras presque tendu, coude vers le bas
        k['F2'] = r2(W + [0, 9, 0]); k['U2'] = [0, 0, 1]; k['c2'] = .05
        V, _, _ = skin(P3, id, k); front = max(front, V[hv, 2].max())
    p['prop']['x'] = round(front + 99.5 + .3, 1)
    return P3

A0 = 73.74   # angle jambe / pied au repos (degrés) ; pointe tendue de x degrés = A0 − x entre les deux
def foot_toward_floor(d, fx):
    """direction du pied faite à partir de la direction du tibia d : pointe tendue de fx degrés, pointe vers le sol"""
    th = np.radians(A0 - fx); n = np.array([0, -d[2], d[1]]); n = n if n[1] < 0 else -n
    return nrm(d * np.cos(th) + nrm(n) * np.sin(th))
def prone_feet(id, k, fx=45, rest=True):
    """à plat ventre : pointe tendue de fx degrés (elle l'était de 70°), pointes de pieds posées au sol (rest) ou jambes levées telles quelles"""
    _, J = joints(k)
    for s, sd in SIDES:
        Kp = J['shin' + sd]; d0 = nrm(J['foot' + sd] - Kp)
        def build(a):
            back = nrm(np.array([0, 0, d0[2]])); d = back * np.cos(a) + np.array([0, 1., 0]) * np.sin(a) if rest else d0
            A = Kp + d * LEG[1] * .995; f = foot_toward_floor(d, fx)
            k['K' + s] = r2(Kp); k['A' + s] = r2(A); k['T' + s] = r2(A + f * 14.5); k.pop('TT' + s, None)
            V, _, _ = skin(P3, id, k); return lowest(V, FEET[sd]) - lowest(V, ['shin' + sd, 'thgh' + sd])
        if not rest: build(0); continue
        lo, hi = np.radians(-5), np.radians(40)
        for _ in range(16):
            m = (lo + hi) / 2
            if build(m) < 0: lo = m      # orteils sous le niveau du genou : tibia plus relevé
            else: hi = m
        build((lo + hi) / 2)

def superman(P3):
    """Superman (« Ce que tu as déjà remarqué ») : au repos, pieds et mains flottaient à 6–7 cm du sol, chevilles tendues à 70°.
    Au repos, mains et pointes de pieds posées ; chevilles moins tendues sur tout le mouvement."""
    id = 'superman'; densify(P3[id], 4)
    for k in P3[id]['k']:
        prone_feet(id, k, rest=k['t'] == 0)
        if k['t'] == 0: contact(id, k, hands='RL')
    return P3

def pronet(P3):
    """Écartés à plat ventre (« Ce que tu as déjà remarqué ») : pieds à 7 cm du sol, chevilles tendues à 70°. Pointes de pieds posées."""
    id = 'pronet'; densify(P3[id], 4)
    for k in P3[id]['k']: prone_feet(id, k)
    return P3

def hands_under_shoulders(id, k, sides=SIDES, ahead=2.):
    """appui sur les mains : poignet à l'aplomb de l'épaule (2 cm devant), doigts vers l'avant, paume au sol ; le poignet se plie
    alors d'environ 90°, ce qu'impose une main à plat sous un bras vertical (il dépassait 100°, épaules en avant des mains)"""
    _, J = joints(k)
    for s, sd in sides:
        w = np.array(k['W' + s], float); w[2] = J['uarm' + sd][2] + ahead; k['W' + s] = r2(w)
        k['F' + s] = r2(w + [0, 0, 9]); k['U' + s] = [0, -1, 0]; elbow(k, s)
    contact(id, k, hands=''.join(sd for _, sd in sides))

def level_quadruped(id, k, sides=SIDES):
    """à quatre pattes, bras tendus verticaux : épaules à hauteur de bras, plus haut que le bassin ; le haut du corps monte ou descend
    jusqu'à ce que mains et genoux touchent le sol ensemble"""
    up = ['N', 'S', 'H'] + [x + s for s, _ in sides for x in ('E', 'W', 'F')]
    for _ in range(12):
        V, _, _ = skin(P3, id, k)
        knee_ = min(lowest(V, ['shinR']), lowest(V, ['shinL'])) - 1.2
        hand_ = min(lowest(V, HANDS[sd]) for _, sd in sides) - 2.1
        d = knee_ - hand_
        if abs(d) < .3: break
        for n in up:
            if n in k: k[n] = r2(np.array(k[n]) + [0, d * .9, 0])
        for s, _ in sides: elbow(k, s)

def quad_base(id, k, arms=SIDES):
    """quatre pattes construit directement : cuisses verticales sous les hanches, genoux et pointes de pieds au sol (cheville tendue
    de 45°), bras tendus verticaux sous les épaules, mains au sol ; le haut du corps monte ou descend pour que mains et genoux
    touchent le sol ensemble"""
    _, J = joints(k)
    for s, sd in SIDES:
        hip = J['thgh' + sd]; k['K' + s] = r2(hip + [0, -LEG[0] * .998, 0])
    prone_feet(id, k, rest=True)
    def arms_down():
        _, J = joints(k)
        for s, sd in arms:
            sh = J['uarm' + sd] + [1.5 * (1 if sd == 'R' else -1), 0, 0]
            k['E' + s] = r2(sh + [0, -ARM[0], 1.]); k['W' + s] = r2(sh + nrm([0, -1, .04]) * (ARM[0] + ARM[1]) * .998)
            k['F' + s] = r2(np.array(k['W' + s]) + [0, 0, 9]); k['U' + s] = [0, -1, 0]
    up = ['N', 'S', 'H']
    for _ in range(10):
        arms_down(); V, _, _ = skin(P3, id, k)
        d = (min(lowest(V, ['shinR']), lowest(V, ['shinL'])) - 1.2) - (min(lowest(V, HANDS[sd]) for _, sd in arms) - 2.1)
        if abs(d) < .3: break
        for n in up:
            if n in k: k[n] = r2(np.array(k[n]) + [0, d, 0])
    arms_down()

def birddog(P3):
    """Bird dog (« Ce que tu as déjà remarqué ») : poignets pliés à 101° (épaules en avant des mains), cheville de la jambe tendue
    pointée à 69°, pied d'appui qui décollait au début. Quatre pattes refait (cuisses et bras verticaux) ; pied de la jambe tendue
    fléchi (pointe vers le sol) ; genou et pointe du pied d'appui posés."""
    id = 'birddog'; p = P3[id]
    k0 = p['k'][0]; quad_base(id, k0)
    k1 = p['k'][-1]
    for x in ('P', 'N', 'S', 'H', 'E1', 'W1', 'F1', 'U1', 'K2', 'A2', 'T2'):
        if x in k0: k1[x] = json.loads(json.dumps(k0[x]))
    # bras gauche tendu devant à hauteur d'épaule, jambe droite tendue derrière à hauteur de hanche
    _, J = joints(k1)
    sh = J['uarmL']; k1['E2'] = r2(sh + nrm([0, .08, 1]) * ARM[0]); k1['W2'] = r2(sh + nrm([0, .08, 1]) * (ARM[0] + ARM[1]) * .998)
    k1['F2'] = r2(np.array(k1['W2']) + [0, 0, 9]); k1['U2'] = [1, 0, 0]
    hp = J['thghR']; d = nrm([0, .05, -1]); k1['K1'] = r2(hp + d * LEG[0]); k1['A1'] = r2(hp + d * (LEG[0] + LEG[1]) * .995)
    k1['T1'] = r2(np.array(k1['A1']) + foot_toward_floor(d, 10) * 14.5); k1.pop('TT1', None)
    densify(p, 4)
    for k in p['k']:
        if 0 < k['t'] < 1:
            for s in '12':
                elbow(k, s); knee(k, s)
            _, J = joints(k); d = nrm(J['footR'] - J['shinR'])
            k['T1'] = r2(J['footR'] + foot_toward_floor(d, 10 + 35 * (1 - k['t']) * 0) * 14.5)
    return P3

def catcow(P3):
    """Chat-vache (« Ce que tu as déjà remarqué ») : épaules plus basses que le bassin, bras pliés à 115°, dos plié à 54° en vache,
    chevilles tendues à 63°. Même quatre pattes que le bird dog (bras tendus, épaules au-dessus des mains, cuisses verticales) ;
    le milieu du dos monte (chat, tête rentrée) puis descend (vache, tête relevée), mains et genoux fixes."""
    id = 'catcow'; base = json.loads(json.dumps(P3['birddog']['k'][0])); base.pop('S', None)
    P = np.array(base['P'], float); N = np.array(base['N'], float); ks = []
    for t, dy, hd in ((0., 7., (0, -13, 7)), (1., -5., (0, 9, 12))):
        k = json.loads(json.dumps(base)); k['t'] = t
        k['S'] = r2(P + (N - P) * .55 + [0, dy, 0]); k['H'] = r2(N + np.array(hd, float))
        ks.append(k)
    P3[id]['k'] = ks; densify(P3[id], 4)
    _, J0 = joints(base); sh0 = (J0['uarmR'] + J0['uarmL']) / 2
    for k in P3[id]['k']:
        for _ in range(6):   # épaules gardées à hauteur de bras : seul le milieu du dos bouge
            _, J = joints(k); d = sh0 - (J['uarmR'] + J['uarmL']) / 2
            if np.linalg.norm(d) < .3: break
            for n in ('N', 'H'): k[n] = r2(np.array(k[n]) + d)
        for s, x in (('1', 'W1'), ('2', 'W2')):
            k[x] = list(base[x]); k['F' + s] = list(base['F' + s]); elbow(k, s)
        contact(id, k, hands='RL')
    return P3

EP_PUSH = (.7, .25, -.65)   # pompes : coudes vers l'extérieur et l'arrière (environ 45° du corps)
def incline(P3):
    """Pompes sur les genoux (« Ce que tu as déjà remarqué ») : en haut, poignets pliés à 115° (épaules en avant des mains), chevilles
    tendues à 72°. Mains fixes, à l'aplomb des épaules en haut du mouvement ; pieds toujours dans le prolongement des jambes (demande
    du 11 novembre) mais cheville tendue de 45°."""
    id = 'incline'; p = P3[id]; k0 = p['k'][0]
    _, J = joints(k0); Wz = {s: J['uarm' + sd][2] + 2 for s, sd in SIDES}
    for k in p['k']:
        for s, sd in SIDES:
            w = np.array(k['W' + s], float); w[2] = Wz[s]; k['W' + s] = r2(w); k['F' + s] = r2(w + [0, 0, 9]); elbow_pole(k, s, EP_PUSH)
        level_quadruped(id, k)
        contact(id, k, hands='RL')
        for s, _ in SIDES: elbow_pole(k, s, EP_PUSH)
        _, J = joints(k)
        for s, sd in SIDES:
            d = nrm(J['foot' + sd] - J['shin' + sd]); k['T' + s] = r2(J['foot' + sd] + foot_toward_floor(d, 45) * 14.5); k.pop('TT' + s, None)
    return P3

def knee_fwd(k, s):
    """genou recalculé à la bonne longueur, toujours plié vers l'avant (jambe presque tendue : le sens de pliage n'est pas ambigu)"""
    sd = 'R' if s == '1' else 'L'; _, J = joints(k)
    hip = J['thgh' + sd]; A = np.array(k['A' + s], float)
    k['K' + s] = r2(two_bone(hip, A, LEG[0], LEG[1], np.array([0, 0, 1.])))

def ballfoot(P3):
    """Balle de massage sous le pied (« Ce que tu as déjà remarqué ») : la balle suivait le pied et s'enfonçait dedans (pied à 4 cm du sol
    pour une balle de 7 cm). Balle fixe au sol sous la voûte, pied posé dessus qui roule d'avant en arrière."""
    id = 'ballfoot'; p = P3[id]; densify(p, 16)   # images clés serrées : entre deux clés, le pied ne replonge pas dans la balle
    zs = []
    for k in p['k']:
        _, R, T = B.skin({kk: np.asarray(v, float) for kk, v in Q_(k).items()}); zs.append(T[NM.index('footR')][2])
    p['prop']['z'] = round(float(np.mean(zs)) + 4, 1)
    for k in p['k']:
        for _ in range(8):
            V, _, _ = skin(P3, id, k); d = 7.3 - lowest(V, FEET['R'])
            if abs(d) < .2: break
            shift(k, ['A1', 'T1', 'TT1'], d); knee_fwd(k, '1')
    return P3

FUN = {'ballfoot': ballfoot, 'incline': incline, 'birddog': birddog, 'catcow': catcow, 'superman': superman, 'pronet': pronet, 'calf1': calf1, 'pike': pike, 'pushup': pushup, 'abwheel': abwheel, 'chairdip': chairdip}
if __name__ == '__main__':
    only = sys.argv[1].split(',') if len(sys.argv) > 1 else list(FUN)
    for id in only: FUN[id](P3)
    json.dump(P3, open('poses3.json', 'w'), ensure_ascii=False, separators=(',', ':'))
    D = json.load(open(PJ)); D['POSES3'] = P3; json.dump(D, open(PJ, 'w'), ensure_ascii=False, separators=(',', ':'))
    print('ok')

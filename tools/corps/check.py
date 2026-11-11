# Contrôle automatique de toutes les poses à chaque modification (Philippe, 11 novembre : « Partons sur 2,3,5 et 7 », point 7).
# Lancé par .github/workflows/poses.yml (et à la main : cd tools/corps && python3 check.py).
#  1. Contrôle strict de audit2.py (appuis posés, appuis qui glissent, chevilles, poignets, cou, dos, consignes, charges dans le corps)
#     comparé à la référence audit_ref.json : une remarque nouvelle, ou pire de plus de la tolérance, fait échouer le contrôle.
#  2. Planche de toutes les poses (début, milieu, fin) pour un coup d'œil : controle/poses.png.
#  3. Liste des poses modifiées depuis le commit précédent (controle/modifiees.txt), capturées ensuite sur téléphone virtuel.
# Une remarque acceptée (vérifiée à l'œil) s'ajoute à la référence avec : python3 check.py --accepter
import sys, os, json, subprocess, numpy as np
os.chdir(os.path.dirname(os.path.abspath(__file__)))
OUT = '../../controle'; os.makedirs(OUT, exist_ok=True)
TOL = {'cm': 1.5, '°': 6, 'points': 6}

def tol(key): return next((v for u, v in TOL.items() if f'({u})' in key), 2)

subprocess.run([sys.executable, 'audit2.py', f'{OUT}/audit.json'], check=True, stdout=open(f'{OUT}/audit.txt', 'w'))
cur = json.load(open(f'{OUT}/audit.json'))
if '--accepter' in sys.argv:
    json.dump(cur, open('audit_ref.json', 'w'), ensure_ascii=False, indent=1); print('référence mise à jour'); sys.exit(0)
ref = json.load(open('audit_ref.json'))
bad = []
for id, iss in cur.items():
    for key, vals in iss.items():
        m = max(v[1] for v in vals); r = ref.get(id, {}).get(key)
        if r is None: bad.append(f'{id} : nouvelle remarque « {key} » (max {m})')
        elif m > max(v[1] for v in r) + tol(key): bad.append(f'{id} : « {key} » passe de {max(v[1] for v in r)} à {m}')

# poses modifiées depuis le commit précédent
try:
    old = json.loads(subprocess.run(['git', 'show', 'HEAD~1:tools/corps/poses3.json'], capture_output=True, text=True, check=True).stdout)
    now = json.load(open('poses3.json'))
    changed = sorted(id for id in now if json.dumps(now[id], sort_keys=True) != json.dumps(old.get(id), sort_keys=True))
except Exception: changed = []
open(f'{OUT}/modifiees.txt', 'w').write(' '.join(changed))

# planche de toutes les poses
import rt, render, matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
b = rt.Body('corps2.bin'); render.G_idx = [g['idx'] for g in b.G]; P3 = json.load(open('poses3.json')); ids = sorted(P3)
cols = 9; rows = (len(ids) * 3 + cols - 1) // cols
fig, axs = plt.subplots(rows, cols, figsize=(cols * 2.2, rows * 2.4)); axs = axs.ravel()
for i, (id, t) in enumerate((id, t) for id in ids for t in (0, .5, 1)):
    Q = {k: np.asarray(v) for k, v in rt.at(P3[id], t).items()}
    lift = max(0, min(Q['A1'][1], Q['A2'][1]) - 2) if P3[id].get('free') else 0
    out, _, _ = b.skin(Q, lift)
    lying = abs(Q['N'][1] - Q['P'][1]) < .55 * np.linalg.norm(np.array(Q['N']) - Q['P'])
    render.view(out, az=-70 if lying else -40, el=12, ax=axs[i], title=f'{id} {t:g}' + (' ⚠' if any(id in x for x in bad) else ''))
for a in axs[len(ids) * 3:]: a.axis('off')
plt.tight_layout(); plt.savefig(f'{OUT}/poses.png', dpi=55)

print(f'{sum(1 for v in cur.values() if not v)} poses sans remarque sur {len(cur)} ; poses modifiées : {" ".join(changed) or "aucune"}')
if bad:
    print('RÉGRESSIONS :'); [print('  ' + x) for x in bad]
    open(f'{OUT}/regressions.txt', 'w').write('\n'.join(bad)); sys.exit(1)
print('Aucune régression.')

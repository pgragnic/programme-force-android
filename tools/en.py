# Ajoute le dictionnaire anglais (clé EN) au programme
# Usage : python3 tools/en.py app/src/main/assets/programme.json
import json, sys
sys.path.insert(0, 'tools')
from en_ui import UI
from en_data import DATA
p = sys.argv[1]; d = json.load(open(p))
from avoid import EN as AVOID
EN = {}; EN.update(DATA); EN.update(UI); EN.update(AVOID)
EN.update({'Langue': 'Language', 'Français': 'Français', 'English': 'English'})
d['EN'] = EN
json.dump(d, open(p, 'w'), ensure_ascii=False, separators=(',', ':'))
print(len(EN), 'traductions')

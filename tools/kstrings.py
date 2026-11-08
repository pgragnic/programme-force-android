# Extrait les chaînes Kotlin (avec gestion des ${...} imbriqués) : renvoie (début, fin, morceaux, a_template)
import re
def strings(src):
    out = []; i = 0; n = len(src)
    def skip_comment(i):
        if src.startswith('//', i): j = src.find('\n', i); return n if j < 0 else j
        if src.startswith('/*', i): j = src.find('*/', i); return n if j < 0 else j + 2
        return None
    def parse_str(i):  # i sur le guillemet ouvrant ; renvoie (fin, morceaux, template)
        j = i + 1; parts = []; cur = ''; tpl = False
        while j < n:
            c = src[j]
            if c == '\\': cur += src[j:j+2]; j += 2; continue
            if c == '"': parts.append(cur); return j + 1, parts, tpl
            if c == '$' and j + 1 < n and src[j+1] == '{':
                tpl = True; parts.append(cur); cur = ''; j = parse_code(j + 2, '}'); continue
            if c == '$' and j + 1 < n and (src[j+1].isalpha() or src[j+1] == '_'):
                tpl = True; parts.append(cur); cur = ''; j += 1
                while j < n and (src[j].isalnum() or src[j] == '_'): j += 1
                continue
            cur += c; j += 1
        return j, parts, tpl
    def parse_code(j, end):
        depth = 0
        while j < n:
            sc = skip_comment(j)
            if sc is not None: j = sc; continue
            c = src[j]
            if src.startswith('"""', j): k = src.find('"""', j + 3); j = k + 3; continue
            if c == '"': e, p, t = parse_str(j); out.append((j, e, p, t)); j = e; continue
            if c == "'": j = src.find("'", j + 2 if src[j+1] == '\\' else j + 1) + 1; continue
            if c == '{': depth += 1
            elif c == '}':
                if depth == 0 and end == '}': return j + 1
                depth -= 1
            j += 1
        return j
    parse_code(0, None)
    return out

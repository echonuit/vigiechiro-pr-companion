"""Passe C : fait entrer dans le graphe les fichiers que graphify ne classe pas.

graphify laisse .fxml, .css, .bats et le registre ServiceLoader en `unclassified`
(absents de la table des langages AST, et pas des documents). Ils portent pourtant des
references explicites, donc EXTRACTED :

  C1 vues     : .fxml -> classe controleur (fx:controller) et -> feuilles .css (@x.css)
  C2 modules  : META-INF/services/...ModuleDeFeature -> classes de modules declarees
  C3 CLI E2E  : .bats -> un noeud par @test -> classe de commande picocli exercee
                (resolution par @Command(name=...), jamais par kebab devine)

Lit .graphify_extract.json s'il existe (chainage apres pont-doc-code.py), sinon graph.json.
"""
import json
import re
from pathlib import Path

SRC = Path('graphify-out/.graphify_extract.json')
if not SRC.exists():
    SRC = Path('graphify-out/graph.json')
g = json.loads(SRC.read_text(encoding='utf-8'))
nodes = {n['id']: n for n in g['nodes']}
edges = g.get('edges') if 'edges' in g else g['links']
print(f'depart : {SRC.name} - {len(nodes)} noeuds, {len(edges)} aretes')


def nid(path, suffixe=None):
    """Identifiant de niveau fichier : chemin complet normalise, extension conservee
    comme jeton pour ne jamais entrer en collision avec la classe Java homonyme."""
    base = re.sub(r'[^a-z0-9]+', '_', str(path).lower()).strip('_')
    return f'{base}_{suffixe}' if suffixe else base


def ajouter_noeud(path, label=None, file_type='code'):
    i = nid(path)
    if i not in nodes:
        nodes[i] = {'id': i, 'label': label or Path(path).name, 'file_type': file_type,
                    'source_file': str(path), 'source_location': 'L1',
                    'norm_label': (label or Path(path).name).lower(), '_origin': 'pont'}
    return i


vues = set()
def lier(src, dst, relation, contexte, sf, ligne):
    k = (src, dst, relation)
    if src == dst or k in vues:
        return 0
    vues.add(k)
    edges.append({'relation': relation, 'confidence': 'EXTRACTED', 'confidence_score': 1.0,
                  'source_file': sf, 'source_location': f'L{ligne}', 'weight': 1.0,
                  '_origin': 'pont', 'context': contexte, 'source': src, 'target': dst})
    return 1


for e in edges:
    vues.add((e['source'], e['target'], e.get('relation')))

# ------------------------------------------------- index des classes Java par nom pleinement qualifie
par_fqn, par_fichier = {}, {}
for n in list(nodes.values()):
    sf = n.get('source_file') or ''
    if sf.endswith('.java') and n.get('_origin') == 'ast':
        stem = Path(sf).stem
        if n['label'] in (stem, stem + '.java'):
            par_fichier[sf] = n['id']
            m = re.match(r'src/(?:main|test)/java/(.+)\.java$', sf)
            if m:
                par_fqn[m.group(1).replace('/', '.')] = n['id']
print(f'index : {len(par_fqn)} classes Java par FQN')

# ------------------------------------------------------------------------ C1 : vues FXML
n_fxml = n_ctrl = n_css = 0
for p in sorted(Path('src/main/java').rglob('*.fxml')) + sorted(Path('src/main/resources').rglob('*.fxml')):
    t = p.read_text(encoding='utf-8', errors='ignore')
    lignes = t.splitlines()
    fid = ajouter_noeud(p)
    n_fxml += 1
    for i, ln in enumerate(lignes, 1):
        m = re.search(r'fx:controller="([\w.]+)"', ln)
        if m and m.group(1) in par_fqn:
            n_ctrl += lier(fid, par_fqn[m.group(1)], 'references', 'fx_controller', str(p), i)
        for css in re.findall(r'@([\w./-]+\.css)|value="[^"]*?([\w./-]+\.css)"', ln):
            nom = css[0] or css[1]
            cible = (p.parent / nom).resolve()
            try:
                rel = cible.relative_to(Path('.').resolve())
            except ValueError:
                continue
            if cible.exists():
                n_css += lier(fid, ajouter_noeud(rel), 'references', 'stylesheet', str(p), i)
print(f'C1 : {n_fxml} vues FXML, {n_ctrl} liens vers un controleur, {n_css} liens vers une feuille CSS')

# ---------------------------------------------------------------- C2 : registre ServiceLoader
n_reg = 0
for p in sorted(Path('src/main/resources/META-INF/services').glob('*')):
    if not p.is_file():
        continue
    rid = ajouter_noeud(p, label=p.name)
    for i, ln in enumerate(p.read_text(encoding='utf-8', errors='ignore').splitlines(), 1):
        ln = ln.strip()
        if not ln or ln.startswith('#'):
            continue
        if ln in par_fqn:
            n_reg += lier(rid, par_fqn[ln], 'references', 'service_loader', str(p), i)
print(f'C2 : {n_reg} modules declares relies au registre ServiceLoader')

# ------------------------------------------------------------------------- C3 : E2E bats
# nom de commande picocli : source autoritaire, le kebab devine echoue (audit-coherence -> Auditer)
commandes = {}
for sf, cid in par_fichier.items():
    if '/cli/' not in sf:
        continue
    t = Path(sf).read_text(encoding='utf-8', errors='ignore')
    m = re.search(r'@Command\s*\(([^)]*)\)', t, re.S)
    if m:
        nom = re.search(r'name\s*=\s*"([^"]+)"', m.group(1))
        if nom:
            commandes[nom.group(1)] = cid
print(f'     {len(commandes)} noms de commandes picocli indexes')

n_bats = n_test = n_inv = 0
non_resolues = set()
for p in sorted(Path('src/test/bats').glob('*.bats')):
    lignes = p.read_text(encoding='utf-8', errors='ignore').splitlines()
    bid = ajouter_noeud(p)
    n_bats += 1
    # tableaux de commandes definis dans le fichier
    tableaux, courant = {}, None
    for ln in lignes:
        m = re.match(r'\s*([A-Z_][A-Z0-9_]*)=\(', ln)
        if m:
            courant = m.group(1)
            tableaux[courant] = set(re.findall(r'[a-z][a-z0-9-]{2,}', ln.split('(', 1)[1]))
            continue
        if courant is not None:
            if ')' in ln:
                tableaux[courant] |= set(re.findall(r'[a-z][a-z0-9-]{2,}', ln.split(')')[0]))
                courant = None
            else:
                tableaux[courant] |= set(re.findall(r'[a-z][a-z0-9-]{2,}', ln))
    # blocs @test
    blocs = []
    for i, ln in enumerate(lignes):
        m = re.match(r'@test\s+"(.+?)"', ln)
        if m:
            blocs.append([i, m.group(1)])
    for j, (deb, titre) in enumerate(blocs):
        fin = blocs[j + 1][0] if j + 1 < len(blocs) else len(lignes)
        corps = '\n'.join(lignes[deb:fin])
        slug = re.sub(r'[^a-z0-9]+', '_', titre.lower())[:48].strip('_')
        tid = f'{bid}_test_{slug}'
        if tid not in nodes:
            nodes[tid] = {'id': tid, 'label': titre[:90], 'file_type': 'code',
                          'source_file': str(p), 'source_location': f'L{deb + 1}',
                          'norm_label': titre[:90].lower(), '_origin': 'pont'}
            n_test += 1
        lier(bid, tid, 'contains', 'bats_test', str(p), deb + 1)
        # `cli api lire --chemin ...` : capter la chaine complete, pas seulement le
        # premier jeton, sinon les sous-commandes passent pour non couvertes.
        invoquees = set()
        for m in re.finditer(r'(?:run\s+)?\bcli\s+((?:[a-z][a-z0-9-]*\s+){0,3})', corps):
            for jeton in m.group(1).split():
                invoquees.add(jeton)
        for nom_tab in re.findall(r'\$\{([A-Z_][A-Z0-9_]*)\[@\]\}', corps):
            invoquees |= tableaux.get(nom_tab, set())
        for cmd in sorted(invoquees):
            if cmd in commandes:
                n_inv += lier(tid, commandes[cmd], 'references', 'cli_invocation', str(p), deb + 1)
            elif re.match(r'^[a-z][a-z0-9-]{3,}$', cmd):
                non_resolues.add(cmd)
print(f'C3 : {n_bats} fichiers bats, {n_test} tests, {n_inv} liens test -> commande CLI')
print(f'     commandes invoquees non resolues : {len(non_resolues)} -> {sorted(non_resolues)[:12]}')

couvertes = {e['target'] for e in edges if e.get('context') == 'cli_invocation'}
print(f'     commandes CLI couvertes par au moins un bats : {len(couvertes)}/{len(commandes)}')

extraction = {'nodes': list(nodes.values()), 'edges': edges,
              'hyperedges': g.get('hyperedges', []), 'input_tokens': 0, 'output_tokens': 0}
Path('graphify-out/.graphify_extract.json').write_text(
    json.dumps(extraction, ensure_ascii=False), encoding='utf-8')
print(f'\nresultat : {len(nodes)} noeuds, {len(edges)} aretes')

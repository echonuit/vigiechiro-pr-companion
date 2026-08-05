"""Relie la documentation au code dans le graphe graphify.

Deux passes deterministes, sans appel LLM :
  A. dedoublonnage : les noeuds file_type=code loges dans un fichier de doc sont
     des doublons de classes AST reelles ; on recable leurs aretes vers la vraie
     classe et on supprime le fantome.
  B. citation : toute classe nommee DANS UN SPAN DE CODE d'un document produit une
     arete `references` EXTRACTED du noeud de niveau fichier vers la classe.
     Le texte libre est ecarte (homonymes : Passage, Importer, Verdict...).
"""
import json
import re
from pathlib import Path

DOCDIRS = ('brief/', 'dev-docs/', 'docs/')

# Chaine sur .graphify_extract.json quand il existe (sortie d'un --update encore
# non clusterise), sinon repart de graph.json.
GRAPH = Path('graphify-out/.graphify_extract.json')
if not GRAPH.exists():
    GRAPH = Path('graphify-out/graph.json')
g = json.loads(GRAPH.read_text(encoding='utf-8'))
nodes = {n['id']: n for n in g['nodes']}
edges = g['edges'] if 'edges' in g else g['links']
print(f'depart : {GRAPH.name} - {len(nodes)} noeuds, {len(edges)} aretes')

# ---------------------------------------------------------------- index classes
classes = {}
for n in g['nodes']:
    sf = n.get('source_file') or ''
    if sf.endswith('.java') and n.get('_origin') == 'ast':
        stem = Path(sf).stem
        if n['label'] in (stem, stem + '.java'):
            classes.setdefault(stem.lower(), (stem, n['id']))
print(f'index : {len(classes)} classes Java')


def est_doc(n):
    return (n.get('source_file') or '').startswith(DOCDIRS)


# ------------------------------------------------------- PASSE A : dedoublonnage
remap, non_resolus = {}, []
for n in g['nodes']:
    if est_doc(n) and n.get('file_type') == 'code':
        tok = re.split(r'[^A-Za-z0-9_]', n['label'].strip())[0].lower()
        hit = classes.get(tok) or classes.get(n['id'].split('_')[-1])
        if hit and hit[1] != n['id']:
            remap[n['id']] = hit[1]
        else:
            non_resolus.append(n['id'])
print(f'passe A : {len(remap)} fantomes resolus, {len(non_resolus)} laisses en place')

# Le rationale porte par le fantome decrit la classe : on le transfere si la
# classe n'en a pas deja un, sinon on le perd au moment de la suppression.
transferes = 0
for gid, cid in remap.items():
    r = nodes[gid].get('rationale')
    if r and not nodes[cid].get('rationale'):
        nodes[cid]['rationale'] = r
        nodes[cid]['rationale_source'] = nodes[gid].get('source_file')
        transferes += 1
print(f'         {transferes} rationale transferes sur la classe')

recablees, boucles = 0, 0
nouvelles = []
for e in edges:
    s, t = remap.get(e['source'], e['source']), remap.get(e['target'], e['target'])
    if (s, t) != (e['source'], e['target']):
        recablees += 1
        if s == t:
            boucles += 1
            continue
        e = {**e, 'source': s, 'target': t}
    nouvelles.append(e)
edges = nouvelles
for gid in remap:
    nodes.pop(gid, None)
print(f'         {recablees} aretes recablees, {boucles} boucles supprimees')

# Dedoublonnage exact des aretes nees du recablage
vues, propres = set(), []
for e in edges:
    k = (e['source'], e['target'], e.get('relation'), e.get('source_file'), e.get('source_location'))
    if k in vues:
        continue
    vues.add(k)
    propres.append(e)
print(f'         {len(edges) - len(propres)} aretes doublons supprimees')
edges = propres

# ----------------------------------------------------------- PASSE B : citations
# noeud de niveau fichier par document
fichier_node = {}
for n in nodes.values():
    if est_doc(n) and n.get('file_type') == 'document':
        sf = n['source_file']
        cur = fichier_node.get(sf)
        if cur is None or len(n['id']) < len(cur['id']):
            fichier_node[sf] = n


def norm_id(p):
    return re.sub(r'[^a-z0-9]+', '_', str(p).rsplit('.', 1)[0].lower()).strip('_')


docs = [p for d in ('brief', 'dev-docs', 'docs') for p in Path(d).rglob('*.md')]
docs += [p for p in Path('.').glob('*.md') if p.name != 'CHANGELOG.md']

existantes = {(e['source'], e['target']) for e in edges}
ajoutees, crees = 0, 0
for p in sorted(docs):
    sf = str(p)
    texte = p.read_text(encoding='utf-8', errors='ignore')
    lignes = texte.splitlines()
    # spans de code : inline `...` et blocs ``` ... ```
    spans = []  # (contenu, no_ligne)
    dans_bloc = False
    for i, ln in enumerate(lignes, 1):
        if ln.lstrip().startswith('```'):
            dans_bloc = not dans_bloc
            continue
        if dans_bloc:
            spans.append((ln, i))
        else:
            for m in re.finditer(r'`([^`\n]{1,120})`', ln):
                spans.append((m.group(1), i))
    if not spans:
        continue
    trouves = {}
    for contenu, no in spans:
        for mot in re.findall(r'\b[A-Za-z][A-Za-z0-9_]{4,}\b', contenu):
            hit = classes.get(mot.lower())
            if hit and hit[0] == mot:          # casse exacte du nom de classe
                trouves.setdefault(hit[1], no)
    if not trouves:
        continue
    src = fichier_node.get(sf)
    if src is None:                            # le document n'a pas de noeud de fichier
        titre = next((l.lstrip('# ').strip() for l in lignes if l.startswith('# ')), p.stem)
        src = {'id': norm_id(sf), 'label': titre[:80] or p.stem, 'file_type': 'document',
               'source_file': sf, 'source_location': 'L1', 'norm_label': (titre[:80] or p.stem).lower(),
               '_origin': 'pont'}
        if src['id'] in nodes:
            src['id'] = src['id'] + '_doc'
        nodes[src['id']] = src
        fichier_node[sf] = src
        crees += 1
    for cid, no in trouves.items():
        if (src['id'], cid) in existantes or (cid, src['id']) in existantes or src['id'] == cid:
            continue
        edges.append({'relation': 'references', 'confidence': 'EXTRACTED', 'confidence_score': 1.0,
                      'source_file': sf, 'source_location': f'L{no}', 'weight': 1.0,
                      '_origin': 'pont', 'context': 'code_span',
                      'source': src['id'], 'target': cid})
        existantes.add((src['id'], cid))
        ajoutees += 1
print(f'passe B : {ajoutees} aretes de citation ajoutees, {crees} noeuds de document crees')

# ------------------------------------------------------------------- ecriture
extraction = {
    'nodes': list(nodes.values()),
    'edges': edges,
    'hyperedges': g.get('hyperedges', []),
    'input_tokens': 0,
    'output_tokens': 0,
}
Path('graphify-out/.graphify_extract.json').write_text(
    json.dumps(extraction, ensure_ascii=False), encoding='utf-8')
print(f'\nresultat : {len(nodes)} noeuds, {len(edges)} aretes')

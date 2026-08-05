"""Passe E : les modeles conceptuels (.mcd, format Mocodo) entrent dans le graphe.

Le format est structure, donc parse exactement plutot que confie a un agent :
  `Nom: attribut, attribut, ...`            -> entite
  `Verbe, 1N Entite A, 11 Entite B`         -> association avec cardinalites

Les entites sont EXTRACTED. Le rapprochement entite <-> classe Java du meme nom est en
revanche une INFERENCE (0.85 : alignement fonctionnel clair, aucun lien symbolique),
marquee comme telle et jamais presentee comme extraite.
"""
import json
import re
import unicodedata
from pathlib import Path

SRC = Path('graphify-out/.graphify_extract.json')
if not SRC.exists():
    SRC = Path('graphify-out/graph.json')
g = json.loads(SRC.read_text(encoding='utf-8'))
nodes = {n['id']: n for n in g['nodes']}
edges = g['edges'] if 'edges' in g else g['links']
print(f'depart : {SRC.name} - {len(nodes)} noeuds, {len(edges)} aretes')
vues = {(e['source'], e['target'], e.get('relation')) for e in edges}


def sansaccent(s):
    return ''.join(c for c in unicodedata.normalize('NFD', s)
                   if unicodedata.category(c) != 'Mn')


def norm(s):
    return re.sub(r'[^a-z0-9]+', '', sansaccent(s).lower())


def nid(s):
    return re.sub(r'[^a-z0-9]+', '_', sansaccent(str(s)).lower()).strip('_')


def lier(s, t, rel, ctx, sf, ligne, conf='EXTRACTED', score=1.0):
    if s == t or (s, t, rel) in vues:
        return 0
    vues.add((s, t, rel))
    edges.append({'relation': rel, 'confidence': conf, 'confidence_score': score,
                  'source_file': str(sf), 'source_location': f'L{ligne}', 'weight': 1.0,
                  '_origin': 'pont', 'context': ctx, 'source': s, 'target': t})
    return 1


# classes Java par nom normalise, pour le rapprochement INFERRED
classes = {}
for n in nodes.values():
    sf = n.get('source_file') or ''
    if sf.endswith('.java') and n.get('_origin') == 'ast':
        stem = Path(sf).stem
        if n['label'] in (stem, stem + '.java'):
            classes.setdefault(norm(stem), n['id'])

n_ent = n_rel = n_lien = 0
for p in sorted(Path('.').rglob('*.mcd')):
    if 'target/' in str(p) or str(p).startswith('site'):
        continue
    lignes = p.read_text(encoding='utf-8', errors='ignore').splitlines()
    fid = nid(p)
    if fid not in nodes:
        nodes[fid] = {'id': fid, 'label': p.name, 'file_type': 'document',
                      'source_file': str(p), 'source_location': 'L1',
                      'norm_label': p.name.lower(), '_origin': 'pont'}
    entites = {}
    # 1er passage : les entites
    for i, ln in enumerate(lignes, 1):
        ln = ln.strip()
        if not ln or ':' not in ln:
            continue
        nom, attrs = ln.split(':', 1)
        nom = nom.strip()
        if not nom or ',' in nom:
            continue
        eid = f'{fid}_{nid(nom)}'
        if eid not in nodes:
            nodes[eid] = {'id': eid, 'label': nom, 'file_type': 'concept',
                          'source_file': str(p), 'source_location': f'L{i}',
                          'norm_label': nom.lower(), '_origin': 'pont',
                          'rationale': f'Attributs : {attrs.strip()}'}
            n_ent += 1
        entites[nom] = eid
        lier(fid, eid, 'contains', 'mcd_entite', p, i)
        # rapprochement avec la classe Java homonyme : INFERENCE assumee
        cible = classes.get(norm(nom))
        if cible:
            n_lien += lier(eid, cible, 'conceptually_related_to', 'mcd_classe', p, i,
                           conf='INFERRED', score=0.85)
    # 2e passage : les associations
    for i, ln in enumerate(lignes, 1):
        ln = ln.strip()
        if not ln or ':' in ln or ',' not in ln:
            continue
        parts = [x.strip() for x in ln.split(',')]
        verbe, pattes = parts[0], parts[1:]
        cibles = []
        for patte in pattes:
            m = re.match(r'^([0-9N]{2})\s+(.+)$', patte)
            if m and m.group(2) in entites:
                cibles.append((m.group(1), entites[m.group(2)]))
        for a in range(len(cibles)):
            for b in range(a + 1, len(cibles)):
                n_rel += lier(cibles[a][1], cibles[b][1], 'shares_data_with',
                              f'mcd {cibles[a][0]}-{verbe}-{cibles[b][0]}', p, i)
    print(f'  {p}: {len(entites)} entites')

print(f'E : {n_ent} entites, {n_rel} associations, {n_lien} rapprochements entite<->classe (INFERRED 0.85)')
Path('graphify-out/.graphify_extract.json').write_text(json.dumps(
    {'nodes': list(nodes.values()), 'edges': edges, 'hyperedges': g.get('hyperedges', []),
     'input_tokens': 0, 'output_tokens': 0}, ensure_ascii=False), encoding='utf-8')
print(f'\nresultat : {len(nodes)} noeuds, {len(edges)} aretes')

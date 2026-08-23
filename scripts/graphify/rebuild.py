#!/usr/bin/env python3
"""Reconstruit le graphe de connaissance graphify apres un commit ou une fusion.

Enchaine, sans aucun appel LLM :
  1. extraction AST des fichiers de code modifies, puis fusion dans graph.json
  2. les quatre passes de pont (doc/code, vues/CLI, CI/pom, MCD)
  3. clustering, libelles, GRAPH_REPORT.md et graph.html

L'extraction semantique de la documentation, elle, demande un LLM : quand un `.md`
change, on se contente de poser le drapeau `graphify-out/.needs_update`, que l'on
resorbe en session avec `/graphify . --update`.

Usage :
    python3 scripts/graphify/rebuild.py [fichier ...]

Sans argument, reconstruit a partir du graphe existant sans rien re-extraire.
Les chemins passes en argument sont relatifs a la racine du depot.
"""
from __future__ import annotations

import json
import runpy
import sys
from collections import Counter
from pathlib import Path

RACINE = Path(__file__).resolve().parents[2]
SORTIE = RACINE / 'graphify-out'
GRAPHE = SORTIE / 'graph.json'
EXTRAIT = SORTIE / '.graphify_extract.json'
PASSES = ('pont_doc_code.py', 'pont_ressources.py', 'pont_ci.py', 'pont_mcd.py')
EXT_CODE = {'.java', '.sql'}
EXT_DOC = {'.md'}


def journal(message: str) -> None:
    print(f'[graphify] {message}', flush=True)


def extraire_le_code(changes: list[Path]) -> bool:
    """Re-extrait les fichiers de code modifies et les fusionne dans graph.json."""
    from graphify.build import build_merge
    from graphify.extract import extract

    fichiers = [p for p in changes if p.suffix in EXT_CODE and (RACINE / p).exists()]
    if not fichiers:
        return False
    journal(f'{len(fichiers)} fichier(s) de code modifie(s), extraction AST')
    # root= est obligatoire : sans lui, source_file ET les identifiants sont
    # tronques au nom de fichier, ce qui rend les noeuds introuvables par chemin.
    resultat = extract([RACINE / p for p in fichiers], cache_root=RACINE, root=RACINE)
    nouveau = {
        'nodes': resultat['nodes'], 'edges': resultat['edges'],
        'hyperedges': [], 'input_tokens': 0, 'output_tokens': 0,
    }
    graphe = build_merge([nouveau], graph_path=str(GRAPHE), root=str(RACINE), directed=False)
    EXTRAIT.write_text(json.dumps({
        'nodes': [{'id': n, **d} for n, d in graphe.nodes(data=True)],
        'edges': [
            {**{k: v for k, v in d.items() if k not in ('_src', '_tgt', 'source', 'target')},
             'source': d.get('_src', u), 'target': d.get('_tgt', v)}
            for u, v, d in graphe.edges(data=True)
        ],
        'hyperedges': list(graphe.graph.get('hyperedges', [])),
        'input_tokens': 0, 'output_tokens': 0,
    }, ensure_ascii=False), encoding='utf-8')
    journal(f'fusion : {graphe.number_of_nodes()} noeuds, {graphe.number_of_edges()} aretes')
    return True


def jouer_les_passes() -> None:
    """Rejoue les quatre passes de pont, qui rescannent le disque et se chainent."""
    dossier = Path(__file__).resolve().parent
    for nom in PASSES:
        chemin = dossier / nom
        if not chemin.exists():
            journal(f'passe absente, ignoree : {nom}')
            continue
        runpy.run_path(str(chemin), run_name='__main__')


def libeller(communautes: dict, noeuds: dict) -> dict:
    """Nomme les communautes, en reprenant les libelles de la passe precedente.

    Les identifiants de communaute changent a chaque clustering : le report se fait
    par recouvrement de membres, jamais par identifiant. Reprendre les anciens
    libelles evite qu'un nommage manuel ne s'erode a chaque reconstruction.
    """
    precedents = SORTIE / '.graphify_labels.json'
    ancien_graphe = GRAPHE
    repris = {}
    if precedents.exists() and ancien_graphe.exists():
        libelles_avant = json.loads(precedents.read_text(encoding='utf-8'))
        membres_avant: dict[int, set] = {}
        for n in json.loads(ancien_graphe.read_text(encoding='utf-8'))['nodes']:
            if n.get('community') is not None:
                membres_avant.setdefault(int(n['community']), set()).add(n['id'])
        for cid_avant, libelle in libelles_avant.items():
            ref = membres_avant.get(int(cid_avant))
            if not ref:
                continue
            meilleur, score = None, 0.0
            for cid, membres in communautes.items():
                m = set(membres)
                j = len(ref & m) / len(ref | m)
                if j > score:
                    meilleur, score = int(cid), j
            if meilleur is not None and score >= 0.3 and meilleur not in repris:
                repris[meilleur] = libelle

    def paquet(n):
        sf = n.get('source_file') or ''
        parts = sf.split('/')
        if sf.endswith('.java') and len(parts) > 7:
            return '.'.join(parts[6:-1])
        return parts[0] if parts else '?'

    libelles = {}
    for cid, membres in communautes.items():
        cid = int(cid)
        if cid in repris:
            libelles[cid] = repris[cid]
            continue
        ns = [noeuds[m] for m in membres if m in noeuds]
        dominant = Counter(paquet(n) for n in ns).most_common(1)
        vedette = Counter(Path(n['source_file']).stem for n in ns if n.get('source_file')).most_common(1)
        base = dominant[0][0] if dominant else 'divers'
        libelles[cid] = f'{base} - {vedette[0][0]}' if vedette else base
    return libelles


def reconstruire() -> None:
    from graphify.analyze import god_nodes, suggest_questions, surprising_connections
    from graphify.build import build_from_json
    from graphify.cluster import cluster, score_all
    from graphify.export import to_json
    from graphify.report import generate

    source = EXTRAIT if EXTRAIT.exists() else GRAPHE
    brut = json.loads(source.read_text(encoding='utf-8'))
    extraction = {
        'nodes': brut['nodes'],
        'edges': brut.get('edges') if 'edges' in brut else brut['links'],
        'hyperedges': brut.get('hyperedges', []),
    }
    graphe = build_from_json(extraction, root=str(RACINE), directed=False)
    if graphe.number_of_nodes() == 0:
        journal('graphe vide, reconstruction abandonnee')
        raise SystemExit(1)
    communautes = cluster(graphe)
    noeuds = {n['id']: n for n in extraction['nodes']}
    libelles = libeller(communautes, noeuds)
    cohesion = score_all(graphe, communautes)
    dieux = god_nodes(graphe)
    surprises = surprising_connections(graphe, communautes)
    questions = suggest_questions(graphe, communautes, libelles)

    # to_json refuse d'ecrire un graphe plus petit que l'existant. La garde protege
    # d'un build partiel, mais elle est trop stricte ici : build_merge dedoublonne
    # quelques noeuds a chaque fusion, et supprimer du code doit legitimement faire
    # retrecir le graphe. On garde donc le garde-fou, avec une tolerance : en dessous
    # de 80 % de l'effectif precedent, on refuse et on laisse l'ancien graphe en place.
    avant = 0
    if GRAPHE.exists():
        avant = len(json.loads(GRAPHE.read_text(encoding='utf-8'))['nodes'])
    apres = graphe.number_of_nodes()
    if avant and apres < avant * 0.8:
        journal(f'retrecissement suspect ({avant} -> {apres} noeuds), graphe conserve en l etat')
        raise SystemExit(1)
    if apres < avant:
        journal(f'{avant - apres} noeud(s) de moins (dedoublonnage ou code supprime)')
        GRAPHE.unlink()
    if not to_json(graphe, communautes, str(GRAPHE)):
        journal('ecriture de graph.json refusee')
        raise SystemExit(1)
    detection = corpus_du_depot()
    (SORTIE / 'GRAPH_REPORT.md').write_text(
        generate(graphe, communautes, cohesion, libelles, dieux, surprises, detection,
                 {'input': 0, 'output': 0}, str(RACINE), suggested_questions=questions),
        encoding='utf-8')
    (SORTIE / '.graphify_labels.json').write_text(
        json.dumps({str(k): v for k, v in libelles.items()}, ensure_ascii=False), encoding='utf-8')
    journal(f'{graphe.number_of_nodes()} noeuds, {graphe.number_of_edges()} aretes, '
            f'{len(communautes)} communautes')


def corpus_du_depot(detection=None):
    """Le corpus tel que l'entete de GRAPH_REPORT.md doit l'annoncer.

    `reconstruire()` passait ici une detection codee en dur a zero. Le rapport ouvrait
    donc sur « 0 files · ~0 words » suivi de « corpus is large enough that graph
    structure adds value » : deux phrases qui se contredisent, sur un depot qui compte
    plus de 2 700 fichiers (#4231). Le meme document mentait ou non selon le chemin qui
    l'avait ecrit - la chaine complete, elle, disait vrai.

    On interroge donc la meme detection qu'elle. Le balayage coute une dizaine de
    secondes, negligeable devant l'extraction et le clustering qui suivent, et c'est le
    prix de la seule propriete qui vaille : le rapport annonce le meme corpus quel que
    soit le chemin qui le produit.

    `detection` n'est la que pour les tests, qui ne balaient pas le disque.
    """
    if detection is None:
        from graphify.detect import detect
        detection = detect(RACINE)
    corpus = dict(detection)
    corpus.setdefault('needs_graph', True)
    corpus.setdefault('skipped_sensitive', [])
    return corpus


def auto_test():
    """Trois cas ; les deux premiers doivent rougir tant que le corpus est code en dur."""
    echecs = []

    def verifier(nom, condition, detail=''):
        if condition:
            print(f'  ok   {nom}')
        else:
            print(f'  ECHEC {nom}{" : " + detail if detail else ""}')
            echecs.append(nom)

    # 1 : une detection injectee doit ressortir telle quelle. Le corpus code en dur
    # faisait ouvrir le rapport sur « 0 files · ~0 words » suivi de « corpus is large
    # enough that graph structure adds value » - deux phrases qui se contredisent (#4231).
    faux = {'files': {'code': ['a.java']}, 'total_files': 2730, 'total_words': 2421355,
            'skipped_sensitive': [], 'warning': 'temoin'}
    c = corpus_du_depot(faux)
    verifier('la detection fournie ressort telle quelle',
             (c.get('total_files'), c.get('total_words')) == (2730, 2421355),
             f"obtenu : {c.get('total_files')} fichiers, {c.get('total_words')} mots")
    verifier("l avertissement de corpus n est pas perdu",
             c.get('warning') == 'temoin', f"obtenu : {c.get('warning')!r}")

    # 2 : la cle que `generate()` consomme reste posee.
    verifier("la cle needs_graph est presente", c.get('needs_graph') is True)

    # 3 : sans argument, la detection est demandee a graphify - c est ce qui fait dire
    # au rapport la meme chose que la chaine complete. On injecte un faux module pour le
    # prouver sans balayer le disque, et sans exiger graphify sur le runner de CI.
    import types
    temoin = {'total_files': 7, 'total_words': 42, 'files': {}, 'skipped_sensitive': []}
    sauvegarde = {n: sys.modules.get(n) for n in ('graphify', 'graphify.detect')}
    try:
        paquet = types.ModuleType('graphify')
        paquet.__path__ = []
        faux = types.ModuleType('graphify.detect')
        faux.detect = lambda racine: dict(temoin)
        paquet.detect = faux
        sys.modules['graphify'] = paquet
        sys.modules['graphify.detect'] = faux
        sans_argument = corpus_du_depot()
    finally:
        for nom, mod in sauvegarde.items():
            if mod is None:
                sys.modules.pop(nom, None)
            else:
                sys.modules[nom] = mod
    verifier('sans argument, la detection vient de graphify et non d un compte en dur',
             sans_argument.get('total_files') == 7,
             f"obtenu : {sans_argument.get('total_files')}")

    if echecs:
        print(f'\n{len(echecs)} cas en echec : {", ".join(echecs)}')
        return 1
    print('\nauto-test : tous les cas passent')
    return 0


def main(argv: list[str]) -> int:
    if '--auto-test' in argv:
        return auto_test()
    if not GRAPHE.exists():
        journal('aucun graphe existant, rien a mettre a jour '
                '(lancer /graphify . une premiere fois)')
        return 0
    changes = [Path(a) for a in argv]
    if any(p.suffix in EXT_DOC for p in changes):
        (SORTIE / '.needs_update').write_text(
            'documentation modifiee : lancer /graphify . --update\n', encoding='utf-8')
        journal('documentation modifiee, drapeau .needs_update pose')
    try:
        extraire_le_code(changes)
        jouer_les_passes()
        reconstruire()
    finally:
        EXTRAIT.unlink(missing_ok=True)
    return 0


if __name__ == '__main__':
    sys.path.insert(0, str(RACINE))
    import os
    os.chdir(RACINE)
    raise SystemExit(main(sys.argv[1:]))

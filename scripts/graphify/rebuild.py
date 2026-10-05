#!/usr/bin/env python3
"""Reconstruit le graphe de connaissance graphify apres un commit ou une fusion.

Enchaine, sans aucun appel LLM :
  1. extraction de structure des fichiers modifies, le code ET les pages, en une fois, puis
     fusion dans graph.json
  2. les quatre passes de pont (doc/code, vues/CLI, CI/pom, MCD)
  3. clustering, libelles, GRAPH_REPORT.md et graph.html

Les TITRES d'une page modifiee se relisent donc ici, et le journal dit ceux qui sortent du
graphe (#5941). Sa PROSE, elle, demande un LLM : quand un `.md` change, on pose le drapeau
`graphify-out/.needs_update`, que l'on resorbe en session. `scripts/graphify/couche_semantique.py a-reextraire` dit quelles
pages, puis `decoupe`, les agents et `fusionne` font le reste. Ce n'est PAS
`/graphify . --update` : son detecteur signale le corpus entier, et il fusionne sans
les parades de l'ADR 5790.

Usage :
    python3 scripts/graphify/rebuild.py [fichier ...]
    python3 scripts/graphify/rebuild.py --mets-a-jour

Sans argument, reconstruit a partir du graphe existant sans rien re-extraire.
Les chemins passes en argument sont relatifs a la racine du depot.

`--mets-a-jour` lance `graphify update .`, relit la structure des pages, puis
rejoue les ponts et la reconstruction en reportant les libelles de communautes de
l'etat d'AVANT. `graphify update .` seul les renomme toutes d'apres leur noeud le plus
connecte : 1 074 communautes le 4 octobre 2026 (#5814).

La relecture des pages n'est pas un doublon de `graphify update .`. Il reextrait le code
et les pages qui n'ont que leur structure, mais laisse telle quelle la structure d'une
page qui porte une autre couche : ses titres dateraient du jour ou elle l'a recue. 586
pages etaient dans ce cas le 5 octobre 2026, et six avaient des titres perimes (#5877).
Un titre que le disque ne porte plus sort du graphe, et la commande dit combien d'aretes
semantiques tombent avec lui, et sur quelles pages.
"""

from __future__ import annotations

import contextlib
import io
import json
import runpy
import shutil
import subprocess
import sys
import tempfile
from collections import Counter
from pathlib import Path

RACINE = Path(__file__).resolve().parents[2]
SORTIE = RACINE / "graphify-out"
GRAPHE = SORTIE / "graph.json"
EXTRAIT = SORTIE / ".graphify_extract.json"
PASSES = ("pont_doc_code.py", "pont_ressources.py", "pont_ci.py", "pont_mcd.py")
EXT_CODE = {".java", ".sql"}
EXT_DOC = {".md"}


def journal(message: str) -> None:
    print(f"[graphify] {message}", flush=True)


def refus_sans_le_moteur() -> int:
    """0 si cet interprete importe graphify, sinon 2 apres avoir dit le remede (ADR 5407).

    `graphify` est une commande, posee avec son propre interprete : le `python3` du poste peut la
    trouver sans importer son module. `--mets-a-jour` lancait alors `graphify update .`, qui
    reecrit le graphe et renomme toutes ses communautes, puis tombait a l'import suivant. Il
    restait un graphe reecrit, sans ponts ni reconstruction, et les libelles d'avant etaient
    perdus avec le dossier temporaire qui les gardait. Le refus vient donc AVANT l'outil.
    """
    try:
        import graphify  # noqa: F401
    except ModuleNotFoundError:
        journal(
            "REFUS : graphify n est pas importable par cet interprete. Ce refus parle du poste,"
            " pas du depot, et rien n a ete touche."
        )
        journal(
            "POUR REPARER : relancer avec l interprete de graphify, que nomme"
            " graphify-out/.graphify_python."
        )
        return 2
    return 0


def extraire_les_modifies(
    changes: list[Path], racine: Path = RACINE, graphe: Path = GRAPHE, extrait: Path = EXTRAIT
) -> bool:
    """Re-extrait le code ET les pages modifies, et les fusionne dans graph.json.

    Ce chemin, celui du crochet de commit, ne relisait que le code : une page dont un titre
    changeait gardait ses titres d'avant jusqu'a la prochaine mise a jour complete (#5941). Le
    lot 6 avait donne ce geste a `--mets-a-jour`, pas a lui.

    UNE seule extraction pour les deux. La fusion part du graphe du disque et ecrit l'extrait :
    deux fusions de suite partiraient chacune du meme graphe, et la seconde effacerait ce que la
    premiere venait d'y mettre.
    """
    from graphify.build import build_merge
    from graphify.extract import extract

    fichiers = [p for p in changes if p.suffix in EXT_CODE and (racine / p).exists()]
    pages = [p for p in changes if p.suffix in EXT_DOC and (racine / p).is_file()]
    if not fichiers and not pages:
        return False
    journal(
        f"{len(fichiers)} fichier(s) de code et {len(pages)} page(s) modifie(s), "
        "extraction de structure"
    )
    # root= est obligatoire : sans lui, source_file ET les identifiants sont
    # tronques au nom de fichier, ce qui rend les noeuds introuvables par chemin.
    resultat = extract([racine / p for p in fichiers + pages], cache_root=racine, root=racine)
    if pages:
        # Seules les pages NOMMEES sont jugees : l'extraction peut en rendre d'autres, et le
        # code a sa propre fusion, qui ne declare rien.
        brut = json.loads(graphe.read_text(encoding="utf-8"))
        nommees = {str(p) for p in pages}
        dit_le_bilan(
            bilan_de_relecture(
                brut.get("nodes", []),
                brut.get("edges") if "edges" in brut else brut.get("links", []),
                [n for n in resultat["nodes"] if n.get("source_file") in nommees],
            ),
            len(pages),
        )
    fusion = fusionner_une_extraction(resultat, build_merge, graphe, extrait, racine)
    journal(f"fusion : {fusion.number_of_nodes()} noeuds, {fusion.number_of_edges()} aretes")
    return True


def fusionner_une_extraction(
    resultat: dict,
    build_merge,
    graphe: Path = GRAPHE,
    extrait: Path = EXTRAIT,
    racine: Path = RACINE,
):
    """Fusionne une extraction de structure dans le graphe, et ecrit l'extrait que les ponts lisent.

    La fusion remplace la structure de chaque fichier extrait et laisse ses autres couches. Elle
    n'ecrit pas le graphe : c'est `reconstruire` qui le fera, depuis l'extrait.
    """
    nouveau = {
        "nodes": resultat["nodes"],
        "edges": resultat["edges"],
        "hyperedges": [],
        "input_tokens": 0,
        "output_tokens": 0,
    }
    fusion = build_merge([nouveau], graph_path=str(graphe), root=str(racine), directed=False)
    extrait.write_text(
        json.dumps(
            {
                "nodes": [{"id": n, **d} for n, d in fusion.nodes(data=True)],
                "edges": [
                    {
                        **{
                            k: v
                            for k, v in d.items()
                            if k not in ("_src", "_tgt", "source", "target")
                        },
                        "source": d.get("_src", u),
                        "target": d.get("_tgt", v),
                    }
                    for u, v, d in fusion.edges(data=True)
                ],
                "hyperedges": list(fusion.graph.get("hyperedges", [])),
                "input_tokens": 0,
                "output_tokens": 0,
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    return fusion


def pages_a_relire(noeuds: list[dict], racine: Path = RACINE) -> list[Path]:
    """Les pages dont la structure se relit : toute page que le graphe porte et que le disque a.

    TOUTES, et non les seules pages qui portent une couche. Le moteur de graphify ne relit plus
    la structure d'un document qui porte un noeud d'une autre couche, et c'est ce defaut qu'on
    repare (#5877). Mais reconnaitre une page couverte, ce serait recopier ici la regle du moteur.
    Relire tout rend le meme graphe, mesure le 5 octobre 2026 sur 753 pages contre 586, pour une
    seconde de plus.
    """
    chemins = {n.get("source_file") for n in noeuds}
    return sorted(
        Path(c) for c in chemins if c and Path(c).suffix in EXT_DOC and (racine / c).is_file()
    )


def bilan_de_relecture(noeuds: list[dict], aretes: list[dict], extraits: list[dict]) -> dict:
    """Ce que la relecture retire du graphe, par page : une mise a jour declare ce qu'elle lache.

    Un titre que le disque ne porte plus sort du graphe, et les aretes semantiques qui y etaient
    ancrees tombent avec lui. Le concept qu'elles reliaient reste, sans son ancrage : la page est
    de celles que `couche_semantique.py a-reextraire` rend, son empreinte ayant change.

    Seules les pages que l'extraction a rendues sont jugees. Une page qu'elle n'a pas su lire
    garde sa structure : la fusion ne remplace que ce qu'elle recoit.
    """
    frais: dict[str, set] = {}
    for n in extraits:
        if n.get("source_file"):
            frais.setdefault(n["source_file"], set()).add(n["id"])
    retires: dict[str, list[str]] = {}
    page_de: dict[str, str] = {}
    for n in noeuds:
        sf = n.get("source_file")
        if n.get("_origin") == "ast" and sf in frais and n["id"] not in frais[sf]:
            retires.setdefault(sf, []).append(n["id"])
            page_de[n["id"]] = sf
    laches: dict[str, int] = {}
    for e in aretes:
        if e.get("_origin") != "semantic":
            continue
        sf = page_de.get(e.get("source")) or page_de.get(e.get("target"))
        if sf:
            laches[sf] = laches.get(sf, 0) + 1
    return {
        "pages": len(frais),
        "retires": {sf: sorted(ids) for sf, ids in sorted(retires.items())},
        "laches": dict(sorted(laches.items())),
    }


def dit_le_bilan(bilan: dict, demandees: int) -> None:
    """Ecrit au journal ce qu'une relecture de structure retire, pour qui lit sa sortie."""
    journal(
        f"structure relue : {bilan['pages']} page(s) sur {demandees} ; "
        f"{sum(len(v) for v in bilan['retires'].values())} titre(s) retire(s) "
        f"sur {len(bilan['retires'])} page(s)"
    )
    if bilan["laches"]:
        journal(
            f"{sum(bilan['laches'].values())} arete(s) semantique(s) lachee(s), ancree(s) sur un "
            f"titre disparu : {', '.join(f'{sf} ({n})' for sf, n in bilan['laches'].items())}"
        )
        journal("ces pages sont a reextraire : scripts/graphify/couche_semantique.py a-reextraire")


def relire_la_structure(
    graphe: Path = GRAPHE, extrait: Path = EXTRAIT, racine: Path = RACINE
) -> bool:
    """Relit la structure de toutes les pages du graphe et la fusionne, en disant ce qui tombe."""
    from graphify.build import build_merge
    from graphify.extract import extract

    brut = json.loads(graphe.read_text(encoding="utf-8"))
    pages = pages_a_relire(brut["nodes"], racine)
    if not pages:
        return False
    resultat = extract([racine / p for p in pages], cache_root=racine, root=racine)
    bilan = bilan_de_relecture(
        brut["nodes"], brut.get("edges") if "edges" in brut else brut["links"], resultat["nodes"]
    )
    dit_le_bilan(bilan, len(pages))
    fusionner_une_extraction(resultat, build_merge, graphe, extrait, racine)
    return True


def jouer_les_passes(dossier: Path | None = None, passes: tuple = PASSES) -> None:
    """Rejoue les quatre passes de pont, qui rescannent le disque et se chainent."""
    dossier = dossier or Path(__file__).resolve().parent
    for nom in passes:
        chemin = dossier / nom
        if not chemin.exists():
            journal(f"passe absente, ignoree : {nom}")
            continue
        runpy.run_path(str(chemin), run_name="__main__")


RECOUVREMENT_MINIMAL = 0.3


def reporte(libelles_avant: dict, membres_avant: dict, communautes: dict) -> dict:
    """Les libelles d'avant, portes sur les communautes d'apres par recouvrement de membres.

    Un libelle va a la communaute qui recouvre le mieux l'ancienne, au sens de Jaccard, si ce
    recouvrement atteint le seuil. Il ne se donne qu'une fois : deux anciennes communautes qui
    ont fusionne ne nomment pas deux fois la meme.
    """
    repris: dict[int, str] = {}
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
        if meilleur is not None and score >= RECOUVREMENT_MINIMAL and meilleur not in repris:
            repris[meilleur] = libelle
    return repris


def libeller(communautes: dict, noeuds: dict, reference: Path | None = None) -> dict:
    """Nomme les communautes, en reprenant les libelles de la passe precedente.

    Les identifiants de communaute changent a chaque clustering : le report se fait
    par recouvrement de membres, jamais par identifiant. Reprendre les anciens
    libelles evite qu'un nommage manuel ne s'erode a chaque reconstruction.

    `reference` designe le dossier ou lire l'etat d'avant. Par defaut c'est la sortie
    elle-meme ; `--mets-a-jour` y passe une copie prise AVANT `graphify update .`, qui
    reecrit le graphe et ses libelles sur place.
    """
    reference = reference or SORTIE
    precedents = reference / ".graphify_labels.json"
    ancien_graphe = reference / "graph.json"
    repris = {}
    if precedents.exists() and ancien_graphe.exists():
        libelles_avant = json.loads(precedents.read_text(encoding="utf-8"))
        membres_avant: dict[int, set] = {}
        for n in json.loads(ancien_graphe.read_text(encoding="utf-8"))["nodes"]:
            if n.get("community") is not None:
                membres_avant.setdefault(int(n["community"]), set()).add(n["id"])
        repris = reporte(libelles_avant, membres_avant, communautes)

    def paquet(n):
        sf = n.get("source_file") or ""
        parts = sf.split("/")
        if sf.endswith(".java") and len(parts) > 7:
            return ".".join(parts[6:-1])
        return parts[0] if parts else "?"

    libelles = {}
    for cid, membres in communautes.items():
        cid = int(cid)
        if cid in repris:
            libelles[cid] = repris[cid]
            continue
        ns = [noeuds[m] for m in membres if m in noeuds]
        dominant = Counter(paquet(n) for n in ns).most_common(1)
        vedette = Counter(
            Path(n["source_file"]).stem for n in ns if n.get("source_file")
        ).most_common(1)
        base = dominant[0][0] if dominant else "divers"
        libelles[cid] = f"{base} - {vedette[0][0]}" if vedette else base
    return libelles


def reconstruire(reference: Path | None = None) -> None:
    from graphify.analyze import god_nodes, suggest_questions, surprising_connections
    from graphify.build import build_from_json
    from graphify.cluster import cluster, score_all
    from graphify.export import to_json
    from graphify.report import generate

    source = EXTRAIT if EXTRAIT.exists() else GRAPHE
    brut = json.loads(source.read_text(encoding="utf-8"))
    extraction = {
        "nodes": brut["nodes"],
        "edges": brut.get("edges") if "edges" in brut else brut["links"],
        "hyperedges": brut.get("hyperedges", []),
    }
    graphe = build_from_json(extraction, root=str(RACINE), directed=False)
    if graphe.number_of_nodes() == 0:
        journal("graphe vide, reconstruction abandonnee")
        raise SystemExit(1)
    communautes = cluster(graphe)
    noeuds = {n["id"]: n for n in extraction["nodes"]}
    libelles = libeller(communautes, noeuds, reference)
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
        avant = len(json.loads(GRAPHE.read_text(encoding="utf-8"))["nodes"])
    apres = graphe.number_of_nodes()
    if avant and apres < avant * 0.8:
        journal(f"retrecissement suspect ({avant} -> {apres} noeuds), graphe conserve en l etat")
        raise SystemExit(1)
    if apres < avant:
        journal(f"{avant - apres} noeud(s) de moins (dedoublonnage ou code supprime)")
        GRAPHE.unlink()
    if not to_json(graphe, communautes, str(GRAPHE)):
        journal("ecriture de graph.json refusee")
        raise SystemExit(1)
    detection = corpus_du_depot()
    (SORTIE / "GRAPH_REPORT.md").write_text(
        generate(
            graphe,
            communautes,
            cohesion,
            libelles,
            dieux,
            surprises,
            detection,
            {"input": 0, "output": 0},
            str(RACINE),
            suggested_questions=questions,
        ),
        encoding="utf-8",
    )
    (SORTIE / ".graphify_labels.json").write_text(
        json.dumps({str(k): v for k, v in libelles.items()}, ensure_ascii=False), encoding="utf-8"
    )
    journal(
        f"{graphe.number_of_nodes()} noeuds, {graphe.number_of_edges()} aretes, "
        f"{len(communautes)} communautes"
    )


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
    corpus.setdefault("needs_graph", True)
    corpus.setdefault("skipped_sensitive", [])
    return corpus


def auto_test():
    """Trois cas ; les deux premiers doivent rougir tant que le corpus est code en dur."""
    echecs = []

    def verifier(nom, condition, detail=""):
        if condition:
            print(f"  ok   {nom}")
        else:
            print(f"  ECHEC {nom}{' : ' + detail if detail else ''}")
            echecs.append(nom)

    # 1 : une detection injectee doit ressortir telle quelle. Le corpus code en dur
    # faisait ouvrir le rapport sur « 0 files · ~0 words » suivi de « corpus is large
    # enough that graph structure adds value » - deux phrases qui se contredisent (#4231).
    faux = {
        "files": {"code": ["a.java"]},
        "total_files": 2730,
        "total_words": 2421355,
        "skipped_sensitive": [],
        "warning": "temoin",
    }
    c = corpus_du_depot(faux)
    verifier(
        "la detection fournie ressort telle quelle",
        (c.get("total_files"), c.get("total_words")) == (2730, 2421355),
        f"obtenu : {c.get('total_files')} fichiers, {c.get('total_words')} mots",
    )
    verifier(
        "l avertissement de corpus n est pas perdu",
        c.get("warning") == "temoin",
        f"obtenu : {c.get('warning')!r}",
    )

    # 2 : la cle que `generate()` consomme reste posee.
    verifier("la cle needs_graph est presente", c.get("needs_graph") is True)

    # 3 : sans argument, la detection est demandee a graphify - c est ce qui fait dire
    # au rapport la meme chose que la chaine complete. On injecte un faux module pour le
    # prouver sans balayer le disque, et sans exiger graphify sur le runner de CI.
    import types

    temoin = {"total_files": 7, "total_words": 42, "files": {}, "skipped_sensitive": []}
    sauvegarde = {n: sys.modules.get(n) for n in ("graphify", "graphify.detect")}
    try:
        paquet = types.ModuleType("graphify")
        paquet.__path__ = []
        faux = types.ModuleType("graphify.detect")
        faux.detect = lambda racine: dict(temoin)
        paquet.detect = faux
        sys.modules["graphify"] = paquet
        sys.modules["graphify.detect"] = faux
        sans_argument = corpus_du_depot()
    finally:
        for nom, mod in sauvegarde.items():
            if mod is None:
                sys.modules.pop(nom, None)
            else:
                sys.modules[nom] = mod
    verifier(
        "sans argument, la detection vient de graphify et non d un compte en dur",
        sans_argument.get("total_files") == 7,
        f"obtenu : {sans_argument.get('total_files')}",
    )

    # 4 : le report des libelles, sur des communautes fabriquees (#5814). Les identifiants
    # changent expres entre l'avant et l'apres : c'est le recouvrement qui porte le libelle.
    avant = {"0": "Le depot", "1": "La synchro", "2": "Le lot"}
    membres = {0: {"a", "b", "c", "d"}, 1: {"e", "f", "g", "h"}, 2: {"x", "y"}}
    apres = {7: ["a", "b", "c", "z"], 8: ["e", "p", "q", "r", "s"], 9: ["x", "y"]}
    repris = reporte(avant, membres, apres)
    verifier(
        "un libelle suit sa communaute quand elle change d identifiant",
        repris.get(7) == "Le depot" and repris.get(9) == "Le lot",
        f"obtenu : {repris}",
    )
    verifier(
        "sous le seuil de recouvrement, le libelle n est pas reporte",
        8 not in repris,
        f"obtenu : {repris}",
    )
    fusionnees = reporte(
        {"0": "Premier", "1": "Second"}, {0: {"a", "b"}, 1: {"c", "d"}}, {5: ["a", "b", "c", "d"]}
    )
    verifier(
        "deux anciennes communautes fusionnees ne nomment pas deux fois la meme",
        fusionnees == {5: "Premier"},
        f"obtenu : {fusionnees}",
    )

    # 5 : `libeller` lit l'etat d'avant dans la REFERENCE qu'on lui passe, et non dans la
    # sortie, que `graphify update .` vient de reecrire. Sans reference, sur un depot qui n'a
    # pas de graphe, il nomme par le paquet dominant.
    with tempfile.TemporaryDirectory() as temporaire:
        dossier = Path(temporaire)
        (dossier / ".graphify_labels.json").write_text(json.dumps(avant), encoding="utf-8")
        (dossier / "graph.json").write_text(
            json.dumps(
                {"nodes": [{"id": i, "community": c} for c, ids in membres.items() for i in ids]}
            ),
            encoding="utf-8",
        )
        noeuds = {
            i: {"id": i, "source_file": "docs/page.md"} for ids in apres.values() for i in ids
        }
        avec_reference = libeller(apres, noeuds, dossier)
        vide = Path(temporaire) / "vide"
        vide.mkdir()
        sans_reference = libeller(apres, noeuds, vide)
    verifier(
        "avec une reference, les libelles d avant sont reportes",
        avec_reference.get(7) == "Le depot" and avec_reference.get(9) == "Le lot",
        f"obtenu : {avec_reference}",
    )
    verifier(
        "sans etat d avant, chaque communaute recoit un libelle calcule",
        sans_reference == {7: "docs - page", 8: "docs - page", 9: "docs - page"},
        f"obtenu : {sans_reference}",
    )

    # 6 : `--mets-a-jour` relit la structure des pages APRES `graphify update .` et AVANT les
    # ponts (#5877). Le moteur ne relit plus une page qui porte une couche : sans ce geste, ses
    # titres datent du jour ou elle l'a recue. On remplace l'outil et les trois etapes par des
    # temoins, pour lire l'ordre sans graphify ni graphe.
    def ordre_de_la_mise_a_jour(code_de_l_outil, moteur_present=True):
        appels = []
        temoins = {
            "relire_la_structure": lambda: appels.append("structure"),
            "jouer_les_passes": lambda: appels.append("ponts"),
            "reconstruire": lambda reference=None: appels.append("reconstruction"),
        }
        portee = globals()
        d_avant = {nom: portee.get(nom) for nom in temoins}
        moteur_d_avant = sys.modules.get("graphify")
        which, run = shutil.which, subprocess.run
        try:
            portee.update(temoins)
            # `None` dans `sys.modules` fait lever l'import : c'est un poste sans le module.
            sys.modules["graphify"] = types.ModuleType("graphify") if moteur_present else None
            shutil.which = lambda nom: "/faux/graphify"
            subprocess.run = lambda *a, **k: (
                appels.append("update"),
                types.SimpleNamespace(returncode=code_de_l_outil),
            )[1]
            # Le journal se tait ici : un refus joue par un cas imprimerait ses marques dans la
            # sortie d un auto-test qui PASSE, et la porte les citerait comme la cause le jour
            # ou cet auto-test rougirait pour une autre raison (#5890).
            with contextlib.redirect_stdout(io.StringIO()):
                code = mets_a_jour()
        finally:
            shutil.which, subprocess.run = which, run
            if moteur_d_avant is None:
                sys.modules.pop("graphify", None)
            else:
                sys.modules["graphify"] = moteur_d_avant
            for nom, valeur in d_avant.items():
                if valeur is None:
                    portee.pop(nom, None)
                else:
                    portee[nom] = valeur
        return code, appels

    obtenu = ordre_de_la_mise_a_jour(0)
    verifier(
        "la mise a jour relit la structure des pages entre l outil et les ponts",
        obtenu == (0, ["update", "structure", "ponts", "reconstruction"]),
        f"obtenu : {obtenu}",
    )
    obtenu = ordre_de_la_mise_a_jour(3)
    verifier(
        "si l outil echoue, rien n est relu ni reconstruit, et son code remonte",
        obtenu == (3, ["update"]),
        f"obtenu : {obtenu}",
    )
    # L'ADR 5407 : sans le module, la mise a jour refuse AVANT l'outil. Lance, il reecrirait le
    # graphe et renommerait ses communautes, et l'import suivant tomberait apres coup.
    obtenu = ordre_de_la_mise_a_jour(0, moteur_present=False)
    verifier(
        "sans le module graphify, la mise a jour refuse en 2 sans avoir lance l outil",
        obtenu == (2, []),
        f"obtenu : {obtenu}",
    )

    # 7 : le choix des pages a relire. Toute page que le graphe porte et que le disque a encore,
    # qu'elle porte une couche ou non ; ni le code, ni un registre, ni un schema, ni une page partie.
    with tempfile.TemporaryDirectory() as temporaire:
        racine = Path(temporaire)
        for chemin in (
            "docs/couverte.md",
            "docs/nue.md",
            "src/Classe.java",
            "scripts/registre.txt",
        ):
            (racine / chemin).parent.mkdir(parents=True, exist_ok=True)
            (racine / chemin).write_text("x", encoding="utf-8")
        (racine / "docs" / "schema.mcd").write_text("x", encoding="utf-8")
        (racine / "docs" / "dossier.md").mkdir()
        fabrique = [
            {"id": "docs_couverte", "source_file": "docs/couverte.md", "_origin": "ast"},
            {"id": "docs_couverte_idee", "source_file": "docs/couverte.md", "_origin": "semantic"},
            {"id": "docs_nue", "source_file": "docs/nue.md", "_origin": "ast"},
            {"id": "docs_partie", "source_file": "docs/partie.md", "_origin": "ast"},
            {"id": "docs_dossier", "source_file": "docs/dossier.md", "_origin": "ast"},
            {"id": "classe", "source_file": "src/Classe.java", "_origin": "ast"},
            {"id": "registre", "source_file": "scripts/registre.txt", "_origin": "semantic"},
            {"id": "schema", "source_file": "docs/schema.mcd", "_origin": "pont"},
            {"id": "sans_fichier"},
        ]
        choisies = pages_a_relire(fabrique, racine)
    verifier(
        "toute page du graphe encore sur le disque se relit, couverte ou non, et rien d autre",
        choisies == [Path("docs/couverte.md"), Path("docs/nue.md")],
        f"obtenu : {choisies}",
    )

    # 8 : ce que la relecture declare lacher. Un titre renomme sort du graphe avec les aretes
    # semantiques qui y tenaient, par l'un ou l'autre bout. Quatre temoins ne comptent pas : le
    # titre garde, l'arete de structure, l'arete de pont, et la page que l'extraction n'a pas rendue.
    page = "docs/ecran.md"
    avant_relecture = [
        {"id": "ecran", "source_file": page, "_origin": "ast"},
        {"id": "ecran_garde", "source_file": page, "_origin": "ast"},
        {"id": "ecran_renomme", "source_file": page, "_origin": "ast"},
        {"id": "ecran_idee", "source_file": page, "_origin": "semantic"},
        {"id": "illisible_titre", "source_file": "docs/illisible.md", "_origin": "ast"},
        {"id": "autre", "source_file": "docs/autre.md", "_origin": "ast"},
    ]
    liens = [
        {"source": "ecran_renomme", "target": "ecran_idee", "_origin": "semantic"},
        {"source": "ecran_idee", "target": "ecran_renomme", "_origin": "semantic"},
        {"source": "ecran_garde", "target": "ecran_idee", "_origin": "semantic"},
        {"source": "ecran", "target": "ecran_renomme", "_origin": "ast"},
        {"source": "ecran_renomme", "target": "classe", "_origin": "pont"},
        {"source": "illisible_titre", "target": "ecran_idee", "_origin": "semantic"},
    ]
    relus = [
        {"id": "ecran", "source_file": page},
        {"id": "ecran_garde", "source_file": page},
        {"id": "ecran_nouveau", "source_file": page},
        {"id": "autre", "source_file": "docs/autre.md"},
    ]
    bilan = bilan_de_relecture(avant_relecture, liens, relus)
    verifier(
        "un titre que le disque ne porte plus est declare retire, avec ses aretes semantiques",
        bilan == {"pages": 2, "retires": {page: ["ecran_renomme"]}, "laches": {page: 2}},
        f"obtenu : {bilan}",
    )

    # 9 : la relecture elle-meme, avec un faux moteur. Elle extrait les pages choisies depuis la
    # racine, fusionne dans le graphe qu'elle a lu, et ecrit l'extrait que les ponts liront.
    class Fusion:
        def __init__(self):
            self.graph = {"hyperedges": [{"id": "h"}]}

        def nodes(self, data=False):
            return [("ecran", {"label": "Ecran"})]

        def edges(self, data=False):
            return [("ecran", "x", {"relation": "contains", "_src": "x", "_tgt": "ecran"})]

        def number_of_nodes(self):
            return 1

        def number_of_edges(self):
            return 1

    recu = {"extractions": 0}

    def faux_extract(chemins, cache_root=None, root=None):
        recu["extractions"] += 1
        recu["chemins"], recu["root"] = list(chemins), root
        return {"nodes": relus, "edges": []}

    def faux_build_merge(lots, graph_path=None, root=None, directed=False):
        recu["lot"], recu["graphe"], recu["racine"] = lots, graph_path, root
        return Fusion()

    noms = ("graphify", "graphify.extract", "graphify.build")
    sauvegarde = {n: sys.modules.get(n) for n in noms}
    with tempfile.TemporaryDirectory() as temporaire:
        racine = Path(temporaire)
        (racine / "docs").mkdir()
        (racine / page).write_text("# Ecran\n", encoding="utf-8")
        lu, ecrit, sans_page = racine / "graph.json", racine / "extrait.json", racine / "vide.json"
        lu.write_text(json.dumps({"nodes": avant_relecture, "links": liens}), encoding="utf-8")
        sans_page.write_text(json.dumps({"nodes": [fabrique[5]], "links": []}), encoding="utf-8")
        try:
            paquet = types.ModuleType("graphify")
            paquet.__path__ = []
            for nom, attribut, faux_objet in (
                ("graphify.extract", "extract", faux_extract),
                ("graphify.build", "build_merge", faux_build_merge),
            ):
                module = types.ModuleType(nom)
                setattr(module, attribut, faux_objet)
                sys.modules[nom] = module
            sys.modules["graphify"] = paquet
            relue = relire_la_structure(lu, ecrit, racine)
            rien = relire_la_structure(sans_page, racine / "jamais.json", racine)
        finally:
            for nom, mod in sauvegarde.items():
                if mod is None:
                    sys.modules.pop(nom, None)
                else:
                    sys.modules[nom] = mod
        # Un extrait absent se lit vide : le cas qui le juge rougit, au lieu que la lecture leve
        # hors de tout cas et arrete l'auto-test sans dire lequel.
        extrait_ecrit = json.loads(ecrit.read_text(encoding="utf-8")) if ecrit.exists() else {}
        obtenu = (
            relue,
            recu.get("chemins"),
            recu.get("root"),
            recu.get("graphe"),
            recu.get("racine"),
            [n["id"] for n in recu["lot"][0]["nodes"]],
        )
        attendu = (
            True,
            [racine / page],
            racine,
            str(lu),
            str(racine),
            ["ecran", "ecran_garde", "ecran_nouveau", "autre"],
        )
        jamais_ecrit = (racine / "jamais.json").exists()
    verifier(
        "la relecture extrait les pages choisies depuis la racine et fusionne dans le graphe lu",
        obtenu == attendu,
        f"obtenu : {obtenu}",
    )
    verifier(
        "l extrait porte ce que la fusion rend, et une arete y garde son sens d origine",
        extrait_ecrit.get("nodes") == [{"id": "ecran", "label": "Ecran"}]
        and extrait_ecrit.get("edges")
        == [{"relation": "contains", "source": "x", "target": "ecran"}]
        and extrait_ecrit.get("hyperedges") == [{"id": "h"}],
        f"obtenu : {extrait_ecrit}",
    )
    verifier(
        "un graphe sans page ne lance aucune extraction et n ecrit aucun extrait",
        (rien, recu["extractions"], jamais_ecrit) == (False, 1, False),
        f"obtenu : {(rien, recu['extractions'], jamais_ecrit)}",
    )

    # 10 : le chemin du crochet de commit. Il re-extrait ce qui est encore sur le disque, le
    # code ET les pages, en UNE extraction : deux fusions de suite partiraient chacune du graphe
    # du disque, et la seconde effacerait la premiere. Joue avec le faux moteur du cas 9.
    def sous_un_faux_moteur(action):
        d_avant = {n: sys.modules.get(n) for n in noms}
        try:
            paquet = types.ModuleType("graphify")
            paquet.__path__ = []
            for nom, attribut, faux_objet in (
                ("graphify.extract", "extract", faux_extract),
                ("graphify.build", "build_merge", faux_build_merge),
            ):
                module = types.ModuleType(nom)
                setattr(module, attribut, faux_objet)
                sys.modules[nom] = module
            sys.modules["graphify"] = paquet
            return action()
        finally:
            for nom, mod in d_avant.items():
                if mod is None:
                    sys.modules.pop(nom, None)
                else:
                    sys.modules[nom] = mod

    with tempfile.TemporaryDirectory() as temporaire:
        racine = Path(temporaire)
        for chemin in ("src/Classe.java", "docs/page.md"):
            (racine / chemin).parent.mkdir(parents=True, exist_ok=True)
            (racine / chemin).write_text("x", encoding="utf-8")
        lu, ecrit = racine / "graph.json", racine / "extrait.json"
        lu.write_text(json.dumps({"nodes": [], "links": []}), encoding="utf-8")
        modifies = [Path("src/Classe.java"), Path("docs/page.md"), Path("src/Partie.java")]
        avant_extraction = recu["extractions"]
        code_relu = sous_un_faux_moteur(lambda: extraire_les_modifies(modifies, racine, lu, ecrit))
        obtenu = (
            code_relu,
            recu.get("chemins"),
            recu.get("root"),
            recu.get("graphe"),
            ecrit.exists(),
        )
        attendu = (
            True,
            [racine / "src/Classe.java", racine / "docs/page.md"],
            racine,
            str(lu),
            True,
        )
        page_seule = sous_un_faux_moteur(
            lambda: extraire_les_modifies([Path("docs/page.md")], racine, lu, ecrit)
        )
        chemins_de_la_page_seule = recu.get("chemins")
        partis = [Path("src/Partie.java"), Path("docs/partie.md")]
        rien_a_relire = sous_un_faux_moteur(
            lambda: extraire_les_modifies(partis, racine, lu, racine / "jamais.json")
        )
        extractions = recu["extractions"] - avant_extraction
        jamais_ecrit = (racine / "jamais.json").exists()
    verifier(
        "le crochet re-extrait en une fois le code et la page encore sur le disque",
        obtenu == attendu,
        f"obtenu : {obtenu}",
    )
    verifier(
        "une page seule fait relire sa structure",
        (page_seule, chemins_de_la_page_seule) == (True, [racine / "docs/page.md"]),
        f"obtenu : {(page_seule, chemins_de_la_page_seule)}",
    )
    verifier(
        "un fichier et une page partis du disque ne lancent aucune extraction, et rien n est ecrit",
        (rien_a_relire, extractions, jamais_ecrit) == (False, 2, False),
        f"obtenu : {(rien_a_relire, extractions, jamais_ecrit)}",
    )

    # 10 bis : ce que le crochet DIT de la page relue (#5941). Le graphe et le faux moteur sont
    # ceux des cas 8 et 9 : la page `docs/ecran.md` a perdu un titre, deux aretes semantiques y
    # tenaient. L'extraction rend aussi `docs/autre.md`, qu'on ne lui a pas nommee : elle ne
    # compte pas.
    def journal_du_crochet(modifies):
        sortie = io.StringIO()
        with tempfile.TemporaryDirectory() as temporaire:
            racine = Path(temporaire)
            for chemin in (page, "docs/autre.md", "src/Classe.java"):
                (racine / chemin).parent.mkdir(parents=True, exist_ok=True)
                (racine / chemin).write_text("x", encoding="utf-8")
            lu = racine / "graph.json"
            lu.write_text(json.dumps({"nodes": avant_relecture, "links": liens}), encoding="utf-8")
            with contextlib.redirect_stdout(sortie):
                sous_un_faux_moteur(
                    lambda: extraire_les_modifies(modifies, racine, lu, racine / "extrait.json")
                )
        return [ligne.removeprefix("[graphify] ") for ligne in sortie.getvalue().splitlines()][:4]

    obtenu = journal_du_crochet([Path(page)])
    attendu = [
        "0 fichier(s) de code et 1 page(s) modifie(s), extraction de structure",
        "structure relue : 1 page(s) sur 1 ; 1 titre(s) retire(s) sur 1 page(s)",
        "2 arete(s) semantique(s) lachee(s), ancree(s) sur un titre disparu : docs/ecran.md (2)",
        "ces pages sont a reextraire : scripts/graphify/couche_semantique.py a-reextraire",
    ]
    verifier(
        "le crochet dit le titre retire de la page relue et les aretes semantiques qui tombent",
        obtenu == attendu,
        f"obtenu : {obtenu}",
    )
    # `docs/autre.md` n'a pas change de titres : l'extraction rend celui que le graphe porte.
    obtenu = journal_du_crochet([Path("docs/autre.md")])[:3]
    verifier(
        "une page dont les titres n ont pas change ne fait rien retirer, et rien n est dit lache",
        obtenu[:2]
        == [
            "0 fichier(s) de code et 1 page(s) modifie(s), extraction de structure",
            "structure relue : 1 page(s) sur 1 ; 0 titre(s) retire(s) sur 0 page(s)",
        ]
        and not any("lachee" in ligne for ligne in obtenu),
        f"obtenu : {obtenu}",
    )
    obtenu = journal_du_crochet([Path("src/Classe.java")])[:2]
    verifier(
        "sans page au commit, le crochet ne dit rien d une structure relue",
        obtenu[:1] == ["1 fichier(s) de code et 0 page(s) modifie(s), extraction de structure"]
        and not any("structure relue" in ligne for ligne in obtenu),
        f"obtenu : {obtenu}",
    )

    # 11 : les passes se jouent dans l'ordre donne, et une passe absente ne casse pas la chaine.
    with tempfile.TemporaryDirectory() as temporaire:
        dossier = Path(temporaire)
        for nom in ("premiere", "seconde"):
            (dossier / f"{nom}.py").write_text(
                "from pathlib import Path\n"
                "trace = Path(__file__).with_name('trace.txt')\n"
                f"trace.write_text((trace.read_text() if trace.exists() else '') + '{nom} ')\n",
                encoding="utf-8",
            )
        jouer_les_passes(dossier, ("premiere.py", "absente.py", "seconde.py"))
        trace = (dossier / "trace.txt").read_text(encoding="utf-8")
    verifier(
        "les passes se jouent dans l ordre, et une passe absente ne casse pas la chaine",
        trace == "premiere seconde ",
        f"obtenu : {trace!r}",
    )

    # 12 : le chemin du crochet de commit, `rebuild.py <fichiers>`. Meme refus que la mise a jour
    # quand le module manque, et le meme ordre sinon : extraction, ponts, reconstruction.
    def chemin_du_crochet(moteur_present):
        appels = []
        with tempfile.TemporaryDirectory() as temporaire:
            sortie = Path(temporaire)
            (sortie / "graph.json").write_text("{}", encoding="utf-8")
            temoins = {
                "GRAPHE": sortie / "graph.json",
                "SORTIE": sortie,
                "EXTRAIT": sortie / "extrait.json",
                "extraire_les_modifies": lambda changes: appels.append("extraction"),
                "jouer_les_passes": lambda: appels.append("ponts"),
                "reconstruire": lambda reference=None: appels.append("reconstruction"),
            }
            portee = globals()
            d_avant = {nom: portee.get(nom) for nom in temoins}
            moteur_d_avant = sys.modules.get("graphify")
            try:
                portee.update(temoins)
                sys.modules["graphify"] = types.ModuleType("graphify") if moteur_present else None
                with contextlib.redirect_stdout(io.StringIO()):
                    code = main(["src/Classe.java", "docs/page.md"])
            finally:
                if moteur_d_avant is None:
                    sys.modules.pop("graphify", None)
                else:
                    sys.modules["graphify"] = moteur_d_avant
                portee.update(d_avant)
            drapeau = (sortie / ".needs_update").exists()
        return code, appels, drapeau

    obtenu = chemin_du_crochet(True)
    verifier(
        "le crochet extrait ce qui a change, rejoue les ponts, reconstruit, et signale la page",
        obtenu == (0, ["extraction", "ponts", "reconstruction"], True),
        f"obtenu : {obtenu}",
    )
    obtenu = chemin_du_crochet(False)
    verifier(
        "sans le module graphify, le crochet refuse en 2 sans rien extraire ni signaler",
        obtenu == (2, [], False),
        f"obtenu : {obtenu}",
    )

    if echecs:
        print(f"\n{len(echecs)} cas en echec : {', '.join(echecs)}")
        return 1
    print("\nauto-test : tous les cas passent")
    return 0


def mets_a_jour() -> int:
    """`graphify update .`, puis ponts et reconstruction avec les libelles de l'etat d'avant."""
    if refus_sans_le_moteur():
        return 2
    outil = shutil.which("graphify")
    if outil is None:
        journal("REFUS : `graphify` est introuvable. Ce refus parle du poste, pas du depot.")
        return 2
    with tempfile.TemporaryDirectory() as temporaire:
        reference = Path(temporaire)
        for nom in ("graph.json", ".graphify_labels.json"):
            if (SORTIE / nom).exists():
                shutil.copy(SORTIE / nom, reference / nom)
        rendu = subprocess.run([outil, "update", "."], cwd=RACINE, check=False)
        if rendu.returncode:
            journal(f"`graphify update .` est sorti en {rendu.returncode}, rien n est reconstruit")
            return rendu.returncode
        try:
            # L'outil vient de relire le code et les pages sans autre couche. Les pages qui en
            # portent une, il les a laissees telles quelles : on relit leur structure ici, avant
            # que les ponts n'y cherchent leurs noeuds de page.
            relire_la_structure()
            jouer_les_passes()
            reconstruire(reference)
        finally:
            EXTRAIT.unlink(missing_ok=True)
    return 0


def main(argv: list[str]) -> int:
    if "--auto-test" in argv:
        return auto_test()
    if not GRAPHE.exists():
        journal(
            "aucun graphe existant, rien a mettre a jour (lancer /graphify . une premiere fois)"
        )
        return 0
    if "--mets-a-jour" in argv:
        return mets_a_jour()
    if refus_sans_le_moteur():
        return 2
    changes = [Path(a) for a in argv]
    if any(p.suffix in EXT_DOC for p in changes):
        (SORTIE / ".needs_update").write_text(
            "documentation modifiee : scripts/graphify/couche_semantique.py a-reextraire"
            " dit quelles pages\n",
            encoding="utf-8",
        )
        journal("documentation modifiee, drapeau .needs_update pose")
    try:
        extraire_les_modifies(changes)
        jouer_les_passes()
        reconstruire()
    finally:
        EXTRAIT.unlink(missing_ok=True)
    return 0


if __name__ == "__main__":
    sys.path.insert(0, str(RACINE))
    import os

    os.chdir(RACINE)
    raise SystemExit(main(sys.argv[1:]))

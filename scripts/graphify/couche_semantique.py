#!/usr/bin/env python3
"""Decoupe, audite et fusionne les lots de la couche semantique du graphe du depot.

La couche de structure du graphe se tient a jour sans LLM, par `rebuild.py`. La couche semantique,
elle, sort d agents qui lisent la prose : chacun rend un lot, et les lots se fusionnent. Trois
defauts de cette fusion ne font rougir aucun dispositif, et l ADR 5790 les nomme. Cet outil porte
leurs parades, et un quatrieme controle que la mise a jour du 4 octobre 2026 a rendu necessaire.

  1. un lot ne REEMET JAMAIS un identifiant de structure : il s y ancre par une arete ;
  2. un noeud semantique HOMONYME d un titre se REPLIE sur ce titre, sans quoi le dedoublonnage
     fusionne les deux sous l identifiant semantique et celui du titre disparait ;
  3. le champ des MEMBRES d une hyperarete se normalise : les agents l ecrivent de quatre facons,
     le moteur n en lit qu une, et les autres hyperaretes tombent sans message ;
  4. un lot de MISE A JOUR remplace toute la couche de sa page : un identifiant semantique qu il
     ne reemet pas est efface, et les aretes venues d autres pages se rompent. Il le DECLARE ;
  5. une hyperarete SANS IDENTIFIANT tombe a la fusion suivante, meme a vide. Elle en recoit un.

La cinquieme a ete trouvee en rejouant cet outil sur des lots reels, le 4 octobre 2026 : 25 des 71
hyperaretes de la premiere extraction n avaient pas d `id`, et la mise a jour du meme jour les a
toutes effacees, dont 22 sur des pages qu elle ne touchait pas. Aucun compte de noeuds ne le montre.

Mesure du 4 octobre 2026, sur la premiere fusion de 32 lots : 62 identifiants de structure perdus
sans la parade 2, dont 10 par ressemblance approchee ; 101 replis et aucune perte avec elle.

La perte se juge contre une FUSION A VIDE, qui fait deja tomber environ 550 identifiants, presque
tous des titres repetes du journal des versions. Le premier garde en comptait 612 et refusait a
tort : seul l ecart est imputable au lot.

Usage :
    couche_semantique.py decoupe  --dossier DIR [--graphe G] PAGE [PAGE ...]
    couche_semantique.py audite   --dossier DIR
    couche_semantique.py fusionne --dossier DIR [--graphe G]
    couche_semantique.py --auto-test

`decoupe` ecrit dans DIR un `lot_NN.json` par lot, plus les index du code et des pages. Chaque agent
lit `consigne-des-agents.md` et rend `rendu_NN.json` dans le meme dossier. `fusionne` refuse si
l audit refuse, et abandonne sans toucher au graphe si un identifiant de structure manque.

Cet outil ne juge aucune demande et ne declare pas de CONTRAT : il repond a qui refait la couche.
Seul `fusionne` a besoin de graphify, et il sort en 2 quand il manque.
"""

from __future__ import annotations

import difflib
import hashlib
import json
import re
import sys
import tempfile
import unicodedata
from pathlib import Path

RACINE = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import cas_d_auto_test

GRAPHE = RACINE / "graphify-out" / "graph.json"
IDENTIFIANT = re.compile(r"^[a-z0-9_]+$")
# Les quatre noms que les agents ont donnes au champ des membres, le 4 octobre 2026. Le moteur ne
# lit que le premier et normalise le deuxieme ; les deux autres faisaient tomber l hyperarete.
CHAMPS_DES_MEMBRES = ("nodes", "members", "member_ids", "node_ids")
# Le seuil du dedoublonnage approche de graphify : en dessous, il ne fusionne pas, donc rien a parer.
SEUIL_DE_RESSEMBLANCE = 0.85
PLAFOND_DE_MOTS = 22_000
PLAFOND_DE_PAGES = 40


def cle_de_libelle(libelle: object) -> str:
    """Le libelle reduit a ses lettres et chiffres, sans accent ni casse.

    C est ce que le dedoublonnage compare : « `WINGET_TOKEN` : huit jours » et « WINGET_TOKEN : huit
    jours » y sont le meme noeud, et l un efface l autre.
    """
    plat = unicodedata.normalize("NFKD", str(libelle)).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]", "", plat.casefold())


def identifiant_d_hyperarete(hyperarete: dict, membres: list[str]) -> str:
    """Un identifiant STABLE pour une hyperarete qui n en porte pas.

    Derive de ce qui la definit, sa page, son libelle et ses membres, pour que deux fusions du
    meme lot lui donnent le meme : un compteur la ferait doubler a chaque rejeu.
    """
    graine = "|".join(
        [str(hyperarete.get("source_file")), str(hyperarete.get("label")), *sorted(membres)]
    )
    return "hyper_" + hashlib.sha1(graine.encode("utf-8")).hexdigest()[:12]


def normalise_hyperaretes(hyperaretes: list[dict]) -> list[dict]:
    """Range les membres sous `nodes`, et donne un identifiant a l hyperarete qui n en a pas."""
    rendues = []
    for hyperarete in hyperaretes:
        propre = {k: v for k, v in hyperarete.items() if k not in CHAMPS_DES_MEMBRES}
        membres = next((hyperarete[c] for c in CHAMPS_DES_MEMBRES if c in hyperarete), [])
        propre["nodes"] = list(membres)
        if not propre.get("id"):
            propre["id"] = identifiant_d_hyperarete(hyperarete, propre["nodes"])
        rendues.append(propre)
    return rendues


def jumeau_de_structure(noeud: dict, structure: list[dict]) -> str | None:
    """L identifiant du noeud de structure du MEME fichier dont le libelle vaut celui-ci, ou None.

    L egalite d abord, la ressemblance ensuite, au seuil ou le dedoublonnage fusionnerait.
    """
    cle = cle_de_libelle(noeud["label"])
    candidats = [s for s in structure if s.get("source_file") == noeud.get("source_file")]
    for candidat in candidats:
        if cle_de_libelle(candidat["label"]) == cle:
            return candidat["id"]
    for candidat in candidats:
        autre = cle_de_libelle(candidat["label"])
        if difflib.SequenceMatcher(None, cle, autre).ratio() >= SEUIL_DE_RESSEMBLANCE:
            return candidat["id"]
    return None


def replie_les_homonymes(
    noeuds: list[dict], aretes: list[dict], hyperaretes: list[dict], structure: list[dict]
) -> tuple[list[dict], list[dict], list[dict], dict[str, str], dict[str, str]]:
    """Replie chaque noeud semantique homonyme d un noeud de structure sur ce dernier.

    Rend les noeuds restants, les aretes et hyperaretes recablees, la table des replis, et les
    justifications a reporter sur les noeuds de structure : l information du noeud replie ne se
    perd pas, elle change de porteur.
    """
    replis: dict[str, str] = {}
    justifications: dict[str, str] = {}
    gardes = []
    for noeud in noeuds:
        cible = jumeau_de_structure(noeud, structure)
        if cible is None or cible == noeud["id"]:
            gardes.append(noeud)
            continue
        replis[noeud["id"]] = cible
        if noeud.get("rationale"):
            justifications[cible] = noeud["rationale"]
    recablees = []
    for arete in aretes:
        neuve = dict(arete)
        neuve["source"] = replis.get(arete["source"], arete["source"])
        neuve["target"] = replis.get(arete["target"], arete["target"])
        if neuve["source"] != neuve["target"]:
            recablees.append(neuve)
    hyper = []
    for hyperarete in normalise_hyperaretes(hyperaretes):
        hyperarete["nodes"] = [replis.get(m, m) for m in hyperarete["nodes"]]
        hyper.append(hyperarete)
    return gardes, recablees, hyper, replis, justifications


def audite(
    rendu: dict, fiche: dict, autres_connus: set[str], emis_ailleurs: frozenset[str] = frozenset()
) -> dict[str, list[str]]:
    """Les defauts d un lot rendu, confronte a la fiche que `decoupe` lui avait donnee.

    Rend un dictionnaire vide quand le lot est sain. `autres_connus` porte les identifiants des
    index du code et des pages : un lot peut les viser par une arete, jamais les reemettre.

    `emis_ailleurs` porte ce que les AUTRES lots de la meme passe emettent. Une arete peut y
    aboutir : deux ADR qui s amendent tombent dans deux lots des que le decoupage les separe, et
    le premier rejeu sur des lots reels refusait a tort cinq aretes de cette forme.
    """
    structure = {n["id"] for page in fiche.values() for n in page["structure"]}
    semantiques = {n["id"] for page in fiche.values() for n in page.get("semantique", [])}
    emis = [n["id"] for n in rendu.get("nodes", [])]
    ensemble = set(emis)
    items = rendu.get("nodes", []) + rendu.get("edges", [])
    declares = set(rendu.get("laches", []))
    defauts = {
        "identifiant de structure reemis": sorted(ensemble & (structure | autres_connus)),
        "identifiant mal forme": sorted(
            i for i in ensemble - semantiques if not IDENTIFIANT.match(i)
        ),
        "identifiant en double": sorted({i for i in emis if emis.count(i) > 1}),
        "extremite inconnue": sorted(
            {
                arete[bout]
                for arete in rendu.get("edges", [])
                for bout in ("source", "target")
                if arete[bout] not in ensemble | structure | autres_connus | emis_ailleurs
            }
        ),
        "source_location non nul": sorted(
            {str(x.get("id", x.get("source"))) for x in items if x.get("source_location")}
        ),
        "origine autre que semantic": sorted(
            {str(x.get("id", x.get("source"))) for x in items if x.get("_origin") != "semantic"}
        ),
        "source_file hors du lot": sorted(
            {str(x.get("source_file")) for x in items if x.get("source_file") not in fiche}
        ),
        "page sans noeud": sorted(
            page
            for page in fiche
            if not any(n.get("source_file") == page for n in rendu.get("nodes", []))
        ),
        "identifiant semantique lache sans le declarer": sorted(semantiques - ensemble - declares),
    }
    return {nom: liste for nom, liste in defauts.items() if liste}


def perdus_imputables(avant: set[str], apres: set[str], a_vide: set[str]) -> set[str]:
    """Les identifiants de structure que le LOT a fait tomber, hors de ce qu une fusion a vide perd."""
    return (avant - apres) - a_vide


def decoupe(
    mots: dict[str, int], plafond_de_mots: int = PLAFOND_DE_MOTS, plafond: int = PLAFOND_DE_PAGES
) -> list[list[str]]:
    """Range les pages en lots, dans l ordre des chemins pour garder les voisines ensemble.

    Une page plus lourde que le plafond fait un lot a elle seule : la couper ferait lire a deux
    agents deux moities d un meme raisonnement.
    """
    lots: list[list[str]] = []
    courant: list[str] = []
    poids = 0
    for page in sorted(mots):
        if courant and (poids + mots[page] > plafond_de_mots or len(courant) >= plafond):
            lots.append(courant)
            courant, poids = [], 0
        courant.append(page)
        poids += mots[page]
    if courant:
        lots.append(courant)
    return lots


def _lis(chemin: Path) -> dict:
    return json.loads(chemin.read_text(encoding="utf-8"))


def _ecris(chemin: Path, contenu: object) -> None:
    chemin.write_text(json.dumps(contenu, ensure_ascii=False, indent=1), encoding="utf-8")


def _noeuds_du_graphe(graphe: Path) -> list[dict]:
    return _lis(graphe)["nodes"]


def commande_decoupe(dossier: Path, graphe: Path, pages: list[str]) -> int:
    """Ecrit les fiches de lot et les deux index dont les agents ont besoin."""
    absentes = [p for p in pages if not (RACINE / p).is_file()]
    if absentes:
        print(f"REFUS : {len(absentes)} page(s) introuvable(s) : {absentes[:5]}", file=sys.stderr)
        return 2
    noeuds = _noeuds_du_graphe(graphe)
    par_page: dict[str, dict[str, list[dict]]] = {}
    for noeud in noeuds:
        origine = noeud.get("_origin")
        if origine not in ("ast", "semantic"):
            continue
        fiche = par_page.setdefault(noeud.get("source_file"), {"structure": [], "semantique": []})
        court = {"id": noeud["id"], "label": noeud["label"]}
        if origine == "ast":
            fiche["structure"].append({**court, "node_kind": noeud.get("node_kind")})
        else:
            fiche["semantique"].append(court)
    textes = {p: (RACINE / p).read_text(encoding="utf-8", errors="ignore") for p in pages}
    lots = decoupe({p: len(t.split()) for p, t in textes.items()})
    dossier.mkdir(parents=True, exist_ok=True)
    for numero, lot in enumerate(lots, 1):
        fiche = {
            page: {
                "lignes": textes[page].count("\n") + 1,
                **par_page.get(page, {"structure": [], "semantique": []}),
            }
            for page in lot
        }
        _ecris(dossier / f"lot_{numero:02d}.json", fiche)
    classes = [
        {"id": n["id"], "label": n["label"], "source_file": n["source_file"]}
        for n in noeuds
        if n.get("_callable_class")
    ]
    feuilles = [
        {"id": n["id"], "source_file": n["source_file"]}
        for n in noeuds
        if n.get("node_kind") == "page"
    ]
    _ecris(dossier / "index-du-code.json", classes)
    _ecris(dossier / "index-des-pages.json", feuilles)
    _ecris(dossier / "plan.json", {"lots": lots})
    print(f"DECOUPE | pages={len(pages)} | lots={len(lots)} | dossier={dossier}")
    return 0


def _connus(dossier: Path) -> set[str]:
    return {x["id"] for x in _lis(dossier / "index-du-code.json")} | {
        x["id"] for x in _lis(dossier / "index-des-pages.json")
    }


def commande_audite(dossier: Path) -> int:
    """Audite chaque lot attendu. Un lot ABSENT est un defaut, jamais un lot de moins."""
    plan = _lis(dossier / "plan.json")["lots"]
    connus = _connus(dossier)
    rendus = {
        numero: _lis(dossier / f"rendu_{numero:02d}.json")
        for numero in range(1, len(plan) + 1)
        if (dossier / f"rendu_{numero:02d}.json").is_file()
    }
    refus = 0
    for numero in range(1, len(plan) + 1):
        if numero not in rendus:
            print(f"lot {numero:02d} : ABSENT")
            refus += 1
            continue
        ailleurs = frozenset(
            n["id"] for autre, rendu in rendus.items() if autre != numero for n in rendu["nodes"]
        )
        fiche = _lis(dossier / f"lot_{numero:02d}.json")
        defauts = audite(rendus[numero], fiche, connus, ailleurs)
        for nom, liste in defauts.items():
            print(f"lot {numero:02d} : {nom} ({len(liste)}) : {liste[:5]}")
        refus += bool(defauts)
    print(f"AUDIT | lots={len(plan)} | refuses={refus}")
    return 1 if refus else 0


def commande_fusionne(dossier: Path, graphe: Path) -> int:
    """Fusionne les lots audites dans le graphe, puis rejoue les ponts et la reconstruction."""
    try:
        from graphify.build import build_merge
    except ModuleNotFoundError:
        print(
            "REFUS : graphify n est pas importable par cet interprete. Ce refus parle du poste,"
            " pas des lots : lancer avec l interprete de graphify-out/.graphify_python.",
            file=sys.stderr,
        )
        return 2
    if commande_audite(dossier):
        print("REFUS : l audit refuse, la fusion n est pas tentee.", file=sys.stderr)
        return 1
    plan = _lis(dossier / "plan.json")["lots"]
    noeuds: dict[str, dict] = {}
    aretes: list[dict] = []
    hyperaretes: list[dict] = []
    for numero in range(1, len(plan) + 1):
        rendu = _lis(dossier / f"rendu_{numero:02d}.json")
        for noeud in rendu["nodes"]:
            noeuds.setdefault(noeud["id"], noeud)
        aretes += rendu["edges"]
        hyperaretes += [{**h, "_origin": "semantic"} for h in rendu.get("hyperedges", [])]
    avant = _noeuds_du_graphe(graphe)
    structure = [n for n in avant if n.get("_origin") == "ast"]
    ids_de_structure = {n["id"] for n in structure}
    gardes, recablees, hyper, replis, justifications = replie_les_homonymes(
        list(noeuds.values()), aretes, hyperaretes, structure
    )
    print(f"replis sur un noeud de structure : {len(replis)}")

    vide = {"nodes": [], "edges": [], "hyperedges": []}
    a_vide = ids_de_structure - set(
        build_merge([vide], graph_path=str(graphe), root=str(RACINE), directed=False).nodes
    )
    lot = {"nodes": gardes, "edges": recablees, "hyperedges": hyper}
    fusion = build_merge([lot], graph_path=str(graphe), root=str(RACINE), directed=False)
    perdus = perdus_imputables(ids_de_structure, set(fusion.nodes), a_vide)
    print(f"FUSION | noeuds={fusion.number_of_nodes()} | perdus du fait des lots={len(perdus)}")
    if perdus:
        print(f"REFUS : identifiants de structure perdus : {sorted(perdus)[:10]}", file=sys.stderr)
        print("Le graphe n a pas ete touche.", file=sys.stderr)
        return 1
    for cible, texte in justifications.items():
        if cible in fusion.nodes:
            fusion.nodes[cible]["rationale"] = texte

    import rebuild

    rebuild.EXTRAIT.write_text(
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
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    try:
        rebuild.jouer_les_passes()
        rebuild.reconstruire()
    finally:
        rebuild.EXTRAIT.unlink(missing_ok=True)
    return 0


def auto_test() -> int:
    """Les cas des quatre parades, sur des lots FABRIQUES : graphify n y est jamais importe.

    Chaque parade a un cas qui rougit quand on la retire ET un cas temoin qui reste vert, sans quoi
    rien ne distinguerait un controle qui detecte d un controle qui refuse toujours.
    """
    verifie, echecs = cas_d_auto_test()

    page = "dev-docs/page.md"
    structure = [
        {"id": "dev_docs_page", "label": "page.md", "source_file": page},
        {
            "id": "dev_docs_page_le_portail",
            "source_file": page,
            "label": "Le portail regarde les deux zones, et le code mort compte",
        },
        {"id": "dev_docs_autre_titre", "label": "Le depot part en WAV", "source_file": "autre.md"},
    ]
    fiche = {
        page: {
            "lignes": 10,
            "structure": [{"id": s["id"], "label": s["label"]} for s in structure[:2]],
            "semantique": [{"id": "dev_docs_page_ancien", "label": "Un concept deja la"}],
        }
    }

    def noeud(identifiant: str, libelle: str, **plus: object) -> dict:
        return {
            "id": identifiant,
            "label": libelle,
            "source_file": page,
            "source_location": None,
            "_origin": "semantic",
            **plus,
        }

    def arete(source: str, cible: str) -> dict:
        return {
            "source": source,
            "target": cible,
            "relation": "references",
            "source_file": page,
            "source_location": None,
            "_origin": "semantic",
        }

    sain = {
        "nodes": [
            noeud("dev_docs_page_ancien", "Un concept deja la"),
            noeud("dev_docs_page_neuf", "Un concept neuf"),
        ],
        "edges": [
            arete("dev_docs_page_le_portail", "dev_docs_page_neuf"),
            arete("dev_docs_page_neuf", "une_classe"),
        ],
    }
    connus = {"une_classe"}

    def avec(**change: object) -> dict:
        return {**sain, **change}

    def noms(rendu: dict) -> list[str]:
        return sorted(audite(rendu, fiche, connus))

    verifie("un lot sain ne porte aucun defaut", lambda: noms(sain), [])

    # ⟨parade 1⟩ un identifiant de structure, ou de l index du code, ne se reemet pas.
    verifie(
        "un lot qui reemet un identifiant de structure est refuse",
        lambda: noms(avec(nodes=[*sain["nodes"], noeud("dev_docs_page_le_portail", "Autre")])),
        ["identifiant de structure reemis"],
    )
    verifie(
        "un lot qui reemet un identifiant de l index du code est refuse",
        lambda: noms(avec(nodes=[*sain["nodes"], noeud("une_classe", "Une classe")])),
        ["identifiant de structure reemis"],
    )

    # ⟨parade 2⟩ l homonyme d un titre se replie, a l egalite comme a la ressemblance.
    # Le libelle du titre se lie a un NOM avant l appel : le releve des harnais muets compte toute
    # expression indexee passee en second argument, et cette aide n est pas un cas.
    titre = structure[1]["label"]
    exact = noeud("dev_docs_page_decision", titre, rationale="la raison")
    proche = noeud(
        "dev_docs_page_decision",
        "Le portail regarde les deux zones de code, et le code mort y compte",
    )
    distinct = noeud("dev_docs_page_cliquet", "Le cliquet du portail descend")
    verifie(
        "un homonyme exact d un titre se replie sur ce titre",
        lambda: replie_les_homonymes([exact], [], [], structure)[3],
        {"dev_docs_page_decision": "dev_docs_page_le_portail"},
    )
    verifie(
        "un homonyme APPROCHE se replie aussi, au seuil du dedoublonnage",
        lambda: replie_les_homonymes([proche], [], [], structure)[3],
        {"dev_docs_page_decision": "dev_docs_page_le_portail"},
    )
    # L egalite passe AVANT la ressemblance : sans cet ordre, un titre voisin place plus haut dans
    # la page capterait le noeud a la place du titre qu il nomme exactement.
    voisins = [
        {"id": "p_voisin", "label": "Le cliquet du portail descend vite", "source_file": page},
        {"id": "p_exact", "label": "Le cliquet du portail descend", "source_file": page},
    ]
    verifie(
        "un titre EXACT l emporte sur un titre voisin place avant lui",
        lambda: replie_les_homonymes([distinct], [], [], voisins)[3],
        {"dev_docs_page_cliquet": "p_exact"},
    )
    verifie(
        "un concept distinct du meme fichier n est pas replie",
        lambda: replie_les_homonymes([distinct], [], [], structure)[3],
        {},
    )
    verifie(
        "un homonyme d un titre d un AUTRE fichier n est pas replie",
        lambda: replie_les_homonymes(
            [noeud("dev_docs_page_wav", "Le depot part en WAV")], [], [], structure
        )[3],
        {},
    )
    verifie(
        "les aretes du noeud replie visent desormais le titre",
        lambda: [
            (a["source"], a["target"])
            for a in replie_les_homonymes(
                [exact, distinct],
                [arete("dev_docs_page_decision", "dev_docs_page_cliquet")],
                [],
                structure,
            )[1]
        ],
        [("dev_docs_page_le_portail", "dev_docs_page_cliquet")],
    )
    verifie(
        "la justification du noeud replie passe sur le titre",
        lambda: replie_les_homonymes([exact], [], [], structure)[4],
        {"dev_docs_page_le_portail": "la raison"},
    )

    # ⟨parade 3⟩ les quatre noms du champ des membres rendent la meme hyperarete.
    # Les quatre noms sont ECRITS ici et non lus dans la constante : boucler sur elle ferait
    # retrecir le temoin avec ce qu il eprouve.
    for champ in ("nodes", "members", "member_ids", "node_ids"):
        verifie(
            f"une hyperarete dont les membres sont sous `{champ}` les garde",
            lambda champ=champ: normalise_hyperaretes([{"id": "h", champ: ["a", "b", "c"]}]),
            [{"id": "h", "nodes": ["a", "b", "c"]}],
        )
    verifie(
        "les membres d une hyperarete suivent le repli",
        lambda: replie_les_homonymes(
            [exact], [], [{"id": "h", "member_ids": ["dev_docs_page_decision", "x"]}], structure
        )[2],
        [{"id": "h", "nodes": ["dev_docs_page_le_portail", "x"]}],
    )

    # ⟨parade 5⟩ une hyperarete sans identifiant en recoit un, stable, et celle qui en a le garde.
    orpheline = {"label": "Un flux", "source_file": page, "nodes": ["b", "a", "c"]}
    verifie(
        "une hyperarete sans identifiant en recoit un",
        lambda: [h["id"].startswith("hyper_") for h in normalise_hyperaretes([orpheline])],
        [True],
    )
    verifie(
        "le meme lot rejoue donne le meme identifiant, quel que soit l ordre des membres",
        lambda: (
            normalise_hyperaretes([orpheline])[0]["id"]
            == normalise_hyperaretes([{**orpheline, "nodes": ["c", "a", "b"]}])[0]["id"]
        ),
        True,
    )
    verifie(
        "deux hyperaretes distinctes de la meme page ne recoivent pas le meme identifiant",
        lambda: (
            normalise_hyperaretes([orpheline])[0]["id"]
            != normalise_hyperaretes([{**orpheline, "label": "Un autre flux"}])[0]["id"]
        ),
        True,
    )
    verifie(
        "un identifiant vide vaut une absence",
        lambda: normalise_hyperaretes([{**orpheline, "id": ""}])[0]["id"].startswith("hyper_"),
        True,
    )

    # ⟨parade 4⟩ une mise a jour qui lache un identifiant semantique le declare, ou est refusee.
    lache = avec(nodes=[sain["nodes"][1]])
    verifie(
        "un lot de mise a jour qui lache un identifiant semantique sans le dire est refuse",
        lambda: noms(lache),
        ["identifiant semantique lache sans le declarer"],
    )
    verifie(
        "le meme lot est accepte quand il declare ce qu il lache",
        lambda: noms({**lache, "laches": ["dev_docs_page_ancien"]}),
        [],
    )

    # ⟨le reste de l audit⟩ chaque defaut a son cas, sans quoi son nom ne prouve rien.
    verifie(
        "un `source_location` rempli est refuse : il ferait passer le noeud pour de la structure",
        lambda: noms(
            avec(nodes=[sain["nodes"][0], {**sain["nodes"][1], "source_location": "L42"}])
        ),
        ["source_location non nul"],
    )
    verifie(
        "une arete vers un identifiant que rien ne connait est refusee",
        lambda: noms(avec(edges=[arete("dev_docs_page_neuf", "nulle_part")])),
        ["extremite inconnue"],
    )
    verifie(
        "une arete vers un noeud emis par un AUTRE lot de la passe est acceptee",
        lambda: sorted(
            audite(
                avec(edges=[arete("dev_docs_page_neuf", "noeud_d_un_autre_lot")]),
                fiche,
                connus,
                frozenset({"noeud_d_un_autre_lot"}),
            )
        ),
        [],
    )
    verifie(
        "une page du lot restee sans noeud est refusee",
        lambda: noms({"nodes": [], "edges": [], "laches": ["dev_docs_page_ancien"]}),
        ["page sans noeud"],
    )

    # ⟨le temoin de la fusion a vide⟩ seul l ecart au lot vide est imputable.
    verifie(
        "ce qu une fusion a vide perd deja n est pas impute au lot",
        lambda: perdus_imputables({"a", "b", "c", "d"}, {"a"}, {"b", "c"}),
        {"d"},
    )
    verifie(
        "un lot qui ne perd rien de plus que la fusion a vide est sain",
        lambda: perdus_imputables({"a", "b", "c"}, {"a"}, {"b", "c"}),
        set(),
    )

    # ⟨le decoupage⟩ toutes les pages, dans l ordre, sous les deux plafonds.
    poids = {"b.md": 10, "a.md": 15, "c.md": 30, "d.md": 5}
    verifie(
        "le decoupage garde toutes les pages, dans l ordre des chemins",
        lambda: decoupe(poids, plafond_de_mots=25),
        [["a.md", "b.md"], ["c.md"], ["d.md"]],
    )
    verifie(
        "le plafond de pages coupe un lot de pages legeres",
        lambda: decoupe(poids, plafond_de_mots=1000, plafond=3),
        [["a.md", "b.md", "c.md"], ["d.md"]],
    )

    # ⟨par le point d entree ENTIER⟩ le refus et le cas positif empruntent `main`, pas `audite`.
    def par_main(rendu: dict | None) -> int:
        with tempfile.TemporaryDirectory() as temporaire:
            dossier = Path(temporaire)
            _ecris(dossier / "plan.json", {"lots": [[page]]})
            _ecris(dossier / "lot_01.json", fiche)
            _ecris(dossier / "index-du-code.json", [{"id": "une_classe"}])
            _ecris(dossier / "index-des-pages.json", [])
            if rendu is not None:
                _ecris(dossier / "rendu_01.json", rendu)
            return main(["couche_semantique.py", "audite", "--dossier", str(dossier)])

    def sans_plan() -> int:
        with tempfile.TemporaryDirectory() as temporaire:
            return main(["couche_semantique.py", "audite", "--dossier", temporaire])

    verifie("`audite` refuse en 2 un dossier que `decoupe` n a pas prepare", lambda: sans_plan(), 2)
    verifie("`audite` sort en 0 sur un lot sain", lambda: par_main(sain), 0)
    verifie("`audite` sort en 1 sur un lot en defaut", lambda: par_main(lache), 1)
    verifie("`audite` sort en 1 quand un lot attendu est ABSENT", lambda: par_main(None), 1)

    return echecs()


USAGE = __doc__.split("Usage :")[1].split("\n\n")[0]


def main(argv: list[str]) -> int:
    arguments = argv[1:]
    if "--auto-test" in arguments:
        return auto_test()
    if not arguments or arguments[0] not in ("decoupe", "audite", "fusionne"):
        print("Usage :" + USAGE, file=sys.stderr)
        return 2
    commande, reste = arguments[0], arguments[1:]
    dossier = graphe = None
    pages = []
    while reste:
        mot = reste.pop(0)
        if mot == "--dossier" and reste:
            dossier = Path(reste.pop(0))
        elif mot == "--graphe" and reste:
            graphe = Path(reste.pop(0))
        else:
            pages.append(mot)
    if dossier is None:
        print("REFUS : --dossier est obligatoire.\nUsage :" + USAGE, file=sys.stderr)
        return 2
    graphe = graphe or GRAPHE
    if commande != "decoupe" and not (dossier / "plan.json").is_file():
        # Un dossier que `decoupe` n a pas prepare n est pas un audit vide : c est une erreur
        # d appel, et elle se dit au lieu de se lire dans une trace de pile.
        print(
            f"REFUS : {dossier} ne porte aucun plan.json, lancer `decoupe` d abord.",
            file=sys.stderr,
        )
        return 2
    if commande == "audite":
        return commande_audite(dossier)
    if not graphe.is_file():
        print(f"REFUS : aucun graphe a {graphe}.", file=sys.stderr)
        return 2
    if commande == "decoupe":
        return commande_decoupe(dossier, graphe, pages)
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    return commande_fusionne(dossier, graphe)


if __name__ == "__main__":
    sys.exit(main(sys.argv))

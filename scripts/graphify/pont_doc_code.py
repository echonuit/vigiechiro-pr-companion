"""Relie la documentation au code dans le graphe graphify.

Deux passes deterministes, sans appel LLM :
  A. dedoublonnage : les noeuds file_type=code loges dans un fichier de doc sont
     des doublons de classes AST reelles ; on recable leurs aretes vers la vraie
     classe et on supprime le fantome.
  B. citation : toute classe nommee DANS UN SPAN DE CODE d'un document produit une
     arete `references` EXTRACTED du noeud de niveau fichier vers la classe.
     Le texte libre est ecarte (homonymes : Passage, Importer, Verdict...).

Le noeud de niveau fichier d'une page est son noeud de PAGE, ou qu'elle vive. La passe B
parcourait les `.md` de la racine sans reconnaitre le leur : elle en fabriquait un second,
suffixe `_doc`, et ce suffixe est celui que le moteur reserve au jumeau d'un document. Il
supprimait alors le noeud de page au profit du doublon. Cinq pages de la racine, dont
`AGENTS.md`, y ont perdu leur noeud de page (#5868).
"""

import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

RACINE = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import cas_d_auto_test

DOCDIRS = ("brief/", "dev-docs/", "docs/")

# `graphify.build._doc_twin_remap` tient `<x>_doc` pour le jumeau semantique du document `<x>` :
# meme fichier, `file_type: document` des deux cotes, et il supprime `<x>`. L'ancienne passe B
# fabriquait de tels noeuds ; ce qu'il en reste dans un graphe quitte ce suffixe pour celui-ci.
SUFFIXE_DE_PONT = "_pont"

# Ce que le harnais des cas imprime apres la passe, et le code qu'il rend quand il ne l'a pas pu.
MAIN_RENDUE = "la passe a rendu la main"
PASSE_SORTIE_SANS_RENDRE_LA_MAIN = "la passe est sortie sans rendre la main"


def est_doc(n):
    return (n.get("source_file") or "").startswith(DOCDIRS)


def norm_id(p):
    return re.sub(r"[^a-z0-9]+", "_", str(p).rsplit(".", 1)[0].lower()).strip("_")


# Les pages que les ponts ne relient pas, nommement. Le journal des versions cite tout le depot ;
# `.claude/skills` est une COPIE de `.agents/skills`, que `synchronise-adaptateurs.py` tient : ses
# citations entreraient en double. 32 pages sur 32 y etaient identiques octet pour octet le
# 5 octobre 2026.
PAGES_NON_RELIEES = ("CHANGELOG.md",)
DOSSIERS_NON_RELIES = (".claude/skills/",)


def pages_reliees(noeuds, racine=None):
    """Les pages dont les ponts relient les citations : celles qui ont un noeud de page.

    Le parcours se DERIVE du graphe. Il venait d'une liste de dossiers, `brief/`, `dev-docs/`,
    `docs/` et la racine, pendant que le noeud de fichier se reconnaissait par une autre regle :
    deux ecritures de « quelles pages », et c'est leur ecart qui a fait perdre leur noeud de page
    a cinq pages de la racine (#5868). Une seule regle ne peut pas diverger d'elle-meme. Elle relie
    aussi les 173 pages qui vivaient hors de la liste, celles d'`openspec/` surtout (#5904).
    """
    racine = racine or Path(".")
    return sorted(
        Path(chemin)
        for chemin in noeuds_de_page(noeuds)
        if chemin not in PAGES_NON_RELIEES
        and not chemin.startswith(DOSSIERS_NON_RELIES)
        and (racine / chemin).is_file()
    )


def est_fabrique(n):
    """Vrai pour un noeud de fichier que l'ancienne passe B fabriquait, faute d'en trouver un."""
    return (
        n.get("_origin") == "pont"
        and n.get("file_type") == "document"
        and n.get("source_location") == "L1"
        and n.get("node_kind") != "page"
    )


def noeuds_de_page(noeuds):
    """Le noeud de PAGE de chaque document, par `source_file`, quel que soit son dossier.

    C'est de lui que partent les citations. Une page que la structure ne porte pas n'en a pas, et
    la passe ne lui en fabrique plus : un noeud de document qui n'est pas de structure fait passer
    sa page pour couverte, et `graphify update .` ne lit plus la structure d'une page couverte.
    """
    retenus = {}
    for n in noeuds:
        sf = n.get("source_file")
        if sf and n.get("file_type") == "document" and n.get("node_kind") == "page":
            retenus.setdefault(sf, n)
    return retenus


def identifiant_libre(base, pris):
    """`base_pont`, ou le premier de ses prolongements qui soit libre. Jamais `base_doc`."""
    candidat = base + SUFFIXE_DE_PONT
    while candidat in pris:
        candidat += SUFFIXE_DE_PONT
    return candidat


def resorbe(noeuds, aretes, hyperaretes):
    """Retire ce que l'ancienne passe B a laisse, et ancre les citations des ponts sur la page.

    Un noeud qu'elle avait fabrique se replie sur le noeud de page des que la page en a un, et ses
    aretes le suivent. Tant qu'elle n'en a pas, il quitte seulement le suffixe reserve : sans cela
    le moteur reprendrait le noeud de page le jour ou la structure le rend. Une citation de pont
    partie d'un autre noeud de la page, le premier titre le plus souvent, repart de la page.

    Rend `(noeuds, aretes, hyperaretes, comptes)` sans toucher a ses arguments.
    """
    page = noeuds_de_page(noeuds.values())
    devient = {}
    for n in noeuds.values():
        sf = n.get("source_file")
        # Seules les pages : une autre passe fabrique le noeud de document d'un schema `.mcd`.
        if not str(sf).endswith(".md") or not est_fabrique(n):
            continue
        if sf in page:
            devient[n["id"]] = page[sf]["id"]
        elif not n["id"].endswith(SUFFIXE_DE_PONT):
            devient[n["id"]] = identifiant_libre(norm_id(sf), set(noeuds) | set(devient.values()))

    gardes = {}
    for identifiant, n in noeuds.items():
        cible = devient.get(identifiant)
        if cible is None:
            gardes[identifiant] = n
        elif cible not in noeuds:
            gardes[cible] = {**n, "id": cible}
    comptes = {
        "replies": sum(1 for cible in devient.values() if cible in noeuds),
        "renommes": sum(1 for cible in devient.values() if cible not in noeuds),
        "reancrees": 0,
    }

    vues, propres = set(), []
    for e in aretes:
        source = devient.get(e["source"], e["source"])
        cible = devient.get(e["target"], e["target"])
        sf = e.get("source_file")
        elu = page.get(sf)
        # Une citation tiree d'un span de code part de la page. Le depart doit etre un noeud de
        # CETTE page : une arete ecrite a l'envers, de la classe vers la page, reste en l'etat.
        if (
            e.get("context") == "code_span"
            and elu is not None
            and source != elu["id"]
            and gardes.get(source, {}).get("source_file") == sf
        ):
            source = elu["id"]
            comptes["reancrees"] += 1
        touchee = (source, cible) != (e["source"], e["target"])
        if touchee and source == cible:
            continue
        # Meme cle que le dedoublonnage de la passe A : une arete recablee peut en doubler une
        # autre, deja la ou recablee avant elle.
        cle = (source, cible, e.get("relation"), e.get("source_file"), e.get("source_location"))
        if cle in vues:
            continue
        vues.add(cle)
        propres.append({**e, "source": source, "target": cible} if touchee else e)
    membres = [
        {**h, "nodes": [devient.get(m, m) for m in h["nodes"]]}
        if isinstance(h.get("nodes"), list)
        else h
        for h in hyperaretes
    ]
    return gardes, propres, membres, comptes


def noeud_de_document(identifiant, libelle, fichier, **plus):
    """Un noeud de document fabrique pour les cas, tel que la structure l'ecrit."""
    return {
        "id": identifiant,
        "label": libelle,
        "file_type": "document",
        "source_file": fichier,
        "source_location": "L1",
        "_origin": "ast",
        **plus,
    }


def joue_sur_un_depot_fabrique(passe, noeuds, aretes, fichiers):
    """Joue une passe sur un depot FABRIQUE, et rend (code de sortie, noeuds, aretes) d'apres.

    Les passes lisent le depot relativement au dossier courant : on les joue donc telles quelles,
    dans un dossier temporaire. C'est le script qui est eprouve, pas une fonction qu'on en aurait
    extraite et qu'il pourrait ne pas appeler.

    Elles sont jouees comme `rebuild.py` les joue, par `runpy` et dans la boucle d'un appelant.
    Une passe qui sortirait par `SystemExit` arreterait la reconstruction entiere, en code 0 : le
    code rendu ici n'est donc 0 que si l'appelant a repris la main apres elle.
    """
    appelant = (
        "import runpy, sys\n"
        "runpy.run_path(sys.argv[1], run_name='__main__')\n"
        f"print({MAIN_RENDUE!r})\n"
    )
    with tempfile.TemporaryDirectory() as temporaire:
        depot = Path(temporaire)
        (depot / "graphify-out").mkdir()
        (depot / "graphify-out" / "graph.json").write_text(
            json.dumps({"nodes": noeuds, "links": aretes}, ensure_ascii=False), encoding="utf-8"
        )
        for chemin, texte in fichiers.items():
            (depot / chemin).parent.mkdir(parents=True, exist_ok=True)
            (depot / chemin).write_text(texte, encoding="utf-8")
        rendu = subprocess.run(
            [sys.executable, "-c", appelant, str(passe)],
            cwd=depot,
            capture_output=True,
            text=True,
            check=False,
        )
        ecrit = depot / "graphify-out" / ".graphify_extract.json"
        # Une passe qui plante n'ecrit rien : le cas le lit alors dans le code rendu, au lieu que
        # ce harnais leve a sa place sans dire lequel.
        extrait = (
            json.loads(ecrit.read_text(encoding="utf-8"))
            if ecrit.is_file()
            else {"nodes": [], "edges": []}
        )
    code = rendu.returncode if MAIN_RENDUE in rendu.stdout else PASSE_SORTIE_SANS_RENDRE_LA_MAIN
    return code, extrait["nodes"], extrait["edges"]


def auto_test():
    """Les cas de la passe B, joues sur un depot fabrique : graphify n'y est jamais importe."""
    verifie, echecs = cas_d_auto_test()
    passe = Path(__file__).resolve()

    classe = {
        "id": "src_importernuit_importernuit",
        "label": "ImporterNuit",
        "file_type": "code",
        "source_file": "src/main/java/ImporterNuit.java",
        "source_location": "L3",
        "_origin": "ast",
    }
    cid = classe["id"]
    page_agents = noeud_de_document("agents", "AGENTS.md", "AGENTS.md", node_kind="page")
    titre_agents = noeud_de_document(
        "agents_methode_de_travail", "Méthode de travail", "AGENTS.md", node_kind="heading"
    )
    page_readme = noeud_de_document("readme", "README.md", "README.md", node_kind="page")
    titre_readme = noeud_de_document(
        "readme_installer", "Installer", "README.md", node_kind="heading", source_location="L9"
    )
    structure = [
        classe,
        page_agents,
        titre_agents,
        noeud_de_document("claude", "CLAUDE.md", "CLAUDE.md", node_kind="page"),
        noeud_de_document("docs_guide", "guide.md", "docs/guide.md", node_kind="page"),
        noeud_de_document("docs_guide_guide", "Guide", "docs/guide.md", node_kind="heading"),
    ]
    fichiers = {
        "AGENTS.md": "# Méthode de travail\n\nLire `ImporterNuit` avant tout.\n",
        "CLAUDE.md": "# Consigne\n\nRien a citer ici.\n",
        "README.md": "# VigieChiro\n\n## Installer\n\nLancer `ImporterNuit`.\n",
        "docs/guide.md": "# Guide\n\nVoir `ImporterNuit`.\n",
    }

    def citation(source, cible, fichier, ligne, **plus):
        return {
            "relation": "references",
            "source_file": fichier,
            "source_location": f"L{ligne}",
            "_origin": "pont",
            "context": "code_span",
            "source": source,
            "target": cible,
            **plus,
        }

    def bilan(noeuds, aretes, fichiers):
        """Ce que la passe a cree ou retire, et d'ou partent les citations des ponts."""
        code, apres, liens = joue_sur_un_depot_fabrique(passe, noeuds, aretes, fichiers)
        avant, ensuite = {n["id"] for n in noeuds}, {n["id"] for n in apres}
        return (
            code,
            sorted(ensuite - avant),
            sorted(avant - ensuite),
            sorted((e["source"], e["target"]) for e in liens if e.get("_origin") == "pont"),
        )

    # Le defaut de #5868. La passe parcourait les pages de la racine sans reconnaitre leur noeud de
    # page : elle en fabriquait un second, `agents_doc`, que le moteur prenait pour le jumeau du
    # document. `README.md` attend ici la meme chose, et `docs/guide.md` est le temoin : son noeud
    # de page etait deja reconnu.
    verifie(
        "une page de la racine qui cite une classe garde son noeud de page, et la citation en part",
        lambda: bilan(structure + [page_readme, titre_readme], [], fichiers),
        (0, [], [], [("agents", cid), ("docs_guide", cid), ("readme", cid)]),
    )

    # Le parcours se derive des noeuds de page du graphe, et non d une liste de dossiers (#5904).
    # Les ponts ne reliaient que `brief/`, `dev-docs/`, `docs/` et la racine : 173 pages qui ont un
    # noeud de page vivaient ailleurs le 5 octobre 2026, et 445 citations de classe n etaient pas
    # reliees. Deux exclusions restent, nommees : le journal des versions, et `.claude/skills`, qui
    # est une copie de `.agents/skills`.
    def page(identifiant, chemin):
        return noeud_de_document(identifiant, Path(chemin).name, chemin, node_kind="page")

    hors_des_quatre = {
        "openspec/specs/lot/spec.md": "openspec_specs_lot_spec",
        ".agents/skills/clore/SKILL.md": "agents_skills_clore_skill",
        ".claude/skills/clore/SKILL.md": "claude_skills_clore_skill",
        ".claude/commands/clore.md": "claude_commands_clore",
        "CHANGELOG.md": "changelog",
    }
    partout = {chemin: "# Titre\n\nVoir `ImporterNuit`.\n" for chemin in hors_des_quatre}
    partout["docs/sans-noeud.md"] = "# Titre\n\nVoir `ImporterNuit`.\n"
    verifie(
        "une page qui a un noeud de page est reliee ou qu elle vive, hors journal et copie",
        lambda: bilan(
            [
                classe,
                *(page(i, chemin) for chemin, i in hors_des_quatre.items()),
                # Le graphe porte encore cette page, le disque ne l'a plus : elle ne se lit pas.
                page("docs_partie", "docs/partie.md"),
            ],
            [],
            partout,
        ),
        (
            0,
            [],
            [],
            [
                ("agents_skills_clore_skill", cid),
                ("claude_commands_clore", cid),
                ("openspec_specs_lot_spec", cid),
            ],
        ),
    )

    # Une page que la structure ne porte pas attend son noeud de page. La passe ne lui en fabrique
    # plus : un noeud de document qui n'est pas de structure ferait passer la page pour couverte,
    # et le moteur ne lirait plus jamais sa structure.
    verifie(
        "une page sans noeud de page ne recoit ni noeud ni citation",
        lambda: bilan([classe, titre_agents], [], fichiers),
        (0, [], [], []),
    )

    # Ce que l'ancienne passe a laisse dans les graphes. `readme_doc` a survecu a la place de la
    # page ; pour `AGENTS.md` le moteur a replie le doublon sur le premier titre, d'ou partent
    # depuis ses citations. Tant que la page n'a pas retrouve son noeud, le doublon quitte
    # seulement le suffixe reserve : sans cela le moteur reprendrait le noeud de page a son retour.
    doublon = {**noeud_de_document("readme_doc", "README", "README.md"), "_origin": "pont"}
    abime = [
        classe,
        titre_agents,
        titre_readme,
        doublon,
        noeud_de_document("docs_guide", "guide.md", "docs/guide.md", node_kind="page"),
    ]
    aretes_abimees = [
        citation("readme_doc", cid, "README.md", 5),
        citation("agents_methode_de_travail", cid, "AGENTS.md", 3),
        {
            "relation": "contains",
            "source_file": "README.md",
            "source_location": "L9",
            "_origin": "ast",
            "source": "readme_doc",
            "target": "readme_installer",
        },
        # Deux temoins, qui ne doivent pas bouger. Un lien de structure parti du premier titre
        # n'est pas une citation de span de code : il reste sur le titre quand la page revient.
        {
            "relation": "references",
            "source_file": "AGENTS.md",
            "source_location": "L7",
            "_origin": "ast",
            "source": "agents_methode_de_travail",
            "target": "docs_guide",
        },
        # Et une citation ecrite a l'envers, de la classe vers la page : son depart n'est pas un
        # noeud de la page, donc elle n'est pas reancree, ni redoublee par la passe.
        citation(cid, "docs_guide", "docs/guide.md", 3),
    ]
    verifie(
        "sans noeud de page, le doublon laisse par l ancienne passe quitte le suffixe reserve",
        lambda: bilan(abime, aretes_abimees, fichiers),
        (
            0,
            ["readme_pont"],
            ["readme_doc"],
            [("agents_methode_de_travail", cid), ("readme_pont", cid), (cid, "docs_guide")],
        ),
    )

    # La page revenue, le doublon se replie sur elle avec toutes ses aretes, et la citation partie
    # du premier titre repart de la page. Deux aretes de plus pour ce que le repli peut casser :
    # une citation deja partie de la page, que le reancrage doublerait, et un lien du doublon vers
    # la page, que le repli refermerait sur elle-meme.
    revenu = [*abime, page_agents, page_readme]
    aretes_revenues = [
        *aretes_abimees,
        citation("agents", cid, "AGENTS.md", 3),
        {
            "relation": "references",
            "source_file": "README.md",
            "source_location": "L1",
            "_origin": "ast",
            "source": "readme_doc",
            "target": "readme",
        },
    ]
    reparees = [("agents", cid), ("readme", cid), (cid, "docs_guide")]
    verifie(
        "le noeud de page revenu, le doublon s y replie et les citations repartent de la page",
        lambda: bilan(revenu, aretes_revenues, fichiers),
        (0, [], ["readme_doc"], reparees),
    )

    def aretes_de(noeuds, aretes):
        _, _, liens = joue_sur_un_depot_fabrique(passe, noeuds, aretes, fichiers)
        return sorted((e["source"], e["target"], e["relation"], e["_origin"]) for e in liens)

    verifie(
        "les aretes du doublon suivent le repli sans se dedoubler ni boucler, et les temoins restent",
        lambda: aretes_de(revenu, aretes_revenues),
        [
            ("agents", cid, "references", "pont"),
            ("agents_methode_de_travail", "docs_guide", "references", "ast"),
            ("readme", "readme_installer", "contains", "ast"),
            ("readme", cid, "references", "pont"),
            (cid, "docs_guide", "references", "pont"),
        ],
    )

    def resorbee():
        hyperaretes = [{"id": "h", "nodes": ["readme_doc", cid]}, {"id": "sans_membres"}]
        _, _, membres, comptes = resorbe({n["id"]: n for n in revenu}, aretes_revenues, hyperaretes)
        return comptes, membres

    verifie(
        "la resorption compte ce qu elle fait, et les membres d une hyperarete suivent le repli",
        resorbee,
        (
            {"replies": 1, "renommes": 0, "reancrees": 1},
            [{"id": "h", "nodes": ["readme", cid]}, {"id": "sans_membres"}],
        ),
    )

    # Les deux temps de la reparation d'un graphe, enchaines : la passe d'abord, puis la structure
    # qui revient. Le doublon sorti du suffixe reserve se replie a son tour.
    def en_deux_temps():
        _, noeuds, aretes = joue_sur_un_depot_fabrique(passe, abime, aretes_abimees, fichiers)
        return bilan([*noeuds, page_agents, page_readme], aretes, fichiers)

    verifie(
        "le doublon sorti du suffixe reserve se replie a son tour quand la page revient",
        en_deux_temps,
        (0, [], ["readme_pont"], reparees),
    )

    # Rejouer la passe sur ce qu'elle vient d'ecrire ne change rien : les ponts se rejouent a
    # chaque reconstruction.
    def deux_fois(noeuds, aretes):
        _, n1, a1 = joue_sur_un_depot_fabrique(passe, noeuds, aretes, fichiers)
        _, n2, a2 = joue_sur_un_depot_fabrique(passe, n1, a1, fichiers)
        return n1 == n2 and a1 == a2

    verifie(
        "la passe rejouee sur sa propre sortie ne change rien",
        lambda: [deux_fois(abime, aretes_abimees), deux_fois(revenu, aretes_revenues)],
        [True, True],
    )

    # Le choix du noeud de page, sur des noeuds seuls. C'est sa nature qui le designe, pas la
    # longueur de son identifiant, qui tenait ce role quand la structure ne nommait pas ses pages.
    longue = noeud_de_document("docs_une_page_au_long_nom", "p.md", "docs/p.md", node_kind="page")
    court = noeud_de_document("docs_p", "Un titre", "docs/p.md", node_kind="heading")
    verifie(
        "le noeud de page est choisi par sa nature, meme quand un titre a l identifiant plus court",
        lambda: [
            noeuds_de_page(ordre)["docs/p.md"]["id"] for ordre in ([longue, court], [court, longue])
        ],
        ["docs_une_page_au_long_nom", "docs_une_page_au_long_nom"],
    )
    verifie(
        "ni un titre ni un noeud de code ne tiennent lieu de page",
        lambda: noeuds_de_page(
            [court, {**classe, "source_file": "docs/p.md", "node_kind": "page"}]
        ),
        {},
    )

    # Ce que la resorption ne touche pas : un noeud de page, meme d'origine pont, et le noeud de
    # document qu'une autre passe fabrique pour un fichier qui n'est pas une page.
    verifie(
        "un doublon se reconnait a son origine et a sa place, et un noeud de page n en est jamais un",
        lambda: [
            est_fabrique(doublon),
            est_fabrique({**doublon, "node_kind": "page"}),
            est_fabrique({**doublon, "source_location": "L9"}),
            est_fabrique({**doublon, "_origin": "ast"}),
            est_fabrique({**doublon, "file_type": "code"}),
        ],
        [True, False, False, False, False],
    )
    schema = {**doublon, "id": "docs_schema_mcd", "source_file": "docs/schema.mcd"}
    verifie(
        "le noeud de document d un fichier qui n est pas une page reste en place",
        lambda: [
            sorted(resorbe({"docs_schema_mcd": schema}, [], [])[0]),
            resorbe({"docs_schema_mcd": schema}, [], [])[3],
        ],
        [["docs_schema_mcd"], {"replies": 0, "renommes": 0, "reancrees": 0}],
    )

    # L'identifiant d'un doublon renomme : libre, et jamais sous le suffixe que le moteur reserve.
    aucun_pris = set()
    verifie(
        "un doublon renomme prend le suffixe du pont, prolonge tant que l identifiant est pris",
        lambda: [
            identifiant_libre("readme", aucun_pris),
            identifiant_libre("readme", {"readme_pont"}),
        ],
        ["readme_pont", "readme_pont_pont"],
    )

    return echecs()


def passe():
    # Chaine sur .graphify_extract.json quand il existe (sortie d'un --update encore
    # non clusterise), sinon repart de graph.json.
    graphe = Path("graphify-out/.graphify_extract.json")
    if not graphe.exists():
        graphe = Path("graphify-out/graph.json")
    g = json.loads(graphe.read_text(encoding="utf-8"))
    nodes = {n["id"]: n for n in g["nodes"]}
    edges = g["edges"] if "edges" in g else g["links"]
    print(f"depart : {graphe.name} - {len(nodes)} noeuds, {len(edges)} aretes")

    # ------------------------------------------------------------ index classes
    classes = {}
    for n in g["nodes"]:
        sf = n.get("source_file") or ""
        if sf.endswith(".java") and n.get("_origin") == "ast":
            stem = Path(sf).stem
            if n["label"] in (stem, stem + ".java"):
                classes.setdefault(stem.lower(), (stem, n["id"]))
    print(f"index : {len(classes)} classes Java")

    # --------------------------------------------------- PASSE A : dedoublonnage
    remap, non_resolus = {}, []
    for n in g["nodes"]:
        if est_doc(n) and n.get("file_type") == "code":
            tok = re.split(r"[^A-Za-z0-9_]", n["label"].strip())[0].lower()
            hit = classes.get(tok) or classes.get(n["id"].split("_")[-1])
            if hit and hit[1] != n["id"]:
                remap[n["id"]] = hit[1]
            else:
                non_resolus.append(n["id"])
    print(f"passe A : {len(remap)} fantomes resolus, {len(non_resolus)} laisses en place")

    # Le rationale porte par le fantome decrit la classe : on le transfere si la
    # classe n'en a pas deja un, sinon on le perd au moment de la suppression.
    transferes = 0
    for gid, cid in remap.items():
        r = nodes[gid].get("rationale")
        if r and not nodes[cid].get("rationale"):
            nodes[cid]["rationale"] = r
            nodes[cid]["rationale_source"] = nodes[gid].get("source_file")
            transferes += 1
    print(f"         {transferes} rationale transferes sur la classe")

    recablees, boucles = 0, 0
    nouvelles = []
    for e in edges:
        s, t = remap.get(e["source"], e["source"]), remap.get(e["target"], e["target"])
        if (s, t) != (e["source"], e["target"]):
            recablees += 1
            if s == t:
                boucles += 1
                continue
            e = {**e, "source": s, "target": t}
        nouvelles.append(e)
    edges = nouvelles
    for gid in remap:
        nodes.pop(gid, None)
    print(f"         {recablees} aretes recablees, {boucles} boucles supprimees")

    # Dedoublonnage exact des aretes nees du recablage
    vues, propres = set(), []
    for e in edges:
        k = (
            e["source"],
            e["target"],
            e.get("relation"),
            e.get("source_file"),
            e.get("source_location"),
        )
        if k in vues:
            continue
        vues.add(k)
        propres.append(e)
    print(f"         {len(edges) - len(propres)} aretes doublons supprimees")
    edges = propres

    # ------------------------------------------------------- PASSE B : citations
    nodes, edges, hyperaretes, comptes = resorbe(nodes, edges, g.get("hyperedges", []))
    print(
        f"passe B : {comptes['replies']} doublons replies sur leur page, "
        f"{comptes['renommes']} sortis du suffixe reserve, "
        f"{comptes['reancrees']} citations reancrees"
    )
    page_de = noeuds_de_page(nodes.values())
    docs = pages_reliees(nodes.values())

    existantes = {(e["source"], e["target"]) for e in edges}
    ajoutees = 0
    for p in docs:
        sf = str(p)
        texte = p.read_text(encoding="utf-8", errors="ignore")
        lignes = texte.splitlines()
        # spans de code : inline `...` et blocs ``` ... ```
        spans = []  # (contenu, no_ligne)
        dans_bloc = False
        for i, ln in enumerate(lignes, 1):
            if ln.lstrip().startswith("```"):
                dans_bloc = not dans_bloc
                continue
            if dans_bloc:
                spans.append((ln, i))
            else:
                for m in re.finditer(r"`([^`\n]{1,120})`", ln):
                    spans.append((m.group(1), i))
        if not spans:
            continue
        trouves = {}
        for contenu, no in spans:
            for mot in re.findall(r"\b[A-Za-z][A-Za-z0-9_]{4,}\b", contenu):
                hit = classes.get(mot.lower())
                if hit and hit[0] == mot:  # casse exacte du nom de classe
                    trouves.setdefault(hit[1], no)
        if not trouves:
            continue
        src = page_de[sf]
        for cid, no in trouves.items():
            if (src["id"], cid) in existantes or (cid, src["id"]) in existantes or src["id"] == cid:
                continue
            edges.append(
                {
                    "relation": "references",
                    "confidence": "EXTRACTED",
                    "confidence_score": 1.0,
                    "source_file": sf,
                    "source_location": f"L{no}",
                    "weight": 1.0,
                    "_origin": "pont",
                    "context": "code_span",
                    "source": src["id"],
                    "target": cid,
                }
            )
            existantes.add((src["id"], cid))
            ajoutees += 1
    print(f"         {ajoutees} aretes de citation ajoutees, depuis {len(docs)} pages reliees")

    # --------------------------------------------------------------- ecriture
    extraction = {
        "nodes": list(nodes.values()),
        "edges": edges,
        "hyperedges": hyperaretes,
        "input_tokens": 0,
        "output_tokens": 0,
    }
    Path("graphify-out/.graphify_extract.json").write_text(
        json.dumps(extraction, ensure_ascii=False), encoding="utf-8"
    )
    print(f"\nresultat : {len(nodes)} noeuds, {len(edges)} aretes")


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        raise SystemExit(auto_test())
    # Pas de `SystemExit` ici : `rebuild.py` joue les passes par `runpy`, dans sa propre boucle,
    # et une sortie arreterait la reconstruction apres celle-ci, en code 0.
    passe()

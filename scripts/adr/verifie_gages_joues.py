#!/usr/bin/env python3
"""Un gage de CODE qu aucun harnais n appelle est inerte, et ce garde le refuse (#5594).

    python3 scripts/adr/verifie_gages_joues.py
    python3 scripts/adr/verifie_gages_joues.py --auto-test

## Les deux especes de l ADR 5483, et celle que rien ne gardait

L ADR 5483 a tranche qu un gage inerte est un defaut, pas un quatrieme niveau de verification. Elle
est mecanisee pour son espece d EN-TETE : `verifie_okf.py` refuse un `enforced_by:` que la demande ne
joue pas, et #5554 l a etendue au niveau `probable`.

La seconde espece n avait AUCUN garde : une fonction qui juge et qu aucun harnais n appelle. Elle ne
se voit pas, parce qu elle est ecrite, annotee, et parfaitement lisible - il ne lui manque qu un
appelant.

## Le taux qui justifie ce garde, mesure au COMMIT et non au jour

Les six gages de `scripts/_commun/` et la date ou un appelant les a joues :

    arbre.py            ne en a4dc17863   joue en a4dc17863   le meme commit
    index.py            ne en f27523e80   joue en a9d505ef9   INERTE 22 jours
    implementations.py  ne en d33b1194f   joue en a9d505ef9   INERTE, meme jour
    champs.py           ne en a9d505ef9   joue en a9d505ef9   le meme commit
    forge.py            ne en 05f6cd0c7   joue en 05f6cd0c7   le meme commit
    epics.py            ne en b46653e7d   joue en b46653e7d   le meme commit

**Deux sur six sont nes inertes.** Et la granularite compte : mesure au JOUR, `implementations.py`
paraissait joue des sa naissance, parce qu il est ne et a ete repare le meme jour par deux demandes
differentes. C est au commit que la question se pose, puisque c est au commit qu une demande entre.

Le sixieme est arrive le 2026-09-30 par #5652, ne et joue dans le MEME commit. Il fait passer le
taux de deux sur cinq a deux sur six, et ce garde n a rien dit du changement : aucun dispositif ne
compte cette prose, et trois enonces sont restes a cinq pendant que le verdict affichait `lus=6`.

## Comment ce garde resout un appel, et pourquoi le nom ne suffit pas

`verifie_grammaire` est declare SIX fois. Un garde qui chercherait « ce nom apparait ailleurs »
trouverait un appelant pour les six des qu un seul est appele, et manquerait exactement les cas qu il
existe pour attraper. La question n est pas « ce nom est-il cite » mais « quelle definition cet appel
atteint-il ».

Trois formes d import suffisent a resoudre les sept appels du depot :

    from _commun.arbre import verifie_grammaire   puis   verifie_grammaire()
    from _commun import champs as lecteur         puis   lecteur.verifie_grammaire()
    from _commun import forge                     puis   forge.verifie_grammaire()

## Le nom d un module se derive de sa RACINE D IMPORT, jamais du seul chemin

Ce garde declare balayer `.github/scripts/**` et ne savait pas lire ce repertoire. `_module_de` ne
connaissait qu une racine, `scripts/`, si bien qu un fichier de `.github/scripts/` recevait pour nom
de module le CHEMIN ABSOLU de l arbre de travail. Un tel nom n est egal a aucun import : un gage
declare la se serait dit inerte tout en etant joue, et le refus aurait porte le nom du repertoire de
travail du poste.

Le depot a DEUX racines d import, et c est ce que la reparation derive au lieu de le coder en dur :
`scripts/`, que les gardes d ADR ajoutent a `sys.path`, et `.github/scripts/`, que ses propres
fichiers s ajoutent pour s importer a plat. Un meme fichier peut viser les deux -
`temoins_de_ci_non_decoratifs.py` importe `_commun` de la premiere et `_forge` de la seconde.

**Le defaut etait LATENT.** Aucun gage ne vit aujourd hui dans `.github/scripts/`, donc ce garde est
vert des DEUX cotes de la correction et aucun temoin historique ne l etablit. Il a ete trouve en
LISANT ce garde, pas en le lancant (#5657).

**Et la moitie de la reparation annoncee n etait pas due.** Le meme diagnostic supposait qu un import
A PLAT - `from _forge import candidats_clos` - ne resolvait vers rien. La mesure les rend deja : un
module sans point est un module comme un autre pour la branche `ImportFrom`. Seul le cote DECLARANT
etait casse, et la moitie supposee aurait ete du code ecrit contre un defaut absent.

## La population se derive de ce que la fonction DECLARE

Un gage de grammaire rend une liste de couples (libelle, verdict). C est un CONTRAT, visible dans
l annotation de retour, et non un idiome d ecriture - l ADR 5452 a tranche qu une exemption se derive
de ce que la chose fait, jamais de la facon dont elle est ecrite. Mesure du 2026-09-30 : six
fonctions du depot portent cette annotation, et ce sont exactement les six gages.

## Ce que ce garde ne fait pas

**Il ne juge pas ce que le gage verifie.** Un gage appele mais vide reste vide, et c est le banc des
temoins qui le dit. Celui-ci ne repond qu a « quelqu un le joue-t-il ».

**Il ne suit pas un appel dynamique.** Un `getattr(module, "verifie_grammaire")()` lui echapperait.
Aucun n existe dans le depot, et l asymetrie va du bon cote : il refuserait un gage joue de cette
facon, donc un faux refus qui fait regarder, jamais un faux vert.
"""

from __future__ import annotations

import ast
import pathlib
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))

from _commun import cas_d_auto_test, refuse, sort_si_contrat_demande

# ⟨le contrat d un gage de grammaire⟩ Une liste de couples (libelle, verdict). C est ce que la
# fonction DECLARE, et non son nom : six fonctions le portent, et ce sont les six gages.
ANNOTATION = "list[tuple[str, bool]]"

# ⟨les racines d import, DERIVEES du depot et non supposees⟩ Un module se nomme par la racine depuis
# laquelle un import l atteint, et ce depot en a DEUX : `scripts/`, que les gardes d ADR ajoutent a
# `sys.path`, et `.github/scripts/`, que ses propres fichiers s ajoutent pour s importer a plat.
# Mesure du 2026-09-30 : 105 fichiers d un cote, 45 de l autre, et AUCUN nom de module ne collisionne
# entre les deux - sans quoi ce garde devrait les distinguer autrement que par le premier couvrant.
RACINES_D_IMPORT = ("scripts", ".github/scripts")


def _fichiers(racine: pathlib.Path) -> list[pathlib.Path]:
    """Les `.py` que git suit, car un fichier non indexe est invisible des cliquets."""
    rendu = subprocess.run(
        ["git", "-C", str(racine), "ls-files", "*.py"],
        capture_output=True,
        text=True,
        check=False,
    )
    return [racine / ligne for ligne in rendu.stdout.split() if ligne]


def _module_de(chemin: pathlib.Path, racine: pathlib.Path) -> str:
    """`scripts/_commun/arbre.py` devient `_commun.arbre`, `.github/scripts/_forge.py` devient
    `_forge` : le nom est celui par lequel un IMPORT atteint le module, donc il se derive de la
    racine d import qui le couvre, et jamais du seul chemin.
    """
    for depuis in RACINES_D_IMPORT:
        base = racine / depuis
        if base in chemin.parents:
            return ".".join(chemin.relative_to(base).with_suffix("").parts)
    # ⟨hors de toute racine d import⟩ `icone/`, `.github/assets/`, `src/test/bats/` : aucun import ne
    # les atteint, mais leur nom se derive quand meme de la RACINE DU DEPOT. Le repli rendait le
    # chemin ABSOLU, donc un nom qui portait le repertoire de travail du poste (#5657).
    return ".".join(chemin.relative_to(racine).with_suffix("").parts)


def gages(racine: pathlib.Path) -> list[tuple[str, str, int]]:
    """Les fonctions qui DECLARENT le contrat d un gage : (module, nom, ligne)."""
    trouves = []
    for fichier in _fichiers(racine):
        try:
            arbre = ast.parse(fichier.read_text(encoding="utf-8"))
        except (SyntaxError, UnicodeDecodeError):
            continue
        for noeud in ast.walk(arbre):
            if not isinstance(noeud, (ast.FunctionDef, ast.AsyncFunctionDef)):
                continue
            if noeud.returns is not None and ast.unparse(noeud.returns) == ANNOTATION:
                trouves.append((_module_de(fichier, racine), noeud.name, noeud.lineno))
    return sorted(trouves)


def appels_resolus(source: str, module_du_fichier: str) -> set[tuple[str, str]]:
    """Les couples (module vise, nom) que ce fichier appelle, resolus par ses IMPORTS.

    Trois formes suffisent, et elles couvrent les sept appels du depot. Ce qui compte est qu un nom
    seul ne resout rien : c est l import qui dit QUELLE definition l appel atteint.
    """
    try:
        arbre = ast.parse(source)
    except SyntaxError:
        return set()

    directs: dict[str, str] = {}  # nom appele seul  -> module qui le fournit
    alias: dict[str, str] = {}  # prefixe d attribut -> module qu il designe
    for noeud in ast.walk(arbre):
        if isinstance(noeud, ast.ImportFrom) and noeud.module:
            for cible in noeud.names:
                # `from _commun import champs as lecteur` : le nom importe EST un module.
                alias[cible.asname or cible.name] = f"{noeud.module}.{cible.name}"
                # `from _commun.arbre import verifie_grammaire` : le nom importe est la fonction.
                directs[cible.asname or cible.name] = noeud.module
        elif isinstance(noeud, ast.Import):
            for cible in noeud.names:
                alias[cible.asname or cible.name.split(".")[0]] = cible.name

    vus: set[tuple[str, str]] = set()
    for noeud in ast.walk(arbre):
        if not isinstance(noeud, ast.Call):
            continue
        cible = noeud.func
        if isinstance(cible, ast.Name) and cible.id in directs:
            vus.add((directs[cible.id], cible.id))
        elif isinstance(cible, ast.Attribute) and isinstance(cible.value, ast.Name):
            prefixe = cible.value.id
            if prefixe in alias:
                vus.add((alias[prefixe], cible.attr))
        # ⟨un appel depuis le module lui-meme ne compte pas⟩ Un gage qui ne se joue que chez lui
        # reste inerte : c est un harnais EXTERIEUR qu on cherche.
    return {(m, n) for m, n in vus if m != module_du_fichier}


def inertes(racine: pathlib.Path | None = None) -> list[str]:
    """Les gages que rien ne joue, nommes par leur module et leur ligne."""
    racine = RACINE if racine is None else racine
    joues: set[tuple[str, str]] = set()
    for fichier in _fichiers(racine):
        try:
            source = fichier.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        joues |= appels_resolus(source, _module_de(fichier, racine))
    return [
        f"{module.replace('.', '/')}.py:{ligne} {nom}()"
        for module, nom, ligne in gages(racine)
        if (module, nom) not in joues
    ]


def juge(racine: pathlib.Path | None = None) -> int:
    racine = RACINE if racine is None else racine
    tous = gages(racine)
    muets = inertes(racine)
    print(f"GAGES DE CODE | lus={len(tous)} | inertes={len(muets)}")
    for gage in tous:
        module, nom, ligne = gage
        libelle = f"{module.replace('.', '/')}.py:{ligne} {nom}()"
        print(f"  {'✘' if libelle in muets else '✔'} {libelle}")
    if muets:
        refuse(
            f"{len(muets)} gage(s) de code qu aucun harnais n appelle : " + ", ".join(muets),
            "faites-les jouer par un `--auto-test` qui les IMPORTE du module qui les declare, "
            "comme `scripts/qualite/lecteurs.py` le fait pour `_commun/champs.py`. Un gage que "
            "personne ne joue ne peut faire rougir aucune demande (ADR 5483).",
        )
    return 0


def _auto_test() -> int:
    verifie, echecs = cas_d_auto_test()

    UN_GAGE = "def verifie_grammaire() -> list[tuple[str, bool]]:\n    return [('un cas', True)]\n"

    verifie(
        "une fonction qui declare le contrat est un gage",
        lambda: [n for _, n, _ in gages_de_source(UN_GAGE)],
        ["verifie_grammaire"],
    )
    verifie(
        "une fonction qui rend autre chose n en est pas un",
        lambda: gages_de_source("def compte() -> int:\n    return 0\n"),
        [],
    )
    verifie(
        "un import direct resout vers le module qui declare",
        lambda: appels_resolus(
            "from _commun.arbre import verifie_grammaire\nverifie_grammaire()\n", "appelant"
        ),
        {("_commun.arbre", "verifie_grammaire")},
    )
    verifie(
        "un alias de module resout vers le module aliase",
        lambda: appels_resolus(
            "from _commun import champs as lecteur\nlecteur.verifie_grammaire()\n", "appelant"
        ),
        {("_commun.champs", "verifie_grammaire")},
    )
    verifie(
        "le NOM seul ne resout rien, sans import qui le fournisse",
        lambda: appels_resolus("verifie_grammaire()\n", "appelant"),
        set(),
    )
    verifie(
        "un gage joue CHEZ LUI reste inerte : on cherche un harnais exterieur",
        lambda: appels_resolus(
            "from _commun import arbre\narbre.verifie_grammaire()\n", "_commun.arbre"
        ),
        set(),
    )
    # ⟨le nom d un module, aux TROIS emplacements possibles⟩ Ce sont ces trois-la qui rougissent si
    # `_module_de` oublie une racine, et le troisieme est celui qui portait le repertoire du poste.
    verifie(
        "un module de scripts/ se nomme depuis cette racine",
        lambda: _module_de(pathlib.Path("/bac/scripts/_commun/arbre.py"), pathlib.Path("/bac")),
        "_commun.arbre",
    )
    verifie(
        "un module de .github/scripts/ aussi, car ses fichiers s importent a plat",
        lambda: _module_de(pathlib.Path("/bac/.github/scripts/_forge.py"), pathlib.Path("/bac")),
        "_forge",
    )
    verifie(
        "hors racine d import, le nom vient du DEPOT et non du repertoire de travail",
        lambda: _module_de(pathlib.Path("/bac/icone/genere_icones.py"), pathlib.Path("/bac")),
        "icone.genere_icones",
    )
    verifie(
        "un import A PLAT resout deja, et c est ce qui a rendu la moitie du remede inutile",
        lambda: appels_resolus("from _forge import verifie_grammaire\nverifie_grammaire()\n", "x"),
        {("_forge", "verifie_grammaire")},
    )

    # ⟨la paire qui FRANCHIT la frontiere de repertoire⟩ Les six cas ci-dessus lisent une source en
    # memoire ou un chemin nu ; aucun ne traverse `gages()` et `inertes()` sur un arbre reel, qui
    # sont les deux fonctions ou le nom du module doit se RENCONTRER. Et la paire compte : corrigee
    # a moitie, la reparation rendrait le garde aveugle plutot que voyant, et l aveugle est vert.
    with tempfile.TemporaryDirectory(prefix="vc-5657-") as bac:
        faux = pathlib.Path(bac) / "depot"
        (faux / ".github" / "scripts").mkdir(parents=True)
        subprocess.run(["git", "-C", str(faux), "init", "-q"], check=True)

        (faux / ".github" / "scripts" / "_forge.py").write_text(UN_GAGE, encoding="utf-8")
        subprocess.run(["git", "-C", str(faux), "add", "-A"], check=True)
        verifie(
            "un gage de .github/scripts/ que rien ne joue est dit inerte, et se NOMME",
            lambda: inertes(faux),
            ["_forge.py:1 verifie_grammaire()"],
        )

        (faux / ".github" / "scripts" / "harnais.py").write_text(
            "from _forge import verifie_grammaire\n\nverifie_grammaire()\n", encoding="utf-8"
        )
        subprocess.run(["git", "-C", str(faux), "add", "-A"], check=True)
        verifie(
            "le MEME gage, joue depuis son repertoire, est vu joue",
            lambda: inertes(faux),
            [],
        )

    return echecs()


def gages_de_source(source: str) -> list[tuple[str, str, int]]:
    """La derivation de `gages()`, sur une source en memoire, pour les cas."""
    arbre = ast.parse(source)
    return [
        ("memoire", n.name, n.lineno)
        for n in ast.walk(arbre)
        if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef))
        and n.returns is not None
        and ast.unparse(n.returns) == ANNOTATION
    ]


CONTRAT = {
    "geste": "gage de code qu aucun harnais n appelle",
    "population": "les fonctions annotees list[tuple[str, bool]] que git suit",
    "dispositif": "invariant",
    "seuil": "(aucune marge : tout gage inerte refuse)",
    "temoin": "scripts/adr/verifie_gages_joues.py --auto-test",
    "decision": "ADR 5483",
    # ⟨tout Python, car un gage peut naitre n importe ou et son appelant vivre ailleurs⟩ Les deux
    # doivent etre dans la portee : un diff qui ajoute un gage sans son appelant ne touche qu un
    # fichier, et c est exactement le cas a attraper.
    "chemins": """
scripts/**
.github/scripts/**
dev-docs/decisions/5483-un-gage-inerte-est-un-defaut-pas-un-quatrieme-niveau.md
""",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    sys.exit(_auto_test() if "--auto-test" in sys.argv else juge())

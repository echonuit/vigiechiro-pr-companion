#!/usr/bin/env python3
"""Pose ce qu un worktree neuf n a pas, pour qu un verdict local porte sur le CODE (issue #5406).

Un worktree neuf n a ni `.github/openspec/node_modules` ni `ruff`. Les gardes qui en dependent
refusent alors de conclure, et ce refus **ressemble a un defaut du changement en cours**. Le depot a
deja constate ce mecanisme pour les MODULES Python, en tete de `pyproject.toml` : « en local, six des
neuf plantaient nu sur `ModuleNotFoundError`, erreur qui ressemble a un defaut du changement en
cours ». Celui-ci le corrige pour les OUTILS.

**Ce qu il pose, et ce qu il ne pose pas.** Uniquement ce qui est bon marche - deux secondes pour
l un, quatre pour l autre, mesure le 2026-09-06. `target/pmd.xml` demande une a deux minutes et
depend de ce que le diff touche : il appartient a la porte, qui sait le derive (#5405).

**Il ne bloque JAMAIS.** Une preparation qui echoue le dit en une ligne et rend la main. Le crochet
qui l appelle a deja ce dessin pour son `clean`, et sa raison vaut ici : personne ne regarde un
crochet, et une commande qui empeche de creer un worktree coute plus qu elle ne rend.

**Il ne ment jamais non plus.** Une preparation muette qui echoue rendrait la porte MOINS sure
qu avant : le lecteur croirait l environnement complet. Chaque echec nomme la commande.

**Il n importe que la STDLIB, et c est necessaire.** `post-commit` et `post-merge` choisissent leur
interpreteur - « uv tool, pipx ou systeme » - parce qu ils lancent un script qui importe `graphify`.
Celui-ci n a pas ce besoin et ne doit pas l avoir : il POSE ce dont les autres dependent, donc il ne
peut dependre de rien. Un lecteur qui l alignerait sur ses voisins par souci de coherence le rendrait
incapable de tourner sur le poste ou il sert le plus - celui ou rien n est encore installe.

**Pourquoi en Python et non dans le crochet.** Ce dispositif n a **aucun gardien en CI** - le runner
installe tout lui-meme et ne joue jamais ce chemin. Son auto-test porte donc seul, et trente lignes
de bash se relisent la ou un script Python s eprouve.

Usage :
    python3 scripts/methode/prepare-l-environnement.py
    python3 scripts/methode/prepare-l-environnement.py --auto-test
"""

import pathlib
import re
import subprocess
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import sort_si_contrat_demande


# `.venv` DANS le worktree, et non un venv partage hors du depot.
#
# Deux prescriptions concurrentes existaient, et ce lot tranche entre elles (#5426) :
# `CONTRIBUTING.md` decrit `python3 -m venv .venv` puis `pip install --group gardes` - le groupe
# ENTIER - tandis que `ouvrir-une-pr` decrivait `~/.venv-outils` avec `ruff` SEUL. La premiere gagne,
# pour trois raisons mesurees.
#
# **Elle porte ce qu un garde importe**, pas seulement des executables. C est la difference qui rendait
# le trou invisible : `ruff` s appelle par son chemin, une grammaire s importe.
#
# **Un venv PAR worktree** est la meme decision que pour `node_modules` : une branche qui change une
# version epinglee serait eprouvee contre celle d une autre. #4849 avait ecarte le partage pour cette
# raison exacte, et l appliquer aux outils Python et pas aux modules aurait ete incoherent.
#
# **Rien a exclure** : depuis Python 3.11, `venv` ecrit lui-meme son `.venv/.gitignore`, ce que
# `CONTRIBUTING.md` dit deja.
def venv_de(racine: pathlib.Path) -> pathlib.Path:
    """Le venv de CE worktree."""
    return racine / ".venv"


CONTRAT = {
    "geste": "prerequis absent d un worktree neuf",
    "population": "les outils declares que le depot peut poser",
    # `generateur`, et non un mot neuf : le vocabulaire des dispositifs est ferme et declare dans
    # `verifie_contrats_tiennent.py` « et nulle part ailleurs » (ADR 5125). Des sept, c est celui qui
    # dit « il ne juge pas, il ECRIT » - et poser un `node_modules` ou un venv, c est ecrire.
    # L elargir pour un seul cas serait une decision, pas une commodite.
    "dispositif": "generateur",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/prepare-l-environnement.py --auto-test",
    "decision": "hygiene, sans decision",
    "chemins": """
.githooks/**
scripts/methode/prepare-l-environnement.py
""",
}


def openspec_pose(racine: pathlib.Path) -> bool:
    """L outil OpenSpec est-il installe dans CE worktree ?"""
    return (racine / ".github" / "openspec" / "node_modules").is_dir()


def ruff_pose(racine: pathlib.Path) -> bool:
    """Le venv de ce worktree porte-t-il `ruff` ?"""
    return (venv_de(racine) / "bin" / "ruff").exists()


def venv_porte_le_groupe(racine: pathlib.Path, lance=subprocess.run) -> bool:
    """Le venv porte-t-il ce qu un garde IMPORTE, et pas seulement ses executables ?

    `ruff` present ne prouve rien : c est un executable, et le venv peut le porter sans porter un
    seul module. Ce critere-la etait trop grossier - il rendait « rien a faire » sur un venv qui ne
    pouvait lancer aucun garde, defaut mesure le 2026-09-07 (#5426).
    """
    modules = list(modules_declares(racine))
    if not modules:
        return True
    python = venv_de(racine) / "bin" / "python"
    if not python.exists():
        return False
    try:
        rendu = lance(
            [str(python), "-c", "import " + ", ".join(modules)],
            capture_output=True,
            text=True,
            check=False,
        )
    except FileNotFoundError:
        return False
    return rendu.returncode == 0


def modules_declares(racine: pathlib.Path) -> dict[str, str]:
    """Ce qu un garde IMPORTE, et la distribution qui le fournit.

    La table `[tool.vigiechiro.modules]` existe deja et dit exactement cela : « le nom de la
    DISTRIBUTION n est pas celui du MODULE ». On la lit plutot que de deviner - deviner exigerait
    d interroger ce qui est installe, donc de rendre un verdict qui depend de la machine.
    """
    fichier = racine / "pyproject.toml"
    if not fichier.exists():
        return {}
    import tomllib

    try:
        donnees = tomllib.loads(fichier.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError:
        return {}
    table = ((donnees.get("tool") or {}).get("vigiechiro") or {}).get("modules") or {}

    # Le groupe `gardes` SEUL. `mkdocs-material` est declare aussi, et ses modules manqueraient a un
    # poste qui ne construit pas la documentation - les nommer ici ferait crier le dispositif sur du
    # bon travail, ce que l ADR 4002 refuse. Ce controle ne parle que de ce qu un GARDE importe.
    du_groupe = {
        re.split(r"[=<>!~\[;]", e)[0].strip().lower().replace("_", "-")
        for e in (donnees.get("dependency-groups") or {}).get("gardes") or []
        if isinstance(e, str)
    }

    rendu = {}
    for distribution, modules in table.items():
        if distribution.lower().replace("_", "-") not in du_groupe:
            continue
        for module in modules if isinstance(modules, list) else []:
            if isinstance(module, str) and module.strip():
                rendu[module.strip()] = distribution
    return rendu


def modules_manquants(racine: pathlib.Path) -> list[tuple[str, str]]:
    """Les modules DECLARES que l interpreteur courant ne porte pas.

    **Ce que ce controle repare.** `ruff` est un EXECUTABLE : le poser dans un venv suffit, on l
    appelle par son chemin. Une grammaire est un MODULE : la poser dans un venv ne sert a rien tant
    que le python qui LANCE le garde ne la porte pas. Le depot avait une convention pour les premiers
    et aucune pour les seconds, et `PyYAML` masquait le trou en etant porte par chance - par le python
    systeme comme par l image de CI (#5426).

    Il ne POSE rien : `pip` n est pas garanti sur un poste, `uv` n est pas un prerequis - `pyproject`
    l ecrit - et choisir a la place de l utilisateur ou vit son interpreteur serait decider pour lui.
    Il DIT, ce qui suffit a distinguer « le code est fautif » de « ton interpreteur ne voit pas ce que
    le depot declare ».
    """
    import importlib.util

    manquants = []
    for module, distribution in sorted(modules_declares(racine).items()):
        if importlib.util.find_spec(module) is None:
            manquants.append((module, distribution))
    return manquants


def a_faire(base: pathlib.Path) -> list[tuple[str, list[str]]]:
    """Ce qui MANQUE, nomme, et la commande qui le pose.

    Rendu comme une liste plutot qu execute ici : c est ce qui rend la decision lisible et le cas
    « rien a faire » verifiable sans lancer quoi que ce soit.
    """
    manques = []
    if not openspec_pose(base):
        manques.append(
            ("l outil OpenSpec", ["npm", "ci", "--prefix", str(base / ".github" / "openspec")])
        )
    if not ruff_pose(base):
        manques.append(("le venv du worktree", [sys.executable, "-m", "venv", str(venv_de(base))]))
    elif not venv_porte_le_groupe(base):
        # Le venv existe et porte `ruff`, mais pas les modules : le groupe a bouge depuis sa creation.
        manques.append(
            (
                "le groupe `gardes` du venv",
                [
                    str(venv_de(base) / "bin" / "pip"),
                    "install",
                    "-q",
                    "--group",
                    str(base / "pyproject.toml") + ":gardes",
                ],
            )
        )
    return manques


def pose(racine: pathlib.Path | None = None, lance=subprocess.run) -> int:
    """Pose ce qui manque. Rend le nombre d echecs, et n en fait jamais un motif de blocage."""
    base = racine or RACINE
    manques = a_faire(base)
    if not manques:
        return 0

    echecs = 0
    for quoi, commande in manques:
        # `FileNotFoundError` quand l OUTIL lui-meme manque - `npm` absent du PATH. Sans ce filet, la
        # trace Python entiere sort dans un crochet que personne ne regarde, la ou le crochet voisin
        # prend soin qu « une seule ligne d avertissement remplace le mur d erreurs ». Trouve en
        # mesurant, pas en relisant.
        try:
            rendu = lance(commande, capture_output=True, text=True, check=False)
        except FileNotFoundError:
            echecs += 1
            print(f"preparation : {quoi} n a pas pu etre pose, « {commande[0]} » est introuvable")
            continue
        if rendu.returncode != 0:
            echecs += 1
            print(f"preparation : {quoi} n a pas pu etre pose ({' '.join(commande[:3])}...)")
            continue
        # `ruff` demande un second geste : le venv, puis le paquet epingle par `pyproject.toml`.
        #
        # Et le GROUPE ENTIER avec lui, pas seulement `ruff` : le venv doit porter ce qu un garde
        # IMPORTE, sans quoi il ne sert qu aux executables. C est ce qui rend le venv utilisable comme
        # interpreteur, et donc #5434 possible - la porte pourra le lancer au lieu de `python3`.
        if quoi == "le venv du worktree":
            suite = lance(
                [
                    str(venv_de(base) / "bin" / "pip"),
                    "install",
                    "-q",
                    "--group",
                    str(base / "pyproject.toml") + ":gardes",
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            if suite.returncode != 0:
                # Repli : le groupe demande un `pip` recent. A defaut, `ruff` seul, qui suffit aux
                # compétences qui l appellent par son chemin.
                suite = lance(
                    [str(venv_de(base) / "bin" / "pip"), "install", "-q", version_de_ruff(base)],
                    capture_output=True,
                    text=True,
                    check=False,
                )
            if suite.returncode != 0:
                echecs += 1
                print("preparation : le groupe `gardes` n a pas pu etre installe dans `.venv`")
                continue
        print(f"preparation : {quoi} pose")
    return echecs


def dit_les_modules_manquants(racine: pathlib.Path | None = None) -> int:
    """Nomme ce que l interpreteur courant ne voit pas, et rend le compte. Ne pose rien."""
    base = racine or RACINE
    manquants = modules_manquants(base)
    if not manquants:
        return 0
    import sys as _sys

    print(
        f"preparation : {len(manquants)} module(s) declare(s) invisible(s) depuis {_sys.executable} :"
    )
    for module, distribution in manquants:
        print(f"    `import {module}` -> absent ; il vient de `{distribution}`")
    print(
        "    Le depot les declare au groupe `gardes` de `pyproject.toml`. Trois facons de les avoir,"
    )
    print(
        "    et le depot n en impose aucune : `pip install --group gardes`, `uv run --group gardes`,"
    )
    print(
        "    ou un venv dont on lance le python. Un garde qui refuse ici ne dit RIEN de votre diff."
    )
    return len(manquants)


def version_de_ruff(racine: pathlib.Path) -> str:
    """La version EPINGLEE, lue dans `pyproject.toml` plutot que recopiee ici.

    Une version ecrite a deux endroits diverge, et c est le garde d epinglage qui le paierait. A
    defaut de la lire, on installe `ruff` nu : mieux vaut un outil present qu un refus de poser.
    """
    fichier = racine / "pyproject.toml"
    if not fichier.exists():
        return "ruff"
    import tomllib

    try:
        donnees = tomllib.loads(fichier.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError:
        return "ruff"
    for exigence in (donnees.get("dependency-groups") or {}).get("gardes") or []:
        if isinstance(exigence, str) and exigence.lower().startswith("ruff"):
            return exigence
    return "ruff"


def _auto_test() -> int:
    import tempfile

    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu!r}, obtenu {obtenu!r}")
            echecs = 1

    bac = pathlib.Path(tempfile.mkdtemp())
    (bac / "pyproject.toml").write_text(
        '[dependency-groups]\ngardes = ["PyYAML==6.0.2", "ruff==0.16.5"]\n', encoding="utf-8"
    )

    # La VERSION vient du fichier, jamais d une constante : deux endroits divergent.
    verifie("la version de ruff se lit dans pyproject.toml", version_de_ruff(bac), "ruff==0.16.5")
    verifie(
        "sans fichier, ruff nu plutot qu un refus de poser", version_de_ruff(bac / "absent"), "ruff"
    )

    # Ce qui MANQUE est nomme. `node_modules` absent de ce bac : OpenSpec doit y figurer.
    quoi = [q for q, _ in a_faire(bac)]
    verifie("un OpenSpec absent est a poser", "l outil OpenSpec" in quoi, True)

    # Et ce qui est LA n est pas repose : c est ce qui rend la seconde creation gratuite.
    (bac / ".github" / "openspec" / "node_modules").mkdir(parents=True)
    quoi = [q for q, _ in a_faire(bac)]
    verifie("un OpenSpec deja pose n est PAS repose", "l outil OpenSpec" in quoi, False)

    # L ECHEC ne bloque pas, et il se compte. Sans ce cas, une preparation muette passerait pour
    # complete, et la porte serait moins sure qu avant.
    vide = pathlib.Path(tempfile.mkdtemp())

    class Rate:
        returncode = 1
        stdout = ""
        stderr = "pas de reseau"

    verifie("un echec est compte, pas leve", pose(vide, lance=lambda *a, **k: Rate()) > 0, True)

    # L OUTIL ABSENT du PATH : `npm` introuvable leve `FileNotFoundError`, et une trace Python
    # entiere dans un crochet que personne ne regarde vaut moins qu une ligne. Trouve en mesurant.
    def introuvable(*a, **k):
        raise FileNotFoundError(2, "No such file or directory", "npm")

    verifie(
        "un outil introuvable est dit en UNE ligne, pas en trace",
        pose(vide, lance=introuvable) > 0,
        True,
    )

    # ⟨LES MODULES DECLARES⟩ Un module absent de l interpreteur courant se DIT, il ne se pose pas.
    decl = pathlib.Path(tempfile.mkdtemp())
    (decl / "pyproject.toml").write_text(
        "[dependency-groups]\n"
        'gardes = ["PyYAML==6.0.2", "tree-sitter-language-pack==1.16.1"]\n'
        'doc = ["mkdocs-material==9.7.7"]\n'
        "[tool.vigiechiro.modules]\n"
        'PyYAML = ["yaml"]\n'
        'tree-sitter-language-pack = ["tree_sitter_language_pack"]\n'
        'mkdocs-material = ["mkdocs", "material"]\n',
        encoding="utf-8",
    )
    vus = modules_declares(decl)
    verifie(
        "les modules du groupe gardes sont lus", sorted(vus), ["tree_sitter_language_pack", "yaml"]
    )
    verifie("ceux du groupe DOC sont ecartes", "material" in vus, False)
    verifie("la distribution est rendue avec le module", vus.get("yaml"), "PyYAML")

    # Le sens NEGATIF de ce controle : un module PRESENT ne se signale pas. `yaml` l est ici.
    manquants = dict(modules_manquants(decl))
    verifie("un module present n est pas signale", "yaml" in manquants, False)

    # Sans declaration, rien a dire - un dispositif qui crierait sur un depot vide serait faux.
    verifie(
        "sans table, aucun module manquant", modules_manquants(pathlib.Path(tempfile.mkdtemp())), []
    )

    # Le sens NEGATIF : rien a poser rend zero sans rien lancer.
    lances = []

    def espion(*a, **k):
        lances.append(a)
        return Rate()

    complet = pathlib.Path(tempfile.mkdtemp())
    (complet / ".github" / "openspec" / "node_modules").mkdir(parents=True)
    if ruff_pose(complet):
        verifie("rien a poser ne lance rien", (pose(complet, lance=espion), len(lances)), (0, 0))

    print()
    return echecs


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    # JAMAIS un motif de blocage : le crochet qui l appelle doit rendre la main quoi qu il arrive.
    pose()
    dit_les_modules_manquants()
    raise SystemExit(0)

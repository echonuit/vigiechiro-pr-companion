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

**Pourquoi en Python et non dans le crochet.** Ce dispositif n a **aucun gardien en CI** - le runner
installe tout lui-meme et ne joue jamais ce chemin. Son auto-test porte donc seul, et trente lignes
de bash se relisent la ou un script Python s eprouve.

Usage :
    python3 scripts/methode/prepare-l-environnement.py
    python3 scripts/methode/prepare-l-environnement.py --auto-test
"""

import pathlib
import subprocess
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import sort_si_contrat_demande

# Le venv d outils vit HORS du depot : l y poser le ferait entrer dans les corpus que les gardes
# balaient, et il faudrait l exclure partout. `ouvrir-une-pr` prescrit deja ce chemin.
VENV = pathlib.Path.home() / ".venv-outils"

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


def ruff_pose() -> bool:
    """Le venv d outils porte-t-il `ruff` ?"""
    return (VENV / "bin" / "ruff").exists()


def a_faire(racine: pathlib.Path) -> list[tuple[str, list[str]]]:
    """Ce qui MANQUE, nomme, et la commande qui le pose.

    Rendu comme une liste plutot qu execute ici : c est ce qui rend la decision lisible et le cas
    « rien a faire » verifiable sans lancer quoi que ce soit.
    """
    manques = []
    if not openspec_pose(racine):
        manques.append(
            ("l outil OpenSpec", ["npm", "ci", "--prefix", str(racine / ".github" / "openspec")])
        )
    if not ruff_pose():
        manques.append(("ruff", [sys.executable, "-m", "venv", str(VENV)]))
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
        if quoi == "ruff":
            suite = lance(
                [str(VENV / "bin" / "pip"), "install", "-q", version_de_ruff(base)],
                capture_output=True,
                text=True,
                check=False,
            )
            if suite.returncode != 0:
                echecs += 1
                print("preparation : ruff n a pas pu etre installe dans le venv d outils")
                continue
        print(f"preparation : {quoi} pose")
    return echecs


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

    # Le sens NEGATIF : rien a poser rend zero sans rien lancer.
    lances = []

    def espion(*a, **k):
        lances.append(a)
        return Rate()

    complet = pathlib.Path(tempfile.mkdtemp())
    (complet / ".github" / "openspec" / "node_modules").mkdir(parents=True)
    if ruff_pose():
        verifie("rien a poser ne lance rien", (pose(complet, lance=espion), len(lances)), (0, 0))

    print()
    return echecs


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    # JAMAIS un motif de blocage : le crochet qui l appelle doit rendre la main quoi qu il arrive.
    pose()
    raise SystemExit(0)

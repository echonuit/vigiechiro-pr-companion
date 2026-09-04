#!/usr/bin/env python3
"""Cliquet sur ce qui reste en shell : le corpus descend, il ne remonte pas (ADR 5188).

Pourquoi « probable » et non « certaine » : ce garde compte des fichiers, ce qui est exact, mais la
DECISION qu il sert est « Bash disparait ». Aucun compte ne prouve qu un script a ete converti plutot
que supprime, ni qu il a ete remplace par quelque chose de meilleur. Le cliquet borne la dette et
rend la cible opposable ; il ne juge pas une conversion en particulier, et c est un humain qui trie.

## Ce qu il empeche, et ce qu il n empeche pas

Il empeche la seule chose qui rendrait la cible inatteignable : **qu on ajoute du shell**. Un depot
qui convertit vingt scripts et en ecrit vingt-deux n avance pas, et rien ne le disait avant ce garde
puisque la regle elle-meme ne vivait dans aucun fichier - elle etait dans le corps de l EPIC #5102,
qu une cloture archive (issue #5188).

Il n empeche pas qu un script grossisse, ni qu on deplace du shell dans un bloc `run:` de workflow.
La seconde faille est reelle et connue : elle se refermera si on la constate, pas par precaution.

## La population est ce que git suit, et c est un choix

`git ls-files` et non un parcours du disque. Un `.sh` non indexe est invisible de ce cliquet, comme
il l est de tous les autres du depot. C est le meme angle mort partout plutot qu un angle mort
different ici, et un fichier non indexe n est de toute facon pas encore du depot.

Le `find` du job `lint`, lui, balaie le disque. Les deux ensembles ont ete compares dans les deux
sens le 2026-09-03 : ils sont identiques, a cinquante fichiers (issue #5187).

## Ce que le seuil vaut, et ce qu il vaudra

3 est la mesure du jour, pas un objectif. Elle etait de 50 le 2026-09-04 au matin, et sept lots l ont fait descendre : #5210, #5219, #5221, #5229, #5231, #5233, #5236, puis #5239 les quatre qui regardent des pixels. Un cliquet ne se negocie pas vers le haut : chaque
conversion le fait descendre, et il ne remonte jamais. La cible est **zero**, ce qui distingue ce
cliquet de la plupart des autres : ceux-la bornent une dette qu on tolere, celui-ci compte une
population qui doit disparaitre entierement.
"""

from __future__ import annotations

import pathlib
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import RACINE_DEPOT, rapporte, sort_si_contrat_demande

ADR = "5188"

# Toleres AUJOURD HUI, avec la condition de levee. Ce n est PAS une liste d exemptions : chacun de
# ces scripts disparait, et la condition dit seulement quand. Ils sont donc COMPTES par le cliquet,
# pas retires de lui - les retirer ferait croire a une dispense, et le compte cesserait de dire la
# verite sur ce qui reste a convertir.
TOLERES = {
    ".github/scripts/lance-test-filme.sh": (
        "1 295 lignes d orchestration, tolerees tant que le banc Java n est pas definitivement "
        "valide. La levee de cette condition declenche la conversion"
    ),
}


def fichiers(racine: pathlib.Path | None = None) -> list[str]:
    """Les scripts shell que git suit, chemins relatifs a la racine du depot.

    Extrait de `suspects()` pour que `lus` les compte : un ciblage manque rendrait zero suspect sur
    zero fichier, et ce zero passerait pour un succes (issue #5007).
    """
    base = racine or RACINE_DEPOT
    rendu = subprocess.run(
        ["git", "ls-files", "*.sh"],
        cwd=base,
        capture_output=True,
        text=True,
        check=False,
    )
    return sorted(l for l in rendu.stdout.split("\n") if l.strip())


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Chaque script shell est une unite de dette : le cliquet compte ce qui reste a convertir."""
    trouves = []
    for f in fichiers(racine):
        raison = TOLERES.get(f)
        trouves.append(f"{f}  a convertir" + (f" (tolere : {raison})" if raison else ""))
    return trouves


def _auto_test() -> int:
    """Les DEUX sens : le cliquet voit un script de plus, et il ne crie pas sur un corpus vide."""
    import tempfile

    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs = 1

    print("Auto-test du cliquet du corpus shell (#5188) :")

    with tempfile.TemporaryDirectory(prefix="vc-5188-") as tmp:
        faux = pathlib.Path(tmp)
        subprocess.run(["git", "init", "-q"], cwd=faux, check=True)

        # UN DEPOT SANS SHELL NE REND RIEN. Sans ce cas, un ciblage manque rendrait zero et
        # ressemblerait a la cible atteinte.
        (faux / "un.py").write_text("x = 1\n", encoding="utf-8")
        subprocess.run(["git", "add", "-A"], cwd=faux, check=True)
        verifie("un depot sans script shell ne rend aucun suspect", suspects(faux), [])

        # LE SENS QUI COMPTE : un script de plus est vu. C est la seule chose qui rendrait la
        # cible inatteignable, et c est donc ce que ce garde doit attraper.
        (faux / "ajoute.sh").write_text("#!/usr/bin/env bash\ntrue\n", encoding="utf-8")
        subprocess.run(["git", "add", "-A"], cwd=faux, check=True)
        verifie("un script shell ajoute est vu", suspects(faux), ["ajoute.sh  a convertir"])

        # Et un second, pour que le compte suive et non seulement la presence.
        (faux / "encore.sh").write_text("#!/usr/bin/env bash\ntrue\n", encoding="utf-8")
        subprocess.run(["git", "add", "-A"], cwd=faux, check=True)
        verifie("le compte suit le corpus", len(suspects(faux)), 2)

        # Un fichier NON INDEXE reste invisible, et c est ecrit dans la docstring plutot que subi.
        (faux / "jamais-ajoute.sh").write_text("#!/usr/bin/env bash\ntrue\n", encoding="utf-8")
        verifie("un .sh non indexe n entre pas dans le corpus", len(suspects(faux)), 2)

    # LA TOLERANCE EST COMPTEE, pas retiree. Ce cas tient la distinction que l ADR pose : un delai
    # n est pas une exemption, donc le tolere reste dans le compte de ce qui reste a convertir.
    reels = suspects()
    tolere = [s for s in reels if "(tolere :" in s]
    verifie("le script tolere est COMPTE, avec sa condition", len(tolere), len(TOLERES))
    verifie("aucun script n est retire du compte", len(reels), len(fichiers()))

    print()
    print("Auto-test concluant." if not echecs else "Auto-test EN ÉCHEC.")
    return echecs


# Ce que ce garde DECLARE etre (issue #5009).
CONTRAT = {
    "geste": "script shell restant, que la cible des deux langages condamne",
    "population": "les fichiers .sh que git suit",
    "dispositif": "cliquet",
    "seuil": "3, polarite=descend",
    "temoin": "scripts/adr/5188-corpus-shell.py --auto-test",
    "decision": "ADR 5188",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(rapporte(ADR, "script shell restant a convertir", suspects(), lus=len(fichiers())))

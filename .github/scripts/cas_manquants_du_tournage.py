#!/usr/bin/env python3
"""Quels cas un tournage n a pas rendus, et non pas combien (#5012, porte du bash en #5229).

## Le defaut qu il ferme

L oracle du tournage comparait deux nombres : « 111 cas sur 116 : le tournage est incomplet ». Il
refusait donc a raison, sans jamais dire QUOI corriger, et il fallait refaire son calcul a la main
pour l apprendre.

Le compte attendu incluait par ailleurs les cas des scenarios `recette-connectee`, exclus du build
sans jeton : un tournage non connecte se voyait demander un nombre que sa configuration lui
interdisait d atteindre. La release a cesse de verser ses clips Java du 26 au 31 aout sans que rien
ne le dise, la publication reussissant par ailleurs. La quatrieme colonne de `sessions-a-filmer.tsv`
porte desormais les cas qu un tournage non connecte doit rendre, et ce script les confronte a
l index.

## Ce qu il ne fait pas

Il ne juge pas les clips : un cas peut etre indexe par un clip vide, et c est l affaire du seuil de
luminance et de `uneFenetreAParu`. Il repond a une seule question, celle qui manquait : lesquels.

## Les deux vides, qui ne veulent pas dire la meme chose

Un **index** vide fait REFUSER : le garde ne conclut pas sur ce qu il n a pas lu. Une liste
d **attendus** vide rend 0 : une session dont tous les cas sont connectes n a rien a rendre hors
connexion, et traiter ce vide comme le premier designerait tout l index comme manquant.

Sorties :
  0, rien sur la sortie standard  : tous les cas attendus sont indexes
  1, un cas par ligne             : ceux qui manquent
  2                               : il n a pas pu lire, et ne conclut pas

Usage : ./.github/scripts/cas_manquants_du_tournage.py <cas-attendus-separes-par-virgule> <index.md>
        ./.github/scripts/cas_manquants_du_tournage.py --auto-test
"""

from __future__ import annotations

import pathlib
import sys


def indexes(index: pathlib.Path) -> set[str]:
    """Les cas indexes, dedoublonnes.

    Un cas joue par deux tests porte deux lignes, et n est pas manquant deux fois.
    """
    trouves = set()
    for ligne in index.read_text(encoding="utf-8").splitlines():
        if ligne.startswith("| S"):
            # `cut -d'|' -f2` : le deuxieme champ, la barre de tete ouvrant un champ vide.
            trouves.add(ligne.split("|")[1].replace(" ", ""))
    return trouves


def manquants(attendus: str, index: str | pathlib.Path) -> tuple[list[str], int]:
    """Les cas attendus qu aucune ligne d index ne porte, et le code de sortie qui va avec."""
    chemin = pathlib.Path(index)
    if not chemin.is_file() or chemin.stat().st_size == 0:
        print(
            f"REFUS : « {index} » est absent ou vide. Ce garde ne conclut pas sur ce qu'il n'a pas lu.",
            file=sys.stderr,
        )
        return [], 2
    if not attendus:
        return [], 0
    voulus = {c for c in attendus.split(",") if c}
    absents = sorted(voulus - indexes(chemin))
    return (absents, 1) if absents else ([], 0)


# (intitule, attendus, sortie voulue, code voulu)
CAS = (
    ("tout indexé : rien à dire, code 0", "S1-01,S1-02", "", 0),
    ("un cas absent : il est NOMMÉ, code 1", "S1-01,S1-03", "S1-03", 1),
    ("deux absents : les deux sont nommés", "S1-04,S1-05", "S1-04\nS1-05", 1),
    ("un cas indexé deux fois ne manque pas", "S1-02", "", 0),
    # Le vide qui n est PAS un refus : sans ce cas, le garde traiterait les deux vides pareil.
    ("aucun attendu : légitime, code 0", "", "", 0),
)

INDEX = "| S1-01 | x |\n| S1-02 | x |\n| S1-02 | y |\n"


def _auto_test() -> int:
    """Les cinq cas de la version bash, plus le refus sur index illisible."""
    import contextlib
    import io
    import tempfile

    echecs = 0
    with tempfile.TemporaryDirectory(prefix="vc-casmanq-") as tmp:
        index = pathlib.Path(tmp) / "index.md"
        index.write_text(INDEX, encoding="utf-8")

        def verifier(intitule: str, attendus: str, voulue: str, code_voulu: int) -> None:
            nonlocal echecs
            with contextlib.redirect_stderr(io.StringIO()):
                absents, code = manquants(attendus, index)
            sortie = "\n".join(absents)
            if sortie == voulue and code == code_voulu:
                print(f"  ✔ {intitule}")
            else:
                print(
                    f"  ✗ {intitule} : code={code} (voulu {code_voulu}), "
                    f"sortie=« {sortie} » (voulue « {voulue} »)"
                )
                echecs = 1

        for intitule, attendus, voulue, code_voulu in CAS:
            verifier(intitule, attendus, voulue, code_voulu)

        with contextlib.redirect_stderr(io.StringIO()):
            absents, code = manquants("S1-01", pathlib.Path(tmp) / "absent.md")
        if code == 2 and not absents:
            print("  ✔ index illisible : REFUSE au lieu de conclure, code 2")
        else:
            print(f"  ✗ index illisible : code={code} (voulu 2)")
            echecs = 1

    print("Auto-test : tous les cas passent." if echecs == 0 else "Auto-test : ÉCHEC.")
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    if len(sys.argv) != 3:
        print(
            f"Usage : {sys.argv[0]} <cas-attendus-separes-par-virgule> <index.md>", file=sys.stderr
        )
        sys.exit(2)
    absents, code = manquants(sys.argv[1], sys.argv[2])
    for c in absents:
        print(c)
    sys.exit(code)

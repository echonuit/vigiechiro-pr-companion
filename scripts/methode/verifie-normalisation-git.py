#!/usr/bin/env python3
"""Garde : aucun blob texte n est range en CRLF contre `.gitattributes` (#5781).

    python3 scripts/methode/verifie-normalisation-git.py
    python3 scripts/methode/verifie-normalisation-git.py --auto-test

`.gitattributes` range tout texte en LF dans le depot (`* text=auto eol=lf`), les scripts Windows
compris (`*.cmd text eol=crlf` ne regle que l extraction sur le poste). Un outil qui commite sans
passer par les filtres de git peut pourtant ranger un blob en CRLF. Mesure : le bump de dependabot
#5749 (`0e99ce246`) a commite `mvnw.cmd` ainsi, 189 retours chariot.

Rien ne rougissait, et le defaut ne se voyait qu a la demande SUIVANTE : son `git add -A`
renormalise le fichier sans le dire, et son diff porte une reecriture complete de `mvnw.cmd` qu elle
n a pas voulue (#5778, 189 lignes supprimees et 189 ajoutees, contenu identique). Le diff d une
demande cesse alors de dire ce qu elle fait.

Le garde lit `git ls-files --eol` : un fichier dont l index est `i/crlf` ou `i/mixed` alors que ses
attributs le declarent texte est refuse, en le nommant. `git add --renormalize <fichier>` le repare.
Il lit l index, sans rien reecrire.
"""

from __future__ import annotations

import pathlib
import subprocess
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import cas_d_auto_test, sort_si_contrat_demande

INDEX_NON_NORMALISE = ("i/crlf", "i/mixed")


def non_normalises(sortie: str) -> list[str]:
    """Les chemins que `git ls-files --eol` montre texte, mais ranges en CRLF ou en mixte. Pure."""
    fautifs = []
    for ligne in sortie.splitlines():
        if "\t" not in ligne:
            continue
        colonnes, chemin = ligne.split("\t", 1)
        index, _, attributs = (colonnes.split() + ["", "", ""])[:3]
        texte = attributs.startswith("attr/text") and not attributs.startswith("attr/-text")
        if index in INDEX_NON_NORMALISE and texte:
            fautifs.append(chemin)
    return fautifs


def lit_l_index(racine: pathlib.Path) -> tuple[int, str]:
    """`git ls-files --eol` dans `racine` : (code, sortie). Le code se lit, la sortie seule ne suffit pas."""
    fait = subprocess.run(
        ["git", "-C", str(racine), "ls-files", "--eol"], capture_output=True, text=True, check=False
    )
    return fait.returncode, fait.stdout


def verdict(racine: pathlib.Path) -> tuple[int, list[str]]:
    code, sortie = lit_l_index(racine)
    if code != 0 or not sortie.strip():
        return 2, [
            f"REFUS : `git ls-files --eol` n a rien rendu de lisible (code {code}). Rien lu, rien conclu."
        ]
    fautifs = non_normalises(sortie)
    lus = len(sortie.splitlines())
    if fautifs:
        return 1, [
            f"REFUS : {len(fautifs)} fichier(s) texte range(s) en CRLF contre `.gitattributes`, sur {lus} lus :"
        ] + [f"  {f}" for f in fautifs] + [
            "Reparer : `git add --renormalize <fichier>`, puis commiter. Sans cela, la demande suivante",
            "portera une reecriture complete du fichier qu elle n a pas voulue (#5781).",
        ]
    return 0, [
        f"Normalisation : {lus} fichier(s) lus, aucun range en CRLF contre `.gitattributes`."
    ]


def auto_test() -> int:
    """Une ligne fabriquee pour chaque forme, puis un vrai depot jetable avec un blob CRLF indexe."""
    verifie, echecs = cas_d_auto_test()
    verifie(
        "un texte range en CRLF est nomme",
        lambda: non_normalises("i/crlf  w/crlf  attr/text eol=crlf\tmvnw.cmd"),
        ["mvnw.cmd"],
    )
    verifie(
        "un texte mixte aussi",
        lambda: non_normalises("i/mixed w/mixed attr/text=auto eol=lf\ta.md"),
        ["a.md"],
    )
    verifie(
        "un texte range en LF passe",
        lambda: non_normalises("i/lf    w/crlf  attr/text eol=crlf\tmvnw.cmd"),
        [],
    )
    verifie(
        "un binaire en CRLF passe, il n est pas du texte",
        lambda: non_normalises("i/crlf  w/crlf  attr/-text \tlogo.png"),
        [],
    )

    with tempfile.TemporaryDirectory() as racine:
        depot = pathlib.Path(racine)

        def git(*args: str, entree: bytes | None = None) -> str:
            return subprocess.run(
                ["git", "-C", racine, *args], input=entree, capture_output=True, check=True
            ).stdout.decode()

        git("init", "-q")
        (depot / ".gitattributes").write_text("* text=auto eol=lf\n*.cmd text eol=crlf\n")
        git("add", ".gitattributes")
        verifie("un depot sain rend 0", lambda: verdict(depot)[0], 0)
        # Comme dependabot : un blob ecrit SANS les filtres de git, puis indexe tel quel.
        blob = git(
            "hash-object", "-w", "--no-filters", "--stdin", entree=b"@echo off\r\nexit /b 0\r\n"
        ).strip()
        git("update-index", "--add", "--cacheinfo", f"100644,{blob},mvnw.cmd")
        code, lignes = verdict(depot)
        verifie("le blob CRLF de dependabot est refuse", lambda: code, 1)
        verifie("le refus le nomme", lambda: "  mvnw.cmd" in lignes, True)
        git("checkout", "-q", "--", "mvnw.cmd")
        git("add", "--renormalize", "mvnw.cmd")
        verifie("`git add --renormalize` le repare", lambda: verdict(depot)[0], 0)
    verifie(
        "hors d un depot, refus plutot qu un vert vide", lambda: verdict(pathlib.Path("/"))[0], 2
    )
    return echecs()


CONTRAT = {
    "geste": "blob texte range en CRLF contre .gitattributes",
    "population": "l index du depot, par git ls-files --eol",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/verifie-normalisation-git.py --auto-test",
    "decision": "hygiene, sans decision",
    # Tout le depot, et c est exact : n importe quel fichier ajoute ou modifie peut arriver en CRLF,
    # et la lecture de l index coute moins d une seconde (ADR 5340 admet un `**` juste).
    "chemins": """
**
""",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv[1:]:
        sys.exit(auto_test())
    code, lignes = verdict(pathlib.Path(__file__).resolve().parents[2])
    print("\n".join(lignes))
    sys.exit(code)

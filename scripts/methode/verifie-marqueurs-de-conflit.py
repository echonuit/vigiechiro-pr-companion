#!/usr/bin/env python3
"""Garde : aucun fichier suivi ne porte de marqueur de conflit (#4329).

    python3 scripts/methode/verifie-marqueurs-de-conflit.py
    python3 scripts/methode/verifie-marqueurs-de-conflit.py --auto-test

Le 2026-08-23, `main` a porte pendant plusieurs heures trois marqueurs de conflit dans la feuille de
style principale, `src/main/java/fr/univ_amu/iut/commun/view/design.css` (#4294, retires par #4310).
A partir du premier, le fichier n etait plus du CSS valide : tout ce qui suivait cessait d etre
applique, et c est la feuille LIVREE qui etait amputee.

Les tests ont rougi, mais dans un ordre d execution sur deux, et apres six minutes de suite Java :
le defaut se prenait pour un flottement alors qu il se lit dans le fichier. Ce garde le lit, en
moins d une seconde.

**Ce qu il refuse.** Une ligne qui COMMENCE par l une des trois formes que git ecrit : sept chevrons
ouvrants suivis d une espace, sept signes egal seuls sur la ligne, sept chevrons fermants suivis d une
espace. Les trois formes vivent dans UN motif, `MARQUEUR`, que le verdict et l auto-test lisent tous
deux par `marqueurs` : un second motif, pour un `git grep`, ne serait eprouve par aucun cas.

**Ce qu il ne refuse pas.** Un marqueur CITE en cours de ligne, un signe egal dans un tableau
Markdown, huit signes egal ou six. Et le marqueur de base du style `diff3`, que l issue ne demande
pas. Les binaires sont ecartes par la regle de git, un octet nul dans les 8000 premiers.

**Le compte de fichiers vient de `git ls-files`**, et non de la recherche : chercher sans rien
trouver est le vert de ce garde, donc une recherche vide ne peut pas y dire « rien lu ». C est le
nombre de fichiers LUS qui le dit, et zero fait refuser.

**Ce qu il ne remplace pas.** Rien n empeche de fusionner une demande rouge. Ce garde rend le defaut
visible plus tot, il ne decide pas a la place de qui fusionne. Et il lit les marqueurs COMMITES : une
demande en conflit avec `main` n a aucun run.

Une ligne de sept signes egal souligne aussi un titre Markdown. Le depot n en porte aucune ; un tel
titre s ecrit avec `#`.
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import cas_d_auto_test, message_de_refus, sort_si_contrat_demande

# ⟨les trois formes de l issue, et rien d autre⟩ Les quantificateurs evitent d ecrire un marqueur en
# clair dans ce fichier, que le garde lit comme les autres.
MARQUEUR = re.compile(rb"^(?:<{7} .*|={7}|>{7} .*)\r?$", re.MULTILINE)

# La regle de git pour dire qu un contenu est binaire : un octet nul dans ses 8000 premiers.
FENETRE_BINAIRE = 8000


def marqueurs(contenu: bytes) -> list[int]:
    """Les numeros des lignes de `contenu` qui portent un marqueur de conflit. Pure."""
    return [contenu.count(b"\n", 0, trouve.start()) + 1 for trouve in MARQUEUR.finditer(contenu)]


def est_binaire(contenu: bytes) -> bool:
    return b"\0" in contenu[:FENETRE_BINAIRE]


def suivis(racine: pathlib.Path, git: str = "git") -> tuple[str, list[str]]:
    """Les fichiers que git suit dans `racine` : (panne, chemins). Une panne non vide = rien lu.

    `check=False` ne couvre pas un executable introuvable, qui leve avant qu il y ait un code : le
    filet le rend en panne NOMMEE, et le verdict refuse au lieu de planter (ADR 5692).
    """
    try:
        fait = subprocess.run(
            [git, "-C", str(racine), "ls-files", "-z"], capture_output=True, check=False
        )
    except OSError as leve:
        return f"`{git}` ne se lance pas ({type(leve).__name__})", []
    if fait.returncode != 0:
        return f"`{git} ls-files` a rendu le code {fait.returncode}", []
    return "", [
        chemin.decode(errors="surrogateescape") for chemin in fait.stdout.split(b"\0") if chemin
    ]


def verdict(racine: pathlib.Path, git: str = "git") -> tuple[int, list[str]]:
    """(code, lignes) : 0 aucun marqueur, 1 des marqueurs nommes, 2 rien lu donc rien conclu."""
    panne, chemins = suivis(racine, git)
    fautifs: list[str] = []
    en_cause = lus = binaires = 0
    for chemin in chemins:
        try:
            contenu = (racine / chemin).read_bytes()
        except OSError:
            # Un lien de sous-module, ou un fichier suivi retire de l arbre de travail : rien a lire.
            continue
        if est_binaire(contenu):
            binaires += 1
            continue
        lus += 1
        lignes = marqueurs(contenu)
        en_cause += bool(lignes)
        fautifs += [f"  {chemin}:{ligne}" for ligne in lignes]
    if panne or not lus:
        cause = panne or f"aucun fichier texte lu sur {len(chemins)} suivi(s)"
        return 2, message_de_refus(
            f"{cause}. Rien lu, rien conclu.",
            "lancer ce garde depuis un depot git, avec `git` dans le PATH. "
            "Ce refus ne dit rien de votre diff.",
        ).splitlines()
    if fautifs:
        return 1, [
            f"{len(fautifs)} marqueur(s) de conflit dans {en_cause} fichier(s) suivi(s), sur {lus} lu(s) :",
            *fautifs,
            "Resoudre le conflit et retirer ces lignes, puis commiter. Un exemple qui CITE un marqueur",
            "se decale d une espace ; un titre Markdown souligne de sept signes egal s ecrit avec `#`.",
        ]
    return 0, [
        f"Marqueurs de conflit : {lus} fichier(s) lu(s), {binaires} binaire(s) ecarte(s), aucun marqueur."
    ]


def auto_test() -> int:
    """Chaque forme fabriquee, les controles negatifs de l issue, puis un depot jetable en conflit."""
    verifie, echecs = cas_d_auto_test()
    ouvrant, milieu, fermant = b"<" * 7, b"=" * 7, b">" * 7

    verifie("le marqueur ouvrant est vu", lambda: marqueurs(b"a\n" + ouvrant + b" HEAD\nb\n"), [2])
    verifie("le separateur est vu", lambda: marqueurs(b"a\nb\n" + milieu + b"\n"), [3])
    verifie("le marqueur fermant est vu", lambda: marqueurs(fermant + b" Stashed changes\n"), [1])
    verifie(
        "le separateur suivi d un retour chariot aussi",
        lambda: marqueurs(b"a\r\n" + milieu + b"\r\nb\r\n"),
        [2],
    )
    verifie("le separateur en derniere ligne sans saut final aussi", lambda: marqueurs(milieu), [1])
    verifie(
        "un conflit entier rend ses trois lignes",
        lambda: marqueurs(
            b"a {\n"
            + ouvrant
            + b" Updated upstream\n  x\n"
            + milieu
            + b"\n  y\n"
            + fermant
            + b" s\n}\n"
        ),
        [2, 4, 6],
    )
    # Les deux controles negatifs que le corps de l issue nomme.
    verifie(
        "un signe egal dans un tableau Markdown passe",
        lambda: marqueurs(b"| a | b |\n| " + milieu + b" | " + milieu + b" |\n"),
        [],
    )
    verifie(
        "un marqueur cite en cours de ligne passe",
        lambda: marqueurs(
            b"Le fichier portait `" + ouvrant + b" Updated upstream` en ligne 1007.\n"
        ),
        [],
    )
    verifie(
        "un separateur cite en fin de ligne passe",
        lambda: marqueurs(b"la ligne etait " + milieu + b"\n"),
        [],
    )
    verifie(
        "un marqueur fermant cite en cours de ligne passe",
        lambda: marqueurs(b"puis `" + fermant + b" Stashed changes`\n"),
        [],
    )
    verifie("huit signes egal passent", lambda: marqueurs(milieu + b"=\n"), [])
    verifie("six signes egal passent", lambda: marqueurs(milieu[:6] + b"\n"), [])
    verifie(
        "sept chevrons ouvrants sans espace passent", lambda: marqueurs(ouvrant + b"HEAD\n"), []
    )
    verifie("sept chevrons fermants sans espace passent", lambda: marqueurs(fermant + b"x\n"), [])
    verifie("un octet nul fait un binaire", lambda: est_binaire(b"\x89PNG\x00" + ouvrant), True)
    verifie("un texte n est pas un binaire", lambda: est_binaire(ouvrant + b" HEAD\n"), False)

    with tempfile.TemporaryDirectory() as bac:
        depot = pathlib.Path(bac)

        def git(*args: str) -> None:
            subprocess.run(["git", "-C", bac, *args], capture_output=True, check=True)

        verifie("un depot jetable se cree", lambda: git("init", "-q"), None)
        verifie("un depot sans fichier suivi refuse, rien lu", lambda: verdict(depot)[0], 2)
        (depot / "sain.css").write_bytes(b".bouton {\n  -fx-padding: 4;\n}\n")
        (depot / "police.ttf").write_bytes(b"\x00\x01\n" + ouvrant + b" HEAD\n" + milieu + b"\n")
        verifie("deux fichiers s indexent", lambda: git("add", "sain.css", "police.ttf"), None)
        verifie("un depot sain rend 0", lambda: verdict(depot)[0], 0)
        verifie(
            "le vert compte ce qu il a lu",
            lambda: verdict(depot)[1],
            ["Marqueurs de conflit : 1 fichier(s) lu(s), 1 binaire(s) ecarte(s), aucun marqueur."],
        )
        # Le cas mesure le 2026-08-23 : un `stash pop` mal resolu, commite tel quel.
        (depot / "design.css").write_bytes(
            b".a {\n"
            + ouvrant
            + b" Updated upstream\n  -fx-padding: 4;\n"
            + milieu
            + b"\n  -fx-padding: 8;\n"
            + fermant
            + b" Stashed changes\n}\n"
        )
        verifie("le fichier en conflit s indexe", lambda: git("add", "design.css"), None)
        verifie("un fichier en conflit est refuse", lambda: verdict(depot)[0], 1)
        verifie(
            "le refus nomme le fichier et ses trois lignes",
            lambda: [ligne for ligne in verdict(depot)[1] if ligne.startswith("  ")],
            ["  design.css:2", "  design.css:4", "  design.css:6"],
        )
        verifie(
            "le refus compte les marqueurs et les fichiers lus",
            lambda: verdict(depot)[1][0],
            "3 marqueur(s) de conflit dans 1 fichier(s) suivi(s), sur 2 lu(s) :",
        )
        verifie(
            "un rouge ne porte pas la marque d un refus de conclure",
            lambda: any("REFUS :" in ligne for ligne in verdict(depot)[1]),
            False,
        )
        (depot / "non-suivi.css").write_bytes(milieu + b"\n")
        verifie(
            "un fichier non suivi n est pas lu",
            lambda: len([ligne for ligne in verdict(depot)[1] if ligne.startswith("  ")]),
            3,
        )
        verifie(
            "git introuvable refuse en le nommant, sans lever",
            lambda: (
                verdict(depot, git="git-absent-4329")[0],
                "git-absent-4329" in "\n".join(verdict(depot, git="git-absent-4329")[1]),
            ),
            (2, True),
        )
        # Un fichier suivi retire de l arbre de travail n a rien a lire : le garde conclut sur le reste.
        (depot / "sain.css").unlink()
        verifie(
            "un fichier suivi absent de l arbre ne fait pas lever",
            lambda: verdict(depot)[1][0],
            "3 marqueur(s) de conflit dans 1 fichier(s) suivi(s), sur 1 lu(s) :",
        )
    verifie(
        "hors d un depot, refus plutot qu un vert vide", lambda: verdict(pathlib.Path("/"))[0], 2
    )
    verifie(
        "ce refus nomme le code que git a rendu",
        lambda: "ls-files` a rendu le code" in "\n".join(verdict(pathlib.Path("/"))[1]),
        True,
    )
    verifie(
        "un refus de conclure porte ses deux marques",
        lambda: [
            marque in "\n".join(verdict(pathlib.Path("/"))[1])
            for marque in ("REFUS :", "POUR REPARER :")
        ],
        [True, True],
    )
    print(f"  {echecs.joues()} cas joues.")
    return echecs()


CONTRAT = {
    "geste": "marqueur de conflit versionne",
    "population": "les fichiers suivis du depot, par git ls-files",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/verifie-marqueurs-de-conflit.py --auto-test",
    "decision": "hygiene, sans decision",
    # Tout le depot, et c est exact : n importe quel fichier suivi peut recevoir un marqueur d un
    # rebase ou d un `stash pop` mal resolu, et la lecture coute moins d une seconde (ADR 5340 admet
    # un `**` juste).
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

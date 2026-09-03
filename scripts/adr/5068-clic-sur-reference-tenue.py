#!/usr/bin/env python3
"""Cliquet sur les clics qui tiennent une reference entre la resolution et le geste.

`clickOn(Node)` calcule son point depuis `node.getScene()`. Une scene nulle signifie que le noeud
n est plus attache au graphe au moment du clic : il a ete resolu plus haut, et rien ne garantit qu il
ait survecu entre les deux.

**Ce que ce cliquet tient, et ce qu il ne tient pas.** Il borne le nombre de sites EXPOSES, pas le
nombre de defauts : une reference tenue sur un noeud qui ne bouge pas est sans danger. C est un
cliquet de dette, pas un detecteur de bogue.

**Pourquoi tenir plutot que convertir.** Le releve mesure ce defaut a 1/1234 sur 21 jours, la
frequence la plus basse de sa liste, et il n est pas retombe depuis. Convertir 38 sites pour cela n
est pas ce que la mesure designe (#4696, fermee en assumant sa dette). Mais 38 peuvent devenir 45 sans
que personne le voie, et l article A9 refuse cela.

**Trois formes sont ecartees, et chacune a fait surcompter pendant #4804.** Un selecteur litteral
`clickOn("#champ")` se RESOUT au moment du clic, donc il ne porte pas le defaut ; une constante
`String` du meme fichier est un selecteur sous un autre nom ; une ligne de commentaire qui cite l
appel n est pas un appel.
"""

from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import TESTS_ANCRES, rapporte, sort_si_contrat_demande

ADR = "5068"

# L aide partagee porte l unique clic sur noeud qui soit delibere : elle EST le geste commun.
EXEMPTES = {"GesteVisible.java"}

APPEL = re.compile(r"\bclickOn\(\s*([^\n]*)")
COMMENTAIRE = re.compile(r"^\s*(///|//|\*|/\*)")
CONSTANTE = re.compile(r'String\s+([A-Z_][A-Z0-9_]*)\s*=\s*"')
JETON = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")


def premierArgument(reste: str) -> str:
    """Le premier argument de l appel, decoupe au niveau zero de parentheses."""
    profondeur, sortie = 0, []
    for caractere in reste:
        if caractere in "([{":
            profondeur += 1
        elif caractere in ")]}":
            if profondeur == 0:
                break
            profondeur -= 1
        elif caractere == "," and profondeur == 0:
            break
        sortie.append(caractere)
    return "".join(sortie).strip()


def fichiers(racine: pathlib.Path = TESTS_ANCRES) -> list[pathlib.Path]:
    """Les unites que ce garde LIT, extraites pour que `lus` les compte (issue #5007).

    Le filtre `EXEMPTES` vit ICI et non dans `suspects()` : un fichier exempte n est pas lu, donc
    le compter gonflerait une population que ce garde ne regarde pas.

    Le defaut n est pas `None` mais `TESTS_ANCRES`, et il le reste. C est l une des trois
    divergences que la PR #5040 a documentees apres avoir casse trois gardes en les traitant par
    motif ; la respecter est moins couteux que de l uniformiser.
    """
    return [f for f in sorted(racine.rglob("*.java")) if f.name not in EXEMPTES]


def suspects(racine: pathlib.Path = TESTS_ANCRES) -> list[str]:
    """Les clics dont l argument est un noeud DEJA RESOLU, une entree par site."""
    trouves = []
    for fichier in fichiers(racine):
        texte = fichier.read_text(encoding="utf-8")
        selecteurs = set(CONSTANTE.findall(texte))
        for rang, ligne in enumerate(texte.splitlines(), 1):
            if COMMENTAIRE.match(ligne.strip()):
                continue
            trouve = APPEL.search(ligne)
            if not trouve:
                continue
            # Le PREMIER argument decide, et lui seul : `clickOn(carte, MouseButton.SECONDARY)`
            # tient une reference tout autant. Une premiere ecriture excluait la ligne entiere des
            # qu elle citait `MouseButton`, et ecartait donc un vrai site.
            argument = premierArgument(trouve.group(1))
            if argument.startswith('"'):
                continue  # un selecteur litteral se resout AU MOMENT du clic
            jeton = JETON.match(argument)
            if jeton and jeton.group(0) in selecteurs:
                continue  # une constante String est un selecteur sous un autre nom
            trouves.append(f"{fichier.name}:{rang}")
    return trouves


def _autoTest() -> int:
    import tempfile

    cas = []
    with tempfile.TemporaryDirectory() as brut:
        r = pathlib.Path(brut)

        # Le defaut : un noeud resolu plus haut, puis clique.
        (r / "A.java").write_text(
            'class A {\n    void cas() {\n        HBox carte = robot.lookup(".c").query();\n'
            "        robot.clickOn(carte);\n    }\n}\n",
            encoding="utf-8",
        )
        cas.append(("un noeud deja resolu est vu", suspects(r) == ["A.java:4"]))

        # NEGATIF 1 : un selecteur litteral se RESOUT au moment du clic.
        (r / "B.java").write_text(
            'class B {\n    void cas() {\n        robot.clickOn("#champCode");\n    }\n}\n',
            encoding="utf-8",
        )
        cas.append(("un selecteur litteral ne compte pas", "B.java:3" not in suspects(r)))

        # NEGATIF 2 : une constante String est un selecteur sous un autre nom. Elle a fait
        # surcompter trois sites lors de la premiere mesure de #4804.
        (r / "C.java").write_text(
            'class C {\n    static final String BOUTON = "#boutonExporter";\n    void cas() {\n'
            "        robot.clickOn(BOUTON);\n    }\n}\n",
            encoding="utf-8",
        )
        cas.append(("une constante String ne compte pas", "C.java:4" not in suspects(r)))

        # NEGATIF 3 : une CITATION en commentaire n est pas un appel. Elle en a fait surcompter un.
        (r / "D.java").write_text(
            "class D {\n    /// `clickOn(libelle)` teleporte le pointeur et clique dans la foulee.\n"
            "    void cas() {}\n}\n",
            encoding="utf-8",
        )
        cas.append(("une citation en commentaire ne compte pas", "D.java:2" not in suspects(r)))

        # L aide partagee est exemptee : elle EST le geste commun sur un noeud en main.
        (r / "GesteVisible.java").write_text(
            "class GesteVisible {\n    static void cliquer(FxRobot robot, Node cible) {\n"
            "        robot.clickOn(cible);\n    }\n}\n",
            encoding="utf-8",
        )
        cas.append(
            (
                "l aide partagee est exemptee",
                not any(s.startswith("GesteVisible") for s in suspects(r)),
            )
        )

        # LE temoin qui a manque au premier jet : un clic sur noeud AVEC un second argument. Une
        # premiere ecriture ecartait la ligne des qu elle citait `MouseButton`, et ratait donc un
        # vrai site. Seul le PREMIER argument decide.
        (r / "E.java").write_text(
            "class E {\n    void cas() {\n        robot.clickOn(carte, MouseButton.SECONDARY);\n    }\n}\n",
            encoding="utf-8",
        )
        cas.append(("un second argument ne soustrait pas le site", "E.java:3" in suspects(r)))

        # Et le compte tient sur plusieurs sites d un meme fichier.
        (r / "A.java").write_text(
            "class A {\n    void cas() {\n        robot.clickOn(carte);\n"
            "        robot.clickOn(autre);\n    }\n}\n",
            encoding="utf-8",
        )
        # L assertion porte sur le SEUL fichier vise : les cas partagent un dossier jetable, et
        # comparer la liste entiere ferait rougir ce temoin des qu un voisin s ajoute.
        cas.append(
            (
                "deux sites d un meme fichier comptent deux fois",
                [s for s in suspects(r) if s.startswith("A.java")] == ["A.java:3", "A.java:4"],
            )
        )

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(
            f"\n{len(rates)} cas en échec : le cliquet ne tient pas ce qu'il annonce.",
            file=sys.stderr,
        )
        return 1
    print(
        f"\n{len(cas)} cas : il voit un noeud tenu, et écarte les trois formes qui font surcompter."
    )
    return 0


CONTRAT = {
    "geste": "clic tenant une reference entre la resolution et le geste",
    "population": "TESTS",
    "dispositif": "cliquet",
    "seuil": "38, polarite=descend",
    "temoin": "scripts/adr/5068-clic-sur-reference-tenue.py --auto-test",
    "decision": "ADR 5068",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_autoTest())
    sys.exit(
        rapporte(
            ADR,
            "clics tenant une référence entre la résolution et le geste",
            suspects(),
            lus=len(fichiers()),
        )
    )

#!/usr/bin/env python3
"""Refus d une classe de test qui monte le toolkit JavaFX sans porter l espion du fil (ADR 6142).

**Ce qui a ouvert ce garde.** Une tache de fond du produit ecrivait le champ du dossier de l ecran
d import depuis son propre fil (#6138). Tous les bancs de l import la jouaient a chaque passe, et
aucun ne l a vue : JavaFX ne verifie pas le fil d une ecriture de propriete. L extension
`SansEcritureHorsDuFil` la releve desormais et fait rougir le cas. Mais elle ne juge que les
classes qui la portent, et une classe neuve nait sans elle : ce garde tient la population.

**La population se lit dans l annotation, par l arbre.** Une classe est a toolkit quand un
`@ExtendWith` y nomme `ApplicationExtension`. Elle est suspecte quand ce meme `@ExtendWith` ne
nomme pas `SansEcritureHorsDuFil`. Les deux noms se lisent dans les arguments de l annotation et
non dans le texte du fichier : une javadoc qui cite l un ou l autre n annote rien.

**Une liste FERMEE d exemptes, a un nom.** `SansEcritureHorsDuFilTest` est l auto-test de l
extension : il ouvre et ferme le releve lui-meme, pour lire ce qui a ete vu au lieu de rougir. En
ajouter une est une decision : elle se prend dans l ADR, pas ici.

**Ce qu il ne lit pas, et il le dit.**

- Un toolkit demarre autrement que par `ApplicationExtension` (`Platform.startup`, un appel direct
  a `FxToolkit`) : la classe n est pas dans la population. Deux classes le font au 2026-10-07,
  `ButoirsTestFxTest` et `BandeauAnnonceTest`.
- Une extension heritee d une classe mere ou portee par une meta-annotation : le depot n en a pas,
  et la premiere fera rougir ce garde a tort, ce qui se verra.
- Il ne dit rien de ce que l espion VOIT. Une classe annotee dont aucun cas ne joue la tache fautive
  reste verte, et c est la limite que l ADR declare.
- L extension s eteint d elle-meme pendant une seance filmee, la ou `recette.film` ou
  `recette.reperes` est posee : c est `SansEcritureHorsDuFilEteintTest` qui le tient.
"""

from __future__ import annotations

import contextlib
import io
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import RACINE_DEPOT, TESTS, cas_d_auto_test, rapporte, sort_si_contrat_demande
from _commun.arbre import arbre, fichiers_java, noeuds_de_type

ADR = "6142"

ANNOTATION = "ExtendWith"
TOOLKIT = "ApplicationExtension"
ESPION = "SansEcritureHorsDuFil"

# Les SEULES classes a toolkit dispensees de l espion. En ajouter une est une decision de l ADR.
EXEMPTES = frozenset({"SansEcritureHorsDuFilTest.java"})


def _nom(annotation) -> str:
    """Le nom simple d une annotation : `org.x.ExtendWith` rend `ExtendWith`."""
    nom = annotation.child_by_field_name("name")
    return "" if nom is None else nom.text.decode("utf-8", "replace").rsplit(".", 1)[-1]


def _extensions(annotation) -> set[str]:
    """Les noms simples des classes qu un `@ExtendWith` nomme, lus dans ses arguments."""
    arguments = annotation.child_by_field_name("arguments")
    if arguments is None:
        return set()
    nommees = set()
    for litteral in noeuds_de_type(arguments, ["class_literal"]):
        texte = litteral.text.decode("utf-8", "replace")
        nommees.add(texte.removesuffix(".class").strip().rsplit(".", 1)[-1])
    return nommees


def sites(source: str) -> list[int]:
    """Les lignes des `@ExtendWith` qui montent le toolkit sans l espion. Porte de l auto-test."""
    racine_ast = arbre(source.encode("utf-8")).root_node
    lignes = []
    for annotation in noeuds_de_type(racine_ast, ["annotation"]):
        if _nom(annotation) != ANNOTATION:
            continue
        nommees = _extensions(annotation)
        if TOOLKIT in nommees and ESPION not in nommees:
            lignes.append(annotation.start_point[0] + 1)
    return sorted(lignes)


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les sources que ce garde LIT, extraites pour que `lus` les compte (ADR 5007)."""
    base = racine or RACINE_DEPOT
    return fichiers_java([base / TESTS])


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Les classes a toolkit sans espion sous `racine`, par leur chemin et leur ligne.

    La racine s INJECTE parce que `verifie_scripts.py` exerce le garde sur un arbre temporaire.
    """
    base = racine or RACINE_DEPOT
    retenus = []
    for fichier in fichiers(racine):
        if fichier.name in EXEMPTES:
            continue
        ou = fichier.relative_to(base).as_posix()
        for ligne in sites(fichier.read_text(encoding="utf-8", errors="replace")):
            retenus.append(f"{ou}:{ligne}  @{ANNOTATION} monte le toolkit sans {ESPION}")
    return retenus


def lus(racine: pathlib.Path | None = None) -> int:
    """Le nombre de sources lues : ce que le garde a REGARDE."""
    return len(fichiers(racine))


def _code_de_main(faux_suspects: list[str], faux_lus: int) -> int:
    """Le code que `main` rend pour un corpus donne, sa sortie retenue.

    Le chemin de REFUS se joue ici : tous les autres cas eprouvent le calcul, et un `main` qui
    calculerait juste puis sortirait en 0 les passerait tous.
    """
    sauve = (globals()["suspects"], globals()["lus"], sys.argv)
    globals()["suspects"], globals()["lus"] = (lambda: faux_suspects), (lambda: faux_lus)
    sys.argv = [__file__]
    try:
        tampon = io.StringIO()
        with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
            return main()
    finally:
        globals()["suspects"], globals()["lus"], sys.argv = sauve


def _auto_test() -> int:
    verifie, echecs = cas_d_auto_test()
    print("Auto-test du banc a toolkit sans espion du fil (#6142) :")

    def classe(annotation: str, javadoc: str = "") -> str:
        return f"{javadoc}{annotation}\nclass UnBancTest {{}}\n"

    # LES DEUX FORMES DU DEPOT avant ce lot : l extension seule, et le tableau sans l espion.
    verifie(
        "le toolkit seul est vu",
        lambda: sites(classe("@ExtendWith(ApplicationExtension.class)")),
        [1],
    )
    verifie(
        "le toolkit dans un tableau sans l espion est vu",
        lambda: sites(
            classe("@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class})")
        ),
        [1],
    )

    # LA FORME JUSTE, dans les deux ordres : sans ces cas, un garde qui refuserait toute classe a
    # toolkit passerait ceux d au-dessus, et rougirait sur tout le depot.
    verifie(
        "le toolkit avec l espion est admis",
        lambda: sites(
            classe("@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})")
        ),
        [],
    )
    verifie(
        "l ordre des extensions ne compte pas",
        lambda: sites(
            classe("@ExtendWith({SansEcritureHorsDuFil.class, ApplicationExtension.class})")
        ),
        [],
    )
    verifie(
        "les noms qualifies sont reconnus des deux cotes",
        lambda: sites(
            classe(
                "@org.junit.jupiter.api.extension.ExtendWith({"
                "org.testfx.framework.junit5.ApplicationExtension.class, "
                "fr.univ_amu.iut.recette.SansEcritureHorsDuFil.class})"
            )
        ),
        [],
    )
    verifie(
        "le nom qualifie du toolkit seul est vu",
        lambda: sites(
            classe("@ExtendWith(org.testfx.framework.junit5.ApplicationExtension.class)")
        ),
        [1],
    )

    # HORS POPULATION : une classe sans toolkit n a rien a porter.
    verifie(
        "une classe sans toolkit n est pas lue",
        lambda: sites(classe("@ExtendWith(MockitoExtension.class)")),
        [],
    )
    verifie("une classe sans extension non plus", lambda: sites(classe("")), [])

    # L ARBRE, ET NON LE TEXTE. Une javadoc qui cite l espion n annote rien : un motif textuel
    # innocenterait cette classe, et c est la raison d etre de la lecture par la structure.
    verifie(
        "l espion cite en javadoc ne vaut pas annotation",
        lambda: sites(
            classe(
                "@ExtendWith(ApplicationExtension.class)",
                "/// Ce banc devrait porter SansEcritureHorsDuFil.class, et ne le porte pas.\n",
            )
        ),
        [2],
    )
    verifie(
        "le toolkit cite en javadoc ne fait pas entrer la classe dans la population",
        lambda: sites(
            classe(
                "@ExtendWith(MockitoExtension.class)",
                "/// Sans ApplicationExtension.class : aucune fenetre n est montee.\n",
            )
        ),
        [],
    )

    # DEUX ANNOTATIONS SEPAREES ne se completent pas : l espion doit etre dans celle du toolkit, ce
    # qui garde l ordre d appel lisible a un seul endroit.
    verifie(
        "l espion dans une autre annotation ne couvre pas celle du toolkit",
        lambda: sites(
            "@ExtendWith(ApplicationExtension.class)\n"
            "@ExtendWith(SansEcritureHorsDuFil.class)\n"
            "class UnBancTest {}\n"
        ),
        [1],
    )

    # UNE CLASSE IMBRIQUEE compte pour elle-meme, a sa ligne.
    verifie(
        "une classe imbriquee sans l espion est vue a sa ligne",
        lambda: sites(
            "@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})\n"
            "class UnBancTest {\n"
            "    @ExtendWith(ApplicationExtension.class)\n"
            "    class Dedans {}\n"
            "}\n"
        ),
        [3],
    )

    # LE REFUS LUI-MEME, et non plus le calcul : un seul suspect fait sortir en 1, sans marge.
    verifie(
        "un seul suspect fait REFUSER le garde",
        lambda: _code_de_main(["src/test/java/A.java:1  @ExtendWith monte le toolkit"], 3),
        1,
    )
    verifie("aucun suspect le laisse passer", lambda: _code_de_main([], 3), 0)
    verifie("un corpus vide REFUSE au lieu de rendre zero", lambda: _code_de_main([], 0), 1)

    if echecs():
        print("auto-test : AU MOINS UN CAS A ROUGI")
        return 1
    print("auto-test : tous les cas sont verts")
    return 0


CONTRAT = {
    "geste": "classe de test a toolkit JavaFX sans l espion du fil",
    "population": "TESTS : les `@ExtendWith` de `src/test/java` qui nomment `ApplicationExtension`, "
    "lus par l arbre syntaxique. Un `@ExtendWith` est suspect quand il ne nomme pas "
    "`SansEcritureHorsDuFil` dans la MEME annotation. Une exempte, dans une liste fermee : "
    "`SansEcritureHorsDuFilTest`, l auto-test de l extension. LIMITES DECLAREES : un toolkit "
    "demarre sans `ApplicationExtension` n entre pas dans la population, et le garde ne dit rien "
    "de ce que l espion voit une fois porte",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/adr/6142-banc-a-toolkit-sans-espion.py --auto-test",
    "decision": "ADR 6142",
    # Lire par l arbre coute. Declarer les chemins rend le garde indolore sur toute demande qui ne
    # touche pas de Java de test (ADR 5340). Un `chemins` INCOMPLET tait le garde en silence.
    "chemins": """
src/test/java/**
scripts/_commun/**
dev-docs/decisions/6142-*.md
""",
}


def main() -> int:
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        return _auto_test()
    return rapporte(
        ADR,
        "classe de test a toolkit JavaFX sans l espion du fil",
        suspects(),
        apercu=12,
        lus=lus(),
    )


if __name__ == "__main__":
    raise SystemExit(main())

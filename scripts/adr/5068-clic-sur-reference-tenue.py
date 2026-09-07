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

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import TESTS_ANCRES, rapporte, sort_si_contrat_demande
from _commun.arbre import LecteurAbsent, arbre, arguments, noeuds_de_type, zones_illisibles

ADR = "5068"

# L aide partagee porte l unique clic sur noeud qui soit delibere : elle EST le geste commun.
EXEMPTES = {"GesteVisible.java"}

GESTE = "clickOn"

# Le nom d une constante de selecteur : MAJUSCULES, comme la regle du depot les ecrit. Le critere
# est celui d avant #5430, repose a la structure et non au texte, pour que le compte ne bouge pas.
NOM_DE_CONSTANTE = re.compile(r"^[A-Z_][A-Z0-9_]*$")


def estUnSelecteur(argument, selecteurs: set[str]) -> bool:
    """Cet argument produit-il une CHAINE, donc un selecteur resolu au moment du clic ?

    Trois formes, et la deuxieme est celle qui a failli disparaitre en migrant. Le depot ecrit
    massivement `clickOn("#" + ContenuDesignation.ID_VALIDER)` : une CONCATENATION, dont le noeud
    est une `binary_expression` et non un `string_literal`. La version d avant #5430 l acceptait
    sans le savoir, en testant si le TEXTE de l argument commence par un guillemet ; poser le
    critere sur le seul type de noeud faisait remonter neuf sites d un coup, tous faux.

    Le critere par la structure est plus juste que celui qu il remplace : `ID_VALIDER + "#"`,
    l ordre inverse, est un selecteur tout autant, et `startswith` ne le voyait pas.
    """
    if argument.type == "string_literal":
        return True
    if argument.type == "identifier" and argument.text.decode() in selecteurs:
        return True
    if argument.type == "binary_expression":
        return any(estUnSelecteur(e, selecteurs) for e in argument.named_children)
    return False


def selecteursDeclares(racine_ast) -> set[str]:
    """Les constantes `String` du fichier, qui sont des selecteurs sous un autre nom.

    Lues par la STRUCTURE : la version d avant cherchait `String NOM = "` dans le texte entier,
    donc une chaine qui contient ces caracteres declarait un selecteur fantome.
    """
    noms = set()
    for declaration in noeuds_de_type(racine_ast, {"variable_declarator"}):
        nom = declaration.child_by_field_name("name")
        valeur = declaration.child_by_field_name("value")
        if nom is None or valeur is None or valeur.type != "string_literal":
            continue
        if NOM_DE_CONSTANTE.match(nom.text.decode()):
            noms.add(nom.text.decode())
    return noms


def fichiers(racine: pathlib.Path = TESTS_ANCRES) -> list[pathlib.Path]:
    """Les unites que ce garde LIT, extraites pour que `lus` les compte (issue #5007).

    Le filtre `EXEMPTES` vit ICI et non dans `suspects()` : un fichier exempte n est pas lu, donc
    le compter gonflerait une population que ce garde ne regarde pas.

    Le defaut n est pas `None` mais `TESTS_ANCRES`, et il le reste. C est l une des trois
    divergences que la PR #5040 a documentees apres avoir casse trois gardes en les traitant par
    motif ; la respecter est moins couteux que de l uniformiser.
    """
    return [f for f in sorted(racine.rglob("*.java")) if f.name not in EXEMPTES]


def analyse(racine: pathlib.Path = TESTS_ANCRES) -> tuple[list[str], list[str]]:
    """UNE passe sur le corpus, DEUX sorties : les suspects, et ce que la grammaire n a pas lu.

    **Trois defauts de la lecture par motif, mesures sur temoin le 2026-09-07**, et le troisieme
    est le plus couteux parce qu il ne se voit pas dans un compte :

        String aide = "appelez robot.clickOn(carte) apres resolution";   -> comptait
        robot.clickOn(nommer("Fiche, nuit du 22/04"));                   -> comptait
        robot.clickOn(
                carteDejaResolue);                                       -> comptait, A L AVEUGLE

    La chaine n est pas un appel. `premierArgument` equilibrait les parentheses sans voir les
    chaines, donc il coupait a la virgule DE LA CHAINE et jugeait sur `nommer("Fiche`. Et sur un
    appel ecrit sur deux lignes, le motif `[^\n]*` s arretait a la fin de ligne : l argument lu
    etait VIDE, ne commencait par aucun guillemet, ne portait aucun jeton connu, et le site etait
    retenu par defaut. Il se trouve qu il etait vrai - retenu pour la mauvaise raison, ce qui ne
    prouve rien (ADR 4918).

    L arbre rend l argument entier quelle que soit sa mise en page, et une chaine n y est pas un
    appel.
    """
    trouves, zones_dites = [], []
    for fichier in fichiers(racine):
        racine_ast = arbre(fichier.read_bytes()).root_node
        zones_dites += [f"{fichier.name}:{d}-{b}" for d, b in zones_illisibles(racine_ast)]
        selecteurs = selecteursDeclares(racine_ast)
        for appel in noeuds_de_type(racine_ast, {"method_invocation"}):
            nom = appel.child_by_field_name("name")
            if nom is None or nom.text.decode() != GESTE:
                continue
            passes = arguments(appel)
            if not passes:
                continue
            # Le PREMIER argument decide, et lui seul : `clickOn(carte, MouseButton.SECONDARY)`
            # tient une reference tout autant. Une premiere ecriture excluait la ligne entiere des
            # qu elle citait `MouseButton`, et ecartait donc un vrai site.
            if estUnSelecteur(passes[0], selecteurs):
                continue  # un selecteur se resout AU MOMENT du clic, il ne tient rien
            trouves.append(f"{fichier.name}:{appel.start_point[0] + 1}")
    return trouves, zones_dites


def suspects(racine: pathlib.Path = TESTS_ANCRES) -> list[str]:
    """Les clics dont l argument est un noeud DEJA RESOLU, une entree par site."""
    return analyse(racine)[0]


def non_lus(racine: pathlib.Path = TESTS_ANCRES) -> list[str]:
    """Les zones qu aucune grammaire n a su lire, nommees pour etre DITES et non reparees."""
    return analyse(racine)[1]


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

        # LA CONCATENATION, et c est le cas le plus important du lot. Le depot ecrit massivement
        # `clickOn("#" + CONSTANTE)` : le noeud est une `binary_expression`, pas un `string_literal`.
        # Poser le critere sur le seul type faisait remonter NEUF sites d un coup, tous faux, et le
        # compte passait de 38 a 47 sans qu aucun cas ne rougisse. Ce cas-ci le verrouille.
        (r / "P.java").write_text(
            'class P {\n    void cas() {\n        robot.clickOn("#" + ID_VALIDER);\n    }\n}\n',
            encoding="utf-8",
        )
        cas.append(("une concatenation de selecteur ne compte pas", "P.java:3" not in suspects(r)))

        # ET DANS L AUTRE ORDRE, que la lecture par `startswith` ne voyait pas : elle testait le
        # debut du TEXTE, donc `ID + "#"` lui echappait. La structure ne depend pas de l ordre.
        (r / "Q.java").write_text(
            'class Q {\n    void cas() {\n        robot.clickOn(ID_VALIDER + "#");\n    }\n}\n',
            encoding="utf-8",
        )
        cas.append(("dans l autre ordre non plus", "Q.java:3" not in suspects(r)))

        # Une CHAINE qui cite l appel n est pas un appel.
        (r / "R.java").write_text(
            "class R {\n    void cas() {\n"
            '        String aide = "appelez robot.clickOn(carte) apres resolution";\n'
            "    }\n}\n",
            encoding="utf-8",
        )
        cas.append(
            ("une chaine qui cite l appel n est pas un appel", "R.java:3" not in suspects(r))
        )

        # UN APPEL SUR DEUX LIGNES. La lecture par motif s arretait a la fin de ligne, lisait un
        # argument VIDE, et retenait le site par defaut : vrai, mais pour la mauvaise raison, ce
        # qui ne prouve rien (ADR 4918). Ici il est retenu parce que son argument a ete LU.
        (r / "S.java").write_text(
            "class S {\n    void cas() {\n        robot.clickOn(\n"
            "                carteDejaResolue);\n    }\n}\n",
            encoding="utf-8",
        )
        cas.append(("un appel sur deux lignes est lu entier", "S.java:3" in suspects(r)))

        # ET LE CONTRASTE : un selecteur ecrit sur deux lignes ne compte toujours pas. Sans lui,
        # les trois negations ci-dessus passeraient sur un garde devenu muet.
        (r / "T.java").write_text(
            "class T {\n    void cas() {\n        robot.clickOn(\n"
            '                "#" + ID_VALIDER);\n    }\n}\n',
            encoding="utf-8",
        )
        cas.append(("et un selecteur sur deux lignes ne compte pas", "T.java:3" not in suspects(r)))

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
    # Lire par l arbre coute, et #5400 retire du temps a la batterie. Declarer les chemins rend la
    # hausse indolore sur toute demande qui ne touche pas de Java (ADR 5340). Un `chemins`
    # INCOMPLET tait le garde en silence, la ou son absence le fait LANCER : les quatre lignes
    # couvrent la population, ce fichier meme, le fonds dont il delegue sa lecture, et la ligne
    # `ratchet:` de sa propre decision.
    "chemins": """
src/test/java/**
scripts/adr/5068-clic-sur-reference-tenue.py
scripts/_commun/**
dev-docs/decisions/5068-une-dette-assumee-se-compte.md
""",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_autoTest())
    # UNE passe, dont sortent le verdict et ce que la grammaire n a pas su lire. Ce dernier se DIT
    # sur la sortie d erreur : le jour ou il ne sera plus nul, le silence serait un faux vert.
    try:
        listes, zones = analyse()
    except LecteurAbsent as absent:
        # Un REFUS, pas une trace : une `ModuleNotFoundError` nue ressemble a un defaut du
        # changement en cours. Le message dit ce qui manque ET quoi faire.
        raise SystemExit(str(absent)) from absent
    for zone in zones:
        print(f"zone non lue par la grammaire : {zone}", file=sys.stderr)
    sys.exit(
        rapporte(
            ADR,
            "clics tenant une référence entre la résolution et le geste",
            listes,
            lus=len(fichiers()),
        )
    )

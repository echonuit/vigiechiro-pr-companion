#!/usr/bin/env python3
"""Un geste du pointeur dont la CIBLE est resolue hors du fil JavaFX (ADR 5707).

`robot.moveTo("#id")` et `robot.clickOn(noeud)` ne font pas que bouger le pointeur : ils SITUENT
leur cible, et ils le font sur le fil APPELANT. `FxRobot.pointOfVisibleNode` filtre les candidats par
`NodeQueryUtils.isNodeVisible`, dont l octet-code dit exactement ce qu il lit :

    Node.getScene -> Node.getBoundsInLocal -> Node.localToScene -> Scene.getWidth/getHeight

Lire des bornes recalcule la geometrie du sous-arbre, donc ITERE les elements de tout `Path` qui s y
trouve. Le fil JavaFX, pendant ce temps, rebatit ces elements quand il le doit - le caret d un champ
de saisie en est un, reconstruit a chaque clignotement. Les deux se croisent, et le banc meurt d une
`ConcurrentModificationException` dont la pile est entierement dans JavaFX.

Mesure : UNE fois sur 558 tirages de trente jours, sur `SelecteurFichierEnFenetreTest` (#5707).

POURQUOI UN GARDE STATIQUE, ET PAS UN BANC. Deux bancs ont ete ecrits et jetes avant celui-ci.

Le premier reproduisait la course : un `Path` de deux mille elements rebati a chaque trame pendant
que quarante gestes resolvaient un selecteur. VERT sur le code fautif. La fenetre de collision est la
duree d une iteration, quelques dixiemes de milliseconde contre seize entre deux trames.

Le second mesurait la CAUSE plutot que le symptome : `visibleProperty` liee a une liaison qui note le
fil appelant. VERT trois fois sur trois, et pour une raison qui condamne la famille entiere - le fil
JavaFX rend a chaque trame, donc il recalcule la liaison le premier et SERT SON CACHE au fil du test.
Toute propriete lue par les deux fils est mise en cache par celui qui rend. Aucun temoin Java d API
publique ne peut donc voir ce fil, et c est pourquoi la regle se tient par un cliquet.
"""

import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import TESTS_ANCRES, cas_d_auto_test, rapporte, sort_si_contrat_demande
from _commun.arbre import arbre, noeuds_de_type, zones_illisibles

# Les gestes qui SITUENT leur cible. `press` et `release` n y sont pas : ils agissent la ou le
# pointeur est deja, sans rien resoudre, et les compter accuserait un geste sain.
GESTES = frozenset({"moveTo", "clickOn", "doubleClickOn", "rightClickOn", "drag", "dropTo"})

# Un premier argument qui n est PAS une cible : `clickOn(MouseButton.PRIMARY)` clique la ou le
# pointeur se trouve deja. `KeyCode` n y figure PAS : aucun geste du pointeur n en prend, et une
# branche qu aucun cas ne peut exercer est une branche qui ne tient rien.
HORS_CIBLE = re.compile(r"^MouseButton\s*\.")

# La forme JUSTE : la cible a ete situee SUR LE FIL, et le geste ne recoit plus qu un point d ecran.
# Elle reste dans la population pour que l auto-test puisse montrer qu elle en SORT : un detecteur
# qui ne verrait jamais la forme juste ne prouverait pas qu il la distingue.
SUR_LE_FIL = "pointSurLeFil("


def appels_de_pointeur(racine_ast) -> list:
    """Les appels de geste du pointeur qui SITUENT une cible, fautifs ou non.

    C est la population : ce que le garde a REGARDE. La rendre a part de ce qu il RETIENT est ce que
    l ADR 4002 demande, et ce qui distingue un ciblage manque d un depot sain.
    """
    vus = []
    for appel in noeuds_de_type(racine_ast, ["method_invocation"]):
        nom = appel.child_by_field_name("name")
        if nom is None or nom.text.decode() not in GESTES:
            continue
        arguments = appel.child_by_field_name("arguments")
        premiers = list(arguments.named_children) if arguments is not None else []
        # `clickOn()` nu agit la ou le pointeur est : aucune cible a situer.
        if not premiers:
            continue
        if HORS_CIBLE.match(premiers[0].text.decode()):
            continue
        vus.append((appel, premiers[0]))
    return vus


def sites_de(racine_ast) -> list[int]:
    """Les lignes des gestes dont la cible est situee HORS du fil, depuis un ARBRE deja lu."""
    return [
        appel.start_point[0] + 1
        for appel, cible in appels_de_pointeur(racine_ast)
        if SUR_LE_FIL not in cible.text.decode()
    ]


def sites(source: str) -> list[int]:
    """La meme, depuis une SOURCE. C est la porte de l auto-test."""
    return sites_de(arbre(source.encode()).root_node)


def lus_de(source: str) -> int:
    """Ce que le garde a regarde dans une SOURCE, pour que l auto-test tienne les DEUX nombres."""
    return len(appels_de_pointeur(arbre(source.encode()).root_node))


def analyse(racine: pathlib.Path | None = None) -> tuple[list[str], int, list[str]]:
    """UNE passe sur le corpus, TROIS sorties : les suspects, les appels lus, les zones illisibles.

    La racine s INJECTE parce que `verifie_scripts.py` exerce les detecteurs sur un arbre
    temporaire : un detecteur qui ne lit qu un chemin fixe n est tenu que par son cliquet.
    """
    racine = TESTS_ANCRES if racine is None else racine
    fautifs, combien, zones_dites = [], 0, []
    for fichier in sorted(racine.rglob("*.java")):
        racine_ast = arbre(fichier.read_bytes()).root_node
        ou = fichier.relative_to(racine)
        zones_dites += [f"{ou}:{d}-{b}" for d, b in zones_illisibles(racine_ast)]
        combien += len(appels_de_pointeur(racine_ast))
        fautifs += [f"{ou}:{ligne}" for ligne in sites_de(racine_ast)]
    return fautifs, combien, zones_dites


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Les sites fautifs sous `racine`, nommes par leur chemin et leur ligne."""
    return analyse(racine)[0]


def lus(racine: pathlib.Path | None = None) -> int:
    """Le nombre de gestes du pointeur lus : ce que le garde a REGARDE."""
    return analyse(racine)[1]


def _auto_test() -> int:
    verifie, echecs = cas_d_auto_test()
    print("Auto-test du geste du pointeur hors du fil (#5707) :")

    litteral = 'class T { void f() { robot.clickOn("#valider"); } }\n'
    verifie("un selecteur LITTERAL est vu", lambda: sites(litteral), [1])

    juste = 'class T { void f() { robot.clickOn(pointSurLeFil(robot, "#valider")); } }\n'
    verifie("le MEME geste, situe sur le fil, ne l est plus", lambda: sites(juste), [])

    # LA POPULATION, a part de ce qui est retenu : la forme juste doit RESTER lue, sans quoi le
    # garde ne prouverait pas qu il la distingue - il pourrait simplement ne pas la voir.
    verifie("et la forme juste reste DANS la population", lambda: lus_de(juste), 1)

    # LES CINQ FORMES REELLES DU DEPOT, mesurees a l arbre le 2026-10-01 sur 170 appels : 110
    # selecteurs litteraux, 30 identifiants, 21 `lookup` imbriques, 7 concatenations, 1 transtypage.
    # Un grep sur `clickOn("` n en voyait que 110, et c est le compte que j avais d abord annonce.
    identifiant = "class T { void f() { robot.clickOn(BOUTON_EXPORTER); } }\n"
    verifie("un identifiant est vu", lambda: sites(identifiant), [1])

    imbrique = 'class T { void f() { robot.clickOn(robot.lookup(".c").queryAs(Case.class)); } }\n'
    verifie("un lookup imbrique est vu", lambda: sites(imbrique), [1])

    concatene = 'class T { void f() { robot.clickOn("#" + ID_RESTAURER); } }\n'
    verifie("une concatenation est vue", lambda: sites(concatene), [1])

    transtype = 'class T { void f() { robot.clickOn((Node) robot.lookup(".s").query()); } }\n'
    verifie("un transtypage est vu", lambda: sites(transtype), [1])

    # LE SENS NEGATIF, sans quoi un motif qui accepterait tout passerait les cas ci-dessus et
    # rendrait le garde vert sur un depot entierement fautif.
    bouton = "class T { void f() { robot.clickOn(MouseButton.PRIMARY); } }\n"
    verifie("un clic sans cible n est pas vu", lambda: sites(bouton), [])
    verifie("et il n entre pas dans la population", lambda: lus_de(bouton), 0)

    touche = "class T { void f() { robot.press(KeyCode.ESCAPE); } }\n"
    verifie("une touche n est pas un geste du pointeur", lambda: sites(touche), [])

    nu = "class T { void f() { robot.clickOn(); } }\n"
    verifie("un clic NU agit ou le pointeur est, et sort du compte", lambda: sites(nu), [])

    sobre = "class T { void f() { int x = 3; } }\n"
    verifie("une source sans geste ne rend rien", lambda: sites(sobre), [])
    verifie("et sa population est vide", lambda: lus_de(sobre), 0)

    # UN GESTE CITE EN DOC-COMMENT n est pas un appel. Le motif textuel que ce garde remplace en
    # comptait, parce que la javadoc de `GesteVisible.cliquer` cite `clickOn` pour l expliquer.
    cite = 'class T {\n /// `robot.clickOn("#x")` teleporte le pointeur.\n void f() { } }\n'
    verifie("un geste cite en doc-comment n est pas lu", lambda: sites(cite), [])
    verifie("et il n entre pas dans la population", lambda: lus_de(cite), 0)

    # PLUSIEURS SITES, et leurs lignes : un detecteur qui rendrait toujours [1] passerait tous les
    # cas ci-dessus.
    plusieurs = (
        "class T {\n"
        '  void f() { robot.clickOn("#a"); }\n'
        "  void g() { robot.clickOn(MouseButton.PRIMARY); }\n"
        '  void h() { robot.moveTo("#b"); }\n'
        "}\n"
    )
    verifie("plusieurs sites sont tous vus, aux bonnes lignes", lambda: sites(plusieurs), [2, 4])
    verifie("et la population n en compte que deux", lambda: lus_de(plusieurs), 2)

    if echecs():
        print("auto-test : AU MOINS UN CAS A ROUGI")
        return 1
    print("auto-test : tous les cas sont verts")
    return 0


CONTRAT = {
    "geste": "geste du pointeur dont la CIBLE est resolue hors du fil JavaFX",
    "population": "les appels a `moveTo`, `clickOn`, `doubleClickOn`, `rightClickOn`, `drag` et "
    "`dropTo` de src/test/java qui SITUENT une cible, l argument etant delimite par la STRUCTURE. "
    "`press` et `release` en sortent : ils agissent ou le pointeur est deja. Un premier argument "
    "`MouseButton.*` ou `KeyCode.*` en sort aussi, et un appel NU `clickOn()` egalement. La forme "
    "JUSTE, `pointSurLeFil(...)`, reste DANS la population et hors des suspects, pour que l "
    "auto-test prouve qu elle est distinguee. LIMITE DECLAREE : un geste dont la cible est situee "
    "sur le fil par une aide d un AUTRE nom echapperait - le garde reconnait un nom, pas une "
    "propriete",
    "dispositif": "cliquet",
    "seuil": "162, polarite=descend",
    "temoin": "scripts/adr/5707-geste-du-pointeur-hors-du-fil.py --auto-test",
    "decision": "ADR 5707",
    # Lire par l arbre coute. Declarer les chemins rend la hausse indolore sur toute demande qui ne
    # touche pas de Java (ADR 5340). Un `chemins` INCOMPLET tait le garde en silence.
    "chemins": """
src/test/java/**
scripts/adr/5707-geste-du-pointeur-hors-du-fil.py
scripts/_commun/**
dev-docs/decisions/5707-un-geste-du-pointeur-situe-sa-cible-sur-le-fil.md
""",
}


def main() -> int:
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        return _auto_test()
    fautifs, combien, zones_dites = analyse()
    for zone in zones_dites:
        print(f"  zone illisible : {zone}")
    return rapporte(
        "5707",
        "geste du pointeur dont la cible est situee hors du fil JavaFX",
        fautifs,
        apercu=12,
        lus=combien,
    )


if __name__ == "__main__":
    raise SystemExit(main())

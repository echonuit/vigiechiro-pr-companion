#!/usr/bin/env python3
"""Cliquet sur les fixtures qui appellent une API POSIX sans declarer qu elles l exigent (ADR 3802).

`Files.setPosixFilePermissions` jette `UnsupportedOperationException` sur un systeme de fichiers qui
ne porte pas la vue `posix`, NTFS en tete. Quand l appel FABRIQUE la fixture, le cas meurt avant sa
premiere assertion : il ne rougit pas sur ce qu il eprouve, il rend une ERREUR sur ce qu il preparait.

## Ce que ce garde ajoute a ce qui existait

L ADR 3802 avait deja tranche - un comportement de plateforme se sonde, et le test qui reste passe
par une couture. Son `enforced_by` ne nommait que `suite-sous-windows-et-macos.yml`, qui DETECTE une
fois par semaine. Entre deux mardis, une fixture non portable se fusionne sans que rien ne la voie :
quatre l ont fait entre le 2026-08-31 et le 2026-09-06, apres onze de meme nature corrigees en aout
(#5433). Ce garde est la moitie qui EMPECHE.

## Pourquoi l arbre syntaxique, et pas un motif de ligne

Mesure d ouverture : huit fichiers de `src/test/java` citent ces API, et DEUX ne les citent qu en
commentaire - `DossierDeFixtureTest` et `SystemeDeFichiers`. Un quart de faux positifs sur une
population de huit : un motif textuel ne pourrait pas annoncer un zero qui veuille dire quelque chose.

Les deux sont de plus des `///` du JEP 467, et `tree-sitter` est le seul lecteur du depot qui les
rende fidelement : Spoon les classe en `//` et re-serialise en `// /`, JavaParser ne les voit pas
sous `getJavadoc()`.

## Les deux formes de declaration, et pourquoi les DEUX sont acceptees

`@EnabledIf` est la forme que #3778 a etablie, et elle est preferable : elle se voit dans le rapport
et ne peut pas interrompre un test au milieu. Un `assumeTrue` en TETE de methode declare pourtant la
meme exigence et protege aussi bien - le mal que #3778 nomme est celui d un `assumeTrue` pose au
MILIEU, qui emporte les assertions qui n avaient rien de POSIX.

Deux fichiers portent encore cette seconde forme, `SondeAccessibiliteTest` et `StockageConnexionTest`,
et leur `assumeTrue` est la premiere instruction de leur methode. Les refuser ferait rougir ce garde
sur des cas JUSTES, pour une preference de style que l ADR 3778 porte deja. Ce garde-ci ne juge que
l absence totale de declaration.

## Ce qu il ne voit pas, et c est declare

**Une declaration qui delegue.** Un `@EnabledIf` nommant un predicat d un autre fichier est suivi par
son NOM, jamais par son corps : un predicat qui rendrait toujours vrai passerait.

**Le second motif du chantier, celui du modificateur clavier, n a pas ete ecrit.** Sa population
illegitime est vide - deux occurrences de `KeyCode.CONTROL` dans tout l arbre de test, l une dans une
chaine de caracteres, l autre JUSTE puisque `MainViewTest` y est d accord avec `MainController` qui
code `CONTROL_DOWN` en dur. Un garde qui lirait des centaines de bancs pour rendre zero sur une regle
dont le seul cas reel doit etre exempte ne pourrait jamais etre vu rouge, et
`verifie_temoins_non_decoratifs.py` le dirait decoratif. C est la lecon de l ADR 5398 : une regle sans
population ecrit ce qu on imagine du probleme, et rien ne dement l imagination.
"""

from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import RACINE_DEPOT, rapporte, sort_si_contrat_demande
from _commun.arbre import LecteurAbsent, arbre, noeuds_de_type

ADR = "5437"

# Les appels qui JETTENT sur un systeme sans vue `posix`. `PosixFilePermissions.fromString` n y est
# pas : elle analyse une chaine et ne touche a aucun fichier, donc elle ne jette nulle part.
APPELS_QUI_JETTENT = ("setPosixFilePermissions", "getPosixFilePermissions", "asFileAttribute")

# Ce qui vaut declaration. Le nom du predicat plutot que son chemin : la couture a demenage une fois
# (#5435), et un garde qui nommerait son paquet aurait rougi sur le demenagement.
#
# `VUE_POSIX` reste accepte a cote du predicat, et ce n est pas de la complaisance : la question
# s ecrivait en CINQ endroits avant #5437, et rien ne garantit qu une sixieme copie ne renaisse pas
# hors de la couture. Un garde qui n accepterait que le nom de l aide refuserait alors une
# declaration JUSTE, ecrite a la main.
PREDICAT = "posixDisponible"
VUE_POSIX = "supportedFileAttributeViews"


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les unites que ce garde LIT, extraites pour que `lus` les compte (ADR 5007)."""
    arbre_de_test = (racine or RACINE_DEPOT) / "src" / "test" / "java"
    return sorted(arbre_de_test.rglob("*.java")) if arbre_de_test.is_dir() else []


def _englobant(noeud, types):
    """Le premier ancetre de `noeud` dont le type est dans `types`, ou None."""
    while noeud is not None:
        if noeud.type in types:
            return noeud
        noeud = noeud.parent
    return None


def _annonce_posix(noeud) -> bool:
    """Les annotations de cette declaration nomment-elles le predicat ?"""
    if noeud is None:
        return False
    for enfant in noeud.children:
        if enfant.type == "modifiers" and PREDICAT in enfant.text.decode("utf-8", "replace"):
            return True
    return False


def _garde_en_tete(methode, avant_octet: int) -> bool:
    """Un `assumeTrue` sur la vue POSIX precede-t-il l appel, dans la meme methode ?"""
    if methode is None:
        return False
    for appel in noeuds_de_type(methode, ("method_invocation",)):
        if appel.start_byte >= avant_octet:
            break
        texte = appel.text.decode("utf-8", "replace")
        if "assumeTrue" in texte and (VUE_POSIX in texte or PREDICAT in texte):
            return True
    return False


def _rattrape_le_refus(appel) -> bool:
    """L appel est-il sous un `try` dont un `catch` nomme `UnsupportedOperationException` ?

    Troisieme forme legitime, et la seule qui n eprouve RIEN de la plateforme : le code demande, et
    se replie quand le systeme refuse. `EncodeurTest#executable` la porte, avec un
    `cible.toFile().setExecutable(true)` en repli. Elle est portable par construction, donc elle n a
    aucune exigence a declarer.
    """
    noeud = appel
    while noeud is not None:
        if noeud.type == "try_statement" and "UnsupportedOperationException" in noeud.text.decode(
            "utf-8", "replace"
        ):
            return True
        noeud = noeud.parent
    return False


def _nom_de(declaration) -> str | None:
    nom = declaration.child_by_field_name("name") if declaration is not None else None
    return nom.text.decode("utf-8", "replace") if nom is not None else None


def _appelants_declarent(racine_ast, aide) -> bool:
    """Les methodes qui appellent cette aide, DANS LE MEME FICHIER, declarent-elles toutes ?

    #3778 a deliberement sorti l hypothese du helper pour la poser chez ses appelants, parce qu un
    `assumeTrue` dans une aide interrompt le test APPELANT au milieu de ses assertions. Un garde qui
    exigerait la declaration sur l aide refuserait donc la forme que le depot a choisie.

    La delegation est suivie dans le fichier seulement, comme le fait le garde 5278 depuis #5353. Une
    aide appelee depuis un AUTRE fichier echappe : c est declare dans le contrat.
    """
    nom = _nom_de(aide)
    if nom is None:
        return False
    appelants = [
        appel
        for appel in noeuds_de_type(racine_ast, ("method_invocation",))
        if _nom_de(appel) == nom and _englobant(appel, ("method_declaration",)) is not aide
    ]
    if not appelants:
        return False
    for appel in appelants:
        englobante = _englobant(appel, ("method_declaration", "constructor_declaration"))
        if englobante is aide:
            continue
        if not (
            _annonce_posix(englobante)
            or _annonce_posix(_englobant(appel, ("class_declaration",)))
            or _garde_en_tete(englobante, appel.start_byte)
        ):
            return False
    return True


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    base = racine or RACINE_DEPOT
    trouves = []
    for source in fichiers(racine):
        octets = source.read_bytes()
        racine_ast = arbre(octets).root_node
        for appel in noeuds_de_type(racine_ast, ("method_invocation",)):
            nom = appel.child_by_field_name("name")
            if nom is None or nom.text.decode("utf-8", "replace") not in APPELS_QUI_JETTENT:
                continue
            methode = _englobant(appel, ("method_declaration", "constructor_declaration"))
            classe = _englobant(appel, ("class_declaration",))
            if (
                _annonce_posix(methode)
                or _annonce_posix(classe)
                or _garde_en_tete(methode, appel.start_byte)
                or _rattrape_le_refus(appel)
                or _appelants_declarent(racine_ast, methode)
            ):
                continue
            ligne = appel.start_point[0] + 1
            trouves.append(
                f"{source.relative_to(base)}:{ligne}  {nom.text.decode()} sans exigence déclarée"
            )
    return trouves


def _sur_source(java: str) -> list[str]:
    """Les suspects d une source Java jetable, montee dans un arbre de test complet."""
    import tempfile

    with tempfile.TemporaryDirectory() as bac:
        racine = pathlib.Path(bac)
        paquet = racine / "src" / "test" / "java" / "fr" / "univ_amu" / "iut"
        paquet.mkdir(parents=True)
        (paquet / "CasTest.java").write_text(java, encoding="utf-8")
        return suspects(racine)


def _auto_test() -> int:
    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu!r}, obtenu {obtenu!r}")
            echecs = 1

    NU = """class CasTest {
    @Test
    void cas() throws IOException {
        Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("r-xr-xr-x"));
    }
}
"""
    verifie("un appel sans declaration est vu", len(_sur_source(NU)), 1)

    # LES QUATRE FORMES JUSTES. Sans elles, le garde refuserait ce que le depot a decide.
    verifie(
        "un @EnabledIf sur la methode le declare",
        len(_sur_source(NU.replace("    @Test", '    @Test\n    @EnabledIf("X#posixDisponible")'))),
        0,
    )
    verifie(
        "un @EnabledIf sur la CLASSE le declare aussi",
        len(_sur_source('@EnabledIf("X#posixDisponible")\n' + NU)),
        0,
    )
    verifie(
        "un assumeTrue en tete de methode le declare",
        len(
            _sur_source(
                NU.replace(
                    "        Files.set",
                    "        assumeTrue(SystemeDeFichiers.posixDisponible());\n        Files.set",
                )
            )
        ),
        0,
    )
    verifie(
        "un appel qui rattrape UnsupportedOperationException se garde seul",
        len(
            _sur_source(
                NU.replace(
                    '        Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("r-xr-xr-x"));',
                    "        try {\n            Files.setPosixFilePermissions(p, null);\n        } catch (UnsupportedOperationException hors) {\n            p.toFile().setExecutable(true);\n        }",
                )
            )
        ),
        0,
    )

    # LA CITATION EN COMMENTAIRE. C est le cas qui justifie l arbre plutot qu un motif de ligne : sur
    # l arbre du 2026-09-06, un grep retenait SEPT fichiers la ou le garde en retient deux.
    verifie(
        "une citation en commentaire n est pas un appel",
        len(
            _sur_source("""class CasTest {
    /// Ici, `Files.setPosixFilePermissions` jetterait sous Windows.
    // et Files.getPosixFilePermissions aussi.
    @Test
    void cas() {}
}
""")
        ),
        0,
    )
    verifie(
        "fromString seule ne touche aucun fichier",
        len(
            _sur_source(
                NU.replace(
                    'Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("r-xr-xr-x"))',
                    'var m = PosixFilePermissions.fromString("r-xr-xr-x")',
                )
            )
        ),
        0,
    )

    # LA DELEGATION, DANS LES DEUX SENS. Le second cas est le controle negatif : sans lui, une regle
    # qui accepterait toute aide rendrait le garde vert sur un depot entierement fautif.
    AIDE = """class CasTest {
    @Test
    @EnabledIf("X#posixDisponible")
    void cas() throws IOException {
        permissions(p);
    }

    private static Set<PosixFilePermission> permissions(Path chemin) throws IOException {
        return Files.getPosixFilePermissions(chemin);
    }
}
"""
    verifie("une aide dont l appelant declare est acceptee", len(_sur_source(AIDE)), 0)
    verifie(
        "la MEME aide, appelant non declare, est vue",
        len(_sur_source(AIDE.replace('    @EnabledIf("X#posixDisponible")\n', ""))),
        1,
    )

    print()
    print("Auto-test en échec." if echecs else "Auto-test concluant.")
    return echecs


CONTRAT = {
    "geste": "appel POSIX dans une fixture de test, sans exigence declaree",
    "population": "les `method_invocation` de src/test/java dont le nom est `setPosixFilePermissions`, "
    "`getPosixFilePermissions` ou `asFileAttribute`, lus par l arbre syntaxique et non par motif : "
    "deux des huit fichiers qui les citent ne le font qu en commentaire. `fromString` en est exclue, "
    "elle ne touche aucun fichier. Un `@EnabledIf` qui delegue est suivi par son NOM, pas par son "
    "corps : un predicat toujours vrai passerait, et c est declare",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/adr/5437-fixture-suppose-la-plateforme.py --auto-test",
    "decision": "ADR 5437",
    # Lire par l arbre coute. Declarer les chemins rend la hausse indolore sur toute demande qui ne
    # touche pas de Java (ADR 5340). Un `chemins` INCOMPLET tait le garde en silence.
    "chemins": """
src/test/java/**
scripts/adr/5437-fixture-suppose-la-plateforme.py
scripts/_commun/**
dev-docs/decisions/3802-un-defaut-de-plateforme-se-sonde-il-ne-se-deduit-pas.md
""",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    try:
        listes = suspects()
    except LecteurAbsent as absent:
        print(absent)
        raise SystemExit(1) from absent
    raise SystemExit(
        rapporte(
            ADR,
            "fixture qui appelle une API POSIX sans déclarer qu'elle l'exige",
            listes,
            lus=len(fichiers()),
        )
    )

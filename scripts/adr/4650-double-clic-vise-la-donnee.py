#!/usr/bin/env python3
"""Un double-clic de test vise une DONNEE, et l exemption qui vise une position se DECLARE.

Le chantier #4650 a livre `DoubleClicDeterministe` : le geste envoie l evenement a la ligne trouvee
par son contenu, au lieu de cliquer une position a l ecran. Douze classes l emploient sur seize
sites. Quatre appels positionnels subsistent, et les quatre sont legitimes - mais leur raison vivait
dans TROIS commentaires au point d appel qui ne se connaissaient pas, dont deux disaient la meme
chose, l un renvoyant a « la meme raison qu au parcours jumeau ».

Ce garde ne refuse pas l appel positionnel : il refuse l appel positionnel MUET. La frontiere reste
un jugement - la position est-elle ce que le cas eprouve ? - et c est pourquoi elle se declare au
lieu de s inferer (ADR 5398).

**Il lit l ARBRE, jamais un motif.** La javadoc du helper cite `doubleClickOn` pour dire ce qu il
imite : un `grep` la compte comme un appel, ce qui est arrive a la passe 7 de la cloture. Seul un
noeud `method_invocation` est un appel.
"""

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from _commun import (
    RACINE_DEPOT,
    TESTS_ANCRES,
    cas_d_auto_test,
    rapporte,
    sort_si_contrat_demande,
)
from _commun.arbre import LecteurAbsent, arbre, fichiers_java, noeuds_de_type

ADR = "4650"

# Le geste fragile, et le nom que l exemption doit porter pour etre lue.
CIBLE = "doubleClickOn"
EXEMPTION = "DoubleClicDeterministe"

# Le helper lui-meme cite les deux noms : il DEFINIT l alternative, il ne s en exempte pas.
HELPER = "DoubleClicDeterministe.java"

# Combien de lignes au-dessus de l appel portent son exemption. Six couvrent un commentaire de
# trois lignes plus une ligne d appel intermediaire, ce que les quatre sites reels emploient.
PORTEE = 6


def appels(source: bytes, racine_ast) -> list[int]:
    """Les lignes ou `doubleClickOn` est APPELE, jamais celles ou il est cite."""
    lignes = []
    for noeud in noeuds_de_type(racine_ast, {"method_invocation"}):
        # Le champ NOMME, et non le premier `identifier` : dans `r.doubleClickOn(d)` le premier
        # identifiant est le RECEPTEUR. La premiere ecriture de ce garde comptait zero appel, et
        # son cas « une exemption declaree ne compte pas » passait alors A VIDE (ADR 5054).
        nom = noeud.child_by_field_name("name")
        if nom is not None and nom.text.decode("utf-8", "replace") == CIBLE:
            lignes.append(noeud.start_point[0] + 1)
    return lignes


def exemption_declaree(lignes: list[str], ligne: int) -> bool:
    """Une des `PORTEE` lignes au-dessus de l appel nomme-t-elle l alternative ?"""
    debut = max(0, ligne - 1 - PORTEE)
    return any(EXEMPTION in l for l in lignes[debut : ligne - 1])


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les unites que ce garde LIT, pour que `lus` les compte (ADR 5015)."""
    return [f for f in fichiers_java([racine or TESTS_ANCRES]) if f.name != HELPER]


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Un suspect par appel positionnel dont aucune ligne voisine ne declare l exemption."""
    trouves = []
    for f in fichiers(racine):
        source = f.read_bytes()
        lignes = source.decode("utf-8", "replace").split("\n")
        for ligne in appels(source, arbre(source).root_node):
            if exemption_declaree(lignes, ligne):
                continue
            nom = f.relative_to(RACINE_DEPOT) if f.is_relative_to(RACINE_DEPOT) else f.name
            trouves.append(f"{nom}:{ligne}  appel positionnel sans exemption declaree")
    return trouves


CONTRAT = {
    "geste": "double-clic positionnel dont l exemption n est pas declaree",
    "population": "TESTS",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/adr/4650-double-clic-vise-la-donnee.py --auto-test",
    "decision": "ADR 4650",
    "chemins": """
src/test/java/**
scripts/adr/4650-double-clic-vise-la-donnee.py
scripts/_commun/**
dev-docs/decisions/4650-*.md
""",
}


def _auto_test() -> int:
    """Les deux moities, plus le faux positif que ce garde existe pour eviter.

    La seconde moitie n est pas une formalite : un garde qui refuserait TOUT passerait la premiere,
    et c est la meme cecite qu un temoin qui n affirmerait que des vides (ADR 5054).
    """
    verifie, echecs = cas_d_auto_test()

    nu = b"class T { void c(FxRobot r) { r.doubleClickOn(D); } }"
    declare = (
        b"class T { void c(FxRobot r) {\n"
        b"  // Le vrai geste, et non DoubleClicDeterministe : ce cas est filme.\n"
        b"  r.doubleClickOn(D); } }"
    )
    cite = b"/// Comme le fait doubleClickOn(String) de TestFX.\nclass T { void c() {} }"
    loin = (
        b"class T { void c(FxRobot r) {\n"
        b"  // DoubleClicDeterministe\n" + b"  int x = 0;\n" * 8 + b"  r.doubleClickOn(D); } }"
    )

    def sans_exemption(source: bytes) -> int:
        lignes = source.decode().split("\n")
        return sum(
            1
            for ligne in appels(source, arbre(source).root_node)
            if not exemption_declaree(lignes, ligne)
        )

    verifie("un appel nu est vu", sans_exemption(nu), 1)
    verifie("un appel dont l exemption est declaree ne l est pas", sans_exemption(declare), 0)
    verifie(
        "une CITATION en commentaire n est pas un appel",
        len(appels(cite, arbre(cite).root_node)),
        0,
    )
    verifie("une exemption trop loin ne couvre pas", sans_exemption(loin), 1)
    verifie("le corpus reel est non vide", len(fichiers()) > 0, True)

    print()
    print("Auto-test concluant." if not echecs() else "Auto-test EN ÉCHEC.")
    return echecs()


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    try:
        trouves = suspects()
    except LecteurAbsent as absent:
        raise SystemExit(str(absent)) from absent
    sys.exit(
        rapporte(
            ADR,
            "double-clic positionnel sans exemption déclarée",
            trouves,
            lus=len(fichiers()),
        )
    )

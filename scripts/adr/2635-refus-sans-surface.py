#!/usr/bin/env python3
"""ADR 2635 - Un refus dit ce qui manque ; la surface dit quoi faire.

Pourquoi « probable » et non « certaine » : un message de modèle peut évoquer un écran sans faute -
« ouvrez la fiche de la nuit » relève du domaine autant que de l'interface, et il n'existe pas de motif
syntaxique qui sépare à coup sûr le vocabulaire métier du chemin d'interface. Le script se concentre
donc sur le marqueur le moins ambigu : le **glyphe du menu**, `☰`, qui ne désigne rien d'autre qu'un
élément d'interface graphique.

Il ne regarde que `**/model/**`, et seulement les **chaînes littérales** : un commentaire qui raconte
l'histoire d'un message corrigé n'est pas un message. C'est la première version de ce script qui l'a
appris - elle comptait ses propres explications.

Ce que la règle protège : la ligne de commande affiche les mêmes refus que l'application. Un message
écrit pour un seul consommateur en trompe tous les autres, et il vieillit mal - le jour où un troisième
apparaît, personne ne repasse sur les six premiers.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import PRODUCTION, rapporte, sans_commentaires_java, sort_si_contrat_demande

# LA PRODUCTION SEULE, et c'est une exception assumee (ADR 4586).
#
# Mesure du 2026-08-26 : 0 suspect en production, 3 dans l arbre de test, et les trois sont
# des MENTIONS. `MoteurTraitementGroupeTest` porte un double qui simule ce que fait
# l application, et deux assertions qui citent le message attendu. Etendre ce garde aux tests
# leur interdirait d affirmer les chaines memes que la regle produit, ce qui est la confusion
# usage / mention que `PatronDuCliquetTest` nomme comme l un des deux pieges du premier
# cliquet du depot.
SOURCES = PRODUCTION

# Chaînes littérales Java, échappements compris.
LITTERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')

# Le glyphe du menu : sans ambiguïté, il ne désigne qu'une interface graphique.
SURFACE = "☰"


def fichiers(sources: pathlib.Path = SOURCES) -> list[pathlib.Path]:
    """Les unites que ce garde LIT, extraites pour que `lus` les compte (issue #5015).

    Le parcours vivait dans `suspects()`, qui ne rendait que ce qu il RETENAIT : un
    ciblage manque donnait zero suspect sur zero fichier, et ce zero passait pour un succes.
    """
    return sorted(sources.rglob("*.java"))


def suspects(sources: pathlib.Path = SOURCES) -> list[str]:
    trouves = []
    for source in fichiers(sources):
        if "/model/" not in source.as_posix():
            continue
        texte = sans_commentaires_java(source.read_text(encoding="utf-8"))
        for litteral in LITTERAL.finditer(texte):
            if SURFACE in litteral.group(1):
                ligne = texte[: litteral.start()].count("\n") + 1
                trouves.append(f"{source}:{ligne}  un refus de modèle nomme le menu")
    return trouves


CONTRAT = {
    "geste": "refus de modele nommant une surface d IHM",
    "population": "PRODUCTION",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/adr/verifie_scripts.py#test_2635_refus_sans_surface",
    "decision": "ADR 2635",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    sys.exit(
        rapporte(
            "2635", "refus de modèle nommant une surface d'IHM", suspects(), lus=len(fichiers())
        )
    )

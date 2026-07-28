#!/usr/bin/env python3
"""ADR 2635 — Un refus dit ce qui manque ; la surface dit quoi faire.

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

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte, sans_commentaires_java  # noqa: E402

SOURCES = pathlib.Path("src/main/java")

# Chaînes littérales Java, échappements compris.
LITTERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')

# Le glyphe du menu : sans ambiguïté, il ne désigne qu'une interface graphique.
SURFACE = "☰"


def suspects(sources: pathlib.Path = SOURCES) -> list[str]:
    trouves = []
    for source in sorted(sources.rglob("*.java")):
        if "/model/" not in source.as_posix():
            continue
        texte = sans_commentaires_java(source.read_text(encoding="utf-8"))
        for litteral in LITTERAL.finditer(texte):
            if SURFACE in litteral.group(1):
                ligne = texte[: litteral.start()].count("\n") + 1
                trouves.append(f"{source}:{ligne}  un refus de modèle nomme le menu")
    return trouves


if __name__ == "__main__":
    sys.exit(rapporte("2635", "refus de modèle nommant une surface d'IHM", suspects()))

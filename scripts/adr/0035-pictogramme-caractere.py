#!/usr/bin/env python3
"""ADR 0035 — Un pictogramme d'IHM est une icône ; un caractère dans une phrase reste un caractère.

Pourquoi « probable » et non « certaine » : l'ADR autorise explicitement un pictogramme **dans une
phrase**. Un `→` au milieu d'un libellé explicatif est donc légitime, là où le `☰` d'un bouton de menu
ne l'est pas. Aucun motif ne sait faire cette différence : c'est un humain qui tranche, script en main.

J'avais d'abord classé cette ADR en « certaine », sur une mesure qui annonçait zéro infraction. Cette
mesure scannait `src/main/resources`, où il n'y a aucun FXML : ils sont co-localisés avec leurs
contrôleurs, sous `src/main/java`. Le scan corrigé en trouve huit.

Ce que la règle protège : un caractère dépend de la police installée, ne suit pas l'échelle du thème,
et n'est pas restitué de la même façon par les lecteurs d'écran. Un `FontIcon` dans le `<graphic>` du
contrôle, si.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402

SOURCES = pathlib.Path("src/main/java")

# Émoticônes, symboles divers, fléchages décoratifs.
PICTOGRAMME = re.compile("[\U0001f300-\U0001faff←-⇿☀-➿⬀-⯿]")

COMMENTAIRE = re.compile(r"<!--.*?-->", re.S)


def sans_commentaires(contenu: str) -> str:
    """Neutralise les commentaires XML en préservant les sauts de ligne.

    Un commentaire est de la prose : le `↔` qui y décrit une barre « à rallonge » est exactement le
    cas que l'ADR autorise. Les compter serait du bruit, et un rapport bruyant cesse d'être lu -
    c'est-à-dire qu'il devient un faux vert. Les retours à la ligne sont conservés pour que les
    numéros de ligne restent justes.
    """
    return COMMENTAIRE.sub(lambda bloc: re.sub(r"[^\n]", " ", bloc.group()), contenu)


def suspects() -> list[str]:
    trouves = []
    for vue in sorted(SOURCES.rglob("*.fxml")):
        contenu = sans_commentaires(vue.read_text(encoding="utf-8"))
        for numero, ligne in enumerate(contenu.splitlines(), 1):
            for signe in PICTOGRAMME.findall(ligne):
                trouves.append(f"{vue}:{numero}  {signe}  {ligne.strip()[:80]}")
    return trouves


if __name__ == "__main__":
    sys.exit(rapporte("0035", "pictogramme posé en caractère dans un FXML", suspects()))

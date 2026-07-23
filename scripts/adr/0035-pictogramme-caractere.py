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
from _commun import rapporte, sans_commentaires_xml  # noqa: E402

SOURCES = pathlib.Path("src/main/java")

# Émoticônes, symboles divers, fléchages décoratifs.
PICTOGRAMME = re.compile("[\U0001f300-\U0001faff←-⇿☀-➿⬀-⯿]")


def suspects(sources: pathlib.Path = SOURCES) -> list[str]:
    # Un commentaire est de la prose : le `↔` qui y décrit une barre « à rallonge » est le cas que
    # l'ADR autorise. On le retire donc d'abord (helper mutualisé dans _commun).
    trouves = []
    for vue in sorted(sources.rglob("*.fxml")):
        contenu = sans_commentaires_xml(vue.read_text(encoding="utf-8"))
        for numero, ligne in enumerate(contenu.splitlines(), 1):
            for signe in PICTOGRAMME.findall(ligne):
                trouves.append(f"{vue}:{numero}  {signe}  {ligne.strip()[:80]}")
    return trouves


if __name__ == "__main__":
    sys.exit(rapporte("0035", "pictogramme posé en caractère dans un FXML", suspects()))

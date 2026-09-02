#!/usr/bin/env python3
"""ADR 0035 - Un pictogramme d'IHM est une icône ; un caractère dans une phrase reste un caractère.

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
from _commun import (
    PRODUCTION,
    RACINE_DEPOT,
    RACINES,
    imprime_contrat,
    rapporte,
    sans_commentaires_xml,
)

# Les DEUX arbres (#4462). Aucune decision n avait restreint ce garde a la production : il est ne
# avant que la question ne se pose. La dette de qualite ne connait pas de code de seconde zone, et
# la mesure d ouverture a rendu ZERO suspect dans l arbre de test - l extension ne coute donc rien
# et ferme la question pour de bon, la ou la laisser ouverte laissait un angle mort grandir.
SOURCES = PRODUCTION

# Émoticônes, symboles divers, fléchages décoratifs.
PICTOGRAMME = re.compile("[\U0001f300-\U0001faff←-⇿☀-➿⬀-⯿]")


def fichiers(sources: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les unites que ce garde LIT, extraites pour que `lus` les compte (issue #5015).

    Le parcours vivait dans `suspects()`, qui ne rendait que ce qu il RETENAIT : un ciblage
    manque donnait zero suspect sur zero fichier, et ce zero passait pour un succes.
    """
    arbres = [sources] if sources else list(RACINES)
    return sorted(f for a in arbres if a.is_dir() for f in a.rglob("*.fxml"))


def suspects(sources: pathlib.Path | None = None) -> list[str]:
    # Un commentaire est de la prose : le `↔` qui y décrit une barre « à rallonge » est le cas que
    # l'ADR autorise. On le retire donc d'abord (helper mutualisé dans _commun).
    trouves = []
    for vue in fichiers(sources):
        contenu = sans_commentaires_xml(vue.read_text(encoding="utf-8"))
        for numero, ligne in enumerate(contenu.splitlines(), 1):
            for signe in PICTOGRAMME.findall(ligne):
                trouves.append(f"{vue}:{numero}  {signe}  {ligne.strip()[:80]}")
    return trouves


CONTRAT = {
    "geste": "pictogramme pose en caractere dans un FXML",
    "population": "PRODUCTION + TESTS",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/adr/verifie_scripts.py#test_0035_pictogramme",
    "decision": "ADR 0035",
}


if __name__ == "__main__":
    # AVANT tout le reste : un contrat s imprime sans rien lire et sans rien exiger.
    if "--contrat" in sys.argv:
        sys.exit(
            imprime_contrat(
                pathlib.Path(__file__).resolve().relative_to(RACINE_DEPOT).as_posix(), CONTRAT
            )
        )
    sys.exit(
        rapporte(
            "0035", "pictogramme posé en caractère dans un FXML", suspects(), lus=len(fichiers())
        )
    )
